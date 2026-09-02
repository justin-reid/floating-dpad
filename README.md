# floating-dpad

An on-screen D-pad that floats over whatever app you're using and works like a TV remote.

It was built for using **TiviMate** on a tablet. TiviMate is an Android TV app designed
around a physical remote, and driving it by touch is awkward — parts of the EPG guide are
effectively unreachable. This pad sends genuine remote-control button presses, so apps
respond to it exactly as they would to real hardware.

Nothing about it is TiviMate-specific. It works with anything that expects a remote.

## What it does

- **Up, Down, Left, Right, Select and Back**
- Drag it anywhere on screen, then lock it in place once it's where you want it
- Collapse it to a small bubble when it's in the way, tap to bring it back
- Three layouts: cross, horizontal row, or vertical column
- Adjustable size, opacity and haptics
- Hold an arrow to scroll, with adjustable repeat speed
- Remap any button if an app expects a different key
- Quick Settings tile to show and hide it

## What you need

An Android 10 device or newer, and **[Shizuku]**.

Android doesn't let an ordinary app send button presses to other apps — that's a
privileged operation, and a normal installed app can't do it. Shizuku is a well-known
helper that grants exactly that privilege, without root. It's free and on the Play Store.

The catch worth knowing up front: **Shizuku stops every time your device reboots** and has
to be started again. It takes about ten seconds and doesn't need a computer.

## Setting up

1. Install [Shizuku] from the Play Store.
2. Start Shizuku (see below).
3. Install floating-dpad.
4. Open it and grant **Display over other apps**.
5. Shizuku will ask whether to give floating-dpad access — allow it.
6. Turn on **Show the D-pad**, then open the app you want to control.

## Starting Shizuku, and restarting it after a reboot

No computer or cable required:

1. **Settings → Developer options → Wireless debugging**, switch it on.
2. Open **Shizuku** and tap **Start via Wireless debugging**.
3. The first time only, Shizuku asks you to pair using the code shown on that same
   Developer options screen. It remembers the pairing afterwards.

You don't have to memorise this. If Shizuku isn't running when you open floating-dpad,
the app shows these steps with a button that takes you straight to Developer options, and
there's a **How do I start Shizuku?** button in the status card any time you want it.

The pad also draws a **red outline** whenever it can't send anything, and the notification
tells you why — so it never silently does nothing.

**Tip:** Developer options lets you add a **Wireless debugging** tile to your Quick
Settings. Put it next to the Floating D-pad tile and the post-reboot routine stays in one
panel.

## Getting the app

Every push builds an installable APK. Open [**Actions**][actions], click the most recent
run, and download the **floating-dpad-debug** artifact.

Two things to expect: it downloads as a `.zip`, so extract the `.apk` inside before
installing, and you'll need to be signed in to GitHub to download it at all.

## Building it yourself

You'll need JDK 21 and the Android SDK (`compileSdk` 36). The Gradle wrapper jar isn't
committed, so generate it once:

```bash
gradle wrapper --gradle-version 8.14.3
```

then:

```bash
./gradlew assembleDebug
```

Android Studio does both for you when it opens the project.

---

Working on the code? [AGENTS.md](AGENTS.md) covers how it works internally, and which
parts are load-bearing and easy to break by accident.

[Shizuku]: https://shizuku.rikka.app/
[actions]: https://github.com/justin-reid/floating-dpad/actions
