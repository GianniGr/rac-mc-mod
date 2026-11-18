# Wearable Clank foundations (#26 / #27)

This is an **inactive foundation**, not completion of either feature. Native Steve
remains the player. There is no Clank item, inventory/chest slot, grant command,
NPC purchase, keyboard binding, jump boost, movement takeover or playable class 87.
No upgrade is automatically granted. The normal mandatory local disc/cache gate
is unchanged.

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
On integration with P35's forthcoming `MODEL_ENCODING_VERSION=1`, reference that
shared constant rather than changing the payload. Unrelated shop/RACM catalogue
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

Only idle component playback is wired to the layer in this stage, with semantic
server-time anchoring for late observers. Action rendering is held alongside
action admission. Source-backed selection metadata retains Heli high
`601:12 / 607:5 / hero:21`, long `601:15 / 607:8 / hero:18`, and glide
`607:6`, with regional ticks(10) blend. Thruster long/high have no special
back-table pair; no fake rotor clips are assigned. Nonrendered authored hero-phase
segments and a generic component interval clock preserve separate source keys,
raw frame times, per-key/override rates, crossing/wrap flags and blend duration.
Full PS2 snapshot blend/quantization, fidgets and hero frame-retiming integration
are not certified or enabled.

Regular textured high-LOD meshes are **not complete original rendering**.
601/608 metal packets, dynamic eyelids/antenna glow, original material callbacks
and procedural Thruster exhaust remain fidelity gaps. The future metadata-only
material API does not itself supply those missing systems.

## Equipment, capability, sessions and semantic protocol

`Rac1ClankEquipment` separates acquired Clank, owned upgrades, worn state and selected
module. Its strict seven-byte versioned save codec contains durable facts only.
It does **not** select acquisition, player-storage, death/drop/clone or respawn
policy; actual player save/load/clone hooks are deliberately not wired.
Synthetic upgraded test fixtures are not grant authority.

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
starts **always fail closed** while owner policies are undefined, even with
synthetic upgraded saved data. Runtime server sessions start `EMPTY`. New common
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
entries. No vanilla movement-network or anti-flight changes are made.

## Pure source-time math, not activated motion

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

## Pending owner choices and verification

Three groups remain held:

1. Acquisition/equip UX and death/drop/persistence policy.
2. Configurable keyboard bindings and digital direction adaptation.
3. MC-only start exclusions/coexistence and episode-scoped fall-damage behavior.

Native collision substeps, movement admission, original high/long/glide full
controllers, prediction/reconciliation, fall-distance/damage, controller
coexistence, action blends and original render fidelity are not enabled.
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
