# Architecture

```
                          ClientTickEvents.END_CLIENT_TICK
                                      |
                                      v
                         +----------------------------+
                         |    AutomationController      |
                         | (owns the tick loop and the  |
                         |  BreakState machine)         |
                         +----------------------------+
                    |         |          |          |        |
        reads config|         |          |          |        | ticks every frame,
                    v         v          v          v        | independent of BreakState
       +---------------+ +-----------+ +------------+ +-------------------+  +-------------------+
       |BlockAssistConfig| |TargetScanner| |TargetSelector| |RecentAttemptTracker|  |ConfirmationQueue  |
       | (ConfigManager) | +-----------+ +------------+ +-------------------+  | (async, decoupled |
       +---------------+       |               |                              |  from BreakState) |
                                v               v                              +-------------------+
                       List<Target> candidates -> Target                                ^
                     (pos, BlockState, face Direction, hitPoint Vec3)                    |
                                                          |                    enqueue(pos, preBreakState)
                    +-------------------------------------+---------------------+        | on predicted break
                    v                                                            v        |
          +--------------------+                                       +----------------------+
          | RotationController |  --sets real player yaw/pitch,-->      | InteractionController |
          |  (aims at hitPoint)|    no hidden/fake channel               +----------------------+
          +--------------------+                                                  |
                                                                     MultiPlayerGameMode.start/continue/stopDestroyBlock
                                                                                   |
                                                                                   v
                                                                  ClientPlayerBlockBreakEvents.AFTER
                                                                    (client-predicted break signal)
                                                                                   |
                                                                                   v
                                                                          InteractionStats
                                                                (acquired/requested/completed/confirmed/
                                                                 failed/expired, rolling rates, latencies,
                                                                 per-phase durations)

  CropAutomation supplies AutomationController's per-tick "is this block a
  valid target" predicate (BlockState -> boolean) when crop automation is
  enabled; it is the only thing that knows what a "valid crop" is.

  HudRenderer and ConfigScreen both just read/write AutomationController's
  stats()/state() and the live BlockAssistConfig - neither owns any pipeline
  state of its own.
```

## Decoupled confirmation (why BREAKING doesn't wait anymore)

Earlier versions transitioned `BREAKING -> WAITING_FOR_CONFIRMATION ->
COMPLETE -> IDLE`, blocking the whole pipeline on a fixed confirmation grace
period before picking the next target. This was the dominant cause of the
observed ~1-2 requests/sec throughput - see docs/26.1.2-api-notes.md
("Investigation: the ~1-2 requests/sec throughput problem") for the full
analysis.

Now, the instant a break is predicted (`ClientPlayerBlockBreakEvents.AFTER`
fires for the current target), `AutomationController` hands the position and
its pre-break `BlockState` to `ConfirmationQueue` and returns straight to
`IDLE` - the state machine does not wait. `ConfirmationQueue.tick(...)` runs
every controller tick, independently of `BreakState`, checking each pending
entry against the live world: if the block reverted to its pre-break state,
the server rejected it (`recordFailedAfterRequest`); if it's still changed
after `confirmationTimeoutMs`, it's confirmed (`recordConfirmed`). Several
targets' confirmations can be in flight at once even though only one target
is ever being actively aimed-at/broken (`RecentAttemptTracker` keeps a
position out of the scanner until its confirmation resolves, so it can never
be double-selected).

**What was *not* done, and why:** true multi-target pipelining - rotating
toward target B while target A is still `BREAKING` - was considered and
rejected. The player has exactly one camera; there is no way to aim at two
blocks at once without a hidden/fake rotation channel, which is explicitly
disallowed. For crops specifically this doesn't cost anything in practice,
since `BREAKING` itself is near-instant (0-hardness blocks complete in one
`continueDestroyBlock` call - see the API notes); the only genuinely
parallelizable stage was confirmation-watching, which is now parallel.

## Face-aware targeting

