---
name: Affirmity
description: A calm daily sanctuary for affirmations and meditation.
colors:
  sanctuary-teal: "#00696F"
  sanctuary-teal-bright: "#5BBCC3"
  sanctuary-teal-deep: "#00494D"
  sanctuary-teal-ink: "#00252A"
  sanctuary-teal-selected: "#18767D"
  quiet-grey: "#5E5E5E"
  paper: "#F9F9F9"
  paper-lowest: "#FFFFFF"
  paper-low: "#F3F3F3"
  paper-container: "#EEEEEE"
  paper-high: "#E8E8E8"
  paper-highest: "#E2E2E2"
  ink: "#1A1C1C"
  ink-muted: "#3E494A"
  outline: "#6E797A"
  outline-soft: "#BDC9C9"
  night: "#000000"
  night-container: "#111111"
  night-high: "#1C1C1C"
  night-highest: "#272727"
  night-ink-muted: "#B0BEC0"
  night-outline: "#5C6B6D"
  night-outline-soft: "#3A4344"
  error: "#BA1A1A"
  premium-light: "#FFE0C7"
  on-premium-light: "#7A3B00"
  premium-dark: "#5C3200"
  on-premium-dark: "#FFD9AD"
  cover-ground: "#0F1216"
  on-cover-ground: "#F2F3F5"
  aurora-sea: "#3FA796"
  aurora-blush: "#E8A9A0"
  tracker-meditation: "#57B6B6"
  tracker-affirmation: "#E3956C"
  tracker-flame-lit: "#FDA856"
  tracker-healed-heart: "#D2CEFD"
  tracker-surface: "#232532"
  tracker-text: "#E9E9ED"
  tracker-caption: "#B2B6CA"
  streak-ember: "#E8590C"
  streak-ember-night: "#FF9A4D"
  streak-ember-chip: "#B93F00"
  streak-ground: "#FFEFD9"
  streak-ground-night: "#3A2412"
  streak-ink: "#3B1F0A"
  reflection-accent: "#456582"
  reflection-ground: "#E9EEF3"
typography:
  headline-large:
    fontFamily: "Serif (Playfair Display intent)"
    fontSize: "40px"
    fontWeight: 700
    lineHeight: "44px"
    letterSpacing: "-0.5px"
  headline-medium:
    fontFamily: "Serif (Playfair Display intent)"
    fontSize: "32px"
    fontWeight: 600
    lineHeight: "38px"
  headline-small:
    fontFamily: "Serif (Playfair Display intent)"
    fontSize: "24px"
    fontWeight: 600
    lineHeight: "30px"
  title-large:
    fontFamily: "Serif (Playfair Display intent)"
    fontSize: "22px"
    fontWeight: 500
    lineHeight: "28px"
  body-large:
    fontFamily: "Sans-serif (Work Sans intent)"
    fontSize: "18px"
    fontWeight: 400
    lineHeight: "28px"
    letterSpacing: "0.5px"
  body-medium:
    fontFamily: "Sans-serif (Work Sans intent)"
    fontSize: "16px"
    fontWeight: 400
    lineHeight: "24px"
    letterSpacing: "0.5px"
  body-small:
    fontFamily: "Sans-serif (Work Sans intent)"
    fontSize: "14px"
    fontWeight: 400
    lineHeight: "20px"
    letterSpacing: "0.25px"
  label-large:
    fontFamily: "Sans-serif (Work Sans intent)"
    fontSize: "14px"
    fontWeight: 600
    lineHeight: "20px"
    letterSpacing: "0.1px"
  label-medium:
    fontFamily: "Sans-serif (Work Sans intent)"
    fontSize: "12px"
    fontWeight: 600
    lineHeight: "16px"
    letterSpacing: "0.5px"
  label-small:
    fontFamily: "Sans-serif (Work Sans intent)"
    fontSize: "10px"
    fontWeight: 600
    lineHeight: "14px"
    letterSpacing: "0.5px"
rounded:
  xs: "4px"
  sm: "8px"
  md: "12px"
  lg: "16px"
  xl: "20px"
  widget: "22px"
  pill-floating: "24px"
  full: "9999px"
