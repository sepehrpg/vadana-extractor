# Vadana Extractor

Android extractor for Adobe Connect / Vadana recordings, built with **Kotlin** and **Jetpack Compose**.

This project ports the core logic from `phoseinq/vadana-extractor` to an Android architecture:

- Parse recording links and session tokens.
- Download Adobe Connect offline packages with progress reporting and retry support.
- Extract Share Pod files with their real names.
- Read `mainstream.xml`, `indexstream.xml`, and all `ftcontent*.xml` files.
- Reconstruct pens, text, shape deletion, and whiteboard page changes.
- Place shared PDFs behind handwriting as page backgrounds.
- Build multi-page whiteboard PDFs.
- Extract and merge `cameraVoip*.flv` audio segments.
- Rebuild screen shares and PDF/pointer events on the timeline.
- Produce synchronized MP4 output with H.264/MPEG-4 video and AAC audio.
- Run foreground WorkManager jobs with progress notifications and cancellation.
- Save outputs in `Downloads/Vadana`, `Movies/Vadana`, and `Music/Vadana`.
- Encrypt temporary Worker URLs and session data with Android Keystore.
- Protect against SSRF, cross-domain redirects, and filename/path traversal.

## Requirements

- A recent Android Studio version.
- Android 10 or later (`minSdk 29`).
- Internet access for Gradle dependencies.

## Running

1. Open the folder in Android Studio.
2. Let Gradle Sync finish.
3. Select an Android 10+ device or emulator.
4. Run the `app` module.

Command-line build:

```bash
./gradlew assembleDebug
```

On Windows:

```powershell
.\gradlew.bat assembleDebug
```

The wrapper scripts download the configured Gradle distribution on first use. You can also use Android Studio's standard **Generate Gradle Wrapper** action if you need to regenerate wrapper artifacts.

## Usage

1. Enter the full recording link. Private classes must include the `session=` value in the link.
2. Tap **Analyze class**.
3. Select the outputs you want.
4. Select video quality and start extraction.
5. Processing continues in the foreground and can be cancelled from the notification.

## Structure

```text
app/src/main/java/ir/vadana/extractor/
├── data/       Adobe Connect download, ZIP, and parser logic
├── domain/     App-independent models
├── render/     Canvas, PDF, and frame generation
├── media/      FFmpeg, audio, and video composition
├── storage/    MediaStore and Worker request encryption
├── worker/     Foreground processing
└── ui/         Compose UI and ViewModel
```

## FFmpeg engine

The project uses this Maven package:

```kotlin
implementation("io.github.arthenica:ffmpeg-kit-https:6.0-2")
```

This package provides an FFmpegKit-compatible API and native libraries compatible with Android's newer page-size requirements. The code tries `libx264` first and automatically falls back to Android's built-in `mpeg4` encoder when the encoder is unavailable.

For store releases, review the exact binary license and enabled FFmpeg build features, and ship the required notices with the app.

## Practical limitations

- Building 1080p/1440p video on low-end phones can be slow and may heat the device.
- Very large recordings can require several gigabytes of free temporary space.
- Unusual Adobe Connect layouts may require parser or PDF-selection improvements.
- Output is functionally equivalent to the Python version, but it is not byte-for-byte identical because device fonts and encoders vary.
- Some old servers use HTTP. Cleartext is enabled for compatibility, but TLS is preferred whenever available.

## Testing and CI

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Basic tests for link parsing, shared files, and streams live under `app/src/test`. The `.github/workflows/android.yml` file also builds and tests the project on GitHub Actions and stores the debug APK as an artifact.

See `BUILD_STATUS.md` for package validation details.

## License and attribution

This repository is published under the MIT license. Its logic is derived from this MIT-licensed project:

- https://github.com/phoseinq/vadana-extractor

License text and dependency notes are available in `LICENSE` and `THIRD_PARTY_NOTICES.md`.
