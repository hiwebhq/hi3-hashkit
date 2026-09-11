package hi3.hashkit.integrations.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import hi3.hashkit.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-app self-update for SIDELOAD builds. Checks the latest GitHub release for the app,
 * downloads its signed APK, and hands off to Android's package installer. Because the update
 * is signed with the same key and shares the package name, Android installs it *over* the
 * existing app — all local data (miners, telemetry, alerts, rules, notes, settings) is kept.
 *
 * This is disabled in the Play Store build (BuildConfig.SELF_UPDATE=false) — Play forbids
 * apps updating themselves. The only network call is one HTTPS GET to the GitHub releases
 * API plus the APK download, both to public GitHub hosts.
 */
@Singleton
class AppUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
) {
    /** Repo that publishes the app's signed release APKs. */
    private val repo = "hiwebhq/hi3-hashkit"
    private val json = Json { ignoreUnknownKeys = true }

    data class UpdateInfo(
        val latestVersion: String,
        val currentVersion: String,
        val apkUrl: String,
        val sizeBytes: Long,
        val releaseUrl: String,
        val isNewer: Boolean,
    )

    val enabled: Boolean get() = BuildConfig.SELF_UPDATE
    val currentVersion: String get() = BuildConfig.VERSION_NAME

    /** Query the latest release; null on failure or when self-update is disabled. */
    suspend fun check(): UpdateInfo? {
        if (!enabled) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("https://api.github.com/repos/$repo/releases/latest")
                    .header("Accept", "application/vnd.github+json")
                    .get().build()
                okHttpClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val obj = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                    val tag = obj["tag_name"]?.jsonPrimitive?.content ?: return@use null
                    val releaseUrl = obj["html_url"]?.jsonPrimitive?.content
                        ?: "https://github.com/$repo/releases"
                    // Find the first .apk asset.
                    val asset = obj["assets"]?.jsonArray?.map { it.jsonObject }
                        ?.firstOrNull { it["name"]?.jsonPrimitive?.content?.endsWith(".apk", true) == true }
                        ?: return@use null
                    val apkUrl = asset["browser_download_url"]?.jsonPrimitive?.content ?: return@use null
                    val size = asset["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                    UpdateInfo(
                        latestVersion = tag.removePrefix("v").removePrefix("V"),
                        currentVersion = currentVersion,
                        apkUrl = apkUrl,
                        sizeBytes = size,
                        releaseUrl = releaseUrl,
                        isNewer = FirmwareUpdateChecker.isNewer(tag, currentVersion),
                    )
                }
            }.getOrNull()
        }
    }

    /**
     * Download the APK to app-private cache, reporting progress 0..1. Returns the file, or
     * null on failure. Only downloads from HTTPS GitHub hosts.
     */
    suspend fun download(url: String, onProgress: (Float) -> Unit): File? {
        if (!enabled) return null
        val host = runCatching { java.net.URI(url).host }.getOrNull().orEmpty()
        if (!url.startsWith("https://") || !(host.endsWith("github.com") || host.endsWith("githubusercontent.com"))) {
            return null
        }
        return withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                // Fresh file each time; clear any stale downloads.
                dir.listFiles()?.forEach { it.delete() }
                val out = File(dir, "hashkit-update.apk")
                okHttpClient.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val body = resp.body ?: return@use null
                    val total = body.contentLength().takeIf { it > 0 }
                    body.byteStream().use { input ->
                        out.outputStream().use { output ->
                            val buf = ByteArray(64 * 1024)
                            var read: Int
                            var downloaded = 0L
                            while (input.read(buf).also { read = it } != -1) {
                                output.write(buf, 0, read)
                                downloaded += read
                                if (total != null) onProgress((downloaded.toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                    }
                    out
                }
            }.getOrNull()
        }
    }

    /** Intent that launches the system package installer for [apk] (same-key update). */
    fun installIntent(apk: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
