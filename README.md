# Aim Tool For 8 Ball — independent Android rebuild

An independent, privacy-first practice overlay inspired by the feature set of the user-provided Aim Tool For 8 Ball APK. This project does not reuse its code, assets, ads, analytics, or network services.

## Target
- Android 8.0+ (API 26+)
- ARM64 Android devices
- Target device: Android 14 / Realme 10

## Planned feature parity
- Automatic table and ball recognition from user-approved screen capture
- Extended cue-ball aiming guide
- Object-ball contact / ghost-ball guide
- Cushion (bank) and kick-shot path calculations
- Optional multi-line guide display

## Privacy and safety
- No ads, analytics SDKs, accounts, or network requests.
- Screen capture begins only after Android's system MediaProjection consent dialog.
- Overlay and Usage Access are separately explained and user-controlled.
- Overlay is intended to appear only while 8 Ball Pool is foregrounded.
- No taps, input injection, memory access, game modification, or automated shots.
- Capture frames are processed locally and discarded; no image storage.

## Accuracy status
This is an experimental computer-vision project. A successful APK build only confirms compilation, not accurate detection. Detection must be validated on real 8 Ball Pool practice screenshots from the target phone. Cushion calculations require calibrated table bounds and ball radius; they are not enabled as trusted guidance until validated.

## Build
GitHub Actions runs gradle testDebugUnitTest assembleDebug and uploads the debug APK as a workflow artifact.
