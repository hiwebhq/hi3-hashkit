package hi3.hashkit.integrations.update

import hi3.hashkit.data.prefs.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
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
 * latest tag plus its downloadable assets; the UI compares the tag to each device's
 * reported version. Checking never flashes anything — [FirmwareUpdater] does that, and
 * only when the user taps Update on a specific miner. Nothing is contacted while the
 * setting is off.
 *
 * Endpoint verified: https://api.github.com/repos/bitaxeorg/ESP-Miner/releases/latest
 * returns `tag_name` (e.g. "v2.15.1"), `html_url`, and `assets[]` with `name`, `size`
 * and `browser_download_url`. Releases ship `esp-miner.bin` (the OTA image; from v2.15 it
 * embeds the web UI) and, on older releases, a separate `www.bin`.
 */
@Singleton
class FirmwareUpdateChecker @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val settingsRepository: SettingsRepository,
) {
    data class Asset(val name: String, val url: String, val sizeBytes: Long)

    data class Release(
        val tag: String,
        val url: String,
        /** `esp-miner.bin` — the OTA firmware image; null if the release has none. */
        val firmware: Asset? = null,
        /** `www.bin` — separate web-UI image on releases that still ship one. */
        val www: Asset? = null,
    )

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
        if (_axeOs.value != null && now - lastCheckedMs < CHECK_INTERVAL_MS) return
        val release = fetchLatest(AXEOS_REPO) ?: return
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
                parseRelease(resp.body?.string().orEmpty(), repo)
            }
        }.getOrElse { if (it is IOException) null else null }
    }

    companion object {
        const val AXEOS_REPO = "bitaxeorg/ESP-Miner"
        const val FIRMWARE_ASSET = "esp-miner.bin"
        const val WWW_ASSET = "www.bin"
        private const val CHECK_INTERVAL_MS = 6 * 3_600_000L

        /** Parse a GitHub "latest release" body; null when it has no tag. */
        fun parseRelease(body: String, repo: String = AXEOS_REPO): Release? {
            val obj = runCatching { Json { ignoreUnknownKeys = true }.parseToJsonElement(body).jsonObject }
                .getOrNull() ?: return null
            val tag = obj["tag_name"]?.jsonPrimitive?.content ?: return null
            val assets = obj["assets"]?.jsonArray?.mapNotNull { el ->
                val a = el.jsonObject
                val name = a["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val url = a["browser_download_url"]?.jsonPrimitive?.content ?: return@mapNotNull null
                Asset(name, url, a["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L)
            }.orEmpty()
            return Release(
                tag = tag,
                url = obj["html_url"]?.jsonPrimitive?.content ?: "https://github.com/$repo/releases",
                firmware = assets.firstOrNull { it.name.equals(FIRMWARE_ASSET, ignoreCase = true) },
                www = assets.firstOrNull { it.name.equals(WWW_ASSET, ignoreCase = true) },
            )
        }

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
