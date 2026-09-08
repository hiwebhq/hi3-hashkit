package hi3.hashkit.data.repo

import hi3.hashkit.data.prefs.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Network difficulty for solo-mining math.
 *
 * The ONLY external (non-miner) request in the app, and it is opt-in: nothing is
 * fetched unless the user enables "Fetch network difficulty" in Settings. The request
 * is a single HTTPS GET to mempool.space that carries no identifying payload beyond
 * the connection itself. Users can instead enter difficulty manually.
 * Documented in docs/SECURITY.md.
 */
@Singleton
class DifficultyRepository @Inject constructor(
    private val client: OkHttpClient,
    private val settingsRepository: SettingsRepository,
) {
    /** Fetch current difficulty and store it in settings. Returns null on failure or when disabled. */
    suspend fun refreshIfEnabled(): Double? {
        val settings = settingsRepository.current()
        if (!settings.difficultyAutoFetch) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("https://mempool.space/api/v1/mining/hashrate/3d")
                    .get()
                    .build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@runCatching null
                    val body = resp.body?.string() ?: return@runCatching null
                    // {"currentDifficulty": <number>, ...}
                    Regex("\"currentDifficulty\"\\s*:\\s*([0-9.eE+]+)")
                        .find(body)?.groupValues?.get(1)?.toDoubleOrNull()
                }
            }.getOrNull()?.also { settingsRepository.setNetworkDifficulty(it) }
        }
    }

    /**
     * Opt-in: fetch the current BTC price in the user's currency from mempool.space and
     * cache it for profitability estimates. Single HTTPS GET, no identifying payload.
     * Verified shape: {"time":..,"USD":..,"EUR":..,"GBP":..,"CAD":..,"CHF":..,"AUD":..,"JPY":..}.
     */
    suspend fun refreshPriceIfEnabled(): Double? {
        val settings = settingsRepository.current()
        if (!settings.btcPriceAutoFetch) return null
        val field = when (settings.currencyCode.uppercase()) {
            "USD", "EUR", "GBP", "CAD", "CHF", "AUD", "JPY" -> settings.currencyCode.uppercase()
            else -> "USD" // mempool.space only serves these; fall back to USD
        }
        return withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url("https://mempool.space/api/v1/prices").get().build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@runCatching null
                    val body = resp.body?.string() ?: return@runCatching null
                    Regex("\"$field\"\\s*:\\s*([0-9.]+)").find(body)?.groupValues?.get(1)?.toDoubleOrNull()
                }
            }.getOrNull()?.also { settingsRepository.setBtcPrice(it) }
        }
    }
}
