# Wrench actions — consolidated implementation candidate

This branch consolidates P27 core/server-local data (`996dc61`) with P28 actions,
not vendor/bolts/explosive-gameplay/rewards/audio branches. T14 runtime verification is pending.

## Controls and availability

- Main-hand wrench, crouch + attack (default left click): cosmetic comet throw over block/entity/miss.
  No throw damage, block breaking, pickup, homing or ricochet is authorized/implemented.
- Otherwise an airborne entity/miss attack requests slam after four airborne MC ticks, with
  clearance0.1 rising /0.57 descending. Near-ground attack and direct block mining stay native.
- Native normal attacks remain total5.1HP, sharing a10-tick accepted-start gate with actions.
  Undercharged/overlapping starts are rejected. Slam raw10.2HP never stacks a vanilla crit/sweep.
  Native mitigation/hurt immunity, attack protection hooks, reach, visibility and PvP/team checks apply.

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

These are explicit anatomical/20TPS adaptations, not pixel-identical original execution. Whole-action
target/block dedup is the MC safety adaptation, not original receiver re-hit timing. No original
aim assistance, pickup/lunge kit, audio or impact-FX pipeline is inferred.

## Contact, crate ownership and networking

Five interpolated hand-to-head segments stop at their first entity/terrain contact; precise tip0.47
and descending-body0.4 sphere-vs-AABB tests can contact multiple targets. Broadphase boxes collect
candidates only. Ground landing is not a radial blast or extra damage pulse.

Ordinary crate contacts use P27's positive policy and the native harvest path. Loaded position,
border/build height, reach/visibility, game mode, interaction/spawn permission, left-click protection
and native BreakEvent must permit it. State/block-entity/held identity is captured before entity
callbacks and rechecked after the pre-hook and again at the removal seam after BreakEvent.
Native `destroyBlock` reaches the ONE `Rac1WrenchCrateHits` adapter through `onDestroyedByPlayer`;
there is no parallel removal/reward path. One action cannot harvest the same position again through
ray/tip/body overlap. Vendor/protected and explosive blocks cannot enter this ordinary-crate path.
Explosives remain non-removable pending later P25 short-fuse integration. Vanilla/unknown blocks
are not slam-destroyed.

Protocol `wrench-actions-2` uses a10-byte C2S serial/intent/default-or-slim flag. The flag selects
one of two fixed native rigs, not a client pose/target. The server owns clocks/collision/damage.
S2C carries phase, separate body/child time/blend, held visibility and cosmetic world attachment
position/spin/release orientation, never original joint/clip/texture arrays or ISO/operator details.

## Actual drawing paths and verification boundary

`WrenchActionClient.presentation(player, partialTick)` interpolates accepted snapshots without
advancing events. The player-model setupAnim-tail Mixin applies projected body/arms after native
animation, copies wear parts, blends from a captured native starting pose, and restores scoped
parts/scales. The item renderer uses that scoped actor, not the local player's state for GUI icons.

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
