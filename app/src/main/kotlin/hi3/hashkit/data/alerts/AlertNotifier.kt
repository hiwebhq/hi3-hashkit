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
        // Per-category channels so users can tune (or silence) each type in system settings.
        listOf(
            CHANNEL_OFFLINE to "Offline miners",
            CHANNEL_THERMAL to "Temperature",
            CHANNEL_FAN to "Fans",
            CHANNEL_SHARES to "Rejected shares",
            CHANNEL_STATUS to "Status changes",
            CHANNEL_RECOVERY to "Recoveries",
        ).forEach { (id, name) ->
            manager.createNotificationChannel(
                NotificationChannel(id, name, NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }

    private fun channelFor(signal: AlertSignal): String {
        if (!signal.active) return CHANNEL_RECOVERY
        return when (signal.type) {
            hi3.hashkit.domain.alerts.AlertType.MINER_OFFLINE -> CHANNEL_OFFLINE
            hi3.hashkit.domain.alerts.AlertType.CHIP_OVER_TEMP,
            hi3.hashkit.domain.alerts.AlertType.VR_OVER_TEMP -> CHANNEL_THERMAL
            hi3.hashkit.domain.alerts.AlertType.FAN_STOPPED -> CHANNEL_FAN
            hi3.hashkit.domain.alerts.AlertType.REJECT_RATE_HIGH -> CHANNEL_SHARES
            else -> CHANNEL_STATUS
        }
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
        val notification = NotificationCompat.Builder(context, channelFor(signal))
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
        const val CHANNEL_OFFLINE = "alerts_offline"
        const val CHANNEL_THERMAL = "alerts_thermal"
        const val CHANNEL_FAN = "alerts_fan"
        const val CHANNEL_SHARES = "alerts_shares"
        const val CHANNEL_STATUS = "alerts_status"
        const val CHANNEL_RECOVERY = "alerts_recovery"
    }
}
