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
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

class VideoGeneratorWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val channelId = "video_generation"
    private val notificationId = 1001

    private val httpClient = OkHttpClient()

    override suspend fun doWork(): Result {
        val projectName = inputData.getString("projectName") ?: "video"
        val deviceIp = inputData.getString("deviceIp") ?: return Result.failure()
        val projectId = inputData.getInt("projectId", -1)
        val frameNumbers = inputData.getIntArray("frameNumbers") ?: return Result.failure()
        val quality = inputData.getString("quality") ?: "low"
        val fps = inputData.getInt("fps", 24)

        val tempDir = File(applicationContext.cacheDir, "video_temp_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        createNotificationChannel()
        
        try {
            setForeground(createForegroundInfo(0, frameNumbers.size))
        } catch (e: Exception) {
            Log.e("VideoWorker", "FGS start failed", e)
        }

        try {
            // 1. Download frames
            val thumbParam = if (quality == "low") 1 else 0
            var downloadedCount = 0
            var detectedExtension = "jpg"
            
            for ((index, frameNum) in frameNumbers.withIndex()) {
                if (isStopped) return Result.failure()
                
                try {
                    val url = "http://$deviceIp:28528/frame?id=$projectId&frame=$frameNum&thumb=$thumbParam"
                    val request = Request.Builder().url(url).build()
                    httpClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            if (index == 0) {
                                val contentType = response.header("Content-Type")
                                detectedExtension = if (contentType?.contains("png") == true) "png" else "jpg"
                            }
                            
                            val file = File(tempDir, "frame_${String.format("%05d", downloadedCount)}.$detectedExtension")
                            response.body?.byteStream()?.use { input ->
                                FileOutputStream(file).use { output ->
                                    input.copyTo(output)
                                }
                            }
                            if (file.exists() && file.length() > 0) {
                                downloadedCount++
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("VideoWorker", "Download failed for frame $frameNum", e)
                }
                
                // Increased update frequency (every 2 frames)
                if (index % 2 == 0 || index == frameNumbers.size - 1) {
                    try {
                        setForeground(createForegroundInfo(index + 1, frameNumbers.size))
                    } catch (_: Exception) {}
                }
            }

            if (downloadedCount < 2) {
                showFinalNotification(false, null, "Not enough frames.")
                return Result.failure()
            }

            // 2. Generate Video in private storage
            val tempOutputFile = File(applicationContext.cacheDir, "temp_video_${System.currentTimeMillis()}.mp4")
            val cmdText = "ffmpeg -y -framerate $fps -i ${tempDir.absolutePath}/frame_%05d.$detectedExtension -c:v mpeg4 -q:v 3 -pix_fmt yuv420p ${tempOutputFile.absolutePath}"
            val command = cmdText.split(" ").toTypedArray()
            
            Log.d("VideoWorker", "Executing RxFFmpeg: $cmdText")
            val result = RxFFmpegInvoke.getInstance().runCommand(command, null)
            
            if (result != 0) {
                showFinalNotification(false, null, "FFmpeg error $result")
                return Result.failure()
            }

            // 3. Move to public Download/Rendrop
            val publicUri = saveVideoToPublicDownloads(tempOutputFile, "${projectName.replace(" ", "_")}_${System.currentTimeMillis()}.mp4")
            
            return if (publicUri != null) {
                showFinalNotification(true, publicUri, "Video saved to Download/Rendrop")
                Result.success()
            } else {
                showFinalNotification(false, null, "Failed to save video file")
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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Video Generation", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
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
            ForegroundInfo(notificationId, createNotification(current, total), type)
        } else {
            ForegroundInfo(notificationId, createNotification(current, total))
        }
    }

    private fun showFinalNotification(success: Boolean, uri: Uri?, message: String?) {
        val title = if (success) applicationContext.getString(R.string.video_success) else applicationContext.getString(R.string.video_error)
        
        val builder = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(if (success) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
            .setContentTitle(title)
            .setContentText(message ?: "")
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
            
        notificationManager.notify(notificationId + 1, builder.build())
    }
}
