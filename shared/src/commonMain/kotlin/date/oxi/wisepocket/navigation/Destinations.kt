package date.oxi.wisepocket.navigation

import kotlinx.serialization.Serializable

/**
 * The app's places. Type-safe routes: a destination is a serializable type, not a string, so a typo is a
 * compile error and any arguments a screen grows later travel as typed fields rather than parsed text.
 *
 * Importing is deliberately absent. It's a task with an end, not a place — it belongs over the list as a
 * dialog, and giving it a route would let the user land back in a half-finished import via the back stack.
 */
@Serializable
data object TransactionsRoute

@Serializable
data object ChatRoute
