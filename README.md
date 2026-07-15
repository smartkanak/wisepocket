# WisePocket

A Kotlin Multiplatform (Android + iOS) personal-finance app. The goal: turn raw bank-statement PDFs
into a gamified, visual "Financial Wrapped" experience plus a chat interface — with **all LLM
inference running on-device**, so there's no backend server and data stays private.

Open-source MVP, built feature by feature. The current slice is **chat over your transactions**: ask
questions in natural language and get answers grounded in your (currently mock) transaction data.

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

## Running

Both platforms need a GGUF model on the device (download in-app via the URL field, or side-load).

- **Android:** `./gradlew :androidApp:assembleDebug`
- **iOS:** open `iosApp/` in Xcode and run.

## Tests

- `./gradlew :shared:testAndroidHostTest`
- `./gradlew :shared:iosSimulatorArm64Test`
