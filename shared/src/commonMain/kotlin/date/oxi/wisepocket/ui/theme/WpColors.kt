package date.oxi.wisepocket.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The palette. Every colour the app uses is named here and nowhere else.
 */
internal object WpColors {

    /** The brand accent. A vibrant, modern brand blue. */
    val Brand = Color(0xFF1B1BD1)

    /** Deep brand blue for dark texts or active states. */
    val BrandDeep = Color(0xFF0F1138)

    /** Deepest blue for titles and text highlights. */
    val BrandMidnight = Color(0xFF0B0D1A)

    // Background and cards for light mode:
    val BackgroundLight = Color(0xFFF6F8FD)
    val CardLight = Color(0xFFFFFFFF)

    // Elevated surface/card tones (soft light grey/blue tints)
    val Raised = Color(0xFFFFFFFF)
    val RaisedHigh = Color(0xFFF1F3FB)
    val RaisedHighest = Color(0xFFE5EAF5)

    /** Body text and secondary backgrounds. */
    val White = Color(0xFFFFFFFF)

    /** Muted text, slate/grey color. */
    val Muted = Color(0xFF5E678E)

    /** Outlines and borders. */
    val Outline = Color(0xFFDCE2F0)
    val OutlineFaint = Color(0xFFEFF2F8)

    /** Money coming in: Mint green (darker for light mode contrast). */
    val Mint = Color(0xFF007A4B)
    val MintDeep = Color(0xFFE2F6EC)
    val OnMint = Color(0xFF003820)

    /** Trouble / Errors (darker for light mode contrast). */
    val Coral = Color(0xFFC62828)
    val CoralDeep = Color(0xFFFFEBEE)
    val OnCoral = Color(0xFF5F0000)
    val OnCoralDeep = Color(0xFFFF8A8A)
}
