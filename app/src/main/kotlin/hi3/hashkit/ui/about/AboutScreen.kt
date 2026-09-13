package hi3.hashkit.ui.about

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import hi3.hashkit.R
import hi3.hashkit.ui.theme.HiBrand
import hi3.hashkit.ui.util.launchChooser
import hi3.hashkit.ui.util.openUrl
import hi3.hashkit.ui.util.viewFile

/** How-to steps shown on the About screen. */
private data class HowToStep(@StringRes val title: Int, @StringRes val body: Int)

/** A supported-miner family row: the family, the models it covers, and support level. */
private data class SupportedMiner(
    @StringRes val family: Int,
    @StringRes val models: Int,
    @StringRes val support: Int,
)

private val SUPPORTED_MINERS = listOf(
    SupportedMiner(
        R.string.about_miner_bitaxe_family,
        R.string.about_miner_bitaxe_models,
        R.string.about_miner_bitaxe_support,
    ),
    SupportedMiner(
        R.string.about_miner_nerdqaxe_family,
        R.string.about_miner_nerdqaxe_models,
        R.string.about_miner_nerdqaxe_support,
    ),
    SupportedMiner(
        R.string.about_miner_bmminer_family,
        R.string.about_miner_bmminer_models,
        R.string.about_miner_bmminer_support,
    ),
    SupportedMiner(
        R.string.about_miner_vnish_family,
        R.string.about_miner_vnish_models,
        R.string.about_miner_vnish_support,
    ),
    SupportedMiner(
        R.string.about_miner_luxos_family,
        R.string.about_miner_luxos_models,
        R.string.about_miner_luxos_support,
    ),
    SupportedMiner(
        R.string.about_miner_avalon_family,
        R.string.about_miner_avalon_models,
        R.string.about_miner_avalon_support,
    ),
    SupportedMiner(
        R.string.about_miner_braiins_family,
        R.string.about_miner_braiins_models,
        R.string.about_miner_braiins_support,
    ),
    SupportedMiner(
        R.string.about_miner_whatsminer_family,
        R.string.about_miner_whatsminer_models,
        R.string.about_miner_whatsminer_support,
    ),
    SupportedMiner(
        R.string.about_miner_demo_family,
        R.string.about_miner_demo_models,
        R.string.about_miner_demo_support,
    ),
)

