# Explosive crates — standalone implementation contract

P25 originated on base `26d9ff5`. Its independently approved behavior is now combined
with the approved vendor and bolt/HUD/motion branches. Actual detonation and eligible
blast-destroyed crates pay through P24's shared reward service, including airborne carriers.
Arming/transfer never pays. See [integration status](integration.md); independent
verification of the combined candidate remains pending.

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

**Current owner-approved balance supersedes the earlier half-TNT policy.**
Players are eligible within **2 blocks**, other living mobs (`Mob`) within
**3 blocks**, measured from blast center to the closest point on their entity
AABB, inclusive at the boundary. Broadphase numerical padding does not extend
this exact boundary. Items, armor stands and unrelated nonliving entities are
not living damage targets. The final range/category checks run again after events.

Every eligible exposed target receives **5 HP (2.5 Minecraft hearts) × vanilla
sampled exposure**, before ordinary armor, enchantments, invulnerability and
damage/protection events through `entity.hurt(explosionDamageSource, amount)`.
There is **no distance falloff inside the boundary**, no TNT damage coefficient
and no point-blank amplification: a fully exposed unarmored player receives
5 HP near the crate or at the 2-block boundary. Partial exposure scales only
the raw hit; a fully blocking wall gives zero hurt, push and entity-hit callbacks.
No manual health subtraction bypasses normal protection.

**Latest owner override: knockback alone is normal vanilla TNT strength 4.**
It has a separate **8-block entity-origin envelope**, magnitude
`(1-distanceFromEntityOrigin/8) * exposure`, then normal
`EXPLOSION_KNOCKBACK_RESISTANCE` (including blast-protection equipment effects)
and the NeoForge explosion-knockback hook. Direction uses entity X/Z and eye Y
away from the blast, exactly as vanilla does for living targets. Damage remains
the independent flat 5 HP × exposure within player 2 / mob 3 AABB boundaries;
entities outside harm range can be pushed but receive **no added TNT damage**.
The previous 2/3-limited impulse contract is superseded, not an active option.

Player transport preserves vanilla's additive float3 impulse and its exclusion
of spectator/creative-flying recipients. A fixed 12-byte clientbound payload
applies the impulse to existing velocity on the client main thread. It does not
replace absolute velocity or replay vanilla explosion terrain/audio/particles.
Ordinary hurt/player movement synchronization stays managed by the engine;
nonplayer mob velocity synchronization is explicit. Matching mod builds are
required on server and client for the registered payload.

Entity wall shielding is an explicit MC protection adaptation. Crates still use
the independent 3-block, wall-ignoring query above, so a wall may protect a mob
while a crate behind that wall is destroyed/chain-armed. **ItemEntity is excluded
both before and after events**, not assumed invulnerable.
## Events, protection, terrain and integration

One Explosion context (strength metadata 4, fire=false) posts NeoForge Start before effects
and Detonate before mutation. Event removals are honored; immutable original
candidate identity/set intersection rejects additions and stale/replaced BEs.
Denied self-removal suppresses the entire emission. The service never calls
vanilla explode/finalizeExplosion, sends no vanilla terrain-destruction packet,
and never removes a non-RaC block or places fire.
The context and Detonate radius argument 8 match TNT knockback compatibility,
not a TNT damage calculation. Damage and knockback have separate eligibility
loops, and neither invokes vanilla `explode()` or terrain finalization.

This honors NeoForge explosion hooks and ordinary damage hooks, **not a guarantee
for every third-party claim mod**. Mods protecting only player BreakEvent need a
separate adapter; no fake player break is emitted. TNT gamerules do not disable
this crate-only original gameplay mechanic; mobGriefing is not used (not a mob).
Unknown protection integrations require T/owner validation before claiming support.

**Integrated P24 seam:** `Rac1CrateBlast.destroyCrate(ServerLevel, BlockPos,
BlockState, @Nullable Entity)` retains authorization/identity guards and calls
`com.github.giannigr.racmc.loot.Rac1CrateRewards.destroy`. Grounded emitter and ordinary
blast targets share this seam. Falling emitters/nonexplosives use its carrier overload,
with the same persisted reward incarnation/profile and one ledger/random-roll owner.
Arming/transfer never pays. Rejected explosive carrier admission now preserves the source.

## Visual/audio scope and remaining runtime gates

