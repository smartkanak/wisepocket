package date.oxi.wisepocket.pdf

/**
 * One character and its bounding box on the page, in PDF points with the origin at the **top-left**
 * (y grows downward). Platform extractors must normalise to this origin — pdfium reports boxes
 * bottom-left, so the Android actual flips them.
 */
data class PdfChar(
    val char: Char,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    /**
     * True when whitespace preceded this character in the PDF's own text stream.
     *
     * Word breaks are taken from the extractor rather than re-derived from the gaps between boxes,
     * because these boxes are tight *glyph* outlines, not advance widths: a narrow "1" leaves a gap as
     * wide as a real space, which turns "27.05.2019" into "27.05.201 9".
     */
    val precededBySpace: Boolean = false,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

data class PdfPageText(
    val index: Int,
    val widthPoints: Float,
    val heightPoints: Float,
    val chars: List<PdfChar>,
)

data class PdfDocText(val pages: List<PdfPageText>) {
    val charCount: Int get() = pages.sumOf { it.chars.size }
}

/** Thrown when a PDF can't be opened or contains no extractable text (e.g. a scanned document). */
class PdfExtractionException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Shown when a PDF yields no characters at all. Lives here rather than in each actual so the two engines
 * can't drift into telling the user different stories about the same situation.
 */
internal const val NO_TEXT_MESSAGE =
    "This PDF has no extractable text — it's probably a scan. WisePocket can't read scanned statements yet."

/**
 * Extracts every character with its position from [bytes].
 *
 * Text-based PDFs only — a scanned statement yields no characters and callers should treat an empty
 * result as "needs OCR", which this MVP does not support.
 */
expect suspend fun extractPdfText(bytes: ByteArray): PdfDocText
