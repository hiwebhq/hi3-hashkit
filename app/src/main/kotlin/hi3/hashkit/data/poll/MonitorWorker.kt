package hi3.hashkit.data.poll

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Background poll cycle. Android schedules periodic work no more often than every
 * 15 minutes and may defer it — background alerting is therefore coarse, and the
 * Settings screen says so explicitly.
 */
@HiltWorker
class MonitorWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val pollingEngine: PollingEngine,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Skip if the app is in the foreground and already polling.
        if (pollingEngine.isPolling.value) return Result.success()
        runCatching { pollingEngine.pollAllOnce() }
            .onFailure { return Result.retry() }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "hi3-background-monitor"

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<MonitorWorker>(15, TimeUnit.MINUTES).build(),
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
