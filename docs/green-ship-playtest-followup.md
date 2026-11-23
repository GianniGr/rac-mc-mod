# Class 531 playtest follow-up

The owner saw the green hull, canopy and underside fire in `f527c8a`.
The isolated proportional retest combines inventory art, rear thrust,
adapted audio, the measured scale and the P45/P46 cockpit refit. The
older `4686d4a` and `0036c81` candidates are baseline-scale references.
Offline checks are not live acceptance.

## Owner feedback follow-up: camera, label, chair and side access

Rear F5 on the proportional ship requests about **9.75 blocks** with
the usual forward-looking orbit: measured aft hull Z `−4.279`, native
pilot eye Z `2.12`, and `3.35` blocks of framing beyond the tail.
It uses the interpolated rider eye and ship pose; no extra pivot,
camera rotation, or forced view switch. The requested distance varies
with orbit and is shortened before unloaded space and world colliders;
vanilla visual collision still runs afterward. Front F5, first person
and on-foot cameras are unchanged. In a cramped spot the terrain can
still push the camera close to the ship. Unnamed ships no longer render
the default world-space label; custom names keep vanilla visibility.
The item name and boarding/error messages are unchanged.

### Captain's chair and cockpit materials

The original class-531 fixed pose has mirrored seat-like surfaces.
At local `X=±.60`, `Z=2.12` and `2.40`, the uncut hull has a pan
at Y `1.759` above the cockpit floor at Y `1.315`; the existing cut
removes both in the center region. Pinned ReRAC `travel/mod.rs`
distinguishes Ratchet's `+0.45` source side and Clank's `−0.45` in
fly-away, placing the driver's chair search near `+0.599` here.
This supports the side assignment, not an original boarding/eye key.

A narrower side cut preserving that pan was tested at candidate body
origin `(0.60,1.23,2.12)`, facing `+Z`. It **did not pass**:

- the eye ray reaches opaque hull at `1.150` blocks before glass at
  `1.534` blocks;
- the closest windshield point is `0.642906` blocks from the head
  pivot, below the `0.647171` padded free-look requirement;
- the sampled access column intersects **123 posed opaque triangles
  and 7 glass triangles** in sequence 1 frame zero (178 opaque in
  sequence 2).

Do not move the authoritative anchor, camera eye, or access endpoint to
that chair yet. P45 handoff: preserve the original pan where possible,
clear the near-side console/window and ingress locally, then solve
actual hip/sole contact with both player layers before publishing one
new shared anchor and facing. Do not further enlarge the hull or reuse
the fly-away origin as a mount endpoint.

The audited source atlas has **65,536 fully opaque texels** and both
the uncut and cut meshes use only material 0. UVs are finite, with U
`−1.990234…−1.002197`, V `.006348… .989990`. The follow-up translates
the entire verified U tile by `+2` (no vertex-wise modulo or texture
replacement). This is equivalent under repeat sampling and avoids
depending on negative UV sampler behavior. It is not proof that sampler
state caused the screenshot. Known differences in the visible cockpit
are the uncapped cut edges, no-cull rendering that exposes the backs of
opaque panels, removal of original interior surfaces, and the explicitly
authored wool/iron supports. There is no evidence of an absent hull
texture, and no blanket alpha/culling change was made.

### Side boarding diagnostic and live retest

The rendered beam ends near `X=±2.884` but the yaw-safe terrain sweep
box extends to `±5.20` at yaw zero. A `.60`-wide standing player
typically needs at least `|X|=5.50` to be completely outside that box.
By contrast, the bow reaches Z `5.006`, only `0.194` short of the box.
The apparent front-only behavior can therefore be reproduced by standing
just beyond visible geometry: visible-wing-side X `3.20` fails the
outside-stand gate, while X `5.70` alongside cockpit Z `2.12` clears
that gate. Other route checks still apply. The prior headless test
boarded from X `5.70`, Z `0`; the new along-chair case is not a live
click result. Do not shrink the Turbo terrain sweep to
make the click easier; P45 can design a separate validated approach
marker/corridor using the real world-space hull and final chair anchor.