spacing:
  xxs: "2px"
  xs: "4px"
  sm: "8px"
  md: "12px"
  lg: "16px"
  xl: "20px"
  xxl: "24px"
  xxxl: "32px"
  touch: "48px"
components:
  button-primary:
    backgroundColor: "{colors.sanctuary-teal}"
    textColor: "{colors.paper-lowest}"
    typography: "{typography.label-large}"
    rounded: "{rounded.full}"
    height: "40px"
  card-surface:
    backgroundColor: "{colors.paper-container}"
    textColor: "{colors.ink}"
    rounded: "{rounded.xl}"
    padding: "16px"
  chip-filter-selected:
    backgroundColor: "{colors.sanctuary-teal-selected}"
    textColor: "{colors.paper-lowest}"
    rounded: "{rounded.sm}"
    height: "32px"
  nav-indicator-selected:
    backgroundColor: "{colors.sanctuary-teal-selected}"
    textColor: "{colors.paper-lowest}"
    rounded: "{rounded.full}"
  premium-badge:
    backgroundColor: "{colors.premium-light}"
    textColor: "{colors.on-premium-light}"
    typography: "{typography.label-small}"
    rounded: "{rounded.full}"
  affirmation-plate:
    textColor: "{colors.paper-lowest}"
    typography: "{typography.headline-large}"
    rounded: "{rounded.md}"
  tracker-widget:
    backgroundColor: "{colors.tracker-surface}"
    textColor: "{colors.tracker-text}"
    rounded: "{rounded.widget}"
    padding: "16px"
---

# Design System: Affirmity

<!-- Recorded from the shipped implementation (scan mode). Values are Android dp/sp; px in frontmatter means 1px = 1dp/1sp. -->

## Overview

**Creative North Star: "The Inner Sanctuary"**

Affirmity is a private, safe place the user steps into for a few minutes a day. The content leads: a full-bleed affirmation card, a breathing orb over a slow aurora, a generated meditation cover. The chrome (Material 3 components on flat tonal surfaces) steps back so nothing competes with the moment.

The mood is calm and spacious. Light mode sits on soft paper greys and dark mode on true black, with a single brand accent, Sanctuary Teal, used sparingly for action and selection. Serif headlines give affirmations a voice. Sans-serif body and labels keep the operating surfaces plain and legible. Warmth comes from small, purposeful color families: ember orange for streak and momentum, and per-theme accents for affirmation universes and meditation categories.

Celebration and urgency borrow the mechanics of gamified apps (streaks, milestones, nudges) but never their look. There are no mascots, no loud saturated palettes and no bouncy game badges.

**Key Characteristics:**
- One brand accent (Sanctuary Teal); everything else is tonal neutral or a scoped semantic family.
- Content-first, full-bleed immersive surfaces; chrome is stock Material 3, flat and tonal.
- Serif voice for headlines and affirmations, sans-serif for UI.
- Full light and true-black dark themes; dynamic color is off on purpose so the brand palette wins.
- Motion is slow and breath-like, never jittery.

## Colors

A quiet neutral field with one teal voice, plus small warm families that each carry one meaning.

