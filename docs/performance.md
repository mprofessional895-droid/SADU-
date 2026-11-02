# Inference performance and stability

What the app actually runs, what was changed, and how to measure it.

## 1. The engine stack, as it is

- `brain/LlamaEngine.kt` - the GGUF engine. One `llama_model` plus one `llama_context` behind a
  native handle, sampling and streaming through a JNI callback (`app/src/main/cpp/llama_jni.cpp`).
  `n_gpu_layers = 0` (CPU only), `n_ctx` from Settings (default 4096), `n_batch = 512`,
  `n_ubatch = n_batch`, `n_threads` from `brain/CpuTopology.kt`.
- `brain/OfflineBrain.kt` - wraps an `Engine` with a chat template and the sampling for the mode.
- `brain/BrainRouter.kt` - picks cloud or local per request, with a fallback path.
- `SaduViewModel.submit()` - the single turn pipeline: probe router, build request, stream deltas
  into a `StreamBuffer`, pace them to the UI (`paceStream`), persist the message.
- `LlmEngine.kt` - the MediaPipe `.task` engine, kept as the alternative runtime.

There is no second inference runtime and no GPU path. The work below stays on llama.cpp CPU.

## 2. Bottlenecks found

1. **No feedback loop between the phone's state and the thread count.** Threads were fixed at
   load, so a hot phone kept burning four cores and throttled into slower generation.
2. **No memory guard.** A model was loaded without checking free RAM, and a `bad_alloc` from
   llama.cpp arrived as an opaque failure.
3. **Generation died with the app.** Leaving the app or turning the screen off let Android kill
   the process mid-answer, and a cancelled turn dropped the text already written.
4. **No measurement.** `PerfLine` was logged but nothing surfaced to the user, so "is this fast?"
   had no answer.

## 3. What changed

- `perf/PerformanceManager.kt` - reads the platform thermal band (`PowerManager.currentThermalStatus`)
  and memory (`ActivityManager.MemoryInfo`, own-process PSS) and decides threads, token ceiling and
  context size. The rules live in `object PerfPlan` so they are unit-testable with no Android.
- `perf/PerfMonitor.kt` - one small snapshot, written by the turn and read by the panel.
- `perf/GenerationService.kt` - data-sync foreground service with a low-importance notification and
  a Stop action; a partial wake lock bounded to 15 minutes; `START_NOT_STICKY`.
- `LlamaEngine` - takes a thread provider, exposes `setThreads()` (mapped to
  `llama_set_n_threads` via a new `nativeSetThreads`), turns a load-time `OutOfMemoryError` into a
  readable message.
- `SaduViewModel` - chooses threads and the token cap per turn, unloads the model on
  `onTrimMemory`/`onLowMemory` when idle, keeps the partial answer on cancellation, refills the
  notification at most once a second.
- `ui/PerformancePanel.kt` - the dashboard: tok/s, prompt tok/s, tokens, timings, threads, context,
  model size, app and phone memory, heat band, background state, plus the three-mode switch,
  background toggle and "unload now".

## 4. Modes

| Mode | Threads (4 big cores) | Under heat | Under memory pressure | Token cap |
|---|---|---|---|---|
| Performance | 4 | 3 | 3 | setting |
| Balanced (default) | 3 | 2 | 2 | setting |
| Battery saver | 2 | 1 | 1 | 768 |

Heat `SEVERE`/`CRITICAL` also caps new tokens at 256; memory pressure caps at 384 and shrinks the
context to 2048. A load is refused when `model + 512 MB` does not fit in free RAM.

These are ceilings, not promises: the goal is that a long answer finishes at a steady speed on a
warm phone instead of stalling.

## 5. Measuring it

The panel shows the numbers the last turn measured; llama.cpp's own perf line is the source
(`prompt tok/s`, `gen tok/s`, `ctx`, `threads`).

Protocol - same model file, same quantization, same 4096 context, same prompt:

1. Cold start: open the app, wait for "Ready", ask a 200-word question, note Generation tok/s from
   the panel.
2. Repeat twice; the third value is the warm-phone number.
3. Switch Performance -> Balanced -> Saver and repeat step 1 for each.
4. Leave a long answer running and lock the screen: the reply must finish and reappear in the chat.
5. Fill RAM (open several heavy apps), return to Sadu, ask a question: it should either answer or
   refuse with the memory note - never crash.

What has been verified here: the app compiles, 429 unit tests pass (12 new ones cover the mode
rules), and the APK builds with the native library. What has **not** been verified: tokens/sec on a
real Nord CE 4, sustained heat behaviour, and screen-off generation - there is no device or
emulator in this workspace. Run the protocol above on the phone and the panel will tell you the
real numbers.
