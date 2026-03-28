# Chanceman Tracker Sync

`Chanceman Tracker Sync` is a RuneLite external plugin for `https://chanceman-tracker.github.io/`.

It exports local account progress data for the tracker. You can either copy the tracker blob manually or use the direct upload button, which opens the tracker and exposes a temporary localhost bridge on `127.0.0.1` for the browser handoff.

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
3. Choose one of these flows:
4. Click `Copy tracker blob`, then open `https://chanceman-tracker.github.io/upload` or `/reupload` and paste the blob into the `RuneLite tracker blob` field.
5. Or click `Open tracker with data` to open the tracker directly with a temporary localhost bridge handoff.

## Important notes

- The plugin stores data locally and survives RuneLite restarts.
- The direct upload flow starts a temporary HTTP server bound to `127.0.0.1` only. It shuts down after a successful handoff, plugin shutdown, or a short timeout.
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
