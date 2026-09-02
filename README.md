# floating-dpad

A floating on-screen D-pad overlay for Android that sends **real remote-control key
events** to whatever app is in the foreground, via [Shizuku]. Built for using TiviMate on
a touchscreen tablet, but there is nothing TiviMate-specific in it — it is a generic
keycode sender.

Six buttons: **Up / Down / Left / Right / Select / Back**.

## Why it works this way

A normal Android app cannot send key events to another app. `INJECT_EVENTS` is
`signature|privileged`, and a sideloaded APK will never hold it. Drawing the buttons is
trivial; *delivering the press* is the whole engineering problem.

Shizuku solves it by running a small service as the `shell` UID — the same UID
`adb shell input keyevent` runs as, which does hold `INJECT_EVENTS`. That service calls
`injectInputEvent()` on our behalf over a Binder interface, so the foreground app
receives literal `KeyEvent` objects, indistinguishable from a physical remote.

An `AccessibilityService` was considered and rejected: it cannot inject keys at all, only
walk the node tree with `focusSearch()` + `ACTION_FOCUS`. That diverges from a leanback
TV app's own `RecyclerView` focus logic exactly where it hurts most — the EPG guide grid,
long-press, and channel up/down. `KeySender` is an interface so an accessibility fallback
backend can be added later without the overlay code changing.

**The tradeoff:** Shizuku has to be started again after every reboot. On Android 16 that
is done entirely on-device via wireless debugging, no PC needed. When it is not running,
the pad draws a red outline, the notification says so, and a press raises a toast — a
dead backend must never look like a working one.

## Setup

1. Install [Shizuku] from the Play Store or GitHub.
2. Start Shizuku via **wireless debugging**. Repeat after every reboot.
3. Install the APK (see *Building* below).
4. Grant **Display over other apps** when prompted.
5. Grant Shizuku access to floating-dpad when it asks.
6. Turn on **Show the D-pad**, then open the app you want to drive.

There is also a Quick Settings tile for show/hide, which is the fastest way back after a
reboot.

## Building

CI builds a sideloadable debug APK on every push: open the latest run under **Actions**
and download the `floating-dpad-debug` artifact.

Locally you need JDK 21 and the Android SDK (`compileSdk` 36). The Gradle wrapper jar is
not committed, so generate it once:

```bash
gradle wrapper --gradle-version 8.14.3
```

then

```bash
./gradlew assembleDebug
```

Android Studio will do both for you when it opens the project.

### Signing

CI builds are signed with a fixed debug keystore, restored from the `DEBUG_KEYSTORE_B64`
repository secret into `debug.keystore` at the repo root (gitignored). `app/build.gradle.kts`
points the debug `signingConfig` at that file explicitly.

This matters more than it sounds. Runners are fresh VMs, so AGP would otherwise generate a
new random debug keystore per run, and Android refuses to update an installed app across a
signature change — reporting only *"App not installed"* with no explanation. Note that
writing the key to `~/.android/debug.keystore` is **not** sufficient: AGP resolves that
default through `ANDROID_USER_HOME` / the `user.home` system property, which does not agree
with `$HOME` on a runner. Hence the explicit path.

Each build prints its certificate fingerprint (`Report signing certificate`) so a
regression is visible in the log. It should stay:

```
e314ff50a753ed41d3ad61be50b06887402e899302ec0d7fea0fa55d44fe3e4c
```

The signing config is guarded on the file existing, so a plain local checkout still gets
AGP's usual auto-generated debug key and builds normally.

### Versions

Everything is pinned in [`gradle/libs.versions.toml`](gradle/libs.versions.toml) — that
is the single place to bump. AGP, Kotlin and Shizuku were resolved from the registries on
2026-08-31; the AndroidX and Compose versions there were chosen to match and are the
first thing to adjust if dependency resolution fails on a clean CI run.

## Layout

```
app/
  overlay/   OverlayService (foreground service), OverlayWindow, DpadView,
             drag handling, key-repeat timer
  input/     KeySender (interface), ShizukuKeySender,
             IKeyInjector.aidl, KeyInjectorService (runs as the shell UID)
  settings/  Compose config screen, SharedPreferences
  tile/      Quick Settings tile for show/hide
```

Four things in here are load-bearing and easy to break:

- **`FLAG_NOT_FOCUSABLE` on the overlay window.** If the overlay takes input focus, the
  injected events land on our own window instead of the app underneath, and nothing
  works. This is the single easiest way to break the app.
- **The window is `WRAP_CONTENT` around the cluster, not fullscreen.** The pad never
  needs touch pass-through — its buttons consume the touch and inject a key — and a
  fullscreen transparent layer would sit in the touch path of everything else.
- **`downTime` is generated once per press** and reused for the initial `ACTION_DOWN`,
  every repeat, and the final `ACTION_UP`. Apps compute long-press duration from it.
- **The user service is bound once and kept bound.** Shelling out to `input keyevent`
  per press costs 100–200 ms of process spawn, which makes scrolling an EPG unusable.

The overlay is plain Android Views rather than Compose: Compose in a `WindowManager`
window needs `ViewTreeLifecycleOwner` / `ViewTreeSavedStateRegistryOwner` shimmed in, and
adds recomposition to the touch-to-key path of a latency-sensitive control. Compose is
used for the settings screen, where none of that matters.

`SharedPreferences` rather than DataStore, also deliberately: the overlay reads config
from inside a touch handler, where a synchronous read is what you want.

## Status

| # | Milestone | State |
|---|---|---|
| 1 | Scaffold + CI producing a debug APK artifact | done |
| 2 | Overlay shell — floating cluster, drag-to-move, position persisted | done |
| 3 | Shizuku injection | done, verified on device |
| 4 | Key repeat + haptics | done |
| 5 | Config UI — size, opacity, layout preset, lock, collapse, per-button keycode | done |
| 6 | Reboot handling — boot receiver, visible "not running" state | done |
| 7 | Accessibility fallback backend | not needed |

Verified driving TiviMate on an Android 16 tablet on 2026-09-01: injection works, the
guide is navigable, and no per-button keycode override was needed — `DPAD_CENTER` was
right for Select. The override dropdown stays because a future TiviMate build may
disagree.

Milestone 7 was contingent on the Shizuku path being unusable. It isn't, so it is not
being built — an accessibility backend walks the node tree rather than driving TiviMate's
own focus logic, and would be worst exactly in the EPG grid. `KeySender` remains an
interface, so it can still be added later without the overlay code changing.

[Shizuku]: https://shizuku.rikka.app/
