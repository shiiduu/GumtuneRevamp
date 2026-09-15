# BlockAssist

A client-side Fabric mod for Minecraft **26.1.2**, built as a development and
testing tool for a privately operated server (see `docs/26.1.2-api-notes.md`
for the reasoning behind several implementation choices, and the project
history for the authorization/context this was built under). It performs
gameplay actions - aiming, rotating, breaking blocks, harvesting mature crops
- through the same public client APIs normal player input goes through,
using honest, visible rotation and treating the server as authoritative for
whether any interaction actually succeeded.

## Minecraft version

- Minecraft: **26.1.2** (Mojang official mappings - this is the first
  unobfuscated Minecraft version, no Yarn mappings involved)
- Fabric Loader: **0.19.5**
- Fabric API: **0.155.3+26.1.2**
- Fabric Loom: **1.17.21**
- Gradle: **9.5.1** (via wrapper)
- Java: **25** (required to compile and run; see below if you don't have it)

## Development requirements

- JDK 25. If you don't have one installed system-wide, point `JAVA_HOME` at
  any JDK 25 distribution before running Gradle, e.g. (PowerShell):
  ```
  $env:JAVA_HOME = "C:\path\to\jdk-25"
  ```
- No global Gradle install is required - use the wrapper (`./gradlew` /
  `gradlew.bat`).
- VS Code with the Java extension pack works fine for editing; there is no
  project-specific IDE configuration beyond what Loom generates.

## Building

```
./gradlew build
```

Produces `build/libs/blockassist-<version>.jar` (plus a sources jar). Unit
tests (`src/test/java`) run automatically as part of `build`; run them alone
with `./gradlew test`.

## Launching the development client

```
./gradlew runClient
```

This downloads/decompiles Minecraft assets on first run and opens a game
window - not something to run unattended. Once in a world, press **B** to
toggle automation and watch the log for `BlockAssist initialized` and
`Automation toggled: ON/OFF`.

## Current functionality

- Client initializes and logs `BlockAssist initialized`.
- Keybinds: toggle automation (**B**), toggle crop automation (**N**), toggle
  HUD (**M**), open configuration screen (**K**). All settings persist to
  `config/blockassist.json`.
- A native in-game **configuration screen** (no Cloth Config/Mod Menu
  dependency - see docs/architecture.md for why) exposes every setting
  across General/Targeting/Rotation/Interaction/Crops/Statistics sections,
  with tooltips and range-validated numeric fields that never crash the
  client on bad input.
- `TargetScanner` finds candidate interactions (block + face + exact aim
  point, not just a position) in a configurable box around the player,
  rejecting air/blacklisted/out-of-range/recently-attempted candidates as
  cheaply as possible before the more expensive face/line-of-sight checks.
- Face-aware targeting: pick a specific face (TOP/BOTTOM/N/S/E/W, only
  accepted if actually usable) or ANY (nearest usable face by rotation
  cost, or a pure-geometry best-guess when line-of-sight checking is off).
- `TargetSelector` picks one candidate: **CLOSEST** (nearest) or **EASIEST**
  (least rotation needed, ties broken by distance). Deterministic - no
  randomization.
- `RotationController` turns the player's real view toward the target's
  exact hit point (instant or smooth, configurable speed). There is no
  hidden/fake rotation channel; the player's camera is exactly what aims.
- `InteractionController` drives the real `MultiPlayerGameMode`
  start/continue/stop block-breaking methods, the same ones vanilla input
  handling uses, with a bounded retry loop (`maxAttemptsPerTarget`/
  `retryDelayMs`) for the normal aiming/raycast timing race rather than
  failing outright on the first miss.
- `BreakState` runs an explicit state machine
  (`IDLE -> TARGETING -> ROTATING -> STARTING -> BREAKING -> COMPLETE/FAILED
  -> ...`) per docs/architecture.md. Confirmation is decoupled into an
  independent, async `ConfirmationQueue` so the pipeline picks its next
  target immediately after a break is predicted, instead of blocking on a
  fixed grace period - this was the main fix for the "choppy/aimlock-like,
  ~1-2 requests/sec" throughput problem found during testing; see
  docs/26.1.2-api-notes.md for the full investigation.
- `RecentAttemptTracker` prevents immediately re-selecting a block that was
  just attempted but hasn't visibly changed yet (never a permanent
  blacklist).
- `InteractionStats` tracks acquired/requested/completed/confirmed/failed/
  expired counts, rolling per-second rates, average target-to-request and
  request-to-confirmation latency, and per-phase instantaneous durations
  (rotation/confirm/scan ms) - a request is never counted as a confirmed
  break just because it was sent.
- `CropAutomation`: mature-crops-only, break-only (no replanting yet - the
  config fields exist for the GUI but have no effect), using the real
  `CropBlock.isMaxAge` check so it works for every vanilla crop without
  per-crop special-casing; optional crop whitelist.
- A HUD (toggleable) shows status, current target + face, state,
  acquire/request/complete/confirm/failed rates, and the last rotation/
  confirm/scan durations.
- Debug mode (`debugMode` in config) logs target selection, rotation,
  interaction requests/results, confirmations, and state transitions;
  produces no log output when disabled.

## Current limitations

- **actionsPerSecond is a decision-cadence target, not a hard interaction
  rate.** Interaction decisions are made from the client tick event
  (thread-safe, matches how vanilla drives `MultiPlayerGameMode`); values
  above roughly the tick rate (~20/s) do not cause extra
  `startDestroyBlock`/`continueDestroyBlock` calls within a single tick. See
  `docs/26.1.2-api-notes.md` ("Scheduling") for the full reasoning and the
  alternative that was considered and rejected.
- **Confirmation is client-predicted, not server-verified.** BlockAssist
  observes `ClientPlayerBlockBreakEvents.AFTER` (fires when the client
  applies its own predicted block removal) and then watches the local block
  state for a configurable grace window (`confirmationTimeoutMs`) to see if
  the server's prediction reconciliation reverts it. This is the closest
  26.1.2 exposes without adding server-side instrumentation - see the
  "Confirmation" section of `docs/26.1.2-api-notes.md` for what server-side
  logging would add.
- **Only `CropAutomation` exists.** A generic `BlockAutomation` feature
  (non-crop blocks) was scoped out of this milestone; `AutomationController`
  already accepts any `Predicate<BlockState>` so adding one is a small
  extension.
- **No replanting**, no tool-checking.
- **Not yet re-verified live against the throughput fix.** The ~1-2/s
  investigation and its fixes (decoupled confirmation, bounded start
  retries, configurable timeouts) are based on source-level analysis of the
  reported symptoms plus new instrumentation, not a fresh in-game
  measurement - this session cannot launch the game against your server.
  Please re-test and check the HUD's new per-second/latency numbers.
- `runClient` was not exercised in this development session (unattended
  environment - a GUI game window and, on first run, a lengthy asset
  download/decompile are impractical to run unattended). Everything above
  was validated via `./gradlew build`/`test` (compiles cleanly against the
  real 26.1.2 API, all unit tests pass) rather than a live playtest.
