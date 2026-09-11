package hi3.hashkit.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import hi3.hashkit.domain.alerts.AlertThresholds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

enum class CardDensity { LARGE, MEDIUM, COMPACT, GRID }

/** Which make-tag action the Fleet table offers for labelling inventory. */
enum class InventoryTagType {
    QR, NFC, BOTH;
    val showQr: Boolean get() = this == QR || this == BOTH
    val showNfc: Boolean get() = this == NFC || this == BOTH

    companion object {
        fun fromName(name: String?): InventoryTagType =
            entries.firstOrNull { it.name == name } ?: BOTH
    }
}

/** Default URL the Hash rental "Rent" button opens; a blank setting resets to this. */
const val DEFAULT_HASH_RENTAL_URL = "https://hashpower.braiins.com"

data class AppSettings(
    val useFahrenheit: Boolean = false,
    val demoModeEnabled: Boolean = false,
    val pollIntervalMs: Long = 15_000,
    /** Background polling via WorkManager (>=15-minute Android minimum). */
    val backgroundMonitoringEnabled: Boolean = false,
    /** Electricity price per kWh in [currencyCode]; 0 disables cost estimates. */
    val electricityRatePerKwh: Double = 0.0,
    val currencyCode: String = "USD",
    /** Manually entered network difficulty for solo probability (no network fetch by default). */
    val networkDifficulty: Double = 0.0,
    /** Opt-in fetch of network difficulty from mempool.space (documented external request). */
    val difficultyAutoFetch: Boolean = false,
    /** Opt-in fetch of the BTC fiat price from mempool.space, for profitability estimates. */
    val btcPriceAutoFetch: Boolean = false,
    /** Last fetched/entered BTC price in [currencyCode]; 0 disables revenue estimates. */
    val btcPrice: Double = 0.0,
    /** Days of telemetry history to keep. */
    val retentionDays: Int = 30,
    /** User-defined extra scan subnets (CSV of CIDRs), e.g. remote Tailscale-routed LANs. */
    val extraSubnetsCsv: String = "",
    /** Automatically scan the local subnet on launch so miners appear before you open Add. */
    val autoScanOnStartup: Boolean = true,
    /** Opt-in check of AxeOS firmware releases on GitHub (one documented external request). */
    val firmwareUpdateCheck: Boolean = false,
    /** Always-on foreground service that runs the smart-plug over-temp cutoff even when closed. */
    val safetyServiceEnabled: Boolean = false,
    /** Auto-recover miners that stay offline: power-cycle a configured plug, else reboot. */
    val autoRecoverEnabled: Boolean = false,
    val autoRecoverAfterMin: Long = 15,
    /** Currently viewed farm/site; -1 means "All farms" (no filter). */
    val activeFarmId: Long = -1,
    /** Miner-card size on the dashboard. */
    val cardDensity: CardDensity = CardDensity.LARGE,
    /** Default inventory-tag method the Fleet table offers (QR, NFC, or both). */
    val inventoryTagType: InventoryTagType = InventoryTagType.BOTH,
    /** Show the "Exit Hi3 Hashkit?" confirmation when tapping the exit button. */
    val confirmBeforeExit: Boolean = true,
    /** One-time seed of the local PPLNS test pool into the address book. */
    val pplnsSeeded: Boolean = false,
    /** One-time seed of the well-known public pools (from the speed test) into the address book. */
    val publicPoolsSeeded: Boolean = false,
    /** URL the Hash rental "Rent" button opens — set to your Braiins referral link if you have one. */
    val hashRentalUrl: String = DEFAULT_HASH_RENTAL_URL,
    /** Pool stats integration — OPT-IN; nothing is contacted while false. */
    val hi3PoolEnabled: Boolean = false,
    val hi3PoolBaseUrl: String = "https://pool.hi3.cc",
    /** Payout address / subaccount used as the read-only account key on the pool. */
    val hi3PoolPayoutAddress: String = "",
    /** Which pool the stats come from. */
    val poolType: hi3.hashkit.integrations.hi3.PoolType = hi3.hashkit.integrations.hi3.PoolType.HI3,
    /** Optional read-only API key / watcher token, for pools that require one. */
    val poolApiToken: String = "",
    /** Hi3 MMP integration — OPT-IN; nothing is contacted while false. */
    val mmpEnabled: Boolean = false,
    val mmpBaseUrl: String = "https://mmp.hi3.cc",
    /** Whether an (encrypted) MMP API key is stored; the key itself is never in this flow. */
    val mmpKeyConfigured: Boolean = false,
    /** Show the solo-mining odds card on the dashboard. */
    val showSoloCard: Boolean = false,
    /** Show the profitability & energy card on the dashboard. */
    val showProfitCard: Boolean = false,
    /** Require device biometric/PIN to open the app. */
    val appLockEnabled: Boolean = false,
    /** Theme: SYSTEM, DARK, or LIGHT. */
    val themeMode: hi3.hashkit.ui.theme.ThemeMode = hi3.hashkit.ui.theme.ThemeMode.SYSTEM,
    /** Accent color scheme ("UI Theme"); default blue. */
    val themeColor: hi3.hashkit.ui.theme.ThemeColor = hi3.hashkit.ui.theme.ThemeColor.BLUE,
    /** Wall / TV mode tile+font size. */
    val wallSize: hi3.hashkit.ui.wall.WallSize = hi3.hashkit.ui.wall.WallSize.MEDIUM,
    /** Wall / TV mode fixed grid columns (0 = auto-wrap by tile width; 1–8 = that many across). */
    val wallColumns: Int = 0,
    /** Whether first-run onboarding has been completed. */
    val onboardingComplete: Boolean = false,
    val alertThresholds: AlertThresholds = AlertThresholds(),
    val alertsEnabled: Boolean = false,
    /** Push alerts to a user webhook (ntfy/Gotify/Telegram/generic) so they arrive when closed. */
    val webhookType: hi3.hashkit.data.alerts.WebhookType = hi3.hashkit.data.alerts.WebhookType.NONE,
    val webhookUrl: String = "",
    val webhookToken: String = "",
    val webhookTarget: String = "",
    /** Quiet hours: suppress alert notifications (events are still recorded) in a daily window. */
    val quietHoursEnabled: Boolean = false,
    val quietStartMinute: Int = 22 * 60,
    val quietEndMinute: Int = 7 * 60,
    /** Daily digest: one summary notification of the last 24h of alerts, at [digestHour]. */
    val digestEnabled: Boolean = false,
    val digestHour: Int = 8,
    val lastDigestSentEpochMs: Long = 0,
    /** MQTT publish to a local broker (Home Assistant etc.) — OPT-IN; off by default. */
    val mqttEnabled: Boolean = false,
    val mqttHost: String = "",
    val mqttPort: Int = 1883,
    val mqttUsername: String = "",
    /** Whether an (encrypted) MQTT password is stored; the value itself never enters this flow. */
    val mqttPasswordConfigured: Boolean = false,
    val mqttBaseTopic: String = "hi3hashkit",
    /** Publish Home Assistant MQTT-discovery config so entities auto-appear. */
    val mqttHomeAssistantDiscovery: Boolean = true,
    /** Prometheus /metrics endpoint (Advanced feature) — OPT-IN; off by default. */
    val prometheusEnabled: Boolean = false,
    val prometheusPort: Int = 9184,
    /**
     * Solar-surplus mining (Advanced). Reads grid-export/surplus watts from a local Home
     * Assistant sensor over its REST API. The long-lived token is stored encrypted; only
     * [homeAssistantTokenConfigured] surfaces here.
     */
    val homeAssistantBaseUrl: String = "",
    val homeAssistantEntityId: String = "",
    val homeAssistantTokenConfigured: Boolean = false,
    /** Resume mining when surplus export ≥ this many watts (hysteresis high band). */
    val solarResumeWatts: Double = 500.0,
    /** Curtail when surplus export ≤ this many watts (hysteresis low band; ≤0 = importing). */
    val solarCurtailWatts: Double = 0.0,
    /**
     * Electricity-price curtailment (Advanced) via the Octopus Agile half-hourly feed (UK).
     * Region is the single tariff letter A–P; empty until configured.
     */
    val octopusRegion: String = "",
    /** Resume mining when price ≤ this many p/kWh (hysteresis low band). */
    val priceResumePence: Double = 15.0,
    /** Curtail when price ≥ this many p/kWh (hysteresis high band). */
    val priceCurtailPence: Double = 30.0,
    /**
     * Advanced-feature license gate. The stored unlock code (empty until entered).
     * [advancedUnlocked] is the derived flag features check. Defaults to unlocked so
     * nothing is locked in this release; a future build flips the default to false and
     * requires a valid code. See [hi3.hashkit.core.LicenseValidator].
     */
    val advancedUnlockCode: String = "",
    val advancedUnlocked: Boolean = true,
)

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val useFahrenheit = booleanPreferencesKey("use_fahrenheit")
        val demoMode = booleanPreferencesKey("demo_mode")
        val pollIntervalMs = longPreferencesKey("poll_interval_ms")
        val backgroundMonitoring = booleanPreferencesKey("background_monitoring")
        val electricityRate = doublePreferencesKey("electricity_rate")
        val currencyCode = stringPreferencesKey("currency_code")
        val networkDifficulty = doublePreferencesKey("network_difficulty")
        val difficultyAutoFetch = booleanPreferencesKey("difficulty_auto_fetch")
        val btcPriceAutoFetch = booleanPreferencesKey("btc_price_auto_fetch")
        val btcPrice = doublePreferencesKey("btc_price")
        val retentionDays = intPreferencesKey("retention_days")
        val extraSubnets = stringPreferencesKey("extra_subnets")
        val autoScanOnStartup = booleanPreferencesKey("auto_scan_on_startup")
        val firmwareUpdateCheck = booleanPreferencesKey("firmware_update_check")
        val safetyServiceEnabled = booleanPreferencesKey("safety_service_enabled")
        val autoRecoverEnabled = booleanPreferencesKey("auto_recover_enabled")
        val autoRecoverAfterMin = longPreferencesKey("auto_recover_after_min")
        val activeFarmId = longPreferencesKey("active_farm_id")
        val cardDensity = stringPreferencesKey("card_density")
        val inventoryTagType = stringPreferencesKey("inventory_tag_type")
        val confirmBeforeExit = booleanPreferencesKey("confirm_before_exit")
        val pplnsSeeded = booleanPreferencesKey("pplns_seeded")
        val publicPoolsSeeded = booleanPreferencesKey("public_pools_seeded")
        val hashRentalUrl = stringPreferencesKey("hash_rental_url")
        val hi3PoolEnabled = booleanPreferencesKey("hi3_pool_enabled")
        val hi3PoolBaseUrl = stringPreferencesKey("hi3_pool_base_url")
        val hi3PoolPayoutAddress = stringPreferencesKey("hi3_pool_payout_address")
        val poolType = stringPreferencesKey("pool_type")
        val poolApiToken = stringPreferencesKey("pool_api_token")
        val mmpEnabled = booleanPreferencesKey("mmp_enabled")
        val mmpBaseUrl = stringPreferencesKey("mmp_base_url")
        val mmpApiKeyEncrypted = stringPreferencesKey("mmp_api_key_encrypted")
        val showSoloCard = booleanPreferencesKey("show_solo_card")
        val showProfitCard = booleanPreferencesKey("show_profit_card")
        val appLockEnabled = booleanPreferencesKey("app_lock_enabled")
        val themeMode = stringPreferencesKey("theme_mode")
        val themeColor = stringPreferencesKey("theme_color")
        val wallSize = stringPreferencesKey("wall_size")
        val wallColumns = intPreferencesKey("wall_columns")
        val onboardingComplete = booleanPreferencesKey("onboarding_complete")
        val alertsEnabled = booleanPreferencesKey("alerts_enabled")
        val thHashBelowPct = doublePreferencesKey("th_hash_below_pct")
        val thChipTempC = doublePreferencesKey("th_chip_temp_c")
        val thVrTempC = doublePreferencesKey("th_vr_temp_c")
        val thRejectPct = doublePreferencesKey("th_reject_pct")
        val thCooldownMin = longPreferencesKey("th_cooldown_min")
        val webhookType = stringPreferencesKey("webhook_type")
        val webhookUrl = stringPreferencesKey("webhook_url")
        val webhookToken = stringPreferencesKey("webhook_token")
        val webhookTarget = stringPreferencesKey("webhook_target")
        val quietHoursEnabled = booleanPreferencesKey("quiet_hours_enabled")
        val quietStartMinute = intPreferencesKey("quiet_start_minute")
        val quietEndMinute = intPreferencesKey("quiet_end_minute")
        val digestEnabled = booleanPreferencesKey("digest_enabled")
        val digestHour = intPreferencesKey("digest_hour")
        val lastDigestSentEpochMs = longPreferencesKey("last_digest_sent_ms")
        val mqttEnabled = booleanPreferencesKey("mqtt_enabled")
        val mqttHost = stringPreferencesKey("mqtt_host")
        val mqttPort = intPreferencesKey("mqtt_port")
        val mqttUsername = stringPreferencesKey("mqtt_username")
        val mqttPasswordEnc = stringPreferencesKey("mqtt_password_enc")
        val mqttBaseTopic = stringPreferencesKey("mqtt_base_topic")
        val mqttHaDiscovery = booleanPreferencesKey("mqtt_ha_discovery")
        val prometheusEnabled = booleanPreferencesKey("prometheus_enabled")
        val prometheusPort = intPreferencesKey("prometheus_port")
        val haBaseUrl = stringPreferencesKey("ha_base_url")
        val haEntityId = stringPreferencesKey("ha_entity_id")
        val haTokenEnc = stringPreferencesKey("ha_token_enc")
        val solarResumeWatts = doublePreferencesKey("solar_resume_watts")
        val solarCurtailWatts = doublePreferencesKey("solar_curtail_watts")
        val octopusRegion = stringPreferencesKey("octopus_region")
        val priceResumePence = doublePreferencesKey("price_resume_pence")
        val priceCurtailPence = doublePreferencesKey("price_curtail_pence")
        val advancedUnlockCode = stringPreferencesKey("advanced_unlock_code")
    }

    companion object {
        /**
         * While true, advanced features stay unlocked for everyone regardless of code —
         * the whole set ships free in this release. Flip to false in a future build to
         * require a valid unlock code (see [hi3.hashkit.core.LicenseValidator]).
         */
        const val ADVANCED_FEATURES_FREE = false
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            useFahrenheit = p[Keys.useFahrenheit] ?: false,
            demoModeEnabled = p[Keys.demoMode] ?: false,
            pollIntervalMs = (p[Keys.pollIntervalMs] ?: 15_000).coerceIn(5_000, 86_400_000),
            backgroundMonitoringEnabled = p[Keys.backgroundMonitoring] ?: false,
            electricityRatePerKwh = p[Keys.electricityRate] ?: 0.0,
            currencyCode = p[Keys.currencyCode] ?: "USD",
            networkDifficulty = p[Keys.networkDifficulty] ?: 0.0,
            difficultyAutoFetch = p[Keys.difficultyAutoFetch] ?: false,
            btcPriceAutoFetch = p[Keys.btcPriceAutoFetch] ?: false,
            btcPrice = p[Keys.btcPrice] ?: 0.0,
            retentionDays = (p[Keys.retentionDays] ?: 30).coerceIn(1, 3650),
            extraSubnetsCsv = p[Keys.extraSubnets] ?: "",
            autoScanOnStartup = p[Keys.autoScanOnStartup] ?: true,
            firmwareUpdateCheck = p[Keys.firmwareUpdateCheck] ?: false,
            safetyServiceEnabled = p[Keys.safetyServiceEnabled] ?: false,
            autoRecoverEnabled = p[Keys.autoRecoverEnabled] ?: false,
            autoRecoverAfterMin = (p[Keys.autoRecoverAfterMin] ?: 15L).coerceIn(2, 1440),
            activeFarmId = p[Keys.activeFarmId] ?: -1,
            cardDensity = runCatching { CardDensity.valueOf(p[Keys.cardDensity] ?: "LARGE") }
                .getOrDefault(CardDensity.LARGE),
            inventoryTagType = InventoryTagType.fromName(p[Keys.inventoryTagType]),
            confirmBeforeExit = p[Keys.confirmBeforeExit] ?: true,
            pplnsSeeded = p[Keys.pplnsSeeded] ?: false,
            publicPoolsSeeded = p[Keys.publicPoolsSeeded] ?: false,
            hashRentalUrl = p[Keys.hashRentalUrl]?.takeIf { it.isNotBlank() } ?: DEFAULT_HASH_RENTAL_URL,
            hi3PoolEnabled = p[Keys.hi3PoolEnabled] ?: false,
            hi3PoolBaseUrl = p[Keys.hi3PoolBaseUrl] ?: "https://pool.hi3.cc",
            hi3PoolPayoutAddress = p[Keys.hi3PoolPayoutAddress] ?: "",
            poolType = hi3.hashkit.integrations.hi3.PoolType.fromName(p[Keys.poolType]),
            poolApiToken = p[Keys.poolApiToken] ?: "",
            mmpEnabled = p[Keys.mmpEnabled] ?: false,
            mmpBaseUrl = p[Keys.mmpBaseUrl] ?: "https://mmp.hi3.cc",
            mmpKeyConfigured = !p[Keys.mmpApiKeyEncrypted].isNullOrBlank(),
            showSoloCard = p[Keys.showSoloCard] ?: false,
            showProfitCard = p[Keys.showProfitCard] ?: false,
            appLockEnabled = p[Keys.appLockEnabled] ?: false,
            themeMode = runCatching {
                hi3.hashkit.ui.theme.ThemeMode.valueOf(p[Keys.themeMode] ?: "SYSTEM")
            }.getOrDefault(hi3.hashkit.ui.theme.ThemeMode.SYSTEM),
            themeColor = hi3.hashkit.ui.theme.ThemeColor.fromName(p[Keys.themeColor]),
            wallSize = hi3.hashkit.ui.wall.WallSize.fromName(p[Keys.wallSize]),
            wallColumns = (p[Keys.wallColumns] ?: 0).coerceIn(0, 8),
            onboardingComplete = p[Keys.onboardingComplete] ?: false,
            alertsEnabled = p[Keys.alertsEnabled] ?: false,
            alertThresholds = AlertThresholds(
                hashrateBelowPercent = p[Keys.thHashBelowPct] ?: 80.0,
                chipTempC = p[Keys.thChipTempC] ?: 70.0,
                vrTempC = p[Keys.thVrTempC] ?: 90.0,
                rejectRatePercent = p[Keys.thRejectPct] ?: 3.0,
                cooldownMs = ((p[Keys.thCooldownMin] ?: 30L).coerceIn(1, 1440)) * 60_000,
            ),
            webhookType = hi3.hashkit.data.alerts.WebhookType.fromName(p[Keys.webhookType]),
            webhookUrl = p[Keys.webhookUrl] ?: "",
            webhookToken = p[Keys.webhookToken] ?: "",
            webhookTarget = p[Keys.webhookTarget] ?: "",
            quietHoursEnabled = p[Keys.quietHoursEnabled] ?: false,
            quietStartMinute = (p[Keys.quietStartMinute] ?: 22 * 60).coerceIn(0, 1439),
            quietEndMinute = (p[Keys.quietEndMinute] ?: 7 * 60).coerceIn(0, 1439),
            digestEnabled = p[Keys.digestEnabled] ?: false,
            digestHour = (p[Keys.digestHour] ?: 8).coerceIn(0, 23),
            lastDigestSentEpochMs = p[Keys.lastDigestSentEpochMs] ?: 0,
            mqttEnabled = p[Keys.mqttEnabled] ?: false,
            mqttHost = p[Keys.mqttHost] ?: "",
            mqttPort = (p[Keys.mqttPort] ?: 1883).coerceIn(1, 65535),
            mqttUsername = p[Keys.mqttUsername] ?: "",
            mqttPasswordConfigured = !p[Keys.mqttPasswordEnc].isNullOrBlank(),
            mqttBaseTopic = (p[Keys.mqttBaseTopic] ?: "hi3hashkit").ifBlank { "hi3hashkit" },
            mqttHomeAssistantDiscovery = p[Keys.mqttHaDiscovery] ?: true,
            prometheusEnabled = p[Keys.prometheusEnabled] ?: false,
            prometheusPort = (p[Keys.prometheusPort] ?: 9184).coerceIn(1024, 65535),
            homeAssistantBaseUrl = p[Keys.haBaseUrl] ?: "",
            homeAssistantEntityId = p[Keys.haEntityId] ?: "",
            homeAssistantTokenConfigured = !p[Keys.haTokenEnc].isNullOrBlank(),
            solarResumeWatts = p[Keys.solarResumeWatts] ?: 500.0,
            solarCurtailWatts = p[Keys.solarCurtailWatts] ?: 0.0,
            octopusRegion = p[Keys.octopusRegion] ?: "",
            priceResumePence = p[Keys.priceResumePence] ?: 15.0,
            priceCurtailPence = p[Keys.priceCurtailPence] ?: 30.0,
            advancedUnlockCode = p[Keys.advancedUnlockCode] ?: "",
            advancedUnlocked = ADVANCED_FEATURES_FREE ||
                hi3.hashkit.core.LicenseValidator.isValid(p[Keys.advancedUnlockCode]),
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setUseFahrenheit(value: Boolean) = edit { it[Keys.useFahrenheit] = value }
    suspend fun setDemoMode(value: Boolean) = edit { it[Keys.demoMode] = value }
    suspend fun setPollIntervalMs(value: Long) = edit { it[Keys.pollIntervalMs] = value }
    suspend fun setBackgroundMonitoring(value: Boolean) = edit { it[Keys.backgroundMonitoring] = value }
    suspend fun setElectricityRate(value: Double) = edit { it[Keys.electricityRate] = value }
    suspend fun setCurrencyCode(value: String) = edit { it[Keys.currencyCode] = value }
    suspend fun setNetworkDifficulty(value: Double) = edit { it[Keys.networkDifficulty] = value }
    suspend fun setDifficultyAutoFetch(value: Boolean) = edit { it[Keys.difficultyAutoFetch] = value }
    suspend fun setBtcPriceAutoFetch(value: Boolean) = edit { it[Keys.btcPriceAutoFetch] = value }
    suspend fun setBtcPrice(value: Double) = edit { it[Keys.btcPrice] = value }
    suspend fun setRetentionDays(value: Int) = edit { it[Keys.retentionDays] = value }
    suspend fun setExtraSubnets(value: String) = edit { it[Keys.extraSubnets] = value }
    suspend fun setAutoScanOnStartup(value: Boolean) = edit { it[Keys.autoScanOnStartup] = value }
    suspend fun setFirmwareUpdateCheck(value: Boolean) = edit { it[Keys.firmwareUpdateCheck] = value }
    suspend fun setSafetyServiceEnabled(value: Boolean) = edit { it[Keys.safetyServiceEnabled] = value }
    suspend fun setAutoRecoverEnabled(value: Boolean) = edit { it[Keys.autoRecoverEnabled] = value }
    suspend fun setAutoRecoverAfterMin(value: Long) = edit { it[Keys.autoRecoverAfterMin] = value }
    suspend fun setActiveFarmId(value: Long) = edit { it[Keys.activeFarmId] = value }
    suspend fun setCardDensity(value: CardDensity) = edit { it[Keys.cardDensity] = value.name }
    suspend fun setInventoryTagType(value: InventoryTagType) = edit { it[Keys.inventoryTagType] = value.name }
    suspend fun setHashRentalUrl(value: String) = edit { it[Keys.hashRentalUrl] = value.trim() }
    suspend fun setConfirmBeforeExit(value: Boolean) = edit { it[Keys.confirmBeforeExit] = value }
    suspend fun setPplnsSeeded(value: Boolean) = edit { it[Keys.pplnsSeeded] = value }
    suspend fun setPublicPoolsSeeded(value: Boolean) = edit { it[Keys.publicPoolsSeeded] = value }
    suspend fun setHi3PoolEnabled(value: Boolean) = edit { it[Keys.hi3PoolEnabled] = value }
    suspend fun setHi3PoolBaseUrl(value: String) = edit { it[Keys.hi3PoolBaseUrl] = value.trim() }
    suspend fun setHi3PoolPayoutAddress(value: String) = edit { it[Keys.hi3PoolPayoutAddress] = value.trim() }
    suspend fun setPoolType(value: hi3.hashkit.integrations.hi3.PoolType) = edit { it[Keys.poolType] = value.name }
    suspend fun setPoolApiToken(value: String) = edit { it[Keys.poolApiToken] = value.trim() }
    suspend fun setMmpEnabled(value: Boolean) = edit { it[Keys.mmpEnabled] = value }
    suspend fun setMmpBaseUrl(value: String) = edit { it[Keys.mmpBaseUrl] = value.trim() }
    suspend fun setShowSoloCard(value: Boolean) = edit { it[Keys.showSoloCard] = value }
    suspend fun setShowProfitCard(value: Boolean) = edit { it[Keys.showProfitCard] = value }
    suspend fun setAppLockEnabled(value: Boolean) = edit { it[Keys.appLockEnabled] = value }
    suspend fun setThemeMode(value: hi3.hashkit.ui.theme.ThemeMode) = edit { it[Keys.themeMode] = value.name }
    suspend fun setThemeColor(value: hi3.hashkit.ui.theme.ThemeColor) = edit { it[Keys.themeColor] = value.name }
    suspend fun setWallSize(value: hi3.hashkit.ui.wall.WallSize) = edit { it[Keys.wallSize] = value.name }
    suspend fun setWallColumns(value: Int) = edit { it[Keys.wallColumns] = value.coerceIn(0, 8) }
    suspend fun setOnboardingComplete(value: Boolean) = edit { it[Keys.onboardingComplete] = value }

    /** Store the MMP API key encrypted with the Android Keystore; blank clears it. */
    suspend fun setMmpApiKey(plaintext: String) = edit {
        val trimmed = plaintext.trim()
        it[Keys.mmpApiKeyEncrypted] =
            if (trimmed.isEmpty()) "" else hi3.hashkit.core.KeystoreCrypto.encrypt(trimmed)
    }

    /** Decrypt the MMP API key on demand; never surfaced through the settings flow. */
    suspend fun mmpApiKey(): String? =
        context.dataStore.data.first()[Keys.mmpApiKeyEncrypted]
            ?.takeIf { it.isNotBlank() }
            ?.let { hi3.hashkit.core.KeystoreCrypto.decrypt(it) }
    suspend fun setAlertsEnabled(value: Boolean) = edit { it[Keys.alertsEnabled] = value }
    suspend fun setHashrateBelowPercent(value: Double) = edit { it[Keys.thHashBelowPct] = value }
    suspend fun setChipTempThreshold(value: Double) = edit { it[Keys.thChipTempC] = value }
    suspend fun setVrTempThreshold(value: Double) = edit { it[Keys.thVrTempC] = value }
    suspend fun setRejectRateThreshold(value: Double) = edit { it[Keys.thRejectPct] = value }
    suspend fun setCooldownMinutes(value: Long) = edit { it[Keys.thCooldownMin] = value }
    suspend fun setWebhookType(value: hi3.hashkit.data.alerts.WebhookType) = edit { it[Keys.webhookType] = value.name }
    suspend fun setWebhookUrl(value: String) = edit { it[Keys.webhookUrl] = value.trim() }
    suspend fun setWebhookToken(value: String) = edit { it[Keys.webhookToken] = value.trim() }
    suspend fun setWebhookTarget(value: String) = edit { it[Keys.webhookTarget] = value.trim() }

    /** Store the advanced-feature unlock code (validated on read into [AppSettings.advancedUnlocked]). */
    suspend fun setAdvancedUnlockCode(value: String) = edit { it[Keys.advancedUnlockCode] = value.trim() }
    suspend fun setQuietHoursEnabled(value: Boolean) = edit { it[Keys.quietHoursEnabled] = value }
    suspend fun setQuietStartMinute(value: Int) = edit { it[Keys.quietStartMinute] = value.coerceIn(0, 1439) }
    suspend fun setQuietEndMinute(value: Int) = edit { it[Keys.quietEndMinute] = value.coerceIn(0, 1439) }
    suspend fun setDigestEnabled(value: Boolean) = edit { it[Keys.digestEnabled] = value }
    suspend fun setDigestHour(value: Int) = edit { it[Keys.digestHour] = value.coerceIn(0, 23) }
    suspend fun setLastDigestSent(value: Long) = edit { it[Keys.lastDigestSentEpochMs] = value }
    suspend fun setMqttEnabled(value: Boolean) = edit { it[Keys.mqttEnabled] = value }
    suspend fun setMqttHost(value: String) = edit { it[Keys.mqttHost] = value.trim() }
    suspend fun setMqttPort(value: Int) = edit { it[Keys.mqttPort] = value.coerceIn(1, 65535) }
    suspend fun setMqttUsername(value: String) = edit { it[Keys.mqttUsername] = value.trim() }
    suspend fun setMqttBaseTopic(value: String) = edit { it[Keys.mqttBaseTopic] = value.trim().ifBlank { "hi3hashkit" } }
    suspend fun setMqttHaDiscovery(value: Boolean) = edit { it[Keys.mqttHaDiscovery] = value }
    suspend fun setPrometheusEnabled(value: Boolean) = edit { it[Keys.prometheusEnabled] = value }
    suspend fun setPrometheusPort(value: Int) = edit { it[Keys.prometheusPort] = value.coerceIn(1024, 65535) }

    // Solar-surplus / Home Assistant
    suspend fun setHomeAssistantBaseUrl(value: String) = edit { it[Keys.haBaseUrl] = value.trim() }
    suspend fun setHomeAssistantEntityId(value: String) = edit { it[Keys.haEntityId] = value.trim() }
    suspend fun setSolarResumeWatts(value: Double) = edit { it[Keys.solarResumeWatts] = value }
    suspend fun setSolarCurtailWatts(value: Double) = edit { it[Keys.solarCurtailWatts] = value }

    /** Store the Home Assistant long-lived token encrypted with the Android Keystore; blank clears it. */
    suspend fun setHomeAssistantToken(plaintext: String) = edit {
        val trimmed = plaintext.trim()
        it[Keys.haTokenEnc] =
            if (trimmed.isEmpty()) "" else hi3.hashkit.core.KeystoreCrypto.encrypt(trimmed)
    }

    /** Decrypt the Home Assistant token on demand; never surfaced through the settings flow. */
    suspend fun homeAssistantToken(): String? =
        context.dataStore.data.first()[Keys.haTokenEnc]
            ?.takeIf { it.isNotBlank() }
            ?.let { hi3.hashkit.core.KeystoreCrypto.decrypt(it) }

    // Electricity-price curtailment / Octopus Agile
    suspend fun setOctopusRegion(value: String) = edit { it[Keys.octopusRegion] = value.trim().uppercase() }
    suspend fun setPriceResumePence(value: Double) = edit { it[Keys.priceResumePence] = value }
    suspend fun setPriceCurtailPence(value: Double) = edit { it[Keys.priceCurtailPence] = value }

    /** Store the MQTT password encrypted with the Android Keystore; blank clears it. */
    suspend fun setMqttPassword(plaintext: String) = edit {
        val trimmed = plaintext.trim()
        it[Keys.mqttPasswordEnc] =
            if (trimmed.isEmpty()) "" else hi3.hashkit.core.KeystoreCrypto.encrypt(trimmed)
    }

    /** Decrypt the MQTT password on demand; never surfaced through the settings flow. */
    suspend fun mqttPassword(): String? =
        context.dataStore.data.first()[Keys.mqttPasswordEnc]
            ?.takeIf { it.isNotBlank() }
            ?.let { hi3.hashkit.core.KeystoreCrypto.decrypt(it) }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }
}
