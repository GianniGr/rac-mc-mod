# Wearable Clank foundations (#26 / #27)

This is an **aerial moveset integration checkpoint**, not completion of either
feature. Native Steve remains the player. The Creative-item and operator session setup below
can equip the visible backpack. The independent source-step controller is tested,
and the approved travel/network/fall hooks are installed in the current integration.
The frozen aerial/Creative review target is **`e979560a11c107908cc35d8054b367560d49cf0d`**.
The later, separately authorized Hydro follow-on below is not part of that target.
The owner subsequently approved a Heli-only landing policy correction. P36's fresh
normal build and 21 real-player headless tests pass; independent T22 and actual
client/owner playtesting remain required. There is no survival acquisition,
dedicated inventory/chest slot, NPC purchase, generic jump boost or playable class87.
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

## Creative-only companion entries

The existing RaC Creative tab contains distinct **Clank Heli-Pack**
(`racmc:clank_heli_pack`) and **Clank Thruster-Pack**
(`racmc:clank_thruster_pack`) entries. Right-click either hand to equip/switch the
matching session companion. No item is consumed. Reusing the selected pack is
idempotent and does not restart movement; changing packs uses existing epoch/action
invalidation without replenishing an airborne action budget.

The server checks actual Creative mode and the actual held registered item.
Survival/Adventure/Spectator use is denied, even if the stack was retained/given
or contains forged custom data. No item payload grants an arbitrary module or
operator permission. Thruster fixture retains the Heli ownership prerequisite.
The item follows existing session lifetime: death/logout/server stop clear it;
dimension invalidates the episode but retains equipment. A later mode change does
not invent a new lifetime rule; the already-equipped temporary session remains.
`/clanktest` is still the operator fallback, including Survival playtest setup.

These are checkpoint setup items, not survival entitlement, recipes, progression,
armor slots, persistence or a new death/drop policy. Inventory stacks retain native
Minecraft behavior; worn companion facts still are not loaded from item NBT/save.

Item visuals reuse the existing client-local Clank sidecar/resource/texture leases
and shared mesh submission: original idle601 plus the matching607/608, fitted as
one item-only composite. No proprietary artwork is bundled, no shared renderer or
wearable mount is replaced, and no player action clock drives inventory poses.
Loading is lazy; unavailable/loading local assets produce no substitute art.
Actual Creative inventory/hand orientation and readable pack silhouettes still
require T22 client verification. Existing original-material fidelity gaps remain.
Fresh Creative verification passed a normal full build (835 passed, 51 skipped,
zero failures) and all 21 native world tests, including the prior 19 movement cases.

## Local original assets

`asset/clank/Rac1ClankImporter.read(Path)` reuses the full retail-image validator,
then the existing Novalis extractor. It imports wearable **601**, separate
Heli-Pack **607**, and separate Thruster-Pack **608**. The optional Hydro609
follow-on uses a separate in-memory assembly; it does not change this bundle.
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
operator/Creative fixture has the explicit session-only clearing behavior above.
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
legacy client starts **fail closed**; only the approved operator command and
Creative held-item server use path mutate test equipment. Runtime server sessions start `EMPTY`. New common
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

Current owner-approved Heli policy negates **all fall damage from that fall** on
the first normal touchdown while a genuine valid server-admitted Heli glide is
still active. This deliberately no longer requires fresh held input at touchdown.
A qualifying accepted Jump release also starts a 40-server-tick window, independent
of glide motion/animation. If release occurs at tick R, touchdown at R through
R+39 is covered; R+40 is not, unless a genuine Heli glide is still active.
Grace follows the same airborne fall through native movement after glide ends.
It consumes on touchdown and clears on death, world change, teleport, equipment/
epoch invalidation and incompatible lifecycle. The scoped connection teleport
hook invalidates only an existing Clank episode/grace, not bystanders.

