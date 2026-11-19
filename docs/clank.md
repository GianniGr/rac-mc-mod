# Wearable Clank foundations (#26 / #27)

This is an **aerial moveset integration checkpoint**, not completion of either
feature. Native Steve remains the player. The operator-only session setup below
can equip the visible backpack. The independent source-step controller is tested,
and the approved travel/network/fall hooks are installed in the current integration.
The owner subsequently approved a Heli-only landing policy correction. P36's fresh
normal build and 12 real-player headless tests pass; independent T22 and actual
client/owner playtesting remain required. There is no Clank
item, inventory/chest slot, NPC purchase, generic jump boost or playable class87.
No upgrade is automatically granted. The normal mandatory local disc/cache gate
is unchanged.

## Provisional operator playtest setup

With server permission level2: `/clanktest <player> heli`, `thruster`, or `unequip`.
The target must be a live player. This is an explicit per-player test fixture,
not permanent acquisition/progression: Heli sets Heli ownership; Thruster sets
Thruster plus its Heli prerequisite (source Thruster glide also checks Heli).
Unequip clears the fixture. Death/respawn/logout/server stop clear test equipment.
Dimension change invalidates the episode/serial namespace but retains selected
test equipment. Reapply the command after death/reconnect as needed.
Permission is checked by both Brigadier and the server mutation entry point;
legacy client equip/action requests remain denied.

## Local original assets

`asset/clank/Rac1ClankImporter.read(Path)` reuses the full retail-image validator,
then the existing Novalis extractor. It imports wearable **601**, separate
Heli-Pack **607**, and separate Thruster-Pack **608**. Hydro609 is not a feature.
The typed bundle checks classes, skeleton sizes, all original component sequences,
source timing and Clank's antenna attachment. It owns validated encoded copies:
`copyModel(Role)` returns a detached mutable generic model, to copy once per
consumer resource generation. `encodedModel(Role)` also returns a defensive copy.
Missing/wrong variants or clips fail rather than substituting another model.

The local `CLNK` envelope has independent format/importer versions (both 1) and
required model encoding **1**. That encoding is P35's existing public
`Rac1CacheCodec.encodeAnimatedModel` / `decodeAnimatedModel`; there is no second
model serializer or RACM/schema bump. A synthetic payload digest pins compatibility.
The required encoding now references P35's shared `MODEL_ENCODING_VERSION=1`
without changing the payload. Unrelated shop/RACM catalogue
changes do not invalidate this envelope.

The envelope bounds the aggregate to 96 MiB and each model to the shared 64 MiB
limit. It checks source-disc identity, exact ordered roles, section sizes/CRCs,
whole-file CRC (including header) and complete readback. Only the effective usable
`RacMcClient.assetCacheDirectory()` is consumed: no second configuration or media
manager. Files use `clank-<identity-sha256>.clnk`; they never rewrite RACM/shop/
wrench files. Writes force a unique staging file, validate it, then atomically
replace the target. No unsafe non-atomic fallback or staging-file cleanup exists;
failed staging files remain for diagnosis and are never read as completed caches.
CRC is corruption detection, not authentication of a malicious local cache.

Client imports are lazy, only when semantic equipment actually requires a worn
model. Local failures are logged and do not grant abilities or request remote
asset uploads. CPU work is asynchronous; GPU texture leases are client-thread,
generation-scoped and cleared on reload/disconnect. A detached background import
may finish its validated local cache write but cannot publish stale GPU/model state.

## Native player layer and mount

A new client-only AddLayers subscriber attaches a regular-textured render layer
to both wide and slim native player renderers. No competing Pre/Post/root mixin,
global PlayerModel writes, skin replacement, armor removal or cape hiding is added.
The layer consumes the existing final native model frame/root and calls
`body.translateAndRotate` **once**, inside its own push/finally-pop scope.

The fixed **Minecraft host adaptation**, not an original Steve socket, is:

- torso-local back center `(0, 6/16, 2/16)`;
- proper rotation imported `(x,y,z) -> (-z,-y,-x)`, determinant +1;
- local scale `16/15`, cancelling only native player scale `.9375`.

Class scales are already baked into imported geometry/animation. Both components
share this one origin; exactly one pack is drawn. No bounding-box recenter,
per-frame fit, Ratchet dynamic joint5 or additional entity/root/crouch transform
is applied. Native crouch/swim/riding/root effects are inherited, not re-created.
Numeric checks do **not** prove visible attachment or absence of clipping.