A candidate is no longer just a `BlockPos`. `core.Target` is a record of
`(pos, state, face, hitPoint)`: which block, which face of it, and the exact
point `RotationController` aims at. `TargetScanner` resolves a face for each
candidate according to `faceMode`:
- A **specific face** (`TOP`/`BOTTOM`/`NORTH`/`SOUTH`/`EAST`/`WEST`) is only
  accepted if `RaycastUtils.isFaceUsable` confirms a real raycast lands on
  that exact face (when `lineOfSight` is on) - never silently falls back to
  a different face.
- **ANY** picks the usable face needing the least rotation from the
  player's current aim (`allowNearestVisibleFaceWhenAny`), or, when
  `lineOfSight` is off (raycasting skipped for performance), the face whose
  outward normal points most toward the player - pure geometry, no world
  access, see `TargetScanner.nearestFacingDirection`.

`InteractionController` validates the live crosshair against the target's
*position* but deliberately not its *face* - see the comment on
`InteractionController.currentHitOn` for why requiring an exact face match
would reintroduce spurious misses without changing what actually breaks.

## Components

### `core.AutomationController`
Single entry point registered on `ClientTickEvents.END_CLIENT_TICK`. Reads
the live config every tick (so keybind/GUI changes take effect immediately),
decides whether automation should be running at all (enabled + a feature
active + world/player/screen state sane), ticks `ConfirmationQueue`, and
drives `BreakState` through exactly one relevant action per tick. Resets the
state machine (and cancels any in-progress interaction) whenever automation
is disabled, the world changes, the player dies, a screen opens, or the
target stops being valid - correctly discarding any in-flight `InteractionStats`
bookkeeping (`discardAcquired`/`discardRequest`) rather than leaking it or
miscounting it as a real failure.

### `core.TargetScanner`
Walks a bounded box (`range` horizontal, `verticalRange` vertical) around the
player's block position using a single reused `BlockPos.MutableBlockPos`,
rejecting candidates in order from cheapest to most expensive: distance ->
recently-attempted -> air -> blacklist -> whitelist -> feature filter (e.g.
crop maturity) -> face resolution (raycasts, only for candidates that passed
every cheaper check). Never scans more than the configured box, never more
than `maxCandidates` results.

### `core.TargetSelector`
Pure selection logic over a candidate list: `CLOSEST` (nearest by squared
distance to the target's hit point) or `EASIEST` (least total rotation
needed, ties broken by distance). Deterministic; no randomization exists.
The real ranking method takes a plain eye position/yaw/pitch rather than a
`LocalPlayer`, specifically so it's unit-testable without a running game;
`select(List, LocalPlayer, SelectionMode)` is a one-line wrapper over it.

### `core.RotationController`
Owns a target yaw/pitch (computed toward a `Target`'s `hitPoint`, not just
the block center) and steps the player's actual `yRot`/`xRot` toward it each
tick (instant, or smooth at a configurable degrees-per-tick speed).
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
phase, current `Target`, time-in-phase, start-attempt count for the
`STARTING` retry loop). All transitions go through `BreakState.transition(...)`,
which also emits the `[STATE]` debug log line. No parallel boolean flags.
`WAITING_FOR_CONFIRMATION` remains a defined phase (handled defensively if
ever entered) but the default flow no longer uses it - confirmation is
`ConfirmationQueue`'s job now (see above).

### `core.ConfirmationQueue`
Independent list of "predicted break, awaiting a real outcome" entries,
ticked every controller tick regardless of `BreakState`'s current phase.
This is what lets the pipeline move on immediately after a break instead of
blocking on a grace period - see "Decoupled confirmation" above.

### `core.RecentAttemptTracker`
A `BlockPos -> (timestamp, attempt count)` map with a configurable
expiration (`attemptMemoryMs`). Consulted by the scanner to skip
recently-attempted positions; entries are removed the moment a break is
confirmed, and simply expire on their own otherwise - never a permanent
blacklist. A position is marked attempted at *acquisition* time (not just on
a successful start), so it can't be re-selected while a `STARTING` retry
loop is still in progress on it.

