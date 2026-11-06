package hi3.hashkit.integrations.metrics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