For a coordinator-run retest, enable `-Dracmc.shipMotionTrace=true`
on the server, try both sides at a documented ship yaw and at the two
stand distances, and capture `boarding_probe` (`reach`, `outside_stand_or_support`,
`center_line_of_sight`, `access_leg_obstructed` or `accepted`). If no
server row appears for a failed aim, investigate vanilla client picking
before changing server safety. Compare the cockpit from the same two
angles and report whether the wrong surface is a cut edge, an
authored support, or the original hull atlas. Toggle all three
camera views in open air and near a wall/chunk boundary; check orbit,
stationary seat eye and Turbo pilot/observer tracking. Check unnamed
and explicitly renamed ships separately. No client was launched for
these checks.

### Footwell comparison and finish

Using the same NTSC class-531 pose, origin, and ray samples in each
comparison, the first parked oblique probe (650 rays from
`(.40,3.15,1.80)` to the footwell) gave:

| Draw geometry | Ground-reaching rays | Rays hitting original geometry below Y `.98` | Rays first hitting a support |
|---|---:|---:|---:|
| Original uncut hull | 0 | 0 | 0 |
| Original hull with the old cut only | 158 | 492 | 0 |
| Old cut with wool/iron supports | 101 | 453 | 96 |
| Shallower cut, opaque liner and finished supports | 0 | 0 | 96 |

The result is geometric, not a reproduction of GPU output or the
owner's exact viewpoint. The supports produce the gray wool and
white iron visible surfaces, but do not produce the hole: they cover
part of an existing geometric gap. Neither alpha nor a missing texture
is needed to reproduce the exposed ground. A vertical grid and two
oblique grids (650 samples each) returned zero misses with the new
liner in both tested frame-zero poses. The source atlas, UV handling,
winding and no-cull policy are unchanged from `256eea5`.

The opaque cut now starts at Y `1.27`, preserving original surfaces
below it, including the center floor near `1.234`. A thin mod-authored
liner with top Y `1.015` backs the remaining gap below both the original
floor and the Y `1.30` body-route column. This is local coverage, not a
new hull collision surface or a claim of a watertight original mesh.
Wool and iron blocks were replaced by lit, opaque meshes in the **same
contact boxes**, using a mod-authored olive cushion and dark pedal
finish. The pad, native seat, shared eye, scale, and access path do not
move. The +X chair is still not cleared; P45's separate side-access
checkpoint `cc64887` is not part of this draw-only build.

### Retail landing gear: evidence only

- Retained NTSC/PAL class-531 sequence 1 frame 0 reaches baseline ship Y
  `−0.000128` and sequence 2 frame 0 reaches `+0.074091`, with the same
  maximum radius and top. At the current scale that raises the lowest
  mesh point by about `0.494` block. These are two distinct static
  hull poses, **not** a joint assignment or proof that all three feet
  move or disappear. The old scale audit's `parked`/`closed` names are
  labels for the sampled poses, not parts classification.
