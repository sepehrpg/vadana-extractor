# Vadana Extractor Android — Agent Instructions

## Project

This is an Android application written in Kotlin using Jetpack Compose.

- Minimum SDK: 29
- Target SDK: 35
- Java: 17
- UI: Jetpack Compose and Material 3
- Background processing: WorkManager
- Networking: OkHttp
- Media processing: FFmpegKit
- Supported ABI: arm64-v8a

## General rules

- Do not change application behavior unless the task explicitly requires it.
- Do not commit generated files, build outputs, APKs, AABs, IDE files, or secrets.
- Do not add new dependencies without explaining why they are required.
- Keep changes focused and suitable for code review.
- Do not modify release signing configuration.
- Never commit session tokens, API keys, URLs containing credentials, or keystore files.

## Language rules

- Source-code comments and KDoc must be written in English.
- Class, function, variable, file, and package names must be English.
- User-facing application text may remain Persian.
- Do not hard-code user-facing text in Kotlin files.
- Store user-facing text in Android string resources.
- Maintain correct RTL support for Persian UI.

## Kotlin style

- Follow official Kotlin coding conventions.
- Prefer immutable values.
- Avoid `!!`.
- Avoid catching `Throwable` unless cancellation and fatal errors are handled correctly.
- Preserve `CancellationException`.
- Use descriptive names.
- Keep functions focused and reasonably short.
- Prefer sealed interfaces or enums for finite UI states.
- Use structured concurrency.
- Do not use `GlobalScope`.

## Compose rules

- Use Material 3 components.
- Hoist UI state where appropriate.
- Keep composables small and reusable.
- Add previews for important screens and reusable components.
- Support dark mode.
- Support RTL layouts.
- Add content descriptions to interactive icons.
- Avoid hard-coded dimensions and colors where a theme token is suitable.
- Do not perform I/O directly inside composables.

## Architecture

Keep responsibilities separated:

- `data`: network access, package access, parsers
- `domain`: models and business rules
- `media`: FFmpeg and media composition
- `render`: whiteboard and PDF rendering
- `storage`: secure and public storage
- `worker`: long-running WorkManager jobs
- `ui`: Compose screens, components, state, and theme

Do not move files between layers without a clear architectural reason.

## Required validation

Before completing a task, run:

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

If a command cannot run, explain the exact reason.

## Pull requests

Each pull request must contain:

* A concise summary
* Files or areas changed
* Behavior changes
* Test results
* Screenshots for visible UI changes when possible
* Known limitations or follow-up tasks
