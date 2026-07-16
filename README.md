<div align="center">
  <img src="docs/wordmark.svg" alt="WisePocket" width="280">

  **A Kotlin Multiplatform personal-finance app that reads your bank statements, categorises your
  spending, and lets you chat with it — with every byte of AI inference running on the phone.**

  No backend. No account. No telemetry. Nothing leaves the device.

  Android · iOS · one shared codebase (UI included)
</div>

---

WisePocket turns a raw bank-statement PDF into something you can actually use: an editable transaction
list, a set of spending insights, a swipeable *Financial Wrapped* story, and a chat that answers questions
about your money. The defining constraint — the reason the project exists — is that the language model runs
**on-device**, so a statement full of personal financial data never touches a server.

It's an open-source MVP, built feature by feature and verified on real devices at each step. This README
describes what it does **today**, not a roadmap.

## What it does

| | |
|---|---|
| **Import a statement** | Pick a PDF; it's parsed entirely on-device into transactions, with no per-bank templates. |
| **Review before it lands** | Parsed rows appear in a dialog next to the statement lines they came from — fix, drop, or discard the whole import. Nothing reaches your data until you confirm. |
| **Own the list** | Full CRUD: tap a row to edit inline, add one by hand, delete one, or wipe everything to start a fresh month. Backed by a real database, so it survives restarts. |
| **Categorise spending** | The on-device model sorts transactions into a fixed set of categories. Every category is a tappable chip, so a wrong guess is one tap to fix. |
| **See the insights** | Totals, spending-by-category, by-month, top merchants, biggest purchase, net saved — all computed in Kotlin, not by the model. |
| **Financial Wrapped** | A full-screen, swipeable story built from those insights. A card only appears when its data exists. |
| **Chat with your money** | Ask *"how much did I spend on groceries?"* and get a streamed answer, grounded in pre-computed figures so a 1.5B model can't fumble the arithmetic. |

Everything above works **identically on Android and iOS**, from one `commonMain` codebase — including the
Compose UI. There are no platform stubs; the only platform-specific code is the PDF text extractor and a
handful of storage-path seams.

## The three ideas worth understanding

### 1. Reading statements with no per-bank parsers

An earlier version hardcoded one parser per bank. It was deleted on purpose: users have banks we've never
seen, so a statement's layout is **inferred per document** instead.

1. **Extract** every character with its bounding box — **pdfium** on Android, Apple **PDFKit** on iOS. This
   is the *only* platform-specific step in the whole pipeline; both engines produce byte-identical parses of
   the same statement.
