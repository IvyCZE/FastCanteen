# LibreCanteen journal

Living project notes: what the app is, why it is built the way it is, what has been done, what is
planned. **Read this before changing anything, and add an entry (see the end) when you finish.**
No personal data belongs here, same rule as the rest of the repository.

---

## 1. What this is

LibreCanteen is an unofficial, open-source Android client for school canteens that run
**iCanteen** (Z-WARE s.r.o.). The stock web UI is dated and clumsy on a phone. This app offers a calm
interface for the weekly menu, ordering, credit, payments, order history, allergens and the meal
exchange ("burza").

Goals and constraints agreed with the user:

- **Android only, no backend of our own.** The app talks directly to the canteen's server.
- **Neutral, polished UX**, portfolio quality, to be used by the author and friends. Possibly pitched
  to Z-WARE later, so the code never contains their code, styles or images, and it says so in the README.
- **Open source (MIT).** Nothing personal in the repo: no credentials, school name, address, usernames,
  balances, tokens or real names. Fixtures are anonymized.
- Name **LibreCanteen**, package `yt.ivy.librecanteen`.
- Languages: Czech (default) and English. Themes: System, Light, Dark, AMOLED, optional dynamic colors.
- Stack: Kotlin, Jetpack Compose (Material 3), OkHttp, Jsoup, kotlinx-serialization, WorkManager,
  AndroidX Biometric. `minSdk 26`, `targetSdk`/`compileSdk 36`. Versions are in `gradle/libs.versions.toml`.

## 2. Architecture

Two Gradle modules.

```
core/   pure Kotlin/JVM, no Android. Models, CanteenProvider, iCanteen HTML client, parsers.
app/    Android app (Compose UI, view models, storage, reminders, app lock).
```

`CanteenProvider` (core) is the seam: everything iCanteen-specific is behind it, so another
backend (for example an official API, should one appear) can replace `ICanteenClient` without touching the UI.

### core (`yt.ivy.librecanteen.core`)

| File | Role |
| --- | --- |
| `CanteenProvider.kt` | Typed interface: login, status/credit, day menus, order/cancel/switch, notes, history, payments, password change, allergen settings, exchange (offers, take, put, withdraw). |
| `icanteen/ICanteenClient.kt` | OkHttp client for iCanteen 2.22. Session handling, per-page order tokens, verification by re-reading. |
| `icanteen/Parsers.kt` | Jsoup parsers: login/status, day menu (`DayMenuParser`), history, payments. |
| `icanteen/ActionParsers.kt` | Parsers for action results, allergen form, password form, exchange (`ExchangeParser`). |
| `model/Models.kt` | `Meal`, `Dish` (with `triggeringAllergens`), `MealState`, `DayMenu`, `Money`, `ExchangeOffer`, `ExchangePutAction`, `ExchangeWithdrawAction`, `Meal.exchangedQuantity`, ... |
| `model/TimeSerializers.kt` | Serializers for the offline cache. |

Tests live in `core/src/test` and run against anonymized HTML in
`core/src/test/resources/fixtures/icanteen/2.22/`. Files named `synthetic_*` were written by hand
from the site's own scripts, not captured.

### app (`yt.ivy.librecanteen`)

| Area | Files |
| --- | --- |
| Entry | `LibreCanteenApp.kt`, `MainActivity.kt`, `ui/AppRoot.kt` (3 tabs: Info, Lunch, Burza; wiring of view models) |
| Data | `data/CanteenRepository.kt` (session + re-login, passthroughs), `CredentialVault.kt` (Android Keystore, non-exportable key), `AppSettings.kt`, `MenuCache.kt` (`FileMenuCache`, offline), `ReminderPolicy.kt` |
| Reminders | `reminders/Reminders.kt` (WorkManager `ReminderWorker`, from Thursday if next week has no order) |
| Lunch | `ui/lunch/LunchScreen.kt`, `LunchViewModel.kt`, `LunchNav.kt` (week/month navigation logic), `CommonDishes.kt` (shared leading dishes) |
| Burza | `ui/exchange/ExchangeScreen.kt`, `ExchangeViewModel.kt` |
| Other screens | `ui/history/HistoryScreen.kt`, `ui/login/LoginScreen.kt`, `ui/settings/SettingsScreen.kt`, `DebouncedSaver.kt`, `ui/lock/*` (biometric app lock) |
| UI kit | `ui/components/DateBar.kt`, `Swipe.kt` (`horizontalSwipe`, 56 dp threshold), `ui/theme/Theme.kt`, `Icons.kt` (Material Symbols paths for icons missing from `material-icons-core`), `ui/Format.kt` |
| Resources | `res/values/strings.xml` (Czech, default), `res/values-en/strings.xml`. **Every string needs both.** |

## 3. How iCanteen behaves (2.22, reverse-engineered)

iCanteen has no JSON API. It is server-rendered JSF/JSP. Facts learned by reading it:

