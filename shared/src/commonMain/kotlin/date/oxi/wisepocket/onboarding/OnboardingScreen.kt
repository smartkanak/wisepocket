package date.oxi.wisepocket.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import date.oxi.wisepocket.llm.ModelSetupPanel
import date.oxi.wisepocket.llm.ModelStatus

/**
 * First run: what this app is, and the one thing it needs to be good at its job.
 *
 * Shown only when there is genuinely nothing here — no transactions **and** no model. That condition is
 * derived rather than stored, which is deliberate: a `hasSeenOnboarding` flag would be the first thing in
 * the app to need DataStore, and it would earn its keep by answering a question the data already answers.
 *
 * **Skippable, and that isn't a hedge.** WisePocket is designed to work without the model — import, editing
 * and totals are all deterministic Kotlin — so a setup step that refused to let you past would be lying
 * about its own necessity. What the model buys you is categorisation and chat, so that's what this says.
 */
/**
 * @param firstRun true for the actual first launch, false when reopened later from the home screen. It only
 *   changes what's said, and that matters: the same words in the wrong place are what make an app feel
 *   thoughtless. Offering "skip — import a statement first" to someone who already has statements, and
 *   telling them they can "set it up later from the home screen" while they're standing on it, reads as a
 *   screen that doesn't know where it is.
 */
@Composable
fun OnboardingScreen(
    status: ModelStatus,
    onDownload: (url: String, token: String?) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    firstRun: Boolean = true,
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        Text(if (firstRun) "👛" else "🤖", style = MaterialTheme.typography.displayMedium)
        Text(
            if (firstRun) "WisePocket" else "Set up the on-device AI",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        if (firstRun) {
            Text(
                "Turn your bank statements into a picture of your money — and chat with them.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            if (firstRun) {
                "Everything happens on this phone. No account, no server, nothing uploaded — which is also " +
                    "why the AI has to be downloaded once, rather than living somewhere else."
            } else {
                "It sorts your spending into categories and answers questions about it. It runs on this " +
                    "phone, so it has to be downloaded once — nothing you import is ever uploaded."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        ModelSetupPanel(status = status, onDownload = onDownload, modifier = Modifier.fillMaxWidth())

        // Not hidden away: plenty of people will want to see their statements before spending a gigabyte on
        // a stranger's promise, and the app genuinely works without it.
        if (status !is ModelStatus.Downloading) {
            TextButton(onClick = onSkip) {
                Text(if (firstRun) "Skip — import a statement first" else "Not now")
            }
            if (firstRun) {
                Text(
                    "Without it you still get your transactions, edits and totals. Categories and chat " +
                        "need the model; you can set it up later from the home screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
