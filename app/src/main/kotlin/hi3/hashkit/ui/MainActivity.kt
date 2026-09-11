package hi3.hashkit.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dagger.hilt.android.AndroidEntryPoint
import hi3.hashkit.core.AppLockManager
import hi3.hashkit.data.poll.PollingEngine
import hi3.hashkit.data.prefs.SettingsRepository
import hi3.hashkit.discovery.AutoScanManager
import hi3.hashkit.ui.dashboard.DashboardScreen
import hi3.hashkit.ui.detail.MinerDetailScreen
import hi3.hashkit.ui.discovery.AddMinerScreen
import hi3.hashkit.ui.theme.Hi3MinerWatchTheme
import hi3.hashkit.ui.theme.HiBrand
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var pollingEngine: PollingEngine

    @Inject
    lateinit var appLockManager: AppLockManager

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var autoScanManager: AutoScanManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch {
            val settings = settingsRepository.current()
            if (savedInstanceState == null && settings.appLockEnabled) {
                appLockManager.lockOnLaunch()
                showUnlockPrompt()
            }
            // Kick off the local-subnet scan at launch so miners are discovered while the
            // user reads the dashboard — but never behind the lock screen or onboarding.
            if (savedInstanceState == null && settings.onboardingComplete && !settings.appLockEnabled) {
                autoScanManager.startIfEnabled()
            }
            // Ensure the always-on safety monitor is running if the user enabled it.
            if (settings.safetyServiceEnabled) {
                hi3.hashkit.data.poll.SafetyMonitorService.start(this@MainActivity)
            }
        }
        setContent {
            val settings by settingsRepository.settings.collectAsState(
                initial = hi3.hashkit.data.prefs.AppSettings()
            )
            Hi3MinerWatchTheme(themeMode = settings.themeMode, themeColor = settings.themeColor) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PollingLifecycle()
                    LockLifecycle()
                    val locked by appLockManager.locked.collectAsState()
                    when {
                        locked -> LockScreen(onUnlock = { showUnlockPrompt() })
                        !settings.onboardingComplete -> hi3.hashkit.ui.onboarding.OnboardingScreen(
                            onDone = {
                                lifecycleScope.launch { settingsRepository.setOnboardingComplete(true) }
                                autoScanManager.startIfEnabled()
                                if (android.os.Build.VERSION.SDK_INT >= 33) {
                                    requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 0)
                                }
                            },
                        )
                        else -> AppNavHost(onExit = {
                            pollingEngine.stop()
                            finishAndRemoveTask()
                        })
                    }
                }
            }
        }
    }

    private fun showUnlockPrompt() {
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    appLockManager.unlock()
                    // Start discovery once, after the app is actually unlocked.
                    autoScanManager.startIfEnabled()
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Hi3 Hashkit")
                .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
                .build()
        )
    }

    /** Track background time so the app relocks after the grace period. */
    @Composable
    private fun LockLifecycle() {
        val owner = LocalLifecycleOwner.current
        DisposableEffect(owner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> appLockManager.onBackground()
                    Lifecycle.Event.ON_START -> lifecycleScope.launch {
                        val enabled = settingsRepository.settings.first().appLockEnabled
                        val wasLocked = appLockManager.locked.value
                        appLockManager.onForeground(enabled)
                        if (!wasLocked && appLockManager.locked.value) showUnlockPrompt()
                    }
                    else -> Unit
                }
            }
            owner.lifecycle.addObserver(observer)
            onDispose { owner.lifecycle.removeObserver(observer) }
        }
    }

    @Composable
    private fun LockScreen(onUnlock: () -> Unit) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            hi3.hashkit.ui.theme.HiLogo(markSize = 40.dp, fontSize = 28.sp)
            Spacer(Modifier.height(24.dp))
            Button(onClick = onUnlock) { Text("Unlock") }
        }
    }

    companion object {
        fun canUseAppLock(activity: FragmentActivity): Boolean =
            BiometricManager.from(activity)
                .canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL) ==
                BiometricManager.BIOMETRIC_SUCCESS
    }

    /** Poll only while the app is visible — no pretend background monitoring in v1. */
    @Composable
    private fun PollingLifecycle() {
        val owner = LocalLifecycleOwner.current
        DisposableEffect(owner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> pollingEngine.start(lifecycleScope)
                    Lifecycle.Event.ON_STOP -> pollingEngine.stop()
                    else -> Unit
                }
            }
            owner.lifecycle.addObserver(observer)
            onDispose {
                owner.lifecycle.removeObserver(observer)
                pollingEngine.stop()
            }
        }
    }
}

