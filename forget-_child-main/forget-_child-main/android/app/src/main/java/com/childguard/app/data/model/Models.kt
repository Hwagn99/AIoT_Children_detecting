package com.childguard.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Các DTO khớp 1-1 với backend/app/schemas.py (snake_case). */

@Serializable
data class LoginRequest(val username: String, val password: String)

@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String = "bearer",
)

@Serializable
data class FcmTokenRequest(val token: String)

@Serializable
data class OkResponse(val ok: Boolean = true, val detail: String = "")

@Serializable
data class StatusDto(
    val level: Int = 0,
    @SerialName("level_name") val levelName: String = "SAFE",
    val adults: Int = 0,
    val children: Int = 0,
    @SerialName("alone_seconds") val aloneSeconds: Double = 0.0,
    val emotion: String = "UNKNOWN",
    @SerialName("emotion_confidence") val emotionConfidence: Double = 0.0,
    @SerialName("temp_c") val tempC: Double? = null,
    @SerialName("door_state") val doorState: String = "unknown",
    @SerialName("hardware_online") val hardwareOnline: Boolean = false,
    @SerialName("camera_online") val cameraOnline: Boolean = false,
    val silenced: Boolean = false,
    val message: String = "",
    val ts: Double = 0.0,
)

@Serializable
data class EventDto(
    val id: Int,
    val ts: Double,
    val level: Int,
    val reason: String = "",
    val emotion: String = "UNKNOWN",
    @SerialName("alone_seconds") val aloneSeconds: Double = 0.0,
    @SerialName("temp_c") val tempC: Double? = null,
    @SerialName("has_snapshot") val hasSnapshot: Boolean = false,
)

@Serializable
data class RuntimeSettingsDto(
    @SerialName("grace_seconds") val graceSeconds: Int = 10,
    @SerialName("warn_seconds") val warnSeconds: Int = 60,
    @SerialName("emergency_seconds") val emergencySeconds: Int = 180,
    @SerialName("temp_warn_c") val tempWarnC: Double = 35.0,
    @SerialName("temp_emergency_c") val tempEmergencyC: Double = 42.0,
    @SerialName("auto_unlock") val autoUnlock: Boolean = true,
    @SerialName("enable_push") val enablePush: Boolean = true,
    @SerialName("enable_sms") val enableSms: Boolean = false,
    @SerialName("emergency_contacts") val emergencyContacts: List<String> = emptyList(),
    @SerialName("analysis_interval_s") val analysisIntervalS: Int = 5,
)

@Serializable
data class DoorRequest(val action: String) // "lock" | "unlock"

@Serializable
data class SirenRequest(val on: Boolean)

@Serializable
data class TestAlertRequest(val level: Int)

/** Tên hiển thị tiếng Việt cho mức cảnh báo / cảm xúc. */
object Labels {
    fun level(level: Int) = when (level) {
        0 -> "An toàn"
        1 -> "Theo dõi"
        2 -> "Cảnh báo"
        else -> "KHẨN CẤP"
    }

    fun emotion(e: String) = when (e) {
        "CALM" -> "Bình tĩnh"
        "SLEEPING" -> "Đang ngủ"
        "FUSSY" -> "Quấy khóc nhẹ"
        "CRYING" -> "Đang khóc"
        else -> "Chưa rõ"
    }

    fun door(d: String) = when (d) {
        "locked" -> "Đã khoá"
        "unlocked" -> "Đã mở"
        else -> "Không rõ"
    }
}
