package date.oxi.wisepocket

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import date.oxi.wisepocket.chat.ChatScreen
import date.oxi.wisepocket.navigation.ChatRoute
import date.oxi.wisepocket.navigation.TransactionsRoute
import date.oxi.wisepocket.onboarding.OnboardingScreen
import date.oxi.wisepocket.pdf.rememberPdfPicker
import date.oxi.wisepocket.review.ImportDialog
import date.oxi.wisepocket.review.ImportViewModel
import date.oxi.wisepocket.transactions.TransactionsScreen
import date.oxi.wisepocket.transactions.TransactionsViewModel
import date.oxi.wisepocket.wrapped.WrappedScreen
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.koin.compose.viewmodel.koinViewModel
import kotlin.reflect.KClass
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Today, in the user's own timezone — a new row should be dated now, not at some fixed point. */
@OptIn(ExperimentalTime::class)
private fun today() = Clock.System.todayIn(TimeZone.currentSystemDefault())

/**
 * The tabs, and the destination each one stands for.
 *
 * Two, not three. Wrapped was a tab and shouldn't have been: a tab claims to be a place you can always be,
 * so it sat there offering itself on a fresh install with nothing to show, and its full-bleed story then
 * rendered inside a tab frame it was never designed for. It's a thing you go and look at — launched from
 * home, closed when you're done — which is the same shape as import.
 */
private enum class TopLevel(val label: String, val route: Any, val routeClass: KClass<*>) {
    TRANSACTIONS("Transactions", TransactionsRoute, TransactionsRoute::class),
    CHAT("Chat", ChatRoute, ChatRoute::class),
}

@Composable
@Preview
fun App() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val transactionsViewModel: TransactionsViewModel = koinViewModel()
            val importViewModel: ImportViewModel = koinViewModel()
            val state by transactionsViewModel.state.collectAsStateWithLifecycle()
            val importState by importViewModel.state.collectAsStateWithLifecycle()

            // Not persisted, and not a setting: "I've seen the intro" is answered by the data itself —
            // once there's a statement or a model, isFirstRun is false forever. This only has to survive a
            // rotation, which is exactly what rememberSaveable is for.
            var skippedSetup by rememberSaveable { mutableStateOf(false) }
            var showSetup by rememberSaveable { mutableStateOf(false) }
            var showWrapped by rememberSaveable { mutableStateOf(false) }
            var newlyAddedId by rememberSaveable { mutableStateOf<String?>(null) }

            val scope = rememberCoroutineScope()
            val pickPdf = rememberPdfPicker { bytes ->
                if (bytes != null) importViewModel.import(bytes)
            }

            if (state.isFirstRun && !skippedSetup) {
                OnboardingScreen(
                    status = state.modelStatus,
                    onDownload = transactionsViewModel::downloadModel,
                    onSkip = { skippedSetup = true },
                    modifier = Modifier.safeContentPadding(),
                )
                return@Surface
            }

            val navController = rememberNavController()
            Column(Modifier.fillMaxSize().safeContentPadding()) {
                TopLevelTabs(navController)
                NavHost(
                    navController = navController,
                    startDestination = TransactionsRoute,
                    modifier = Modifier.weight(1f),
                ) {
                    composable<TransactionsRoute> {
                        TransactionsScreen(
                            state = state,
                            onUpdate = transactionsViewModel::update,
                            onDelete = transactionsViewModel::remove,
                            onAdd = {
                                scope.launch { newlyAddedId = transactionsViewModel.addBlank(today()) }
                            },
                            onImport = pickPdf,
                            onCategorize = transactionsViewModel::categorizeExisting,
                            onSetUpModel = { showSetup = true },
                            onOpenWrapped = { showWrapped = true },
                            onDeleteAll = {
                                // Clearing the list without a model recreates isFirstRun exactly, which
                                // would throw the user back to the intro for doing something deliberate.
                                // Emptying your own data is not arriving for the first time.
                                skippedSetup = true
                                transactionsViewModel.deleteAll()
                            },
                            newlyAddedId = newlyAddedId,
                        )
                    }
                    composable<ChatRoute> { ChatScreen(onSetUpModel = { showSetup = true }) }
                }
            }

            // All three of these are tasks with an end rather than places, so none of them gets a route: a
            // back stack entry would let the user land back inside a half-finished import, or in a story
            // about data they've since deleted.
            ImportDialog(
                state = importState,
                onUpdate = importViewModel::updateRow,
                onDelete = importViewModel::deleteRow,
                onConfirm = importViewModel::confirm,
                onDiscard = importViewModel::discard,
                onRetry = {
                    importViewModel.discard()
                    pickPdf()
                },
            )

            if (showWrapped) {
                FullScreen(onDismiss = { showWrapped = false }) {
                    WrappedScreen(onClose = { showWrapped = false })
                }
            }

            if (showSetup) {
                FullScreen(onDismiss = { showSetup = false }) {
                    OnboardingScreen(
                        status = state.modelStatus,
                        onDownload = transactionsViewModel::downloadModel,
                        onSkip = { showSetup = false },
                        modifier = Modifier.safeContentPadding(),
                        firstRun = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun FullScreen(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) { content() }
    }
}

@Composable
private fun TopLevelTabs(navController: NavHostController) {
    val entry by navController.currentBackStackEntryAsState()
    val current = entry?.destination
    val selected = TopLevel.entries
        .indexOfFirst { top -> current?.hierarchy?.any { it.hasRoute(top.routeClass) } == true }
        .coerceAtLeast(0)

    PrimaryTabRow(selectedTabIndex = selected) {
        TopLevel.entries.forEachIndexed { index, top ->
            Tab(
                selected = selected == index,
                onClick = {
                    navController.navigate(top.route) {
                        // Switching tabs replaces rather than stacks: without this, hopping between the two
                        // builds a back stack the user then has to press Back through to leave the app.
                        popUpTo(TransactionsRoute) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                text = { Text(top.label) },
            )
        }
    }
}
