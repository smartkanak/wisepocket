package date.oxi.wisepocket.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * The app's colour roles.
 *
 * ## Built on `darkColorScheme`, and not by mistake
 *
 * The app is blue-flooded, and `#1B1BD1` is a dark colour — so this *is* a dark theme, whatever the system
 * setting says. That word chooses real behaviour, not just defaults: Material tints elevated surfaces
 * towards `surfaceTint` in dark schemes and leaves them alone in light ones. Declaring a dark canvas
 * through `lightColorScheme` would render correctly and then quietly mislead every component that asks.
 *
 * ## The mapping, and the one part of it that looks wrong
 *
 * `primary = White` / `onPrimary = Brand` reads backwards until you check the contrast — see [WpColors].
 * The payoff is that stock Material components are then *right by default*: a `Button` is white with a blue
 * label, a tab indicator is white, a `LinearProgressIndicator` is white. Nothing in this app passes a
 * `colors =` argument to fix up a component that the scheme should have got right, and that is the test of
 * whether a scheme is honest.
 *
 * ## Why the muted colours are opaque
 *
 * `onSurfaceVariant` is [WpColors.Muted], not `White.copy(alpha = 0.7f)`. Translucent white composites
 * against whatever is behind it, so the same "secondary text" lands at one colour on the canvas and a
 * different one on a card — and its contrast silently changes with it. An opaque colour is the same colour
 * everywhere, which is the only version that can be checked once and trusted.
 */
private val Scheme = darkColorScheme(
    // The interactive colour. White, because on this canvas white is what emphasis looks like.
    primary = WpColors.White,
    onPrimary = WpColors.Brand,
    primaryContainer = WpColors.White,
    onPrimaryContainer = WpColors.Brand,
    inversePrimary = WpColors.Brand,

    // Secondary carries the things that inform rather than invite: the status strip, unselected chips.
    secondary = WpColors.Muted,
    onSecondary = WpColors.Brand,
    secondaryContainer = WpColors.RaisedHigh,
    onSecondaryContainer = WpColors.White,

    // Tertiary is money coming in. Giving income a role of its own is what lets a figure mean something
    // before it's read — previously income was `primary`, which now would make it plain white text.
    tertiary = WpColors.Mint,
    onTertiary = WpColors.OnMint,
    tertiaryContainer = WpColors.MintDeep,
    onTertiaryContainer = WpColors.Mint,

    // The flood.
    background = WpColors.Brand,
    onBackground = WpColors.White,
    surface = WpColors.Brand,
    onSurface = WpColors.White,
    surfaceVariant = WpColors.Raised,
    onSurfaceVariant = WpColors.Muted,
    surfaceTint = WpColors.White,

    // The elevation ladder. Lighter = closer to the reader; a shadow on saturated blue is just mud.
    surfaceDim = WpColors.BrandDeep,
    surfaceBright = WpColors.RaisedHigh,
    surfaceContainerLowest = WpColors.BrandMidnight,
    surfaceContainerLow = WpColors.BrandDeep,
    surfaceContainer = WpColors.Raised,
    surfaceContainerHigh = WpColors.RaisedHigh,
    surfaceContainerHighest = WpColors.RaisedHighest,

    inverseSurface = WpColors.White,
    inverseOnSurface = WpColors.Brand,

    outline = WpColors.Outline,
    outlineVariant = WpColors.OutlineFaint,

    error = WpColors.Coral,
    onError = WpColors.OnCoral,
    errorContainer = WpColors.CoralDeep,
    onErrorContainer = WpColors.OnCoralDeep,

    scrim = WpColors.BrandMidnight,
)

/**
 * The app's theme. Wraps every screen, on both platforms, via `App()`.
 *
 * Deliberately takes no `darkTheme` parameter and never reads `isSystemInDarkTheme()`: there is one scheme
 * and the system doesn't get a vote. A brand this saturated has no light counterpart worth shipping — a
 * "light" `#1B1BD1` app would be a white app with blue bits, which is a different product decision, not a
 * variant. If a second scheme is ever wanted, it belongs here as a parameter and nowhere else, because no
 * screen names a colour.
 */
@Composable
fun WisePocketTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Scheme,
        typography = WpTypography,
        shapes = WpShapes,
        content = content,
    )
}
