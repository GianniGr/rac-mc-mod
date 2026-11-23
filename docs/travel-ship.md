# Travel Ship gameplay (#13)

Single-seat atmospheric vehicle for the Overworld. The gameplay branch uses a temporary
Minecraft cuboid hull. Class 531 presentation is separate; no game disc extraction or
retail animation is part of this branch. The normal client ISO policy is unchanged.

## Test in a fresh world

Use Minecraft Java 1.21.1, NeoForge 21.1.265, Java 21, and the same build on client and
server. Use a disposable Overworld and the existing legal-media setup normally required by
the mod. Don't use an owner's existing world for this checklist.

1. Find **Travel Ship (Creative Placement)** in the mod's Creative tab. Right-click the top
   of a solid block at the center of a dry, open pad with at least 7×7 clear space and room
   above. Placement refuses other modes/dimensions, unsupported or obstructed placements,
   and craft or living entities in the placement space. There is no recipe, drop, spawn or
   Survival acquisition policy yet.
2. Right-click the hull to board. The bar marks the server-owned boarding/exit phases; a
   second player can observe but cannot board until the seat is empty. Camera and mouse look
   are vanilla. **W** thrusts forward, **S** brakes (no reverse), **A/D** turn, and **Space**
   climbs. Releasing thrust/lift eases into a hover.
3. Tap **Left Ctrl** to toggle **Boost**. The small `Boost` HUD label is visible only to the
   pilot while enabled. It does not thrust on its own; **W** still thrusts and **S** still
   brakes. Tap again to return to normal. The current playtest tuning is:

   | Movement (20 TPS) | Normal | Turbo |
   |---|---:|---:|
   | Forward | 8 blocks/s (0.40/tick) | 30 blocks/s (1.50/tick) |
   | Climb | 3.6 blocks/s (0.18/tick) | 12 blocks/s (0.60/tick) |
   | Descend | 2.8 blocks/s (0.14/tick) | 10 blocks/s (0.50/tick) |
   | Yaw | 70°/s | 70°/s |

   The per-tick acceleration, braking and neutral drag factors are unchanged. Turning Turbo off
   immediately restores the normal caps. Landing, exit, disconnect and a new
   seat/world/reload clear Turbo.
4. In flight, hold **Sneak** (**Left Shift** by default) to descend. You will remain seated
   if you hold it through landing. Once stopped and supported, release it and press it again
   to exit. If all nearby standing positions are blocked or become unsafe during exit, the
   ship keeps you seated. Clear a side and try again.
5. Remap Sneak through the normal Minecraft binding and Boost through **Travel Ship → Toggle
   Ship Boost**. Both sample physical keys, even with Toggle Sneak/Sprint configured. The
   default Ctrl sharing is intentional: mounted Ctrl toggles Boost, without changing the
   binding or latch of Sprint on foot. If Boost and Sneak are assigned the same key and
   modifier, Sneak takes precedence. Hold Ctrl or Shift across boarding, a menu, or window
   blur/focus: neither may produce a new toggle/exit on return without a fresh press.
6. With a second client, watch both access phases, fly at full Boost into a wall or under a
   low ceiling, and check observer smoothness. The ship must not cross the swept collision
   shape, move into unloaded space, or break blocks. Rejoin after a low-altitude disconnect
   and verify the seat is empty and another pilot can use the still-persisted ship.

## Persistence and safety

The entity uses normal chunk storage for identity, position and yaw. Unload/restart clears
momentum, active player input, seat authority, pending access, and boost; a restored hull
never auto-boards a reconnecting player. It cannot be normally boarded while parked in the air.
Player saves do not duplicate the hull as a `RootVehicle`. If the pilot disconnects, or a save
occurs while occupied, the server tries a checked nearby exit or an earlier berth (within
24 horizontal and 64 vertical blocks, only if already loaded and still safe). A forced
detach with no valid berth leaves the player where they are with normal Minecraft hazards,
including falling. An airborne empty hull holds its last position.

Collision uses an upright 1.8×2.45 block envelope including the seat and a one-block loaded
collision halo for each swept motion. No ship chunk tickets, world generation, blocks
broken or placed by flight, portal travel, combat or additional seats are implemented.
Control frames contain seat ID, frame sequence, ground token and button flags only, never
transforms. Duplicate/out-of-order/missed frames cannot generate a new button edge; after
a four-tick input grace, stale movement eases to neutral. A focus gap sends a neutral frame
when possible; the client also disarms both edges locally even if that frame never arrives.

