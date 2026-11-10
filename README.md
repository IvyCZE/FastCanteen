# LibreCanteen

A source-available Android client for school canteens running **iCanteen**, built to be pleasant to use:
a calm, fast interface for the weekly menu, one-tap ordering, credit, payments and account settings
(including changing your password).

> **Not affiliated with Z-WARE s.r.o.** LibreCanteen is an independent, non-commercial project.
> "iCanteen" is a product of Z-WARE s.r.o. This project contains none of its code, styles or images.

## Features

- Week or month view of the menu (switch in settings or by long-pressing the date bar), with order and cancel deadlines, allergens, and canteen-stated reasons when a meal is locked
- Light, dark and AMOLED themes, optionally using your wallpaper colors (Android 12+)
- Order, cancel and switch meals with a checkbox on the meal card (with "Undo"); every change is verified against the server
- Order history and payments
- Change password (confirmed by logging in with the new one)
- Sign-in is remembered; the login is stored encrypted with a non-exportable Android Keystore key
- Order notes
- Meal exchange (burza): take meals other people offer, put your own order in (once it can no longer be cancelled) and take it back; every step is verified against the server
- Allergen codes that match your selection are marked in red with a warning icon; the selection is edited in the app and saved to the canteen automatically a moment after the last change
- Offline cache: the last loaded menus stay readable without a connection (ordering is disabled)
- Optional reminder from Thursday when nothing is ordered for next week
- Optional app lock with biometrics or the device screen lock
- Czech and English (follows the system or can be set in the app), light and dark theme
- Tablet layouts: a navigation rail from 600 dp width, lists and settings centered at a readable width, and on wide screens the month calendar beside the chosen day
- Two-step sign-in (welcome, then address and credentials on one screen) and a themed app icon that follows the wallpaper colors on Android 13+

Not implemented yet: multiple portions (the author's canteen does not offer them).

## How it works

iCanteen has no public API, so `:core` reads the same server-rendered pages a browser would and
turns them into typed models. It is built against **iCanteen 2.22**. Unknown markup is reported as
an error instead of being guessed, because wrong data here can mean a wrong order.

Everything backend-specific lives behind `CanteenProvider`, so another implementation
(for example an official API, should one appear) can replace it without touching the UI.

```
core/   pure Kotlin/JVM: models, CanteenProvider, iCanteen HTML client and parsers
app/    Android app (Jetpack Compose); session handling, UI
```

Design decisions worth knowing:

- Order operations are signed by the server per page. The app never reuses one after a session
  renewal; it reloads and asks again.
- A change counts as done only after the server's own view of the day confirms it.
- Only `https` canteen addresses are accepted.

## Building

Requires JDK 21 and the Android SDK (platform 36). Point Gradle at the SDK with `local.properties`
(`sdk.dir=/path/to/Android/Sdk`) or `ANDROID_HOME`.

```sh
./gradlew :core:test :app:testDebugUnitTest   # tests
./gradlew :app:assembleDebug                  # app/build/outputs/apk/debug/app-debug.apk
```

A signed release build needs your own keystore. Keep it and its passwords outside the repository,
describe it in a properties file (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`) and run:

```sh
LIBRECANTEEN_KEYSTORE_PROPERTIES=/path/to/keystore.properties ./gradlew :app:assembleRelease
```

Without that variable the release APK is built unsigned. Updates only install over an earlier version
signed with the same key, so back the keystore up.

Parser tests run against anonymized HTML samples in `core/src/test/resources/fixtures/`.
The `synthetic_*` samples were written by hand from the site's own scripts, not captured.
When adding samples, remove names, usernames, addresses, balances, CSRF values and order tokens first.

## Privacy

Your canteen address and password are entered in the app and stay on your device.
No credentials, school names or personal data belong in this repository.

## Project notes

`JOURNAL.md` records the design decisions, how iCanteen behaves, the history and the plan. `AGENTS.md` is for AI coding agents.

## License

[PolyForm Strict 1.0.0](LICENSE). The source is public so you can read it, build it yourself and use it for
noncommercial and personal purposes. You may not change it, redistribute it or use it commercially. This is
not an open-source license. The only official builds are the releases in this repository; the app is not
published in any app store.
