package com.newoether.rendrop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Socket

@Serializable
data class ProjectInfo(
    val id: Int,
    val name: String,
    val path: String,
    val outputPath: String,
    val state: String,
    val frameStart: Int,
    val frameEnd: Int,
    val frameStep: Int,
    val resolutionX: Int,
    val resolutionY: Int,
    val resolutionScale: Int,
    val renderEngine: String,
    val finishedFrame: Int,
    val totalFrame: Int,
)

suspend fun scanLanDevices(subnet: String = "192.168.31"): List<String> = coroutineScope {
    val results = mutableListOf<String>()
    val jobs = (1..254).map { i ->
        async(Dispatchers.IO) {
            val ip = "$subnet.$i"
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(ip, 28528), 200)
                    synchronized(results) { results.add(ip) }
                }
            } catch (_: Exception) {}
        }
    }
    jobs.awaitAll()
    results
}

fun fetchProjectsFromDevice(ip: String): List<ProjectInfo> {
    return try {
        val client = OkHttpClient()
        val req = Request.Builder()
            .url("http://$ip:28528/projects")
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body.string()
            Json.decodeFromString<List<ProjectInfo>>(body)
        }
    } catch (_: Exception) {
        emptyList()
    }
}

suspend fun fetchAllProjects(devices: List<String>): List<ProjectInfo> = coroutineScope {
    devices.map { ip ->
        async(Dispatchers.IO) { fetchProjectsFromDevice(ip) }
    }.awaitAll().flatten()
}
