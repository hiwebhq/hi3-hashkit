package hi3.hashkit.integrations.update

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import hi3.hashkit.adapters.espminer.EspMinerAdapter
import hi3.hashkit.adapters.espminer.EspMinerFirmware
import hi3.hashkit.data.db.AuditDao
import hi3.hashkit.data.db.AuditEventEntity
import hi3.hashkit.data.db.MinerEntity
import hi3.hashkit.data.repo.MinerRepository
import hi3.hashkit.domain.adapter.ActionResult
import hi3.hashkit.domain.adapter.MinerHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-tap AxeOS update for a single Bitaxe: download the release image(s) from the
 * GitHub release the checker already resolved, upload them to the miner over the LAN
 * (web UI first when the release ships one, then the firmware), wait through the reboot
 * and confirm the miner reports the new version. Every attempt is written to the audit
 * log. Only official ESP-Miner v2.x flavors are eligible — forks (NerdQAxe, BC01) ship
 * their own firmware and are refused rather than guessed at.
 *
 * Network: HTTPS downloads from github.com (only while the user taps Update), then plain
 * HTTP to the miner's private address.
 */
@Singleton
class FirmwareUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val espMinerAdapter: EspMinerAdapter,
    private val minerRepository: MinerRepository,
    private val auditDao: AuditDao,
) {
    sealed interface Step {
        data class Downloading(val progress: Float) : Step
        data class UploadingWebUi(val progress: Float) : Step
        data class UploadingFirmware(val progress: Float) : Step
        data object Rebooting : Step
        data object Verifying : Step
        data class Done(val version: String) : Step
        data class Failed(val message: String) : Step
    }

    private class UpdateFailed(message: String) : Exception(message)

    private fun fail(message: String): Nothing = throw UpdateFailed(message)

    /** Eligible = AxeOS family, official v2.x flavor, and the release actually has an image. */
    fun canUpdate(entity: MinerEntity, release: FirmwareUpdateChecker.Release?): Boolean {
        if (release?.firmware == null || entity.isDemo) return false
        if (!FirmwareUpdateChecker.isAxeOsFamily(entity.firmwareFamily)) return false
        val flavor = EspMinerFirmware.flavorOf(minerRepository.identityOf(entity))
        return EspMinerFirmware.controlsSupported(flavor) &&
            FirmwareUpdateChecker.isNewer(release.tag, entity.firmwareVersion)
    }

    fun update(entity: MinerEntity, release: FirmwareUpdateChecker.Release): Flow<Step> = channelFlow {
        val previous = entity.firmwareVersion ?: "unknown"
        var outcome = "failed"
        try {
            val version = runUpdate(entity, release)
            outcome = "success: $version"
            send(Step.Done(version))
        } catch (e: UpdateFailed) {
            outcome = "failed: ${e.message}"
            send(Step.Failed(e.message ?: "Update failed."))
        } finally {
            audit(entity, previous, release.tag, outcome)
            File(context.cacheDir, UPDATES_DIR).listFiles()?.forEach { it.delete() }
        }
    }

    /** The happy path; any problem throws [UpdateFailed] with a user-facing message. */
    private suspend fun ProducerScope<Step>.runUpdate(
        entity: MinerEntity,
        release: FirmwareUpdateChecker.Release,
    ): String {
        val firmwareAsset = release.firmware
        if (firmwareAsset == null || !canUpdate(entity, release)) fail("This miner can't be updated from here.")
        val host = MinerHost(entity.host, entity.port)

        val wwwFile = release.www?.let { asset ->
            download(asset, WWW_FILE) { trySend(Step.Downloading(it * WWW_SHARE)) }
                ?: fail("Couldn't download ${asset.name}.")
        }
        val fwFile = download(firmwareAsset, FIRMWARE_FILE) {
            trySend(Step.Downloading(if (wwwFile == null) it else WWW_SHARE + it * (1f - WWW_SHARE)))
        } ?: fail("Couldn't download ${firmwareAsset.name}.")

        if (wwwFile != null) {
            send(Step.UploadingWebUi(0f))
            espMinerAdapter.uploadImage(host, wwwFile, webUi = true) { trySend(Step.UploadingWebUi(it)) }.orFail()
            send(Step.Rebooting)
            if (!waitForMiner(host)) fail("The miner didn't come back after the web-UI update.")
        }
        send(Step.UploadingFirmware(0f))
        espMinerAdapter.uploadImage(host, fwFile, webUi = false) { trySend(Step.UploadingFirmware(it)) }.orFail()
        send(Step.Rebooting)
        if (!waitForMiner(host)) fail("The miner didn't come back after flashing; check it on its own screen.")
        send(Step.Verifying)
        val version = espMinerAdapter.getIdentity(host)?.firmwareVersion
        if (version == null || FirmwareUpdateChecker.isNewer(release.tag, version)) {
            fail("The miner is back but still reports ${version ?: "no version"}.")
        }
        return version
    }

    private fun ActionResult.orFail() {
        when (this) {
            is ActionResult.Success -> Unit
            is ActionResult.Failure -> fail(message)
            is ActionResult.Unsupported -> fail(reason)
        }
    }

    /** HTTPS download from GitHub into the updates cache; null on any failure or size mismatch. */
    private suspend fun download(
        asset: FirmwareUpdateChecker.Asset,
        fileName: String,
        onProgress: (Float) -> Unit,
    ): File? {
        val hostName = runCatching { java.net.URI(asset.url).host }.getOrNull().orEmpty()
        val trusted = hostName.endsWith("github.com") || hostName.endsWith("githubusercontent.com")
        if (!asset.url.startsWith("https://") || !trusted) return null
        val dir = File(context.cacheDir, UPDATES_DIR).apply { mkdirs() }
        val out = File(dir, fileName)
        val ok = withContext(Dispatchers.IO) {
            runCatching {
                okHttpClient.newBuilder().callTimeout(0, TimeUnit.SECONDS).build()
                    .newCall(Request.Builder().url(asset.url).get().build()).execute().use { resp ->
                        val body = resp.body?.takeIf { resp.isSuccessful } ?: return@use false
                        val total = body.contentLength().takeIf { it > 0 } ?: asset.sizeBytes.takeIf { it > 0 }
                        body.byteStream().use { input ->
                            out.outputStream().use { output -> copyWithProgress(input, output, total, onProgress) }
                        }
                        true
                    }
            }.getOrDefault(false)
        }
        val sizeOk = out.length() >= MIN_IMAGE_BYTES && (asset.sizeBytes <= 0 || out.length() == asset.sizeBytes)
        return out.takeIf { ok && sizeOk }
    }

    private fun copyWithProgress(
        input: java.io.InputStream,
        output: java.io.OutputStream,
        total: Long?,
        onProgress: (Float) -> Unit,
    ) {
        val buf = ByteArray(DOWNLOAD_CHUNK)
        var downloaded = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            output.write(buf, 0, n)
            downloaded += n
            if (total != null) onProgress((downloaded.toFloat() / total).coerceIn(0f, 1f))
        }
    }

    /** Poll the miner until it answers again (it reboots after every image), up to [REBOOT_WAIT_MS]. */
    private suspend fun waitForMiner(host: MinerHost): Boolean {
        delay(REBOOT_GRACE_MS)
        val deadline = System.currentTimeMillis() + REBOOT_WAIT_MS
        while (System.currentTimeMillis() < deadline) {
            if (espMinerAdapter.getIdentity(host) != null) return true
            delay(REBOOT_POLL_MS)
        }
        return false
    }

    private suspend fun audit(entity: MinerEntity, previous: String, tag: String, outcome: String) {
        runCatching {
            auditDao.insert(
                AuditEventEntity(
                    minerId = entity.id,
                    atEpochMs = System.currentTimeMillis(),
                    action = ACTION_FIRMWARE_UPDATE,
                    previousJson = "{\"version\":\"$previous\"}",
                    appliedJson = "{\"version\":\"$tag\"}",
                    outcome = outcome,
                )
            )
        }
    }

    companion object {
        const val ACTION_FIRMWARE_UPDATE = "firmware_update"
        private const val UPDATES_DIR = "updates/firmware"
        private const val FIRMWARE_FILE = "esp-miner.bin"
        private const val WWW_FILE = "www.bin"
        /** Share of the download bar given to www.bin when a release ships one. */
        private const val WWW_SHARE = 0.3f
        private const val DOWNLOAD_CHUNK = 64 * 1024
        /** Anything smaller than this is not an ESP32 firmware image. */
        private const val MIN_IMAGE_BYTES = 200 * 1024L
        private const val REBOOT_GRACE_MS = 8_000L
        private const val REBOOT_POLL_MS = 4_000L
        private const val REBOOT_WAIT_MS = 150_000L
    }
}
