package date.oxi.wisepocket.pdf

import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGRect
import platform.Foundation.NSData
import platform.Foundation.NSMakeRange
import platform.Foundation.create
import platform.PDFKit.PDFDocument
import platform.PDFKit.PDFPage
import platform.PDFKit.kPDFDisplayBoxMediaBox

private const val LOG = "WP-PDF"

/**
 * PDFKit-backed extraction.
 *
 * A different engine from Android's pdfium, which would normally be a problem — but the seam here is
 * narrow by design. All this has to produce is characters, their boxes, and where the spaces were;
 * [LayoutText] rebuilds lines and columns from that in `commonMain`, identically on both platforms. The
 * engines only have to agree on glyph geometry, not on how to read a table.
 *
 * PDFKit is Apple's own and already ships in Kotlin/Native's platform libraries: no dependency, no binary
 * to embed, no Xcode step. pdfium was tried first and dropped — no prebuilt *static* iOS library exists
 * (upstream ships a dylib only), so matching Android's engine would have meant embedding and signing a
 * dylib by hand.
 */
@OptIn(ExperimentalForeignApi::class)
actual suspend fun extractPdfText(bytes: ByteArray): PdfDocText = withContext(Dispatchers.Default) {
    val document = PDFDocument(bytes.toNSData())
        ?: throw PdfExtractionException("Could not open PDF — it may be corrupt or password-protected.")

    val pages = (0 until document.pageCount.toInt()).mapNotNull { index ->
        val page = document.pageAtIndex(index.convert()) ?: return@mapNotNull null
        readPage(page, index)
    }

    val result = PdfDocText(pages)
    println("$LOG extracted ${result.charCount} chars from ${pages.size} pages")
    if (result.charCount == 0) {
        throw PdfExtractionException(
            "This PDF has no extractable text — it's probably a scan. WisePocket can't read scanned statements yet.",
        )
    }
    result
}

@OptIn(ExperimentalForeignApi::class)
private fun readPage(page: PDFPage, index: Int): PdfPageText? {
    // The media box can sit at a non-zero origin and character bounds are reported in that same space, so
    // coordinates are normalised against it rather than assumed to start at (0, 0).
    val box = page.boundsForBox(kPDFDisplayBoxMediaBox)
    val originX = box.useContents { origin.x.toFloat() }
    val originY = box.useContents { origin.y.toFloat() }
    val width = box.useContents { size.width.toFloat() }
    val height = box.useContents { size.height.toFloat() }

    // page.string indexes 1:1 with the glyph indices — verified against numberOfCharacters on every
    // sample statement. (Deriving the text per index from selectionForRange instead is a trap: on a
    // newline index the selection snaps back to the previous character and duplicates it.)
    val text = page.string ?: return null
    val count = minOf(page.numberOfCharacters.toInt(), text.length)
    if (count == 0) return null

    val chars = ArrayList<PdfChar>(count)
    var pendingSpace = false

    for (i in 0 until count) {
        val char = text[i]
        // PDFKit's page.string carries the document's own spaces and line breaks. Word breaks come from
        // those rather than from gaps between boxes — see PdfChar.precededBySpace.
        if (char.isWhitespace()) {
            pendingSpace = true
            continue
        }

        val rect = boundsOf(page, i) ?: continue
        val left = rect.useContents { origin.x.toFloat() } - originX
        val glyphBottom = rect.useContents { origin.y.toFloat() } - originY
        val glyphWidth = rect.useContents { size.width.toFloat() }
        val glyphHeight = rect.useContents { size.height.toFloat() }
        if (glyphWidth <= 0f || glyphHeight <= 0f) continue

        // Page space has its origin bottom-left and the rect grows upward; flip into the top-left origin
        // the common code expects.
        chars += PdfChar(
            char = char,
            left = left,
            top = height - (glyphBottom + glyphHeight),
            right = left + glyphWidth,
            bottom = height - glyphBottom,
            precededBySpace = pendingSpace,
        )
        pendingSpace = false
    }

    return PdfPageText(index = index, widthPoints = width, heightPoints = height, chars = chars)
}

/**
 * The box of character [index], via `PDFSelection` rather than `characterBounds(at:)`.
 *
 * Both were measured on the sample statements, and the obvious choice is the wrong one:
 *
 * - `characterBounds(at:)` returns a **zero-size rect** for a sizeable minority of characters. Dropping those deletes letters
 *   mid-word and digits out of amounts.
 * - Backing it up with selections only for the failures is worse still, because the two APIs return
 *   *differently shaped* boxes: `characterBounds` gives a tight glyph outline (heights 4.4–5.9 on one
 *   line), while a selection gives the **full line height** (10.9 for every character on that same line).
 *   Mixed together, the tall boxes swell [LayoutText]'s line band until neighbouring rows merge, and the
 *   page comes out interleaved.
 *
 * Selections alone are uniform, complete, and — since line-height boxes are exactly what grouping
 * characters into lines wants — better suited than the tight boxes anyway.
 */
@OptIn(ExperimentalForeignApi::class)
private fun boundsOf(page: PDFPage, index: Int): CValue<CGRect>? =
    page.selectionForRange(NSMakeRange(index.convert(), 1.convert()))?.boundsForPage(page)

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData = usePinned {
    NSData.create(bytes = it.addressOf(0), length = size.convert())
}
