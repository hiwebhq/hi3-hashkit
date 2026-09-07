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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            "Your mining fleet, in your pocket.",
            style = MaterialTheme.typography.titleMedium,
            color = HiBrand.textPrimary,
        )
        Spacer(Modifier.height(28.dp))

        Point("Local-first", "Talks only to the miners on your network — or over your Tailscale VPN. No account, no cloud.")
        Point("Live & honest", "Real hashrate, temps, power and health — clearly marked when a value is estimated or unavailable.")
        Point("Safe controls", "Reboot, pool, fan and firmware-bounded tuning with confirmations and rollback, only where verified.")
        Point("Private", "Your data stays on this device. Hi3 Pool and MMP views are optional, read-only, and off by default.")

        Spacer(Modifier.height(32.dp))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text("Get started")
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Next you can scan your network, add a miner by IP, or try demo mode.",
            style = MaterialTheme.typography.labelSmall,
            color = HiBrand.textSecondary,
        )
    }
}

@Composable
private fun Point(title: String, body: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        androidx.compose.foundation.Canvas(Modifier.size(8.dp).padding(top = 6.dp)) {
            drawCircle(HiBrand.accent)
        }
        Spacer(Modifier.size(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = HiBrand.textPrimary)
            Text(body, style = MaterialTheme.typography.bodySmall, color = HiBrand.textSecondary)
        }
    }
}
