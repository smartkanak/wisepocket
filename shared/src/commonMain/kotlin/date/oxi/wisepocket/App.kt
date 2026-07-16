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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
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
import date.oxi.wisepocket.pdf.rememberPdfPicker
import date.oxi.wisepocket.review.ImportDialog
import date.oxi.wisepocket.review.ImportViewModel
import date.oxi.wisepocket.transactions.TransactionStore
import date.oxi.wisepocket.transactions.TransactionsScreen
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import kotlin.reflect.KClass
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Today, in the user's own timezone — a new row should be dated now, not at some fixed point. */
@OptIn(ExperimentalTime::class)
private fun today() = Clock.System.todayIn(TimeZone.currentSystemDefault())

/** The tabs, and the destination each one stands for. */
private enum class TopLevel(val label: String, val route: Any, val routeClass: KClass<*>) {
    TRANSACTIONS("Transactions", TransactionsRoute, TransactionsRoute::class),
    CHAT("Chat", ChatRoute, ChatRoute::class),
}

@Composable
@Preview
fun App() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val importViewModel: ImportViewModel = koinViewModel()
            val store: TransactionStore = koinInject()
            val importState by importViewModel.state.collectAsStateWithLifecycle()
            val transactions by store.transactions.collectAsStateWithLifecycle()
            var newlyAddedId by remember { mutableStateOf<String?>(null) }

            val navController = rememberNavController()
            val scope = rememberCoroutineScope()

            val pickPdf = rememberPdfPicker { bytes ->
                if (bytes != null) importViewModel.import(bytes)
            }

            Column(Modifier.fillMaxSize().safeContentPadding()) {
                TopLevelTabs(navController)
                NavHost(
                    navController = navController,
                    startDestination = TransactionsRoute,
                    modifier = Modifier.weight(1f),
                ) {
                    composable<TransactionsRoute> {
                        TransactionsScreen(
                            transactions = transactions,
                            onUpdate = store::update,
                            onDelete = store::remove,
                            onAdd = { scope.launch { newlyAddedId = store.addBlank(today()) } },
                            onImport = pickPdf,
                            newlyAddedId = newlyAddedId,
                        )
                    }
                    composable<ChatRoute> { ChatScreen() }
                }
            }

            // Import sits over the list rather than beside it: it's a task with an end, not a place.
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
        }
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
