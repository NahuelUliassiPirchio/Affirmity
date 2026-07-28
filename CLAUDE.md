# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

Affirmity is a native Android app built with Kotlin and Jetpack Compose. Application ID / namespace: `com.pirxhio.affirmity`. The project is in an early/scaffold state (default Android Studio Compose template with a placeholder `AppDestinations` nav enum for Home/Favorites/Profile).

- minSdk 24, targetSdk 36, compileSdk 36
- Kotlin 2.2.10, AGP 9.3.1, Compose BOM 2025.12.00
- Single Gradle module: `:app`

## Commands

Use the Gradle wrapper (`gradlew.bat` on Windows / `./gradlew` in Bash) from the repo root.

- Build debug APK: `gradlew.bat assembleDebug`
- Build release APK: `gradlew.bat assembleRelease`
- Run unit tests (JVM, `app/src/test`): `gradlew.bat testDebugUnitTest`
- Run a single unit test class: `gradlew.bat testDebugUnitTest --tests "com.pirxhio.affirmity.ExampleUnitTest"`
- Run instrumented tests (`app/src/androidTest`, requires a connected device/emulator): `gradlew.bat connectedDebugAndroidTest`
- Lint: `gradlew.bat lint`
- Clean: `gradlew.bat clean`

## Architecture

- `app/src/main/java/com/pirxhio/affirmity/MainActivity.kt` — single entry point. Hosts `AffirmityApp()`, a `NavigationSuiteScaffold` that switches between destinations defined by the `AppDestinations` enum (icon + label pairs, driven by `rememberSaveable` state). New screens should be added as Composables wired into this destination switch rather than via a separate navigation library, unless that's introduced later.
- `app/src/main/java/com/pirxhio/affirmity/ui/theme/` — Compose theming (`Color.kt`, `Type.kt`, `Theme.kt`) exposing `AffirmityTheme`, which wraps all UI in `MainActivity`.
- Dependency versions are centralized in `gradle/libs.versions.toml` (version catalog) and referenced via `libs.*` in `app/build.gradle.kts` — add new dependencies there, not as inline coordinates.
