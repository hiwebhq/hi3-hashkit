package hi3.hashkit.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hi3.hashkit.R
import hi3.hashkit.core.Units
import hi3.hashkit.domain.model.Miner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import hi3.hashkit.ui.components.Metric
import hi3.hashkit.ui.components.StatusBadge
import hi3.hashkit.ui.components.color
import hi3.hashkit.ui.theme.HiBrand
import hi3.hashkit.ui.util.openUrl
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onMinerClick: (Long) -> Unit,
    onAddMiner: () -> Unit,
    onAlerts: () -> Unit,
    onSettings: () -> Unit,
    onFlow: () -> Unit,
    onNetworkScan: () -> Unit,
    onAbout: () -> Unit,
    onPrivacy: () -> Unit,
    onFleet: () -> Unit,
    onLeaderboard: () -> Unit,
    onWall: () -> Unit,
    onTable: () -> Unit,
    onAdvanced: () -> Unit,
    onScan: () -> Unit,
    onExit: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var logoTaps by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val rescanMessage by viewModel.rescanMessage.collectAsStateWithLifecycle()
    val firmwareLatest by viewModel.firmwareLatest.collectAsStateWithLifecycle()
    val poolState by viewModel.poolState.collectAsStateWithLifecycle()
    val mmpState by viewModel.mmpState.collectAsStateWithLifecycle()
    val fleetTrend by viewModel.fleetTrend.collectAsStateWithLifecycle()
    val fleetWindow by viewModel.fleetWindowMs.collectAsStateWithLifecycle()
    val networkEpoch by viewModel.networkEpoch.collectAsStateWithLifecycle()
    val bests by viewModel.bests.collectAsStateWithLifecycle()
    // Count of AxeOS miners behind the latest release; the banner item only exists when > 0
    // so a disabled banner doesn't add phantom LazyColumn spacing.
    val firmwareOutdated = firmwareLatest?.let { latest ->
        state.miners.count { m ->
            hi3.hashkit.integrations.update.FirmwareUpdateChecker.isAxeOsFamily(m.identity.firmwareFamily) &&
                hi3.hashkit.integrations.update.FirmwareUpdateChecker.isNewer(latest.tag, m.identity.firmwareVersion)
        }
    } ?: 0
    var bulkKind by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<BulkActionKind?>(null)
    }
    var confirmExit by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var menuOpen by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var panicOpen by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    rescanMessage?.let { msg ->
        val ctx = androidx.compose.ui.platform.LocalContext.current
        androidx.compose.runtime.LaunchedEffect(msg) {
            android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_SHORT).show()
            kotlinx.coroutines.delay(2500)
            viewModel.clearRescanMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    hi3.hashkit.ui.theme.HiLogo(
                        markSize = 22.dp,
                        fontSize = 18.sp,
                        modifier = Modifier.clickable(
                            indication = null,
                            interactionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        ) {
                            logoTaps++
                            if (hi3.hashkit.BuildConfig.EASTER_EGG && logoTaps >= 7) {
                                logoTaps = 0
                                context.openUrl("https://www.hi3.cc/bh/pay-bitcoin")
                            } else {
                                onAbout()
                            }
                        }
                    )
                },
                actions = {
                    // Header keeps only Notifications, Setup and Exit; everything else
                    // lives in the overflow menu.
                    IconButton(onClick = onAlerts) {
                        BadgedBox(
                            badge = {
                                if (state.unresolvedAlerts > 0) {
                                    Badge { Text("${state.unresolvedAlerts}") }
                                }
                            }
                        ) {
                            Icon(Icons.Filled.Notifications, contentDescription = stringResource(R.string.dash_cd_notifications))
                        }
                    }
                    if (state.settings.advancedUnlocked) {
                        IconButton(onClick = onScan) {
                            Icon(Icons.Filled.QrCodeScanner, contentDescription = stringResource(R.string.dash_cd_scan_tag))
                        }
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.dash_cd_setup))
                    }
                    IconButton(onClick = {
                        if (state.settings.confirmBeforeExit) confirmExit = true else onExit()
                    }) {
                        Icon(
                            Icons.Filled.PowerSettingsNew,
                            contentDescription = stringResource(R.string.dash_cd_exit_app),
                            tint = HiBrand.statusOffline,
                        )
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.dash_cd_more))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.common_refresh)) },
                                leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                                onClick = { menuOpen = false; viewModel.refreshNow() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.dash_menu_flow_view)) },
                                leadingIcon = { Icon(Icons.Filled.AccountTree, contentDescription = null) },
                                onClick = { menuOpen = false; onFlow() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.dash_menu_network_scan)) },
                                leadingIcon = { Icon(Icons.Filled.Wifi, contentDescription = null) },
                                onClick = { menuOpen = false; onNetworkScan() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.dash_menu_leaderboard)) },
                                leadingIcon = { Icon(Icons.Filled.EmojiEvents, contentDescription = null) },
                                onClick = { menuOpen = false; onLeaderboard() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.dash_menu_fleet_table)) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                                onClick = { menuOpen = false; onTable() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.dash_menu_wall_tv)) },
                                leadingIcon = { Icon(Icons.Filled.Tv, contentDescription = null) },
                                onClick = { menuOpen = false; onWall() },
                            )
                            // Advanced hub: one entry gathering every advanced-gated feature.
                            // Revealed once unlocked in Settings → Advanced features.
                            if (state.settings.advancedUnlocked) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.dash_menu_advanced)) },
                                    leadingIcon = { Icon(Icons.Filled.Tune, contentDescription = null) },
                                    onClick = { menuOpen = false; onAdvanced() },
                                )
                            }
                            androidx.compose.material3.HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.dash_menu_store)) },
                                leadingIcon = { Icon(Icons.Filled.Store, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    context.openUrl("https://hi3btc.printify.me/")
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.dash_menu_about)) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Help, contentDescription = null) },
                                onClick = { menuOpen = false; onAbout() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.dash_menu_privacy_policy)) },
                                leadingIcon = { Icon(Icons.Filled.Shield, contentDescription = null) },
                                onClick = { menuOpen = false; onPrivacy() },
                            )
                            androidx.compose.material3.HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.dash_panic_title), color = HiBrand.statusOffline) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.Warning,
                                        contentDescription = null,
                                        tint = HiBrand.statusOffline,
                                    )
                                },
                                onClick = { menuOpen = false; panicOpen = true },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = HiBrand.background,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddMiner) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.dash_cd_add_miner))
            }
        },
        containerColor = HiBrand.background,
    ) { padding ->
        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { viewModel.refreshNow() },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        // Responsive columns: 1 on phones, 2 on small tablets/landscape, 3 on large.
        val wideCols = when {
            maxWidth >= 1000.dp -> 3
            maxWidth >= 640.dp -> 2
            else -> 1
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().widthIn(max = 1200.dp).align(Alignment.TopCenter),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Pinned to a default farm (Settings → Farms): no selector row — the space
            // goes to the miners.
            if (state.farms.isNotEmpty() && !state.farmPinned) {
                item { FarmSelector(state, viewModel::setActiveFarm) }
            }
            firmwareLatest?.takeIf { firmwareOutdated > 0 }?.let { latest ->
                item { FirmwareUpdateBanner(firmwareOutdated, latest, context) }
            }
            // User-arranged blocks (Settings → Display → Dashboard card order). Each block
            // still renders only when its feature is enabled/applicable.
            DashboardCard.orderFrom(state.settings.dashboardCardOrder).forEach { dashCard ->
            when (dashCard) {
            DashboardCard.FLEET -> item {
                FleetSummary(state, fleetTrend, fleetWindow, viewModel::setFleetWindow, onClick = onFleet)
            }
            DashboardCard.PROFIT -> if (state.settings.showProfitCard) {
                state.profit?.let { profit ->
                    if (profit.revenuePerDay != null || profit.energyKwhPerDay != null) {
                        item { ProfitCard(profit) }
                    }
                }
            }
            DashboardCard.SOLO -> if (state.settings.showSoloCard) {
                state.solo?.let { solo -> item { SoloCard(solo) } }
            }
            DashboardCard.BESTS -> if (bests.isNotEmpty()) {
                item { BestsCard(bests, state.solo?.networkDifficulty, onMinerClick) }
            }
            DashboardCard.HALVING -> networkEpoch?.let { epoch -> item { HalvingCountdownCard(epoch) } }
            DashboardCard.POOL -> if (poolState.enabled) {
                item { Hi3PoolCard(poolState) }
            }
            DashboardCard.MMP -> if (mmpState.enabled) {
                item { MmpCard(mmpState, state) }
            }
            DashboardCard.MINERS -> {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    androidx.compose.material3.OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = viewModel::setSearch,
                        placeholder = { Text(stringResource(R.string.common_search)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    if (state.miners.isNotEmpty()) {
                        IconButton(onClick = { viewModel.rescanLocalNetwork() }) {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = stringResource(R.string.dash_cd_rescan),
                                tint = HiBrand.accent,
                            )
                        }
                    }
                    DensitySelector(
                        current = state.settings.cardDensity,
                        onSelect = viewModel::setDensity,
                    )
                }
            }
            if (state.miners.isEmpty() && state.searchQuery.isBlank()) {
                item { EmptyState(onAddMiner, onEnableDemo = { viewModel.setDemoMode(true) }) }
            } else {
                state.groups.forEach { (group, groupMiners) ->
                    if (state.groups.size > 1 || group != null) {
                        item(key = "group-${group ?: "~none"}") {
                            Text(
                                (group ?: stringResource(R.string.dash_group_ungrouped)).uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = HiBrand.textSecondary,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                    // Multi-column when the user picked GRID or the screen is wide.
                    val cols = when {
                        state.settings.cardDensity == hi3.hashkit.data.prefs.CardDensity.GRID -> maxOf(wideCols, 2)
                        else -> wideCols
                    }
                    if (cols > 1) {
                        items(groupMiners.chunked(cols), key = { it.first().id }) { rowMiners ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                rowMiners.forEach { miner ->
                                    Box(Modifier.weight(1f)) {
                                        MinerCard(
                                            miner = miner,
                                            density = if (state.settings.cardDensity == hi3.hashkit.data.prefs.CardDensity.GRID)
                                                hi3.hashkit.data.prefs.CardDensity.GRID
                                            else hi3.hashkit.data.prefs.CardDensity.MEDIUM,
                                            selected = miner.id in state.selection,
                                            selectionMode = state.selection.isNotEmpty(),
                                            onClick = {
                                                if (state.selection.isNotEmpty()) viewModel.toggleSelect(miner.id)
                                                else onMinerClick(miner.id)
                                            },
                                            onLongClick = { viewModel.toggleSelect(miner.id) },
                                        )
                                    }
                                }
                                repeat(cols - rowMiners.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    } else items(groupMiners, key = { it.id }) { miner ->
                        val spark by viewModel.sparklines.collectAsStateWithLifecycle()
                        MinerCard(
                            miner = miner,
                            density = state.settings.cardDensity,
                            sparkline = spark[miner.id],
                            monthlyCost = hi3.hashkit.domain.solo.HomeEconomics.monthlyCost(
                                miner.lastTelemetry?.powerW?.value, state.settings.electricityRatePerKwh,
                            )?.let { Units.formatMoney(it, state.settings.currencyCode) },
                            selected = miner.id in state.selection,
                            selectionMode = state.selection.isNotEmpty(),
                            onClick = {
                                if (state.selection.isNotEmpty()) viewModel.toggleSelect(miner.id)
                                else onMinerClick(miner.id)
                            },
                            onLongClick = { viewModel.toggleSelect(miner.id) },
                        )
                    }
                }
                if (state.selection.isNotEmpty()) {
                    item {
                        BulkBar(
                            count = state.selection.size,
                            onReboot = { bulkKind = BulkActionKind.REBOOT },
                            onPool = { bulkKind = BulkActionKind.POOL },
                            onFan = { bulkKind = BulkActionKind.FAN },
                            onPause = { bulkKind = BulkActionKind.PAUSE },
                            onResume = { bulkKind = BulkActionKind.RESUME },
                            onClear = viewModel::clearSelection,
                        )
                    }
                }
                if (state.settings.demoModeEnabled) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                stringResource(R.string.dash_demo_banner),
                                style = MaterialTheme.typography.bodySmall,
                                color = HiBrand.statusDegraded,
                            )
                            Switch(
                                checked = true,
                                onCheckedChange = { viewModel.setDemoMode(false) },
                            )
                        }
                    }
                }
            }
            } // MINERS block
            } // when (dashCard)
            } // forEach card in order
        }
        }
        }
    }

    BulkDialogs(
        state = state,
        bulkKind = bulkKind,
        onDismissKind = { bulkKind = null },
        viewModel = viewModel,
    )

    if (confirmExit) {
        var dontAskAgain by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text(stringResource(R.string.dash_exit_title)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.dash_exit_body),
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { dontAskAgain = !dontAskAgain },
                    ) {
                        androidx.compose.material3.Checkbox(
                            checked = dontAskAgain,
                            onCheckedChange = { dontAskAgain = it },
                        )
                        Text(
                            stringResource(R.string.dash_exit_dont_ask),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    if (dontAskAgain) viewModel.setConfirmBeforeExit(false)
                    confirmExit = false
                    onExit()
                }) { Text(stringResource(R.string.dash_exit_confirm), color = HiBrand.statusOffline) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmExit = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }

    if (panicOpen) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { panicOpen = false },
            title = { Text(stringResource(R.string.dash_panic_title)) },
            text = {
                Text(
                    stringResource(R.string.dash_panic_body),
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    panicOpen = false
                    viewModel.planPanic(hi3.hashkit.data.repo.BulkAction.Power(hi3.hashkit.domain.adapter.PowerAction.PAUSE))
                }) { Text(stringResource(R.string.dash_pause_all)) }
            },
            dismissButton = {
                Row {
                    androidx.compose.material3.TextButton(onClick = {
                        panicOpen = false
                        viewModel.planPanic(hi3.hashkit.data.repo.BulkAction.Reboot)
                    }) { Text(stringResource(R.string.dash_reboot_all), color = HiBrand.statusOffline) }
                    androidx.compose.material3.TextButton(onClick = { panicOpen = false }) { Text(stringResource(R.string.common_cancel)) }
                }
            },
        )
    }
}

