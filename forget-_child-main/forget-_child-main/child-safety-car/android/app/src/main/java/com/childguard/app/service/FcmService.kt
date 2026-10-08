package com.childguard.app.service

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.childguard.app.ChildGuardApp
import com.childguard.app.MainActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Nhận push từ backend (payload dạng data: level/title/body) và hiện thông báo. */
class FcmService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        val repo = (application as ChildGuardApp).repository
        scope.launch { repo.registerFcmToken(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val level = message.data["level"]?.toIntOrNull() ?: 1
        val title = message.data["title"] ?: message.notification?.title ?: "ChildGuard"
        val body = message.data["body"] ?: message.notification?.body ?: ""

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val channel = if (level >= 3) ChildGuardApp.CH_EMERGENCY else ChildGuardApp.CH_WARNING
        val builder = NotificationCompat.Builder(this, channel)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(open)
        if (level >= 3) builder.setFullScreenIntent(open, true)
        NotificationManagerCompat.from(this).notify(1001, builder.build())
    }
}
