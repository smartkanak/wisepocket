package date.oxi.wisepocket.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The type scale.
 *
 * No font files. That's a decision, not a gap: a custom face is a per-platform resource, a licence and a
 * download, and the app already asks the user for a gigabyte of GGUF. The system face on each platform is
 * the one that renders euro signs, umlauts and a merchant called `ebay Kleinanzeigen GmbH` correctly, and
 * it costs nothing. Only weight, size and tracking are set here — which is most of what a type scale is.
 *
 * Two departures from the Material baseline:
 *
 *  - **Display and headline are heavier and tighter.** The baseline sets `displayLarge` at `W400` with
 *    positive tracking — it's built for a light canvas and airy marketing type. On a saturated blue it goes
 *    thin and hazy: white on `#1B1BD1` blooms at large sizes, and a 400-weight 57sp number looked
 *    *blurred* rather than bold. These sizes exist for one thing here — the Wrapped screens shouting a
 *    number — so they're `W700` with negative tracking.
 *  - **Money is tabular.** [Mono] is used for amounts, because a column of proportional digits doesn't
 *    line up at the decimal point and a statement is nothing but a column of digits.
 */
internal val WpTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 60.sp,
            lineHeight = 64.sp,
            letterSpacing = (-1.5).sp,
        ),
        displayMedium = displayMedium.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1).sp,
        ),
        displaySmall = displaySmall.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
        ),
        headlineMedium = headlineMedium.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.5).sp,
        ),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.25).sp,
        ),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.copy(fontWeight = FontWeight.SemiBold),
        // Labels sit on buttons and chips, where white-on-blue needs the extra stroke to hold its edge.
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = labelSmall.copy(letterSpacing = 0.5.sp),
    )
}

/** Amounts, and the raw statement line in the editor. Digits that line up under each other. */
internal val Mono: TextStyle = TextStyle(fontFamily = FontFamily.Monospace)
