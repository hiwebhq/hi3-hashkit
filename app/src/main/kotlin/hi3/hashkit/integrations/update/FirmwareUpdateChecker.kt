package hi3.hashkit.integrations.update

import hi3.hashkit.data.prefs.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Opt-in firmware-update awareness for AxeOS (Bitaxe / ESP-Miner). When enabled in
 * Settings, this makes a single documented GET to the GitHub releases API and caches the
 * latest tag; the UI compares it to each device's reported version. It never flashes
 * anything and contacts nothing while the setting is off.
 *
 * Endpoint verified: https://api.github.com/repos/bitaxeorg/ESP-Miner/releases/latest
 * returns `tag_name` (e.g. "v2.15.1") and `html_url`.
 */
@Singleton
class FirmwareUpdateChecker @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val settingsRepository: SettingsRepository,
) {
    data class Release(val tag: String, val url: String)

    private val json = Json { ignoreUnknownKeys = true }
    private val _axeOs = MutableStateFlow<Release?>(null)
    val axeOs: StateFlow<Release?> = _axeOs

    @Volatile private var lastCheckedMs = 0L

    /** Fetch the latest AxeOS release at most every 6h, only while the setting is enabled. */
    suspend fun refreshIfEnabled() {
        if (!settingsRepository.current().firmwareUpdateCheck) {
            _axeOs.value = null
            return
        }
        val now = System.currentTimeMillis()
        if (_axeOs.value != null && now - lastCheckedMs < 6 * 3_600_000L) return
        val release = fetchLatest("bitaxeorg/ESP-Miner") ?: return
        lastCheckedMs = now
        _axeOs.value = release
    }

    private suspend fun fetchLatest(repo: String): Release? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$repo/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .get()
            .build()
        runCatching {
            okHttpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val obj = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                val tag = obj["tag_name"]?.jsonPrimitive?.content ?: return@use null
                Release(tag = tag, url = obj["html_url"]?.jsonPrimitive?.content ?: "https://github.com/$repo/releases")
            }
        }.getOrElse { if (it is IOException) null else null }
    }

    companion object {
        /** Only AxeOS/ESP-Miner devices have a verified release feed. */
        fun isAxeOsFamily(firmwareFamily: String?): Boolean {
            val f = firmwareFamily?.lowercase() ?: return false
            return "axeos" in f || "esp-miner" in f || "espminer" in f || "bitaxe" in f
        }

        /** True when [latestTag] is a strictly higher semantic version than [current]. */
        fun isNewer(latestTag: String?, current: String?): Boolean {
            val latest = parse(latestTag) ?: return false
            val cur = parse(current) ?: return false
            for (i in 0..2) {
                if (latest[i] != cur[i]) return latest[i] > cur[i]
            }
            return false
        }

        private fun parse(v: String?): IntArray? {
            val cleaned = v?.trim()?.removePrefix("v")?.removePrefix("V")
                ?.takeWhile { it.isDigit() || it == '.' } ?: return null
            val parts = cleaned.split('.').mapNotNull { it.toIntOrNull() }
            if (parts.isEmpty()) return null
            return intArrayOf(parts.getOrElse(0) { 0 }, parts.getOrElse(1) { 0 }, parts.getOrElse(2) { 0 })
        }
    }
}