/**
 * Dedicated fleet view: just every miner's card — no summary card and none of the
 * pool/MMP/solo/search sections of the dashboard. Reached by tapping the Fleet card.
 */
@Suppress("LongMethod") // declarative screen layout: scaffold + adaptive card grid
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FleetDetailScreen(
    onBack: () -> Unit,
    onMinerClick: (Long) -> Unit,
    onFlow: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spark by viewModel.sparklines.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dash_fleet_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(onClick = onFlow) {
                        Icon(
                            Icons.Filled.AccountTree,
                            contentDescription = stringResource(R.string.dash_menu_flow_view),
                        )
                    }
                    DensitySelector(
                        current = state.settings.cardDensity,
                        onSelect = viewModel::setDensity,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = HiBrand.background),
            )
        },
        containerColor = HiBrand.background,
    ) { padding ->
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val wideCols = when {
                maxWidth >= 1000.dp -> 3
                maxWidth >= 640.dp -> 2
                else -> 1
            }
            // Same 4 display formats as the main dashboard: GRID packs into a >=2-col grid;
            // LARGE/MEDIUM/COMPACT flow one-per-row (multi-column on wide screens).
            val cols = when {
                state.settings.cardDensity == hi3.hashkit.data.prefs.CardDensity.GRID -> maxOf(wideCols, 2)
                else -> wideCols
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize().widthIn(max = 1200.dp).align(Alignment.TopCenter),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (cols > 1) {
                    items(state.miners.chunked(cols), key = { it.first().id }) { rowMiners ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            rowMiners.forEach { miner ->
                                Box(Modifier.weight(1f)) {
                                    MinerCard(
                                        miner = miner,
                                        density = if (state.settings.cardDensity == hi3.hashkit.data.prefs.CardDensity.GRID)
                                            hi3.hashkit.data.prefs.CardDensity.GRID
                                        else hi3.hashkit.data.prefs.CardDensity.MEDIUM,
                                        selected = false,
                                        selectionMode = false,
                                        onClick = { onMinerClick(miner.id) },
                                        onLongClick = { onMinerClick(miner.id) },
                                    )
                                }
                            }
                            repeat(cols - rowMiners.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                } else items(state.miners, key = { it.id }) { miner ->
                    MinerCard(
                        miner = miner,
                        density = state.settings.cardDensity,
                        sparkline = spark[miner.id],
                        selected = false,
                        selectionMode = false,
                        onClick = { onMinerClick(miner.id) },
                        onLongClick = { onMinerClick(miner.id) },
                    )
                }
            }
        }
    }
}

