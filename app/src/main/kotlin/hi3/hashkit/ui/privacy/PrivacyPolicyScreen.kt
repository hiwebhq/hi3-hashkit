package hi3.hashkit.ui.privacy

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import hi3.hashkit.R
import hi3.hashkit.ui.theme.HiBrand

private data class PolicyItem(@StringRes val title: Int, @StringRes val body: Int)

private val POLICY = listOf(
    PolicyItem(R.string.priv_local_title, R.string.priv_local_body),
    PolicyItem(R.string.priv_storage_title, R.string.priv_storage_body),
    PolicyItem(R.string.priv_talks_title, R.string.priv_talks_body),
    PolicyItem(R.string.priv_optional_title, R.string.priv_optional_body),
    PolicyItem(R.string.priv_camera_title, R.string.priv_camera_body),
    PolicyItem(R.string.priv_notifications_title, R.string.priv_notifications_body),
    PolicyItem(R.string.priv_control_title, R.string.priv_control_body),
    PolicyItem(R.string.priv_contact_title, R.string.priv_contact_body),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.priv_title), fontWeight = FontWeight.Bold) },
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
                Text(
                    stringResource(R.string.priv_intro, HiBrand.appName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = HiBrand.textSecondary,
                )
            }
            POLICY.forEach { policy ->
                item(key = policy.title) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = HiBrand.surface),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(policy.title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = HiBrand.textPrimary)
                            Text(stringResource(policy.body), style = MaterialTheme.typography.bodySmall, color = HiBrand.textSecondary)
                        }
                    }
                }
            }
        }
    }
}
