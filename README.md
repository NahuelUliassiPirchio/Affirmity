# Affirmity

Native Android app (Kotlin + Jetpack Compose) for daily affirmations and meditation practice.

## Features

### 1. Affirmations feed
Full-screen card stack of affirmations. Cards are shown in random order and advanced with a
vertical swipe (swipe down to go to the next card, looping back to the start of the shuffled
deck when exhausted). Each card renders a background (solid color or image) behind its text.

### 2. Add / manage affirmations
Users can create their own affirmations. Each affirmation is persisted using the JSON shape
described in [Affirmation data format](#affirmation-data-format) below.

Background images are supplied as a URL. On save, the app downloads the image once and stores it
in local app storage (`filesDir`); the persisted record references the local file path so cards
render correctly offline. The original URL is kept only as provenance, not as the render source.

### 3. Meditation timer
A dedicated tab with a circular dial timer: the user sets a duration by dragging around a clock
face, starts a countdown, and a sound plays when the timer reaches zero.

### 4. Daily tracker
Tracks, per calendar day, two independent streaks:
- Affirmations: complete once the user has viewed at least 2 affirmations that day.
- Meditation: complete once the user has completed a meditation session that day.

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
open-ended, introspective ("uncomfortable") questions, independent of the reminder notifications
in point 5.

## Affirmation data format

Draft JSON shape for a single affirmation (subject to the open questions below):

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

## Decisions

- **Persistence**: local-only (Room + DataStore), no backend/auth/sync.
- **Background images**: submitted as a URL, downloaded once and cached to local storage; the
  persisted reference is the local file path, not the remote URL.
- **Streaks**: affirmations and meditation each track their own independent streak, resetting on
  the first day their own condition is missed.
- **Initial content**: the affirmations feed starts empty; there is no bundled starter set — the
  user must add their own before the feed has anything to show.
- **Meditation sound**: a single sound bundled as an app resource, no per-user choice.
- **Background reliability**: streak/tracker resets and reminder/prompt notifications must fire
  correctly even when the app process isn't running, implying `WorkManager`/`AlarmManager`-backed
  scheduling rather than in-memory state tied to an open Activity.

## Status

Early scaffold stage — requirements above are being refined before implementation begins.
