# Bolt motion candidate (P26)

Standalone follow-up on `feature/issue10-bolt-motion`, based on `9cd9cfa`.
This branch retains that base's plaintext HUD. It does **not** integrate P24's
parallel original-HUD work. No merge, publication or owner playtest is claimed.

## Motion contract

Each pending pickup receives its own immutable launch state **before entity
admission/tracking**. The source is the placed crate's bottom-center. In Minecraft
axes, with independent horizontal draws A/B:

```
H = (cos(angle)*A, 0, -sin(angle)*B), A/B in [0,3)
position = source + (0,.5,0) + .5*normalize(H)
velocity = (H + (0,U,0))/60, U in [3.7,6)
```

Zero horizontal velocity has zero outward offset. Java RNG is used, not an
emulated retail PRNG. Duplicate random draws can legitimately overlap; there is
no repulsion, merging or shared mutable crate vector.

The server performs three 60 Hz virtual frames per 20 TPS tick. Each falling
frame subtracts `10.8/3600` from vertical velocity **before** moving, without air
drag. Physical world scale is one original unit per block. All denominations
share launch/gravity; sphere radii are .15/.23/.23/.25 for values 1/5/20/50.

Unsuitable contacts reflect only incoming velocity, without restitution loss.
Both separation and surface normal must be strictly within 45 degrees of up
to settle. Suitable ground enters settling on first contact. Settlement lasts
30–39 virtual frames, followed by an independent 10–19-frame visual idle hop.
Counters are sampled once at creation and persisted, rather than rerolled on
view recreation. The original RNG's endpoints/quantization are not reproduced.
Settle and idle hop terms are respectively `t*(1-t)*L*.025` and
`t*(1-t)*L*.0125`, retaining the original 60 Hz counter in the amplitude.
Class rest offsets, tilt and angular steps are kept in the pure motion model.

Attraction eligibility is state-based, **not a mandatory pickup-delay timer**:

- Falling: proximity only after contact; falling two units below both launch
  height and an eligible player can force attraction.
- Settling: no proximity interruption.
- Idle: range check before the visual hop, every virtual frame.
- Attracting: preserve residual velocity, apply the inspected 10-unit drag,
  accelerate homing by `16/3600` toward a maximum `48/60` per frame.

Range remains horizontal <3 and vertical difference <1.75. The server selects
an eligible same-dimension player deterministically, retains a valid acquired
target, and releases invalid/disconnected targets without awarding value.
Only the server ledger can credit currency. Client packet interpolation does
not simulate phases or collect.

## Explicit Minecraft adaptations and limits

`Rac1BoltWorldCollision` reads actual **loaded block collision shapes**, including
slab/stair boxes, using the entity's collision context. `Rac1BoltCollision`
performs sphere/box sampling at at most half the denomination radius per sample,
with bounded repeated depenetration and multiple-face reflection. Embedded ring
starts choose the closest exit face; unloaded chunks act as temporary barriers
without force-loading. Edge/corner sphere separation is distinct from the
selected voxel face normal.

This is **not** a bit-identical PS2 sphere/mesh kernel, a captured emulator
trajectory, or a universal guarantee for impossible enclosures narrower than
the sphere diameter. Candidate ordering, rounded voxel corners and bounded
depenetration can differ from the original. Extremely constrained geometry
requires independent runtime testing. The ledger retains value even if geometry
cannot yield a free position.

Static voxel rest poses use a stable tangent basis, rather than the original
mesh surface's full orientation frame. Original tumble quaternions, full
rest-orientation rendering, glints, homing bends/hero-displacement compensation,
Bolt Grabber, moving carriers and optional imported crate paths are **not**
implemented here. Existing renderer/model anchoring is unchanged; server
translation and client packet interpolation are this patch's scope. PAL live
timing is not independently reproduced; the reference profile is NTSC 60 Hz.

The existing void policy is retained: recover unclaimed currency above the
minimum build height, as idle currency without awarding anyone. This is a
Minecraft value-conservation adaptation, not researched PS2 void behavior.

## Persistence and economy

The ledger remains the sole durable owner; bolt entities remain disposable,
unsaved views. Pending records now include phase, frame velocity, launch height,
rest point/normal, counters, spin phases/direction, homing speed and target UUID.
The entity updates that record after each server tick. Normal save/restart
resumes from the saved frame, including an unadmitted launch. This retains the
existing **normal-save consistency, not fsync/crash-atomicity** guarantee.

Missing entities are reconstructed with the **same** UUID/value/motion.
Failed admission leaves the exact pending record untouched for retry. Repeated
emission or destruction does not reroll state or create another reward.
Old records without motion migrate deterministically to idle, not to a second
random launch. Existing balances, denomination splitting, reward incarnations,
currency synchronization, debit behavior, blast/fire/damage immunity and no-XP
semantics are unchanged. Explosion immunity never clears intentional velocity.

## Evidence and validation

Behavioral source: accepted R05 handoff `R05-MOTION-HANDOFF.md` under registered
private artifact `r05-bolt-motion-20261116`, especially §§3–5 and §8.
Pinned ISC ReRAC reference revision:
`ae1e3432a0d5e21bd08d3872d3b04afef98be666`.
This is independently written Java; no GPL code, game assets or cache files
were copied into the project. No new ISO research was performed.

Measured on Windows with session-local Java 21:

```
gradlew.bat compileJava test --tests "com.github.giannigr.racmc.loot.Rac1Bolt*Test" --console=plain
```

**23 focused tests passed**, including existing payout/ledger regressions.
Coverage includes explicit launch draws, zero normalization, fixed-seed 100-drop
ring spread, all denominations, R05 free-flight goldens, 60 Hz/20 TPS agreement,
first-plane-contact frames, reflection and 44°/46° slope tests, thin wall,
slab/ceiling/corner/embedded-start geometry, ledges, stacked source heights,
settle/idle hop timing, collision attraction before any artificial delay,
disconnect recovery, interpolation arithmetic, and exact persisted continuation
through all four phases. Admission-boundary tests reject an attempted admission,
serialize/reload, then accept the same UUID/value/motion on retry; unloaded/wrong
dimension/existing-view cases skip admission. Existing legacy ledger serialization
still roundtrips.
The first run found an overly strict exact-double assertion in a synthetic
air-movement test; it was corrected to numeric tolerance. No pre-fix test run is
claimed: the new motion API does not exist on the base.

Two new synthetic-world GameTests compile for actual slab/wall adapter behavior
and pre-admission launch/repeated-emit identity. The existing repeated-explosion
test now checks preservation of intentional velocity, not obsolete zero velocity.
**These GameTests have not been run by P26.**

Pending independent T/owner gates:

- Full relevant suite and GameTests on this exact standalone branch.
- Actual-world `addFreshEntity` rejection/retry (beyond the tested admission
  boundary), unload/reload while moving, void
  recovery, tight enclosures and repeated blasts during every motion phase.
- Dedicated-server/two-client synchronization, packet quantization and visible
  interpolation/renderer anchoring at near/far distances.
- Owner observation from outside attraction range, including stacks and ledges.
- Reconciliation with P24 only after the separately required approvals.

Compilation/unit goldens are not evidence of smooth in-game rendering,
production runtime correctness or an owner-approved completed playtest.
