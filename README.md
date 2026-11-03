# YTX Feature Lab

[![Build](https://github.com/shadowguarda/YTX-Feature-Lab/actions/workflows/build.yml/badge.svg)](https://github.com/shadowguarda/YTX-Feature-Lab/actions/workflows/build.yml)

A standalone Android sandbox for developing and validating self-contained UI features
before porting them into **YTXTube**. Each feature lives in its own package with no
dependencies on the host app, so it can be lifted over file-by-file.

Built with Kotlin, Jetpack Compose and Material 3.

## Features

| Module | Package | Description |
| --- | --- | --- |
| Dynamic theming | `theme` | Seed-colour scheme generator (HSL-derived light/dark palettes), System / Light / Dark / AMOLED modes, persisted via `SharedPreferences`. Includes a ready-made picker screen with ten preset accents. |
| Gesture settings | `gesture` | Persistent configuration for player gestures: double-tap seek step (5/10/15/30 s), swipe sensitivity, edge-zone width and left/right edge swap, plus a Material 3 bottom-sheet editor. |
| Ambient mode | `ambient` | Extracts vibrant and dark-muted colours from a thumbnail with AndroidX Palette (loaded through Coil) and renders an animated two-lobe radial gradient behind content. Results are cached per URL. |
| UI kit | `ui` | Shared helpers: `pressable` spring press-scale modifier, `focusRing` for TV/D-pad focus, `PillButton`, `SectionHeader` and `SheetCard`. |

`App.kt` hosts each feature in a tab so it can be exercised on a device or emulator.

## Tech stack

- Kotlin 2.0.21, Jetpack Compose (BOM 2024.09.03), Material 3
- Android Gradle Plugin 8.7.2, Gradle 8.9 (wrapper included)
- Coil 2.7 and AndroidX Palette for image loading and colour extraction
- Media3 ExoPlayer 1.5 (declared for upcoming player features)
- `minSdk` 24, `compileSdk` / `targetSdk` 35, JVM target 17

## Getting started

Requirements: JDK 17+ and the Android SDK (or Android Studio Ladybug or newer).

```bash
git clone https://github.com/shadowguarda/YTX-Feature-Lab.git
cd YTX-Feature-Lab
./gradlew assembleDebug        # app/build/outputs/apk/debug/
./gradlew installDebug         # install on a connected device
```

## Project structure

```
app/src/main/java/com/example/ytxfeaturelab/
├── App.kt               # Tabbed host for the feature demos
├── MainActivity.kt      # Initialises settings stores, sets Compose content
├── theme/               # Theme.kt, ThemeSettings.kt, ThemeScreen.kt
├── gesture/             # GestureSettings.kt (store + settings sheet)
├── ambient/             # Ambient.kt (palette extraction + background)
└── ui/                  # UiKit.kt (shared modifiers and components)
```

## Porting a feature into YTXTube

1. Copy the feature package (for example `theme/`) into the host app and adjust the package name.
2. Call the store's `init(context)` once at startup, as `MainActivity` does here.
3. Read state directly from the store object (backed by Compose snapshot state) so UI recomposes automatically.

## CI and releases

- **Build** (`.github/workflows/build.yml`): every push and pull request runs `./gradlew assembleDebug` and uploads the debug APK as a workflow artifact.
- **Release** (`.github/workflows/release.yml`): pushing a tag such as `v0.1.0` builds a signed release APK, verifies its signature with `apksigner`, and publishes it with a SHA-256 checksum to [GitHub Releases](https://github.com/shadowguarda/YTX-Feature-Lab/releases).

```bash
git tag v0.1.0 && git push origin v0.1.0
```

### Release signing

If the repository secrets below are set, the release is signed with your key, which keeps
the signature stable across versions so users can update in place. Without them, the
workflow generates a one-off key for that build; the APK installs fine, but a later build
signed with a different key cannot update over it.

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | Base64 of your `.jks` keystore (`base64 -w0 release.jks`) |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias |
| `KEY_PASSWORD` | Key password |

Never commit keystores or passwords to the repository.