/** Opt-in AxeOS firmware-update notice: shows when any Bitaxe is behind the latest release. */
@Composable
private fun FirmwareUpdateBanner(
    outdated: Int,
    latest: hi3.hashkit.integrations.update.FirmwareUpdateChecker.Release,
    context: android.content.Context,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = HiBrand.surface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().clickable { context.openUrl(latest.url) },
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Refresh, contentDescription = null, tint = HiBrand.accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(R.string.dash_firmware_banner, latest.tag, outdated),
                style = MaterialTheme.typography.bodySmall,
                color = HiBrand.textPrimary,
            )
        }
    }
}

/** Compact farm/site switcher: taps open a menu of farms plus "All farms". */
@Composable
private fun FarmSelector(state: DashboardUiState, onSelect: (Long) -> Unit) {
    var open by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val activeName = state.farms.firstOrNull { it.id == state.activeFarmId }?.name
        ?: stringResource(R.string.dash_all_farms)
    Box {
        androidx.compose.material3.AssistChip(
            onClick = { open = true },
            label = { Text(activeName) },
            leadingIcon = { Icon(Icons.Filled.Warehouse, contentDescription = stringResource(R.string.dash_cd_farm), modifier = Modifier.size(18.dp)) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.dash_all_farms)) },
                onClick = { open = false; onSelect(-1) },
            )
            state.farms.forEach { farm ->
                DropdownMenuItem(
                    text = { Text(farm.name) },
                    onClick = { open = false; onSelect(farm.id) },
                )
            }
        }
    }
}

