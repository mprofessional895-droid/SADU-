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
- `Prompt.kt` - the two prompts. `Prompt.chat` deliberately contains no tool protocol, no menu and
  no JSON at all; `Prompt.control` carries the menu and the command examples.
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
  The internet still works, gated by `Prefs.internetEnabled` as always.
- **Control** - everything: rules, tools, accessibility, SMS, calls.

The mode is a permission, not a hint. It is enforced in `ModePolicy` and inside `Tools.run`, so a
model that ignores its instructions still cannot touch the phone. `Prefs.mode` defaults to Chat;
a missing or unreadable value reads as Chat. Each saved conversation records the mode it was
started in and shows it as a tag in the chat list.

## Release

Version lives in `app/build.gradle.kts` (`versionCode`/`versionName`). Releases are debug-signed and
published per version (`v0.5`, ...) with the APK attached, so the phone can install without Studio.

## CI

`.github/workflows/build.yml` builds a debug APK and publishes a GitHub Release
(`build-<run_number>`) on push to `main`. Pushing workflow-file changes needs a token with
the `workflow` scope.
