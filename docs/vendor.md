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
- Compute an item fit from the **posed closed** X/Z footprint, not the bind/open
  pose. Items retain this one-block fit. Owner's interim world correction is
  **exactly 2x that baseline** on every axis, centered horizontally on the block
  and grounded at the lowest closed vertex. Original antenna-inclusive width is
  2.302903 units; item scale is .434235, world scale .868469. World antenna tips
  therefore span **2 blocks**, with total closed height about 1.098 blocks.
  This is not an arbitrary re-fit of the blue panel surfaces.
- Body and logo share the world outer scale. The logo is translated +2.95 original
  units above the original body origin, with instance scale `2.5 * growth`.
  Imported class scale is not applied again. The logo may extend beyond the
  body footprint; the renderer's enlarged culling box includes that overhang.
  Logo offset, geometry and growth all enlarge together; item drawing still
  omits the logo and does not use the world enlargement.
- Client-local multiplayer default: nearest alive, non-spectator player inside
  the current vertical envelope, measured horizontally from the block center and
  vertically from its ground origin. Survival and Creative both qualify. No
  facing or line-of-sight requirement. Each client uses the players it knows
  about; this is not authoritative server/shop state and requires no networking.
- Retail enter cylinder: radius 16, vertical distance <=8; exit cylinder: radius
  18, vertical distance <=10. Boundary is inclusive. Thresholds are scaled by
  `1 / 2.303` (about 6.95/7.82 blocks horizontally, 3.47/4.34 vertically).
  These retain the baseline proximity calibration, independent of world visual
  enlargement. Doubling the model must not silently double its activation area.
- Poll every eight **60 Hz retail frames**, quantized to Minecraft client ticks
  (three retail frames per 20 Hz tick). Growth changes .1 per retail frame,
  clamped 0..1, with render interpolation; full growth takes about 1/6 second.
  Logo joints 1/2 counter-rotate .01 rad per retail frame (.6 rad/s) using a
  vendor-only rest-pose layer and the shared evaluator. No global animation or
  importer semantics changed.
- The retail collision class has no vertex hull. The chosen Minecraft default
  is a non-occluding grounded **1 x 1 x 1** collision/selection box. It represents
  the approximately one-block main enclosure, not its antenna overhang or logo.
  It deliberately rounds the enclosure dimensions below to a normal block.
  The kiosk does not fall,
  drops nothing when mined, and has metal sound/hardness 2/blast resistance 6.
  These are local defaults, not extracted retail physics.
- The item draws only the closed body. GUI uses full light; held/ground items
  use supplied world light. All display contexts use the vanilla block-cube
  corner origin: **translate before uniform scale**, never scale the half-block
  origin offset. Block destruction buffers are supported without forced casts
  to closeable buffer types.

### Measured body versus the one-block intent

Read-only closed geometry measurements on both supported revisions distinguish
the panels and their enclosing corners from the protruding antenna arms:

| Geometry selection | Original X/Y/Z size (game units) | World X/Y/Z size after requested 2x correction (blocks) |
| --- | --- | --- |
| Panels, material 1 (552 triangles) | .999859 / 1.000040 / .999779 | .868347 / .868504 / .868277 |
| Panel-and-corner enclosure, materials 1+2 (1016 triangles) | 1.171726 / 1.182213 / 1.161938 | 1.017608 / 1.026715 / 1.009107 |
| Complete closed model including antennas/projector cap | 2.302903 / 1.263721 / 2.302845 | 2.000000 / 1.097503 / 1.999950 |

The antenna-free enclosure is thus about **1.018 x 1.009** blocks in footprint,
not mathematically 1 x 1. The panel faces alone are about **.868 x .868**.
Both facts matter: neither the original 2.303-unit antenna span nor a radius-clipped
subset of vertices is a measurement of the blue body itself. Material selections
retain their original triangles; no antenna-tip cutoff is used. The projector cap
and antenna arms are material 0; the cap is not included in the panel/corner row.
No additional tuning is applied beyond the owner's requested 2x interim change.
Collision remains a one-block body approximation, not a two-block antenna hull.

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
texture reuse/release/retry. Additional scale regressions cover exactly doubled
world placement, unchanged item cube, proportional logo offset/size, asymmetric
grounded centering, one-block collision/selection, and unchanged proximity bounds.
`RealDiscVendorTest` is an opt-in read-only check using `-Dracmc.isoImage=...`,
with no extracted/cache files written. It asserts the actual supported revision:
NTSC logo 741 vertices, PAL logo 724; both have 826 triangles, with posing, spin,
body bounds and cache round-trip assertions exercised on each disc.

Separate T verification on the exact candidate remains required. Automated
pose/resource tests are **not** in-game visual acceptance. Client placement,
mining, GUI/held/dropped items, culling, reload, chunk unload/reload, multiplayer
visibility, and dedicated-server startup must be reported separately.
T11's previous verification targets baseline `8b0dfc1`, not this corrected tree;
affected scale/collision/item/runtime checks must be rechecked. No active owner
client was controlled, closed or reloaded for the correction.
