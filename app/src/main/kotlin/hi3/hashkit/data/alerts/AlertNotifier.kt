package hi3.hashkit.data.alerts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import hi3.hashkit.R
import hi3.hashkit.domain.alerts.AlertSignal
import hi3.hashkit.ui.MainActivity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlertNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    init {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERTS,
                "Miner alerts",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Offline miners, overheating, fan failures, and recoveries" }
        )
    }

    fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun notify(signal: AlertSignal) {
        if (!canNotify()) return
        val intent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = if (signal.active) "Alert: ${signal.minerName}" else "Recovered: ${signal.minerName}"
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(signal.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(signal.message))
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
        // Stable id per miner+type so a recovery replaces its alert instead of stacking.
        val id = (signal.minerId * 31 + signal.type.ordinal).toInt()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    companion object {
        const val CHANNEL_ALERTS = "alerts"
    }
}
