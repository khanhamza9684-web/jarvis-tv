# TV Pad

Turn an Android phone into a Bluetooth gamepad for **Google TV / Android TV**. The phone registers as a standard Bluetooth HID game controller, so the TV sees it like any other pad — no app needed on the TV.

Includes an on-screen **HUD** showing link status, connected host, report rate (Hz), send time, stick/trigger values and the buttons currently pressed.

## Features

- Bluetooth HID gamepad: 16 buttons, 8-way D-pad (hat), two analog sticks, two analog triggers
- Multi-touch controls (sticks + buttons at the same time), light haptics
- Single-touch D-pad with diagonals
- Live HUD (toggle with "Hide HUD")
- Reports sent at up to ~125 Hz, only when input changes

## Requirements

- Phone: Android 9+ (API 28) **with Bluetooth HID Device profile support**. Most Pixel/Samsung/OnePlus phones work; a few vendors disable it.
- TV: Google TV / Android TV with Bluetooth (Chromecast with Google TV, Onn 4K, Sony/TCL/Philips TVs, Shield, etc.)

## Pair and play

1. Install the APK on the phone and open the app; allow the **Nearby devices** permission.
2. Tap **Connect → Make discoverable**.
3. On the TV: **Settings → Remote & Accessories → Pair remote or accessory**, then select your phone.
4. After pairing, tap **Connect** again and choose the TV. The HUD turns green: *Connected · <TV name>*.

If the TV disconnects later, just tap **Connect** and select it again.

## Button mapping

| On screen | HID button |
|---|---|
| A / B / X / Y | 1 / 2 / 3 / 4 |
| L1 / R1 | 5 / 6 |
| L2 / R2 | 7 / 8 (also analog Rx / Ry) |
| Select / Start | 9 / 10 |
| L3 / R3 | 11 / 12 (reserved) |
| Home | 13 |
| Left stick | X / Y |
| Right stick | Z / Rz |

Android maps these to its standard gamepad keycodes (BUTTON_A, BUTTON_B, DPAD_*, etc.); games that read generic controller input should work without configuration. Mapping can differ slightly per game — report issues.

## Build

### On GitHub (no local setup)

Push to `main`. The **Build APK** workflow produces `TVPad-debug-apk` under the run's *Artifacts*. Pushing a tag like `v1.0.0` also attaches the APK to a GitHub Release.

### Locally

Open the folder in Android Studio (Koala or newer) and run, or with Gradle 8.9+ and JDK 17:

```
gradle assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## Publish to GitHub

```
cd TVPad
git init -b main
git add .
git commit -m "Initial commit: TV Pad"
gh repo create tvpad --public --source=. --push
```

## Project layout

```
app/src/main/java/com/tvpad/
  MainActivity.kt     permissions, discoverable, immersive mode
  HidGamepad.kt       BluetoothHidDevice registration, HID descriptor, report sending
  GamepadState.kt     button/axis state and 9-byte report encoding
  PadViewModel.kt     state → report streaming loop
  ui/GamepadScreen.kt touch controls
  ui/Hud.kt           heads-up display
```

## Troubleshooting

- **"This phone does not support Bluetooth HID device mode"** — the phone's firmware doesn't expose the HID Device profile; no app-level workaround.
- **TV doesn't list the phone** — keep the "Make discoverable" prompt active (2 min), and make sure Bluetooth is on.
- **Nothing happens in a game** — test in TV Settings → Remote & Accessories, or any controller-tester app first.
- Pairing issues: forget the phone on the TV and the TV on the phone, then pair again.
