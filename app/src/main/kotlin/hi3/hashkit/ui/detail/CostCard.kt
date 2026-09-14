package hi3.hashkit.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import hi3.hashkit.R
import hi3.hashkit.core.Units
import hi3.hashkit.domain.solo.HomeEconomics
import hi3.hashkit.domain.solo.ProfitMath
import hi3.hashkit.domain.solo.SoloMiningMath
import hi3.hashkit.ui.theme.HiBrand
import java.util.Locale

private const val MONTHS_PER_YEAR = 12.0
/** Past this many months the payback reads better in years. */
private const val PAYBACK_YEARS_THRESHOLD_MONTHS = 24.0

/**
 * "What this costs" in plain terms for one miner: monthly power bill, expected pool
 * earnings, whether the hardware ever pays for itself, and the solo-lottery framing.
 * Every number is an estimate and says so. With no electricity rate set it asks for one
 * inline rather than sending the user to Settings.
 */
@Suppress("LongMethod") // declarative card: one line per economic fact, each guarded
@Composable
fun CostCard(
    powerW: Double?,
    hashrateGhs: Double?,
    networkDifficulty: Double?,
    btcPrice: Double?,
    ratePerKwh: Double,
    currency: String,
    purchasePrice: Double?,
    onSaveRate: (Double) -> Unit,
) {
    if (ratePerKwh <= 0.0) {
        var rateText by remember { mutableStateOf("") }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.det_cost_rate_hint),
                style = MaterialTheme.typography.bodySmall,
                color = HiBrand.textSecondary,
            )
            OutlinedTextField(
                value = rateText,
                onValueChange = { rateText = it },
                label = { Text(stringResource(R.string.det_cost_rate_label, currency)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(
                enabled = (rateText.toDoubleOrNull() ?: 0.0) > 0.0,
                onClick = { rateText.toDoubleOrNull()?.let(onSaveRate) },
            ) { Text(stringResource(R.string.common_save)) }
        }
        return
    }

    val monthlyCost = HomeEconomics.monthlyCost(powerW, ratePerKwh)
    val monthlyKwh = HomeEconomics.monthlyKwh(powerW)
    val revenue = HomeEconomics.monthlyRevenue(hashrateGhs, networkDifficulty, btcPrice)
    val net = HomeEconomics.monthlyNet(revenue, monthlyCost)
    val payback = HomeEconomics.payback(purchasePrice, net)
    fun money(v: Double?) = Units.formatMoney(v, currency)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(R.string.det_cost_per_month, money(monthlyCost)),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = HiBrand.accent,
        )
        Text(
            stringResource(
                R.string.det_cost_energy_line,
                String.format(Locale.US, "%.1f", monthlyKwh ?: 0.0),
                String.format(Locale.US, "%.3f", ratePerKwh).trimEnd('0').trimEnd('.'),
                currency,
                String.format(Locale.US, "%.0f", ProfitMath.heatBtuPerHour(powerW) ?: 0.0),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = HiBrand.textSecondary,
        )

        if (revenue != null && net != null) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.det_cost_earn_line, money(revenue), money(net)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (net >= 0) HiBrand.statusOnline else HiBrand.statusDegraded,
                )
            }
        } else {
            Text(
                stringResource(R.string.det_cost_needs_market),
                style = MaterialTheme.typography.bodySmall,
                color = HiBrand.textSecondary,
            )
        }

        PaybackLine(purchasePrice, payback)

        val expected = networkDifficulty?.let { d ->
            hashrateGhs?.let { h -> SoloMiningMath.expectedSecondsPerBlock(h, d) }
        }
        if (expected != null) {
            Text(
                stringResource(
                    R.string.det_cost_solo_line,
                    SoloMiningMath.formatExpectedTime(expected),
                    money(monthlyCost),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = HiBrand.textSecondary,
            )
        }
        Text(
            stringResource(R.string.det_cost_note),
            style = MaterialTheme.typography.labelSmall,
            color = HiBrand.textSecondary,
        )
    }
}

@Composable
private fun PaybackLine(purchasePrice: Double?, payback: HomeEconomics.Payback?) {
    when {
        purchasePrice == null || purchasePrice <= 0.0 -> Text(
            stringResource(R.string.det_cost_payback_add_price),
            style = MaterialTheme.typography.bodySmall,
            color = HiBrand.textSecondary,
        )
        payback is HomeEconomics.Payback.Never -> Text(
            stringResource(R.string.det_cost_payback_never),
            style = MaterialTheme.typography.bodyMedium,
            color = HiBrand.statusDegraded,
        )
        payback is HomeEconomics.Payback.Months -> Text(
            if (payback.months >= PAYBACK_YEARS_THRESHOLD_MONTHS) {
                stringResource(
                    R.string.det_cost_payback_years,
                    String.format(Locale.US, "%.1f", payback.months / MONTHS_PER_YEAR),
                )
            } else {
                stringResource(R.string.det_cost_payback_months, payback.months.toInt().coerceAtLeast(1))
            },
            style = MaterialTheme.typography.bodyMedium,
            color = HiBrand.statusOnline,
        )
        else -> Unit // payback unknown until earnings are known; the earnings line explains why
    }
}