Idle and action component playback are wired with semantic server-time anchoring,
local-joint snapshot blending and authored high-action clock segments. These
integration changes still require actual client verification. Selection retains Heli high
`601:12 / 607:5 / hero:21`, long `601:15 / 607:8 / hero:18`, and glide
`607:6`, with regional ticks(10) blend. Thruster long/high have no special
back-table pair; no fake rotor clips are assigned. Nonrendered authored hero-phase
segments and a generic component interval clock preserve separate source keys,
raw frame times, per-key/override rates, crossing/wrap flags and blend duration.
Full PS2 quantization and fidgets are not implemented; visual fidelity is not certified.

Regular textured high-LOD meshes are **not complete original rendering**.
601/608 metal packets, dynamic eyelids/antenna glow, original material callbacks
and procedural Thruster exhaust remain fidelity gaps. The future metadata-only
material API does not itself supply those missing systems.

## Equipment, capability, sessions and semantic protocol

`Rac1ClankEquipment` separates acquired Clank, owned upgrades, worn state and selected
module. Its strict seven-byte versioned save codec contains durable facts only.
It does **not** select permanent acquisition, player-storage or death/drop policy;
actual player save/load/clone hooks are deliberately not wired. The provisional
operator fixture has the explicit session-only clearing behavior above.
Synthetic upgraded test fixtures are not general grant authority.

Per-action source predicates remain separate from complete start admission:

- long/high require matching ready module2/3 and proper ownership;
- module readiness is original slot state2, otherwise sentinel **-1**;
- glide requires Heli ownership and ready-module **!=4**, so source swap sentinel
  can pass that one predicate; it is not a generic ready-only blanket;
- worn, shown and native hero-body conditions remain necessary;
- folded607 visibility before Heli ownership grants no capabilities;
- normal original Thruster selection implies ownership; server data validation
  rejects forged unowned Thruster selection.

`Rac1ClankSession` owns epoch/serial replay checks. All equipment intents and action
legacy client starts **fail closed**; the approved operator command alone mutates
test equipment. Runtime server sessions start `EMPTY`. New common
subscribers provide tracking+self and late-tracking full snapshots; dimension/
respawn invalidate transient/replay epochs, logout/server stop forget sessions.
These are foundations, not live dedicated/two-client certification.

The registered `clank-foundations-1` protocol has fixed semantic payloads:
19-byte C2S epoch/serial/kind/requested-module/worn intent and 68-byte S2C
UUID/epoch/revision/server-time/durable equipment/action/serial/start-time/source
phase snapshot. No paths, ISO identity, textures, models, joints, client velocity
or target positions cross the network. Decoders reject unknown fields/enums,
invalid times and inconsistent equipment. Server-local visuals are not required.
Per-observer views reject stale snapshots, isolate UUIDs and bound retained
entries. The additional bounded movement wire carries held input and authoritative
S2C controller/position checkpoints. Client positions never drive server physics.

## Source-step controller and unaccepted runtime integration

The engineering baseline is original60, **three source steps per MC tick**.
The client ISO never selects gameplay speed. Original50 timing data remains
explicit, including actual float initializer bits, rounded regional durations
and Heli lift start `ticks(34)` rather than universally using PAL tick34.
There is no claim of bit-exact PAL runtime trajectories.

Pure lift curves retain the observed Thruster rows
`(151,0,5), (0,0,27), (22,+2,6), (-999999,-3,6), (0,+2,99)` and Heli `(48,0,1000)`.
The sentinel retains preceding acceleration. Increments are per source step,
scaled by source dt². Lift stops at its action window, not table exhaustion;
gravity remains a separate controller concern. Sequential stepping cannot
rewind/replenish the lift budget. Source step displacement is not MC
deltaMovement: conversion to blocks/MC-tick multiplies by sourceHz/20.
Glide release is strict `elapsed > ticks(30)` with **both** held-input views clear,
not immediate release or an invented universal cooldown.

`Rac1ClankInput` represents remap-independent native Jump/Sneak/digital direction;
diagonals normalize to magnitude1, opposing keys cancel. Its connection-owned
stream validates epoch/monotonic serial, bounds accepted samples and expires lost
held state. `Rac1ClankMotion` consumes a jump edge once, preserves crouch>ticks6 /
jump-within-ticks9 and projected forward speed>.9 (not a sprint flag), and returns
one displacement per source step. Every controlled request must receive collision
feedback before another step. Equipment changes cancel without replenishing the
same controller's airborne jump budget. Glide is separately gated and can still
use held input; it is not prohibited by an arbitrary once-per-flight cooldown.

