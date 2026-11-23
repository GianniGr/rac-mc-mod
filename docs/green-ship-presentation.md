# Green travel ship: P46 presentation checkpoint

The sole retail hull in scope is class 531 (`green_ship`), not Clank's pod or
Veldin's class 530. All art remains local to a verified player disc. The
placeholder ship, safety checks, camera modes and controls must keep working
when any optional art or effect cannot be prepared.

## Read boundary and evidence

The existing validator accepted NTSC-U v1.00 and PAL v2.00. The first probe
read three eight-byte TOC records per disc. Sector-derived extents are:

| Revision | `spaceships[1]` | matched takeoff | matched landing |
| --- | --- | --- | --- |
| NTSC-U | `3844034560 + 360448` | `3845226496 + 229376` (1) | `3846250496 + 348160` (5) |
| PAL | `3844233216 + 360448` | `3848249344 + 200704` (11) | `3849138176 + 301056` (15) |

The second probe read only 16 bytes at each hull start and 560 bytes at each
scene start, 2,272 bytes in total after validation. It confirmed the following
candidate reads for a *separately authorized* payload check:

| Revision | Class slice | Normal PIF header, CLUT and mip 0 | Takeoff chunk 0 cap | Landing chunk 0 cap |
| --- | --- | --- | --- | --- |
| NTSC-U | `3844034624 + 205824` | `3844289216 + 66608` | `3845228544 + 49152` | `3846252544 + 51200` |
| PAL | `3844233280 + 205824` | `3844487872 + 66608` | `3848251392 + 43008` | `3849140224 + 43008` |

Numbers are absolute byte offset plus length. Both hull headers had relative
normal class/texture offsets `0x40`/`0x3e2c0`, and extra class/texture boundaries
`0x32440`/`0x53b00`. The extra data lies outside the proposed reads. Both takeoff
directories had seven nonoverlapping valid chunks; both landing directories had
eight. The chunk-0 caps are padded spans, not exact WAD lengths. Read and validate
the first 16 bytes inside each selected span before reading its exact remaining
compressed bytes; inspect only the decompressed actor directory/header and end
tick. Actor offsets are not in the on-disc table, so their exact values are
unknown. Chunk 0 suffices for the initial cast and scene end tick; later chunks
need a separate reason, and no audio is needed. The hull check needs only the
normal class and PIF slices; neither class 535 nor its texture is required.

The format source is ReRAC `ae1e3432a0d5e21bd08d3872d3b04afef98be666`,
`rc-formats` `moby_spawn.rs`, `gadget.rs` and `scene.rs`. Its
`2026-10-01-planet-travel.md`, `2026-10-02-ship-travel.md`, and
`rc-game` `travel/space.rs` and `travel/ship.rs` establish the source matches.
Both approved retail slices decoded identically on NTSC and PAL: 63 high and 21
low packets, **zero metal packets**, 5,963 decoded high-LOD vertices, 5,014
triangles, one material (0), a 256×256 PSMT8 texture and 27 rig joints. List 0
is `[0,1,2,3,18]` with target `[18]`; the separate canopy glass callback at
`0x2a70a8` therefore attaches to joint 18. Sequence 1 at `0x190` has one
source frame; sequence 2 at `0x2a0` has two. Both decode source frame zero at
time zero. Sequence 2 is the closed flight basis, not a verified hatch track.
The CPU-posed meshes fit the fixed P45 horizontal envelope at 0.20 blocks per
source unit (maximum radius 0.777 blocks); this is not visual fit approval.

Chunk 0 verified the following (actor offsets are relative to the decoded
chunk, not the ISO):

| Disc / entry | Stored WAD bytes | Scene end | Ratchet slot 0 | Ship slot 2 |
| --- | ---: | ---: | ---: | ---: |
| NTSC takeoff | 48,275 | 598 | `0xc50` | `0x172a0` |
| NTSC landing | 49,264 | 738 | `0xc40` | `0x186d0` |
| PAL takeoff | 41,617 | 498 | `0xa50` | `0x13630` |
| PAL landing | 42,081 | 614 | `0xa40` | `0x14710` |

The matched green retail scenes contain class 531 directly at slot 2 and
Ratchet class 0 at slot 0. The generic slot-0/class-533 substitution in the
reference source does not describe these four chunks. The takeoff directory
lists a class-535 actor, but its extra class or texture was not read or reused.
Chunk 0 contains only the opening track (49 NTSC / 41 PAL source actor keys).
It cannot certify later authored motion or an exit. NTSC cuts 103/243/363/503
are scene timing references, not animation keys. Exact later motion would
require a diagnosed key interval and separate bounded chunk approvals; the
Minecraft-only Steve adaptation does not require those reads.

## Presentation seam

- P45 owns the replicated phase, episode/transition start and duration, pilot
  identity, and real boarding/seat/chosen exit anchors. Flight FX need actual
  liftoff/approach/settled signals; reaching the seat is not liftoff. Late
  observers should sample the current phase, not replay phase starts.
