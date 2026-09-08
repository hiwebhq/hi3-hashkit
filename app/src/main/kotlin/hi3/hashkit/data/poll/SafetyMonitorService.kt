package hi3.hashkit.data.poll

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Opt-in always-on safety monitor. Runs as a foreground service with a persistent
 * notification so the smart-plug over-temp cutoff keeps working even when the app is
 * closed. It only polls miners with an armed plug cutoff (cheap when none are set) and
 * never turns power back on. Enabled/disabled from Settings.
 */
@AndroidEntryPoint
class SafetyMonitorService : Service() {

    @Inject lateinit var pollingEngine: PollingEngine

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loop: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        if (loop?.isActive != true) {
            loop = scope.launch {
                while (isActive) {
                    runCatching { pollingEngine.runSafetyCycleOnce() }
                    delay(PollingEngine.SAFETY_INTERVAL_MS)
                }
            }
        }
        // Restart if the OS kills us — thermal protection should be durable.
        return START_STICKY
    }

    override fun onDestroy() {
        loop?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Safety monitor", NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Keeps the smart-plug over-temp cutoff running in the background." }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val open = PendingIntent.getActivity(
            this, 0,
            Intent().setClassName(this, "hi3.hashkit.ui.MainActivity"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Hi3 Hashkit safety monitor")
            .setContentText("Watching armed miners for over-temperature — power cutoff active.")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "safety_monitor"
        private const val NOTIFICATION_ID = 4201

        fun start(context: Context) {
            val intent = Intent(context, SafetyMonitorService::class.java)
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SafetyMonitorService::class.java))
        }
    }
}