- **Login:** `POST /j_spring_security_check`. After login the main page (`main.jsp`) must be visited
  before day fragments work.
- **Day fragment:** `faces/secured/db/dbJidelnicekOnDayView.jsp?day=…`. Weekend and holiday answer
  with the standard "no meals" text, which the parser treats as an empty menu.
- **Order operations:** `db/dbProcessOrder.jsp?time&token&ID&day&type=…`. `time` and `token` are signed
  by the server **per page**, so they cannot be reused after a session renewal. Types seen:
  `make`, `delete`, `reorder`, `none`, `note`, `getburza` (take from the exchange, issued from the
  burza page with the pickup point; redirects to `main.jsp`), `multiburza` (put an ordered, locked meal
  into the exchange, with `amount`), `minusburza` (withdraw it).
  Success bodies contain the credit block (`#kreditInclude`); errors are JSON `{"message": …}`.
- **Burza page:** `faces/secured/burza.jsp`. Table rows: variant, date, description, pickup point, count,
  and a button whose `onclick` navigates to the `getburza` URL (the HTML is entity-escaped, unescape it
  first). **Own offers are not listed there.**
- **Own offers** appear on the day fragment: the meal shows a `fa-exchange` icon and a `minusburza`
  link ("1 ks z burzy"). The put control only appears on ordered meals past the cancel deadline.
  Putting into the exchange does not change credit; the credit changes when someone takes it.
- **Notes** are stored HTML-escaped by the server, so decode once more when reading, drop `<`, cap at 200.
- **Allergens:** codes only appear inline in the soup text; the names and the selectable list come from
  the allergen settings form. The app edits that form.
- Multiple portions per meal exist in the system but not at the author's canteen, so they are not implemented.

## 4. Rules the code lives by

1. **Verify every state change against the server.** After an order, cancel, switch, note, exchange put,
   take or withdraw, re-read the day and check the result. Never trust the response body alone.
2. **Never retry order or exchange operations automatically.** Tokens are one-shot and a blind retry
   can double-spend. Reload and ask again instead. Repository calls use `retry = false`.
3. **Unknown markup is an error**, not a guess. Wrong data here can mean a wrong order.
4. **HTTPS only** for canteen addresses.
5. **Password never in saved instance state**; login stored encrypted with a Keystore key that cannot be exported.
6. **Offline cache** is read-only: ordering is disabled, and the cache strips action links (order, exchange).
7. **Anonymize fixtures** before committing: names, usernames, addresses, balances, CSRF values, order tokens.
8. Prefer live read-only probing; any state-changing probe against a real account needs the user's
   approval and must be restored afterwards.

## 5. Current state of the app

### Navigation
Bottom bar: **Info** (order history and payments), **Lunch** (menu), **Burza** (exchange).
The top bar is one line with the user's credit. Settings are split into category pages: Customization,
Account and security (including app lock), Allergens, Notifications, About. Back returns to the list first.

### Lunch
- Week view (Monday to Sunday) or month view (switch in settings or long-press the date bar).
- Week list scrolls to today once per app start. Horizontal swipe on the meal list changes day (month view)
  or week (week view); swipe on the month calendar changes month. Month title opens a month/year picker.
- Month calendar has thin grid lines between the cells (no outer border), a dot under ordered days.
- Each day: header "Weekday, d. m." (with "today" tag), then **one container** (rounded, with a 3 dp
  theme-colored line as its left border and half the left corner radius) holding the shared soup in its own
  inner card and one card per meal.
