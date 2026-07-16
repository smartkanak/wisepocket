package date.oxi.wisepocket

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
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
import date.oxi.wisepocket.ui.theme.WisePocketTheme
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

/**
 * Which way the incoming tab slides. With two tabs, "forward" is simply moving to Chat (the right-hand tab),
 * which enters from the right; anything else is a move back to Transactions, entering from the left.
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.slideDirection(): SlideDirection {
    val toChat = targetState.destination.hierarchy.any { it.hasRoute(ChatRoute::class) }
    return if (toChat) SlideDirection.Left else SlideDirection.Right
}

@Composable
@Preview
fun App() {
    WisePocketTheme {
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
                    modifier = Modifier.safeDrawingPadding(),
                )
                return@Surface
            }

            val navController = rememberNavController()
            val entry by navController.currentBackStackEntryAsState()
            val current = entry?.destination
            val selected = TopLevel.entries
                .indexOfFirst { top -> current?.hierarchy?.any { it.hasRoute(top.routeClass) } == true }
                .coerceAtLeast(0)

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                contentWindowInsets = WindowInsets.systemBars,
                topBar = {
                    PrimaryTabRow(
                        selectedTabIndex = selected,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.statusBarsPadding()
                    ) {
                        TopLevel.entries.forEachIndexed { index, top ->
                            val isSelected = selected == index
                            val icon = if (top == TopLevel.TRANSACTIONS) {
                                Icons.AutoMirrored.Outlined.ReceiptLong
                            } else {
                                Icons.AutoMirrored.Outlined.Chat
                            }
                            Tab(
                                selected = isSelected,
                                onClick = {
                                    navController.navigate(top.route) {
                                        popUpTo(TransactionsRoute) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                text = {
                                    Text(
                                        text = top.label,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                icon = { Icon(icon, contentDescription = top.label) }
                            )
                        }
                    }
                }
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = TransactionsRoute,
                    // Only the top inset (the height of the tab row) is applied to the frame. The bottom
                    // navigation-bar inset is deliberately left for each screen to spend itself — as
                    // LazyColumn contentPadding on the lists, and ime ∪ navigationBars padding on the chat
                    // input. Baking it into the frame here is what made a scrollable list end at a fixed dead
                    // band above the navigation bar instead of reaching the bottom edge.
                    modifier = Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding()),
                    // Tabs slide horizontally rather than cross-fade, so the direction of the switch reads:
                    // moving to a tab on the right brings it in from the right, going back brings it in from
                    // the left. The order is the tab order (Transactions left, Chat right), so an off-screen
                    // destination (index -1) still resolves a sensible direction against a real tab.
                    enterTransition = { slideIntoContainer(slideDirection()) },
                    exitTransition = { slideOutOfContainer(slideDirection()) },
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

            // Wrapped is an inline overlay, not a Dialog: a Dialog window can't be told to draw behind the
            // system bars from common code (decorFitsSystemWindows is Android-only), so its scrim left grey
            // bands over the full-bleed gradient. Rendered here it sits on the app's own edge-to-edge canvas
            // and reaches the real screen edges. The Surface is opaque and blocks touches to the screen
            // beneath; BackHandler keeps the system-back gesture closing the story as the Dialog used to.
            if (showWrapped) {
                CloseOnBack { showWrapped = false }
                Surface(Modifier.fillMaxSize()) {
                    WrappedScreen(onClose = { showWrapped = false })
                }
            }

            if (showSetup) {
                FullScreen(onDismiss = { showSetup = false }) {
                    OnboardingScreen(
                        status = state.modelStatus,
                        onDownload = transactionsViewModel::downloadModel,
                        onSkip = { showSetup = false },
                        modifier = Modifier.safeDrawingPadding(),
                        firstRun = false,
                    )
                }
            }
        }
    }
}

/**
 * System-back closes an inline overlay, the way the [Dialog] behind [FullScreen] does for free.
 *
 * [BackHandler] is Compose Multiplatform's common back API. It's marked deprecated in 1.11 in favour of
 * NavigationEventHandler, whose hoisted-state ceremony buys nothing for a plain close-on-back — so it's kept
 * deliberately, with the opt-in and deprecation suppressed here, at the one site that needs them, rather than
 * spread across App() or left as a build warning.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Suppress("DEPRECATION")
@Composable
private fun CloseOnBack(onBack: () -> Unit) = BackHandler(onBack = onBack)

@Composable
private fun FullScreen(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) { content() }
    }
}