The controller retains different Heli/Thruster hop ramps, windup, gravity,
horizontal targets, high-lift stages and jump-to-glide holdoffs. Thruster long
slows toward3.5u/s from source tick55 and its gravity approaches25 after tick54.
Heli long preserves its windup's forward push and no ordinary air steering.
Native-voxel geometry tests cover floor/ceiling/wall/slab bounds, takeoff,
lift-window goldens, landing, source50/60, ownership/readiness and cancellation.
These tests are **not a real ServerLevel/player or two-client acceptance run**.
Original eased turning, momentum/ice/slope, side/back flip, conditioned wall/
ledge and airborne combo branches still need exact runtime coverage; do not claim
bit-exact PS2 trajectories or complete original game feel from this checkpoint.

`Rac1ClankFallEpisode` tracks pre-admission distance, controlled descent, one landing
and conservative cancellation. The approved scoped `LivingFallEvent` adjustment
and native `ServerPlayer.doCheckFallDamage` processing are installed.

Owner-approved Heli policy: an admitted Heli glide held through a normal landing
negates **all fall damage from that fall**, including distance before late activation.
Releasing glide revokes protection immediately, even while the original minimum
glide motion timer remains active. Interrupted/released glide conservatively restores
pre-activation plus actual glide descent without charging it twice.
A protected landing requires fresh held input consumed by that authoritative step, the same player,
world, epoch and admitted action, and a collision-resolved native landing.
Main-thread payload receipt may precede the next world-time increment: a new serial
received in that immediately preceding tick can belong to its first consumption.
Each serial proves at most one authoritative tick; receipt alone or a later held
sample cannot retroactively prove an already-running step. The original four-tick
motion-input grace is unchanged and does not grant damage protection. Accepted
release revokes protection immediately, including after consumption and before
landing; a newer held sample cannot clear that glide's release latch.
Missing/stale/replayed input does not prove a held landing. Equipping/toggling or
launching high/long does not itself grant this protection. Thruster/high/long retain
their existing policy; unrelated, void and hazard damage are not intercepted.
No blanket event cancellation, global movement/flight flag or native render-root
hook change is used.

## Pending owner choices and verification

Permanent acquisition/death/drop policy remains future work. For this iteration
the owner approved provisional operator test setup, remapped native Jump/Sneak
and no aerial activation while swimming/riding/elytra/flying/spectating. The
approved integration is installed; verification status:

1. P36 verified the Heli policy using damage-eligible native players, native
   fall-event distance/count, real pre-activation descent and positive damage controls.
2. Fresh normal build passed; independent exact-candidate T22 checks remain.
3. Verify native input/prediction/action animations with dedicated/two-client runtime.

Hydro609 is deferred until the aerial moveset is playable, independently checked
and owner-playtested. Current aerial water gates are not a final “no Clank
swimming” product policy. No Hydro research, bundle expansion or water boost is
part of this checkpoint. The observed lower-cape overlap is owner-accepted;
native cape/mesh/mount are not hidden or rescaled.

Native collision substeps, high/long/glide admission, prediction/reconciliation,
fall bookkeeping and action blends are installed, not independently accepted.
The earlier owner-approved Heli correction passed its normal build and 10 headless
tests, but T22's actual-network held landing on `3b85b86` failed (distance10,
health20→13). That result remains a release blocker pending independent actual
network retest of the consumed-input correction; headless results do not waive it.
The consumed-input correction passes a fresh full build (820 passed, 51 skipped,
zero failures) and all 12 required native headless world tests. The native queued
regression first reproduced the failure, then passed with prior-world-tick receipt
followed by next-tick authoritative consumption/landing. Release-after-consumption
and stale/replay controls retain normal damage. Fixture players
age through native `tick`/`doTick`, without disabling spawn protection. The
real late-fall case accumulated 6.5938 blocks before glide activation: held Heli
landed at 20 health; released Heli and the native control both landed at 15.
Interrupted/released tests also check distance/count and no duplicate charging.
Earlier pre-policy failed outputs remain historical evidence, not current oracles.
No actual owner client was controlled. Required T22 exact-tree real render,
dedicated-server/two-client equipment/time/lifecycle checks and eventual movement
checks remain pending. Armor/cape/slim/crouch/swim/riding clipping needs actual
visual acceptance; pure matrices cannot certify it. Later P35 integration reopens
affected checks. Neither #26 nor #27 is done from this foundation alone.

Format/behavior facts: independently inspected supported retail discs and R13's
2026-11-18 accepted research/addendum; public source facts pinned to
[ReRAC ae1e343 (ISC)](https://github.com/re-rac/rerac/tree/ae1e3432a0d5e21bd08d3872d3b04afef98be666).
Implementation and tests are independent MIT project code, not copied upstream
implementation. No GPL dependency/code or proprietary game assets are included.
