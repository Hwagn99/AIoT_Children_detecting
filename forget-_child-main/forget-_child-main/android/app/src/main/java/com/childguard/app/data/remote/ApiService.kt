package com.childguard.app.data.remote

import com.childguard.app.data.model.*
import okhttp3.ResponseBody
import retrofit2.http.*

interface ApiService {
    @POST("auth/login") suspend fun login(@Body body: LoginRequest): TokenResponse
    @POST("devices/fcm-token") suspend fun registerFcm(@Body body: FcmTokenRequest): OkResponse

    @GET("status") suspend fun status(): StatusDto
    @GET("events") suspend fun events(@Query("limit") limit: Int = 50): List<EventDto>

    @GET("settings") suspend fun getSettings(): RuntimeSettingsDto
    @PUT("settings") suspend fun putSettings(@Body body: RuntimeSettingsDto): RuntimeSettingsDto

    @POST("control/door") suspend fun door(@Body body: DoorRequest): OkResponse
    @POST("control/siren") suspend fun siren(@Body body: SirenRequest): OkResponse
    @POST("control/silence") suspend fun silence(): OkResponse
    @POST("control/test-alert") suspend fun testAlert(@Body body: TestAlertRequest): OkResponse

    @Streaming @GET("snapshots/latest") suspend fun latestSnapshot(): ResponseBody
}