@Composable
private fun BulkDialogs(
    state: DashboardUiState,
    bulkKind: BulkActionKind?,
    onDismissKind: () -> Unit,
    viewModel: DashboardViewModel,
) {
    val bulk = state.bulk
    when {
        bulk.outcomes != null -> BulkResultsDialog(
            outcomes = bulk.outcomes,
            skippedCount = bulk.plan?.skipped?.size ?: 0,
            onDismiss = { viewModel.clearSelection() },
        )
        bulk.plan != null -> BulkPlanDialog(
            plan = bulk.plan,
            running = bulk.running,
            onExecute = viewModel::executeBulk,
            onDismiss = viewModel::dismissBulk,
        )
        bulkKind != null -> BulkParamsDialog(
            kind = bulkKind,
            onPlan = { action ->
                onDismissKind()
                viewModel.planBulk(action)
            },
            onDismiss = onDismissKind,
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun BulkBar(
    count: Int,
    onReboot: () -> Unit,
    onPool: () -> Unit,
    onFan: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onClear: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = HiBrand.surfaceRaised),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                stringResource(R.string.dash_bulk_selected, count),
                style = MaterialTheme.typography.labelSmall,
                color = HiBrand.textSecondary,
            )
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                androidx.compose.material3.OutlinedButton(onClick = onReboot) { Text(stringResource(R.string.dash_bulk_restart)) }
                androidx.compose.material3.OutlinedButton(onClick = onPool) { Text(stringResource(R.string.dash_bulk_pool)) }
                androidx.compose.material3.OutlinedButton(onClick = onFan) { Text(stringResource(R.string.dash_bulk_fan)) }
                androidx.compose.material3.OutlinedButton(onClick = onPause) { Text(stringResource(R.string.dash_bulk_pause)) }
                androidx.compose.material3.OutlinedButton(onClick = onResume) { Text(stringResource(R.string.dash_bulk_resume)) }
                androidx.compose.material3.TextButton(onClick = onClear) { Text(stringResource(R.string.dash_bulk_clear)) }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun FleetSummary(
    state: DashboardUiState,
    trend: List<hi3.hashkit.data.repo.FleetTrendPoint>,
    windowMs: Long,
    onWindow: (Long) -> Unit,
    onClick: (() -> Unit)? = null,
) {
    val totals = state.totals
    Card(
        colors = CardDefaults.cardColors(containerColor = HiBrand.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.dash_fleet_header), style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary)
                Text(
                    lastRefreshLabel(state.lastRefresh),
                    style = MaterialTheme.typography.labelSmall,
                    color = HiBrand.textSecondary,
                )
            }
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    Units.formatHashrate(totals?.totalHashrateGhs),
                    style = MaterialTheme.typography.headlineMedium,
                    color = HiBrand.accent,
                    fontWeight = FontWeight.Bold,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 4.dp),
                ) {
                    Counter(totals?.online ?: 0, stringResource(R.string.dash_counter_on), HiBrand.statusOnline)
                    (totals?.degraded ?: 0).takeIf { it > 0 }?.let { Counter(it, stringResource(R.string.dash_counter_deg), HiBrand.statusDegraded) }
                    (totals?.offline ?: 0).takeIf { it > 0 }?.let { Counter(it, stringResource(R.string.dash_counter_off), HiBrand.statusOffline) }
                    (totals?.unknown ?: 0).takeIf { it > 0 }?.let { Counter(it, stringResource(R.string.dash_counter_stale), HiBrand.statusUnknown) }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Metric(stringResource(R.string.dash_metric_power), Units.formatPower(totals?.totalMeasuredPowerW))
                Metric(stringResource(R.string.dash_metric_efficiency), Units.formatEfficiency(totals?.fleetEfficiencyJTh))
                Metric(stringResource(R.string.dash_metric_hottest), Units.formatTemp(totals?.hottestChipC, state.settings.useFahrenheit))
                totals?.dailyCost?.let { cost ->
                    Metric(stringResource(R.string.dash_metric_energy_est), Units.formatMoney(cost, state.settings.currencyCode))
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(R.string.dash_fleet_hashrate),
                    style = MaterialTheme.typography.labelSmall,
                    color = HiBrand.textSecondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("1h" to 3_600_000L, "6h" to 21_600_000L, "24h" to 86_400_000L).forEach { (label, ms) ->
                        FilterChip(
                            selected = windowMs == ms,
                            onClick = { onWindow(ms) },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            if (trend.size >= 2) {
                FleetTrendChart(trend, Modifier.fillMaxWidth().height(72.dp))
            } else {
                Text(
                    stringResource(R.string.dash_collecting_data),
                    style = MaterialTheme.typography.labelSmall,
                    color = HiBrand.textSecondary,
                    modifier = Modifier.height(72.dp),
                )
            }
        }
    }
}

@Composable
private fun FleetTrendChart(
    trend: List<hi3.hashkit.data.repo.FleetTrendPoint>,
    modifier: Modifier = Modifier,
) {
    val chartDescription = stringResource(R.string.dash_cd_fleet_trend_chart)
    androidx.compose.foundation.Canvas(
        modifier.semantics { contentDescription = chartDescription },
    ) {
        val minT = trend.first().timeMs
        val maxT = trend.last().timeMs
        val spanT = (maxT - minT).coerceAtLeast(1)
        val maxV = (trend.maxOf { it.totalGhs } * 1.1).coerceAtLeast(1.0)
        fun x(t: Long) = (t - minT).toFloat() / spanT * size.width
        fun y(v: Double) = size.height - (v / maxV).toFloat() * size.height

        val line = androidx.compose.ui.graphics.Path()
        val area = androidx.compose.ui.graphics.Path()
        trend.forEachIndexed { i, p ->
            val px = x(p.timeMs); val py = y(p.totalGhs)
            if (i == 0) { line.moveTo(px, py); area.moveTo(px, size.height); area.lineTo(px, py) }
            else { line.lineTo(px, py); area.lineTo(px, py) }
        }
        area.lineTo(x(maxT), size.height)
        area.close()
        drawPath(area, HiBrand.accent.copy(alpha = 0.15f))
        drawPath(
            line,
            color = HiBrand.accent,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 3f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            ),
        )
    }
}

