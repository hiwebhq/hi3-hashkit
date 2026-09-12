package hi3.hashkit.data.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import hi3.hashkit.data.db.MinerDao
import hi3.hashkit.data.db.ScheduleDao
import hi3.hashkit.data.db.ScheduleEntity
import hi3.hashkit.data.db.TelemetryDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local exports, shared via the system share sheet (FileProvider). Nothing is uploaded
 * by the app itself. Worker names (wallets) are redacted from CSV by default; the
 * backup contains them (it is meant for the user's own restore) and says so in the UI.
 */
@Singleton
class Exporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val minerDao: MinerDao,
    private val telemetryDao: TelemetryDao,
    private val scheduleDao: ScheduleDao,
    private val maintenanceDao: hi3.hashkit.data.db.MaintenanceDao,
    private val pollingEngine: hi3.hashkit.data.poll.PollingEngine? = null,
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private fun exportFile(name: String): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        return File(dir, name)
    }

    fun shareIntent(file: File, mime: String): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** Telemetry CSV for one miner (or all when minerId is null) over the given window. */
    suspend fun telemetryCsv(minerId: Long?, sinceEpochMs: Long): File =
        withContext(Dispatchers.IO) {
            val miners = minerDao.observeAll().first()
                .filter { !it.isDemo && (minerId == null || it.id == minerId) }
            val file = exportFile("hi3-telemetry-${timestamp()}.csv")
            file.bufferedWriter().use { w ->
                w.appendLine(
                    "timestamp,miner,model,status,hashrate_ghs,hashrate_source,power_w," +
                        "power_source,efficiency_j_th,chip_temp_c,vr_temp_c,fan_rpm," +
                        "shares_accepted,shares_rejected,best_difficulty,uptime_s"
                )
                for (miner in miners) {
                    val rows = telemetryDao.observeSince(miner.id, sinceEpochMs).first()
                    for (r in rows) {
                        w.appendLine(
                            listOf(
                                Instant.ofEpochMilli(r.timestampEpochMs).toString(),
                                csv(miner.name),
                                csv(miner.model ?: ""),
                                r.status,
                                r.hashrateGhs?.toString() ?: "",
                                r.hashrateSource,
                                r.powerW?.toString() ?: "",
                                r.powerSource,
                                r.efficiencyJTh?.toString() ?: "",
                                r.chipTempC?.toString() ?: "",
                                r.vrTempC?.toString() ?: "",
                                firstFanRpm(r.fansJson) ?: "",
                                r.sharesAccepted?.toString() ?: "",
                                r.sharesRejected?.toString() ?: "",
                                r.bestDifficulty?.toString() ?: "",
                                r.uptimeSeconds?.toString() ?: "",
                            ).joinToString(",")
                        )
                    }
                }
            }
            file
        }

    // ------------------------------------------------------------------- backup ----

    @Serializable
    data class BackupMiner(
        val stableKey: String,
        val adapterType: String,
        val name: String,
        val host: String,
        val port: Int,
        val group: String?,
        val location: String?,
        val notes: String?,
        val tagsCsv: String,
        val expectedHashrateGhs: Double?,
    )

    @Serializable
    data class BackupSchedule(
        val enabled: Boolean,
        val label: String,
        val actionType: String,
        val paramsJson: String,
        val targetMinerIdsCsv: String,
        val targetGroup: String?,
        val timeMinutesOfDay: Int,
        val daysOfWeekCsv: String,
        val minIntervalMinutes: Int,
    )

    @Serializable
    data class BackupNote(
        /** The owning miner's stableKey — notes are re-attached by it on restore. */
        val minerStableKey: String,
        val atEpochMs: Long,
        val text: String,
        /** The attached photo's bytes (base64), carried inside the backup; null if none. */
        val photoBase64: String? = null,
    )

    @Serializable
    data class Backup(
        val format: Int = 2,
        val exportedAt: String,
        val miners: List<BackupMiner>,
        val schedules: List<BackupSchedule>,
        /** Added in format 2; older backups decode with no notes. */
        val maintenanceNotes: List<BackupNote> = emptyList(),
    )

    /**
     * Configuration backup: miners + schedules + maintenance notes (with their photos,
     * base64-embedded). Telemetry history is not included.
     * When [passphrase] is non-blank the file is encrypted with [BackupCrypto] and
     * gets a .hi3enc extension; otherwise it is plaintext JSON.
     */
    suspend fun backupJson(passphrase: String? = null): File = withContext(Dispatchers.IO) {
        val miners = minerDao.observeAll().first().filter { !it.isDemo }
        val schedules = scheduleDao.observeAll().first()
        val backup = Backup(
            exportedAt = Instant.now().toString(),
            miners = miners.map {
                BackupMiner(
                    it.stableKey, it.adapterType, it.name, it.host, it.port,
                    it.groupName, it.location, it.notes, it.tagsCsv, it.expectedHashrateGhs,
                )
            },
            schedules = schedules.map {
                BackupSchedule(
                    it.enabled, it.label, it.actionType, it.paramsJson,
                    it.targetMinerIdsCsv, it.targetGroup, it.timeMinutesOfDay,
                    it.daysOfWeekCsv, it.minIntervalMinutes,
                )
            },
            maintenanceNotes = miners.flatMap { miner ->
                maintenanceDao.listForMiner(miner.id).map { note ->
                    BackupNote(
                        minerStableKey = miner.stableKey,
                        atEpochMs = note.atEpochMs,
                        text = note.text,
                        photoBase64 = note.photoPath?.let { path ->
                            runCatching {
                                java.util.Base64.getEncoder().encodeToString(File(path).readBytes())
                            }.getOrNull()
                        },
                    )
                }
            },
        )
        val plain = json.encodeToString(backup)
        val pass = passphrase?.trim().orEmpty()
        val file: File
        if (pass.isNotEmpty()) {
            file = exportFile("hi3-backup-${timestamp()}.hi3enc")
            file.writeText(hi3.hashkit.core.BackupCrypto.encrypt(plain, pass))
        } else {
            file = exportFile("hi3-backup-${timestamp()}.json")
            file.writeText(plain)
        }
        file
    }

    /**
     * Restore miners (by stableKey, non-destructive merge) and schedules (appended).
     * Encrypted backups require the [passphrase] used at export.
     */
    suspend fun restore(content: String, passphrase: String? = null): String = withContext(Dispatchers.IO) {
        val decoded = if (hi3.hashkit.core.BackupCrypto.isEncrypted(content)) {
            val pass = passphrase?.trim().orEmpty()
            if (pass.isEmpty()) return@withContext "ENCRYPTED"
            hi3.hashkit.core.BackupCrypto.decrypt(content, pass)
                ?: return@withContext "Wrong passphrase, or the backup file is corrupt."
        } else content
        val backup = runCatching { json.decodeFromString<Backup>(decoded) }.getOrNull()
            ?: return@withContext "Not a valid Hi3 Miner Watch backup file."
        var minersAdded = 0
        var minersUpdated = 0
        for (m in backup.miners) {
            val existing = minerDao.byStableKey(m.stableKey)
            if (existing == null) {
                minerDao.insert(
                    hi3.hashkit.data.db.MinerEntity(
                        stableKey = m.stableKey, adapterType = m.adapterType, name = m.name,
                        host = m.host, port = m.port, macAddress = null, serialNumber = null,
                        hostname = null, manufacturer = null, model = null, boardVersion = null,
                        asicModel = null, firmwareFamily = null, firmwareVersion = null,
                        groupName = m.group, location = m.location, notes = m.notes,
                        tagsCsv = m.tagsCsv, expectedHashrateGhs = m.expectedHashrateGhs,
                        isDemo = false, createdAtEpochMs = System.currentTimeMillis(),
                        lastSeenAtEpochMs = null,
                    )
                )
                minersAdded++
            } else {
                minerDao.update(
                    existing.copy(
                        name = m.name, host = m.host, port = m.port, groupName = m.group,
                        location = m.location, notes = m.notes, tagsCsv = m.tagsCsv,
                        expectedHashrateGhs = m.expectedHashrateGhs,
                    )
                )
                minersUpdated++
            }
        }
        for (s in backup.schedules) {
            scheduleDao.upsert(
                ScheduleEntity(
                    enabled = s.enabled, label = s.label, actionType = s.actionType,
                    paramsJson = s.paramsJson, targetMinerIdsCsv = "",
                    targetGroup = s.targetGroup, timeMinutesOfDay = s.timeMinutesOfDay,
                    daysOfWeekCsv = s.daysOfWeekCsv, minIntervalMinutes = s.minIntervalMinutes,
                    lastRunAtEpochMs = null, lastResult = null,
                )
            )
        }
        // Maintenance notes (format 2+): re-attach by stableKey; skip duplicates so a repeated
        // restore doesn't multiply notes. Photos are re-materialized into app-private storage.
        var notesAdded = 0
        for (n in backup.maintenanceNotes) {
            val miner = minerDao.byStableKey(n.minerStableKey) ?: continue
            val existing = maintenanceDao.listForMiner(miner.id)
            if (existing.any { it.atEpochMs == n.atEpochMs && it.text == n.text }) continue
            val photoPath = n.photoBase64?.let { b64 ->
                runCatching {
                    val dir = File(context.filesDir, "maintenance").apply { mkdirs() }
                    val file = File(dir, "note_${n.atEpochMs}_${System.nanoTime()}.jpg")
                    file.writeBytes(java.util.Base64.getDecoder().decode(b64))
                    file.absolutePath
                }.getOrNull()
            }
            maintenanceDao.insert(
                hi3.hashkit.data.db.MaintenanceNoteEntity(
                    minerId = miner.id, atEpochMs = n.atEpochMs, text = n.text,
                    photoPath = photoPath,
                )
            )
            notesAdded++
        }
        "Restored: $minersAdded miners added, $minersUpdated updated, " +
            "${backup.schedules.size} schedules and $notesAdded maintenance notes imported " +
            "(identity re-verifies on next poll)."
    }

    // -------------------------------------------------------------- diagnostics ----

    /** Support bundle. IP addresses are redacted unless the user opts in. */
    suspend fun diagnostics(includeAddresses: Boolean): File = withContext(Dispatchers.IO) {
        val miners = minerDao.observeAll().first()
        val sb = StringBuilder()
        sb.appendLine("Hi3 Hashkit diagnostics — ${Instant.now()}")
        sb.appendLine("App version: ${runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrDefault("?")}")
        sb.appendLine("Android: ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})")
        sb.appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        sb.appendLine("Notifications permitted: ${
            androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
        }")
        // Battery state + poll timing — the inputs for real on-device drain analysis.
        runCatching {
            val bm = context.getSystemService(android.content.Context.BATTERY_SERVICE) as android.os.BatteryManager
            val level = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val charging = bm.isCharging
            sb.appendLine("Battery: $level%${if (charging) " (charging)" else ""}")
        }
        pollingEngine?.let {
            sb.appendLine(
                "Poll timing: last ${it.lastPollDurationMs} ms, avg ${it.avgPollDurationMs} ms " +
                    "over ${it.pollCount} cycles"
            )
        }
        val db = context.getDatabasePath("hashkit.db")
        sb.appendLine("Database size: ${db.length() / 1024} KB")
        sb.appendLine()
        sb.appendLine("Miners (${miners.size}):")
        for (m in miners) {
            val host = if (includeAddresses) "${m.host}:${m.port}" else "[redacted]"
            sb.appendLine(
                "- ${m.name} | ${m.manufacturer ?: "?"} ${m.model ?: "?"} | " +
                    "fw ${m.firmwareFamily ?: "?"} ${m.firmwareVersion ?: "?"} | " +
                    "adapter ${m.adapterType} | $host | demo=${m.isDemo}"
            )
        }
        sb.appendLine()
        sb.appendLine("No passwords, wallet addresses, or Tailscale material are included.")
        val file = exportFile("hi3-diagnostics-${timestamp()}.txt")
        file.writeText(sb.toString())
        file
    }

    // ------------------------------------------------------------------ helpers ----

    private fun csv(value: String): String =
        if (value.contains(',') || value.contains('"')) "\"${value.replace("\"", "\"\"")}\"" else value

    private fun firstFanRpm(fansJson: String): String? = runCatching {
        Json.parseToJsonElement(fansJson).let { el ->
            (el as? kotlinx.serialization.json.JsonArray)?.firstOrNull()
                ?.let { (it as? kotlinx.serialization.json.JsonObject)?.get("rpm") }
                ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
        }
    }.getOrNull()

    private fun timestamp(): String =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .format(java.time.LocalDateTime.now())
}
