# Wrench actions — consolidated implementation candidate

This branch consolidates P27 core/server-local data (`996dc61`) with P28 actions,
not vendor/bolts/explosive-gameplay/rewards/audio branches. T14 runtime verification is pending.

## Controls and availability

- Main-hand wrench, crouch + attack (default left click): comet throw over block/entity/miss.
  Both legs can deliver normal-strength5.1HP and contact ordinary RaC crates. Pickup, currency
  attraction, enemy homing and multi-bounce ricochet remain excluded.
- Otherwise an airborne attack requests slam after four airborne MC ticks, with clearance0.1
  rising /0.57 descending, regardless of MISS/ENTITY/actual BLOCK target. Grounded mining stays native.
- Native normal attacks remain total5.1HP, sharing a10-tick accepted-start gate with actions.
  Undercharged/overlapping starts are rejected. Slam raw10.2HP never stacks a vanilla crit/sweep.
  Native mitigation/hurt immunity, attack protection hooks and PvP/team checks apply. Slam retains
  native reach/visibility checks; ranged throw uses bounded authoritative flight, not melee reach3.

The dynamic server-local provider is described in [wrench-server-data.md](wrench-server-data.md).
Integrated servers use the ordinary legitimate ISO configuration; dedicated servers need explicit
local configuration. Missing/loading/invalid data disables managed actions only, not server startup
or ordinary core melee/mining. Status/details stay in local logs; assets/paths are never broadcast.
Client assets remain imported through the existing mandatory client gate.

Release/catch never transfers/spawns an inventory item. The detached wrench is a rendered attachment.
Death, held item/slot switch, respawn, dimension/disconnect/server-stop and invalid mode cancel state.
A bounded timeout prevents permanently hidden held meshes.

## Original source clock and rigid Steve projection

`WrenchOriginalSampler` is installed at server start and reads provider availability dynamically,
including after asynchronous import completes. `WrenchOriginalClock` keeps original key index,
header key-time/16 and converted pose seconds distinct. No render-FPS clock or baked trajectory.

- Slam body43 / child10 start at each original index4, with one source speed and body11/child13
  original-update blends. Initial upward approach7.7units/s is reduced above original height
  thresholds1.8 and2.25. After update14, descent130units/s² is capped at−37units/s.
- Body speed0.33 before descent; an airborne landing estimate retimes toward source key26,
  with an authored bounded speed range0.5..3 and near-ground minimum0.75. Ground recovery
  uses speed2 before key26, then1. Native gravity, collision, fallDistance and fall damage remain.
- Throw body26 loops original indices6..21; release crosses source key-time33; catch tail starts
  at index26. Child1 -> detached6 -> near-catch1. Fixed60Hz flight substeps reproduce outward23units/s,
  the deceleration/return accumulator, moving-hand catch, early wall return, ground-clearance
  approach and24.434608rad/s local source-Z (converted local-Y) spin.
- Actual attachment lists/bind matrices/evaluator palettes calibrate torso, shoulders and hands.
  Steve's rigid arms project source direction/roll; there is no invented elbow ID, stretching,
  replacement Ratchet geometry or advanced moveset.
- The common socket includes native pivot/RzRyRx, slim correction before rotation, item-in-hand
  transform and actual mirrored left/right display rotation. World anchors include player scale,
  native0.9375 model scale and crouch render offset.
- One hip-anchored root transforms all native Steve parts and armor/cape layers together.
  Arms are projected locally; source wrist rotation adjusts the accessory about the real grip.
  Independent source torso translation is not applied to flat native torso/arm siblings.
- Three original60Hz poses, including interpolated actor position/facing, are sampled per MC tick.
  Authoritative attack yaw, not potentially stale native body yaw, controls contacts and drawing.

These are explicit anatomical/20TPS adaptations, not pixel-identical original execution. Whole-action
slam target/block dedup is the MC safety adaptation, not original receiver re-hit timing. No original
aim assistance, pickup/lunge kit, audio or impact-FX pipeline is inferred.

## Contact, crate ownership and networking

Five interpolated hand-to-head segments stop at their first entity/terrain contact; precise tip0.47
and descending-body0.4 sphere-vs-AABB tests can contact multiple targets. Broadphase boxes collect
candidates only. Ground landing is not a radial blast or extra damage pulse.
Original active wrench contacts remain eligible through landing/recovery; landing alone grants no hit.