@Composable
private fun MmpCard(mmp: hi3.hashkit.integrations.hi3.MmpState, dash: DashboardUiState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = HiBrand.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.dash_mmp_header), style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary)
                Text(
                    mmp.lastUpdated?.let {
                        stringResource(R.string.dash_updated_ago, java.time.Duration.between(it, Instant.now()).seconds)
                    } ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = HiBrand.textSecondary,
                )
            }
            Spacer(Modifier.height(8.dp))
            mmp.error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = HiBrand.statusDegraded)
                return@Column
            }
            val s = mmp.summary ?: return@Column
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                Metric(
                    stringResource(R.string.dash_metric_mmp_hashrate),
                    Units.formatHashrate(s.hashrateThs?.times(1000.0)),
                    valueColor = HiBrand.accentAlt,
                )
                Metric(stringResource(R.string.status_online), "${s.online ?: "—"}/${s.installed ?: "—"}")
                Metric(stringResource(R.string.dash_metric_power), s.powerKw?.let { Units.formatPower(it * 1000.0) } ?: "—")
                s.realizationPct?.let { Metric(stringResource(R.string.dash_metric_realization), "${it.toInt()}%") }
            }
            if ((s.needsAttention ?: 0) > 0 || (s.zeroHash ?: 0) > 0) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    s.needsAttention?.takeIf { it > 0 }?.let {
                        Metric(stringResource(R.string.dash_metric_attention), "$it", valueColor = HiBrand.statusDegraded)
                    }
                    s.zeroHash?.takeIf { it > 0 }?.let {
                        Metric(stringResource(R.string.dash_metric_zero_hash), "$it", valueColor = HiBrand.statusOffline)
                    }
                }
            }
            if (mmp.sites.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                mmp.sites.forEach { site ->
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    ) {
                        Text(site.siteName, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text(
                            "${site.online ?: "?"}/${site.installed ?: "?"} · " +
                                Units.formatHashrate(site.hashrateThs?.times(1000.0)),
                            style = MaterialTheme.typography.bodySmall,
                            color = HiBrand.textSecondary,
                        )
                    }
                }
            }
            dash.totals?.let { totals ->
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(
                        R.string.dash_mmp_local_vs,
                        Units.formatHashrate(totals.totalHashrateGhs),
                        Units.formatHashrate(s.hashrateThs?.times(1000.0)),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = HiBrand.textSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun Hi3PoolCard(pool: hi3.hashkit.integrations.hi3.Hi3PoolState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = HiBrand.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(pool.poolType.displayName.uppercase(), style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary)
                Text(
                    pool.lastUpdated?.let {
                        stringResource(R.string.dash_pool_updated_ago, java.time.Duration.between(it, Instant.now()).seconds)
                    } ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = HiBrand.textSecondary,
                )
            }
            Spacer(Modifier.height(8.dp))
            pool.error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = HiBrand.statusDegraded)
                return@Column
            }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Metric(stringResource(R.string.dash_metric_pool_hashrate), Units.formatHashrate(pool.totalPoolHashrateGhs), valueColor = HiBrand.accentAlt)
                Metric(stringResource(R.string.dash_metric_workers), "${pool.workersCount}")
                pool.blockHeight?.let { Metric(stringResource(R.string.dash_metric_height), "$it") }
            }
            if (pool.aggregatedViaProxy) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.dash_pool_proxy_note, pool.workersCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = HiBrand.textSecondary,
                )
            }
            if (pool.comparisons.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    if (pool.aggregatedViaProxy) stringResource(R.string.dash_pool_fleet_vs_pool)
                    else stringResource(R.string.dash_pool_miner_vs_pool),
                    style = MaterialTheme.typography.labelSmall,
                    color = HiBrand.textSecondary,
                )
                Spacer(Modifier.height(4.dp))
                pool.comparisons.forEach { c ->
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    ) {
                        Text(
                            c.localMinerName ?: stringResource(R.string.dash_pool_no_local_match, c.poolWorkerName),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${Units.formatHashrate(c.localHashrateGhs)} → ${Units.formatHashrate(c.poolHashrateGhs)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = HiBrand.textSecondary,
                        )
                        c.deltaPercent?.let { delta ->
                            Text(
                                String.format(java.util.Locale.US, " %+.0f%%", delta),
                                style = MaterialTheme.typography.bodySmall,
                                color = when {
                                    delta < -25 -> HiBrand.statusOffline
                                    delta < -10 -> HiBrand.statusDegraded
                                    else -> HiBrand.statusOnline
                                },
                            )
                        }
                    }
                }
            }
            if (pool.unmatchedLocal.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.dash_pool_not_seen, pool.unmatchedLocal.joinToString(", ")),
                    style = MaterialTheme.typography.labelSmall,
                    color = HiBrand.statusDegraded,
                )
            }
        }
    }
}

