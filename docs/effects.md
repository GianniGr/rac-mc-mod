# Original effects: connected preview candidate

The optional effects provider now has live client rendering and server-authorized
semantic hooks. It is **not full particle-system completion or original-frame
fidelity approval**. Independent T verification and owner in-world playtest remain
required. Existing arrow damage/firing/physics and crate transactions are unchanged.

## What is connected

- Existing accepted Blaster firing emits `BLASTER_SHOT` after shot bookkeeping.
  Accepted block impact emits position, incoming tangent and block-face normal.
  Entity impact emits only when vanilla actually ends the arrow; its cosmetic
  normal is the opposite tangent (MC does not provide an original collision face).
  Explicit expiry emits `BLASTER_END`. Impact/expiry share a terminal guard.
- Existing crate reward/removal service emits typed wooden/ammo or health break
  only after successful removal. Duplicate destruction cannot replay it.
  Accepted explosive detonation emits `CRATE_TNT_BLAST` after its existing
  event/identity guards, damage, removal, chaining and knockback dispatch.
  Canceled detonation emits nothing.
- `Rac1EffectNetwork` registers an optional **clientbound-only** payload, separate
  from sound, bolt balance, mesh cache or any shared manifest. It has no serverbound
  request, client class link, media importer or sound-engine dependency.
- Client-local verified ISO -> bounded CPU decode/sidecar -> render-thread upload.
  Missing/failed assets do not fall back to vanilla arrow geometry; effects never become another
  mandatory media gate. F3+T reload invalidates the old generation/resources.
  Stale asynchronous results cannot attach after media/reload generation changes.
- Tracked authoritative Blaster arrow UUIDs also admit a shot (for late tracking).
  Spawn+packet deduplicate. Terminal-before-spawn cannot resurrect a trail.
  Untracking, chunk unload, dimension change and disconnect stop attached effects
  without inventing impacts. No client collision causes gameplay or crate mutation.
- Type26 glow, seventeen type72 trail sprites, type27 accepted-impact sparks and
  type23 end smoke follow authoritative projectile positions. Available imported
  graphics are admitted on the first eligible entity frame, before the fixed effect
  clock advances, and drawn through the existing AFTER_PARTICLES path. No normal
  invisible replacement is used. Genuine unavailable media or bounded pool capacity
  hides geometry with one diagnostic per generation, never a vanilla shaft.
- Explosives show six type13 dust sprites, class112 flash shells, bounded type11
  recursive sparks transitioning to smoke, and original mesh debris363/364.
  Ordinary wood/ammo breaks use debris357/358; health breaks348/349.
  Generic static-moby import/render preserves original scale/material slots,
  rather than registering debris as unit-fitted crates. No P29 branch was merged.
- Intact grounded health-crate rendering supplies visible authoritative block
  positions: eight procedural type62 orbs, integer-fade trails, original overlay
  hemisphere/halo/glass with FX21/FX11, and camera-dependent glass ST. The geometry
  tables are read from the player's disc, not synthesized spheres or class806 meshes.
  Removal stops that intact anchor. P33 additionally renders falling interiors and
  released/free/attracting server-ledger carriers through this same local catalog;
  released geometry has no intact glass. See [nanotech.md](nanotech.md) for gameplay,
  scaled healing, canonical timing adaptations and current verification boundaries.

## Source and cache contracts

`EffectAssetRef(discSha1, sourceLevel, bank, sourceId)` keeps PARTICLE, FX, MOBY and
OVERLAY namespaces distinct. Source level1 is a curated original profile, never a
Minecraft dimension number. Controllers305/806 have no mesh blobs.

The importer retains complete particle definitions (header, offset table, alignment
padding and byte blob). Offset0 resolves to blob start. Stored spans contain no
frame count or FPS; the procedural program selects textures.

EFFECTSIDECAR uses magic `RACEFX\0\1`, schema1, converter1, **program version2**,
source SHA1/level, typed bank metadata/bytes and definitions, with whole-file
SHA256 integrity. Checksums detect corruption, not authenticity. Physical path:

`<effects-cache-root>/<discSha1>/effects/1/1/level-1.racefx`

The live optional root is `config/racmc/effects-cache`. Unique staging, reread
validation and atomic publication preserve last-good files. Failed staging is
retained for diagnosis. Meshes/overlay tables are currently read from the ISO on
provider load, not serialized into this sidecar. No RACM/SOUND codec or unified
manifest is modified.

Nanotech overlay references are independently verified R09 level1 NTSC/PAL absolute
addresses; all seven geometry digests are checked. No PAL GP guess is used. The
debris position/rotation and spark-color tables are also imported with verified
regional references/digests. Original table values/asset bytes are not Git fixtures.

All Java behavior is independently authored from permitted ReRAC ISC facts pinned
at `ae1e3432a0d5e21bd08d3872d3b04afef98be666`; no GPL code/dependency was copied.

## Event interface / future sound consolidation

`EffectSignal` carries a typed semantic, exact dimension resource ID, lifecycle
UUID, monotonic sequence, server start tick, local deterministic seed, operation,
world position and (impact only) bounded tangent/unit normal. Wire version1 has a
384-byte hard maximum. No proprietary bytes, ISO paths or original bank/audio IDs.
Client whitelist is the currently loaded dimension. Ten-second dedup tombstones
reject stale/reordered/replayed or wrong-generation events; capacity fails closed.
One-shots older than250ms are discarded instead of replayed; late terminal events
still close their attached trails. Within that window bursts start on reception
(latency-age-perfect one-shot reconstruction is not claimed).
Explicit STOP also cancels detached one-shots. Terminal bursts use their event
seed independently of trail RNG, so spawn/packet ordering does not change them.

