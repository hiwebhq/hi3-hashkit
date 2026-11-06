package hi3.hashkit.data.prefs

/**
 * Which DataStore keys may travel in a config backup. Secrets never do — whether stored as a
 * Keystore blob (device-bound ciphertext, useless elsewhere) or, historically, as plaintext.
 * The decision is made by **name pattern** as well as an explicit list, so a new credential
 * key is excluded by default instead of leaking until someone remembers the list.
 */
object BackupKeyPolicy {

    private val explicit = setOf(
        "active_farm_id", "pinned_farm_id", // DB row ids, not portable
        "auto_backup_folder_uri", "auto_backup_last_ms", // SAF grant is device-bound
    )

    private val secretFragments = listOf("token", "password", "passphrase", "secret", "api_key", "credential")

    /** True when [key] must not be written to, or read from, a backup. */
    fun isExcluded(key: String): Boolean {
        if (key in explicit) return true
        val k = key.lowercase()
        if (k.endsWith("_enc") || k.endsWith("_encrypted")) return true
        return secretFragments.any { it in k }
    }
}
