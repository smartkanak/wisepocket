# WisePocket

A Kotlin Multiplatform (Android + iOS) personal-finance app. The goal: turn raw bank-statement PDFs
into a gamified, visual "Financial Wrapped" experience plus a chat interface — with **all LLM
inference running on-device**, so there's no backend server and data stays private.

Open-source MVP, built feature by feature. Today it's a **transaction list you own**: import a bank
statement PDF, check and correct what was read, then edit, add or delete rows freely — and ask questions
about them in chat. All on-device.

## Reading statements

There is **no parser per bank**. A statement's layout is worked out per document:

1. Every character is extracted with its position — **pdfium** on Android, Apple's **PDFKit** on iOS
   (~200–600 ms for a whole statement). That's the only platform-specific step; both produce identical
   parses of the same statements.
2. Columns are rebuilt from those coordinates into layout-preserving text.
3. A **profile** is inferred — date format, number format (`1.234,56` vs `1,234.56`) and how spending is
   marked (`-12,34`, unsigned-with-`+`-for-income, or a `S`/`H` suffix).
4. Where the statement prints its own opening/closing balance, the profile is **proven**: the right one
   reproduces `new − old` to the cent. A wrong sign convention misses by a mile.
5. The on-device LLM is asked one narrow question — *how does this bank mark money going out?* — and only
   when the text genuinely can't answer it (no stated balance **and** no sign markers anywhere). It reads a
   dozen sample rows, not the whole statement, and it chooses among conventions already consistent with the
   text rather than overriding them. It names the convention; code then applies it to every row, so the
   model never has to copy a digit correctly.
6. Whatever comes out is a **proposal**, shown in a review dialog next to the statement lines it came from.
   Fix rows, drop rows, or discard the whole import — nothing reaches your data until you say so.

Verified against real statements from four banks (Fidor, Klarna, insha, Mainzer Volksbank, 2019–2024).
Three reconcile exactly against their own stated balances; the fourth (which prints no balance) reads all
108 of its rows and is flagged for review. A fifth, invented bank layout is in the test suite on purpose —
if the pipeline only worked on layouts it was written against, it wouldn't be general.

Text-based PDFs only. Scanned statements would need OCR, which this MVP doesn't do.

## On-device LLM

Inference runs fully on-device via **[llama.cpp](https://github.com/ggml-org/llama.cpp)**, wrapped by
the Kotlin Multiplatform library **[Llamatik](https://github.com/ferranpons/llamatik)** — the same
engine on **both Android (JNI) and iOS (Kotlin/Native cinterop)**, no per-platform engine code.

- **Model:** a license-free **GGUF** — default **Qwen2.5-1.5B-Instruct** (Apache-2.0, ~1 GB Q4_K_M),
  downloadable in-app with no login. Swap in any GGUF via the URL field.
- **No backend, no accounts, no telemetry** — the model file and all inference stay on the device.
- **Accuracy comes from the prompt, not model size:** totals, category rankings, net savings, and the
  most recent purchase are pre-computed in Kotlin (`PromptBuilder`) so the small model just reads them.

Everything — Compose UI, chat flow, prompt building, transaction model, *and* the LLM engine — is
shared `commonMain` code, identical on both platforms.

> Verified: builds + links llama.cpp natively on both platforms; real streamed answers on an Android
> device and the iOS simulator. Note: inference speed is CPU-prefill-bound — fast on modern SoCs, slow
> (~25s/answer) on older chips like the Tensor G1. (Google's LiteRT-LM was tried first and dropped —
> its iOS support isn't production-ready: Swift-only packaging + broken simulator inference.)

## Project layout

- `shared/` — shared logic and Compose Multiplatform UI (chat screen, LLM abstraction, model,
  transactions).
- `androidApp/` — Android entry point.
- `iosApp/` — iOS entry point (open in Xcode to run).

Android and iOS run the same import, review and chat — no stubs.

## Running

Both platforms need a GGUF model on the device (download in-app via the URL field, or side-load).

- **Android:** `./gradlew :androidApp:assembleDebug`
- **iOS:** open `iosApp/` in Xcode and run.

## Tests

- `./gradlew :shared:testAndroidHostTest`
- `./gradlew :shared:iosSimulatorArm64Test`
