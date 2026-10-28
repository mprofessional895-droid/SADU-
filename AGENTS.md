# Jarvis (Android assistant)

Personal, sideloaded Android voice assistant. Package `com.jarvis.assistant`.
Offline AI (on-device Gemma via MediaPipe LLM Inference) + phone control + accessibility + speech.

## Build

Requires JDK 17, Android SDK (platform 34, build-tools 34.0.0), Gradle 8.9.

```bash
gradle assembleDebug            # -> app/build/outputs/apk/debug/app-debug.apk
gradle assembleDebug --no-daemon --console=plain
```

`local.properties` must contain `sdk.dir=<path to Android SDK>` (git-ignored).

The in-app model is a Gemma 3 1B `.task` file the user imports once into app storage
(`filesDir/model.task`); it is not in the repo. `LlmEngine.isModelPresent()` requires >50 MB.

## Module map (`app/src/main/java/com/jarvis/assistant/`)

- `MainActivity.kt` - entry point; edge-to-edge; requests the permissions the catalog asks for;
  hosts the Compose shell.
- `JarvisViewModel.kt` - all UI state: five sections, the assistant state machine (idle/ listening/
  processing/ speaking/ error), conversations, toggles, model load/import, voice wiring.
- `AiState.kt` - `AiState` (assistant states) and `Tab` (the five sections).
- `AssistantText.kt` / `ReplyClassifier.kt` - pure wording helpers and the answer/notice/failure
  classifier, kept out of the composables so they can be unit tested.
- `PhoneActionCatalog.kt` - every phone ability with its permission, accessibility need, group and
  example; drives the Phone Actions screen and the permission request list. Android-free.
- `ChatHistory.kt` - saves every conversation to `filesDir/chats.json` for the chat list.
- `ReplyText.kt` - cleans raw model output (literal `\n`, prompt tokens, code fences).
- `ui/` - Compose shell and screens: `JarvisApp.kt` (column: content + navigation bar, cross-fade
  between sections), `JarvisNavBar.kt` (five items), `Theme.kt` (monochrome `Ink` palette, type,
  `rememberReducedMotion()`), `MarkdownText.kt` (bold/headings/bullets in plain text).
- `ui/components/` - `Common.kt` (SurfaceCard, buttons, fields, empty states, `FadeIn`) and
  `Orb.kt` (the concentric-ring orb with a real microphone level).
- `ui/screens/` - `HomeScreen.kt`, `ConversationsScreen.kt`, `MemoryScreen.kt`,
  `PhoneActionsScreen.kt`, `SettingsScreen.kt`. Each file exposes a thin `*Screen(vm)` wrapper and a
  stateless `*Content(...)` composable, which is what the screenshot tests render.
- `Mode.kt` - `Mode` (Chat / Control) and `ModePolicy`, the single place that decides whether a
  tool may run and what to say when it may not. Android-free, so it is unit tested directly.
- `Prompt.kt` - a router only, no wording of its own: `Prompt.chat`/`Prompt.control` pick the
  builder and `Prompt.local` wraps them in Gemma's chat template. The words live in `prompt/`.
- `prompt/ChatPrompt.kt` - Chat mode's instructions (persona + reply length + optional language,
  the user's own extra, facts, web-results line). Deliberately tiny; no tool protocol, no menu,
  no JSON, and nothing about the phone or about modes.
- `prompt/ControlPrompt.kt` - the tool protocol, the menu and the command examples.
- `prompt/GemmaChatTemplate.kt` - Gemma's `<start_of_turn>` template, frozen (`VERSION`).
- `ChatPolicy.kt` - Chat mode's two code-level checks: a clear phone request is refused before the
  model is asked, and a reply that claims the deed is flagged. Android-free.
- `Sampling.kt` - temperature/top-k/top-p per mode: `Sampling.chat(prefs.chatTemperature)` for
  conversation, `Sampling.Command` (0.6) for Control so tool JSON stays valid.
- `Assistant.kt` - the router: rules first (Chat mode uses only the rules that need no phone
  access), then the brain (JSON tool-calling in Control mode, plain conversation in Chat mode).
- `Tools.kt` - tool specs + dispatch; the AI's list of abilities.
- `Actions.kt` - what actually touches the phone (rules, intents, SMS, calls, torch, screen control).
- `brain/` - `Brain` interface + `OfflineBrain` (the seam for a future cloud/hybrid brain).
- `control/JarvisAccessibilityService.kt` - tap/type/scroll/back/home/recents/read screen.
- `voice/` - `Voice.kt` (TTS + STT), `WakeWordService.kt` (experimental "Hey Jarvis", off by default).
- `Prefs.kt` / `Memory.kt` - SharedPreferences: settings, and short user facts.

## Conventions

