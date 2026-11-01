# Jarvis Home: a custom Google TV launcher

An Apple TV-inspired home screen for Google TV / Android TV, with an assistant called **Jarvis**.

## What you get

- **tvOS-style app row.** Tiles lift, scale up and shine when focused. The app name fades in underneath, and the focused tile stays centred as you scroll.
- **Wallpapers.** Three bundled (Dusk Ridge, Aurora, Nebula) with a slow aerial-style drift and cross-fade. You can add your own (see below).
- **Jarvis.** Select the pill at the top right, or press the remote's search/assist key if it reaches the app. He listens, shows what he hears, and answers out loud.
- **Smart ordering.** The apps you use most move to the front the next time the launcher starts.
- **Long-press an app** to open its system info page (uninstall, permissions).
- Shows both TV apps and regular sideloaded apps.

### Things you can say to Jarvis

| Say | Result |
|---|---|
| "Open YouTube" / "Launch Netflix" | Opens the app (fuzzy name match) |
| "Play Interstellar" / "Watch cricket" | Opens the app if one matches, otherwise runs a web search |
| "What time is it?" / "What's the date?" | Answers aloud |
| "Volume up", "Quieter", "Mute" | Adjusts device volume |
| "Next wallpaper" | Cross-fades to the next wallpaper |
| "Go home" | Back to the first app |
| "Open settings" | System settings |
| "Search for ..." | Web search |
| "Who are you?", "Tell me a joke", "Thanks" | Small talk |
| Anything else ("How tall is Everest?", "Suggest a thriller") | Answered by the optional AI brain (see below) |

The "Jarvis" or "Hey Jarvis" prefix is optional, because you start listening yourself.

### Optional AI brain

Without a key, Jarvis handles every command above offline and says "I don't know how to do that yet" to anything else. To let him answer open questions, add your Anthropic API key to `local.properties` (Android Studio creates this file; see `local.properties.example`):

```
anthropic.api.key=sk-ant-...
anthropic.model=claude-haiku-5-5
```

The key is compiled into the APK, so use this only on your own TV and never publish the APK.

## Build

1. Install **Android Studio** (Koala or newer) with JDK 17.
2. **File > Open** and choose this `JarvisLauncher` folder. Let Gradle sync; it downloads Gradle 8.7 automatically.
3. **Build > Build APK(s)**. The file is `app/build/outputs/apk/debug/app-debug.apk`.

> This project was written without access to the Android SDK, so it has never been compiled. It passed static checks (XML, resource references, bracket balance) but not a real build. If sync or build shows an error, send me the message and I'll fix it.
>
> The Gradle wrapper jar isn't included; Android Studio uses the Gradle 8.7 named in `gradle/wrapper/gradle-wrapper.properties`.

## Install on your TV

1. TV: **Settings > System > About**, tap **Android TV OS build** 7 times to enable Developer options.
2. **Settings > System > Developer options**, turn on **USB debugging** (or **Wireless debugging**).
3. On your computer, with the TV's IP address:
   ```
   adb connect <TV_IP>:5555
   adb install -r app-debug.apk
   ```

## Make it your Home screen

Install first and open the app once from the app list. Then try:

```
adb shell cmd package set-home-activity com.jarvis.launcher/.MainActivity
```

If Home still opens the stock launcher, disable it. Install Jarvis Home first, or you'll have no home screen.

```
# Google TV
adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
# Older Android TV
adb shell pm disable-user --user 0 com.google.android.tvlauncher
```

To go back:

```
adb shell pm enable com.google.android.apps.tv.launcherx
adb shell pm enable com.google.android.tvlauncher
```

Trade-offs of replacing the stock launcher:
- You lose Google TV's recommendations and Watchlist rows.
- Many remotes' dedicated **Assistant** button is handled by the system and goes to Google Assistant, never reaching this app. Use the on-screen Jarvis button instead.
- "Volume" changes the TV box's own volume. If your sound goes through HDMI-CEC or ARC, the TV or soundbar may ignore it.

## Your own wallpaper

Push any JPG or PNG (1920x1080 or larger works best) to the app's private folder:

```
adb push my_wallpaper.jpg /sdcard/Android/data/com.jarvis.launcher/files/Pictures/wallpaper.jpg
```

It becomes the first wallpaper after the next launch. Say "next wallpaper" to cycle.

## Customise

- `Config.kt`: your name for the greeting, the assistant's name, and Jarvis's voice pitch and speed.
- `res/drawable-nodpi/`: swap the bundled wallpapers (keep the filenames) and the banner and icon.
- Tile size: `res/layout/item_app.xml` (176dp x 99dp, which is 16:9).

## Notes and limits

- There is no always-on "Hey Jarvis" wake word. Android has no reliable background hotword on TV, and an always-listening loop would be flaky and beep constantly. Select the Jarvis button to talk.
- Speech recognition and the voice both depend on the Google app and Google's speech and text-to-speech services already on your TV.
