package com.newoether.rendrop

import android.app.Application
import android.content.Context

class RendropApplication : Application() {
    lateinit var projectRepository: ProjectRepository
        private set

    override fun onCreate() {
        super.onCreate()
        projectRepository = ProjectRepository(this).also { it.start() }
    }
}

val Context.projectRepository: ProjectRepository
    get() = (applicationContext as RendropApplication).projectRepository