Thrown enemy contacts use sampled radius0.4 spheres at posed attachment-list1, falling back to list0
only when list1 reports no candidates. All eligible candidates in the selected sphere are considered;
this is not a damaging swept capsule. Server-owned identities1/2 alternate on accepted throw start
and persist across OUT/BACK. The per-owner/receiver gate lasts37 original60Hz updates (~0.617s),
not37 MC ticks and not once per leg. Same-sample messages coalesce; accepted native held damage
can change the recorded identity. Native hurt immunity is never cleared to force a hit.
Outward surface contact starts half-speed return without snapping to impact or reflecting.
The final catch update can query once; subsequent in-hand idle updates cannot damage.

Ordinary crate contacts use P27's positive policy and the native harvest path. Loaded position,
border/build height, reach/visibility, game mode, interaction/spawn permission, left-click protection
and native BreakEvent must permit it. State/block-entity/held identity is captured before entity
callbacks and rechecked after the pre-hook and again at the removal seam after BreakEvent.
Native `destroyBlock` reaches the ONE `Rac1WrenchCrateHits` adapter through `onDestroyedByPlayer`;
there is no parallel removal/reward path. One action cannot harvest the same position again through
ray/tip/body overlap. Vendor/protected and explosive blocks cannot enter this ordinary-crate path.
Explosives remain non-removable pending later P25 short-fuse integration. Vanilla/unknown blocks
are not action-destroyed. Thrown ordinary-crate contact is an exact radius0.4 swept sphere between
posed head samples, with authoritative flight reach and once-per-block-entity-lifecycle dispatch.
Throw does not impose a blanket world-LOS gate on return contacts. Explosive contact has a future
P25 handler seam, but no handler is installed here: absent one, explosives remain unchanged,
never silently removed/disarmed. There is no duplicate reward or currency-attraction implementation.

Protocol `wrench-actions-3` uses a10-byte C2S serial/intent/default-or-slim flag. The flag selects
one of two fixed native rigs, not a client pose/target. The server owns clocks/collision/damage.
S2C carries phase, authoritative facing yaw, separate body/child time/blend, held visibility and world attachment
position/spin/release orientation, never original joint/clip/texture arrays or ISO/operator details.

## Actual drawing paths and verification boundary

`WrenchActionClient.presentation(player, partialTick)` interpolates accepted snapshots without
advancing events. The player-model setupAnim-tail Mixin applies projected local arms after native
animation; a living-render Mixin applies the shared root before body and all layers. Scoped native
head/hat/body/arm/leg/wear transforms and scales are restored; native matrix push/pop resets the root.
The item renderer uses that scoped actor, not the local player's state for GUI icons.

First person actually draws skin arms/sleeves and the imported held wrench instead of the native
helper that zeros arm xRot. A viewmodel-only offset does not affect server contacts. A world pass
draws the detached original mesh using captured release orientation plus local spin. GUI/ground/
fixed retain the core held model.

Focused checks include a separate JDK+existing-JOML-only process: real NTSC ISO validation/shared
cache -> original clock -> all left/right/default/slim rigs -> slam/throw motion/contact/catch.
This is not in-game rendering/gameplay acceptance. T14 still verifies exact-tree client/dedicated
startup, missing-data fallback, real input/network/permissions/combat/crate behavior, observers/
late join/equip/death/dimension changes, actual arm/body/armor/grip/proxy visuals, reload and regions.
No full-suite, Minecraft-runtime or owner acceptance result is inferred from headless tests.

## Correction measurements and remaining acceptance

Real NTSC data/native model tests reproduced a baseline torso-only waist separation of0.52718
blocks; shared-root seam separation is0, with maximum added native waist/neck error~1.04e-7.
Rendered grip/head versus authority differs by at most~1.96e-6 blocks over tested keys/rigs/yaws.
Normal-jump native-zombie AABB cases at yaw0/90 each have two contact samples, both in the
landing window. Side/height/edge/corner noncontact checks retain the original small radii.
These are headless geometry measurements, not live mob damage, armor, plugin or visual acceptance.

Source sequence10 genuinely deforms: peak grip/head distance1.81484 blocks and mesh span2.25316,
versus held grip/head~0.43. No duplicate class-scale multiplication was found. The action-only
deformation parameter remains **1.0** pending owner approval; the proposed0.4 (~0.98 peak
grip/head) is not applied. Do not attribute all previous misses to the handle size.
T14/owner must still accept actual anatomy/grip/layers, combat/protection/native hurt, crate
transactions and multiplayer on the exact corrected commit.
