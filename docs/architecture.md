# Architecture

```
                          ClientTickEvents.END_CLIENT_TICK
                                      |
                                      v
                         +---------------------------+
                         |   AutomationController     |
                         |  (owns the tick loop and    |
                         |   the BreakState machine)   |
                         +---------------------------+
                          |      |       |        |
             reads config |      |       |        | reads
                     v            v       v              v
        +----------------+ +-----------+ +--------------+ +------------------+
        | BlockAssistConfig| |TargetScanner| |TargetSelector| |RecentAttemptTracker|
        | (ConfigManager) | +-----------+ +--------------+ +------------------+
        +----------------+       |               |
                                  v               v
                         List<BlockPos> candidates -> BlockPos target
                                                          |
                    +-------------------------------------+------------------------+
                    v                                                              v
          +--------------------+                                       +----------------------+
          | RotationController |  --sets real player yaw/pitch-->       | InteractionController |
          +--------------------+                                       +----------------------+
                                                                                   |
                                                                    MultiPlayerGameMode.start/continue/stopDestroyBlock
                                                                                   |
                                                                                   v
                                                                  ClientPlayerBlockBreakEvents.AFTER
                                                                    (client-predicted break signal)
                                                                                   |
                                                                                   v
                                                                          InteractionStats
                                                                    (requested/completed/confirmed/
                                                                     failed/expired, rolling rates)

  CropAutomation supplies AutomationController's per-tick "is this block a
  valid target" predicate (BlockState -> boolean) when crop automation is
  enabled; it is the only thing that knows what a "valid crop" is.

  HudRenderer reads AutomationController.stats()/state() every frame to
  render the status overlay; it never mutates anything.
```

## Components

### `core.AutomationController`
Single entry point registered on `ClientTickEvents.END_CLIENT_TICK`. Reads
the live config every tick (so keybind toggles and future config-reload take
effect immediately), decides whether automation should be running at all
(enabled + a feature active + world/player/screen state sane), and otherwise
drives `BreakState` through exactly one relevant action per tick. Resets the
state machine (and cancels any in-progress interaction) whenever automation
is disabled, the world changes, the player dies, a screen opens, or the
target stops being valid.

### `core.TargetScanner`
Walks a bounded box (`range` horizontal, `verticalRange` vertical) around the
player's block position using a single reused `BlockPos.MutableBlockPos`,
rejecting candidates in order from cheapest to most expensive: distance ->
recently-attempted -> air -> blacklist -> whitelist -> feature filter (e.g.
crop maturity) -> line-of-sight (`Level#clip`, the same raycast mechanism
vanilla block interaction uses). Never scans more than the configured box,
never more than `maxCandidates` results.

### `core.TargetSelector`
Pure selection logic over a candidate list: `CLOSEST` (nearest by squared
distance) or `EASIEST` (least total rotation needed, ties broken by
distance - candidates are already visibility/reachability-filtered by the
scanner). Deterministic; no randomization exists.

### `core.RotationController`
Owns a target yaw/pitch and steps the player's actual `yRot`/`xRot` toward it
each tick (instant, or smooth at a configurable degrees-per-tick speed).
Completion is detected by total angular distance dropping below a small
threshold. This *is* the player's real view - see
`docs/26.1.2-api-notes.md` ("Rotation") for why there is no other way to
represent aiming in 26.1.2, and why BlockAssist doesn't try to invent one.

### `core.InteractionController`
Thin, direct wrapper around `Minecraft.getInstance().gameMode`
(`MultiPlayerGameMode`) - `startDestroyBlock` / `continueDestroyBlock` /
`stopDestroyBlock`, validated against the live crosshair hit result before
every call (never acts on a stale/mismatched target). Also registers the
`ClientPlayerBlockBreakEvents.AFTER` listener and forwards it to
`AutomationController` as the "client predicted a break" signal.

### `core.BreakState` / `core.BreakPhase`
Explicit state machine (`BreakPhase` enum + `BreakState` holder: current
phase, current target position/expected state, time-in-phase). All
transitions go through `BreakState.transition(...)`, which also emits the
`[STATE]` debug log line. No parallel boolean flags.

### `core.RecentAttemptTracker`
A `BlockPos -> (timestamp, attempt count)` map with a configurable
expiration (`attemptMemoryMs`). Consulted by the scanner to skip
recently-attempted positions; entries are removed the moment a break is
confirmed, and simply expire on their own otherwise - never a permanent
blacklist.

### `core.InteractionStats`
Counts (`requestedTotal`, `confirmedTotal`, `failedTotal`, `expiredTotal`)
plus 1-second rolling windows for requested/confirmed rates and an
exponentially-smoothed average confirmation latency. "Requested" and
"confirmed" are recorded at different points in the state machine and are
never conflated.

### `features.CropAutomation`
The only place that knows what a "valid crop target" is: must be a
`CropBlock` instance, and (when `requireMatureCrop` is set, the current
default and only supported mode) must satisfy `CropBlock.isMaxAge`. Supplies
this as the `Predicate<BlockState>` `AutomationController` passes to the
scanner. No replanting logic exists yet.

### `config.BlockAssistConfig` / `config.ConfigManager`
Plain JSON-serialized data class (Gson, pretty-printed) at
`config/blockassist.json` under the standard Fabric config directory.
Loaded once at client init; keybind toggles mutate it in place and save
immediately.

### `input.KeybindManager`
Registers the three key mappings and, once per tick, drains their click
queues into direct config field flips (config is the single source of
truth - there is no separate "is automation on" flag anywhere else).

### `ui.HudRenderer`
Registered via `HudElementRegistry.addLast`; reads
`AutomationController.stats()`/`state()` and the live config every frame and
draws plain text lines. No interaction, no state of its own.

## State machine

```
IDLE --(decision-gate allows + candidate found)--> TARGETING
TARGETING --(target still valid)--> ROTATING
TARGETING --(target invalid)--> FAILED
ROTATING --(rotation converged)--> STARTING
ROTATING --(target invalid)--> FAILED
STARTING --(gameMode.startDestroyBlock succeeds)--> BREAKING
STARTING --(rejected)--> FAILED
BREAKING --(ClientPlayerBlockBreakEvents.AFTER fires for this pos)--> WAITING_FOR_CONFIRMATION
BREAKING --(player too far / target invalid / continue rejected)--> FAILED
WAITING_FOR_CONFIRMATION --(block state still changed after grace period)--> COMPLETE
WAITING_FOR_CONFIRMATION --(block state reverted - server rejected)--> FAILED
COMPLETE --(next controller tick)--> IDLE
FAILED --(next controller tick)--> IDLE
```

Any tick, from any phase, the controller resets straight to `IDLE` (cancelling
an in-progress interaction if one exists) when: automation is disabled, the
player/world becomes unavailable, the player dies, a screen opens, or the
Minecraft `ClientLevel` instance changes (world switch/rejoin).