- **Meal card:** title is the first dish not shared with the other meals (squeezed up to 5 % letter spacing
  to stay on one line, wraps otherwise); allergen codes under it (red with warning icon where they match the
  user's selection); (i) button top right opens deadlines and allergen names; swap icon + "In exchange" if
  offered. Footer: note field (borderless, pencil icon), quantity if more than one, price, and an order
  **checkbox**. Locked unordered meals hide price and checkbox. Meals that can go to the exchange show an
  icon-only button (swap icon to put in, close icon to take back) in place of the checkbox.
- Order/cancel/switch has an "Undo" snackbar.

### Burza
Sections "Offered by you" (found by scanning menus from today for 7 days, hidden when empty) and "Offers by
others". Cards show the day ("Weekday, d. m."), dishes, pickup point, pieces and a filled 36 dp button; taking
asks for confirmation with the price.

### Settings and storage
Allergen edits autosave 1.5 s after the last tick (`DebouncedSaver`, one request per batch), flush on leaving
the page, with a small status line. Reminders (WorkManager) and biometric app lock are optional.

### Login
Centered brand header, one card with address, username, password (leading icons, text toggle to show the
password), tall sign-in button, short privacy note.

## 6. Timeline

| Date | Commit | What |
| --- | --- | --- |
| 2026-10-16 | `6d109eb` | `:core` with login, status and daily menu parsing |
| | `15d04ba` | Ordering, order history, payments, password change in core |
| | `165d446` | Android app: week menu, ordering, history, payments, settings |
| | `5bd6843` | Notes, allergen highlighting, offline cache, reminders, app lock |
| | `e90d901` | Handle the canteen's double HTML escaping of notes |
| | `2a0fe2f` | Edit allergen preferences from the app |
| | `2514ac1` | UI redesign: top bar with account, bottom navigation, week and month views |
| 2026-10-17 | `e35c292`, `0c99250` | Deadlines in details dialog, allergens per dish, meal highlight |
| 2026-10-19 | `2503c6b` | One-line top bar, info button |
| | `dc59868` | Read the exchange and take meals from it |
| | `9dc6d0a` | Put ordered meals into the exchange and take them back |
| | `e14e680`, `c15354e` | Burza tab, own offers section, swap icon |
| | `762017a`, `f509293` | Weekends, swipe, month picker, settings category pages |
| | `7ea6096`, `f2e2134` | Shared soup shown once, compact card footer, allergen warning icon |
| | `e5136f0` | Allergen autosave, spacing, day label fix |
| | `5f65c31` | Release signing from an external keystore file |
| | `b7bf7e7`, `6cbcac4` | Meal name as title, checkbox ordering, icon-only exchange buttons |
| | `ebe49e4` .. `8cba23d` | One container per day with left line, soup in its own card |
| | `12a16cd`, `43e26cd` | Exchange card tidy-up, calendar grid lines, login redesign |

Live-tested against a real account during development (always restored afterwards): ordering and
cancelling, notes, allergen settings, taking a meal from the exchange, putting one in and withdrawing it.

## 7. Working on it

### Build and test
JDK 21 and the Android SDK (platform 36) are required; point `ANDROID_HOME` or `local.properties` at the SDK.

```sh
./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Notes:
- The development host has little RAM: use `--no-daemon -Pkotlin.compiler.execution.strategy=in-process`
  and kill idle Kotlin daemons.
- Lint is expected to show only cosmetic warnings (`UseKtx`, `UseTomlInstead`). Anything else, including
  `UnusedResources`, should be fixed.
- There is no emulator on the host, so **UI changes are not visually verified by the agent.** Say so
  when reporting, and rely on the user's feedback from a real phone.

### Release
- Release build is minified and shrunk (`proguard-rules.pro`). The signing key lives **outside the repo**;
  `LIBRECANTEEN_KEYSTORE_PROPERTIES` points at a properties file (`storeFile`, `storePassword`,
  `keyAlias`, `keyPassword`). Without it the release APK is unsigned.
  `LIBRECANTEEN_KEYSTORE_PROPERTIES=/path ./gradlew :app:assembleRelease`
- Updates only install over a build signed with the same key, so **the keystore must be backed up**.
  Debug and release builds use different keys and cannot replace each other.
- The release build has been built and signature-verified but not run on a device yet.
- Version is still 0.1.0 (`versionCode 1`); bump both before the next release.

### Distribution during development
Debug APKs are built after each change and served from a local directory over the LAN/Tailscale; the user
installs from the phone and reports back. One APK is kept at a time, named by commit hash.

### Commits
Small commits with an imperative subject line describing the visible change. Run a leak scan on the staged
diff first (see `AGENTS.md`).

## 8. Plan and ideas

Priority is not confirmed by the user; the assistant's suggestion is first.

1. **Pickup reminder and low-credit warning** notifications (approved in principle).
2. **Home-screen widget** (today's meal / order state) (approved in principle).
3. **Tablet / wide layout** (approved in principle).
4. **Release prep:** public GitHub repository, CI (tests + lint), screenshots, privacy note in README,
   version bump, signed release distribution, a **parser health check** (detect when the server markup changes
   and fail loudly with a useful message).
5. Eye icon for the password field on the login screen (text toggle for now; the vector path has to be
   verified on a device).
6. Multiple portions per meal, if another canteen needs it.
7. Possibly pitch to Z-WARE once the app is stable.

## 9. Open items and unverified things

- Reminders (`ReminderWorker`) have not been tested by the user yet.
- Gestures (swipe, month picker), the latest card layout, left line and calendar grid were built without a
  device; confirm on the phone.
- Release APK not yet run on a device (check that R8 did not break login, menu, ordering, notifications).
- The user's test password was used during development and must be changed by them; never commit it.
- Letter-spacing squeeze limit for meal titles is a constant (`TITLE_TIGHTENING_EM`), not a setting.

## 10. How to add an entry

Append to the log below, newest at the top. One entry per work session or notable change:

```
### YYYY-MM-DD, short title
- What changed and why (the reason, not a diff summary)
- Decisions made or user preferences learned
- What was verified (tests, lint, device) and what was not
- Follow-ups
```

Also update sections 5, 6, 8 and 9 above when they stop being true, and the README feature list when the
visible behavior changes.

## Log

### 2026-10-19, journal and agent instructions
- Wrote this journal and `AGENTS.md` so later sessions start from the same knowledge.
- State at this point: latest UI commit `43e26cd`; release signing set up; everything above is current.
