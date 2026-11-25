# EyeBreak

A tiny 20-20-20 reminder for your eyes: after 20 minutes of screen time, take a 1-minute break and look at something about 20 feet (6 m) away. Each break shows a random short line from [`messages.txt`](messages.txt): motivation, learning tips, or a fun fact.

## macOS (menu bar app)

```sh
./mac/install.sh
```

This builds the app, starts it now, and starts it again at every login. It also restarts after a crash.

- The eye icon in the menu bar shows the minutes until your next break. Its menu has **Break now**, **Pause/Resume** and **Quit**.
- During a break every screen dims. A chime plays when the break is over. To skip, click **Skip** or press **Esc**.
- For a quick test, run `./mac/eyebreak 10` to get a break every 10 seconds.
- To uninstall: `launchctl bootout gui/$(id -u) ~/Library/LaunchAgents/io.github.aganzefelicite.eyebreak.plist && rm ~/Library/LaunchAgents/io.github.aganzefelicite.eyebreak.plist`

## Android

```sh
./android/build.sh   # needs the Android SDK command-line tools and a JDK; no Gradle
```

Install `android/build/eyebreak.apk` on the phone, for example with Taildrop (`tailscale file cp android/build/eyebreak.apk <phone>:`) or with `adb install`. Then:

1. Open **EyeBreak** once and allow notifications. Then tap **Allow full-screen breaks** and turn on **Display over other apps**. If that switch is greyed out, open App info → ⋮ → **Allow restricted settings** and try again.
2. Set battery use to **Unrestricted** (Settings → Apps → EyeBreak → Battery), so Samsung/Android doesn't put it to sleep.

The app only counts time while the screen is on. Every 20 minutes a dark full-screen overlay covers the phone with a 1-minute countdown and a **Skip** button, and the phone buzzes when the break ends. Until you allow the overlay, it shows a notification instead. If the screen is off for a full minute, the timer resets. The app starts again on its own after a reboot.

## Messages

Edit `messages.txt`, one message per line. The Mac app re-reads the file on every break, so you don't need to rebuild it. To update the phone, rebuild and reinstall the APK.
