package date.oxi.wisepocket.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * The app's colour roles.
 *
 * ## Built on a premium light scheme
 *
 * The background is a clean off-white (#F6F8FD) and surfaces are pure white.
 * Accent actions are styled with the brand blue (#1B1BD1).
 */
private val Scheme = lightColorScheme(
    primary = WpColors.Brand,
    onPrimary = WpColors.White,
    primaryContainer = WpColors.RaisedHigh,
    onPrimaryContainer = WpColors.BrandMidnight,
    inversePrimary = WpColors.White,

    secondary = WpColors.Muted,
    onSecondary = WpColors.White,
    secondaryContainer = WpColors.Raised,
    onSecondaryContainer = WpColors.BrandMidnight,

    tertiary = WpColors.Mint,
    onTertiary = WpColors.White,
    tertiaryContainer = WpColors.MintDeep,
    onTertiaryContainer = WpColors.Mint,

    background = WpColors.BackgroundLight,
    onBackground = WpColors.BrandMidnight,
    surface = WpColors.CardLight,
    onSurface = WpColors.BrandMidnight,
    surfaceVariant = WpColors.RaisedHigh,
    onSurfaceVariant = WpColors.Muted,
    surfaceTint = WpColors.Brand,

    // The elevation ladder.
    surfaceDim = WpColors.RaisedHigh,
    surfaceBright = WpColors.CardLight,
    surfaceContainerLowest = WpColors.CardLight,
    surfaceContainerLow = WpColors.BackgroundLight,
    surfaceContainer = WpColors.Raised,
    surfaceContainerHigh = WpColors.RaisedHigh,
    surfaceContainerHighest = WpColors.RaisedHighest,

    inverseSurface = WpColors.BrandMidnight,
    inverseOnSurface = WpColors.White,

    outline = WpColors.Outline,
    outlineVariant = WpColors.OutlineFaint,

    error = WpColors.Coral,
    onError = WpColors.White,
    errorContainer = WpColors.CoralDeep,
    onErrorContainer = WpColors.Coral,

    scrim = WpColors.BrandMidnight,
)

/**
 * The app's theme. Wraps every screen, on both platforms, via `App()`.
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