Repeated unheld samples, stale/replayed input, unadmitted actions and an unrelated
fall cannot start/refresh grace. A deliberate subsequent genuine glide/release
can start a new window; a same-action re-press must first be freshly consumed
before its later release can renew that window. No timer is extended by old input.
Original motion input grace, admission and strict source glide-release timing are
unchanged. If starvation ends the glide before touchdown and no qualifying server
release window exists, damage is normal. The separate stall caveat remains open.
After expiry, interrupted descent retains native pre-activation plus actual
controlled distance conservatively, without duplicate charging.
Equipping/toggling or
launching high/long does not itself grant this protection. Thruster/high/long retain
their existing policy; unrelated, void and hazard damage are not intercepted.
No blanket event cancellation, global movement/flight flag or native render-root
hook change is used.

## Landing momentum handoff

Ground resolution retains collision-resolved horizontal velocity and clears
controlled downward velocity, including exact-contact cases. Original Heli-long
authored skid scaling remains; no replacement jump impulse or wall momentum is
created. The next tick returns to ordinary native ground travel/friction.

While the controller is idle, authoritative correction checkpoints carry native
`deltaMovement`, not the idle displacement probe. Existing client restore/replay
therefore receives the server's retained velocity instead of a false zero during
the landing correction interval. Actual network/client feel still requires T22;
headless packet capture and checkpoint roundtrip are not an owner playtest.
The combined candidate passes a fresh full build (827 passed, 51 skipped, zero
failures) and all 19 native world tests. Both packs' high/long/glide handoffs are
checked unobstructed and against walls, including next-tick vanilla friction and
the actual emitted authoritative checkpoint. Protection checks remain green.

## Pending owner choices and verification

Permanent acquisition/death/drop policy remains future work. For this iteration
the owner approved provisional operator test setup, remapped native Jump/Sneak
and no aerial activation while swimming/riding/elytra/flying/spectating. The
approved integration is installed; verification status:

1. P36 verified the Heli policy using damage-eligible native players, native
   fall-event distance/count, real pre-activation descent and positive damage controls.
2. Fresh normal build passed; independent exact-candidate T22 checks remain.
3. Verify native input/prediction/action animations with dedicated/two-client runtime.

Hydro609 was excluded from the frozen aerial checkpoint. The owner subsequently
authorized the separate temporary test implementation described below while T22
verifies that exact aerial checkpoint in another worktree.
The observed lower-cape overlap is owner-accepted;
native cape/mesh/mount are not hidden or rescaled.

Native collision substeps, high/long/glide admission, prediction/reconciliation,
fall bookkeeping and action blends are installed, not independently accepted.
Earlier T22 live and owner reports are retained historical evidence, not waived
by headless checks. The owner subsequently changed active/released landing policy
to the rule above. Independent integrated-singleplayer retest is required.
This protection scope passes a fresh full build (824 passed, 51 skipped,
zero failures) and 17 native headless world tests. Native boundary landings prove
R+39 distance0/health20 and R+40 distance13.224/health9. Tests cover original
motion ending into native travel, one-shot consumption, actual same-world teleport,
death/world transfer/equipment invalidation, hazard/void controls, and active
missing-fresh-input landing versus ended-glide/no-release native damage.
Fixture players age through native `tick`/`doTick`, without disabling spawn protection.
Existing source timing, collision, packet authority, native falls and Thruster
controls remain tested; older released-Heli damage oracles within R+40 are superseded.
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

### Main-based rate-only checkpoint (2026-11-23, `09b34db`)

The reviewed partial AV patch is ported alone onto merged main
`d6dd262aec76d7fe9a197c331318a6907af8fd2c`; its old gameplay/shop ancestors are
not imported. This does not supersede the frozen movement review target above.

