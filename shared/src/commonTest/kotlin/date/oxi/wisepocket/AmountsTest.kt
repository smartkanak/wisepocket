package date.oxi.wisepocket

import date.oxi.wisepocket.statement.Amounts
import date.oxi.wisepocket.statement.Amounts.NumberFormat
import date.oxi.wisepocket.statement.Amounts.SignConvention
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Each case here is a convention taken from a real sampled statement. The sign cases matter most: a
 * wrong sign doesn't crash, it just quietly inverts a total.
 */
class AmountsTest {

    @Test
    fun germanFormatParsesCommaDecimalAndDotThousands() {
        assertEquals(-0.09, Amounts.parse("-0,09", NumberFormat.GERMAN, SignConvention.EXPLICIT_MINUS))
        assertEquals(1315.60, Amounts.parse("1.315,60", NumberFormat.GERMAN, SignConvention.PLUS_MEANS_INCOME)?.let { -it })
    }

    @Test
    fun angloFormatParsesDotDecimalAndCommaThousands() {
        assertEquals(1091.24, Amounts.parse("€1,091.24", NumberFormat.ANGLO, SignConvention.EXPLICIT_MINUS))
        assertEquals(-5751.00, Amounts.parse("€-5,751.00", NumberFormat.ANGLO, SignConvention.EXPLICIT_MINUS))
    }

    @Test
    fun klarnaBareAmountIsSpendingAndPlusIsIncome() {
        // The trap: no minus sign anywhere in Klarna's statement.
        assertEquals(-2.46, Amounts.parse("2,46 €", NumberFormat.GERMAN, SignConvention.PLUS_MEANS_INCOME))
        assertEquals(25.00, Amounts.parse("+25,00 €", NumberFormat.GERMAN, SignConvention.PLUS_MEANS_INCOME))
        assertEquals(1315.60, Amounts.parse("+1.315,60 €", NumberFormat.GERMAN, SignConvention.PLUS_MEANS_INCOME))
    }

    @Test
    fun mvbSollIsSpendingAndHabenIsIncome() {
        assertEquals(-19.34, Amounts.parse("19,34 S", NumberFormat.GERMAN, SignConvention.SOLL_HABEN))
        assertEquals(74.16, Amounts.parse("74,16 H", NumberFormat.GERMAN, SignConvention.SOLL_HABEN))
        assertEquals(-1040.98, Amounts.parse("1.040,98 S", NumberFormat.GERMAN, SignConvention.SOLL_HABEN))
    }

    @Test
    fun sollHabenRequiresTheSuffix() {
        // Without S/H the direction is unknown — better to fail than to guess.
        assertNull(Amounts.parse("19,34", NumberFormat.GERMAN, SignConvention.SOLL_HABEN))
    }

    @Test
    fun nonAmountsAreRejected() {
        assertNull(Amounts.parse("Verwendungszweck", NumberFormat.GERMAN, SignConvention.EXPLICIT_MINUS))
        assertNull(Amounts.parse("", NumberFormat.GERMAN, SignConvention.EXPLICIT_MINUS))
    }
}
