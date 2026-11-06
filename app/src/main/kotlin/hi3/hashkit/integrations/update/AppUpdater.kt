package hi3.hashkit.integrations.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import hi3.hashkit.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
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

    /** Download client: an https→http redirect is refused outright (cleartext is allowed app-wide for miners). */
    private val downloadClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .followSslRedirects(false)
            .callTimeout(0, java.util.concurrent.TimeUnit.SECONDS) // APK download; read timeout still applies
            .build()
    }

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
     * null on failure. Only downloads from HTTPS GitHub hosts ([UpdateHosts]); when the release
     * manifest gave a size ([expectedSize] > 0) the file must match it exactly; and the result
     * must be signed with this app's own certificate ([ApkSignature.matchesInstalled]).
     */
    suspend fun download(url: String, expectedSize: Long = 0L, onProgress: (Float) -> Unit): File? {
        if (!enabled || !UpdateHosts.isTrustedUrl(url)) return null
        return withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            // Fresh file each time; clear any stale downloads.
            dir.listFiles()?.forEach { it.delete() }
            val out = File(dir, "hashkit-update.apk")
            val streamed = runCatching {
                downloadClient.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
                    acceptable(resp, expectedSize) && streamBody(resp.body, out, expectedSize, onProgress)
                }
            }.getOrDefault(false)
            val sizeOk = expectedSize <= 0 || out.length() == expectedSize
            if (streamed && sizeOk && ApkSignature.matchesInstalled(context, out)) out else { out.delete(); null }
        }
    }

    /** 2xx, final hop (after redirects) still an HTTPS GitHub host, declared length consistent with the manifest. */
    private fun acceptable(resp: Response, expectedSize: Long): Boolean {
        if (!resp.isSuccessful) return false
        val finalUrl = resp.request.url
        if (!finalUrl.isHttps || !UpdateHosts.isTrustedHost(finalUrl.host)) return false
        val declared = resp.body.contentLength()
        return expectedSize <= 0 || declared <= 0 || declared == expectedSize
    }

    /** Copies the body to [out], aborting (false) as soon as it exceeds [expectedSize]. */
    private fun streamBody(body: ResponseBody, out: File, expectedSize: Long, onProgress: (Float) -> Unit): Boolean {
        val total = body.contentLength().takeIf { it > 0 } ?: expectedSize.takeIf { it > 0 }
        return body.byteStream().use { input ->
            out.outputStream().use { output -> copyLimited(input, output, expectedSize, total, onProgress) }
        }
    }

    private fun copyLimited(
        input: java.io.InputStream,
        output: java.io.OutputStream,
        limit: Long,
        total: Long?,
        onProgress: (Float) -> Unit,
    ): Boolean {
        val buf = ByteArray(DOWNLOAD_BUFFER_BYTES)
        var downloaded = 0L
        var read = input.read(buf)
        while (read != -1) {
            output.write(buf, 0, read)
            downloaded += read
            if (limit > 0 && downloaded > limit) return false
            if (total != null) onProgress((downloaded.toFloat() / total).coerceIn(0f, 1f))
            read = input.read(buf)
        }
        return true
    }

    /** Whether the user has granted this app the "install unknown apps" permission. */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** System settings screen where the user grants this app "install unknown apps". */
    fun unknownSourcesIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Install [apk] through a PackageInstaller session. On Android 12+ the session asks for
     * no user action, which Android honours once this app is its own installer of record —
     * i.e. after the first self-update, later updates install fully unattended. When Android
     * does require confirmation (first time, or older OS), [UpdateResultReceiver] relaunches
     * the system's one-tap confirm dialog. Progress lands on [installEvents].
     */
    suspend fun startInstall(apk: File) {
        if (!enabled) return
        withContext(Dispatchers.IO) {
            if (!ApkSignature.matchesInstalled(context, apk)) {
                apk.delete()
                installEvents.value = InstallEvent.Failed("Update rejected: the APK is not signed with this app's key.")
                return@withContext
            }
            runCatching {
                val installer = context.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL
                ).apply {
                    setAppPackageName(context.packageName)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                    }
                }
                val sessionId = installer.createSession(params)
                installer.openSession(sessionId).use { session ->
                    session.openWrite("hashkit-update.apk", 0, apk.length()).use { out ->
                        apk.inputStream().use { it.copyTo(out) }
                        session.fsync(out)
                    }
                    val statusIntent = Intent(context, UpdateResultReceiver::class.java)
                        .setAction(UpdateResultReceiver.ACTION_UPDATE_STATUS)
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
                    val pending = PendingIntent.getBroadcast(context, sessionId, statusIntent, flags)
                    session.commit(pending.intentSender)
                }
            }.onFailure {
                installEvents.value = InstallEvent.Failed("Install failed: ${it.message ?: "unknown error"}")
            }
        }
    }

    sealed interface InstallEvent {
        /** Android is showing its one-tap confirm dialog (first update, or pre-12 OS). */
        data object AwaitingConfirm : InstallEvent
        data object Success : InstallEvent
        data class Failed(val message: String) : InstallEvent
    }

    companion object {
        private const val DOWNLOAD_BUFFER_BYTES = 64 * 1024

        /**
         * Install-session outcomes, posted by [UpdateResultReceiver]. Static because the
         * manifest-registered receiver is instantiated by the system outside Hilt's graph.
         */
        val installEvents = MutableStateFlow<InstallEvent?>(null)
    }
}

/**
 * Signing-certificate comparison between an APK on disk and the installed app. Android enforces
 * the same rule at install time; checking first means a tampered or foreign file is never handed
 * to PackageInstaller (and never gets the unattended-install path).
 */
internal object ApkSignature {

    fun matchesInstalled(context: Context, apk: File): Boolean = runCatching {
        val pm = context.packageManager
        val ours = digests(pm.getPackageInfo(context.packageName, flags()))
        val theirs = digests(pm.getPackageArchiveInfo(apk.path, flags()) ?: return false)
        ours.isNotEmpty() && ours == theirs
    }.getOrDefault(false)

    @Suppress("DEPRECATION")
    private fun flags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES
        else PackageManager.GET_SIGNATURES

    @Suppress("DEPRECATION")
    private fun digests(info: android.content.pm.PackageInfo): Set<String> {
        val sigs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val si = info.signingInfo ?: return emptySet()
            if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory
        } else {
            info.signatures
        } ?: return emptySet()
        val md = java.security.MessageDigest.getInstance("SHA-256")
        return sigs.map { sig -> md.digest(sig.toByteArray()).joinToString("") { "%02x".format(it) } }.toSet()
    }
}
