package com.childguard.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import com.childguard.app.data.local.SettingsStore
import com.childguard.app.data.repo.Repository

/** Container DI thủ công — đủ cho quy mô đồ án, khỏi cần Hilt. */
class ChildGuardApp : Application() {
    lateinit var repository: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = Repository(SettingsStore(this))
        createAlertChannels()
    }

    private fun createAlertChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        val alarm = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        nm.createNotificationChannel(
            NotificationChannel(CH_WARNING, "Cảnh báo", NotificationManager.IMPORTANCE_HIGH)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_EMERGENCY, "Khẩn cấp", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), alarm)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 600, 300, 600, 300, 600)
                setBypassDnd(true)
            }
        )
    }

    companion object {
        const val CH_WARNING = "childguard_warning"
        const val CH_EMERGENCY = "childguard_emergency"
    }
}
