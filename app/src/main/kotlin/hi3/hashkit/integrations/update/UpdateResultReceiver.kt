package hi3.hashkit.integrations.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build

/**
 * Receives the status of an [AppUpdater] install session. When Android decides the update
 * needs the user's OK (first self-update, or Android < 12), the session hands us the system
 * confirm dialog to launch; otherwise the update completes unattended and the app process is
 * restarted by the system. Outcomes are relayed to the UI via [AppUpdater.installEvents].
 */
class UpdateResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_UPDATE_STATUS) return
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                AppUpdater.installEvents.value = AppUpdater.InstallEvent.AwaitingConfirm
                runCatching {
                    confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let(context::startActivity)
                }
            }
            PackageInstaller.STATUS_SUCCESS ->
                AppUpdater.installEvents.value = AppUpdater.InstallEvent.Success
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                AppUpdater.installEvents.value = AppUpdater.InstallEvent.Failed(
                    message ?: "Install was not completed."
                )
            }
        }
    }

    companion object {
        const val ACTION_UPDATE_STATUS = "hi3.hashkit.ACTION_UPDATE_STATUS"
    }
}
