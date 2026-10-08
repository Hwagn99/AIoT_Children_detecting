package com.childguard.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "childguard_prefs")

data class Session(val serverUrl: String, val token: String, val username: String) {
    val isLoggedIn get() = serverUrl.isNotBlank() && token.isNotBlank()
}

/** Lưu địa chỉ server + JWT. (Bản thật nên mã hoá token bằng Android Keystore.) */
class SettingsStore(private val context: Context) {
    private val kUrl = stringPreferencesKey("server_url")
    private val kToken = stringPreferencesKey("token")
    private val kUser = stringPreferencesKey("username")

    val session: Flow<Session> = context.dataStore.data.map {
        Session(it[kUrl].orEmpty(), it[kToken].orEmpty(), it[kUser].orEmpty())
    }

    suspend fun current(): Session = session.first()

    suspend fun save(serverUrl: String, token: String, username: String) {
        context.dataStore.edit {
            it[kUrl] = serverUrl.trim().trimEnd('/')
            it[kToken] = token
            it[kUser] = username
        }
    }

    suspend fun clearToken() {
        context.dataStore.edit { it[kToken] = "" }
    }
}
