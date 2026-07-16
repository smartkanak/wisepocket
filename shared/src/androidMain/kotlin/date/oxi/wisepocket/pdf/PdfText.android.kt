package date.oxi.wisepocket.pdf

import date.oxi.wisepocket.llm.AndroidAppContext
import io.legere.pdfiumandroid.PdfTextPage
import io.legere.pdfiumandroid.PdfiumCore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val LOG = "WP-PDF"

/**
 * pdfium-backed extraction — the same engine Chrome renders PDFs with.
 *
 * iOS deliberately uses a *different* engine (PDFKit); see the iOS actual for why matching pdfium there
 * isn't practical. That's safe because this seam only has to produce characters and boxes — [LayoutText]
 * turns those into lines and columns in commonMain, identically for both.
 */
@Suppress("TooGenericExceptionCaught") // pdfium reports every failure as a bare Exception across JNI.
actual suspend fun extractPdfText(bytes: ByteArray): PdfDocText = withContext(Dispatchers.Default) {
    val context = AndroidAppContext.context
        ?: error("initWisePocketAndroid(context) must be called before importing PDFs")

    val core = PdfiumCore(context)
    val doc = try {
        core.newDocument(bytes)
    } catch (e: Exception) {
        throw PdfExtractionException("Could not open PDF: ${e.message}", e)
    }

    try {
        val pages = (0 until doc.getPageCount()).mapNotNull { pageIndex ->
            val page = doc.openPage(pageIndex) ?: return@mapNotNull null
            page.use {
                val height = page.getPageHeightPoint().toFloat()
                val textPage = page.openTextPage()
                textPage.use {
                    PdfPageText(
                        index = pageIndex,
                        widthPoints = page.getPageWidthPoint().toFloat(),
                        heightPoints = height,
                        chars = readChars(textPage, height),
                    )
                }
            }
        }
        val result = PdfDocText(pages)
        println("$LOG extracted ${result.charCount} chars from ${pages.size} pages")
        if (result.charCount == 0) {
            throw PdfExtractionException(NO_TEXT_MESSAGE)
        }
        result
    } finally {
        doc.close()
    }
}

// The two `continue`s skip whitespace (its position is carried on the next char instead) and glyphs with
// no usable box. Both are the point of the loop, not clutter to refactor away.
@Suppress("LoopWithTooManyJumpStatements")
private fun readChars(textPage: PdfTextPage, pageHeight: Float): List<PdfChar> {
    val count = textPage.textPageCountChars()
    if (count <= 0) return emptyList()

    // One call for the whole page: per-character text reads are a JNI round-trip each, which is the
    // difference between a snappy import and a visibly slow one on a 41-page statement.
    val text = textPage.textPageGetText(0, count) ?: return emptyList()

    val chars = ArrayList<PdfChar>(count)
    var pendingSpace = false
    for (i in 0 until minOf(count, text.length)) {
        val char = text[i]
        // pdfium emits the document's own spaces and line breaks; carry them through as a flag rather
        // than as positioned characters, since generated whitespace has no meaningful box.
        if (char.isWhitespace()) {
            pendingSpace = true
            continue
        }
        val box = textPage.textPageGetCharBox(i) ?: continue
        // pdfium's origin is bottom-left and its RectF carries top > bottom in PDF space; flip into the
        // top-left origin the common code expects.
        chars += PdfChar(
            char = char,
            left = box.left,
            top = pageHeight - box.top,
            right = box.right,
            bottom = pageHeight - box.bottom,
            precededBySpace = pendingSpace,
        )
        pendingSpace = false
    }
    return chars
}