## Class 531 presentation interface

`Rac1TravelShip.accessSnapshot()` returns a read-only `AccessSnapshot`: `phase` (`PARKED`,
`BOARDING`, `PILOTING`, `EXITING`), `startTick` (server level game time), `serial` (increasing
per live entity), and `boardingLocal`, `seatLocal`, `exitLocal`. Anchors are in Minecraft
blocks, relative to the ship at yaw zero (+X east, +Y up, +Z bow); use `localToWorld` for
current world coordinates or apply the renderer's yaw once. `seatLocal` is always present;
boarding/exit anchors exist only for their phases. Vanilla passenger tracking is the rider
identity source. Eight server ticks are reserved for each ordinary access phase in this
gameplay revision. The view can seed late tracking from the replicated start/serial; authored
animation, canopy geometry, and Steve presentation must not change the server's mount,
input, collision, or dismount timing.

`seatLocal` is the same feet anchor used by native rider positioning and hence the real
player view. Do not compensate for a hull scale change with a render-only seat offset.
`ShipCockpitProfile.CURRENT` holds the shared source scale, seat, sill, and collision/placement
dimensions. Boarding checks the standing player capsule at every possible yaw; the
server-selected exit is computed outside the actual hull and revalidated, not scaled from
a visual anchor. Use `seatLocal` for the third-person body, `sillLocal` for the access join,
and the vanilla pilot eye height relative to that same body on each peer. Do not force
rotation to the ship yaw; the renderer can face the seated body along +Z while preserving look.

P46's `4686d4a` handoff supplies baseline bounds, not a chosen class 531 cockpit fit.
The profile therefore retains `.20` scale, `(0,.54,0)` seat, `(0,.65,.20)` sill and the
`1.8×2.45` envelope. An integration candidate needs the smallest confirmed source scale,
seat/sill and outer bounds in block units, the minimum floor/roof/side clearance and
tolerance at the classic/slim head/eye, arms and leg stations (including outer layers),
and a closed-canopy/ingress/egress check. Vanilla physical capsule checks do not certify
visual clearance. Do not use the `.225` envelope comparison as a cockpit fit.

### Engine effects contract

- `Rac1TravelShip.engineState()` is the current replicated `ShipEngineState`:
  `UNVERIFIED`, `SUPPORTED`, `AIRBORNE`, or `APPROACH`. Only `APPROACH` permits
  pre-contact exhaust, and only if presentation has the verified source material and
  anchors. Do not use `PILOTING`, `groundSerial()`, the Boost flag or client positions to
  infer takeoff, landing, or approach. `UNVERIFIED` includes parked/unoccupied and
  invalidated sweeps; it does not authorize any engine transition effect.
- `Rac1TravelShip.engineBeat()` is a short-lived client admission sample (`null` if absent).
  Its `ShipEngineBeat` contains `kind` (`LIFTOFF` or `TOUCHDOWN`), entity ID and UUID,
  increasing `serial` for the live entity, and `serverTick` (level game time).
  Sample it every presentation frame and start a beat at most once per live entity
  and serial. The getter keeps the accepted beat for at most two client entity ticks;
  any verified effect's own clock is separate. An unavailable material can skip a beat;
  do not wait and replay it when imports become ready. Do not require the state and packet
  to arrive simultaneously.
- The server sends beats only to **current trackers**. A late/retracking observer gets
  only the current state, never a `LIFTOFF`/`TOUCHDOWN` payload from pairing or saves.
  Receipt rejects a mismatched live entity and older/repeated serials. Detach,
  invalidation, reload, or entity removal clears the applicable visual sample.
- `LIFTOFF` is real upward travel away from verified central block contact;
  `TOUCHDOWN` is real downward travel onto verified central block contact. A wall,
  ceiling, entity, near floor, unknown chunk, or unsafe contact is not proof.
  `APPROACH` requires current unclipped descent followed by predicted safe block
  contact within at most **eight physics steps** using the current controls and
  the same collision envelope. Any unsafe/missing swept chunk, lateral blockage,
  or unresolved support fails closed. The lookahead is read-only: it neither
  moves the ship nor requests chunk tickets. It is a presentation proof window,
  not a new scene duration or a promise if the pilot changes inputs.
