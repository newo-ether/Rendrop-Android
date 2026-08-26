package com.newoether.rendrop

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

class VideoGeneratorWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private data class DownloadSummary(
        val successCount: Int,
        val extension: String?,
    )

    companion object {
        private const val MAX_DOWNLOAD_RETRIES = 2
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var workerNotificationId = 1001
    private val channelId = "video_generation"
    private val resultChannelId = "video_generation_result"
    private val httpClient = OkHttpClient()

    private fun projectIdFromInput(): String? =
        inputData.keyValueMap[VideoWorkInput.PROJECT_ID]?.toString()?.takeIf { it.isNotBlank() }

    private fun frameNumbersFromInput(): IntArray? {
        inputData.getIntArray(VideoWorkInput.LEGACY_FRAME_NUMBERS)
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }

        val start = inputData.keyValueMap[VideoWorkInput.FRAME_START] as? Int ?: return null
        val count = inputData.keyValueMap[VideoWorkInput.FRAME_COUNT] as? Int ?: return null
        val step = inputData.keyValueMap[VideoWorkInput.FRAME_STEP] as? Int ?: return null
        return buildFrameNumbers(start, count, step)
    }

    override suspend fun doWork(): Result {
        val projectName = inputData.getString(VideoWorkInput.PROJECT_NAME) ?: "video"
        val deviceIp = inputData.getString(VideoWorkInput.DEVICE_IP) ?: return Result.failure()
        val projectId = projectIdFromInput() ?: return Result.failure()
        val frameNumbers = frameNumbersFromInput() ?: return Result.failure()
        val quality = inputData.getString(VideoWorkInput.QUALITY) ?: "low"
        val fps = inputData.getInt(VideoWorkInput.FPS, 24)

        workerNotificationId = ("video_progress_${deviceIp}_$projectId").hashCode()
        val tempDir = File(applicationContext.cacheDir, "video_temp_${System.currentTimeMillis()}")
        val tempOutputFile =
            File(applicationContext.cacheDir, "temp_video_${System.currentTimeMillis()}.mp4")
        if (!tempDir.mkdirs() && !tempDir.isDirectory) return Result.failure()

        createNotificationChannels()
        try {
            setForeground(createForegroundInfo(0, frameNumbers.size))
        } catch (error: Exception) {
            Log.e("VideoWorker", "Foreground start failed", error)
        }

        try {
            val thumbParam = if (quality == "low") 1 else 0
            val download = downloadFrames(
                deviceIp = deviceIp,
                projectId = projectId,
                frameNumbers = frameNumbers,
                thumbParam = thumbParam,
                tempDir = tempDir,
            )

            if (download.successCount != frameNumbers.size || download.extension == null) {
                val failedCount = frameNumbers.size - download.successCount
                val message = applicationContext.getString(
                    R.string.error_frame_downloads,
                    failedCount,
                    frameNumbers.size,
                )
                if (runAttemptCount < MAX_DOWNLOAD_RETRIES) {
                    Log.w("VideoWorker", "$message; retry ${runAttemptCount + 1}")
                    return Result.retry()
                }
                showFinalNotification(false, null, message)
                return Result.failure()
            }

            if (!prepareFrameSequence(tempDir, frameNumbers.size, download.extension)) {
                showFinalNotification(
                    false,
                    null,
                    applicationContext.getString(R.string.error_prepare_frames),
                )
                return Result.failure()
            }

            val inputPattern = File(tempDir, "frame_%05d.${download.extension}").absolutePath
            val session = FFmpegKit.executeWithArguments(
                arrayOf(
                    "-y",
                    "-framerate",
                    fps.toString(),
                    "-i",
                    inputPattern,
                    "-c:v",
                    "mpeg4",
                    "-q:v",
                    "3",
                    "-pix_fmt",
                    "yuv420p",
                    tempOutputFile.absolutePath,
                )
            )
            if (!ReturnCode.isSuccess(session.returnCode)) {
                Log.e("VideoWorker", "FFmpeg failed with ${session.returnCode}")
                showFinalNotification(
                    false,
                    null,
                    applicationContext.getString(R.string.error_ffmpeg, session.returnCode.value),
                )
                return Result.failure()
            }

            val publicUri = saveVideoToPublicDownloads(
                tempOutputFile,
                "${projectName.replace(" ", "_")}_${System.currentTimeMillis()}.mp4",
            )
            return if (publicUri != null) {
                showFinalNotification(true, publicUri, applicationContext.getString(R.string.video_saved_path))
                Result.success()
            } else {
                showFinalNotification(false, null, applicationContext.getString(R.string.error_save_video))
                Result.failure()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e("VideoWorker", "Worker crash", error)
            showFinalNotification(false, null, error.localizedMessage)
            return Result.failure()
        } finally {
            tempDir.deleteRecursively()
            tempOutputFile.delete()
        }
    }

    private suspend fun downloadFrames(
        deviceIp: String,
        projectId: String,
        frameNumbers: IntArray,
        thumbParam: Int,
        tempDir: File,
    ): DownloadSummary = coroutineScope {
        val downloadedCount = AtomicInteger(0)
        val extensionLock = Any()
        var detectedExtension: String? = null
        val semaphore = Semaphore(5)

        val results = frameNumbers.mapIndexed { index, frameNum ->
            async(Dispatchers.IO) {
                if (isStopped) return@async false
                semaphore.withPermit {
                    currentCoroutineContext().ensureActive()
                    try {
                        val url =
                            "http://$deviceIp:28528/frame?id=$projectId&frame=$frameNum&thumb=$thumbParam"
                        val request = Request.Builder().url(url).build()
                        httpClient.newCall(request).execute().use { response ->
                            if (!response.isSuccessful) {
                                Log.w("VideoWorker", "Frame $frameNum returned HTTP ${response.code}")
                                return@use false
                            }

                            val tempFile = File(tempDir, "raw_$index.tmp")
                            response.body.byteStream().use { input ->
                                FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                            }
                            if (tempFile.length() == 0L) {
                                tempFile.delete()
                                return@use false
                            }

                            synchronized(extensionLock) {
                                if (detectedExtension == null) {
                                    detectedExtension = if (
                                        response.header("Content-Type")?.contains("png") == true
                                    ) {
                                        "png"
                                    } else {
                                        "jpg"
                                    }
                                }
                            }

                            val count = downloadedCount.incrementAndGet()
                            if (count % 2 == 0 || count == frameNumbers.size) {
                                try {
                                    setForeground(createForegroundInfo(count, frameNumbers.size))
                                } catch (error: Exception) {
                                    Log.w("VideoWorker", "Progress notification update failed", error)
                                }
                            }
                            true
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: IOException) {
                        Log.e("VideoWorker", "Download failed for frame $frameNum", error)
                        false
                    }
                }
            }
        }.awaitAll()

        DownloadSummary(results.count { it }, detectedExtension)
    }

    private fun prepareFrameSequence(tempDir: File, frameCount: Int, extension: String): Boolean {
        for (index in 0 until frameCount) {
            val source = File(tempDir, "raw_$index.tmp")
            val destination = File(
                tempDir,
                "frame_${String.format(Locale.US, "%05d", index)}.$extension",
            )
            if (!source.isFile || !source.renameTo(destination)) return false
        }
        return true
    }

    private fun saveVideoToPublicDownloads(videoFile: File, fileName: String): Uri? {
        val contentResolver = applicationContext.contentResolver
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val downloadsCollection =
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "video/mp4")
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/Rendrop")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val videoUri = contentResolver.insert(downloadsCollection, contentValues) ?: return null
            try {
                val outputStream = contentResolver.openOutputStream(videoUri)
                    ?: throw IOException("Unable to open MediaStore output")
                outputStream.use { output -> videoFile.inputStream().use { it.copyTo(output) } }
                contentValues.clear()
                contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                contentResolver.update(videoUri, contentValues, null, null)
                videoFile.delete()
                videoUri
            } catch (error: Exception) {
                Log.e("VideoWorker", "Saving video failed", error)
                contentResolver.delete(videoUri, null, null)
                null
            }
        } else {
            val downloadsDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val rendropDir = File(downloadsDir, "Rendrop")
            if (!rendropDir.mkdirs() && !rendropDir.isDirectory) return null
            val destination = File(rendropDir, fileName)
            try {
                videoFile.copyTo(destination, overwrite = true)
                videoFile.delete()
                FileProvider.getUriForFile(
                    applicationContext,
                    "${applicationContext.packageName}.fileprovider",
                    destination,
                )
            } catch (error: Exception) {
                Log.e("VideoWorker", "Saving legacy video failed", error)
                null
            }
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val progressChannel = NotificationChannel(
                channelId,
                applicationContext.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
            val resultChannel = NotificationChannel(
                resultChannelId,
                applicationContext.getString(R.string.video_success),
                NotificationManager.IMPORTANCE_HIGH,
            )
            notificationManager.createNotificationChannel(progressChannel)
            notificationManager.createNotificationChannel(resultChannel)
        }
    }

    private fun createNotification(current: Int, total: Int): android.app.Notification {
        val percent = if (total > 0) current * 100 / total else 0
        val title = "${applicationContext.getString(R.string.generating_video)} ($percent%)"
        val progressText = applicationContext.getString(R.string.downloading_frames, current, total)
        return NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(progressText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(progressText))
            .setProgress(total, current, false)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun createForegroundInfo(current: Int, total: Int): ForegroundInfo {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        return if (type == 0) {
            ForegroundInfo(workerNotificationId, createNotification(current, total))
        } else {
            ForegroundInfo(workerNotificationId, createNotification(current, total), type)
        }
    }

    private fun showFinalNotification(success: Boolean, uri: Uri?, message: String?) {
        val title = if (success) {
            applicationContext.getString(R.string.video_success)
        } else {
            applicationContext.getString(R.string.video_error)
        }
        val builder = NotificationCompat.Builder(applicationContext, resultChannelId)
            .setSmallIcon(
                if (success) android.R.drawable.stat_sys_download_done
                else android.R.drawable.stat_notify_error
            )
            .setContentTitle(title)
            .setContentText(message.orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.orEmpty()))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)

        if (success && uri != null) {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/mp4")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            builder.setContentIntent(
                PendingIntent.getActivity(
                    applicationContext,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )
        }

        val resultNotificationId = (
            "video_result_${inputData.getString(VideoWorkInput.DEVICE_IP).orEmpty()}_" +
                "${projectIdFromInput().orEmpty()}_${System.currentTimeMillis()}"
            ).hashCode()
        notificationManager.notify(resultNotificationId, builder.build())
    }
}
