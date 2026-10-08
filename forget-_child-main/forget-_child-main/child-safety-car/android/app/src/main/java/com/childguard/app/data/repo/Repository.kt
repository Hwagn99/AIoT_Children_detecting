package com.childguard.app.data.repo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.childguard.app.data.local.Session
import com.childguard.app.data.local.SettingsStore
import com.childguard.app.data.model.*
import com.childguard.app.data.remote.ApiService
import com.childguard.app.data.remote.StatusSocket
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

class Repository(private val store: SettingsStore) {

    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }

    @Volatile private var token: String = ""
    @Volatile private var baseUrl: String = ""
    @Volatile private var api: ApiService? = null

    private val okHttp: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val b = chain.request().newBuilder()
            if (token.isNotBlank()) b.header("Authorization", "Bearer $token")
            chain.proceed(b.build())
        }
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private val socket = StatusSocket(okHttp, json)

    // ---------- cấu hình kết nối ----------
    private fun configure(url: String, tok: String) {
        baseUrl = url.trim().trimEnd('/')
        token = tok
        api = Retrofit.Builder()
            .baseUrl("$baseUrl/")
            .client(okHttp)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ApiService::class.java)
    }

    private suspend fun api(): ApiService {
        api?.let { return it }
        val s = store.current()
        configure(s.serverUrl, s.token)
        return api ?: error("Chưa cấu hình server")
    }

    suspend fun session(): Session = store.current()

    // ---------- auth ----------
    suspend fun login(serverUrl: String, username: String, password: String) {
        configure(serverUrl, "")
        val res = api().login(LoginRequest(username, password))
        store.save(serverUrl, res.accessToken, username)
        configure(serverUrl, res.accessToken)
    }

    suspend fun logout() {
        store.clearToken()
        token = ""
    }

    suspend fun registerFcmToken(fcm: String) {
        if (session().isLoggedIn) runCatching { api().registerFcm(FcmTokenRequest(fcm)) }
    }

    // ---------- dữ liệu ----------
    suspend fun statusStream(): Flow<StatusDto> {
        api() // đảm bảo đã cấu hình
        return socket.connect(baseUrl, token)
    }

    suspend fun status() = api().status()
    suspend fun events() = api().events(50)
    suspend fun getSettings() = api().getSettings()
    suspend fun saveSettings(s: RuntimeSettingsDto) = api().putSettings(s)

    suspend fun snapshot(): Bitmap? = withContext(Dispatchers.IO) {
        api().latestSnapshot().use { BitmapFactory.decodeStream(it.byteStream()) }
    }

    // ---------- điều khiển ----------
    suspend fun door(action: String) = api().door(DoorRequest(action))
    suspend fun siren(on: Boolean) = api().siren(SirenRequest(on))
    suspend fun silence() = api().silence()
    suspend fun testAlert(level: Int) = api().testAlert(TestAlertRequest(level))
}
