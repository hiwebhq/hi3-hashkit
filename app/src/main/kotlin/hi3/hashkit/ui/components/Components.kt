package hi3.hashkit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import hi3.hashkit.domain.model.MinerStatus
import hi3.hashkit.domain.model.ValueSource
import hi3.hashkit.ui.theme.HiBrand

fun MinerStatus.color(): Color = when (this) {
    MinerStatus.ONLINE -> HiBrand.statusOnline
    MinerStatus.DEGRADED -> HiBrand.statusDegraded
    MinerStatus.OFFLINE -> HiBrand.statusOffline
    MinerStatus.UNKNOWN -> HiBrand.statusUnknown
}

fun MinerStatus.label(): String = when (this) {
    MinerStatus.ONLINE -> "Online"
    MinerStatus.DEGRADED -> "Degraded"
    MinerStatus.OFFLINE -> "Offline"
    MinerStatus.UNKNOWN -> "Stale"
}

@Composable
fun StatusDot(status: MinerStatus, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .size(10.dp)
            .background(status.color(), CircleShape)
    )
}

@Composable
fun StatusBadge(status: MinerStatus) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .background(status.color().copy(alpha = 0.14f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        StatusDot(status)
        Text(
            status.label(),
            style = MaterialTheme.typography.labelMedium,
            color = status.color(),
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Small provenance tag so estimated values are never mistaken for measured ones. */
@Composable
fun SourceTag(source: ValueSource) {
    val (label, color) = when (source) {
        ValueSource.MEASURED -> null to HiBrand.textSecondary
        ValueSource.REPORTED -> null to HiBrand.textSecondary
        ValueSource.CALCULATED -> "calc" to HiBrand.accentAlt
        ValueSource.ESTIMATED -> "est" to HiBrand.statusDegraded
        ValueSource.UNAVAILABLE -> null to HiBrand.textSecondary
    }
    if (label != null) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier
                .background(color.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}

@Composable
fun Metric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    source: ValueSource? = null,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary)
            source?.let { SourceTag(it) }
        }
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = valueColor,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
