package date.oxi.wisepocket

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.ext.list.modifierprovider.withExpectModifier
import com.lemonappdev.konsist.api.verify.assertTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue as kotlinAssertTrue

/**
 * These pin decisions that were expensive to learn and are invisible to a compiler — the kind a
 * well-meaning change quietly undoes. Each one below cost us something the first time round.
 *
 * Konsist is JVM-only so these run from `androidHostTest`, but they read `.kt` files off disk rather than
 * off a classpath, so they still see commonMain, iosMain, androidMain and androidApp alike. The scope is
 * asserted in [scopeCoversEveryProductionSourceSet] so a future source-set rename can't turn these into
 * tests that pass by looking at nothing.
 */
class ArchitectureTest {

    private fun production(): KoScope = Konsist.scopeFromProduction()

    /**
     * Strips comments so the rules below judge *code*.
     *
     * Bank names in comments are legitimate — they document which real statement taught us a convention.
     * The thing worth forbidding is a bank name the program actually branches on.
     */
    private fun codeOf(text: String): String = text
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
        .replace(Regex("""//[^\n]*"""), " ")

    @Test
    fun scopeCoversEveryProductionSourceSet() {
        val paths = production().files.map { it.path }
        listOf("/commonMain/", "/androidMain/", "/iosMain/").forEach { sourceSet ->
            kotlinAssertTrue(
                paths.any { sourceSet in it },
                "Konsist saw no files in $sourceSet — the rules in this class would be checking nothing.",
            )
        }
    }

    /**
     * **No per-bank parsers.** The pipeline infers each document's conventions and proves them by
     * reconciliation; it never asks "which bank is this?".
     *
     * We built bank-specific parsers once and threw them away: users have banks we have never seen, so a
     * parser per bank is both endless and worse — the generic pipeline reads *more* rows than the
     * hand-written ones did. A new bank belongs in a test fixture, never in an `if`.
     */
    @Test
    fun productionCodeNeverBranchesOnABank() {
        val banks = listOf("fidor", "klarna", "insha", "mvb")

        val offenders = production().files
            .filter { file -> banks.any { it in codeOf(file.text).lowercase() } }
            .map { it.path.substringAfterLast("WisePocket/") }

        assertEquals(
            emptyList(), offenders,
            "Bank names belong in comments and test fixtures, not in production code. " +
                "Add a general rule plus a test case instead of a branch for this bank.",
        )
    }

    /**
     * **The platform seam stays narrow.** Only PDF text extraction, the file picker and the models
     * directory differ per platform; everything else is `commonMain` and behaves identically.
     *
     * This is what makes the two PDF engines safe. Android runs pdfium and iOS runs PDFKit, and that is
     * only tolerable because all they must agree on is characters and boxes — the moment `expect` spreads,
     * the two platforms can start reading the same statement differently.
     *
     * `WisePocketDatabase` is listed but is not a seam: Room requires an `expect object` constructor on
     * non-Android targets and generates every `actual` itself, so there is no platform code behind it.
     * Note what is *not* here — the database's file path genuinely differs per platform, and it stays out
     * of this list because the Koin platform modules carry it instead.
     */
    @Test
    fun expectDeclarationsOnlyExistInTheKnownSeams() {
        val allowed = setOf("PdfText", "PdfPicker", "ModelStorage", "WisePocketDatabase")
        val scope = production()

        val offenders = buildList {
            addAll(scope.functions().withExpectModifier().map { it.name to it.containingFile.name })
            addAll(scope.properties().withExpectModifier().map { it.name to it.containingFile.name })
            addAll(scope.classes().withExpectModifier().map { it.name to it.containingFile.name })
        }.filterNot { (_, file) -> file in allowed }

        assertEquals(
            emptyList(), offenders,
            "New expect/actual seam outside $allowed. Every one of these doubles the surface where the " +
                "platforms can diverge — keep the logic in commonMain if you possibly can.",
        )
    }

    /**
     * **The parts that compute stay UI-free.** `statement`, `model` and `insights` are pure Kotlin.
     *
     * That purity is why the whole profile-detection story is unit-testable without a device, which is the
     * only reason the reconciliation rules are trustworthy at all. `insights` is held to the same line for
     * the same reason: every figure the app states about someone's money is computed there, and the Wrapped
     * screens are a rendering of it — the moment a total can only be checked by looking at a screen, it
     * stops being checked. (`pdf` is deliberately not in this list: `PdfPicker` is a @Composable by nature.)
     */
    @Test
    fun theComputingPackagesDoNotDependOnCompose() {
        val pure = production().files
            .filter { it.packagee?.name?.matches(Regex(""".*\.(statement|model|insights)$""")) == true }

        kotlinAssertTrue(pure.isNotEmpty(), "matched no statement/model/insights files — the rule checks nothing")

        val offenders = pure
            .filter { file -> file.imports.any { it.name.startsWith("androidx.compose") } }
            .map { it.name }

        assertEquals(
            emptyList(), offenders,
            "statement/, model/ and insights/ must stay free of Compose — they are the part we can test " +
                "without a device.",
        )
    }

    /**
     * **Categories are the closed [date.oxi.wisepocket.model.Category] set.**
     *
     * The one failure that would quietly break every total: a free-form label. The same supermarket coming
     * back as "Groceries" here and "Lebensmittel" there splits its own subtotal in two, and nothing about
     * the app would look broken — the sums would just be wrong. So no production code may write a category
     * as a literal; it goes through the enum, whose names the model's answers are validated against.
     */
    @Test
    fun categoriesAreNeverWrittenAsStringLiterals() {
        val offenders = production().files
            .filterNot { it.name == "Category" }
            .filter { file -> CATEGORY_LITERAL.containsMatchIn(codeOf(file.text)) }
            .map { it.path.substringAfterLast("WisePocket/") }

        assertEquals(
            emptyList(), offenders,
            "Assign categories via the Category enum, not a string literal — free-form labels don't add up.",
        )
    }

    /**
     * Compose names UI-emitting functions in PascalCase.
     *
     * Worth a rule of its own because detekt's FunctionNaming is switched off for @Composable in
     * config/detekt/detekt.yml (it flags the whole convention as a violation). Without this, nothing would
     * check Composable naming at all. Value-returning Composables — `rememberPdfPicker` — stay camelCase,
     * which is the same convention's other half.
     */
    @Test
    fun unitReturningComposablesArePascalCase() {
        val offenders = production().functions()
            .filter { fn -> fn.annotations.any { it.name == "Composable" } }
            .filter { it.returnType == null }
            .filterNot { it.name.first().isUpperCase() }
            .map { it.name }

        assertEquals(emptyList(), offenders, "UI-emitting @Composable functions are PascalCase.")
    }

    @Test
    fun viewModelsAreNamedViewModel() {
        production().classes()
            .filter { klass -> klass.parents().any { it.name == "ViewModel" } }
            .assertTrue { it.name.endsWith("ViewModel") }
    }

    private companion object {
        /** `category = "…"` — the assignment, not any mention of the word, so KDoc and labels stay legal. */
        val CATEGORY_LITERAL = Regex("""category\s*=\s*"""")
    }
}