- Pinned ReRAC [`fly_away_setup` and `fly_away_body`](https://github.com/re-rac/rerac/blob/ae1e3432a0d5e21bd08d3872d3b04afef98be666/crates/rc-game/src/travel/space.rs#L945-L1048)
  set sequences 1 and 2 and advance the blend on the branch with a
  configured fly-away path, alongside hull translation, pitch, yaw
  and fade. This is evidence of an animated **whole-hull pose blend**
  in that branch, not evidence that a particular gear limb is visibly
  retracted in the matched green takeoff/landing shot.
- The interplanetary flight has a different
  [`flight_sequence`](https://github.com/re-rac/rerac/blob/ae1e3432a0d5e21bd08d3872d3b04afef98be666/crates/rc-game/src/travel/space.rs#L223-L239):
  sequence 2 frame 0 is the static base; only streamed quaternions
  0–3 are replaced. That base can present a raised pose without a gear
  motion during flight. The same mode separately hides/scales actors
  in its first-arrival variant. At the end of a takeoff/landing scene,
  the streamed actors are deleted and the world hull unhidden
  ([`scene_end`](https://github.com/re-rac/rerac/blob/ae1e3432a0d5e21bd08d3872d3b04afef98be666/crates/rc-game/src/travel/space.rs#L904-L927)).
  Hiding, changing actor, fading, selecting a static pose and moving a
  specific gear joint are different effects.
- Retained scene records verify class 531 at slot 2, with 49 NTSC or
  41 PAL opening keys; they do not report gear channels, intervals
  or visibility. Joint 18 is the glass attachment; source joint lists
  1/2 attach engine trails, not feet. ReRAC's
  [gear/engine vent coordinates](https://github.com/re-rac/rerac/blob/ae1e3432a0d5e21bd08d3872d3b04afef98be666/crates/rc-game/src/travel/ship.rs#L74-L80)
  identify exhaust locations only. The checked OpenRAC
  [Novalis program metadata](https://github.com/OpenRAC/rac1-decomp/blob/84bd565ff5d079384f1b673ac8e39ea3e06c68b7/config/versions/us/programs.tsv)
  confirms the program/section source pin, not a gear rig or a visible
  deployment sequence.

Next gear evidence, only if separately requested: first tag the three
lowest skinned components by joint from already-approved class-531
geometry and compare their **local** motion between sequence 1/2 frame
zero after removing the root transform. If that maps actual gear, inspect
only those joint channels in the already-bounded matched slot-2 opening
payloads, not whole later scene chunks. If the openings don't contain
movement, report that gap rather than guessing a later key interval.
No gear probe read or gear implementation was added in this follow-up.

### Combined Ship Flight and roof/seat restoration

`9b2b035` is the exact source composition of Ship Flight `188f5f1`
and the draw-only `08da71c` cockpit. It includes the side
boarding/dismount and pedestrian hull collision work without shrinking
the flight sweep, Creative empty-main-hand removal of a targeted
unoccupied ship, and Turbo `30 / 12 / 10` blocks/sec. Camera, nameplate,
UV and liner changes are retained. The centerline seat and native eye
stay as before; the +X captain-chair move remains blocked.

A separate mesh audit compared the intact verified frame-zero hull and
the active cut at the same scale and coordinates. At local
`(X,Z)=(+.60,2.12)`, original surfaces at Y `1.315` (floor), `1.759`
(pan), and `3.335/3.511` (roof/frame) all disappear in the cut. The
mirrored pan shows the same removal. At `X=+.60`, Y `1.82/2.02/2.22`,
rear surfaces near Z `2.031/1.984/1.918` are also present in the
original and absent in the cut (matching backrest geometry).
This is removed geometry, not an intact roof or chair obscured by the
liner, UVs, or authored pads.

The follow-up shows the **uncut original hull** when parked with no
rider and no route; the liner and authored supports are not drawn on
top of the restored paired seats. When occupied or using an active
route, the aperture is reduced to X `±.68` (from `±.86`), retaining
more side roof/frame and seat geometry without entering the verified
`±.59` access column or padded `±.647171` head envelope. The original
windshield, eye ray, floor height inside the aperture, and seat/contact
transforms are unchanged. The roof switch is a **local Minecraft
adaptation**, not original animation or a change in flight pose. Missing
media still uses the mod-owned open fallback; PAL uses its existing
no-glass fallback.

Do not close the entire roof during access or flight with the current
seat: the closest uncut opaque surface to the seated head pivot is
`0.409811` blocks in both poses versus `0.647171` required. The former
cut cleared this, and the strips restored by narrowing are at least
`0.68` blocks from the centerline in X. The live retest still needs
parked top-down agreement, in-seat free look with both skins, rollback
without a premature roof close, and side-route checks. The new side
route should use the same authoritative anchor until the separate +X
chair fit passes, rather than moving the player to match only the
restored parked art.

## P45 handoff: choose one cockpit profile, not a renderer multiplier

Retained parked hull measurements at `.20` blocks/source unit are
Y `-0.00013..0.60007`, horizontal radius `0.77682`; closed frame-zero
opaque hull Y `0.07409..0.60007`. NTSC and PAL agree for the opaque hull.
Production uses a yaw-independent `1.8 × 2.45` collision/placement box,
seat body origin `(0,.54,0)`, and a render-only boarding sill `(0,.65,.20)`.
Consequently the seat origin is already within `0.061` blocks of the top
of the entire opaque hull. A standing-pose adult eye at `seatY + 1.62`
is about `2.16`; the skin's head is higher, and the bent legs and arms
extend forward. Bare skin head width is `8/16` block and classic/slim
arms are `4/16` and `3/16` wide before renderer scaling and outer layers.
These are bounds inputs, not demonstrated clearance.

The pinned source hatch is forward `+1` source unit (`+0.20` block), on the
centerline; this is a longitudinal search station, not a seat or exit
endpoint. Green ship bow is local `+Z`; verify the seated body faces that
way while the pilot retains free look. Ratchet's fly-away rider point is
not an original boarding or driver-seat key. The retained matching scene
does not establish one. Use the in-lease cockpit dimensions plus the
owner's side/front views to choose a centerline cockpit location; label
the result **Minecraft cockpit fit**, not a recovered original seat.
The retest logs closed-glass Y/Z bounds and centerline roof height at the
old origin and the hatch station from the existing NTSC lease. A missing
roof intersection is not clearance. No new geometry read is needed for
those measurements.

The owner selected source character-to-hull proportions as the sizing
rule. The old `.225` collision-envelope comparison and first passing
clearance sample below are diagnostics, **not** sizing targets.

P45 review request: use one shared ship-local seat **body origin** in the
mounted entity and access snapshot, derive eye height from that body
origin consistently on both peers, and use that same origin for the
third-person pose and board/exit joins. Scale the sill and configured
render/FX attachment points with the same profile. Do not scale the
server-selected exit destination, which needs a new safe-space search
outside the resulting hull. Keep camera rotation under player control;
compare first-person eye location to the third-person head and test all
four headings, classic/slim, and crouch/descend. Verify no floating feet,
face/roof/arms/legs clipping, or jumps at phase joins. Also retest pad
placement, ceiling/pane sweeps, unloaded chunks and safe dismount.
Do not promote a fit until these side/front and pilot/observer checks pass.

### Ratchet-to-Steve scale evidence

Use a standing, neutral visual stature for each character, not seated
height or the difference between two projected pixel boxes. Nominal
bare Steve (classic/slim) spans 32 model pixels, at `0.9375 / 16`
blocks per model pixel: **1.875 blocks from sole to bare crown**. A hat
layer and renderer origin offset affect clearance, not the neutral
stature reference. The standing gameplay body is separately 1.8 blocks
high, with an eye 1.62 blocks above the body origin.

- Retained NTSC Novalis landing chunk 0 identifies class 0 at actor slot
  0 (`0xc40`, position track `0xf5f0`) and class 531 at slot 2
  (`0x186d0`, track `0x1bed0`), with 49 keys each. PAL independently
  matches those classes and has 41 keys. These are decoded offsets and
  timing metadata, not positions, visible stature or an original seat.
- Pinned ReRAC `ae1e343`, `rc-game/src/travel/space.rs` `scene_tick`
  and `rc-engine/src/travel_render.rs` `upload_actors` establish the
  common ship-local basis: each actor's own translated track in the
  ship's rows and yaw, plus the actor's streamed skeleton pose and
  effective class scale. The camera has a separate ship-local eye and
  perspective tangent. Comparing screenshot heights would mix actor
  depth, camera angle, and pose. For a scene sanity check, transform
  both actors at the **same key** before taking extrema; do not replace
  neutral stature with the standing-looking frame or treat a
  flying-away rider point as the live seat.
- The verified sequence-1 opaque hull measures approximately
  **6.97298 forward × 4.33138 across × 3.00098 up**, after class scale,
  in RaC world units; the max horizontal radius is **3.88411** from the
  hull origin. The joint-18 glass reaches **2.50566** above that origin.
  These are local 3D geometry extents, not measured dimensions of the
  posed hull in the opening scene.
- Pinned `rc-game/src/hero/physics.rs` documents mode-0 normal Ratchet's
  capsule centers at `+0.70` and `+0.80` from the root and radius `0.45`
  in those same world units. The capsule spans `+0.25..+1.25`, **not**
  the soles-to-crown range. As a ground-datum comparison only, mapping
  `1.25` to Steve's `1.80` gives `1.44` blocks/source unit (`7.2×`
  baseline): **10.041 × 6.237 × 4.321 blocks** and **11.186 blocks**
  yaw-safe width. The earlier seat scan did not pass at `7.2×`; this
  comparison is neither a visual ratio nor a fit approval.

The approved NTSC Novalis measurement now supplies **R = 1.408195999**
RaC units. This is the high-LOD class-0 body in neutral gameplay idle
slot 0, key 0 (pinned `rc-game/src/hero/common.rs`, `idle_seq`), not a
scene silhouette. After the class scale `0.145833328`, the source-up
extrema are `-0.030035760` at the supporting sole and `1.378160238` at
the bare body mesh crown. The 111-joint body has no metal packets; no
separate helmet, item, or scene attachment was measured. The streaming
probe read **12,521,658 compressed body bytes**, stopping at ISO
`3,893,824,552` exclusive and decoded core offset `21,314,144`. It
selected class-0 data at `11,645,312` (144,288 bytes) and 1,264 bytes
for the idle header, key pointer, and first key at `21,312,832`.
Only scalar evidence was retained.

Thus **`k = 1.875 / R = 1.331490788` blocks per RaC unit** (`6.657454×`
baseline). The ratio sets the hull to **9.284 bow-to-stern × 5.767 beam
× 3.996 high**, with **10.343** yaw-safe width before margin. The
windshield reaches about `3.336` blocks above the hull origin. These
dimensions describe the exterior before collision margins.

At this fixed ratio a CPU centerline search against the verified closed
opaque hull and joint-18 glass found **1,338** forward eye/sightline
stations. **328** also cleared the neutral head box. None of those
cleared the conservative free-look envelope. None cleared both full
classic/slim bodies at `.025` blocks of padding, even when testing
knees-down, closer-elbow, and varied torso poses. The search used `.005`
baseline-block increments, sampled only the centerline, and does not
prove every possible pose is impossible. It does rule out promoting
the tested seats, and there is still no verified standing approach or
access route.

**P45 review:** keep `k` fixed. The next option is a separately
reviewed, Minecraft-authored local cockpit opening and seat/footwell,
not a larger outer hull or smaller player. Measure that modified
cockpit with real posed meshes and free head rotation; use the same
body origin for eye and model and test the new approach, sweep and exit
against the real terrain collider. No camera constraint or unmeasured
sill. Bring the exact interpolation delta `725cba6` and its optional
motion trace into that single runnable candidate once the fit passes;
do not present a baseline or interpolation-only build as the
proportional retest.

### Authored open cockpit and proportional retest

At the fixed ratio `1.331490788`, the initial refit subtracted only opaque triangles
inside X `[-.86,.86]`, Y `[.98,4.10]`, Z
`[1.25,3.19]`, in ship-local blocks. Clip crossing triangles at its
faces; preserve exterior UVs, source scale, and the joint-18 windshield.
The untouched extrema after clipping are X `±2.883597`, Z
`[-4.278368,5.006094]`, top `3.994919`, yaw radius `5.171661`.
This is an open-top Minecraft adaptation, not an original hinge.

The trial body origin `(0,1.30,2.12)`, facing bow `+Z`, puts the vanilla
standing eye at `(0,2.92,2.12)`. The original windshield intersects
that forward ray at Z about `3.775`; no intervening opaque triangles
remain. The cut leaves **zero** opaque or windshield triangles in the
sampled padded body/access column (X `±.59`, Y `1.30–4.15`, Z
`1.49–3.04`) in both verified frame-zero hulls. Glass that intersects
the wider stencil outside that column is left in place. This
is geometric room for the tested classic/slim boxes and head envelope,
not a live player-model/skin-layer check or proof of footing.

P45 review values: proposed square yaw envelope `10.40 × 4.05`; above-roof
sill `(0,4.15,2.12)`; an outside stand about `5.70` blocks from center
on a cardinal approach. The auth and presentation paths would need
the same waypoints: rise outside the hull, cross above the roof,
descend through the open cockpit, and reverse on exit. A diagonal
from the outside stand to the seat is not acceptable. Isolated server
tests check terrain obstruction on every proposed leg and use the
proposed envelope in a real Turbo movement attempt at an unloaded
chunk halo. Neither installs that envelope for normal gameplay nor
constitutes a full mount/dismount test for this layout.

The authored interior uses two cushion pads and two raked
pedals. They are defined in each leg's model-pixel basis (not hull
pixels), with contact planes at `z=2.25` for the pants underside and
`y=12.25` for the sole. Tests submit the baked vanilla classic/slim
models and their inflated skin layers and check their transformed
vertices: both legs contact both intended surfaces without penetration,
and bare legs have a `0.25`-pixel (`0.01465`-block) skin-layer gap.
The rest of the tested body and clothing clear the supports. Every pad
corner is inside the local opening and more than half a block below the
fixed forward eye ray. The authored seat pose suppresses native limb
splay only while rendering the pose; head, hat, player eye, camera and
subsequent model state are not changed.

**Free-look gate:** the actual baked hat has a rotation-independent
radius of about `0.622` blocks, larger than the previous glass proof's
`0.590` half-width. With padding, the in-memory verifier checks the
center-to-triangle distance at `0.647` blocks and requires a forward
windshield hit. On the previously approved NTSC class and glass slices,
the nearest triangle was `1.168342` blocks away in both poses. The
forward ray reached the windshield after `1.655359` blocks without
hitting opaque hull. PAL remains explicitly no-glass.

**P45 path handoff:** keep the verified seat `(0,1.30,2.12)` centered,
the roof sill `(0,4.15,2.12)`, and take the actual server-selected
outside anchor rather than the nominal `5.70`-block example. Expose the
same ordered legs, phase start and tick boundaries in both peers.
Square the limb pose before descending into the portal, keep it through
the seated join, and keep it until the figure is above the roof on
exit. The composed `b185475` path has three server-confirmed eight-tick
legs. The renderer uses checked progress and matching serial for limb
posing, with no second translation of the vanilla-interpolated rider.

The isolated retest enables the measured profile. Its media-independent
open fallback keeps the same supports and clears the portal and eye ray.
Check native eye/mesh
alignment, third-person pad contact with classic/slim, clean ingress
and exit from an ordinary pad, and live Turbo tracking with
`-Dracmc.shipMotionTrace=true` when useful. `725cba6` stays composed
exactly; no interpolation-only build is a proportional-playtest pin.

### Earlier closed-cockpit clearance sweep (P45 `561d30a`)

The verified NTSC frame-zero opaque hull at `.20` has X
`[-.433138,.433138]`, Z `[-.642643,.751953]`, and yaw radius `.776823`.
The joint-18 glass has X `[-.204554,.204554]`, Y `[.347911,.501132]`,
Z `[.194265,.604974]`. Glass bounds are identical at the two available
frame-zero poses; they do not establish an open hatch.

A CPU centerline sweep used nominal adult eye height `1.62`, forward
`+Z`, and the current posed classic/slim head, jacket, sleeves and pants,
including the outer layer boxes. The scale grid was `1.0–8.0×` at
`0.1×` steps, with seat Y `0–.45` and Z `-.12–.54` in `.01` baseline
block steps. An eye station needed glass in front and no earlier opaque
intersection. With a `.025` block padding on the posed boxes, no sampled
seat below `7.4×` passed both models and the eye ray. The first static
match at `1.48` blocks/source unit had seat `(0,1.702,2.368)` and eye
`(0,3.322,2.368)`, with the windshield at Z `4.159`. This is a grid
result for the current seated pose, not a global optimum, a source
pilot key, or a recommended scale.

The full-mesh recheck at that station passed all six padded parts on
both skins at `.025`; at `.040` only three of six passed on each. A
conservative free-look head envelope did not clear. The resulting hull
would be about `11.497` blocks wide in the yaw-safe collision envelope,
`10.320` blocks bow-to-stern, and `4.440` blocks tall before gameplay
clearance margins. A `.60`-wide standing player would start a cardinal
outside approach about `6.25` blocks from the center using P45's berth
rule. At the seat's hip height, the straight side path meets opaque
surfaces at X about `±1.32` and `±1.76`; the upward ray above the seat
also meets opaque hull. None of these rays establish a traversable sill
or an ingress or exit path. The source hatch marker is only a station
reference, not authorization to invent an opening track.

**Decision:** retain baseline scale, body origin, sill and server
envelope. The renderer now consumes the shared profile for world source
scale and the access sill; item framing stays independent. Ask P45 to
review the nominal eye ray vs. actual player pose/eye and the sweep and
dismount implications before any larger collider. To proceed, prove a
pose/access route at an acceptable footprint, or seek owner approval
for a different cockpit adaptation. For live acceptance compare front
and side with both skins, forward sightline and free look, then the
board/exit joins and footing at all headings.

## Rear exhaust and audio provenance

- Pinned ReRAC `ae1e343`, `travel/mod.rs` and `travel/ship.rs`: class-531
  wing-tip engine trail anchors are `(forward, side, up) = (-3.5, ±1.5,
  1.2)` in the fly-away. The cinematic trail uses FX19, or FX0 in the
  flight variant where the samples follow engine joint lists 1 and 2.
  It is distinct from the six underside vents and their type-2 puffs.
  The retest uses the known rear locations and the already verified FX5
  flame material as **continuous Minecraft jets**, not a retail trail
  recreation. No new texture load; no change to the underside pass.
- `travel/space.rs` documents class-indexed scene streams; R20 identifies
  `40006`, `40003`, `40000` only as scene streams. Source also shows a
  flight-transition bank with variant-indexed plays, not a verified
  discrete class-531 engine cue/loop. Title-scene and fighter sounds are
  not evidence for those cues. The retest uses only vanilla fire charge,
  piston and minecart sounds as a separately reversible adaptation.
- Rear jets require verified flight and actual forward displacement under
  current forward input without brake. Turbo uses P45 `e2c077c`'s resolved
  `boostThrust().forward()`; normal thrust uses the same server-side
  displacement predicate. Resolved vertical Turbo can modulate flight
  audio independently without firing the rear jets. P45's exact Turbo
  delta supplies the new caps; this presentation layer changes neither
  controls nor caps and does not invoke physics from the client. The transient
  audio edges use P45's existing tracked-only liftoff/touchdown packets;
  a late observer can hear current flight but never a historical edge.
  Use the same candidate on both client and server (engine protocol v2).
- Owner retest: Creative/hotbar with media ready and unavailable; rear
  jets from back/side at normal throttle and boost, then brake/idle/pane
  and land; verify that the previous underside effect is unchanged.
  Compare pilot and observer loudness and stop behavior across touchdown,
  tracking range, and disconnect. No client/GPU/audio playback was used
  for this check.
