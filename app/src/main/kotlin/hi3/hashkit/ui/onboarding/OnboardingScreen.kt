package hi3.hashkit.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.annotation.StringRes
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hi3.hashkit.R
import hi3.hashkit.ui.language.LanguagePicker
import hi3.hashkit.ui.theme.HiBrand
import hi3.hashkit.ui.theme.HiLogo

/** First-run welcome: explains local-first operation and what the app will do. */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        HiLogo(markSize = 56.dp, fontSize = 32.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.onboarding_tagline),
            style = MaterialTheme.typography.titleMedium,
            color = HiBrand.textPrimary,
        )
        Spacer(Modifier.height(20.dp))

        // Let non-English speakers switch before reading anything else; picking a chip
        // recreates the activity and re-renders this screen translated.
        LanguagePicker(showSystemChoice = false)
        Spacer(Modifier.height(20.dp))

        Point(R.string.onboarding_local_title, R.string.onboarding_local_body)
        Point(R.string.onboarding_live_title, R.string.onboarding_live_body)
        Point(R.string.onboarding_safe_title, R.string.onboarding_safe_body)
        Point(R.string.onboarding_private_title, R.string.onboarding_private_body)

        Spacer(Modifier.height(32.dp))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.onboarding_get_started))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.onboarding_next_hint),
            style = MaterialTheme.typography.labelSmall,
            color = HiBrand.textSecondary,
        )
    }
}

@Composable
private fun Point(@StringRes title: Int, @StringRes body: Int) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        androidx.compose.foundation.Canvas(Modifier.size(8.dp).padding(top = 6.dp)) {
            drawCircle(HiBrand.accent)
        }
        Spacer(Modifier.size(12.dp))
        Column {
            Text(
                stringResource(title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = HiBrand.textPrimary,
            )
            Text(stringResource(body), style = MaterialTheme.typography.bodySmall, color = HiBrand.textSecondary)
        }
    }
}
