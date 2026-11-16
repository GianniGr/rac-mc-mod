# Explosive crates — standalone implementation contract

P25; base `26d9ff5`; no bolt/vendor dependency. This branch intentionally retains
baseline **no payouts**. Independent T verification and owner playtesting must
precede consolidation. Unit/source checks do not establish runtime correctness.

## Gameplay mapping

- Class 505 only: alive nonspectator Player/Mob face contact (including creative,
  grounded or airborne) arms once. Numerical contact tolerance is 1e-6 blocks,
  not proximity. Items/projectiles/neighbor blocks do not arm.
- Supported placement alone does not arm. Vanilla falling placement arms only
  after positive vertical drop from persisted origin. Vanilla physics remains;
  explosive carriers subclass FallingBlockEntity for tracked fuse state.
- Full fuse: 60 loaded server ticks; arm tick does not decrement. Fuse pauses
  unloaded/offline, continues in flight, and cannot decrement twice on transfer.
  NBT persists remaining ticks and last processed game time; BE packets and
  tracked entity data transport flash state to clients, including late tracking.
  Already-falling baseline vanilla explosive carriers migrate lazily on server
  tick or blast, preserving entity/BE NBT and using a fresh transport UUID.
  If their old save lacks origin height, current height is the conservative
  fallback (the baseline never saved that height). Spawn veto keeps the old
  carrier instead of consuming it.
- Idle blast-hit explosives get **one deferred MC tick**, the smallest available
  approximation of the original one-update (1/60 s) fuse. This is not recursive
  same-call detonation or another 60 ticks. Already-armed fuses are preserved:
  original random 5..15-update re-hit shortening is NOT implemented.
  Grounded vanilla destructive-explosion callbacks also arm rather than remove
  explosives (client replay retains survivors); airborne explosion callbacks arm.
- Terminal reservation precedes callbacks; each lifecycle emits at most once.
  A canceled Start or denied self-consumption leaves a terminal inert crate
  rather than retrying every tick. Hand breaking keeps baseline ordinary removal;
  failed fall/timeout/void keeps vanilla no-drop behavior, without a blast/payout.

## Geometry and damage are separate contracts

Original evidence: R06-ORIGINAL-VALUES.md, supplied private research; no fresh ISO
reads or extraction. Original expanding queries run to roughly 2 world units for
hero-facing delivery and 3 for nonhero mobys; the first query is not literally
hero-only. Only hit-enabled mobys and receiver hit masks accept messages.
Original health/knockback is receiver-owned, not a universal two-damage formula.

**MC mapping:** all RaC crate kinds, including loaded falling carriers, use one
instant sphere of radius **3 blocks**, centered at crate cube center, against
closest point on the unit block AABB / carrier AABB, inclusive at the boundary.
This approximates original collision primitives, not exact expanding PS2 queries.
It ignores intervening walls per adopted original rule. Loaded chunks only;
no terrain raymarch or chunk acquisition.

Living entities use **vanilla TNT strength 4 baseline**, damage envelope radius
**8 blocks**, vanilla entity-position distance and vanilla sampled exposure.
Raw damage is exactly `0.5 * floor(((impact² + impact)/2)*7*8 + 1)`,
`impact=(1-distance/8)*exposure`, then normal explosion damage/armor/events apply.
Point-blank unarmored damage can be **28.5 HP**, potentially lethal. This is NOT
strength 2, radius 4, fixed damage, or original Ratchet HP behavior. Walls do not
shield the crate chain query, but DO affect living damage/knockback exposure.
Knockback retains vanilla TNT magnitude, exposure falloff, explosion resistance
attribute and NeoForge knockback hook, independent of damage scaling.
Only LivingEntity targets receive damage/knockback/callbacks; **ItemEntity is
excluded both before and after events**, not assumed invulnerable. Other
nonliving entities are not TNT-equivalent targets on this branch.

## Events, protection, terrain and integration

One Explosion context (strength 4, fire=false) posts NeoForge Start before effects
and Detonate before mutation. Event removals are honored; immutable original
candidate identity/set intersection rejects additions and stale/replaced BEs.
Denied self-removal suppresses the entire emission. The service never calls
vanilla explode/finalizeExplosion, sends no vanilla terrain-destruction packet,
and never removes a non-RaC block or places fire.

This honors NeoForge explosion hooks and ordinary damage hooks, **not a guarantee
for every third-party claim mod**. Mods protecting only player BreakEvent need a
separate adapter; no fake player break is emitted. TNT gamerules do not disable
this crate-only original gameplay mechanic; mobGriefing is not used (not a mob).
Unknown protection integrations require T/owner validation before claiming support.

**P24 consolidation seam:** `Rac1CrateBlast.destroyCrate(ServerLevel, BlockPos,
BlockState, @Nullable Entity)` currently validates then calls `level.removeBlock`.
Replace that removal with `com.github.giannigr.racmc.loot.Rac1CrateRewards.destroy`
when consolidating, preserving authorization/identity guards. Grounded emitter
and ordinary blast targets share this single seam. Falling nonexplosives currently
use the explicitly marked `falling.discard()` seam; extend the reward service to
support falling carriers before wiring payouts there. Arming/transfer never pays.
No production stub or reflection dependency on P24 exists.

## Visual/audio scope and remaining runtime gates

Existing imported mesh/material textures are reused; original red predicate
`counter % 60 > 40 || counter < 15`, sampled with `counter=179-3*elapsed`,
modulates vertex color in world and airborne renderers. This is runtime material
modulation, not a fabricated skeletal animation. Item transforms and Blaster
render paths default to unchanged white tint.

**No beeps, explosion sound, or blast/debris FX are implemented.** Original sample
identity/audio pipeline belongs to R07/future work. Original debris/puff assets,
growth/blend/trajectory/lifetime rendering remain import/runtime gaps. No vanilla
placeholder is presented as original fidelity.

T/owner must exercise contact on six faces/no halo, supported placement vs fall,
armed fall/landing/saving/retracking, in-air expiry/no ghost block, stacked chains,
all crate kinds behind terrain, no terrain/fire/item damage, event veto/removal/
injection, damage/armor/exposure/knockback, dedicated-client synchronization and
visual flash cadence. Runtime/claim-plugin compatibility is not proven by compilation.