- Offline by default. Network tools check `Prefs.internetEnabled` and refuse when off.
- SMS asks to confirm and calls open the dialer first, unless the user changes Settings.
- Accessibility control only acts when the user gives a command.
- Keep `Brain` as the swap point for cloud/hybrid; do not shortcut it.
- All model output goes through `ReplyText.clean()` before it is stored or shown. Small models
  copy the JSON escaping from the tool-call examples and emit literal `\n`, so never trust raw text.
- `JarvisViewModel.sessions` is the single source of truth for conversations; `messages` is derived.
- Design language: background `#050505`, cards `#101010`, borders `#292929`, text `#F5F5F2`,
  secondary `#858585`, accent white. No colour, no glow, no gradients beyond the orb's own shading.
  Keep whitespace, hairline borders and 48dp-plus touch targets.
- Animation must communicate state, never run for decoration. Anything that moves must consult
  `rememberReducedMotion()` (system "remove animations") and go still when it is set.
- Layout must survive a small phone and a short window: Home switches to a scrolling layout when
  the content box is under 520dp tall, so nothing is ever clipped; otherwise the orb takes the
  leftover height and is never larger than its box.
- Tests: `gradle testDebugUnitTest`. Pure logic: `CommandParserTest`, `ReplyTextTest`,
  `ReplyClassifierTest`, `AssistantTextTest`, `PhoneActionCatalogTest`, `ModeTest` (policy and
  prompts) and `ModePersistenceTest` (saved mode, saved chat tags, and that the Chat-mode rules
  never act on the phone). UI layout:
  `ScreensRenderTest` renders every screen off-device (Robolectric + Roborazzi) and writes real
  PNGs to `app/build/screenshots/` - committed copies live in `docs/screenshots/`.
  There is no emulator in this environment, so a render is layout evidence, not runtime proof.

## Modes

Two modes, chosen in Settings or from the switcher on Home and in a chat header:

- **Chat** - conversation only. The prompt has no tool syntax, the rule layer runs only the rules
  that need no phone access (memory, clock, network), and `Tools.run` refuses every phone tool.
  Chat mode also never opens anything on the phone itself: no browser tab, no Play Store. With
  `Prefs.internetEnabled` off a search request gets the honest "Internet is off" sentence;
  otherwise the Assistant looks it up, or the model answers.
  The internet still works, gated by `Prefs.internetEnabled` as always.
- **Control** - everything: rules, tools, accessibility, SMS, calls.

The mode is a permission, not a hint. It is enforced in `ModePolicy` and inside `Tools.run`, so a
model that ignores its instructions still cannot touch the phone. Chat mode's prompt says nothing
about the phone, because a line like "you cannot touch the phone" costs tokens and can be argued
with; the app is what refuses: `ChatPolicy.phoneIntent` catches a clear request before the model is
asked, and `Assistant.lastActionClaim` (from `ChatPolicy.claimsPhoneAction`) adds a note under a
reply that claims the deed. The two prompts live in separate files so Chat and Control can be
changed and released without touching each other. `Prefs.mode` defaults to Chat;
a missing or unreadable value reads as Chat. Each saved conversation records the mode it was
started in and shows it as a tag in the chat list.

## Release

Version lives in `app/build.gradle.kts` (`versionCode`/`versionName`). Releases are debug-signed and
published per version (`v0.5`, ...) with the APK attached, so the phone can install without Studio.

## CI

`.github/workflows/build.yml` builds a debug APK and publishes a GitHub Release
(`build-<run_number>`) on push to `main`. Pushing workflow-file changes needs a token with
the `workflow` scope.

## The on-device engine, and where its ceiling is

`LlmEngine` wraps MediaPipe `LlmInference` (tasks-genai 0.10.27). Its API surface was read
straight out of the AAR (`javap` on `classes.jar`) and is small:

- **Session sampling** (`LlmInferenceSessionOptions.Builder`): `topK`, `topP`, `temperature`,
  `randomSeed`, plus optional LoRA, graph options, a constraint handle and `promptTemplates`.
  There is **no repetition penalty, no frequency/presence penalty and no min-p** - nothing that
  damps the loops a 1B model falls into. PocketPal runs llama.cpp, which has all of them, so the
  same Gemma weights do not behave the same in the two apps.
- **Engine options** (`LlmInferenceOptions.Builder`): `maxTokens` (the whole sequence - prompt
  plus reply - and the KV cache), `maxTopK`, `maxNumImages`, `setPreferredBackend(CPU/GPU/DEFAULT)`.
  The engine's `maxTopK` defaults to 40, so a session that asks for `setTopK(64)` is capped in
  practice. `LlmEngine` pins the CPU backend and raises the engine's `maxTopK` to
  `Sampling.MaxTopK` (64), the highest MediaPipe honours.