- `Rac1TravelShip.boostThrust()` is a current replicated sample of *actual* Turbo travel.
  `forward()` requires W-driven movement towards the bow, and `vertical()` is `CLIMB`,
  `DESCEND`, or `NONE` according to achieved movement in the active vertical direction.
  It distinguishes selected Turbo mode (`isBoosted()`, e.g. HUD) from rear or vertical
  thrust; it is clear on idle, a blocked axis, braking for the forward axis, invalid
  sweeps, and Turbo reset. It neither supplies effect geometry nor relaxes `APPROACH`.

### Ratio-cockpit access contract (proportional retest)

The measured profile is enabled in the isolated retest: `k=1.331490788`, body
seat `(0,1.30,2.12)`, eye Y `2.92` for the normal standing eye offset,
sill `(0,4.15,2.12)`, outer envelope `10.40×4.05`. NTSC uses the
in-memory local cut and verified glass; PAL retains its no-glass
fallback. Cushion and pedals are mod-authored on both. Use the same
artifact/profile on both peers; `-Dracmc.shipLegacyProfile=true` is
the isolated regression escape hatch, not a live toggle.

For Creative placement, stand outside the planned footprint and click the near
edge of a flat pad. The ship center goes five blocks inward along the dominant
horizontal direction from builder to click. Both blocks must support placement;
the full loaded footprint must be free of obstructions, hazards and overlap.
Reserve clear space above the roof for access as well as for the parked hull.

- `Rac1TravelShip.accessPath()` is optional replicated state. During routed
  `BOARDING` or `EXITING`, sample `ShipAccessPath.waypoints()` (ship-local
  feet), `startTick()`, `serial()`, `rollback()` and `durationTicks()`.
  `accessPathProgress()` is the last server-checked elapsed tick. The
  native rider uses `path.sample(progress)` in the ship frame on both
  peers, never the local clock to predict an unchecked step. Draw the
  body at the vanilla interpolated rider feet (no second translation);
  use the itinerary and confirmed progress for limb posing and joins.
  Each of the three normal legs lasts **8 server ticks (24 total)**:
  rise outside, cross above roof, descend to seat; reverse for exit.
  The access snapshot's phase clock and serial match the itinerary.
  Start/end support and model posing are not substitutes for the actual
  per-leg waypoint. On rollback there are one to three reverse-prefix
  legs under a new serial, not a jump or the rest of the old clip.
- Boarding starts at the player's real supported stand; no mounting snap to
  the seat. The native rider and first-person view follow the server path
  with free look. The ship cannot fly during access. Both load and collision
  checks use the entire asserted standing capsule per leg, including other
  living occupants; the outside endpoint must be loaded, dry, supported,
  unoccupied and outside the actual hull. The hull itself must be supported
  and stationary. All are rechecked during movement and at completion.
- On ordinary obstruction the traversed clear prefix is reversed, to safe
  ground on failed boarding or back to the seat on failed exit. If both
  directions become obstructed, forced recovery tries a currently validated
  outside berth; with no safe loaded endpoint it does not invent a teleport
  or force a chunk and ordinary Minecraft hazards still apply. Seat/path
  state is cleared on detach, logout/removal or reload; no route is saved.

Offline gates cover classic/slim model vertices at the supports, actual NTSC
triangles, native placement and access, and Turbo boundary rejection. They
do not establish visual acceptance: observe pad contact, free look,
pilot/observer alignment during access and rollback, and near/far Turbo
movement in the owner-coordinated retest.

### On-foot hull and cockpit-side interaction

The refit has separate collision shapes. The **10.40 × 4.05** yaw-safe box
still handles the ship's terrain sweep, tracker and vanilla pick broad phase.
An on-foot player instead collides with a yaw-rotated approximation bounded
by P46's measured exterior (X `±2.883597`, Z `−4.278368..5.006094`, Y
`0..3.994919`). At non-cardinal yaw it uses 0.20-block strips. This is a
conservative oriented hull, not a per-triangle mesh: fine nose/wing cutouts
are not represented yet, but the empty corners of the terrain square are
not walls. A moving ship also stops for an unrelated player in its swept
pedestrian footprint; terrain checks and chunk guards remain in force.