@Composable
private fun ProfitCard(p: hi3.hashkit.ui.dashboard.ProfitSummary) {
    fun money(v: Double?): String = Units.formatMoney(v, p.currencyCode)
    Card(
        colors = CardDefaults.cardColors(containerColor = HiBrand.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.dash_profit_header), style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Metric(stringResource(R.string.dash_metric_revenue_day), money(p.revenuePerDay))
                Metric(stringResource(R.string.dash_metric_power_cost_day), money(p.costPerDay))
                Metric(
                    stringResource(R.string.dash_metric_net_day),
                    money(p.profitPerDay),
                    valueColor = when {
                        (p.profitPerDay ?: 0.0) > 0 -> HiBrand.statusOnline
                        (p.profitPerDay ?: 0.0) < 0 -> HiBrand.statusOffline
                        else -> HiBrand.accent
                    },
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Metric(stringResource(R.string.dash_metric_btc_day), p.btcPerDay?.let { "%.8f".format(it) } ?: "—")
                Metric(stringResource(R.string.dash_metric_energy_day), p.energyKwhPerDay?.let { "%.1f kWh".format(it) } ?: "—")
                Metric(stringResource(R.string.dash_metric_heat), p.heatBtuPerHour?.let { "%,.0f BTU/hr".format(it) } ?: "—")
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(
                    R.string.dash_profit_estimate_note,
                    "%.3f".format(hi3.hashkit.domain.solo.ProfitMath.BLOCK_SUBSIDY_BTC),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = HiBrand.textSecondary,
            )
        }
    }
}

/** Fleet trophy shelf: the best shares any of your miners ever found. */
@Composable
private fun BestsCard(
    bests: List<hi3.hashkit.data.db.PersonalBestRow>,
    networkDifficultyNow: Double?,
    onMinerClick: (Long) -> Unit,
) {
    val dateFormat = androidx.compose.runtime.remember {
        java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM)
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = HiBrand.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.dash_bests_header),
                style = MaterialTheme.typography.labelSmall,
                color = HiBrand.textSecondary,
            )
            Spacer(Modifier.height(8.dp))
            bests.forEachIndexed { i, best ->
                val pct = hi3.hashkit.domain.solo.PersonalBests
                    .percentOfBlock(best.difficulty, best.networkDifficulty ?: networkDifficultyNow)
                    ?.let { hi3.hashkit.domain.solo.PersonalBests.formatPercent(it) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onMinerClick(best.minerId) }
                        .padding(vertical = 4.dp),
                ) {
                    Text(if (i == 0) "🏆" else "🏅", modifier = Modifier.padding(end = 10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            best.minerName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            listOfNotNull(
                                pct?.let { stringResource(R.string.det_bests_pct_block, it) },
                                dateFormat.format(java.util.Date(best.atEpochMs)),
                            ).joinToString("  ·  "),
                            style = MaterialTheme.typography.labelSmall,
                            color = HiBrand.textSecondary,
                        )
                    }
                    Text(
                        Units.formatDifficulty(best.difficulty),
                        style = MaterialTheme.typography.titleMedium,
                        color = HiBrand.accent,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun SoloCard(solo: hi3.hashkit.ui.dashboard.SoloSummary) {
    Card(
        colors = CardDefaults.cardColors(containerColor = HiBrand.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.dash_solo_header), style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Metric(stringResource(R.string.dash_metric_day), hi3.hashkit.domain.solo.SoloMiningMath.formatProbability(solo.pDay))
                Metric(stringResource(R.string.dash_metric_week), hi3.hashkit.domain.solo.SoloMiningMath.formatProbability(solo.pWeek))
                Metric(stringResource(R.string.dash_metric_month), hi3.hashkit.domain.solo.SoloMiningMath.formatProbability(solo.pMonth))
                Metric(stringResource(R.string.dash_metric_year), hi3.hashkit.domain.solo.SoloMiningMath.formatProbability(solo.pYear))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(
                    R.string.dash_solo_expected_time,
                    hi3.hashkit.domain.solo.SoloMiningMath.formatExpectedTime(solo.expectedSeconds),
                ) +
                    solo.bestDifficulty?.let {
                        stringResource(R.string.dash_solo_best_diff_suffix, Units.formatDifficulty(it))
                    }.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = HiBrand.textSecondary,
            )
            Text(
                stringResource(R.string.dash_solo_disclaimer),
                style = MaterialTheme.typography.labelSmall,
                color = HiBrand.textSecondary,
            )
        }
    }
}

