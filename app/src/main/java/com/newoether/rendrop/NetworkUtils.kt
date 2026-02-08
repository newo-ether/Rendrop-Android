package com.newoether.rendrop

import android.Manifest
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Locale
import java.util.concurrent.TimeUnit

private val client = OkHttpClient.Builder()
    .connectTimeout(2, TimeUnit.SECONDS)
    .readTimeout(5, TimeUnit.SECONDS)
    .build()

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
    val deviceIp: String = "",
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
    return try {
        val inetAddress = InetAddress.getByName(ip)
        inetAddress.hostName
    } catch (_: Exception) {
        null
    }
}

@RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
fun getSubnet(context: Context): List<String> {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork ?: return emptyList()
    val capabilities = cm.getNetworkCapabilities(network) ?: return emptyList()

    // Support both WiFi and Ethernet
    val hasWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    val hasEthernet = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    
    if (!hasWifi && !hasEthernet) return emptyList()
    if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return emptyList()

    val ipList: MutableList<String> = mutableListOf()
    val linkProperties = cm.getLinkProperties(network) ?: return emptyList()
    for (linkAddress in linkProperties.linkAddresses) {
        if (linkAddress == null) continue
        val address = linkAddress.address
        if (address is Inet4Address) {
            val ip = address.hostAddress ?: continue
            val prefixLength = linkAddress.prefixLength
            // Only scan subnets with prefix >= 24 (max 256 IPs)
            if (prefixLength < 24) continue
            
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

suspend fun scanLanDevices(context: Context): List<Pair<String, String>> = coroutineScope {
    val subnet = getSubnet(context)
    val results = mutableListOf<Pair<String, String>>()
    val semaphore = Semaphore(32)
    
    val jobs = subnet.map { ip ->
        async(Dispatchers.IO) {
            semaphore.withPermit {
                try {
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress(ip, 28528), 400)
                        val name = getHostName(ip) ?: context.getString(R.string.unknown_device)
                        synchronized(results) {
                            results.add(Pair(ip, name))
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }
    jobs.awaitAll()
    results
}

fun fetchProjectsFromDevice(ip: String): List<ProjectInfo> {
    return try {
        val req = Request.Builder()
            .url("http://$ip:28528/projects")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val body = resp.body.string()
            Json.decodeFromString<List<ProjectInfo>>(body).map { it.copy(deviceIp = ip) }
        }
    } catch (_: Exception) {
        emptyList()
    }
}

fun fetchProjectDetail(ip: String, id: Int): ProjectInfo? {
    return try {
        val req = Request.Builder()
            .url("http://$ip:28528/projects/$id")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body.string()
            Json.decodeFromString<ProjectInfo>(body).copy(deviceIp = ip)
        }
    } catch (_: Exception) {
        null
    }
}

suspend fun fetchAllProjects(devices: List<String>): List<ProjectInfo> = coroutineScope {
    devices.map { ip ->
        async(Dispatchers.IO) { fetchProjectsFromDevice(ip) }
    }.awaitAll().flatten()
}
