package hi3.hashkit.ui.language

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hi3.hashkit.R

/**
 * Chip row for picking the app language, shared by Settings and Onboarding. Selecting a chip
 * applies the language immediately (the activity recreates, redrawing the UI translated).
 */
@Composable
fun LanguagePicker(showSystemChoice: Boolean = true, modifier: Modifier = Modifier) {
    // Track selection locally too: the recreate is async and chips should reflect the tap at once.
    var selected by remember { mutableStateOf(AppLanguage.current()) }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        AppLanguage.entries
            .filter { showSystemChoice || it != AppLanguage.SYSTEM }
            .forEach { lang ->
                FilterChip(
                    selected = selected == lang,
                    onClick = {
                        selected = lang
                        AppLanguage.apply(lang)
                    },
                    label = {
                        Text(
                            if (lang == AppLanguage.SYSTEM) stringResource(R.string.language_system)
                            else lang.nativeName,
                        )
                    },
                )
            }
    }
}
