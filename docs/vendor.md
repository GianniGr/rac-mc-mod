# Gadgetron vendor — visual slice (#12)

The Creative tab contains `racmc:gadgetron_vendor`, a placeable, stationary kiosk.
It imports RaC1 body class **11** and logo child **1143** from level 1 through the
existing animated extraction/cache/skinning/mesh-rendering pipeline. No original
assets are shipped. Class identities are documented in OpenRAC rac1-decomp
`tools/extract/moby_classes.tsv`; proximity facts below are attributed to ReRAC
`docs/plan/interaction.md` §9.1 (ISC). This implementation does not copy those
projects' code.

## Accepted behavior and Minecraft defaults

- Body **always** uses `sequence_0` at its first key (closed). Retail sequence 1
  is byte-identical. Unfold/open/fold belong to the retail menu, not proximity,
  and are not played here. No shop, menu, use handler, salesman or rewards.
- Uniformly scale the **posed closed** X/Z footprint to one block, preserving all
  proportions; center it at the block center and ground its lowest vertex.
  Measured original footprint is about 2.303 units; scaled height is about .549
  blocks. The bind pose and open pose are not used to compute the fit.
- Body and logo share that outer scale. The logo is translated +2.95 original
  units above the original body origin, with instance scale `2.5 * growth`.
  Imported class scale is not applied again. The logo may extend beyond the
  body footprint; the renderer's culling box includes that overhang.
- Client-local multiplayer default: nearest alive, non-spectator player inside
  the current vertical envelope, measured horizontally from the block center and
  vertically from its ground origin. Survival and Creative both qualify. No
  facing or line-of-sight requirement. Each client uses the players it knows
  about; this is not authoritative server/shop state and requires no networking.
- Retail enter cylinder: radius 16, vertical distance <=8; exit cylinder: radius
  18, vertical distance <=10. Boundary is inclusive. Thresholds are scaled by
  `1 / 2.303` (about 6.95/7.82 blocks horizontally, 3.47/4.34 vertically).
  These are the measured closed-footprint reference scale, not player scale.
- Poll every eight **60 Hz retail frames**, quantized to Minecraft client ticks
  (three retail frames per 20 Hz tick). Growth changes .1 per retail frame,
  clamped 0..1, with render interpolation; full growth takes about 1/6 second.
  Logo joints 1/2 counter-rotate .01 rad per retail frame (.6 rad/s) using a
  vendor-only rest-pose layer and the shared evaluator. No global animation or
  importer semantics changed.
- The retail collision class has no vertex hull. The chosen Minecraft default
  is a non-occluding grounded box, 1 x .55 x 1 blocks. The kiosk does not fall,
  drops nothing when mined, and has metal sound/hardness 2/blast resistance 6.
  These are local defaults, not extracted retail physics.
- The item draws only the closed body. GUI uses full light; held/ground items
  use supplied world light. All display contexts use the vanilla block-cube
  corner origin: **translate before uniform scale**, never scale the half-block
  origin offset. Block destruction buffers are supported without forced casts
  to closeable buffer types.

## Cache and resource lifetime

Schema **5**, converter **4**, container format unchanged (**2**). Mandatory
section kind **5** holds the kiosk/logo pair with bounded length-prefixed
animated assets. Old crate schema/converter files are rejected and rebuilt by
the existing cache manager. The production builder always imports both classes;
an explicit empty vendor section is supported only for synthetic cache fixtures.

Item/world renderers share vendor textures. Native textures are released on
cache identity changes, cache loss and resource reload, including when no vendor
is visible. Partial registration failures release already owned textures.
Minecraft's texture manager owns final shutdown. Closed posed geometry is cached
per vendor asset; the small logo is re-posed for its runtime spin.

## Fidelity limitations — not a retail-perfect renderer

The existing importer omits metal/chrome packets. Body 11 has none, but logo
1143 has **seven chrome packets**: its reflective second pass is missing.
The shared mesh renderer uses textured cutout surfaces, not the retail
chrome/additive hologram material. Full-bright logo lighting is an approximation.
Retail projector beam, scan plane, four antenna-tip glow points and pulsing body
glow are draw callbacks, not imported class mesh data, and are **not rendered**.
The monitor-arm manipulators are not reproduced. Counter-spin uses a bounded
vendor-specific rest-pose layer; runtime visual agreement still needs in-game
comparison. No broad importer, shader or shared evaluator rewrite is implied.

## Verification contract

Focused synthetic checks cover closed-only pose, counter-spin/source immutability,
uniform fit and ground alignment, seven item contexts and both hands, enter/exit
boundaries, absent/dead/spectator/vertical player eligibility, growth/cadence,
cache round-trip/corrupt lengths/missing sections/version invalidation, and
texture reuse/release/retry. `RealDiscVendorTest` is an opt-in read-only check
using `-Dracmc.isoImage=...`, with no extracted/cache files written.

Separate T verification on the exact candidate remains required. Automated
pose/resource tests are **not** in-game visual acceptance. Client placement,
mining, GUI/held/dropped items, culling, reload, chunk unload/reload, multiplayer
visibility, and dedicated-server startup must be reported separately.
