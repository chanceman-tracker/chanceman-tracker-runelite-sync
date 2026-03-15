# Chanceman Tracker Sync

`Chanceman Tracker Sync` is a RuneLite external plugin for `https://chanceman-tracker.github.io/`.

It exports a local JSON blob with account progress data that can be pasted into the tracker. The plugin is offline-only and does not make network requests.

## What it exports

The current blob includes:

- player name
- account type
- skill levels
- quest states
- quest points
- achievement diary tier completion
- achievement diary per-step completion
- combat achievement task counts and tier completion
- slayer points and task streak
- slayer unlocks such as `Bigger and Badder` and `Like a Boss`
- barbarian training vars used by the tracker
- hunter rumours completed, after the plugin has seen the in-game total message once

## How to use it

1. Open RuneLite with the plugin enabled.
2. Open the `Chanceman Tracker Sync` side panel.
3. Click `Copy tracker blob`.
4. Open `https://chanceman-tracker.github.io/upload` or `/reupload`.
5. Paste the blob into the `RuneLite tracker blob` field.

## Important notes

- The plugin stores data locally and survives RuneLite restarts.
- Hunter rumours are only updated after RuneLite sees the in-game chat message that reports your total rumours completed.

## Development

```powershell
./gradlew.bat test
./gradlew.bat run
```

Useful tools while developing:

- RuneLite developer mode
- Var Inspector

## License

BSD-2-Clause. See [LICENSE](LICENSE).