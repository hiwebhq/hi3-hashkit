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
    /** Whether first-run onboarding has been completed. */
    val onboardingComplete: Boolean = false,
    val alertThresholds: AlertThresholds = AlertThresholds(),
    val alertsEnabled: Boolean = false,
    /** Push alerts to a user webhook (ntfy/Gotify/Telegram/generic) so they arrive when closed. */
    val webhookType: hi3.hashkit.data.alerts.WebhookType = hi3.hashkit.data.alerts.WebhookType.NONE,
    val webhookUrl: String = "",
    val webhookToken: String = "",
    val webhookTarget: String = "",
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

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }
}
