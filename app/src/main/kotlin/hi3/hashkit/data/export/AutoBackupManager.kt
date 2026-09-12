package hi3.hashkit.data.export

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import hi3.hashkit.data.prefs.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Weekly automatic config backup into a user-chosen SAF folder (persisted URI grant).
 * Runs opportunistically from the poll cycle; keeps the newest [KEEP] backups and prunes
 * older ones. Plaintext JSON — the folder is the user's own choice, and an unattended
 * backup can't prompt for a passphrase.
 */
@Singleton
class AutoBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    // Lazy breaks the construction cycle PollingEngine → this → Exporter → PollingEngine.
    private val exporter: dagger.Lazy<Exporter>,
    private val settingsRepository: SettingsRepository,
) {
    suspend fun maybeRun() {
        val settings = settingsRepository.current()
        val tree = settings.autoBackupFolderUri.takeIf { it.isNotBlank() }?.let(Uri::parse) ?: return
        val now = System.currentTimeMillis()
        if (now - settings.autoBackupLastMs < EVERY_MS) return

        withContext(Dispatchers.IO) {
            val file = exporter.get().backupJson()
            val resolver = context.contentResolver
            val parent = DocumentsContract.buildDocumentUriUsingTree(
                tree, DocumentsContract.getTreeDocumentId(tree),
            )
            val target = DocumentsContract.createDocument(
                resolver, parent, "application/json", file.name,
            ) ?: return@withContext
            resolver.openOutputStream(target)?.use { out ->
                file.inputStream().use { it.copyTo(out) }
            } ?: return@withContext
            settingsRepository.setAutoBackupLastMs(now)
            prune(tree)
        }
    }

    /** Delete the oldest auto-backups beyond [KEEP], matching only our own file names. */
    private fun prune(tree: Uri) = runCatching {
        val resolver = context.contentResolver
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(
            tree, DocumentsContract.getTreeDocumentId(tree),
        )
        val backups = mutableListOf<Pair<String, String>>() // documentId to name
        resolver.query(
            children,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            ),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1) ?: continue
                if (name.startsWith("hi3-backup-")) backups += c.getString(0) to name
            }
        }
        backups.sortedByDescending { it.second } // timestamped names sort chronologically
            .drop(KEEP)
            .forEach { (docId, _) ->
                runCatching {
                    DocumentsContract.deleteDocument(
                        resolver, DocumentsContract.buildDocumentUriUsingTree(tree, docId),
                    )
                }
            }
    }

    companion object {
        const val EVERY_MS = 7L * 86_400_000L // weekly
        const val KEEP = 8
    }
}
