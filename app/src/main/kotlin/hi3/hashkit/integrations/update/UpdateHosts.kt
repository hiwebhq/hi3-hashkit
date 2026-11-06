package hi3.hashkit.integrations.update

import java.net.URI

/**
 * Download-origin policy shared by the app self-updater and the AxeOS firmware updater:
 * HTTPS only, and the host must be github.com, githubusercontent.com or a true subdomain of
 * either. (A plain `endsWith("github.com")` would accept `evilgithub.com`.)
 */
object UpdateHosts {

    private val trustedDomains = listOf("github.com", "githubusercontent.com")

    fun isTrustedHost(host: String?): Boolean {
        val h = host?.trim()?.lowercase()?.removeSuffix(".") ?: return false
        if (h.isEmpty()) return false
        return trustedDomains.any { d -> h == d || h.endsWith(".$d") }
    }

    /** True for an absolute `https://` URL whose host passes [isTrustedHost]. */
    fun isTrustedUrl(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        if (uri.userInfo != null) return false // https://github.com@evil.example/
        return isTrustedHost(uri.host)
    }
}
