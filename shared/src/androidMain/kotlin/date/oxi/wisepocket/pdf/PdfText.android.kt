package date.oxi.wisepocket.pdf

import date.oxi.wisepocket.llm.AndroidAppContext
import io.legere.pdfiumandroid.PdfiumCore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val LOG = "WP-PDF"

/**
 * pdfium-backed extraction. pdfium is the same engine Chrome uses for PDFs, and the same one the iOS
 * side will bind via cinterop — one engine on both platforms, so the layout heuristics only get tuned
 * once.
 */
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
                val textPage = page.openTextPage() ?: return@mapNotNull null
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
            throw PdfExtractionException(
                "This PDF has no extractable text — it's probably a scan. WisePocket can't read scanned statements yet.",
            )
        }
        result
    } finally {
        doc.close()
    }
}

private fun readChars(textPage: io.legere.pdfiumandroid.PdfTextPage, pageHeight: Float): List<PdfChar> {
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
