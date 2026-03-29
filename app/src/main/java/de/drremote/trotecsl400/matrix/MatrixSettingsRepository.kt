package de.drremote.trotecsl400.matrix

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.matrixDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "matrix_settings"
)

class MatrixSettingsRepository(private val context: Context) {

    private object Keys {
        val homeserver = stringPreferencesKey("homeserver")
        val accessToken = stringPreferencesKey("access_token")
        val roomId = stringPreferencesKey("room_id")
        val enabled = booleanPreferencesKey("enabled")
        val syncToken = stringPreferencesKey("sync_token")
    }

    val config: Flow<MatrixConfig> = context.matrixDataStore.data.map { prefs ->
        MatrixConfig(
            homeserverBaseUrl = prefs[Keys.homeserver] ?: "",
            accessToken = prefs[Keys.accessToken] ?: "",
            roomId = prefs[Keys.roomId] ?: "",
            enabled = prefs[Keys.enabled] ?: false
        )
    }

    suspend fun setHomeserver(url: String) {
        context.matrixDataStore.edit { it[Keys.homeserver] = url }
    }

    suspend fun setAccessToken(token: String) {
        context.matrixDataStore.edit { it[Keys.accessToken] = token }
    }

    suspend fun setRoomId(roomId: String) {
        context.matrixDataStore.edit { it[Keys.roomId] = roomId }
    }

    suspend fun setEnabled(enabled: Boolean) {
        context.matrixDataStore.edit { it[Keys.enabled] = enabled }
    }

    suspend fun setSyncToken(token: String) {
        context.matrixDataStore.edit { it[Keys.syncToken] = token }
    }

    suspend fun getSyncToken(): String? {
        return context.matrixDataStore.data.first()[Keys.syncToken]
    }

    suspend fun getConfig(): MatrixConfig {
        return config.first()
    }
}
