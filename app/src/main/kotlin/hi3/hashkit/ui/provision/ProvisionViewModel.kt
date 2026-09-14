package hi3.hashkit.ui.provision

import android.net.Network
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hi3.hashkit.adapters.espminer.ParsedSystemInfo
import hi3.hashkit.data.db.SavedPoolEntity
import hi3.hashkit.data.db.SavedPoolDao
import hi3.hashkit.data.provision.BitaxeProvisioner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Wizard state for setting up a factory-fresh Bitaxe from the phone. */
sealed interface ProvisionStep {
    data object Intro : ProvisionStep
    data object Connecting : ProvisionStep
    /** Joined the AP and read the unit; the form is shown. */
    data class Connected(val info: ParsedSystemInfo?) : ProvisionStep
    data object Applying : ProvisionStep
    /** Settings written and the miner told to restart; it is now joining [homeSsid]. */
    data class Done(val homeSsid: String, val hostname: String?) : ProvisionStep
    data class Failed(val message: String) : ProvisionStep
}

@HiltViewModel
class ProvisionViewModel @Inject constructor(
    private val provisioner: BitaxeProvisioner,
    savedPoolDao: SavedPoolDao,
) : ViewModel() {

    private val _step = MutableStateFlow<ProvisionStep>(ProvisionStep.Intro)
    val step: StateFlow<ProvisionStep> = _step

    /** Address book entries offered as one-tap pool choices. */
    val savedPools: StateFlow<List<SavedPoolEntity>> = savedPoolDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val supported: Boolean get() = provisioner.supported

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }

    private var network: Network? = null

    fun connect() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        _step.value = ProvisionStep.Connecting
        viewModelScope.launch {
            val net = provisioner.connect()
            if (net == null) {
                _step.value = ProvisionStep.Failed("No Bitaxe network was joined.")
                return@launch
            }
            network = net
            _step.value = ProvisionStep.Connected(provisioner.readInfo(net))
        }
    }

    fun apply(homeSsid: String, homePassword: String, hostname: String, pool: BitaxeProvisioner.PoolSetup?) {
        val net = network ?: return
        _step.value = ProvisionStep.Applying
        viewModelScope.launch {
            val error = provisioner.provision(
                net, BitaxeProvisioner.WifiSetup(homeSsid, homePassword, hostname.ifBlank { null }), pool,
            )
            provisioner.disconnect()
            network = null
            _step.value = if (error == null) {
                ProvisionStep.Done(homeSsid.trim(), hostname.trim().ifBlank { null })
            } else {
                ProvisionStep.Failed(error)
            }
        }
    }

    fun reset() {
        provisioner.disconnect()
        network = null
        _step.value = ProvisionStep.Intro
    }

    override fun onCleared() {
        provisioner.disconnect()
        super.onCleared()
    }
}