Negative center notes now have a verified fixed-note60/fine0 rate path, independently
implemented from the integer lookup facts in pinned ReRAC
[`vag.rs`](https://github.com/re-rac/rerac/blob/ae1e3432a0d5e21bd08d3872d3b04afef98be666/crates/rc-formats/src/vag.rs)
(ISC, Copyright (c) 2026 ReRAC contributors), cross-checked against OpenGOAL
[`util.cpp`](https://github.com/open-goal/jak-project/blob/4172dfe7ab66cefb22218cc0da92b23c0342fa98/game/sound/989snd/util.cpp).
The negative sign selects SPU2 48 kHz and the absolute center note. Quantized
pitch words, not a smooth guessed exponential, determine the PCM rates:

| Verified metadata | SPU pitch word | PCM rate |
| --- | ---: | ---: |
| Heli note−80/fine124 | 1364 | 15984.375 Hz |
| Thruster note−74/fine65 | 1879 | 22019.53125 Hz |
| Hydro tone note−74/fine66, **without script bend** | 1880 | 22031.25 Hz |

The existing positive-note approximation remains bit-identical, including Blaster.
No catalog event, cache schema, PCM streamer or Blaster binding changes here.
These are rate tests, **not audible acceptance or an actor-loop binding**.
Clank's runtime backend still returns no voice; its warning now distinguishes
missing import/binding from missing signed-rate support.

Hydro cue37 remains refused as a multi-grain program. Its retained metadata and
the pinned public grain interpreter establish PB−126, register0=−63, a looping
tone, increment/add-PB2/test/loop control with a one-tick test delay, then opcode43
and a nonlooping tone. **The name `ON_STOP_MARKER` does not prove a release-tail
dispatch.** Both pinned ReRAC `Snd989::stop_sound` and OpenGOAL
`BlockSoundHandler::Stop` key voices off; neither executes the post-marker tone.
The exact RAC1 release behavior remains unverified. Do not select either Hydro
tone as a fallback or infer that its base rate implements the program.

Finishing Hydro needs a bounded timed-voice/bend/release design beyond the current
single-clip API and evidence resolving that stop path. The original Thruster cone
still needs its overlay mesh/ST/color/quad/UV tables; no new retail reads were
performed or authorized by this continuation. PAL aerial equivalence and rendered
alignment remain unaccepted.

## Later Hydro test follow-on (not the frozen aerial review target)

**Already-worn Clank automatically supplies Hydro in this temporary test build**,
whether equipped by either existing Creative fixture or the operator command.
No separate Hydro command is required. Permission2 `/clanktest <player> hydro
off` optionally suppresses Hydro for the current worn-Clank session; `hydro on`
restores the automatic default. Both require worn Clank; repeated on is idempotent.
This does not grant Clank/Heli/Thruster, consume an item, or add ownership/save data.
There is **no Hydro Creative entry, item, recipe, purchase or persistence**.
Existing Heli/Thruster Creative items are temporary test fixtures too, not production
unlock semantics. The existing equipment codec/module enum remains unchanged.

The automatic default and optional off suppression survive a dry-pack switch,
dimension change and teleport; those events invalidate the wet action/input epoch.
Unequip/death/respawn/logout/server stop clear the session override. A newly worn
Clank starts automatically available again. Off does not immediately re-enable
on the next tick, and it cannot pre-disable a future unworn session.
The authoritative dry selection is never replaced by Hydro, so
exit/off returns the exact prior Heli/Thruster selection; explicitly switching the
dry fixture while wet changes that remembered selection rather than granting access.

### Minecraft water predicate (explicit host adaptation)

Native `Entity.updateSwimming()` requires `isSprinting()` to enter **and** continue;
`Player.updateSwimming()` also excludes flight. Thus `isSwimming()` alone would
violate automatic no-Sprint equip. Hydro samples native fluid volumes, eye immersion
and native collision support independently, without forcing Sprint or a swim pose:

- No body water-volume overlap: dry. Rain, water contact flags and lava do not count.
- Eye point below actual native water surface: submerged, even on a deep pool floor.
- Otherwise: surface only if unsupported (floor distance >.6 blocks) and body
  immersion >=.8 blocks; native swimming flag **or native low swimming body pose**
  may use immersion >.2. The low pose can survive a Sprint release on surfacing:
  qualifying submerged→surface keeps Hydro without another swap. Neither pose
  alone nor grounded/shallow wading qualifies.
- Other water overlap: wading, with no Hydro override or power.

These are Minecraft thresholds, **not measured retail distances or a universal
fluid API**. They let a nonsprinting standing body enter surface swim without
requiring Ctrl, while preserving native shallow-wading behavior. Native surface
travel, Jump/Sneak outside admitted propulsion and Steve pose remain vanilla.

### Controls, movement owner and coexistence

Configured **Sprint key held** requests thrust only while eye-submerged, never
from native sprint/toggle state or a hardcoded Ctrl key. No direction key is needed.
Hydro reads the physical state of the **current native binding**, not
`ToggleKeyMapping.isDown()` (which remains latched after release in Toggle Sprint).
The same physical-held path handles Hydro Jump/Sneak, including Toggle Sneak.
Key symbols, mouse buttons and native scan-code bindings resolve without a fixed
Ctrl dependency; native conflict/modifier context still applies. UI or lost window
focus supplies released input. No global mapping/options/click state is modified;
vanilla behavior outside Hydro remains intact. In eligible water, a fresh press
of configured Sprint (Left Ctrl by default) toggles Hydro boost; release and
repeat do not toggle it. The latch starts disarmed and requires a release
sample after entering eligibility before a press can arm it. Leaving eligible
water, unequipping, or losing the session resets it.
Configured Jump pitches up; Sneak pitches down; Jump wins both. Direction keys
steer yaw-relative heading while forward is held. During **armed forward
thrust only**, native player look pitch is a finite-clamped ±78° pitch target
through the existing spring/rate limit. Jump/Sneak override that camera target;
Jump wins both. Existing low-air/initial dive-in priority remains. Looking around
does not change normal native surface/dry travel or steer released coast/drift.
This is an owner-approved Minecraft adaptation, not original camera-pitch behavior.
The server reads native player rotation, not a new Hydro packet field. Prediction
uses the same bound and retains local look targets for bounded checkpoint replay;
resolved pitch/rate already travel in the existing authoritative Hydro snapshot.

Independent source60 controller: three bounded native-collision substeps per MC
tick, 1 source unit mapped to 1 block, target7 units/s, accel13 units/s²,
yaw spring .007/.08 capped300°/s; pitch .015/.2, ±78°, capped70°/s. W release
stops powered effects immediately and brakes collision-resolved Hydro horizontal
carry at 48 units/s²: three normal ticks and less than half a block at full
speed in still water. The toggle stays armed for the next W. Jump/Sneak can
continue vertical control during this handoff without new horizontal boost.
Digital keys use full pressure; exact original nonpressure/PAL trajectories are
not certified. Native ordinary swim pulses/surface movement are not replaced.

Aerial water cancellation runs first. Hydro owns native travel only after actual
server-authoritative collision movement; the existing connection adapter ignores
client position during those ticks. Input is epoch/serial/rate bounded; no
missing sample supplies thrust, and four ticks of receipt grace cover isolated
packet gaps before the toggle resets. Native oxygen, drowning, damage and collision
remain active. Hydro grants **no Heli fall grace, flight flags or hazard immunity**.

Propulsion yields to vehicles, spectators, flight/elytra, sleeping/spin/climbing,
levitation, hurt/teleport, flowing water, bubble columns, Dolphin's Grace and
nonzero native water-movement-efficiency (including Depth Strider). Compatible
surface/submerged players may still wear the override without thrust. Conduit/
Water Breathing keep their native oxygen behavior; Hydro never adds breathing.
External velocity changes cancel the powered episode rather than overwriting
currents/knockback. This conservative coexistence policy is not original-current
parity, and enhanced-swim composition remains a future product choice.

### Semantic visuals and limits

A separate bounded Hydro protocol carries access/effective wet category, prior dry
selection, transient boost latch, action/collision checkpoint and source-timed put-away/gap/unfold/fold
phases to self and late observers. No proprietary data or private path crosses it.
The existing torso layer/mount/native skin/armor/cape stays intact. Optional local
609 uses the existing validated media path/extractor and shared texture/mesh APIs;
no new extraction was executed to develop this change. No substitute art is bundled.
Nominal lifecycle is2 old-pack updates,1 gap,14 unfold; exit6 fold,1 restore gap.
Surface movement uses609:3/601:16, distinct from underwater thrust/idle.

Actual GPU appearance, authored transition blending/material fidelity, remapped
physical-key input and two-real-client synchronization still require separately
cleared T22/owner verification. Headless packet sinks are not real network clients.
No live client is launched while the owner's T22 runtime is active.

P36's fresh Hydro full build passed: **847 passed,51 skipped,0 failures/errors**
from179 JUnit suites/898 tests. All **25/25 native tests** passed (four Hydro
cases plus all21 frozen aerial/Creative regressions). The normal JAR excludes
opt-in Clank world fixtures and proprietary/cache payloads. This is implementation
evidence, not independent T22 or owner acceptance.

Later isolated corrections (automatic default, qualifying-surface continuity,
bounded look steering and physical-held toggle-safe input) pass a fresh full
build: **855 passed,51 skipped,0 failures/errors** from181 suites/906 tests;
**28/28 native tests** pass, retaining all21 aerial/Creative regressions.
Native hold/toggle mappings are regression-tested against detached device states;
actual remapped physical release, focus/UI and two-client checks remain T22 work.

## Separate Clank audiovisual follow-on (partial; gameplay unchanged)

Client-only Clank controllers reuse the bounded original-effect pool, current
local particle definitions, texture generation and world renderer. They never
step movement, inspect physical controls, grant access or alter vanilla bubbles.
Jets follow the **same blended animated attachment chains and native back mount**
as the rendered pack: Hydro609 lists0/1, Thruster608 pairs2/3 and0/1. Missing
attachments/assets fail closed, never become fixed mouth/hand/torso emitters.

Hydro's accepted submerged THRUST state emits four type34 outlet bubbles per
timer fire, alternating0/1 twice. Cadence uses retained timer and latest
authoritative speed: trunc((1-speed/7)*5), bounded at0; full speed fires each
source60 update. Ordinary surface, idle and drift do not emit outlet bubbles.
New bubbles start at each terminal outlet joint's local origin in the current
render frame. First person samples the same native body mount at the frame's
interpolation time without drawing a player or pack. See the
[nozzle alignment retest](clank-hydro-nozzle-alignment.md).
Already emitted bubbles buoy, wobble, grow/fade and pop against the native water
surface if the bounded32-block fluid query found one. An unknown surface
disables only surface pop, not emission. No breath/hand/wade/splash
or vanilla-particle replacement is included.

NTSC-backed Thruster windows are ascending/non-descending HIGH, LONG source
phase<44, and GLIDE. Two nozzle controllers wait, grow2 updates, burn, then
shrink8; repress during shrink does not instantly restart burn. Burn emits
type21 orange→white splitting sparks and blended type23 smoke (first puff,
then4 per nozzle/update;2 in glide). Source-confirmed crossed additive FX12
quads and FX11 glow are drawn. There is no point light or plume-owned sound.
**The original196-vertex scrolling cone is unavailable**: its overlay tables
were not covered by the approved audio-only read. No replacement cone is invented.
PAL aerial plumes remain fail-closed pending equivalent callback/FX evidence;
sampled static mesh identity is not procedural-region proof. Hover/ledge/stomp
actions are not implemented, so no corresponding effect or ledge one-shot is added.

Presentation uses the project's source60 host clock, independent of render FPS
and viewer region. Positions/speeds are held from latest20Hz snapshots; RNG,
particle update order, camera-facing wobble/glow and batching are documented MC
adaptations, not bit-exact retail simulation. A separate2048-particle pool and
nearest16 wearers within48 blocks bound load without stealing Blaster capacity.
Rendered layer anchors expire after2 ticks; culled/missing/first-person-only
pack poses suppress nozzle effects rather than guessing a pose. Actor-loop
requests do **not** depend on render/pack presence. World/resource/logout/stall
invalidation clears cosmetic state without affecting gameplay.

### Verified audio identity and optional Heli/Thruster wiring

Owner-approved read-only verification used the existing supported NTSC-U1.00 and
PAL2.00 media paths, **level1 class0 local2/18/19 only**. No playback or original
bank/PCM files were written. Both sampled revisions resolve:

| Source request | Local→bank cue | Program / loop metadata | Current binding |
|---|---|---|---|
| Heli GLIDE, hero slot3 | 2→24 | one tone; signed note−80/fine124; loop[28,20440), repeat | optional actor loop at15984.375 Hz; audible acceptance pending |
| Thruster GLIDE/hover, hero slot4 | 18→36 | one tone; signed note−74/fine65; loop[168,13468), repeat | optional GLIDE actor loop at22019.53125 Hz; no distinct hover action exists |
| Hydro underwater THRUST, hero slot5 | 19→37 | ten scripted grains, two tone references | unavailable: cue program unsupported |

Matching sampled mappings/metadata are not audible output, mixer/envelope/rate
or whole-bank/regional equivalence. Bank hashes differ. Hydro's two referenced
waveforms have loop[28,7420)/repeat and no-loop8988 samples respectively; **neither
may be selected as a first-tone fallback for the script**.

The owner-approved audio follow-on wires **only accepted Heli/Thruster GLIDE**.
Current gameplay has no distinct hover action; none is invented. No high/long
jump, landing, idle, raw-key-release or Heli landing-grace audio is bound.
Source handler ownership slots3/4 are **not** definition entries2/18.

`Rac1ClankAudioCatalog` imports only those two cues, checking each against retained
waveform identity, note/fine, tone flags and loop extents. Each optional failure
is isolated. Its separate, fenced generation is **volatile memory only**: no
new sound sidecar, shared cache version, shared event enum/catalog or Blaster
import/binding changes. Resource reload retains ready immutable PCM but retires
voices and fences stale work. Missing/corrupt optional input never blocks play.

`Rac1ClankAudioClient` is independent of nozzle captures, sprites, rendering and
VFX import availability. At most **two nearest valid supported actors within32
blocks** own voices. A sustained voice uses the existing `Rac1PcmStream`, with
the intro once and the original marked suffix repeated. Positions follow the
actor; accepted action serial/equipment epoch/cue changes retire the old voice.
Unrelated dry Hydro epochs do not restart it. Action end, stale/untracked state,
death/spectator/removal, range/budget displacement, disconnect/world/media change
and resource reload stop **only Clank-owned handles**, never shared/Blaster
voices. Asynchronous channel startup gets5 ticks grace; dropped voices close
and wait20 ticks before retry. This is a two-handle budget, not a reservation or
an audible guarantee under native channel/device pressure.

No fresh ISO or retained proprietary audio payload was read during this coding/
synthetic-test continuation. Future runtime importing needs normal configured-
image validation (including hashing), level1 bank/index and compressed core data
for hero class0 entries2/18, then volatile decode of cues24/36. It reads no GS RAM,
textures or cone overlay. The old metadata-only permission is **not** permission
for a media-backed decode/playback retest; obtain explicit clearance for that
scope and the live slot first. No original audio is distributed in the mod.

**Implemented wiring is not verified audible output.** Host spatialization/mixer,
the existing ADPCM decoder, immediate owned-voice shutdown and lack of SPU ADSR/
reverb emulation remain project adaptations. Hydro's scripted API/release path
and the original cone remain separate unresolved work; Hydro stays silent.

CPU/native regression evidence cannot certify GPU nozzle alignment, first-person
coverage, original particle/material fidelity, real audio output or two-client
presentation. These remain a separate T22/owner AV handoff after runtime clearance;
the exact `f0dad42` movement verification remains independent.

Historical legacy-tree partial-AV full build: **866 passed,51 skipped,0 failures/errors**,
184 suites/917 tests. Native gameplay regression: **28/28 passed**. This does
not certify actual GPU effects or audible Clank loops.

### Post-owner glide check: bounded NTSC plume correction

The owner reports audible glide shutdown on landing for the `23f64ad` line.
That is a narrow owner observation, not certification of all two-client/audio
lifecycle cases. The glide importer, loops, budget and ownership are unchanged.

The pinned ISC ReRAC `thruster_render.rs` callback establishes that quad jitter
is subtracted **after** table scaling, and that glow size is fixed at table×0.1,
not multiplied by flame grow/shrink. The renderer now observes those facts.
Glow facing uses the nozzle point-to-viewer/gravity basis rather than camera
roll. Degenerate bases collapse as in the reference, without a guessed socket
or alternate orientation. Existing sprite materials, windows, nozzle poses,
source60 host timing and the NTSC-only aerial gate remain unchanged. No new
jump/ledge/stomp/hover trigger or PAL equivalence is introduced.

The original cone and Hydro scripted audio remain fail-closed pending the
precise media/release evidence scopes described in the continuation handoff.

### R15-backed native body trails and conservative touchdown motes

Pinned NTSC facts in `afterimage.rs`, `hero/packs.rs`, `hero/pose.rs` and
`particles/type28.rs` now drive additional client-only presentation:

- Thruster high jump: current native player skin/body at older roots, source
  alphas40/20 and lags3/5. Long jump: alphas40/20/10 and lags2/4/6. The ring holds8
  placements; lag counts the newest as1. Alpha drops2 per source update after
  phase12. Action/serial/epoch change, teleport/stall, stale/untracked or invalid
  wearer clears history. These are body ghosts, not particles or screen blur.
  Heli and glide get no ghosts. No new sound is dispatched.
- Ghosts reuse the existing native player-model hook and **current** pose/skin,
  not Ratchet clips, old poses, armor/cape/equipment or recursive player layers.
  Roots are sampled/interpolated on a bounded source60 host clock; this is a
  Steve/native-render adaptation, not original Ratchet pose/placement playback.
- Imported `def[28][0]` motes use actual rendered native leg-end transforms and
  the existing bounded `EffectRuntime` sprite backend. Their source pulse,
  fade, drift, spin, size and random-colour rules are independent of nozzle
  smoke. Missing sprite/feet means silence visually—no vanilla dust fallback.
- A contiguous, fresh accepted-action descent followed by native ground contact,
  same equipment epoch/serial/module, and passed native takeoff guard is needed.
  Spawn/equip/raw ground/jump input, ordinary walking, teleport, wet/dead/
  spectator/hidden/riding/flight/slippery controls do not create a landing burst.
  Supported high jumps and Thruster long jump get an **initial one-update**
  two-per-foot pulse. Stationary glide gets the evidenced non-oriented LAND
  five-update pulse. New airborne/wet/invalid state cancels pending emission.

There is no exposed original post-land group/skid latch in the current native
snapshot. Accordingly **Heli-long skid, continuing post-land refresh, running
glide LAND_ROWS, and other slippery/wading landing branches remain omitted**,
not inferred from a generic onGround packet. Original dry-body animation sound
trigger words remain unavailable, so no jump/effort/landing sound is invented.

Body ghosts and touchdown motes are **NTSC-only** until PAL counterparts are
separately evidenced. Source60 from20Hz semantics, bounded render-root
interpolation, native contact/feet/body/skin, Java RNG and conservative omissions
are explicit host adaptations. They require isolated GPU/owner retest; CPU and
native gameplay regressions do not certify appearance.
