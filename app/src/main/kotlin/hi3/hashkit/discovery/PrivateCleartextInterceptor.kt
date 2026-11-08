package hi3.hashkit.discovery

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp *network* interceptor that binds the cleartext policy to the socket that was
 * actually opened: any `http://` (or `ws://`) request whose connection landed on a non-private
 * address is aborted before a single byte of the request — headers, tokens, bodies — is sent.
 *
 * HTTPS is unaffected (public hosts must use TLS; that is the documented rule), so cloud
 * integrations keep working while miner/plug/HA/MQTT-style cleartext can never escape the
 * LAN/tailnet even when a hostname resolves differently between pre-check and connect.
 */
object PrivateCleartextInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.isHttps) {
            val address = chain.connection()?.route()?.socketAddress?.address
            if (address == null || !MinerHostValidator.isAllowedAddress(address)) {
                throw IOException(
                    "Refused cleartext request to ${request.url.host} " +
                        "(${address?.hostAddress ?: "unresolved"}): not a private/Tailscale address",
                )
            }
        }
        return chain.proceed(request)
    }
}
