package com.newoether.rendrop

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.work.WorkManager
import coil3.SingletonImageLoader
import com.newoether.rendrop.ui.theme.RendropTheme

class MainActivity : ComponentActivity() {
    companion object {
        lateinit var instance: MainActivity
            private set
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startMonitorService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this
        enableEdgeToEdge()

        requestNotificationPermission()
        requestStoragePermission()
        
        // Ensure a clean state on restart
        clearImageCache()
        cancelOngoingWork()

        // Start background monitor if we already have permission
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || 
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            startMonitorService()
        }

        setContent {
            RendropTheme {
                MainScreen()
            }
        }
    }

    private fun startMonitorService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(Intent(this, ProjectMonitorService::class.java))
        } else {
            startService(Intent(this, ProjectMonitorService::class.java))
        }
    }

    private fun clearImageCache() {
        val imageLoader = SingletonImageLoader.get(this)
        imageLoader.diskCache?.clear()
        imageLoader.memoryCache?.clear()
    }

    private fun cancelOngoingWork() {
        WorkManager.getInstance(this).cancelAllWorkByTag(VideoGeneratorWorker::class.java.name)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }
}