private val HOW_TO = listOf(
    HowToStep(R.string.about_howto_1_title, R.string.about_howto_1_body),
    HowToStep(R.string.about_howto_2_title, R.string.about_howto_2_body),
    HowToStep(R.string.about_howto_3_title, R.string.about_howto_3_body),
    HowToStep(R.string.about_howto_4_title, R.string.about_howto_4_body),
    HowToStep(R.string.about_howto_5_title, R.string.about_howto_5_body),
    HowToStep(R.string.about_howto_6_title, R.string.about_howto_6_body),
    HowToStep(R.string.about_howto_7_title, R.string.about_howto_7_body),
    HowToStep(R.string.about_howto_8_title, R.string.about_howto_8_body),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"
    }
    fun open(url: String) = context.openUrl(url)
    // Open the bundled user guide: copy the asset into the FileProvider's cache dir, then hand it
    // to whatever PDF viewer the user has. The PDF ships in the app — no network needed.
    fun openGuide() {
        runCatching {
            val outDir = java.io.File(context.cacheDir, "exports").apply { mkdirs() }
            val out = java.io.File(outDir, "USER_GUIDE.pdf")
            context.assets.open("USER_GUIDE.pdf").use { input ->
                out.outputStream().use { input.copyTo(it) }
            }
            context.viewFile(out, "application/pdf")
        }.onFailure {
            android.widget.Toast.makeText(
                context, context.getString(R.string.about_guide_error),
                android.widget.Toast.LENGTH_LONG,
            ).show()
        }
    }
    fun shareApp() {
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                android.content.Intent.EXTRA_SUBJECT,
                context.getString(R.string.about_share_subject, HiBrand.appName),
            )
            putExtra(
                android.content.Intent.EXTRA_TEXT,
                context.getString(R.string.about_share_text, HiBrand.appName),
            )
        }
        context.launchChooser(send, context.getString(R.string.about_share_chooser, HiBrand.appName))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = HiBrand.background),
            )
        },
        containerColor = HiBrand.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Section(stringResource(R.string.about_section_about)) {
                    Text(
                        stringResource(R.string.about_app_version, HiBrand.appName, version),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(R.string.about_app_desc),
                        style = MaterialTheme.typography.labelSmall,
                        color = HiBrand.textSecondary,
                    )
                    LinkRow(
                        title = stringResource(R.string.about_link_guide_title),
                        subtitle = stringResource(R.string.about_link_guide_subtitle),
                        linkLabel = stringResource(R.string.about_link_guide_label),
                        highlight = true,
                    ) { openGuide() }
                    LinkRow(
                        title = "Hi3",
                        subtitle = stringResource(R.string.about_link_hi3_subtitle),
                        linkLabel = "hi3.cc",
                        highlight = false,
                    ) { open("https://www.hi3.cc") }
                    LinkRow(
                        title = stringResource(R.string.about_link_pool_title),
                        subtitle = stringResource(R.string.about_link_pool_subtitle),
                        linkLabel = "pool.hi3.cc",
                        highlight = true,
                    ) { open("https://pool.hi3.cc") }
                    LinkRow(
                        title = stringResource(R.string.about_link_support_title),
                        subtitle = stringResource(R.string.about_link_support_subtitle),
                        linkLabel = "mmp.hi3.cc",
                        highlight = false,
                    ) { open("https://mmp.hi3.cc") }
                    LinkRow(
                        title = stringResource(R.string.about_link_share_title),
                        subtitle = stringResource(R.string.about_link_share_subtitle),
                        linkLabel = stringResource(R.string.common_share),
                        highlight = false,
                    ) { shareApp() }
                    LinkRow(
                        title = stringResource(R.string.about_link_feedback_title),
                        subtitle = stringResource(R.string.about_link_feedback_subtitle),
                        linkLabel = "hi3.cc/contact",
                        highlight = false,
                    ) { open("https://hi3.cc/contact") }
                }
            }
            item {
                Section(stringResource(R.string.about_section_supported)) {
                    Text(
                        stringResource(R.string.about_supported_intro),
                        style = MaterialTheme.typography.labelSmall,
                        color = HiBrand.textSecondary,
                    )
                    SUPPORTED_MINERS.forEach { m -> SupportedMinerRow(m) }
                }
            }
            item {
                Section(stringResource(R.string.about_section_howto)) {
                    HOW_TO.forEach { step -> HowToRow(step) }
                }
            }
        }
    }
}

@Composable
private fun SupportedMinerRow(m: SupportedMiner) {
    Column(Modifier.fillMaxWidth()) {
        Text(stringResource(m.family), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = HiBrand.textPrimary)
        Text(stringResource(m.models), style = MaterialTheme.typography.labelSmall, color = HiBrand.accent)
        Text(stringResource(m.support), style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary)
    }
}

@Composable
private fun HowToRow(step: HowToStep) {
    Column(Modifier.fillMaxWidth()) {
        Text(stringResource(step.title), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = HiBrand.textPrimary)
        Text(stringResource(step.body), style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary)
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = HiBrand.surface),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(14.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary)
            content()
        }
    }
}

@Composable
private fun LinkRow(
    title: String,
    subtitle: String,
    linkLabel: String,
    highlight: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal,
                color = if (highlight) HiBrand.accent else MaterialTheme.colorScheme.onSurface,
            )
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary)
        }
        Spacer(Modifier.size(8.dp))
        Text(
            "$linkLabel ↗",
            style = MaterialTheme.typography.labelLarge,
            color = HiBrand.accent,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