Existing imported mesh/material textures are reused. Original red predicate
`counter % 60 > 40 || counter < 15` is sampled at original-frame boundaries:
`counter=max(1,179-floor(3*(elapsedMCticks+clampedPartialTick)))`, with idle/terminal
always normal. This is 60 Hz visual sampling inside 20 TPS, not modulo60 applied
to MC ticks. The final `<15` phase is **solid red**, not an invented fast strobe.
The final original frame is held until the owner's authoritative 60-tick removal
(3s, versus original179/60 ~=2.983s). Client visuals do not advance gameplay.
They pair the synced remaining count with its processed server tick and sample
`60-remaining+clamp(clientTick-serverTick+partialTick,0,1)`, retaining the furthest
presentation phase within that object's active fuse. This predicts at most one
loaded tick, never a missed-packet/offline interval, and cannot rewind on a
partial-tick rollover, stale snapshot or backward client-time correction.
Idle/terminal state clears this transient, nonserialized presentation clock.
Unknown timestamps disable extrapolation. Grounded block entities already carry
`RacFuseTick`; falling entities additionally track that timestamp beside their
remaining count. Initial tracking and representation transfers use the same pair.

A deterministic 120 FPS sweep with packets arriving 1/4 client tick after each
boundary reproduced **7 red windows instead of 4** before this clock fix:
wrapping `partialTick` with an unchanged received count briefly re-entered red
after each of the first three off edges. The fixed path preserves four windows
across 30/60/120/144/240 FPS and five packet phases. These tests do not claim exact
network-independent phase alignment during arbitrary packet loss or time skew.

A bounded GPU temporal probe used one visible imported-texture triangle-centroid
UV sample per cardinal face, unchanged geometry and world light, four lighting
scenarios (above/opposed lights, world-light 1.0/0.25), and 366 frames per case
(360 fuse frames at 120 FPS plus six terminal normal frames). Actual shader
readbacks reproduced the old three extra red frames, with maximum off-reference
RGB error up to 0.355292 in float readback units. The fixed path had four red
windows/eight on-off edges including terminal restoration, versus seven/fourteen
before; all reference-off RGB and linear weighted-luminance residuals were zero
(assertion tolerance 1e-6). Thus the tested shader switch restores normal lighting
without a residual afterpulse; the measured defect was phase rewind, not a reason
to retune ambient/highlight coefficients. This isolated sampler is not Minecraft
buffer-order, moving-worldlight, postprocessing or live-network scene verification,
and does not establish the sole cause of the owner's reported scene.

R06's retained permissible original crate source saves the current light word and
ambient bytes before full fuse initialization, sets ambient `[200,64,64,0]` during
red phases, and restores the **saved lighting**, not constant white, otherwise
and before break. During red phases the renderer applies that RGB as its MC
**ambient term**, plus an explicit readability adaptation for white highlights:
`D=min(MC_diffuse,1-MC_ambient)`, `D'=D-0.75*max(0,D-sideBudget)`,
then `min(1,RGB(200,64,64)/255+D')`.
Imported texture/alpha, normals, directional shading, world lightmap, overlay and
fog remain; all vertex colors remain white. Off-phases switch back to the exact
preexisting stock RenderType with caller-supplied world light, so lighting state
does not leak to idle crates, item meshes or the Blaster. No fullbright/emissive
pass and no `(255,48,48)` hard multiplication remain.

**Fidelity boundary:** this is an original-rule/color ambient adaptation, not
bit-exact PS2 rendering. `/255` normalization, MC directional lights, MC lightmap,
clipping and white-highlight compression are explicit adapter choices. Original
light-word decoding, scene-specific ambient values and PS2 GS color scale/blending are not imported or
established by the retained crate-update evidence. It would be false to claim
exact PS2 luminance. The off-phase preserves existing MC lighting rather than
inventing an original normal-ambient constant. GPU probes below verify shader
compilation/linking; client appearance and resource reload still require
independent verification.

The diffuse cap retains only the contribution visible in stock MC lighting
(`MC_ambient=0.4`, so diffuse headroom is `0.6`). Without it, upward normals under
the two MC lights yield diffuse about `0.970`: even green/blue ambient64/255 then
clips to 1, making the whole red phase white and visually identical to normal.
The cap corrects that **MC adapter artifact**, not an original per-face exemption.
Under the ordinary above-facing lights, standard vertical sides and the unlit
downward face stay unchanged; upward faces/trim no longer lose all ambient chroma.

