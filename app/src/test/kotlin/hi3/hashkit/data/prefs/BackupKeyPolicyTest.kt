package hi3.hashkit.data.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupKeyPolicyTest {

    @Test
    fun `every credential key is excluded from backups`() {
        listOf(
            "mmp_api_key_encrypted", "mqtt_password_enc", "ha_token_enc", "kasa_password_encrypted",
            "webhook_token", "pool_api_token", "hashkit_ap_passphrase", "prometheus_token",
            "webhook_token_enc", "pool_api_token_enc", "hashkit_ap_passphrase_enc",
            "some_future_secret", "x_credential",
        ).forEach { assertTrue("$it should be excluded", BackupKeyPolicy.isExcluded(it)) }
    }

    @Test
    fun `device-bound ids are excluded`() {
        listOf("active_farm_id", "pinned_farm_id", "auto_backup_folder_uri", "auto_backup_last_ms")
            .forEach { assertTrue(it, BackupKeyPolicy.isExcluded(it)) }
    }

    @Test
    fun `ordinary settings still travel`() {
        listOf(
            "webhook_url", "webhook_type", "webhook_target", "mqtt_host", "mqtt_username", "kasa_username",
            "hi3_pool_base_url", "hi3_pool_payout_address", "pool_type", "prometheus_port",
            "theme_mode", "alerts_enabled", "th_chip_temp_c", "advanced_unlock_code",
        ).forEach { assertFalse("$it should be exported", BackupKeyPolicy.isExcluded(it)) }
    }

    @Test
    fun `sanitizeUrl strips userinfo and credential query params but keeps the rest`() {
        assertEquals(
            "https://hooks.example.com/plug/on?id=7",
            BackupKeyPolicy.sanitizeUrl("https://user:pa55@hooks.example.com/plug/on?id=7&token=abc&auth=x"),
        )
        assertEquals("http://10.0.0.9/cm?cmnd=Power%20On", BackupKeyPolicy.sanitizeUrl("http://10.0.0.9/cm?cmnd=Power%20On"))
        assertEquals("https://ntfy.sh/my-topic", BackupKeyPolicy.sanitizeUrl("https://ntfy.sh/my-topic?Key=zzz"))
        assertEquals("https://gotify.local", BackupKeyPolicy.sanitizeUrl("https://admin:secret@gotify.local"))
        // Non-URLs and odd input pass through unchanged.
        assertEquals("not a url", BackupKeyPolicy.sanitizeUrl("not a url"))
        assertEquals("", BackupKeyPolicy.sanitizeUrl(""))
    }

    @Test
    fun `decodeTagged only accepts the declared type`() {
        assertEquals(true, BackupKeyPolicy.decodeTagged("b:true", 'b'))
        assertEquals(42, BackupKeyPolicy.decodeTagged("i:42", 'i'))
        assertEquals(9_000_000_000L, BackupKeyPolicy.decodeTagged("l:9000000000", 'l'))
        assertEquals(0.25, BackupKeyPolicy.decodeTagged("d:0.25", 'd'))
        assertEquals("dark", BackupKeyPolicy.decodeTagged("s:dark", 's'))
        // A string stored under a Boolean key would crash every later read — must be dropped.
        assertNull(BackupKeyPolicy.decodeTagged("s:true", 'b'))
        assertNull(BackupKeyPolicy.decodeTagged("b:yes", 'b'))
        assertNull(BackupKeyPolicy.decodeTagged("i:4.5", 'i'))
        assertNull(BackupKeyPolicy.decodeTagged("x:1", 'i'))
        assertNull(BackupKeyPolicy.decodeTagged("garbage", 's'))
        assertNull(BackupKeyPolicy.decodeTagged("", 's'))
    }

    @Test
    fun `endpoint guards cover every integration that stores a credential`() {
        listOf("mmp_base_url", "hi3_pool_base_url", "webhook_url", "ha_base_url", "mqtt_host")
            .forEach { assertTrue("$it should be guarded", BackupKeyPolicy.endpointGuard.containsKey(it)) }
        // Each guard names only credential keys (which are themselves excluded from backups).
        BackupKeyPolicy.endpointGuard.values.flatMap { it.secretKeys }
            .forEach { assertTrue("$it should be a credential key", BackupKeyPolicy.isExcluded(it)) }
    }
}
