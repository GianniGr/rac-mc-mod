# OmniWrench core (standalone candidate)

This branch is based on crate slice `26d9ff5`. It does **not** contain vendor,
bolt/reward, explosive gameplay, or P28 action branches.

## Item and combat

- `com.github.giannigr.racmc.item.Rac1WrenchItem extends Item`;
  registry ID `racmc:omniwrench`, holder `RacMcItems.OMNIWRENCH`.
- Creative tab registration; one reusable item per stack, no default durability,
  ammunition or newly introduced enchantment/combination rules.
- Main-hand modifiers **+4.1 attack damage**, **−2 attack speed**. With the
  ordinary player defaults these mean **5.1 total raw HP** at full charge and
  **10 Minecraft ticks** to full charge. Partial attacks still use native charge
  scaling; this is not a second custom hit scheduler.
- Native `Player.attack` retains target attack hooks, armor, PvP/team rules,
  effects and receiver hurt immunity. The wrench's `CriticalHitEvent` is made
  noncritical, multiplier 1: no extra falling critical. It grants no sword sweep
  ability and does not bypass health or immunity through direct health writes.
- The 5.1 choice is the owner-accepted original Alien snapper → baseline adult
  zombie kill-count calibration, **not** a universal RaC/MC health conversion.
  Slam 10.2, combos, throw, Steve poses, movement and action networking are P28's
  separate scope. No action state or action packet is duplicated here.

No new UI input is introduced. Inventory selection and normal attack/mining
remain native Minecraft controls; crouch/airborne action interception belongs
to P28 and is not implemented by this standalone core.

## Block policy and validation

`Rac1WrenchBlockPolicy.action(registryId, protectedTag)` has a positive, exact
allowlist: `racmc:bolt_crate`, `racmc:health_crate`, `racmc:ammo_crate`.
Only those existing crate blocks return destroy progress 1 when holding the
wrench in the main hand. All other blocks retain ordinary native mining.

`racmc:gadgetron_vendor` is a **hard deny**, including creative. Its exact ID was
read from `RacMcBlocks` in owner-approved immutable commit `17773dc`, without
accessing that worktree or copying its classes. No vendor compile dependency.
The optional `racmc:wrench_protected` block tag can only add protection. There
is deliberately no datapack allow tag: tags cannot turn a vendor, unknown RaC
block or another namespace into an instantly breakable crate.

The left-click subscriber **only cancels/resynchronizes** denied actions, on
both sides and for all action types. It never destroys anything: that event
fires before native server reach/mode/permission validation. The item
`canAttackBlock` and server `BlockEvent.BreakEvent` provide additional denies.
Loaded, in-reach server clients receive authoritative block/BE state on denial.
Unloaded positions are not queried/loaded just to resynchronize a forged action.

Ordinary crate removal stays in `ServerPlayerGameMode`:
reach/build-height → interaction restriction → native instant destroy →
cancellable break event → native restricted-mode check → block harvest.
The crate's `onDestroyedByPlayer` is the **single** adapter call site. No manual
removal in START, STOP, attack, item callbacks or render code. Other tools and
vanilla blocks keep their existing behavior.

### Ordinary-crate integration seam

`Rac1WrenchCrateHits.install(Handler)` is bootstrap-only and rejects a second
installation. The typed handler is:

```java
boolean destroy(ServerPlayer player, BlockPos pos, BlockState expectedState);
```

It is reached only for the allowlisted ordinary crate, after native validation
and break-event acceptance. The helper checks the live expected state before
calling the handler. Without a handler, the existing superclass/native removal
runs **once**: standalone crates still break and still have no rewards.
Client prediction always uses the native removal, never the server handler.
If the installed transaction returns false, the server explicitly sends the
current block/BE state to undo predicted air; native harvest's return alone
does not reliably communicate a removal-hook rejection.

At consolidation, wire **one** genuine P24 destruction transaction. Its known
`Rac1CrateRewards.destroy(ServerLevel, BlockPos, BlockState, @Nullable Entity)`
returns void: the integration handler must determine actual removal afterward
without treating that void as success, rerolling rewards, calling native removal
again, or installing a second payout from `playerDestroy`. P24 owns lifecycle
UUID/admission, reentrancy and exactly-once rewards. Do not independently infer
identity from a cached blockstate across asynchronous actions.

