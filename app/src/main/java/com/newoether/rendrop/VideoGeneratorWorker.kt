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
import androidx.work.*
import io.microshow.rxffmpeg.RxFFmpegInvoke
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicInteger

class VideoGeneratorWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var workerNotificationId = 1001
    private val channelId = "video_generation"
    private val resultChannelId = "video_generation_result"

    private val httpClient = OkHttpClient()

    override suspend fun doWork(): Result {
        val projectName = inputData.getString("projectName") ?: "video"
        val deviceIp = inputData.getString("deviceIp") ?: return Result.failure()
        val projectId = inputData.getInt("projectId", -1)
        val frameNumbers = inputData.getIntArray("frameNumbers") ?: return Result.failure()
        val quality = inputData.getString("quality") ?: "low"
        val fps = inputData.getInt("fps", 24)

        // Use a unique notification ID for this project's progress to avoid overwriting others
        workerNotificationId = ("video_progress_" + deviceIp + "_" + projectId).hashCode()

        val tempDir = File(applicationContext.cacheDir, "video_temp_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        createNotificationChannels()
        
        try {
            setForeground(createForegroundInfo(0, frameNumbers.size))
        } catch (e: Exception) {
            Log.e("VideoWorker", "FGS start failed", e)
        }

        try {
            // 1. Download frames
            val thumbParam = if (quality == "low") 1 else 0
            val downloadedCount = AtomicInteger(0)
            var detectedExtension = "jpg"
            val extensionLock = Any()
            val semaphore = Semaphore(5) // Limit parallel downloads

            coroutineScope {
                val jobs = frameNumbers.mapIndexed { index, frameNum ->
                    async(Dispatchers.IO) {
                        if (isStopped) return@async

                        semaphore.withPermit {
                            try {
                                val url = "http://$deviceIp:28528/frame?id=$projectId&frame=$frameNum&thumb=$thumbParam"
                                val request = Request.Builder().url(url).build()
                                httpClient.newCall(request).execute().use { response ->
                                    if (response.isSuccessful) {
                                        synchronized(extensionLock) {
                                            if (downloadedCount.get() == 0) {
                                                val contentType = response.header("Content-Type")
                                                detectedExtension = if (contentType?.contains("png") == true) "png" else "jpg"
                                            }
                                        }

                                        // Save to temporary file with index to preserve order
                                        val tempFile = File(tempDir, "raw_${index}.tmp")
                                        response.body?.byteStream()?.use { input ->
                                            FileOutputStream(tempFile).use { output ->
                                                input.copyTo(output)
                                            }
                                        }

                                        val count = downloadedCount.incrementAndGet()
                                        // Update notification periodically
                                        if (count % 2 == 0 || count == frameNumbers.size) {
                                            try {
                                                setForeground(createForegroundInfo(count, frameNumbers.size))
                                            } catch (_: Exception) {
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("VideoWorker", "Download failed for frame $frameNum", e)
                            }
                        }
                    }
                }
                jobs.awaitAll()
            }

            // Rename and sequence files for FFmpeg
            val rawFiles = tempDir.listFiles { _, name -> name.startsWith("raw_") }
                ?.sortedBy { it.name.removePrefix("raw_").removeSuffix(".tmp").toInt() }

            var finalCount = 0
            rawFiles?.forEach { file ->
                val newFile = File(tempDir, "frame_${String.format("%05d", finalCount)}.$detectedExtension")
                if (file.renameTo(newFile)) {
                    finalCount++
                }
            }

            if (finalCount < 2) {
                showFinalNotification(false, null, applicationContext.getString(R.string.error_not_enough_frames))
                return Result.failure()
            }

            // 2. Generate Video in private storage
            val tempOutputFile = File(applicationContext.cacheDir, "temp_video_${System.currentTimeMillis()}.mp4")
            val cmdText = "ffmpeg -y -framerate $fps -i ${tempDir.absolutePath}/frame_%05d.$detectedExtension -c:v mpeg4 -q:v 3 -pix_fmt yuv420p ${tempOutputFile.absolutePath}"
            val command = cmdText.split(" ").toTypedArray()
            
            Log.d("VideoWorker", "Executing RxFFmpeg: $cmdText")
            val result = RxFFmpegInvoke.getInstance().runCommand(command, null)
            
            if (result != 0) {
                showFinalNotification(false, null, applicationContext.getString(R.string.error_ffmpeg, result))
                return Result.failure()
            }

            // 3. Move to public Download/Rendrop
            val publicUri = saveVideoToPublicDownloads(tempOutputFile, "${projectName.replace(" ", "_")}_${System.currentTimeMillis()}.mp4")
            
            return if (publicUri != null) {
                showFinalNotification(true, publicUri, applicationContext.getString(R.string.video_saved_path))
                Result.success()
            } else {
                showFinalNotification(false, null, applicationContext.getString(R.string.error_save_video))
                Result.failure()
            }

        } catch (e: Exception) {
            Log.e("VideoWorker", "Worker crash", e)
            showFinalNotification(false, null, e.localizedMessage)
            return Result.failure()
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun saveVideoToPublicDownloads(videoFile: File, fileName: String): Uri? {
        val contentResolver = applicationContext.contentResolver
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val downloadsCollection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "video/mp4")
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/Rendrop")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }

            val videoUri = contentResolver.insert(downloadsCollection, contentValues) ?: return null

            try {
                contentResolver.openOutputStream(videoUri)?.use { outputStream ->
                    videoFile.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                contentValues.clear()
                contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                contentResolver.update(videoUri, contentValues, null, null)
                videoFile.delete()
                videoUri
            } catch (e: Exception) {
                contentResolver.delete(videoUri, null, null)
                null
            }
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val rendropDir = File(downloadsDir, "Rendrop")
            rendropDir.mkdirs()
            val destFile = File(rendropDir, fileName)
            
            try {
                videoFile.copyTo(destFile, overwrite = true)
                videoFile.delete()
                FileProvider.getUriForFile(
                    applicationContext,
                    "${applicationContext.packageName}.fileprovider",
                    destFile
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val progressChannel = NotificationChannel(channelId, applicationContext.getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW)
            val resultChannel = NotificationChannel(resultChannelId, applicationContext.getString(R.string.video_success), NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(progressChannel)
            notificationManager.createNotificationChannel(resultChannel)
        }
    }

    private fun createNotification(current: Int, total: Int): android.app.Notification {
        val percent = if (total > 0) (current * 100 / total) else 0
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
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        return if (type != 0) {
            ForegroundInfo(workerNotificationId, createNotification(current, total), type)
        } else {
            ForegroundInfo(workerNotificationId, createNotification(current, total))
        }
    }

    private fun showFinalNotification(success: Boolean, uri: Uri?, message: String?) {
        val title = if (success) applicationContext.getString(R.string.video_success) else applicationContext.getString(R.string.video_error)
        
        val builder = NotificationCompat.Builder(applicationContext, resultChannelId)
            .setSmallIcon(if (success) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
            .setContentTitle(title)
            .setContentText(message ?: "")
            .setStyle(NotificationCompat.BigTextStyle().bigText(message ?: ""))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)

        if (success && uri != null) {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/mp4")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            val pendingIntent = PendingIntent.getActivity(
                applicationContext,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setContentIntent(pendingIntent)
        }
            
        // Use a separate unique ID with a timestamp for the result so every export result is kept
        val resultNotificationId = ("video_result_" + (inputData.getString("deviceIp") ?: "") + "_" + inputData.getInt("projectId", -1) + "_" + System.currentTimeMillis()).hashCode()
        notificationManager.notify(resultNotificationId, builder.build())
    }
}
