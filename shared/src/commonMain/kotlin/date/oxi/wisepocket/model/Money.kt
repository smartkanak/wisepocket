package date.oxi.wisepocket.model

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Formats a major-unit amount as a plain `-12.34` string.
 *
 * Hand-rolled because `String.format` is JVM-only and this has to run on iOS too. Rounds to cents via
 * [roundToLong] rather than letting the double's binary representation leak into the output.
 */
fun formatMoney(value: Double): String {
    val cents = (abs(value) * 100).roundToLong()
    val sign = if (value < 0) "-" else ""
    return "$sign${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
}

/** Same as [formatMoney] but always carries an explicit `+` or `-` — used where direction matters at a glance. */
fun formatSignedMoney(value: Double): String =
    if (value >= 0) "+${formatMoney(value)}" else formatMoney(value)
