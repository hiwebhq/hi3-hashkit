package hi3.hashkit.integrations.hi3

import hi3.hashkit.data.prefs.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class MmpState(
    val enabled: Boolean = false,
    val lastUpdated: Instant? = null,
    val error: String? = null,
    val summary: MmpClient.FleetSummary? = null,
    val sites: List<MmpClient.SiteRollup> = emptyList(),
)

/**
 * Opt-in MMP fleet view. refresh() is a no-op unless enabled + key configured;
 * the API key is decrypted on demand (Android Keystore) and never cached in state.
 */
@Singleton
class MmpRepository @Inject constructor(
    private val client: MmpClient,
    private val settingsRepository: SettingsRepository,
) {
    private val _state = MutableStateFlow(MmpState())
    val state: StateFlow<MmpState> = _state

    suspend fun refresh() {
        val settings = settingsRepository.current()
        if (!settings.mmpEnabled) {
            _state.value = MmpState(enabled = false)
            return
        }
        val key = settingsRepository.mmpApiKey()
        if (key.isNullOrBlank()) {
            _state.value = MmpState(
                enabled = true,
                error = "Add an MMP API key in Settings (minted in the MMP admin UI).",
            )
            return
        }
        when (val summary = client.fetchFleetSummary(settings.mmpBaseUrl, key)) {
            is MmpClient.MmpResult.Error ->
                _state.value = _state.value.copy(enabled = true, error = summary.message)
            is MmpClient.MmpResult.Ok -> {
                val sites = (client.fetchBySite(settings.mmpBaseUrl, key)
                    as? MmpClient.MmpResult.Ok)?.value.orEmpty()
                _state.value = MmpState(
                    enabled = true,
                    lastUpdated = Instant.now(),
                    error = null,
                    summary = summary.value,
                    sites = sites.sortedByDescending { it.hashrateThs ?: 0.0 },
                )
            }
        }
    }
}