@Composable
private fun HalvingCountdownCard(epoch: hi3.hashkit.data.repo.DifficultyRepository.NetworkEpoch) {
    val math = hi3.hashkit.domain.model.HalvingMath
    val blocksToHalving = math.blocksToHalving(epoch.currentHeight)
    Card(
        colors = CardDefaults.cardColors(containerColor = HiBrand.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.dash_network_header), style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Metric(stringResource(R.string.dash_metric_block), "%,d".format(epoch.currentHeight))
                Metric(stringResource(R.string.dash_metric_subsidy), "%.3f BTC".format(math.subsidyBtc(epoch.currentHeight)))
            }
            Spacer(Modifier.height(10.dp))
            val changeSign = if (epoch.difficultyChangePercent >= 0) "+" else ""
            Text(
                stringResource(
                    R.string.dash_next_adjustment,
                    "${epoch.remainingBlocks}",
                    math.humanDuration(epoch.remainingTimeMs),
                    changeSign,
                    "%.1f".format(epoch.difficultyChangePercent),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = HiBrand.textPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(
                    R.string.dash_next_halving,
                    "%,d".format(math.nextHalvingBlock(epoch.currentHeight)),
                    "$blocksToHalving",
                    math.humanDuration(math.timeToHalvingMs(epoch.currentHeight)),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = HiBrand.textPrimary,
            )
            Text(
                stringResource(R.string.dash_halving_note),
                style = MaterialTheme.typography.labelSmall,
                color = HiBrand.textSecondary,
            )
        }
    }
}

@Composable
private fun Counter(count: Int, label: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(
            Modifier
                .height(8.dp)
                .background(color, RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp)
        )
        Text(
            "$count $label",
            style = MaterialTheme.typography.labelMedium,
            color = HiBrand.textSecondary,
        )
    }
}

@Composable
private fun lastRefreshLabel(instant: Instant?): String {
    if (instant == null) return stringResource(R.string.dash_no_refresh_yet)
    val secs = Duration.between(instant, Instant.now()).seconds
    return when {
        secs < 5 -> stringResource(R.string.dash_just_now)
        secs < 120 -> stringResource(R.string.dash_ago_seconds, secs)
        else -> stringResource(R.string.dash_ago_minutes_stale, secs / 60)
    }
}

/** Single button showing the current card style; each tap cycles L → M → C → ▦. */
@Composable
private fun DensitySelector(
    current: hi3.hashkit.data.prefs.CardDensity,
    onSelect: (hi3.hashkit.data.prefs.CardDensity) -> Unit,
) {
    val label = when (current) {
        hi3.hashkit.data.prefs.CardDensity.LARGE -> "L"
        hi3.hashkit.data.prefs.CardDensity.MEDIUM -> "M"
        hi3.hashkit.data.prefs.CardDensity.COMPACT -> "C"
        hi3.hashkit.data.prefs.CardDensity.GRID -> "▦"
    }
    val next = when (current) {
        hi3.hashkit.data.prefs.CardDensity.LARGE -> hi3.hashkit.data.prefs.CardDensity.MEDIUM
        hi3.hashkit.data.prefs.CardDensity.MEDIUM -> hi3.hashkit.data.prefs.CardDensity.COMPACT
        hi3.hashkit.data.prefs.CardDensity.COMPACT -> hi3.hashkit.data.prefs.CardDensity.GRID
        hi3.hashkit.data.prefs.CardDensity.GRID -> hi3.hashkit.data.prefs.CardDensity.LARGE
    }
    val styleDescription = stringResource(R.string.dash_cd_card_style, label)
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = HiBrand.accent,
        modifier = Modifier
            .background(HiBrand.accent.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            .clickable { onSelect(next) }
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics { contentDescription = styleDescription },
    )
}