The later owner visibility revision also compresses white diffuse **above the
maximum available to any vertical face**, retaining 25% of that excess.
For horizontal projections `h0=Light0.xz`, `h1=Light1.xz`, the bound is
`sideBudget=min(0.6,0.6*max(length(h0),length(h1),length(h0+h1)))`.
This is the exact maximum of the two clamped directional contributions for a
horizontal normal. Below it lighting is unchanged; above it the slope stays
positive (0.25), preserving a continuous, monotonic normal-based gradient rather
than flattening faces or introducing a top/bottom texture mask.
All vertices still receive the same global ambient rule and source RGB.
This legibility compression is **not original PS2 lighting** and does not claim
retail pixel parity. It does not alter source textures, normals, world-light
modulation, off-phase restoration, fuse cadence, alpha, overlay or fog.

The original crate update writes one moby-wide ambient value, with no face or
material selection. The pinned public [ReRAC renderer](https://github.com/re-rac/rerac/blob/ae1e3432a0d5e21bd08d3872d3b04afef98be666/crates/rc-engine/assets/shaders/moby.wgsl)
and [lighting notes](https://github.com/re-rac/rerac/blob/ae1e3432a0d5e21bd08d3872d3b04afef98be666/docs/plan/moby_render_notes.md)
(ISC; port-source evidence, not a new retail capture) apply that instance
ambient to ordinary lit vertices before packet RGBA multipliers and texture
modulation. Glow/unlit paths can override lighting, so this is not a claim that
every original packet/texel has identical brightness or that retail pixel output
was measured. The current imported class 505 cache routes its 20 top/20 bottom
triangles and 316 side/bevel triangles through the same material 0; top normals
are +Y, bottom normals -Y. There is no top/bottom material bypass. A downward-face
flash was already predicted by `6e20c88`; the initial upward-face defect was white
clipping, followed by the readability limits quantified below.
The public port models GS color unity at 128 plus per-vertex packet multipliers;
this correction intentionally retains the accepted MC `/255` side appearance,
not a new wholesale PS2 lighting port or a guarantee of identical retail pixels.

An opt-in automated test (`RACMC_TEST_SHADER_GPU=1`) compiles the real vertex
shader plus vanilla fragment shader in a never-shown, unfocused 1×1 GL context
and samples all six cardinal normals. It uses synthetic texture/worldlight
values, not proprietary assets or the running Minecraft client. Normal tests
also verify top/bottom pass routing and unchanged normals/UVs/worldlight.
GPU coverage includes sunlight/shade world-light levels 1.0/0.25, both ordinary
above-facing lights and opposing above/below lights, plus bevel-gradient and
maximum-side-bound samples. Numeric contrast is evidence of a stronger signal,
not a substitute for the owner's in-world visual acceptance.

A further read-only GPU probe used existing imported textures with bilinear
filtering at triangle-centroid UVs and the mesh's actual flat face normals
(32 samples each for +Y/-Y, 42 each for +X/-X, 36 each for +Z/-Z). Mean relative
RGB contrast, `length(flash-normal)/length(normal)`, changed as follows:

| Directional lighting | Upward samples: before → after | Downward samples: before → after |
|---|---|---|
| Both lights above | 5.6% → 12.6% | 89.3% → 89.3% |
| Opposing above/below | 13.6% → 16.0% | 13.7% → 16.3% |

The same relative results held at world-light levels 1.0 and 0.25; absolute
brightness still scales with world light. Actual vertical-side pixels and
off-phase readbacks were unchanged. The original albedo therefore limits the
upward signal more than a generic synthetic color suggests: this is a bounded
increase, not a claim that all faces now have equal perceived contrast. No
textures were changed or exported, and no retail or owner-client screenshot
was measured. Final scene-level legibility remains an owner playtest gate.

**No beeps, explosion sound, or blast/debris FX are implemented.** Original sample
identity/audio pipeline belongs to R07/future work. Original debris/puff assets,
growth/blend/trajectory/lifetime rendering remain import/runtime gaps. No vanilla
placeholder is presented as original fidelity.

T/owner must exercise contact on six faces/no halo, supported placement vs fall,
armed fall/landing/saving/retracking, in-air expiry/no ghost block, stacked chains,
all crate kinds behind terrain, no terrain/fire/item damage, event veto/removal/
injection, damage/armor/exposure/knockback, dedicated-client synchronization and
visual flash cadence. Runtime/claim-plugin compatibility is not proven by compilation.