### `core.InteractionStats`
Counts and rolling per-second rates for every stage (`acquired` ->
`requested` -> `completed` -> `confirmed`/`failed`/`expired`), plus
exponentially-smoothed average latencies (target-to-request,
request-to-confirmation) and named per-phase instantaneous durations
(`recordPhaseDuration("ROTATING", ms)`, etc., surfaced on the HUD). Every
`record*` call that starts tracking a pending latency is paired with exactly
one call that resolves or discards it - see the class doc for the accounting
rules that keep the internal pending-latency queues from leaking or being
misattributed across concurrent confirmations.

### `features.CropAutomation`
The only place that knows what a "valid crop target" is: must be a
`CropBlock` instance, optionally restricted to `cropWhitelist`, and (when
`requireMatureCrop` is set, the default) must satisfy `CropBlock.isMaxAge`.
Supplies this as the `Predicate<BlockState>` `AutomationController` passes to
the scanner. `replantEnabled`/`replantDelayMs` exist in config for the GUI
but have no effect yet - no replanting logic has been implemented.

### `config.BlockAssistConfig` / `config.ConfigManager`
Plain JSON-serialized data class (Gson, pretty-printed) at
`config/blockassist.json` under the standard Fabric config directory.
Loaded once at client init; keybind toggles and the `ConfigScreen` both
mutate the same live instance in place, and save on every change/close.

### `input.KeybindManager`
Registers the four key mappings (toggle automation, toggle crop automation,
toggle HUD, open config) and, once per tick, drains their click queues into
direct config field flips or opening `ConfigScreen` (config is the single
source of truth - there is no separate "is automation on" flag anywhere
else).

### `ui.HudRenderer`
Registered via `HudElementRegistry.addLast`; reads
`AutomationController.stats()`/`state()` and the live config every frame and
draws plain text lines (target/face/state, acquire/request/complete/confirm/
failed rates, last rotation/confirm/scan durations, pending-confirmation
count). No interaction, no state of its own.

### `ui.ConfigScreen`
A native `Screen` (not Cloth Config/Mod Menu - see the class doc for why:
those libraries' 26.1.2 API surface hasn't been source-verified the way
everything else in this project has, so a plain `Screen` built from
real, decompiled-and-confirmed vanilla widgets - `HeaderAndFooterLayout`,
`LinearLayout`, `ScrollableLayout`, `CycleButton`, `EditBox`, `Tooltip` -
was used instead). Every field edits the live `BlockAssistConfig` directly;
numeric fields go through `util.ConfigValidation` (parse + range-clamp,
never throws) so malformed input is simply ignored rather than crashing the
client. Opened via the "Open Configuration" keybind (default **K**).

## State machine

```
IDLE --(decision-gate allows + candidate found)--> TARGETING
TARGETING --(target still valid)--> ROTATING
TARGETING --(target invalid)--> FAILED
ROTATING --(rotation converged)--> STARTING
ROTATING --(target invalid)--> FAILED
STARTING --(start accepted)--> BREAKING
STARTING --(start missed, attempts remain)--> STARTING (retry after retryDelayMs)
STARTING --(start missed, maxAttemptsPerTarget reached)--> FAILED
BREAKING --(ClientPlayerBlockBreakEvents.AFTER fires for this pos)--> COMPLETE
                                                          (+ enqueued into ConfirmationQueue, async from here on)
BREAKING --(player too far / target invalid / continue rejected / breakTimeoutMs exceeded)--> FAILED
COMPLETE --(next controller tick)--> IDLE
FAILED --(next controller tick)--> IDLE

ConfirmationQueue (independent of the above, ticks every frame):
  pending --(block state still changed after confirmationTimeoutMs)--> confirmed
  pending --(block state reverted to pre-break)--> failed
```

Any tick, from any phase, the controller resets straight to `IDLE` (cancelling
an in-progress interaction if one exists) when: automation is disabled, the
player/world becomes unavailable, the player dies, a screen opens, or the
Minecraft `ClientLevel` instance changes (world switch/rejoin). Pending
`ConfirmationQueue` entries are not touched by this reset (they keep
resolving in the background); only the in-flight `BreakState` target's
stats bookkeeping is discarded.
