# LibreCanteen

An open-source Android client for school canteens running **iCanteen**, built to be pleasant to use:
a calm, fast interface for the weekly menu, ordering, credit, payments and account settings
(including changing your password).

> **Not affiliated with Z-WARE s.r.o.** LibreCanteen is an independent, non-commercial project.
> "iCanteen" is a product of Z-WARE s.r.o. This project contains none of its code, styles or images.

## Status

Early development. Only the `:core` library exists so far (read-only: login, account status, daily menu).
The Android app (`:app`) is next.

## How it works

iCanteen has no public API, so `:core` reads the same server-rendered pages a browser would and
turns them into typed models. It is built against **iCanteen 2.22**. Unknown markup is reported as
an error instead of being guessed, because wrong data here can mean a wrong order.

Everything backend-specific lives behind `CanteenProvider`, so another implementation
(for example an official API, should one appear) can replace it without touching the UI.

```
core/   pure Kotlin/JVM: models, CanteenProvider, iCanteen HTML client and parsers
app/    Android UI (planned)
```

## Development

Requires JDK 21.

```sh
./gradlew :core:test
```

Parser tests run against anonymized HTML samples in `core/src/test/resources/fixtures/`.
When adding samples, remove names, usernames, addresses, balances, CSRF values and order tokens first.

## Privacy

Your canteen address and password are entered in the app and stay on your device.
No credentials, school names or personal data belong in this repository.

## License

MIT, see [LICENSE](LICENSE).
