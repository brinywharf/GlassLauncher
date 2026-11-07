# Glass Launcher

An Android home-screen launcher with a liquid-glass look, built with Kotlin and Jetpack Compose.
The glass effect (refraction, blur, vibrancy and highlights) comes from Kyant's
[backdrop](https://github.com/Kyant0/AndroidLiquidGlass) library.

## What's in it

- **Clock card**: a large time and date on a thick glass panel
- **Search pill**: tap it to open the app drawer with the keyboard ready
- **Glass dock**: holds up to 5 pinned apps (the first 4 are pinned on first run)
- **App drawer**: swipe up to open it as a frosted glass sheet with search and a 4-column grid. Swipe down or press Back to close it
- **Long-press an app** for a glass menu with *Add to dock / Remove from dock*, *App info* and *Uninstall*
- **Long-press the home screen** to change the wallpaper (system photo picker, no permissions needed) or go back to the animated background
- Updates on its own when you install or remove apps (`LauncherApps.Callback`)
- Pressing Home while on the launcher closes the drawer and any menus

Requires **Android 13 (API 33) or newer**. The lens and refraction effect needs `RuntimeShader`.

## Why the launcher draws its own wallpaper

Glass needs the actual pixels behind it so it can bend them. Starting with Android 13, a normal app can't
read the system wallpaper. So instead of a see-through window, the launcher draws its own
background, either your chosen photo or animated color blobs. Everything inside
`Modifier.layerBackdrop(backdrop)` is what the glass refracts.

## Build and run

1. Open the `GlassLauncher` folder in **Android Studio** (a recent stable version).
2. Let Gradle sync. If Studio offers to update the AGP, Kotlin or library versions, accept the offer.
3. Run it on a phone or emulator with Android 13 or newer.
4. Press **Home**, choose **Glass Launcher**, then **Always**.
   (Or go to Settings → Apps → Default apps → Home app.)

To get back to your old launcher, use the same *Default apps → Home app* setting.

## Tweaking the glass

The whole look is controlled in `ui/Glass.kt`, inside `Modifier.liquidGlass(...)`:

| Parameter | Effect |
|---|---|
| `tint` | Color wash on the glass. Higher alpha makes it more frosted and solid |
| `blurRadius` | How blurry the background looks |
| `refractionHeight` | Width of the bending zone along the edges |
| `refractionAmount` | Strength of the edge bending (the "thick glass" look) |

Each component (clock, dock, drawer, menus) passes its own values, so you can make the dock
thicker and the drawer frostier, for example.

Note: when `lens()` is used, the shape has to be a Compose `RoundedCornerShape` or `CircleShape`.

## Project layout

```
app/src/main/java/com/example/glasslauncher/
├── MainActivity.kt          # HOME activity, edge-to-edge, handles the Home button
├── LauncherViewModel.kt     # app list, launching, dock pins, wallpaper storage
└── ui/
    ├── Glass.kt             # the liquidGlass() modifier
    ├── Background.kt        # wallpaper or animated aurora background
    └── LauncherApp.kt       # home screen, dock, drawer, glass menus
```

## Ideas for next steps

- Interactive glass: stretch and squash on press, like the library's catalog demo
- Home-screen pages with freely placed icons and widgets (`AppWidgetHost`)
- Icon packs and themed icons
- Notification badges (needs a `NotificationListenerService`)
- Rename the package from `com.example.glasslauncher` to your own before publishing