What this means for Chat quality: with no repetition penalty to lean on, temperature is the only
knob that damps a loop, so Chat mode samples at `Prefs.chatTemperature` (0.5-1.0, default 0.7 -
`Sampling.chat`) instead of the Gemma card's 1.0, and the Chat prompt was cut from roughly 300
tokens of instructions to a couple of hundred characters. There is still no repetition penalty,
and a reply can still run out of the `maxTokens` window rather than finish. Replies stream through
`LlmEngine.generateStream` (MediaPipe's `generateResponseAsync` progress callback hands over the
text so far, so the delta is worked out here) and `JarvisViewModel.stopGenerating` stops one.
`Prompt.local` builds the Gemma chat template by hand; MediaPipe 0.10.27 does expose
`PromptTemplates`, which was not used.

`Prefs.replyLength` (Short 512 / Normal 1200 / Long 3000) is applied twice: as `max_tokens` for a
cloud model, and as a sentence in the prompt for this local one, which has no output cap of its
own. Regression test worth keeping: `PromptTest.aLongPromptNeverEatsTheUsersWords` - the
instructions and the user's message share one character cap, and the instructions must give way.

## Chat mode's own settings

Chat mode has a Settings card of its own (`ui/screens/SettingsChat.kt`, section "Chat mode"):
`Prefs.chatStyle` (Minimal - the default, no personality imposed - or Friendly),
`Prefs.chatTemperature` and `Prefs.chatInstructions`. None of it reaches Control mode, which keeps
its own sampler and instructions, so an experiment with the answer style cannot make a tool call
misfire. While a local reply is being written the composer offers Stop
(`JarvisViewModel.canStop`), which reaches `LlmEngine.cancel`.

Regression tests worth keeping for this split: `PromptTest.chatInstructionsStaySmall` (the Chat
prompt must stay under ~700 characters), `ChatPolicyTest` (what counts as a phone request, and
what counts as claiming one), `AssistantPhoneGuardTest` (Chat refuses it and never asks the model;
Control still asks), `OfflineBrainStreamTest` (deltas, the template, the per-mode sampler) and
`ChatPromptTest`.

## Two engines: `.task` (MediaPipe) and `.gguf` (llama.cpp)

`brain/Engine.kt` is the seam. One interface, two implementations - `LlmEngine` (MediaPipe,
unchanged in behaviour) and `brain/LlamaEngine.kt` (llama.cpp) - and `brain/OfflineBrain.kt` wraps
either one, so nothing above it knows which answered. `brain/SwitchableBrain.kt` is the holder
`JarvisViewModel` swaps when the user changes engine; `Prefs.localEngine` (key `mediapipe` /
`llama`) remembers the choice, and both model files stay on the phone, so switching back is a
reload and nothing more. Only the chosen engine is ever loaded into memory.

The template is engine-specific: `prompt/GemmaChatTemplate.kt` is Gemma's format, written by hand,
and `prompt/ModelChatTemplate.kt` asks the loaded model for its own template through
`llama_jni.cpp` (`llama_model_chat_template` + `llama_chat_apply_template`), falling back to
Gemma's when the model carries none or the call fails. `brain/Gguf.kt` validates a picked file
before it is copied (magic `GGUF`, version 2-3, then `general.architecture` / `general.name` if
they are in the first 64 KB) - a `.task` in the `.gguf` slot is refused in a second, not after a
700 MB copy.

Native build: `app/src/main/cpp/CMakeLists.txt` fetches llama.cpp v0.7.0 through CMake
FetchContent (pinned by `URL_HASH` sha256) and builds `libjarvis_llama.so` plus llama's own
libraries; `app/src/main/cpp/llama_jni.cpp` is the JNI surface. It is wired into Gradle behind a
`hasNdk` check, so a machine without the NDK still builds and ships MediaPipe-only, and
`LlamaEngine.isAvailable` is then false with Settings saying so. On a phone, the `.gguf` engine
adds the repetition penalty and min-p that MediaPipe has no API for.

Building the native part in a constrained container needs `taskset -c 0-2` around Gradle: ninja
otherwise spawns ~100 clang processes and the OOM killer takes the Gradle daemon. Two memory
notes worth keeping: `-g0` on llama.cpp's targets (Android Gradle adds `-g` to everything, which
made `libllama.so` 42 MB before stripping) and `ANDROID_STL=c++_shared`.

Not verified: no `.gguf` has ever been loaded or run - there is no device and no emulator for this
project, and the library is arm64 only. What is proved by tests is everything up to inference:
header validation (`GgufTest`), the engine's refusals and bookkeeping (`LlamaEngineTest`), the
template fallback (`ModelChatTemplateTest`) and the engine-agnostic brain (`OfflineBrainStreamTest`).
