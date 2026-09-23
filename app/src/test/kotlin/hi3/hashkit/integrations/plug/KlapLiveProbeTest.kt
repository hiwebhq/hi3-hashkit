package hi3.hashkit.integrations.plug

import okhttp3.OkHttpClient
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Opt-in LAN probe (skipped unless KLAP_HOST is set): exercises the real KlapClient against a
 * plug, e.g. `KLAP_HOST=10.0.0.106 KLAP_USER=you@x.com KLAP_PW=… ./gradlew :app:testDebugUnitTest
 * --tests '*KlapLiveProbeTest' -i | grep KLAP_PROBE`. Verified 2026-09-22: wrong account → AuthFailed.
 */
class KlapLiveProbeTest {
    @Test
    fun probe() {
        val host = System.getenv("KLAP_HOST"); assumeTrue(host != null)
        val user = System.getenv("KLAP_USER") ?: ""
        val pw = System.getenv("KLAP_PW") ?: ""
        val http = OkHttpClient.Builder().connectTimeout(3, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS).build()
        val client = KlapClient(http)
        val r = client.request(host!!, KasaCredentials(user, pw), KlapClient.method("get_energy_usage"))
        println("KLAP_PROBE host=$host user=${user.take(3)}… -> $r")
        if (r is KlapClient.Result.Ok) {
            val info = client.request(host, KasaCredentials(user, pw), KlapClient.method("get_device_info"))
            println("KLAP_PROBE device_info -> ${(info as? KlapClient.Result.Ok)?.raw?.take(400) ?: info}")
        }
    }
}
