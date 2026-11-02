# Reverse-engineering notes: Aim Tool For 8 Ball 1.1

These notes describe the user-supplied APK (package `aim.pool.eight.ball.mood`, version 1.1). This is a clean-room analysis; the original application's proprietary source is not copied into this project.

## Manifest findings

The APK declares:
- INTERNET, ACCESS_NETWORK_STATE, ACCESS_WIFI_STATE
- Google advertising ID permission
- WAKE_LOCK, RECEIVE_BOOT_COMPLETED, FOREGROUND_SERVICE
- An app-local dynamic receiver permission

It does **not** declare:
- SYSTEM_ALERT_WINDOW (draw over other apps)
- a MediaProjection foreground-service permission/type
- an AccessibilityService
- camera or screen-capture service belonging to the app
- a query for the 8 Ball Pool package

The application is marked debuggable, allows backup, and allows cleartext traffic. These settings are security weaknesses / poor release hardening, not proof of malware.

## App-owned DEX classes

The app's own package contains a small set of classes:
- AppClass
- Splash
- StartActivity
- ToolsActivity
- MoreToolsActivity
- CC_loadAds / CC_QurekaAds and their data / listener classes

There are no app-owned ball detector, image classifier, screen-capture, overlay renderer, or billiards-physics classes in the custom package.

## Activity behavior

- Splash uses Volley networking for remote content / ads and routes to the start screen.
- StartActivity inflates `activity_start`, displays a GIF view, loads ad content, and wires a click listener to proceed.
- ToolsActivity inflates `activity_tools`, wires five switches, and handles each switch by calling `openDialog(featureName, checked)`. The feature labels are Back Shots or Kick Shot, Privacy Protection System, AI Guide Tools, Line Guidelines, and Auto Extended Lines.
- `openDialog` goes through the ad/interstitial manager and then displays an activation dialog. The app-owned listeners do not invoke ball recognition, screen capture, overlay drawing, or shot calculations.
- MoreToolsActivity similarly wires informational / purchase dialogs and ad handling.

## Bundled visuals

The APK contains:
- `table_anim.gif`: 600x277, 111 frames. It is a pre-rendered table animation with guide paths already drawn into the frames.
- `image2.gif`: 1200x675, 2 frames. It is promotional artwork showing an extended-aim-line example.
- Several button images and extensive Qureka ad assets.

These are demonstrations / pre-rendered visuals, not an algorithm that tracks the live 8 Ball Pool table.

## Conclusion

For this supplied 1.1 APK, the advertised controls appear to be UI switches that trigger activation dialogs, while the illustrated aim paths are bundled animations. The APK does not show evidence of a live in-game aim overlay or automatic ball detection.

This project therefore aims to recreate the recognizable app flow and feature controls while implementing actual local screen analysis and guide drawing independently. It must not copy the original APK's code, ad SDK, tracking, or artwork. A working automatic guide is a new implementation, not something that can be recovered from this APK.
