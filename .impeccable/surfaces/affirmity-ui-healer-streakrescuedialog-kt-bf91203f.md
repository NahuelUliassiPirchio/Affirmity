---
version: 1
slug: "affirmity-ui-healer-streakrescuedialog-kt-bf91203f"
primary_target: "app/src/main/java/com/pirxhio/affirmity/ui/healer/StreakRescueDialog.kt"
related_targets: []
---

# Streak rescue dialog

Scope: modal dialog over the main scaffold. Mode: Operate (one time-bound decision). Extends the incumbent teal Material 3 world; no new identity.

Trigger: app open or "healer" notification tap while `HealerActivation.Available`; once per day after dismissal (notification tap overrides); never when no healer is held; never stacked on onboarding/healer-granted/paywall.

## Direction contract

THESIS: The streak is a chain with one broken link, and the healer mends it in front of you. Refuses the generic "warning icon + paragraph + OK" alert.

OWN-WORLD: Incumbent theme roles (teal primary, surfaceContainerHigh dialog, Serif headline / SansSerif body). Warm flame accent only on lit streak days; teal mended heart (`ic_heart_mended`, untinted) is the only healer glyph.

STORY: User sees which day slipped, how many days are at stake, and that one tap saves them; dismissing is guilt-free.

FIRST VIEWPORT: Top: three-day chain (day before = lit flame, yesterday = dashed empty ring with the connector broken on both sides, today = outline flame), labels under each. Then headline "Ayer se te pasó un día", one body line with the streak count, healer-count line, short fine print. Bottom: full-width filled "Usar sanador", text "Ahora no" beneath.

SIGNATURE INTERACTION: On apply, the dashed ring strokes solid, connectors draw closed toward it, the mended heart springs in, headline crossfades to "Racha recuperada"; reduced motion = instant swap.

FORM: Extension of existing surface (no roll; seed key n/a).

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
