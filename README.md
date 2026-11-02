# Pool Trainer Android

Pool Trainer is an experimental Android practice overlay for 8 Ball Pool.

## Version 0.2.0 changes
- Analysis is gated to the 8 Ball Pool package (com.miniclip.eightballpool).
- The overlay is hidden when another app or the Android home screen is in the foreground.
- Ball candidates are limited to a central play-field region, filtered by size and rim contrast, deduplicated and capped.
- The overlay is drawn only after a plausible cue ball and at least one object ball are detected.
- Added a persistent notification with a Stop action.
- Uses Android MediaProjection consent and the system overlay permission; no hidden capture or input automation.

## Important limitations
This is not a certified or guaranteed-accuracy detector. 8 Ball Pool layouts, device resolutions, animations, table skins and UI changes can affect detection. The current detector uses classical OpenCV circle detection and conservative pixel checks; it does not use a trained object-detection model. Test with offline practice before relying on any guide.

The guide estimates a ghost-ball contact point and a nearest-pocket route. It is a visual training aid only, not a physics-perfect solver. It does not automate taps, cue movement or shots. Use in accordance with the game's rules.

## Build
The GitHub Actions workflow builds the debug APK with Java 17 and Gradle 8.9. Download the artifact from the successful workflow run.

## Permissions
- Display over other apps: needed for the visual guide.
- Usage access: used only to show the overlay while 8 Ball Pool is the foreground app.
- Screen capture: granted by Android's MediaProjection confirmation dialog for each session.
- Notifications: used for the foreground service and Stop action.
