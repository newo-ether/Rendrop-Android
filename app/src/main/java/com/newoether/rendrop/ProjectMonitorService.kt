package com.newoether.rendrop

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

class ProjectMonitorService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var monitorJob: Job? = null
    private val previousStates = mutableMapOf<String, String>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundService()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundService()
        startMonitoring()
        return START_STICKY
    }

    private fun startForegroundService() {
        val channelId = "monitor_service"
        val channelName = getString(R.string.monitor_service_channel_name)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notificationIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this, 1, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = getString(R.string.monitor_service_title)
        val text = getString(R.string.monitor_service_text)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }

        ServiceCompat.startForeground(this, 1004, notification, type)
    }

    private fun startMonitoring() {
        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            DeviceManager.getDevices(applicationContext).collectLatest { devices ->
                while (isActive) {
                    val ipList = devices.map { it.first }
                    if (ipList.isNotEmpty()) {
                        try {
                            val allProjects = fetchAllProjects(ipList)
                            val currentIps = ipList.toSet()
                            val filteredProjects = allProjects.filter { it.deviceIp in currentIps }

                            filteredProjects.forEach { project ->
                                val key = "${project.deviceIp}_${project.id}"
                                val newState = project.state.lowercase()
                                val oldState = previousStates[key]

                                if (oldState != null) {
                                    if (oldState != newState && (newState == "finished" || newState == "error")) {
                                        showProjectNotification(project, newState == "finished")
                                    }
                                }
                                previousStates[key] = newState
                            }
                        } catch (e: Exception) {
                            Log.e("MonitorService", "Error fetching projects", e)
                        }
                    }
                    delay(5000) // Poll every 5 seconds
                }
            }
        }
    }

    private fun showProjectNotification(project: ProjectInfo, isSuccess: Boolean) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Ensure channel exists (might have been created in MainScreen, but good to be safe)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
             if (notificationManager.getNotificationChannel("project_status") == null) {
                 val name = getString(R.string.channel_project_status)
                 val channel = NotificationChannel("project_status", name, NotificationManager.IMPORTANCE_HIGH)
                 notificationManager.createNotificationChannel(channel)
             }
        }

        val title = if (isSuccess) getString(R.string.project_finished_title) else getString(R.string.project_error_title)
        val text = if (isSuccess) getString(R.string.project_finished_text, project.name) else getString(R.string.project_error_text, project.name)
        val icon = R.drawable.ic_launcher_foreground

        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(this, 2, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, "project_status")
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        // Use a unique ID based on type, IP, project ID and timestamp to ensure every event is kept
        val notificationId = ("status_" + project.deviceIp + "_" + project.id + "_" + System.currentTimeMillis()).hashCode()
        notificationManager.notify(notificationId, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
