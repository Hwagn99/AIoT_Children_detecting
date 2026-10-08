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
    @SerialName("age_ranges") val ageRanges: Map<String, Int> = emptyMap(),
    @SerialName("unclassified_persons") val unclassifiedPersons: Int = 0,
    @SerialName("alone_seconds") val aloneSeconds: Double = 0.0,
    val emotion: String = "UNKNOWN",
    @SerialName("emotion_confidence") val emotionConfidence: Double = 0.0,
    @SerialName("door_state") val doorState: String = "unknown",
    @SerialName("hardware_online") val hardwareOnline: Boolean = false,
    @SerialName("camera_online") val cameraOnline: Boolean = false,
    val silenced: Boolean = false,
    val message: String = "",
    @SerialName("verified_subject") val verifiedSubject: String = "",
    @SerialName("verified_real_child") val verifiedRealChild: Boolean = true,
    @SerialName("needs_parent_confirm") val needsParentConfirm: Boolean = false,
    @SerialName("verify_note") val verifyNote: String = "",
    @SerialName("parent_confirmed_real_child") val parentConfirmedRealChild: Boolean? = null,
    @SerialName("parent_confirm_note") val parentConfirmNote: String = "",
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
    @SerialName("has_snapshot") val hasSnapshot: Boolean = false,
)

@Serializable
data class ReportDto(
    val id: Int,
    val ts: Double,
    val summary: String = "",
    val timeline: List<String> = emptyList(),
    @SerialName("duration_s") val durationS: Double = 0.0,
    @SerialName("system_response") val systemResponse: String = "",
    @SerialName("false_alarm") val falseAlarm: Boolean = false,
    @SerialName("false_alarm_note") val falseAlarmNote: String = "",
)

@Serializable
data class ChatRequest(val question: String)

@Serializable
data class ChatResponseDto(
    val answer: String = "",
    @SerialName("used_tools") val usedTools: List<String> = emptyList(),
    val ts: Double = 0.0,
)

@Serializable
data class ParentConfirmRequest(
    @SerialName("is_real_child") val isRealChild: Boolean = true,
    val note: String = "",
)

@Serializable
data class ChatMessage(val question: String, val answer: String, val tools: List<String> = emptyList())

@Serializable
data class RuntimeSettingsDto(
    @SerialName("grace_seconds") val graceSeconds: Int = 10,
    @SerialName("warn_seconds") val warnSeconds: Int = 60,
    @SerialName("emergency_seconds") val emergencySeconds: Int = 180,
    @SerialName("auto_unlock") val autoUnlock: Boolean = true,
    @SerialName("enable_push") val enablePush: Boolean = true,
    @SerialName("enable_sms") val enableSms: Boolean = false,
    @SerialName("emergency_contacts") val emergencyContacts: List<String> = emptyList(),
    @SerialName("analysis_interval_s") val analysisIntervalS: Int = 5,
    @SerialName("report_delay_s") val reportDelayS: Int = 20,
    @SerialName("enable_ai_notify") val enableAiNotify: Boolean = true,
    @SerialName("enable_ai_report") val enableAiReport: Boolean = true,
    @SerialName("enable_ai_chat") val enableAiChat: Boolean = true,
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