### Explosive boundary — not implemented

`racmc:explosive_crate` returns `EXPLOSIVE_ADAPTER_REQUIRED`, progress 0, and is
currently denied for wrench mining **including creative**. It is never silently
deleted, disarmed, rewarded or detonated by this core.

The next integration must replace that temporary denial with P25's real,
server-validated short-fuse hit transaction: **5–15 original 60-Hz updates,
roughly 0.08–0.25 seconds, not 5–15 Minecraft ticks**. P25 owns quantization,
already-armed shortening without extension, falling/detonating states, flash,
blast and chain races. No reward on the arming hit; call P24 only at actual
destruction. Creative's early native destroy path and survival's validated
block attack path need deliberate shared dispatch and authoritative resync;
do not mutate from the pre-validation left-click event.

## Imported drawing and P28 API

Import standalone **gadget 71** with the existing gadget-WAD reader, not the
offset-zero moby geometry entry, not geometry sliced from Ratchet. Original
texture/skeleton/sequences use the existing independent Java converters. No GPL
implementation was copied/transliterated and no dependency was added.

`Rac1CacheContent.wrench()` holds the result. This standalone branch uses schema
**6**, converter **5**, section kind **71**. Missing/wrong/duplicate wrench
sections and version mismatch fail closed; deliberately incomplete synthetic
content can hold null, but the production manager rebuilds it rather than
reporting READY. Old cache files are rebuilt, not reused as complete.
**These version numbers are branch-local**. The first core commit used 5/4,
coincidentally also used by the separate vendor slice; 6/5 adds timing and
attachment metadata. Consolidation must reconcile all sections and select a
new combined schema/converter; never overwrite a combined cache with this slice.

`Rac1ImportedItemDrawing` shares texture lifecycle, item-cube origin correction,
GUI fullbright lighting and mesh submission with the existing Blaster renderer.
Blaster fire/aim/muzzle/pose behavior stays Blaster-specific. Texture leases
release on replacement, missing content, reload and partial registration failure;
failure does not mark the lease as successfully initialized.

`Rac1WrenchItemRenderer` samples **held sequence 1, time 0**. No action clock,
Steve pose, player tracking, detach state or networking here. The future
P28/consolidation sampling API is:

```java
Rac1ImportedItemPose.Sample sample =
    Rac1ImportedItemPose.sample(asset, sequenceNumber, seconds);
// sample.positions(), sample.normals(); missing sequence/asset -> empty arrays
```

Sampling is pure, uses each asset's own sequence names and the existing
evaluator's **seconds**, rejects negative/nonfinite time, and never advances
gameplay or fires action events. This is a mesh sampler, not a promise to
represent source gameplay key-time or blend state. Consult P28's one generic
action-presentation interface **only after consolidation**; do not introduce a
parallel presentation store in this renderer.

`Rac1WrenchItemRenderer.renderSequence(context, poses, buffers, light, sequence,
seconds)` draws an item sample with native cube correction. `renderDetached(
poses, buffers, light, sequence, seconds)` draws at an already composed world
socket with **no** cube correction. Both share the same mesh/texture path and
neither creates an item or advances action state. P28's presentation owns
visibility, world position, local spin, body/wrench clocks and transitions;
these entry points do not install its client hooks before consolidation.
Reuse `RacMcClientRegistration.wrenchRenderer()` rather than constructing a
second renderer with competing texture registrations.

### Source metadata / action-sampler dependency

`Rac1Animation.sourceTiming()` returns `Rac1SourceTiming`: raw
`sequenceRateBits`, original `headerTimes16` and `keyRateBits`, rate-derived
`sourceSeconds` for **every original key**, and `sampledSourceKeys` mapping
converted pose keys back to original indices. `-1` marks a converter-generated
loop endpoint. Original timing is retained even for zero-duration pose keys
removed by the existing converter. Raw rate bit patterns are kept as integers,
including terminal sentinel/NaN payloads; synthetic animations explicitly say
timing unavailable. Header time /16 is not seconds*60 or a key index.
This preserves data; it does not implement P28's action-event crossing clock,
loop6..21, catch-at-index26, dynamic speed, blend or landing retiming.