Stand beside the shared seat station and face/aim across the cockpit side.
The server uses the player's current eyes, look, native reach and a loaded
block ray, not a client-supplied hit point. Both the outside standing point
and overhead legs still require solid support/clearance where applicable.
Exit tries the boarding side first (or the measured captain side, source +X),
then the other side and verified existing
outer stands if needed. Only the ship's own rider and that pilot's scoped
server access-route check ignore its pedestrian shape; neither exemption
applies to another player or another hull.

Ship Presentation `256eea5` identifies the captain-side original pan, but its
trial body at `(0.60,1.23,2.12)` failed the sightline, free-look and access
checks. The authored pads/pedals and shared `seatLocal` are unchanged. Do not
move the authoritative seat into the unverified chair geometry.

In Creative, an empty main-hand punch removes an unoccupied ship without
drops. Aim at the hull, not an empty corner of the flight sweep; the server
checks reach and blocks in the way. A pilot aboard or transferring through
the access route prevents removal. Survival behavior is unchanged.

## Turbo interpolation retest (baseline `4686d4a`)

Minecraft's tracker sends relative position deltas (also occasional full coordinates).
The ship has no native controlling passenger: the pilot and observers both consume
server tracker targets, and the pilot does not send native vehicle transforms. A finite
`MoveVehicle` sent by a mounted test player did not move the hull or produce a correction.
The legacy presentation compared a new server target against the interpolated client
position and snapped above two blocks. At constant 1.50 blocks/tick this produces
alternating 0.75 and 2.25 block client steps. The sim follows native client tick order:
the snap moves the hull before pre-tick history is captured; the rider's earlier
position still anchors the camera at that frame's start. At half tick the resulting
longitudinal hull-to-rider error is 1.125 blocks in the replay. This is a verified
mechanism, not a recording of the owner's rendering.

The fix keeps two-tick smoothing on successive tracker targets; a warp is detected
from **target-to-target** displacement outside the native relative range, applied on
the client tick with hull and rider history rebased together. No movement is predicted,
including during packet stalls, and server motion/collision/Turbo caps are unchanged.
P46 hull, glasses, and pose transforms continue to sample the same ship partial-tick
pose. No rendering change is required to consume the corrected position.

For an owner retest, use the same new build on both peers. Optional JVM argument
`-Dracmc.shipMotionTrace=true` logs at most 1,800 `[ship-motion]` rows per process to
the normal local log. It records server requested/resolved movement and guard stops,
decoded vanilla tracker targets on the clients (the common entry of relative/full
packets), tick samples, and render-stage ship/rider/camera positions. Render samples
are labelled pilot or near (camera within 16 blocks) / far observer. Match rows by
ship UUID and tick; an observer's distance may change during a pass. Compare straight
Turbo, a powered turn, brake, a wall stop and a preloaded-border approach. Check
first/third person and a second client outside the immediate camera range but still
tracking. The trace does not send new gameplay packets or touch saves. JUnit replays
near steady and far-burst delivery; the native fixture logs relative packet types
and authority/brake/boundary assertions. Neither executes a real client renderer.

## Headless checks

```powershell
.\gradlew.bat test --offline --tests '*ShipFlightTest' --tests '*ShipPilotInputTest'
.\gradlew.bat --offline --no-configuration-cache -I src/test/fixtures/ship.init.gradle runGameTestServer --console=plain
```

The fixture writes only under `build/ship-gametest`. It exercises actual server entities,
native clientbound seat packets, collision and entity-region storage using local packet
sinks. It does not replace a live two-client input, HUD, presentation, or latency playtest.

For a real two-process dedicated restart, use a new case name and place the normal
`eula.txt` in `build/ship-gametest/<case>/`. Run the same command twice, with `write` and then
`read`; the disposable server uses loopback and stops automatically after each phase.

```powershell
.\gradlew.bat --offline --no-configuration-cache -I src/test/fixtures/ship.init.gradle -PshipDedicatedCase=restart-check -PshipDedicatedStage=write runServer
.\gradlew.bat --offline --no-configuration-cache -I src/test/fixtures/ship.init.gradle -PshipDedicatedCase=restart-check -PshipDedicatedStage=read runServer
```
