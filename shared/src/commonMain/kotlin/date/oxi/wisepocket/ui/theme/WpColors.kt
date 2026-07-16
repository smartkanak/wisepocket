package date.oxi.wisepocket.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The palette. Every colour the app uses is named here and nowhere else.
 *
 * ## Why this isn't a Material Theme Builder export
 *
 * Material's tonal algorithm takes a seed, reads its hue, and *rebuilds* lightness and chroma from its own
 * tone ladder. Feed it `#1B1BD1` and it hands back a primary at tone 40 — a duller, greyer blue. The seed
 * is a hue suggestion to it, not a colour. That is the opposite of what was asked for here: the vibrancy
 * *is* the brand, so [Brand] is written down exactly and the rest of the ramp is built around it.
 *
 * ## Why the brand blue is the canvas and white is the action colour
 *
 * `#1B1BD1` is a **dark** colour — relative luminance 0.056, around tone 25. That single fact decides the
 * whole scheme. It means:
 *
 *  - White on it is 9.9:1. Superb. That's the pairing the app is built on.
 *  - It on any other blue is under 2:1. So a `#1B1BD1` button on a blue canvas would be a rumour — below
 *    even the 3:1 that WCAG asks of a control's *shape*, never mind its label.
 *
 * So on a blue-flooded app the brand blue cannot also be the button. Flooding the canvas with it and
 * making white the interactive colour is the only reading of "primary is `#1B1BD1`, everything else is
 * white" that survives contact with a contrast ratio — and it's why [Scheme] maps `primary = White`.
 * That isn't a demotion of the brand: on this canvas, white *is* emphasis.
 *
 * Ratios below are against [Brand], computed rather than eyeballed.
 */
internal object WpColors {

    /** The brand. The canvas the whole app is flooded with, and the label colour on every white control. */
    val Brand = Color(0xFF1B1BD1)

    /** Deeper than [Brand] — anchors the top of the screen so the header reads as behind the content. */
    val BrandDeep = Color(0xFF1414A8)

    /** Deepest. Scrims, and the far end of a Wrapped gradient. */
    val BrandMidnight = Color(0xFF0A0A4F)

    // Cards lift off the canvas by getting lighter, not by casting a shadow: a shadow on a saturated blue
    // is mud. These are Brand mixed with white at 12 / 18 / 24%.
    val Raised = Color(0xFF3636D7)
    val RaisedHigh = Color(0xFF4444D9)
    val RaisedHighest = Color(0xFF5252DD)

    /** Body text and every primary control. 9.9:1 on [Brand]. */
    val White = Color(0xFFFFFFFF)

    /** Secondary text. Not `White.copy(alpha = …)` — see the KDoc on [Scheme] for why that matters. 6.0:1. */
    val Muted = Color(0xFFC6C6F7)

    /** Hairlines and the outline of a text field. Never carries text, so it isn't held to 4.5:1. */
    val Outline = Color(0xFF8A8AEE)
    val OutlineFaint = Color(0xFF4A4AE0)

    /** Money coming in, and anything else that went right. 7.2:1 — a green would have died on this blue. */
    val Mint = Color(0xFFA8F5D5)
    val MintDeep = Color(0xFF00513C)
    val OnMint = Color(0xFF00382A)

    /** Trouble. A saturated red is illegible on blue; this is lifted to 5.8:1. */
    val Coral = Color(0xFFFFB4AB)
    val CoralDeep = Color(0xFF8C0009)
    val OnCoral = Color(0xFF690005)
    val OnCoralDeep = Color(0xFFFFDAD6)
}
