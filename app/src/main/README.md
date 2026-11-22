# TinyBattery

TinyBattery is a small, offline-first Android app and home-screen widget for checking the phone battery and the batteries of connected Bluetooth accessories.

## What it shows

- A large circular phone battery indicator and up to four connected Bluetooth accessories.
- Circular percentage rings inspired by the glanceable battery circles on iOS.
- Phone, headset, speaker, and unknown-device glyphs.
- Material You-style dynamic accent colors, plus a translucent Glass surface.
- A refresh action directly on the widget and a settings/dashboard screen in the app.
- A proper adaptive launcher icon, themed-icon artwork, round icon, and density-specific PNG fallbacks.
- No Compose, AndroidX, network permission, accounts, ads, analytics, or tracking.

Bluetooth percentages are shown when Android reports them. A connected accessory remains visible with `—` when the platform does not expose its battery percentage.

## Widget reliability

The widget uses a deliberately conservative framework `RemoteViews` implementation:

- One fixed XML hierarchy, with four predeclared accessory slots.
- No nested/dynamically-added `RemoteViews`, collection adapters, custom widget `View` classes, or reflective `setInt` calls.
- Only standard `RemoteViews` operations are used to update text, image resources, bitmaps, visibility, and click actions.
- Battery rings are small bitmaps rendered before the update; the launcher only displays them.

This avoids a common failure mode where a launcher rejects an otherwise valid widget because one unsupported view operation or nested remote layout fails during inflation. Android documents that app-widget `RemoteViews` supports only a restricted set of views and operations.

## Open-source references reviewed

The implementation and visual direction were checked against these projects:

- [MaterialBatteryWidget](https://github.com/takusan23/MaterialBatteryWidget) — directly relevant phone + Bluetooth battery widget, Android dynamic color support, and an explicit note that accessory battery reporting depends on device support.
- [Batre](https://github.com/nongbit/batre) — a minimal Kotlin battery monitor with a native `RemoteViews` widget and event-driven battery updates. Its local/event-driven architecture informed TinyBattery's approach to avoiding frequent background polling.
- [Device Watch](https://github.com/jrs8205/Device-Watch) — an offline Android monitor with Glance widgets. It was useful as a comparison for offline data handling and widget presentation; TinyBattery does not take its Compose/Glance dependency stack.
- [AndroidBatteryWidget](https://github.com/xckevin/AndroidBatteryWidget) — an open Android launcher widget focused on battery presentation and Bluetooth battery support.

These repositories were reviewed as references. TinyBattery does not copy or bundle their source code or dependencies; it keeps its own MIT-licensed, framework-only implementation.

## Android compatibility

- Minimum SDK: 33 (Android 13).
- Compile SDK: 35.
- Target SDK: 35.
- Intended runtime range: Android 13 through Android 17.

The widget uses the platform `AppWidgetProvider` and `RemoteViews` APIs. No Android 17-only API is required.

## Bluetooth behavior and limitations

TinyBattery asks Android's already-connected A2DP, Headset, GATT, Hearing Aid, and LE Audio profiles for connected devices. It does not run Bluetooth discovery scans.

Android's general third-party SDK does not expose a universal battery-level getter for every Bluetooth accessory. TinyBattery listens for the platform Bluetooth battery-level broadcast as a best-effort path and stores values supplied by the Bluetooth stack. As a result:

- Some headsets and speakers report a percentage and show it in the widget.
- Some expose only one combined value, update intermittently, or expose no battery level.
- The connected device can still be displayed when its level is unavailable; it shows `—` rather than an invented value.
- Device/ROM-specific behavior can differ between LineageOS, PixelOS, and other Android builds.

## Build on NixOS / Nix

The Nix flake explicitly accepts the Android SDK license with `android_sdk.accept_license = true`.

The development shell supplies JDK 17, Gradle 8, Android SDK Platform 35, and Build Tools 35.0.0. It omits the emulator, system images, NDK, and CMake.

Enter the shell from the project root:

```sh
nix develop
gradle --stop
gradle assembleDebug
```

The shell checks that the Nix-provided AAPT2 binary starts and creates a project-local Gradle launcher. The launcher passes the `android.aapt2FromMavenOverride` project property on every Gradle invocation, ensuring AGP uses Nix's AAPT2 instead of its bundled Maven binary.

Builds run outside the Nix shell or through an IDE that does not inherit the shell's `PATH` will not use that launcher or its AAPT2 override.

APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Install with:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Add the widget

1. Install TinyBattery and open it once.
2. Grant the Bluetooth permission when asked.
3. Choose Material You or Glass.
4. Open the launcher's widget picker and add **TinyBattery**.
5. Tap the widget body to open the app, or tap **Refresh** in its header to refresh connected Bluetooth devices.

The widget refreshes on Bluetooth connection/battery broadcasts where the ROM supplies them, when manually refreshed, and on Android's periodic widget update schedule. Android enforces a minimum 30-minute period for `updatePeriodMillis`.

## Debugging a launcher-specific widget failure

If a launcher still shows “Can't load widget”, capture the exception from the device immediately after adding the widget:

```sh
adb logcat -c
# Add the TinyBattery widget, then run:
adb logcat -d -v time | grep -iE 'RemoteViews|AppWidget|TinyBattery|AndroidRuntime'
```

The first `RemoteViews` or `AppWidgetHost` exception is more useful than the launcher's generic message. Different launchers can have different inflation behavior, so a log from the affected ROM is the definitive diagnosis.

## License

MIT. See [LICENSE](LICENSE).
