package date.oxi.wisepocket.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * The corner scale.
 *
 * Rounder than the Material baseline at every step (baseline: 4/8/12/16/28). On a flat, shadowless design a
 * card's *silhouette* is the only thing separating it from the canvas — there's no drop shadow doing the
 * work — so the corner has to be visible enough to read as an edge rather than as a rendering artefact.
 *
 * `extraLarge` stays at the baseline 28: it's the one the full-screen dialogs use, and past ~28dp a corner
 * starts eating the content behind it instead of framing it.
 */
internal val WpShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
