package com.newoether.rendrop

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ProjectKey(val deviceIp: String, val id: String)

data class ProjectRepositoryState(
    val projects: List<ProjectInfo> = emptyList(),
    val errors: Map<String, RendropError> = emptyMap(),
    val isRefreshing: Boolean = false,
    val lastUpdatedAt: Long? = null,
)

class ProjectRepository(
    private val context: Context,
    private val pollIntervalMillis: Long = 3_000L,
    private val fetchProjects: suspend (List<String>) -> ProjectRefreshBatch = ::fetchAllProjects,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshMutex = Mutex()
    private val _state = MutableStateFlow(ProjectRepositoryState())
    private var pollingJob: Job? = null

    @Volatile
    private var deviceIps: List<String>? = null
    private var lastRefreshCompletedAt = 0L

    val state: StateFlow<ProjectRepositoryState> = _state.asStateFlow()

    fun start() {
        if (pollingJob != null) return
        pollingJob = scope.launch {
            DeviceManager.getDevices(context)
                .map { devices -> devices.map { it.first }.distinct() }
                .distinctUntilChanged()
                .collectLatest { ips ->
                    deviceIps = ips
                    if (ips.isEmpty()) {
                        _state.value = ProjectRepositoryState()
                        return@collectLatest
                    }

                    while (currentCoroutineContext().isActive) {
                        refreshFor(
                            ips,
                            showLoading = false,
                            minIntervalMillis = 1_000L,
                        )
                        delay(pollIntervalMillis)
                    }
                }
        }
    }

    suspend fun refresh(showLoading: Boolean = true) {
        refreshFor(deviceIps.orEmpty(), showLoading)
    }

    suspend fun refreshOnOpen() {
        val ips = deviceIps ?: run {
            DeviceManager.getDevices(context)
                .first()
                .map { it.first }
                .distinct()
        }
        refreshFor(
            ips,
            showLoading = false,
            minIntervalMillis = 1_000L,
        )
    }

    private suspend fun refreshFor(
        ips: List<String>,
        showLoading: Boolean,
        minIntervalMillis: Long = 0L,
    ) {
        if (ips.isEmpty()) {
            _state.value = ProjectRepositoryState()
            return
        }

        if (showLoading) {
            _state.update { it.copy(isRefreshing = true) }
        }

        try {
            refreshMutex.withLock {
                if (
                    minIntervalMillis > 0L &&
                    lastRefreshCompletedAt > 0L &&
                    SystemClock.elapsedRealtime() - lastRefreshCompletedAt < minIntervalMillis
                ) {
                    return@withLock
                }

                val batch = fetchProjects(ips)
                val currentDeviceIps = deviceIps
                if (currentDeviceIps != null && ips != currentDeviceIps) return@withLock
                lastRefreshCompletedAt = SystemClock.elapsedRealtime()

                _state.update { current ->
                    current.copy(
                        projects = mergeProjectRefresh(current.projects, ips, batch),
                        errors = batch.errors,
                        lastUpdatedAt = if (batch.projectsByDevice.isNotEmpty()) {
                            System.currentTimeMillis()
                        } else {
                            current.lastUpdatedAt
                        },
                    )
                }
            }
        } finally {
            if (showLoading) {
                _state.update { it.copy(isRefreshing = false) }
            }
        }
    }
}

internal fun mergeProjectRefresh(
    previousProjects: List<ProjectInfo>,
    deviceIps: List<String>,
    batch: ProjectRefreshBatch,
): List<ProjectInfo> = deviceIps.flatMap { ip ->
    batch.projectsByDevice[ip] ?: previousProjects.filter { it.deviceIp == ip }
}.distinctBy { ProjectKey(it.deviceIp, it.id) }
