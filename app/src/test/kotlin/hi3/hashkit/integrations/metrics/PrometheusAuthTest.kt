package hi3.hashkit.integrations.metrics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpRequestReaderTest {

    private fun req(text: String, deadlineNanos: Long = Long.MAX_VALUE) =
        HttpRequestReader.read(text.toByteArray(Charsets.ISO_8859_1).inputStream(), 8 * 1024, 64, deadlineNanos)

    @Test
    fun `captures host without port and lowercased`() {
        val r = req("GET /metrics?token=x HTTP/1.1\r\nHost: 10.0.0.5:9184\r\nAuthorization: Bearer y\r\n\r\n")!!
        org.junit.Assert.assertEquals("GET", r.method)
        org.junit.Assert.assertEquals("/metrics", r.path)
        org.junit.Assert.assertEquals("token=x", r.query)
        org.junit.Assert.assertEquals("10.0.0.5", r.host)
        org.junit.Assert.assertEquals("Bearer y", r.authorization)
        org.junit.Assert.assertEquals("example.com", HttpRequestReader.hostPart("Example.COM:80"))
        org.junit.Assert.assertEquals("::1", HttpRequestReader.hostPart("[::1]:9184"))
        org.junit.Assert.assertNull(HttpRequestReader.hostPart("  "))
    }

    @Test
    fun `missing host header is reported as null (HTTP 1_0 clients)`() {
        org.junit.Assert.assertNull(req("GET / HTTP/1.0\r\n\r\n")!!.host)
    }

    @Test
    fun `an expired deadline aborts the read`() {
        org.junit.Assert.assertNull(req("GET / HTTP/1.1\r\nHost: a\r\n\r\n", deadlineNanos = System.nanoTime() - 1))
    }
}

class PrometheusAuthTest {

    @Test
    fun `no token configured means open`() {
        assertTrue(PrometheusAuth.authorized(null, "", expected = ""))
    }

    @Test
    fun `bearer header or token query must match exactly`() {
        assertTrue(PrometheusAuth.authorized("Bearer s3cret", "", expected = "s3cret"))
        assertTrue(PrometheusAuth.authorized("bearer s3cret", "", expected = "s3cret"))
        assertTrue(PrometheusAuth.authorized(null, "token=s3cret", expected = "s3cret"))
        assertTrue(PrometheusAuth.authorized(null, "refresh=5&token=s3cret", expected = "s3cret"))
        assertFalse(PrometheusAuth.authorized(null, "", expected = "s3cret"))
        assertFalse(PrometheusAuth.authorized("Bearer wrong", "", expected = "s3cret"))
        assertFalse(PrometheusAuth.authorized("Bearer s3cre", "", expected = "s3cret"))
        assertFalse(PrometheusAuth.authorized(null, "token=S3CRET", expected = "s3cret"))
        assertFalse(PrometheusAuth.authorized("Basic s3cret", "", expected = "s3cret"))
    }
}
