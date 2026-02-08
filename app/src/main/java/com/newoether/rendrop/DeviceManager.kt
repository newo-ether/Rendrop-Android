package com.newoether.rendrop

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "settings")

object DeviceManager {
    private val DEVICES_KEY = stringPreferencesKey("devices")

    fun getDevices(context: Context): Flow<List<Pair<String, String>>> {
        return context.dataStore.data.map { preferences ->
            val json = preferences[DEVICES_KEY] ?: "[]"
            try {
                Json.decodeFromString<List<List<String>>>(json).map { it[0] to it[1] }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    suspend fun saveDevices(context: Context, devices: List<Pair<String, String>>) {
        context.dataStore.edit { preferences ->
            val json = Json.encodeToString(devices.map { listOf(it.first, it.second) })
            preferences[DEVICES_KEY] = json
        }
    }
}