@Composable
private fun AppNavHost(onExit: () -> Unit) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "dashboard") {
        composable("dashboard") {
            DashboardScreen(
                onMinerClick = { id -> nav.navigate("miner/$id") },
                onAddMiner = { nav.navigate("add") },
                onAlerts = { nav.navigate("alerts") },
                onSettings = { nav.navigate("settings") },
                onFlow = { nav.navigate("flow") },
                onNetworkScan = { nav.navigate("network") },
                onAbout = { nav.navigate("about") },
                onPrivacy = { nav.navigate("privacy") },
                onFleet = { nav.navigate("fleet") },
                onLeaderboard = { nav.navigate("leaderboard") },
                onWall = { nav.navigate("wall") },
                onTable = { nav.navigate("table") },
                onAdvanced = { nav.navigate("advanced") },
                onExit = onExit,
            )
        }
        composable("advanced") {
            val context = androidx.compose.ui.platform.LocalContext.current
            hi3.hashkit.ui.advanced.AdvancedScreen(
                onBack = { nav.popBackStack() },
                onSchedules = { nav.navigate("schedules") },
                onFarms = { nav.navigate("farms") },
                onAddressBook = { nav.navigate("addressbook") },
                onRack = { nav.navigate("rack") },
                onPoolSpeed = { nav.navigate("poolspeed") },
                onRules = { nav.navigate("rules") },
                onHeatReuse = { nav.navigate("heatreuse") },
                onSolar = { nav.navigate("solar") },
                onPriceCurtailment = { nav.navigate("price") },
                onAcoustic = { nav.navigate("acoustic") },
                onArOverlay = { nav.navigate("ar") },
                onNfcProgram = { nav.navigate("nfcprog") },
                onLiveBitcoin = {
                    runCatching {
                        context.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://hi3.cc/bitcoin"),
                            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                },
            )
        }
        composable("heatreuse") {
            hi3.hashkit.ui.heat.HeatReuseScreen(onBack = { nav.popBackStack() })
        }
        composable("solar") {
            hi3.hashkit.ui.solar.SolarSurplusScreen(onBack = { nav.popBackStack() })
        }
        composable("price") {
            hi3.hashkit.ui.price.PriceCurtailmentScreen(onBack = { nav.popBackStack() })
        }
        composable("acoustic") {
            hi3.hashkit.ui.acoustic.AcousticScreen(onBack = { nav.popBackStack() })
        }
        composable("ar") {
            hi3.hashkit.ui.ar.ArOverlayScreen(
                onBack = { nav.popBackStack() },
                onMinerClick = { id -> nav.navigate("miner/$id") },
            )
        }
        composable("leaderboard") {
            hi3.hashkit.ui.leaderboard.LeaderboardScreen(
                onBack = { nav.popBackStack() },
                onMinerClick = { id -> nav.navigate("miner/$id") },
            )
        }
        composable("fleet") {
            hi3.hashkit.ui.dashboard.FleetDetailScreen(
                onBack = { nav.popBackStack() },
                onMinerClick = { id -> nav.navigate("miner/$id") },
            )
        }
        composable("schedules") {
            hi3.hashkit.ui.schedules.SchedulesScreen(onBack = { nav.popBackStack() })
        }
        composable("rules") {
            hi3.hashkit.ui.rules.RulesScreen(onBack = { nav.popBackStack() })
        }
        composable("addressbook") {
            hi3.hashkit.ui.addressbook.AddressBookScreen(onBack = { nav.popBackStack() })
        }
        composable("poolspeed") {
            hi3.hashkit.ui.poolspeed.PoolSpeedScreen(onBack = { nav.popBackStack() })
        }
        composable("rack") {
            hi3.hashkit.ui.rack.RackScreen(
                onBack = { nav.popBackStack() },
                onMinerClick = { id -> nav.navigate("miner/$id") },
            )
        }
        composable("table") {
            hi3.hashkit.ui.table.TableScreen(
                onBack = { nav.popBackStack() },
                onMinerClick = { id -> nav.navigate("miner/$id") },
                onProgramNfc = { ids -> nav.navigate("nfcprog?ids=${ids.joinToString(",")}") },
            )
        }
        composable(
            route = "nfcprog?ids={ids}",
            arguments = listOf(navArgument("ids") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) {
            hi3.hashkit.ui.nfcprog.NfcProgramScreen(onBack = { nav.popBackStack() })
        }
        composable("wall") {
            hi3.hashkit.ui.wall.WallScreen(onExit = { nav.popBackStack() })
        }
        composable("network") {
            hi3.hashkit.ui.network.NetworkScanScreen(onBack = { nav.popBackStack() })
        }
        composable("farms") {
            hi3.hashkit.ui.farms.FarmsScreen(onBack = { nav.popBackStack() })
        }
        composable("about") {
            hi3.hashkit.ui.about.AboutScreen(onBack = { nav.popBackStack() })
        }
        composable("privacy") {
            hi3.hashkit.ui.privacy.PrivacyPolicyScreen(onBack = { nav.popBackStack() })
        }
        composable("flow") {
            hi3.hashkit.ui.flow.FlowScreen(
                onBack = { nav.popBackStack() },
                onMinerClick = { id -> nav.navigate("miner/$id") },
            )
        }
        composable("alerts") {
            hi3.hashkit.ui.alerts.AlertsScreen(onBack = { nav.popBackStack() })
        }
        composable("settings") {
            hi3.hashkit.ui.settings.SettingsScreen(onBack = { nav.popBackStack() })
        }
        composable(
            route = "miner/{minerId}",
            arguments = listOf(navArgument("minerId") { type = NavType.LongType }),
        ) { entry ->
            MinerDetailScreen(
                onBack = { nav.popBackStack() },
                onLogs = {
                    val id = entry.arguments?.getLong("minerId") ?: return@MinerDetailScreen
                    nav.navigate("miner/$id/logs")
                },
                onAutotune = {
                    val id = entry.arguments?.getLong("minerId") ?: return@MinerDetailScreen
                    nav.navigate("miner/$id/autotune")
                },
            )
        }
        composable(
            route = "miner/{minerId}/logs",
            arguments = listOf(navArgument("minerId") { type = NavType.LongType }),
        ) {
            hi3.hashkit.ui.logs.LogsScreen(onBack = { nav.popBackStack() })
        }
        composable(
            route = "miner/{minerId}/autotune",
            arguments = listOf(navArgument("minerId") { type = NavType.LongType }),
        ) {
            hi3.hashkit.ui.autotune.AutotuneScreen(onBack = { nav.popBackStack() })
        }
        composable("add") {
            AddMinerScreen(onDone = { nav.popBackStack() })
        }
    }
}