### Primary
- **Sanctuary Teal** (#00696F light / Sanctuary Teal Bright #5BBCC3 dark): primary actions, icon tints, links, the splash screen. Bright teal is the cross-cutting accent reused in the meditation aurora, the group play accent and the default group highlight.
- **Sanctuary Teal Selected** (#18767D light / #5BBCC3 dark): secondaryContainer for the selected navigation pill and selected chips (4.61:1 against surfaceContainer).
- **Sanctuary Teal Deep / Ink** (#00494D, #00252A): on-container text over teal.

### Secondary
- **Quiet Grey** (#5E5E5E): the secondary role; low-emphasis UI.

### Tertiary
- **Streak Ember** (#E8590C light / #FF9A4D night, chip #B93F00, ground #FFEFD9 / #3A2412, ink #3B1F0A): the streak and momentum family. It is used in streak notifications and echoed by the widget flame (#FDA856).
- **Reflection Slate** (#456582 on #E9EEF3): reflection-prompt notifications.
- **Premium Apricot** (#FFE0C7 / #7A3B00 light; #5C3200 / #FFD9AD dark): Pro badges only.

### Neutral
- **Paper** (#F9F9F9) and its container ramp (#FFFFFF, #F3F3F3, #EEEEEE, #E8E8E8, #E2E2E2): light backgrounds and tonal layering.
- **Night** (#000000) and its ramp (#111111, #1C1C1C, #272727): dark backgrounds; true black.
- **Ink** (#1A1C1C light / #FFFFFF dark), **Ink Muted** (#3E494A / #B0BEC0): text.
- **Outline** (#6E797A / #5C6B6D), **Outline Soft** (#BDC9C9 / #3A4344): borders and dividers.
- **Cover Ground** (#0F1216 with #F2F3F5 text): dark collection covers in both themes.

### Scoped accent families (content color, not UI color)
- **Aurora** (meditation backdrop, alpha-blended): teal #5BBCC3, sea #3FA796, blush #E8A9A0; dark variants #3AA5AD, #2E8B7A, #C98A8F.
- **Meditation categories** (generated covers): calm #7FA8E0, focus #E2C26A, sleep #A493E6, body #E8906F, compassion #E58AA6, default #5BBCC3.
- **Affirmation universes** (`ui/feed/UniverseAccent.kt`): peach, tangerine, lavender, sunflower, coral, lime, raspberry, dusty plum, orchid.
- **Group highlights** (user-picked): teal #5AB3C8, violet #A48FE1, amber #ED914C, rose #E3839A, green #7FC581, gold #DEC26D.
- **Weekly tracker widget** (always dark, over wallpaper): meditation #57B6B6, affirmation #E3956C, flame lit #FDA856, healed heart #D2CEFD, surface #232532, text #E9E9ED, caption #B2B6CA.

### Named Rules
**The One Voice Rule.** Sanctuary Teal is the only UI accent. Other hues appear only inside their scoped family: streak, premium, category, universe, group.

**The One Meaning per Hue Rule.** Ember orange means streak and momentum. Apricot means Pro. Don't borrow a family's hue for an unrelated purpose.

**The Contrast-Checked Rule.** New color pairs are verified with `ui/theme/ContrastRatio.kt` (tests already exist for the theme and the aurora).

## Typography

**Display Font:** FontFamily.Serif (the design intent is Playfair Display; no font file is bundled)
**Body Font:** FontFamily.SansSerif (the design intent is Work Sans; no font file is bundled)

**Character:** A literary serif gives affirmations and headings a warm, human voice. A neutral sans keeps controls quiet.

### Hierarchy
- **Headline Large** (Bold, 40/44, -0.5): affirmation card text in white; screen heroes.
- **Headline Medium** (SemiBold, 32/38): major section heads.
- **Headline Small** (SemiBold, 24/30): sheet and card titles.
- **Title Large** (Medium, 22/28, serif): list headers, dialog titles.
- **Body Large / Medium / Small** (Regular 18/28, 16/24, 14/20): reading text and descriptions.
- **Label Large / Medium / Small** (SemiBold 14/20, 12/16, 10/14): buttons, chips, badges, captions.
- Display and titleMedium/titleSmall keep their Material 3 defaults.

### Named Rules
**The Serif Speaks Rule.** Serif is for the voice (affirmations, headings, celebration moments). Controls, numbers in dense UI, and labels use sans.

## Layout

Single-activity, edge-to-edge Compose. Navigation is an adaptive `NavigationSuiteScaffold` (bar on phones, rail on larger windows), hidden on immersive surfaces. The spacing rhythm is a 4dp grid dominated by 16, 8, 12 and 24dp. 16dp is the default screen and card inset. Touch targets are at least 48dp. Immersive surfaces (the affirmation feed, the meditation player) run full-bleed and respect safe-drawing and cutout insets. Content cards sit in calm vertical stacks with generous gaps.

## Elevation & Depth

Flat by default. Depth comes from tonal layering through the surfaceContainer ramp, not shadows. There are no elevated or outlined cards in the system. Lift appears only on floating chrome: the floating status pill and save indicator (4dp, surface at 90% alpha), the group play button (6dp circular shadow) and the customization sheet (3dp tonal).

### Named Rules
**The Flat-By-Default Rule.** Surfaces are flat and tonal at rest. Shadow is reserved for elements that float over content.

## Shapes

Soft, gently rounded geometry. Material 3 default shapes are kept, and custom radii cluster at 12dp (most common: plates, containers), 8dp and 14dp (small elements), 16–20dp (cards; discovery cards use 20dp) and 22dp (widgets). Pills (50%) are used for status and badges. Circles are used for avatars, the play button, progress dots, the like-burst ring and the tracker half-discs. Dividers are hairlines (1dp), sometimes short (48dp under the affirmation title).

## Components

Stock Material 3 components, soft and unobtrusive. They step back so the content leads.

### Buttons
- **Shape:** fully rounded pill (M3 default).
- **Primary:** filled Sanctuary Teal with white label (labelLarge).
- **Secondary / Tertiary:** FilledTonal, Outlined and Text buttons with stock M3 states. No custom button component exists.

### Chips
- **Style:** M3 AssistChip and FilterChip; selected uses Sanctuary Teal Selected.
- **State:** locked theme chips render muted (`CatalogThemeChip`).

### Cards / Containers
- **Corner Style:** 20dp (discovery cards), 12dp (plates).
- **Background:** surfaceContainer tonal ramp.
- **Shadow Strategy:** none (see Elevation).
- **Internal Padding:** 16dp default.

### Inputs / Fields
- **Style:** M3 OutlinedTextField and TextField; inline editable affirmation tokens use BasicTextField.

### Navigation
- **Style:** adaptive NavigationSuiteScaffold, with the selected indicator pill in Sanctuary Teal Selected and labels in labelMedium. Bottom sheets (ModalBottomSheet, docked BottomSheetScaffold for "Your feed") are the primary secondary surface.

### Affirmation Card (signature)
Full-bleed solid-color or local-image background under a vertical black scrim (alpha 0.6 → 0.2 → 0.8). White serif headline centered, with a 12dp black plate at 60% when the background needs it. Like gesture: AffirmationLikeBurst (scale pop 0.15 → 1.16 → 1.0, teal 2dp ring, white/teal particles, about 600ms).

### Meditation Surfaces (signature)
AuroraBackdrop (1200ms entry on cubic(0.16, 1, 0.3, 1), slow warm shift), BreathingOrb on BreathEasing cubic(0.37, 0, 0.63, 1), and generated MeditationCover motifs (rings, crescent, overlap, radiate) tinted by category color.

### Weekly Tracker Widget (signature)
Glance widget, always dark (#232532 preview surface over wallpaper). Rolling 7-day grid of half-discs (left meditation #57B6B6, right affirmation #E3956C), a healed-day heart (#D2CEFD), and a streak flame lit (#FDA856) or dim depending on whether today is done. Size tiers: Strip, Regular, Tall.

### Streak Notification (signature)
Custom RemoteViews on a warm ember ground (#FFEFD9 / #3A2412) with an ember accent and chip. This is the canonical expression of the streak family.

### Healer Granted (signature)
A full-screen one-time celebration (`StreakHealerGrantedScreen`) with the mended-heart icon. It is the established pattern for once-only celebration moments.

## Do's and Don'ts

### Do:
- **Do** use Sanctuary Teal (#00696F / #5BBCC3) as the single UI accent, and the surfaceContainer ramp for depth.
- **Do** express streak and momentum through the ember family (#E8590C / #FF9A4D, flame #FDA856).
- **Do** use serif (headline styles) for affirmations and celebration copy, sans for controls.
- **Do** keep both themes: light paper (#F9F9F9) and true-black dark (#000000).
- **Do** contrast-check every new pair with `ContrastRatio.kt`.
- **Do** reuse the full-screen once-only pattern of `StreakHealerGrantedScreen` for big moments.

### Don't:
- **Don't** use a cartoon mascot, a loud saturated palette or bouncy game-style badges; borrow gamification mechanics, not its look.
- **Don't** add shadows to resting surfaces; lift is only for floating chrome.
- **Don't** introduce a second UI accent or reuse a scoped family's hue for an unrelated meaning.
- **Don't** turn on dynamic color; the brand palette wins.
- **Don't** use the leftover template colors in `res/values/colors.xml` (purple_200/500/700, teal_200/700).
