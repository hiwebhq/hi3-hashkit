package hi3.hashkit.data.prefs

/**
 * Which DataStore keys may travel in a config backup, and how a backup's values are vetted on
 * the way back in. Secrets never travel — whether stored as a Keystore blob (device-bound
 * ciphertext, useless elsewhere) or, historically, as plaintext. The decision is made by
 * **name pattern** as well as an explicit list, so a new credential key is excluded by default
 * instead of leaking until someone remembers the list.
 *
 * Everything here is pure so the policy is unit-testable without Android.
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

    // ------------------------------------------------------------ outbound: URL values ----

    /** String settings that hold a URL; [sanitizeUrl] is applied to them on export. */
    val urlKeys = setOf("webhook_url", "mmp_base_url", "hi3_pool_base_url", "ha_base_url", "hash_rental_url")

    private val credentialQueryParams = setOf(
        "token", "auth", "key", "apikey", "api_key", "access_token", "password", "pass", "secret", "signature",
    )

    /**
     * Strip the parts of a URL that carry a credential: `user:pass@` userinfo and query
     * parameters named like a token/key/password. Everything else (scheme, host, port, path,
     * other params) is preserved. Non-URL strings are returned unchanged.
     */
    fun sanitizeUrl(value: String): String {
        val v = value.trim()
        val schemeEnd = v.indexOf(SCHEME_SEPARATOR)
        if (schemeEnd <= 0) return value
        val scheme = v.substring(0, schemeEnd + SCHEME_SEPARATOR.length)
        val rest = stripQueryCredentials(stripUserInfo(v.substring(scheme.length)))
        return scheme + rest
    }

    /** Drop `user:pass@` — everything up to the last '@' that precedes the first '/', '?' or '#'. */
    private fun stripUserInfo(afterScheme: String): String {
        val authorityEnd = afterScheme.indexOfFirst { it == '/' || it == '?' || it == '#' }
            .let { if (it < 0) afterScheme.length else it }
        val at = afterScheme.lastIndexOf('@', authorityEnd - 1)
        return if (at >= 0) afterScheme.substring(at + 1) else afterScheme
    }

    /** Drop query parameters whose name looks like a credential; keep the others and any fragment. */
    private fun stripQueryCredentials(s: String): String {
        val q = s.indexOf('?')
        if (q < 0) return s
        val frag = s.indexOf('#', q).let { if (it < 0) s.length else it }
        val kept = s.substring(q + 1, frag).split('&').filter { param ->
            param.isNotEmpty() && param.substringBefore('=').lowercase() !in credentialQueryParams
        }
        val query = if (kept.isEmpty()) "" else "?" + kept.joinToString("&")
        return s.substring(0, q) + query + s.substring(frag)
    }

    private const val SCHEME_SEPARATOR = "://"

    // ------------------------------------------------------------- inbound: typed decode ----

    /**
     * Decode one `tag:value` backup entry **only if** its tag equals the key's declared
     * [expectedTag]; returns null for a type mismatch, an unknown tag or a garbled value.
     * This keeps a crafted file from storing, say, a String under a Boolean key — which would
     * throw on every later read and crash-loop the app.
     */
    fun decodeTagged(tagged: String, expectedTag: Char): Any? {
        val tag = tagged.substringBefore(':', "")
        if (tag.length != 1 || tag[0] != expectedTag) return null
        val value = tagged.substringAfter(':', "")
        return runCatching {
            when (expectedTag) {
                'b' -> when (value) { "true" -> true; "false" -> false; else -> null }
                'i' -> value.toInt()
                'l' -> value.toLong()
                'f' -> value.toFloat()
                'd' -> value.toDouble()
                's' -> value
                else -> null
            }
        }.getOrNull()
    }

    // --------------------------------------------------- inbound: endpoint-change guard ----

    /** An integration endpoint and the (encrypted) credential keys that authenticate to it. */
    class EndpointGuard(val label: String, val secretKeys: List<String>)

    /**
     * Endpoint settings that must not be changed by a restore while a credential for that
     * integration is already stored on this device. Without this, a tampered backup could
     * re-point the stored MMP key / pool token / Gotify key / HA token / MQTT password at an
     * attacker-controlled server; on a fresh device (no secret yet) the URL restores normally.
     */
    val endpointGuard: Map<String, EndpointGuard> = mapOf(
        "mmp_base_url" to EndpointGuard("MMP", listOf("mmp_api_key_encrypted")),
        "hi3_pool_base_url" to EndpointGuard("Pool stats", listOf("pool_api_token_enc", "pool_api_token")),
        "pool_type" to EndpointGuard("Pool type", listOf("pool_api_token_enc", "pool_api_token")),
        "webhook_url" to EndpointGuard("Webhook", listOf("webhook_token_enc", "webhook_token")),
        "webhook_type" to EndpointGuard("Webhook type", listOf("webhook_token_enc", "webhook_token")),
        "ha_base_url" to EndpointGuard("Home Assistant", listOf("ha_token_enc")),
        "mqtt_host" to EndpointGuard("MQTT broker", listOf("mqtt_password_enc")),
    )
}
