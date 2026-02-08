package com.newoether.rendrop

import android.Manifest
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Locale

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

fun isValidIp(ip: String): Boolean {
    val regex = """^((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)\.){3}(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)$""".toRegex()
    return regex.matches(ip)
}

fun ipToInt(ip: String): Int {
    if (!isValidIp(ip)) {
        return 0
    }
    return ip.split(".")
        .map { it.toInt() }
        .reduce { acc, part -> (acc shl 8) or part }
}

fun intToIp(ip: Int): String {
    return listOf(
        (ip shr 24) and 0xFF,
        (ip shr 16) and 0xFF,
        (ip shr 8) and 0xFF,
        ip and 0xFF
    ).joinToString(".")
}

fun getHostName(ip: String): String? {
    try {
        val inetAddress = InetAddress.getByName(ip)
        val hostName = inetAddress.hostName
        return hostName
    }
    catch (_: Exception) {
        return null
    }
}

@RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
fun getSubnet(): List<String> {
    val cm = MainActivity.instance.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork ?: return emptyList()
    val capabilities = cm.getNetworkCapabilities(network) ?: return emptyList()

    if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return emptyList()
    if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return emptyList()

    val ipList: MutableList<String> = mutableListOf()
    val linkProperties = cm.getLinkProperties(network) ?: return emptyList()
    for (linkAddress in linkProperties.linkAddresses) {
        if (linkAddress == null)
        {
            continue
        }
        val address = linkAddress.address
        if (address is Inet4Address) {
            val ip = address.hostAddress ?: continue
            val prefixLength = linkAddress.prefixLength
            val mask = -1 shl (32 - prefixLength)
            val subnetMask = String.format(
                Locale.US,
                "%d.%d.%d.%d",
                (mask shr 24 and 0xff),
                (mask shr 16 and 0xff),
                (mask shr 8 and 0xff),
                (mask and 0xff)
            )
            ipList.addAll(getSubnetIps(ip, subnetMask))
        }
    }
    return ipList
}

fun getSubnetIps(ip: String, mask: String): List<String> {
    val ipInt = ipToInt(ip)
    val maskInt = ipToInt(mask)
    val network = ipInt and maskInt
    val broadcast = network or maskInt.inv()
    val ipList = mutableListOf<String>()
    for (i in network + 1 until broadcast) {
        ipList.add(intToIp(i))
    }
    return ipList
}

suspend fun scanLanDevices(): List<Pair<String, String>> = coroutineScope {
    val subnet = getSubnet()
    val results = mutableListOf<Pair<String, String>>()
    val jobs = subnet.map {
        async(Dispatchers.IO) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(it, 28528), 500)
                    synchronized(results) {
                        results.add(Pair(it, getHostName(it) ?: "未知设备"))
                    }
                }
            } catch (_: Exception) {}
        }
    }
    jobs.awaitAll()
    results
}

fun fetchProjectsFromDevice(ip: String): List<ProjectInfo> {
    return try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(ip, 28528), 500)
        }

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
