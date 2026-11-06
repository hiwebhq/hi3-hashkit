package hi3.hashkit.data.prefs

import org.junit.Assert.assertFalse
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
}
