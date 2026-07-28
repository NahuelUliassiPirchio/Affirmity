# Affirmity

A native Android app (Kotlin + Jetpack Compose) for tracking a personal daily practice:
reading affirmations and meditating, with streaks that keep you honest about it.

This is a personal practice project — built to work through a real, self-contained Android
architecture (Compose UI, Room, DataStore, WorkManager) rather than to ship on the Play Store.
It isn't published, but it's built the way a real app would be.

## Why this exists

Habit apps usually outsource the hard part to a backend. The goal here was the opposite:
a fully local, offline-first app that still behaves like a "real" product —
persistent state, background-scheduled notifications that fire even when the app isn't running,
and streak tracking that survives app restarts and day boundaries correctly.

Secondary goal: use the project as a sandbox for modern Android patterns — Compose Material 3,
Room + DataStore for persistence, and WorkManager-based scheduling — without the overhead of
auth, sync, or a server.

## Features

### 1. Affirmations feed
Full-screen card stack of affirmations. Cards are shown in random order and advanced with a
vertical swipe (swipe down to go to the next card, looping back to the start of the shuffled
deck when exhausted). Each card renders a background (solid color or image) behind its text.

### 2. Add / manage affirmations
Users can create their own affirmations, one at a time or via bulk JSON paste. Each affirmation
is persisted using the JSON shape described in [Affirmation data format](#affirmation-data-format)
below.

Background images are supplied as a URL (or picked from the device gallery). On save, the app
downloads/copies the image once and stores it in local app storage (`filesDir`); the persisted
record references the local file path so cards render correctly offline. The original URL is
kept only as provenance, not as the render source.

### 3. Meditation timer
A dedicated tab with a circular dial timer: the user sets a duration by dragging around a clock
face, starts a countdown, and a gong sound plays when the timer reaches zero.

### 4. Daily tracker
Tracks, per calendar day, two independent streaks:
- **Affirmations**: complete once the user has viewed at least 2 affirmations that day.
- **Meditation**: complete once the user has completed a meditation session that day.

Each streak resets to zero the first day its own condition is missed; they don't depend on
each other.

### 5. Reminders
Local notifications prompting the user to meditate and/or view affirmations, fired at a random
time within a user-configured time window (e.g. "sometime between 9am and 9pm") rather than at a
fixed time.

### 6. Affirmation-in-notification
The affirmation reminder notification can include the text of an affirmation directly in its body.

### 7. Reflection prompts
A separate notification channel sends prompts at random times within a configured window, with
open-ended, introspective questions, independent of the reminder notifications in point 5.

## Affirmation data format

```json
{
  "title": "I am capable of change",
  "subtitle": "Growth starts with a single choice",
  "background": {
    "type": "color",
    "value": "#2A9D8F"
  }
}
```

`background.type` is either `"color"` (hex value) or `"image"` (a URI/resource reference).
Affirmations can also be bulk-imported by pasting a JSON array of objects in this shape.

## Architecture decisions

- **Persistence**: local-only (Room + DataStore), no backend/auth/sync.
- **Background images**: submitted as a URL or picked from the gallery, cached once to local
  storage; the persisted reference is the local file path, not the remote source.
- **Streaks**: affirmations and meditation each track their own independent streak, resetting on
  the first day their own condition is missed. Persisted as a compact `(streakDays,
  lastCompletedEpochDay)` window rather than a raw day-by-day array.
- **Initial content**: the affirmations feed starts empty; there is no bundled starter set — the
  user must add their own before the feed has anything to show.
- **Meditation sound**: a single sound bundled as an app resource, no per-user choice.
- **Background reliability**: streak/tracker resets and reminder/prompt notifications fire
  correctly even when the app process isn't running, via `WorkManager`-backed self-rescheduling
  work chains rather than in-memory state tied to an open Activity.

## Tech stack

- Kotlin 2.2.10, Jetpack Compose (Material 3), Compose BOM 2025.12.00
- Room (structured data) + DataStore Preferences (streaks, settings)
- WorkManager for background scheduling
- minSdk 24, targetSdk / compileSdk 36
- Single Gradle module: `:app`

## Status

Actively developed as a personal practice project. Core features (feed, meditation timer, daily
tracker, notifications, bulk import) are implemented and working.
