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
            CHANNEL_OFFLINE to context.getString(R.string.notif_channel_offline),
            CHANNEL_THERMAL to context.getString(R.string.notif_channel_thermal),
            CHANNEL_FAN to context.getString(R.string.notif_channel_fan),
            CHANNEL_SHARES to context.getString(R.string.notif_channel_shares),
            CHANNEL_STATUS to context.getString(R.string.notif_channel_status),
            CHANNEL_RECOVERY to context.getString(R.string.notif_channel_recovery),
            CHANNEL_WATCHDOG to context.getString(R.string.notif_channel_watchdog),
            CHANNEL_DIGEST to context.getString(R.string.notif_channel_digest),
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
            hi3.hashkit.domain.alerts.AlertType.RULE_TRIGGERED -> CHANNEL_WATCHDOG
            else -> CHANNEL_STATUS
        }
    }

    fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun notify(signal: AlertSignal) {
        if (!canNotify()) return
        // Per-miner request code + action so each notification keeps its own miner extra
        // (a shared PendingIntent would be overwritten by the next alert).
        val intent = PendingIntent.getActivity(
            context,
            signal.minerId.toInt(),
            Intent(context, MainActivity::class.java)
                .setAction("hi3.hashkit.OPEN_MINER_${signal.minerId}")
                .putExtra(MainActivity.EXTRA_OPEN_MINER_ID, signal.minerId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = if (signal.active) {
            context.getString(R.string.notif_alert_title, signal.minerName)
        } else {
            context.getString(R.string.notif_recovered_title, signal.minerName)
        }
        // Stable id per miner+type so a recovery replaces its alert instead of stacking.
        val id = (signal.minerId * 31 + signal.type.ordinal).toInt()
        val builder = NotificationCompat.Builder(context, channelFor(signal))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(signal.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(signal.message))
            .setContentIntent(intent)
            .setAutoCancel(true)
        // Quick-actions on active alerts: reboot the miner or acknowledge, straight from here.
        if (signal.active) {
            builder.addAction(
                0,
                context.getString(R.string.common_reboot),
                actionIntent(signal, id, AlertActionReceiver.ACTION_REBOOT),
            )
            builder.addAction(
                0,
                context.getString(R.string.notif_action_acknowledge),
                actionIntent(signal, id, AlertActionReceiver.ACTION_ACK),
            )
        }
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }

    /** A single daily-digest notification summarizing the last 24h of alerts. */
    fun notifyDigest(title: String, body: String) {
        if (!canNotify()) return
        val intent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_DIGEST)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body.lineSequence().firstOrNull() ?: body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(intent)
            .setAutoCancel(true)
        NotificationManagerCompat.from(context).notify(DIGEST_NOTIFICATION_ID, builder.build())
    }

    private fun actionIntent(signal: AlertSignal, notificationId: Int, action: String): PendingIntent {
        val intent = Intent(context, AlertActionReceiver::class.java).apply {
            this.action = action
            putExtra(AlertActionReceiver.EXTRA_MINER_ID, signal.minerId)
            putExtra(AlertActionReceiver.EXTRA_TYPE, signal.type.name)
            putExtra(AlertActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        return PendingIntent.getBroadcast(
            context,
            (notificationId.toString() + action).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val CHANNEL_OFFLINE = "alerts_offline"
        const val CHANNEL_THERMAL = "alerts_thermal"
        const val CHANNEL_FAN = "alerts_fan"
        const val CHANNEL_SHARES = "alerts_shares"
        const val CHANNEL_STATUS = "alerts_status"
        const val CHANNEL_RECOVERY = "alerts_recovery"
        const val CHANNEL_WATCHDOG = "alerts_watchdog"
        const val CHANNEL_DIGEST = "alerts_digest"
        const val DIGEST_NOTIFICATION_ID = 424242
    }
}
