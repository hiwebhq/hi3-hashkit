package hi3.hashkit.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dagger.hilt.android.AndroidEntryPoint
import hi3.hashkit.data.poll.PollingEngine
import hi3.hashkit.ui.dashboard.DashboardScreen
import hi3.hashkit.ui.detail.MinerDetailScreen
import hi3.hashkit.ui.discovery.AddMinerScreen
import hi3.hashkit.ui.theme.Hi3MinerWatchTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var pollingEngine: PollingEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Hi3MinerWatchTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PollingLifecycle()
                    AppNavHost(onExit = {
                        pollingEngine.stop()
                        finishAndRemoveTask()
                    })
                }
            }
        }
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
                onSchedules = { nav.navigate("schedules") },
                onExit = onExit,
            )
        }
        composable("schedules") {
            hi3.hashkit.ui.schedules.SchedulesScreen(onBack = { nav.popBackStack() })
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
        ) {
            MinerDetailScreen(onBack = { nav.popBackStack() })
        }
        composable("add") {
            AddMinerScreen(onDone = { nav.popBackStack() })
        }
    }
}
