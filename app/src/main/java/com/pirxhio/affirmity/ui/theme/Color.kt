package com.pirxhio.affirmity.ui.theme

import androidx.compose.ui.graphics.Color

// Teal brand palette, carried over from the Stitch design mockups
// (afirmaciones.html / meditacion.html / progreso.html Tailwind config).

// Light theme tokens
val TealPrimaryLight = Color(0xFF00696F)
val TealPrimaryContainerLight = Color(0xFF5BBCC3)
val OnPrimaryLight = Color(0xFFFFFFFF)
val OnPrimaryContainerLight = Color(0xFF00494D)
val BackgroundLight = Color(0xFFF9F9F9)
val SurfaceLight = Color(0xFFF9F9F9)
val SurfaceContainerLowestLight = Color(0xFFFFFFFF)
val SurfaceContainerLowLight = Color(0xFFF3F3F3)
val SurfaceContainerLight = Color(0xFFEEEEEE)
val SurfaceContainerHighLight = Color(0xFFE8E8E8)
val SurfaceContainerHighestLight = Color(0xFFE2E2E2)
val OnSurfaceLight = Color(0xFF1A1C1C)
val OnSurfaceVariantLight = Color(0xFF3E494A)
val OutlineLight = Color(0xFF6E797A)
val OutlineVariantLight = Color(0xFFBDC9C9)
val SecondaryLight = Color(0xFF5E5E5E)

// Bug 2b fix: 0xFFE2E2E2 (the old value) sits only ~1.12:1 against SurfaceContainerLight
// (0xFFEEEEEE) -- WCAG's non-text UI-component minimum is 3:1 -- so the selected-nav-tab pill was
// nearly invisible. This teal-tinted tone (between TealPrimaryLight 0x00696F and
// TealPrimaryContainerLight 0x5BBCC3 on the brand ramp) clears 4.61:1 against SurfaceContainerLight,
// and pairs with white text/icons (OnSecondaryContainerLight) at 5.35:1 -- both verified in
// ContrastRatioTest via [wcagContrastRatio].
val SecondaryContainerLightArgb: Long = 0xFF18767D
val SecondaryContainerLight = Color(SecondaryContainerLightArgb)
val OnSecondaryContainerLightArgb: Long = 0xFFFFFFFF
val OnSecondaryContainerLight = Color(OnSecondaryContainerLightArgb)
val SurfaceContainerLightArgb: Long = 0xFFEEEEEE
val ErrorLight = Color(0xFFBA1A1A)

// Dark theme tokens (afirmaciones.html full-bleed feed uses a near-black theme)
val TealPrimaryDark = Color(0xFF5BBCC3)
val BackgroundDarkArgb: Long = 0xFF000000
val BackgroundDark = Color(BackgroundDarkArgb)
val SurfaceDark = Color(0xFF000000)
val SurfaceContainerLowDark = Color(0xFF111111)
val SurfaceContainerDarkArgb: Long = 0xFF111111
val SurfaceContainerDark = Color(SurfaceContainerDarkArgb)
val OnSurfaceDark = Color(0xFFFFFFFF)
val OnPrimaryDark = Color(0xFFFFFFFF)
val SecondaryDark = Color(0xFF5E5E5E)
val ErrorDark = Color(0xFFBA1A1A)

// Bug 2b fix: DarkColorScheme previously left secondaryContainer/onSecondaryContainer/
// onSurfaceVariant/outline unset, falling back to Material's default purple baseline -- mismatched
// from the app's teal brand. Reusing TealPrimaryDark as the container tone keeps the selected-tab
// pill on-brand and gives 8.48:1 against SurfaceContainerDark; the near-black teal text on it holds
// 7.26:1. onSurfaceVariant/outline are muted teal-greys with enough contrast against the near-black
// background for body text (10.97:1) and outlines/borders (3.78:1, the non-text minimum).
val SecondaryContainerDarkArgb: Long = 0xFF5BBCC3
val SecondaryContainerDark = Color(SecondaryContainerDarkArgb)
val OnSecondaryContainerDarkArgb: Long = 0xFF00252A
val OnSecondaryContainerDark = Color(OnSecondaryContainerDarkArgb)
val OnSurfaceVariantDark = Color(0xFFB0BEC0)
val OutlineDarkArgb: Long = 0xFF5C6B6D
val OutlineDark = Color(OutlineDarkArgb)

// Contrast-audit fix: DarkColorScheme left primaryContainer/onPrimaryContainer/
// surfaceContainerHigh/surfaceContainerHighest/outlineVariant unset, so they silently fell back to
// Material's default (mismatched) baseline in dark mode even though MeditationScreen/
// GuidedMeditationScreen read all five roles. TealPrimaryDark doubles as the container tone (same
// value already used for `primary`/`SecondaryContainerDark`), with a near-black on-container text
// for 7.26:1 contrast (mirrors the SecondaryContainerDark/OnSecondaryContainerDark pairing above).
// The two surfaceContainer steps continue the near-black elevation ramp past SurfaceContainerDark;
// outlineVariant is a low-contrast divider tone, not text -- 3:1 non-text minimum only.
val PrimaryContainerDark = Color(0xFF5BBCC3)
val OnPrimaryContainerDark = Color(0xFF00252A)
val SurfaceContainerHighDark = Color(0xFF1C1C1C)
val SurfaceContainerHighestDark = Color(0xFF272727)
val OutlineVariantDark = Color(0xFF3A4344)

// Contrast-audit fix: AffirmationGroupAccessBadge's PREMIUM badge previously paired the same raw
// orange (0xFFED9A68) as both text/icon color AND (at 8-20% alpha) its own container tint -- a
// self-referential low-contrast combo, not a verified pair. These replace it: a light peach/dark
// near-black-brown pair (light theme) and a dark brown/light peach pair (dark theme), each holding
// >6.2:1 (well past the 4.5:1 text/icon minimum), computed with the same relative-luminance math
// ContrastRatioTest uses.
val PremiumContainerLight = Color(0xFFFFE0C7)
val OnPremiumContainerLight = Color(0xFF7A3B00)
val PremiumContainerDark = Color(0xFF5C3200)
val OnPremiumContainerDark = Color(0xFFFFD9AD)
