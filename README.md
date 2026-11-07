# Glass Launcher (ALPHA)

> ⚠️ **Alpha software.** Glass Launcher is still being developed. Expect bugs, rough edges and
> features that change or disappear between builds. Don't rely on it as your only launcher yet.

A Pixel-style Android home-screen launcher where everything is made of **liquid glass**: real-time
refraction, blur, vibrancy and light rims, powered by Kyant's
[backdrop](https://github.com/Kyant0/AndroidLiquidGlass) library. Built with Kotlin and Jetpack Compose.

**Download the latest alpha APK:**
https://github.com/brinywharf/GlassLauncher/releases/latest/download/GlassLauncher-alpha.apk

Requires **Android 13 (API 33) or newer**.

## Features

**Look**
- Liquid glass everywhere: "At a glance" card, dock, search bar, app drawer, menus and settings
- **Auto-generated glass icons for every app**: each icon becomes a glass tile with the app's symbol
  (it uses Android 13 themed-icon symbols when the app has them)
- Icon colors: *From app* (picked automatically from each icon), *One color* for all, or *Clear glass*
- **Per-app color**: long-press an app → Edit → pick any color from swatches or the hue slider
- Adjustable glass strength: refraction, blur, frost, light rim
- Finger-following app drawer with spring physics, pull-down-to-close, apps zoom open from their icon
- Squishy press animation on icons, haptic feedback
- Your own wallpaper (photo picker) or the default aurora background

**Home screen (Pixel style)**
- "At a glance" card: clock, date, battery, next alarm (tap to open the Clock app)
- Multiple home pages with an adjustable grid, plus page dots
- Widgets page (swipe right): add, show and remove real Android widgets
- Glass dock (3–7 apps) and a search bar at the bottom
- Swipe up: app drawer · Swipe down: notifications · Double-tap: lock screen (optional)
- Long-press empty space: wallpaper, widgets, settings

**App drawer**
- Search with web-search fallback
- Suggested apps (most used)
- A–Z fast scroller on the right edge
- Adjustable number of columns

**App menu (long-press any app)**
- App shortcuts (e.g. "New message"), when Glass Launcher is your default home app
- Add to/remove from home or dock, reorder on home
- Rename and recolor, hide, App info, Uninstall

**New in 0.7.0**
- Drag & drop: long-press an icon, then move it. Icons glide out of the way; drag to the screen edge to change page
- Drag between home, dock, folders and from the app drawer; drop on "Remove" or "Uninstall" at the top
- Folders: drop an app onto another app (hold for a moment) to make a folder; tap to open, rename inline,
  drag apps back out; long-press a folder to ungroup it

**New in 0.6.0**
- Calculator in search: type `12*7+3` in the drawer and tap the result to copy it
- Polished motion: drawer icons rise in row by row, home gently zooms back in when you return from an app,
  a haptic tick when the drawer swipe passes the opening point, touch frame-rate boost on Android 15+

**Nova-style settings (0.5.0)**
- Gestures: swipe up / swipe down / double-tap → app drawer, notifications, quick settings, lock screen,
  search, launcher settings or any app
- Page transitions: slide, cube, card stack, zoom, rotate, fade
- Icon shapes: squircle, rounded, circle, square, teardrop · icon & label size
- Animation speed (relaxed → faster), app open animation (zoom / circle reveal / system), haptics toggle
- Drawer: sort A–Z / most used / newest, labels, suggestions, fast scroller, background darkness
- Dock: show/hide, glass background, size · Search bar: bottom / top / hidden · page indicator
- Add new apps to home automatically · hide status bar · rotation · maximum refresh rate (120/144 Hz)
- Backup & restore of the whole setup to a JSON file

**Other**
- Notification dots (needs notification access)
- Hidden apps list
- English and Turkish UI
- Sensible tablet defaults (bigger grid)

## Why the launcher draws its own wallpaper

Glass needs the actual pixels behind it so it can bend them. Since Android 13, a normal app can't read
the system wallpaper. So the launcher draws its own background behind the glass.

## Notes for alpha testers

- Notification dots and double-tap-to-lock need extra permissions. If Android says a setting is
  "restricted", open **App info → ⋮ → Allow restricted settings** for Glass Launcher.
- App shortcuts only appear when Glass Launcher is set as the default home app.
- Every build is signed with the same key, so new APKs install over old ones (from 0.2.0-alpha on).

## Building

Open the project in Android Studio and run it, or push to GitHub: the **Build APK** workflow builds
the APK and publishes it as a release.

## Tweaking the glass in code

`ui/Glass.kt` → `Modifier.liquidGlass(...)` defines the material: `lens()` refraction, `blur()`,
`vibrancy()`, plus a frost wash and a specular rim drawn on the surface.

## License

Copyright © 2026 Anıl. **All rights reserved**: the source is visible for reference only, see [LICENSE](LICENSE).
Third-party components keep their own licenses: see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
