# Instructions for agents working on LibreCanteen

## First and last step: the journal

1. **Before doing anything, read `JOURNAL.md` completely.** It holds the project goals, architecture,
   server behavior, rules, current state, plan and open items. Do not re-derive what it already says.
2. **Before you finish, write to `JOURNAL.md`:**
   - add a dated entry at the top of the *Log* section (what changed and why, decisions, what was
     verified and what was not, follow-ups);
   - update the sections that are no longer true (current state, timeline, plan, open items);
   - keep it free of personal data.
   Commit the journal together with the change it describes.

## Project in one paragraph

Unofficial open-source Android client for iCanteen school canteens. Kotlin, Compose, OkHttp, Jsoup.
`:core` is pure JVM (models, `CanteenProvider`, HTML parsers), `:app` is the Android UI. No backend.

## Non-negotiable rules

- **No personal data in the repository**: no credentials, school or canteen names and addresses,
  usernames, balances, order tokens, real names, phone numbers. This includes code, fixtures, tests,
  docs, commit messages and the journal. Fixtures must be anonymized.
- Before every commit, scan the staged diff for the author's private strings. The patterns live in the
  untracked file `.leak-patterns` (one regex per line, ignored by git); if it is missing, ask the user
  for them:
  `git diff --cached | grep -iEf .leak-patterns`
  Any hit blocks the commit. Never put those patterns in a tracked file.
- Credentials for live testing come only from environment variables and are never written to disk,
  logs or output. Read-only probing is fine; anything that changes state on a real account needs the
  user's approval and must be restored afterwards.
- **Server write operations** (order, cancel, switch, note, exchange): verify the result by re-reading
  the day, and never retry automatically. The server's per-page tokens are one-shot.
- Unknown markup is an error, not a guess. HTTPS only.
- Do not commit signing keys, keystores or `keystore.properties`; they live outside the repo.
- Never push, force-push or rewrite history unless the user asks.

## Conventions

- Match the surrounding code. Comments only for non-obvious reasons.
- Every user-visible string goes into both `res/values/strings.xml` (Czech, default) and
  `res/values-en/strings.xml`. Remove strings that become unused (lint reports `UnusedResources`).
- Keep `:core` free of Android dependencies. New parser behavior needs a fixture test; capture real
  markup read-only, anonymize it, put it under `core/src/test/resources/fixtures/icanteen/2.22/`.
- Update `README.md` when the feature list or build steps change.
- Commit messages: imperative, one line describing the visible change, optional short body.
  Small, focused commits.

## Build and verify

```sh
./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

- Low-RAM host: add `--no-daemon -Pkotlin.compiler.execution.strategy=in-process`, and stop idle
  Kotlin daemons afterwards.
- Expected lint output: only the cosmetic `UseKtx` and `UseTomlInstead` warnings. Fix anything else.
- Release: `LIBRECANTEEN_KEYSTORE_PROPERTIES=/path/to/keystore.properties ./gradlew :app:assembleRelease`.
- There is no emulator here. Report honestly that UI changes were not seen on a device, and ask the
  user to check on their phone.

## Working with the user

The user mixes Czech and English; answer in the language of the last message. They test debug APKs on a
phone and give design feedback in small steps, so keep changes tight and publish a new APK after each
change. Do not remove or redesign things that were not asked for. Be explicit about anything you
assumed (for example how an ambiguous design request was interpreted).