`Rac1AnimatedAsset.attachmentChain(listIndex)` retains the actual imported
root-to-joint lists. `Rac1AttachmentSampler.sample(asset, sequenceName, seconds,
listIndex)` returns an optional 16-float column-major **converted model-space
joint pose**, not a skin matrix or Steve world socket. It uses the existing
chain evaluator, excluding local-only post-scales. Ratchet list0 is the known
hand chain; wrench list1 is the head chain, not necessarily joint1. Body clips
are named `ratchet_sequence_26` / `ratchet_sequence_43`, child clips
`sequence_1` / `sequence_6` / `sequence_10`. No invented left-arm IDs, source
trajectory arrays or calibrated poses are embedded in the jar.
The imported matrices are already in the converter's Minecraft basis and
scaled units: do not apply the source `(x,z,-y)` basis/class scale a second
time. Full palettes are also available through the existing pure
`Rac1AnimationEvaluator.evaluatePose/evaluateChain(skeleton, animation, seconds)`.
Source-rest calibration, torso-relative projection and column normalization
for the target socket remain explicit P28/integration math, not source assets
or a calibrated Steve pose supplied by this method.

**Full actions remain blocked, not complete.** P28 `535b5a3` has an explicitly
unavailable server `Sampler`, so original metadata and rendering seams alone
do not make its actions usable. Decoder math, sequence/event IDs and verified
behavioral constants cannot reconstruct copyrighted original joint trajectories
on an asset-free server. This core supplies neither assets over the network
nor a disguised baked trajectory table.

The integration worker must resolve one of these explicit contracts:

1. A lawful server-local source-data deployment, separately authorized and
   reviewed: headless sampler reads its own local clip/skeleton/attachment data,
   not a client singleton/cache and not runtime ISO IO. No such deployment
   pipeline is implemented or implicitly approved here.
2. Owner-approved, independently authored **Minecraft server contact/phase
   model**, with original imported clips used only for client presentation.
   Label anatomical/timing/collision approximations honestly and validate the
   visual/contact mismatch. Never trust client poses or target lists as damage
   authority. No such approximation is silently installed.

Then P28/integration must install `WrenchActions.Sampler`, map source/rest
channels into its `SteveWrenchPose` math, supply the actual resulting Steve
hand/head/body-contact anchors and event clock, snapshot transition poses, wire
one normal/action cooldown, install complete skin-arm/item first-person drawing
and detached proxy drawing, and verify live hooks. P27 owns the imported mesh,
metadata and renderer APIs above; P28 owns action state/network/contact math and
pose hooks. Do not begin T14 full-action acceptance with the capability still off.
Specifically, native normal attacks need the P28
`normalAttackAllowed(ServerPlayer)` / `normalAttackAccepted(ServerPlayer)` gate;
when `deliveringContact(Player)` is true, do not run that normal pipeline again
or cancel P28's native protection-hook dispatch. A managed action also blocks
ordinary vanilla attacks through P28's `busy(Player)` guard. The standalone core
has no custom normal-start listener and does not pretend these calls are wired.

Held meshes preserve the imported root/socket and class scale, with the native
item-cube correction applied exactly once. Model display rotations are
wrench-specific; no Blaster bore/aim transform is reused. Both hands have
explicit first-/third-person transforms. Only GUI display centers the posed
bounds and fits a bounding sphere; it does not recenter the held wrench.
Ground/fixed have separate display transforms.

## Checks and remaining gates

Focused JUnit covers attributes/reusability, critical event policy, exact-ID/tag
policy, sequence/time sampling, texture lifecycle/retry, real ItemTransform cube
origin and GUI fit, cache round-trip/version/corruption, and existing cache /
crate transform regressions. Opt-in `RealDiscWrenchTest` reads the permitted
disc in place, checks gadget metadata, samples slots 1/3/4/5/6/10 and round-trips
the original wrench in **memory only**.

No runtime client/server or full suite is claimed here. T14 must independently
verify the consolidated tree, native cancellation/spawn/adventure/PvP protection,
creative vendor and forged packets, repeated/reentrant crate operations, actual
combat charge/armor/hurt-time behavior, and P24/P25 interactions. Both-hand
first-/third-person grip/orientation, GUI visibility, clipping, resource reload
and other-player views still need actual in-game visual acceptance. Mathematical
transform tests are not screenshots or owner playtesting.