- The ship-local draw will use the current fitted hull pose and the terminal
  glass chain at that same pose. Normal hull, reflection/glass materials and
  FX need independent verification. Class 531 has no normal metal packets;
  successful hull decoding cannot certify the separate canopy reflection pass.
  `GreenShipHullRender` prepares the verified mesh off-thread under a
  generation-fenced media lease and uploads only the normal hull texture on
  the render thread. It leaves the placeholder available when preparation fails.
- Render the real player once: blend from the server's boarding origin through
  a hull-local sill/grip waypoint into the seat, then animate the same skin in
  the seat. Exit is a separate Steve climb-out and step onto the chosen exit
  anchor, not the boarding animation reversed or a claim that the original
  landing (including the tick-240 revisit start) contains that exit. Rendering
  must not move authoritative player feet or force a camera perspective.
  The scratch combination routes the native classic/slim player render through
  `GreenShipPlayerRender`, which uses the shared access clock and vanilla
  passenger identity for both pilot and observers. The hook applies pitch to
  the body, arms, legs and matching skin layers after native setup; it uses a
  private draw stack and restores mutated pitch in `finally`, including nested,
  cancelled and failing renders. The sill, facing, and all Steve motion are
  Minecraft adaptations; the hook does not move authoritative feet, change
  first-person hands, or force perspective/camera yaw.
- Parked glow continues through ordinary boarding and exit. Actual takeoff can
  use the 360–503 ramp and only enables the eight additive FX5 flame quads after
  420, with the class-531 source anchors at `0x1bde30`. Landing exhaust (vent
  source `0x1be0e0`, two bursts of six) stops before reference tick 200, followed by flame fade and
  parked glow. These are reference beats, not copied Minecraft durations.
  No boarding flash, screen takeover, or world-space puffs without a verified
  effect and anchor. `GreenShipFxGeometry` prepares ship-local quads with stable
  landing flicker. `GreenShipFlamePass` consumes them in a depth-tested additive
  pass without writing depth. A verified FX5 texture and live hook are still
  required; this pass has not been exercised on a GPU.
- The `+0x13c0` references 6/3/0 are mixed transition streams, not isolated
  engine or canopy cues. No audio is bound, read, exported, or bundled.

### Outstanding inputs

- **Canopy glass:** the separately approved Novalis metadata chain verified
  raw overlays on NTSC and PAL (48 bytes per disc). Six NTSC selectors
  (24 bytes) then identified 39 points and normals, 24 quads, and GS tint
  `0x30807060`. The pinned OpenRAC section map and a direct `.data` header
  read agree: NTSC `3876693432 + 16`, kind 1, VA `0x166480`, payload length
  677,456, entry `0x259c40`. The three verified slices are points
  `3877057800 + 624`, quads `3877058424 + 384`, normals `3877058808 + 624`.
  All homogeneous vectors are finite with `w=1`; all indices are within
  `0..38`. No canopy bytes are bundled. The runtime reads only the pinned
  header and geometry under the same media lease, keeps the result in
  memory for that generation, and borrows ready, identity-matched
  level-01 FX21 without requesting textures. The normal hull and the
  glass on joint 18 share sequence 1 frame 0; there is no guessed hinge
  animation. Missing art or invalid canopy records leave the normal hull
  intact and omit the glass. PAL explicitly uses that no-glass fallback
  without any NTSC canopy reads. The PAL callback has been located at
  `0x2a82a8` in pinned OpenRAC metadata, but selector and geometry
  provenance are not verified there. Near/far reflection freezing,
  fade and live visual fit remain unverified.
- **FX timing:** P45 `groundSerial` is an exit authorization token and
  also clears for access changes or grounded motion. `PILOTING` starts
  before liftoff. Neither can drive FX. The minimal presentation seam is
  the two separate facts supplied by P45 `768cba2`: transient
  `engineBeat()` (`LIFTOFF`/`TOUCHDOWN`, serial, server tick) and current
  `engineState()` (`APPROACH` alone enables pre-contact exhaust). The
  ship renderer consumes edges once, drops edges without material and
  never reconstructs missed emissions after tracking, pauses or clock
  gaps. Leaving proven approach clears its puffs immediately; accepted
  touchdown also vetoes an approach state that is still in transit.
  Boarding, exiting, and missing engine verification do not play flight FX.
  FX5 and the first frame of particle definition 23 come only
  from the existing matching level-01 effect catalog (`type02` with
  `def=-1`); no replacement sprite or audio. Block lighting, world-space
  billboards and event-relative timing are Minecraft adaptations.
- **Later retail keys:** no extra chunks required for the Minecraft
  adaptation. Source cut markers do not establish where joint 18 moves.
  No later chunk is proposed without detecting a specific verified channel
  interval first; the whole linked scene stays out of scope.