@Suppress("LongMethod", "CyclomaticComplexMethod") // one declarative layout per card density
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun MinerCard(
    miner: Miner,
    density: hi3.hashkit.data.prefs.CardDensity,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    sparkline: List<Double>? = null,
    /** Pre-formatted monthly electricity cost; null when no rate is set (LARGE cards only). */
    monthlyCost: String? = null,
) {
    val t = miner.lastTelemetry
    val clickMod = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    val colors = CardDefaults.cardColors(
        containerColor = if (selected) HiBrand.surfaceRaised else HiBrand.surface,
    )
    val border =
        if (selected) androidx.compose.foundation.BorderStroke(2.dp, HiBrand.accent) else null

    when (density) {
        hi3.hashkit.data.prefs.CardDensity.GRID -> {
            Card(
                colors = colors, border = border, shape = RoundedCornerShape(12.dp),
                modifier = clickMod.fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        hi3.hashkit.ui.components.StatusDot(miner.status)
                        Text(
                            miner.name + if (miner.isDemo) " ᴰᴱᴹᴼ" else "",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        Units.formatHashrate(t?.hashrateGhs?.value),
                        style = MaterialTheme.typography.titleLarge,
                        color = HiBrand.accent,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        listOfNotNull(
                            Units.formatTemp(t?.chipTempC?.value).takeIf { it != "—" },
                            Units.formatPower(t?.powerW?.value).takeIf { it != "—" },
                            t?.uptimeSeconds?.let { stringResource(R.string.dash_up_prefix, Units.formatUptime(it)) },
                        ).joinToString(" · ").ifEmpty { stringResource(R.string.dash_no_telemetry) },
                        style = MaterialTheme.typography.labelSmall,
                        color = HiBrand.textSecondary,
                        maxLines = 1,
                    )
                }
            }
            return
        }
        hi3.hashkit.data.prefs.CardDensity.COMPACT -> {
            Card(colors = colors, border = border, shape = RoundedCornerShape(8.dp), modifier = clickMod) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                ) {
                    hi3.hashkit.ui.components.StatusDot(miner.status)
                    Text(
                        miner.name + if (miner.isDemo) " ᴰᴱᴹᴼ" else "",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        Units.formatHashrate(t?.hashrateGhs?.value),
                        style = MaterialTheme.typography.bodySmall,
                        color = HiBrand.accent,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        Units.formatTemp(t?.chipTempC?.value),
                        style = MaterialTheme.typography.bodySmall,
                        color = HiBrand.textSecondary,
                    )
                    Text(
                        Units.formatPower(t?.powerW?.value),
                        style = MaterialTheme.typography.bodySmall,
                        color = HiBrand.textSecondary,
                    )
                }
            }
            return
        }
        hi3.hashkit.data.prefs.CardDensity.MEDIUM -> {
            Card(colors = colors, border = border, shape = RoundedCornerShape(10.dp), modifier = clickMod) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        hi3.hashkit.ui.components.StatusDot(miner.status)
                        Text(
                            miner.name + if (miner.isDemo) " ᴰᴱᴹᴼ" else "",
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            Units.formatHashrate(t?.hashrateGhs?.value),
                            style = MaterialTheme.typography.titleSmall,
                            color = HiBrand.accent,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        listOfNotNull(
                            Units.formatPower(t?.powerW?.value).takeIf { it != "—" },
                            Units.formatTemp(t?.chipTempC?.value).takeIf { it != "—" },
                            Units.formatEfficiency(t?.efficiencyJTh?.value).takeIf { it != "—" },
                            t?.uptimeSeconds?.let { stringResource(R.string.dash_up_prefix, Units.formatUptime(it)) },
                            t?.attainmentPercent?.let { stringResource(R.string.dash_pct_of_expected, it.toInt()) },
                        ).joinToString("  ·  ").ifEmpty { stringResource(R.string.dash_no_telemetry) },
                        style = MaterialTheme.typography.labelSmall,
                        color = HiBrand.textSecondary,
                    )
                }
            }
            return
        }
        hi3.hashkit.data.prefs.CardDensity.LARGE -> Unit // falls through to the full card below
    }

    Card(
        colors = colors,
        border = border,
        shape = RoundedCornerShape(14.dp),
        modifier = clickMod,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            miner.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (miner.isDemo) {
                            Text(
                                stringResource(R.string.dash_demo_badge),
                                style = MaterialTheme.typography.labelSmall,
                                color = HiBrand.statusDegraded,
                                modifier = Modifier
                                    .background(
                                        HiBrand.statusDegraded.copy(alpha = 0.15f),
                                        RoundedCornerShape(4.dp),
                                    )
                                    .padding(horizontal = 5.dp, vertical = 1.dp),
                            )
                        }
                    }
                    Text(
                        listOfNotNull(
                            miner.identity.model?.takeIf { it != miner.name },
                            miner.host,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = HiBrand.textSecondary,
                    )
                }
                StatusBadge(miner.status)
            }
            Spacer(Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                Metric(
                    stringResource(R.string.dash_metric_hashrate),
                    Units.formatHashrate(t?.hashrateGhs?.value),
                    valueColor = HiBrand.accent,
                )
                Metric(stringResource(R.string.dash_metric_power), Units.formatPower(t?.powerW?.value), source = t?.powerW?.source)
                Metric(stringResource(R.string.dash_metric_chip), Units.formatTemp(t?.chipTempC?.value))
                Metric(stringResource(R.string.dash_metric_eff), Units.formatEfficiency(t?.efficiencyJTh?.value), source = t?.efficiencyJTh?.source)
                Metric(stringResource(R.string.dash_metric_uptime), Units.formatUptime(t?.uptimeSeconds))
                monthlyCost?.let {
                    Metric(
                        stringResource(R.string.dash_metric_cost_mo), it,
                        source = hi3.hashkit.domain.model.ValueSource.ESTIMATED,
                    )
                }
            }
            if ((sparkline?.size ?: 0) >= 2) {
                Spacer(Modifier.height(8.dp))
                MiniSparkline(
                    sparkline!!,
                    Modifier.fillMaxWidth().height(28.dp),
                    color = miner.status.color(),
                )
            }
        }
    }
}

@Composable
private fun MiniSparkline(points: List<Double>, modifier: Modifier, color: androidx.compose.ui.graphics.Color) {
    val sparklineDescription = stringResource(R.string.dash_cd_sparkline)
    androidx.compose.foundation.Canvas(
        modifier.semantics { contentDescription = sparklineDescription },
    ) {
        val maxV = (points.max() * 1.1).coerceAtLeast(1.0)
        val minV = (points.min() * 0.9).coerceAtLeast(0.0)
        val span = (maxV - minV).coerceAtLeast(1e-6)
        val stepX = if (points.size > 1) size.width / (points.size - 1) else size.width
        val path = androidx.compose.ui.graphics.Path()
        points.forEachIndexed { i, v ->
            val x = i * stepX
            val y = size.height - ((v - minV) / span).toFloat() * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path, color = color,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 2.5f, cap = androidx.compose.ui.graphics.StrokeCap.Round,
            ),
        )
    }
}

@Composable
private fun EmptyState(onAddMiner: () -> Unit, onEnableDemo: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = HiBrand.surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(stringResource(R.string.dash_empty_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.dash_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = HiBrand.textSecondary,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(
                    stringResource(R.string.dash_empty_scan_add),
                    color = HiBrand.accent,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onAddMiner() },
                )
                Text(
                    stringResource(R.string.dash_empty_try_demo),
                    color = HiBrand.accentAlt,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onEnableDemo() },
                )
            }
        }
    }
}