2. **Rebuild** visual lines and columns from those coordinates (grouping by vertical overlap, word breaks
   from the PDF's own spacing).
3. **Infer a profile** — date format, number format (`1.234,56` vs `1,234.56`), and how spending is marked.
4. **Prove it by reconciliation:** where the statement prints its own opening/closing balance, the correct
   profile reproduces `new − old` to the cent. A wrong sign convention misses by a mile, so it's rejected.
5. **Ask the model one narrow question** — *how does this bank mark money going out?* — and **only** when
   the text genuinely can't answer (no stated balance **and** no sign markers anywhere). It picks among
   conventions already consistent with the text; it names the convention, and code applies it to every row,
   so the model never has to copy a digit correctly.

> Verified against real statements from four banks (2019–2024). Three reconcile exactly against their own
> stated balances; the fourth, which prints no balance, reads all 108 of its rows and is flagged for
> review. A fifth, **invented** bank layout lives in the test suite on purpose — if the pipeline only worked
> on layouts it was written against, it wouldn't be general.

*Text-based PDFs only — scanned statements would need OCR, which this MVP doesn't do.*

### 2. On-device LLM, one engine for both platforms

Inference runs fully on-device via **[llama.cpp](https://github.com/ggml-org/llama.cpp)**, wrapped by the
Kotlin Multiplatform library **[Llamatik](https://github.com/ferranpons/llamatik)** — the *same* engine on
Android (JNI) and iOS (Kotlin/Native cinterop), so there is **no per-platform engine code**.

- **Model:** any **GGUF**. Default is **Qwen2.5-1.5B-Instruct** (Apache-2.0, ~1 GB Q4_K_M), downloadable
  in-app over a plain URL with **no login**; paste a different URL to swap it, add a bearer token for gated
  hosts.
- **Accuracy comes from the prompt, not the model.** Every figure the app states — totals, category
  rankings, net saved, biggest/most-recent purchase — is pre-computed in Kotlin and rendered into the
  prompt, so the small model *reads* the numbers rather than deriving them. (~1,385 chars covers 61
  transactions in full.)
- **Two sampling modes, and it matters.** Chat is sampled for fluency (`temp 0.7`); categorisation and
  profile detection are **greedy** (`temp 0`). Measured on eleven real merchants, sampling at 0.7 answered
  the wrong category five times; greedy got them right. Same model, same prompt.

> Verified: builds and links llama.cpp natively on both platforms, with real streamed answers on an Android
> device, the iOS simulator, and the in-app download-then-load path exercised end-to-end. Inference is
> **CPU-prefill-bound** — fast on modern SoCs, slow (~25 s/answer) on older chips like the Tensor G1.
> *(Google's LiteRT-LM was tried first and dropped — its iOS support isn't production-ready: Swift-only
> packaging plus broken simulator inference.)*

### 3. Everything the app claims is computed in code, then tested

The chat used to answer from a running total and the newest eight rows, because `Transaction.category` was
a field nothing ever wrote. The fix was to move *every* stated figure into a pure `insights/` package that
imports no UI — so profile detection, categorisation aggregates, and every Wrapped card can be unit-tested
without a device. An architecture test (Konsist) enforces that purity: `statement/`, `model/` and
`insights/` may not import Compose, and it's mutation-verified (a planted violation is confirmed to fail).

## Tech stack

| Area | Choice |
|---|---|
| **Language / platforms** | Kotlin 2.4, Kotlin Multiplatform → Android + iOS (`iosArm64`, `iosSimulatorArm64`) |
| **UI** | Compose Multiplatform 1.11 (Material 3), shared in `commonMain` — one UI, both platforms |
| **On-device LLM** | llama.cpp via Llamatik; GGUF models (default Qwen2.5-1.5B-Instruct) |
| **PDF text** | pdfium (Android) · Apple PDFKit (iOS) — the pipeline's single platform seam |
| **Persistence** | Room Multiplatform + KSP, bundled SQLite driver on native |
| **Networking** | Ktor client (model download, streamed to disk via kotlinx-io) |
| **DI** | Koin — started from the platform entry point, not a Compose scope |
| **Navigation** | Jetpack Navigation for KMP, type-safe routes |
| **Async** | Coroutines + Flow (streaming tokens, DAO Flows via `stateIn`) |
| **Design system** | Hand-built palette around a single brand blue (`#1B1BD1`); Material 3 the *system*, not its colours |
| **Quality gates** | detekt (no baseline — zero findings) + Konsist architecture tests, both on `check` |

## Architecture at a glance

```
┌───────────────────────── :shared (commonMain — Android + iOS) ─────────────────────────┐
│                                                                                         │
│  pdf/         extractPdfText  ← the ONE platform seam (pdfium / PDFKit)                  │
│    ↓          layout reconstruction                                                     │
│  statement/   tokens → profile detection → reconcile → (LLM only if undecidable)        │
│    ↓                                                                                     │
│  model/ · data/ (Room)   the transaction, and where it's stored                         │
│    ↓                                                                                     │
│  insights/    pure aggregates — every figure the app states, computed once, UI-free     │
│    ↓                              ↘                                                      │
│  chat/ (PromptBuilder → LLM)      wrapped/ (story cards)                                 │
│                                                                                         │
│  llm/   one llama.cpp engine, app-scoped, serialised   ·   di/ (Koin)   ·   ui/theme/   │
│  transactions/ · review/ · onboarding/   ← Compose screens, shared                      │
└─────────────────────────────────────────────────────────────────────────────────────────┘
        ▲                                                                    ▲
  :androidApp                                                            iosApp/
  MainActivity → App()                                    MainViewController() → App()
```

**Design decisions that shaped it:**

- **Nearly everything is in `:shared`, UI included.** The platform apps are thin entry points that both call
  the same `App()` composable. The narrow PDF seam is exactly what lets iOS use a *different* engine
  (PDFKit) safely — no prebuilt static pdfium exists for iOS, so matching Android's engine wasn't an option.
- **One LLM engine, never two.** `LlamaBridge` is a singleton over one native llama.cpp context; a second
  engine would load a ~1 GB model into that context twice. So chat and import share a single app-scoped
  provider that serialises generation calls.
- **Koin is started from the platform entry point, not a `KoinApplication` composable** — the usual Compose
  advice ties the container's lifetime to the composition, and an Android rotation would restart Koin and
  reload the model.
- **The category set is a closed enum stored by name** — free-form labels are the one failure that silently
  splits a merchant's own subtotal ("Groceries" here, "Lebensmittel" there). Stored as the enum name in the
  existing text column, so it needed no database migration.
- **A closed palette with a Konsist rule.** The design system is Material 3's *structure* with a hand-written
  blue scheme; a rule fails the build if any colour literal appears outside the theme files. `#1B1BD1` is a
  dark colour, so — counter-intuitively — white is the action colour and the brand blue is the canvas.

## Project layout

```
shared/      all shared logic AND Compose UI (feature packages above); commonMain + androidMain + iosMain
androidApp/  Android host — MainActivity calls App()
iosApp/      iOS host — SwiftUI wraps MainViewController() (open in Xcode)
```

## Running

Both platforms need a GGUF model on the device — download it in-app (paste a URL, no login for the default),
or side-load one during development.

```bash
# Android
./gradlew :androidApp:assembleDebug        # or :androidApp:installDebug onto a device/emulator
```

```
# iOS — open iosApp/ in Xcode and run
#       (the shared framework is built by Gradle as part of the Xcode build)
```

## Tests & quality

```bash
./gradlew :shared:testAndroidHostTest          # shared logic on the JVM (fast)
./gradlew :shared:iosSimulatorArm64Test        # the same tests on the iOS simulator
./gradlew :shared:allTests                     # both
./gradlew check                                # tests + detekt + Konsist architecture tests
```

- **87 shared tests**, run on **both** the JVM and the iOS simulator — the parsing pipeline, categorisation,
  insights, Wrapped story, prompt building and the stores are all covered without a device.
- **detekt** with **no baseline file** — the tree sits at zero findings, so any new one is real.
- **Konsist** pins the architecture the compiler can't: no per-bank branches, the `expect` seam stays
  narrow, the computing packages import no Compose, and no colour literals leak out of the theme. Every rule
  is mutation-verified — a planted violation is confirmed to fail it.

> The lesson the project keeps relearning: a green build log proves less than it looks. The real failures —
> a wrong sign convention corrupting every number, a status-bar clock rendered invisible, a category parser
> discarding exactly the merchants the model recognised — all lived where Gradle can't see, and were caught
> by running it on a device.

## License

The default model (Qwen2.5-1.5B-Instruct) is Apache-2.0; the wordmark is set in
[Rammetto One](https://fonts.google.com/specimen/Rammetto+One) (OFL-1.1). No real bank statements are in
this repository — they're personal data and belong nowhere near it.
