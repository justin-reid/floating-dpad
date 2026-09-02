# Working on floating-dpad

Context for anyone — human or agent — changing this code. The [README](README.md) covers
what the app is and how to use it; this covers why it is built the way it is, and what
breaks if you change the wrong thing.

## The constraint everything follows from

A normal Android app cannot send key events to another app. `INJECT_EVENTS` is
`signature|privileged`, and a sideloaded APK will never hold it. Drawing the buttons is
trivial — *delivering the press* is the entire problem, and it dictates the architecture.

Shizuku solves it by running a helper as the `shell` UID, the same UID `adb shell input
keyevent` runs as, which does hold `INJECT_EVENTS`. That helper calls `injectInputEvent()`
on our behalf over Binder, so the foreground app receives literal `KeyEvent` objects,
indistinguishable from a physical remote.

### Why not an AccessibilityService

It needs no reboot dance, which is tempting. It was rejected because it cannot inject keys
at all — it can only walk the node tree with `focusSearch()` + `ACTION_FOCUS`, and click
the focused node. That is not the same thing as the app's own internal focus logic, and it
diverges precisely where it hurts most in a leanback TV app with custom `RecyclerView`
focus handling: the EPG guide grid, long-press, and channel up/down.

Verified on device 2026-09-01 that the Shizuku path works, so the accessibility fallback
(milestone 7 of the original plan) is **not being built**. `KeySender` remains an interface
specifically so it can still be added later without the overlay code changing. Do not
inline Shizuku calls into the views.

Two other channels were considered and are dead ends: root (device isn't rooted, and it
buys nothing over Shizuku), and an IME (`InputConnection.sendKeyEvent()` only reaches a
focused text field, useless for navigation).

### The accepted tradeoff

Shizuku must be restarted after every reboot. On Android 16 that is entirely on-device via
wireless debugging — **no PC is needed**, and this is the single most forgettable fact
about operating the app, which is why `ShizukuHelpDialog` exists and says so explicitly.

A dead backend must never look like a working one. Three things surface it: a red outline
on the pad, the notification text, and a throttled toast on press.

## Layout

```
app/
  overlay/   OverlayService (foreground service), OverlayWindow, DpadView,
             drag handling, key-repeat timer
  input/     KeySender (interface), ShizukuKeySender,
             IKeyInjector.aidl, KeyInjectorService (runs as the shell UID)
  settings/  Compose config screen, ShizukuHelpDialog, SharedPreferences
  tile/      Quick Settings tile
  boot/      BootReceiver
```

## Load-bearing details

Four things are easy to break by accident:

- **`FLAG_NOT_FOCUSABLE` on the overlay window.** If the overlay takes input focus, the
  injected events land on our own window instead of the app underneath and nothing works.
  This is the single easiest way to break the app.
- **The window is `WRAP_CONTENT` around the cluster, not fullscreen.** Since Android 12
  the system blocks touches passing *through* an overlay anyway, and the pad never wants
  pass-through — its buttons consume the touch and inject a key instead. A fullscreen
  transparent layer would sit in the touch path of everything else and cost battery for
  nothing.
- **`downTime` is generated once per press** and reused for the initial `ACTION_DOWN`,
  every repeat, and the final `ACTION_UP`. Apps compute long-press duration from it, so
  recomputing it per event silently breaks long-press.
- **The user service is bound once and kept bound.** Shelling out to `input keyevent` per
  press costs 100–200 ms of process spawn, which makes scrolling an EPG unusable.

Smaller ones:

- `repeatCount` must **climb** while an arrow is held, not sit at 1 — that is what engages
  the target app's own scroll acceleration.
- `injectKey` is `oneway` so the repeat timer never blocks the UI thread on a binder round
  trip. Oneway calls from one thread on one binder keep their order.
- AIDL rejects a mix of assigned and unassigned transaction ids. `destroy()` must sit at
  `16777114` for Shizuku's `UserService` contract, so every method needs an explicit id.
- No `HiddenApiBypass` dependency is needed: hidden-API restrictions don't apply to
  processes running as `shell`, which is where `KeyInjectorService` executes.
- The `injectInputEvent` binder moved from `InputManager` to `InputManagerGlobal` in
  Android 14, hence the reflection with a fallback.
- The Shizuku binder arrives **asynchronously** after process start. Checking state
  immediately reports `NOT_RUNNING` for a healthy install — this is why the help dialog's
  auto-prompt waits 1.5 s, and why the QS tile does not gate on Shizuku state at all.

## Deliberate deviations from the original plan

- **Plain Android Views for the overlay, not Compose.** Compose in a `WindowManager`
  window needs `ViewTreeLifecycleOwner` / `ViewTreeSavedStateRegistryOwner` shimmed in, and
  adds recomposition to the touch-to-key path of a latency-sensitive control. Compose *is*
  used for the settings screen, where none of that matters.
- **`SharedPreferences`, not DataStore.** The overlay reads config from inside a touch
  handler, where a synchronous read is exactly what you want. The change listener is all
  the reactivity six buttons need. `OverlayService` ignores change callbacks for the
  position keys, since dragging writes them every frame.

## CI and signing

There is no local Android toolchain on the machine this was developed on, so **CI is the
compiler**. Push and read the run log; don't claim a build works without one.

The Gradle wrapper jar is not committed — the workflow pins Gradle via
`gradle/actions/setup-gradle` instead.

Builds are signed with a fixed debug keystore restored from the `DEBUG_KEYSTORE_B64`
repository secret into `debug.keystore` at the repo root (gitignored), and
`app/build.gradle.kts` points the debug `signingConfig` at that file **explicitly**.

This matters more than it sounds. Runners are fresh VMs, so AGP would otherwise generate a
new random debug keystore per run, and Android refuses to update an installed app across a
signature change — reporting only *"App not installed"*, with no explanation anywhere.

Writing the key to `~/.android/debug.keystore` is **not** sufficient, and this was tried
and failed: AGP resolves that default through `ANDROID_USER_HOME` / the `user.home` system
property, which does not agree with `$HOME` on a runner. Two consecutive builds still
produced two different certificates. Hence the explicit path.

Each build prints its certificate fingerprint in the `Report signing certificate` step, so
a regression is visible in the log rather than on the tablet. It should stay:

```
e314ff50a753ed41d3ad61be50b06887402e899302ec0d7fea0fa55d44fe3e4c
```

If it ever changes, every installed copy has to be uninstalled before it can be updated.
The signing config is guarded on the file existing, so a plain local checkout still gets
AGP's usual auto-generated debug key and builds fine.

## Versions

Everything is pinned in [`gradle/libs.versions.toml`](gradle/libs.versions.toml) — the one
place to bump. AGP, Kotlin and Shizuku were resolved from the registries on 2026-08-31; the
AndroidX and Compose versions were chosen to match and are the first thing to adjust if
dependency resolution ever fails on a clean CI run.

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

Verified driving TiviMate on an Android 16 tablet on 2026-09-01: injection works, the guide
is navigable, and no per-button keycode override was needed — `DPAD_CENTER` was right for
Select. The override dropdown stays because a future TiviMate build may disagree, and it
saves a rebuild round-trip if it does.