`EffectTriggeredEvent` is a **server-local non-cancellable observation** of the
accepted semantic fact, before its optional effects packet dispatch. P30 can be
consolidated deliberately against this seam. P32 now preserves the distinct shared
reward sound hook alongside this visual hook; no sound lookup is embedded in effects.
The four existing hook files (`Rac1BlasterServer`, `Rac1BlasterArrow`,
`Rac1CrateBlast`, `Rac1CrateRewards`) require single-writer conflict review when
consolidating sound/wrench/debris. Never copy or merge their other branches merely
to obtain these hooks.

## Timing, rendering and bounded adaptations

Simulation uses fractional original updates (NTSC60/PAL50), independent of render
FPS, paused with game time. Catch-up above250ms discards old effects rather than
replaying a burst. Local seeds are not original global RNG parity.

Type0 projection now implements the source depth+0.5 calculation, diamond corner/
ST order, 1.0625 vertical correction and fixed12 near/far fades, adapted to the
current Minecraft perspective/viewport. This is **procedural projection**, not
“extracted projectile geometry.” Original framebuffer/FOV parity is unverified.
The `AFTER_PARTICLES` hook uses the already-installed view matrix exactly once.

Normal/additive blend, bilinear sampling, sprite clamp/mesh repeat, depth test on
and soft depth writes off remain explicit. Particle normal draws are sorted
back-to-front then additive; nanotech callback overlays follow the particle pass
in stable crate distance order. This is not claimed to reproduce every GS draw/
opaque-core Z-write/display-byte blending/overbright case. RGB128 and alpha128
are converted independently, but values above neutral saturate in this backend.

Pool cap2048, tracked shots512, nanotech visible-position register128 with nearest16
rendered controllers; trails reserve128 slots for shots/blasts. Type11 caps512,
debris128. Original frame-load throttling is explicitly adapted to visual pool
pressure, not falsely presented as original GPU timing. Recursive spark generations
stop at2. Type11 size uses its original quadratic rule, not a linear flipbook.
Original debris gravity/delay/reflect0.75/spin/fade rules are ported; the client
collision adapter is a conservative voxel center-sweep extended by radius, **not**
the original PS2 world/moby sphere solver. Cosmetic collision changes no blocks,
entities, health, rewards or arrow physics.

## Still incomplete / acceptance gaps

M2 regression investigation compared `7106e9d` to the integrated base: projectile
renderer/playback/Blaster adapter/world drawing were unchanged; nanotech controllers
were added to the same subscriber. The proven first-frame ownership race predated
integration (entity rendering preceded effect admission). Shared pool pressure is
another real fallback path; the owner's earlier working imported graphics are
preserved, not replaced wholesale. Headless first-frame tests require18 nonzero
original-asset draw descriptors (glow32 plus17 trail28), including before clock advance.
This does **not** certify nonzero GPU submission or reproduce the owner's symptom;
available-media captures, missing media/reload/capacity and first/stuck/body paths
still need T20/live checks. Blaster-only hit delivery no longer adds embedded shafts;
ordinary bow rendering/counts and accepted debris remain intact.

- Actual in-world appearance, camera/depth/FOV/material interleaving and two-client
  networking need separate T and owner playtest. Earlier actual-texture GPU
  material/resource fixtures passed; they are not live-world approval.
- Exact source GS color space, overbright and opaque-core depth passes, original
  global RNG/PS2 arithmetic and full framebuffer parity.
- Blaster periodic camera-relative side sparks, muzzle/socket attachment, exact
  original end-reason presentation and aiming/alignment work #11.
- Nanotech falling/free/attracting rendering and the separate server pickup ledger
  are now implemented by P33, pending independent T19. Eight visual orbs equal one
  original HP/cluster, scaled to5MC-HP, not eight rewards. Type59/60 heal ring/glints,
  exact PAL nested timing and full original platform/hazard fidelity remain missing.
- General particle kinds1/2/3, other type11 flags/consumers, other original effect
  adapters, overlay/mesh sidecar caching and shared media-manifest integration.

## Checks / owner preview

Focused unit checks: `gradlew.bat test --tests '*Effect*Test'`.
Real checks are opt-in with `-Dracmc.isoImage=<permitted-local-ISO>`, cover both
supported revisions and write no original binary fixtures.
The three headless accepted-hook GameTests use namespace `racmc_effects`; the
private handoff includes the init script selecting only that namespace.

For owner preview on this candidate branch, use an isolated world and the normal
verified local ISO gate. Wait for “Optional original effects ready” in the log.
In creative, obtain `racmc:blaster`, `racmc:explosive_crate`, `racmc:health_crate`,
`racmc:bolt_crate` and `racmc:ammo_crate`. Keep health crates supported/grounded.
Observe flight/impact, intact blue orbs/glass, ordinary breaks and existing TNT
detonation. Reload with F3+T and switch dimensions to inspect cleanup. A second
client must import its own ISO. This is a manual checklist, **not a passed test**.
