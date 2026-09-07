package hi3.hashkit.discovery

import hi3.hashkit.domain.adapter.AdapterRegistry
import hi3.hashkit.domain.adapter.MinerHost
import hi3.hashkit.domain.adapter.ProbeResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ScanEvent {
    data class Progress(val scanned: Int, val total: Int) : ScanEvent
    data class Found(val host: String, val probe: ProbeResult.Supported) : ScanEvent
    data class Finished(val scanned: Int, val found: Int) : ScanEvent
}

/**
 * Bounded, cancelable subnet scan. Probes only known miner endpoints on port 80 with
 * short timeouts; concurrency is capped. Cancelling the collecting coroutine stops
 * all in-flight probes.
 */
@Singleton
class MinerScanner @Inject constructor(
    private val registry: AdapterRegistry,
) {
    fun scan(
        hosts: List<String>,
        concurrency: Int = 32,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): Flow<ScanEvent> = callbackFlow {
        val semaphore = Semaphore(concurrency.coerceIn(1, 64))
        val scanned = AtomicInteger(0)
        val found = AtomicInteger(0)
        val safeHosts = hosts.filter { MinerHostValidator.isAllowedIp(it) }
        val job = launch(dispatcher) {
            val probes = safeHosts.map { host ->
                launch {
                    semaphore.withPermit {
                        for (adapter in registry.probeable()) {
                            val result = adapter.probe(MinerHost(host))
                            if (result is ProbeResult.Supported) {
                                found.incrementAndGet()
                                trySend(ScanEvent.Found(host, result))
                                break
                            }
                            if (result is ProbeResult.Unreachable) break // port closed; skip other adapters
                        }
                        trySend(ScanEvent.Progress(scanned.incrementAndGet(), safeHosts.size))
                    }
                }
            }
            probes.forEach { it.join() }
            trySend(ScanEvent.Finished(scanned.get(), found.get()))
            close()
        }
        awaitClose { job.cancel() }
    }
}
