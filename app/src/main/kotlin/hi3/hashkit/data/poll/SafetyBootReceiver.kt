package hi3.hashkit.data.poll

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import hi3.hashkit.data.prefs.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Re-arms the always-on safety monitor after a reboot, if the user enabled it. */
class SafetyBootReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BootEntryPoint {
        fun settingsRepository(): SettingsRepository
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val settings = EntryPointAccessors
            .fromApplication(context.applicationContext, BootEntryPoint::class.java)
            .settingsRepository()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                if (settings.current().safetyServiceEnabled) SafetyMonitorService.start(context)
            } finally {
                pending.finish()
            }
        }
    }
}
