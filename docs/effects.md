# Original effects: bounded provider slice

This is an **unwired provider**, not a replacement for all original particles.
It does not change Blaster gameplay, crate transactions, healing, sound, the media
gate, RACM, or any existing renderer registration.

## Implemented boundaries

- `asset/effect`: generic particle/FX core-bank parser; namespaced source refs;
  linear indexed/CSM1 decoding through the existing decoder; complete stored
  particle definition header, offset table, alignment padding and byte blob.
  Zero offsets resolve to blob start. Stored spans are **not** flipbooks/FPS.
- `Rac1EffectMeshImporter`: generic static moby meshes/textures, original scale
  and material slots. No crate definition/registration or unit-fitting. Controllers
  305 and 806 correctly have no mesh. This slice does not cache mesh assets.
- `cache/effect`: `RACEFX\0\1` / schema 1 / converter 1 / program version 1,
  disc SHA1 + source level + typed bank identities + original bank metadata/bytes
  and definitions + whole-file SHA256. Checksums detect corruption, not authenticity.
  Store: `<root>/<sha1>/effects/1/1/level-<n>.racefx`.
  Unique sibling staging, reread validation and atomic replacement preserve the
  last-good file. Failed staging is retained for diagnosis. No shared manifest
  or RACM sections are edited; P30's SOUND sidecar is independent.
- `effect`: 50/60 Hz fractional clock; bounded visual pool; normal/additive
  material model; stable normal back-to-front ordering followed by additive;
  resource-generation ownership; validated semantic envelope/standalone wire codec
  (384-byte maximum), dedup window and lifecycle UUIDs. Impact starts require
  a bounded tangent and unit normal; other operations cannot carry impact vectors.
- `client/effect`: owned texture/RenderType generation; billboard and tinted,
  translucent static-mesh backend using existing mesh material-run routing.
  Bilinear; depth test on, depth writes off; explicit clamp sprites/repeat meshes.
  Missing original assets produce no draw, not invented replacements.
- `Rac1BlastVisuals`: six type13 dust puffs and two class112 shells; three optional
  shells only beyond nine units with caller-provided suppression permission.
  Shell head start, lifetime, three random Euler angles and expansion/fade are
  visual rules. Three random draws are **not three render submissions**.
- `Rac1BlasterVisuals`: type26 glow, seventeen owner-controlled type72 trail
  sprites, accepted-impact type27 sparks, end type23 smoke. Plain close/untracking
  emits no impact. Existing projectile position/tangent is input only.

These are independently authored Java behaviors from permitted ReRAC ISC format
and behavior facts, pinned at `ae1e3432a0d5e21bd08d3872d3b04afef98be666`.
No original program code or GPL library is included. Local deterministic event
seeds are not original global RNG parity. Ordinary Java float arithmetic is not
bit-exact PS2 arithmetic.

## Integration contract (not installed)

One designated integration writer must supply:

1. Validated client-local disc identity and explicit source level 1; CPU load/
   decode off the render thread. Optional provider readiness must stay separate
   from the existing media gate. `Rac1EffectCacheStore` only owns its own sidecar.
2. Render-thread `EffectGpuCatalog` creation and `EffectCatalogGeneration` attach.
   Capture the generation before asynchronous work. Invalidate on disc/reload,
   clear instances before closing old resources, and close rejected stale results.
3. Existing projectile lifecycle UUID including spawn generation, interpolated
   anchor/tangent and original-update age. Update the adapter once per original
   fixed step. Rendering must not alter arrow aim/damage/collision/physics (#11).
   Only hide vanilla projectile graphics when the required original textures and
   adapter are ready. No first-flight-tick visibility policy is chosen here.
4. Server-accepted crate detonation/Blaster impact signals after the authoritative
   transaction. Resolve semantic names to client-local assets. No proprietary
   bytes, local paths or numeric original sound IDs are sent.
   `EffectSignalCodec` is not registered as a Minecraft payload in this slice.
   `EffectEventGate` rejects duplicates/stale events; clear it on world/generation
   changes. It is not an authority/security substitute for server-owned triggers.
5. Stop attached adapters on untracking, source death, chunk unload, entity reuse,
   dimension switch and disconnect. Snapshotted finite one-shots can finish.
   Call the runtime clock even while paused so resume rebases without a burst.
   A gap above 250 ms deliberately drops effects rather than unbounded catch-up.

Audio is neither imported nor called by this provider. Effects failure must not
affect rewards, damage, healing, chaining or sound.

## Remaining / fidelity limits

- Live optional provider bootstrap, transport registration, server transaction
  hooks, projectile renderer switch and multi-client synchronization are **not
  installed or verified**.
- Blast type11 branching sparks/smoke and original pressure/throttle rules are
  deferred. Existing P29 debris stays untouched; the generic static mesh backend
  can consume it after a single-owner adapter agreement. No second debris
  simulation/collision/drop implementation is installed.
- Blaster periodic camera-relative side sparks, muzzle/socket attachment, expiry
  reason policy and exact original RNG/rounding remain deferred.
- Nanotech particle33/39 and FX11/21 banks are imported, but orbit/controller,
  overlay geometry and sphere-map/glass rendering are **not implemented**.
  Never seek nonexistent class806 meshes or substitute an invented sphere.
  Pickup release/attraction/healing is separate server gameplay. Eight original
  visual orbs are one original HP per cluster, not eight HP. No Minecraft health
  scaling, drops or attraction policy is selected.
- Sprite width `size/210000` is an explicit world-scale approximation, not the
  original depth/viewport projection. Original near/far fades, GS overbright
  vertex RGB and display-byte blending need calibration/acceptance. Minecraft
  colour-space blending is approximate. This is not PS2 framebuffer parity.
- Actual-texture finite GPU fixtures can check materials/resources; they do not
  replace original-frame comparison, in-world occlusion/FOV tests, independent
  T verification or owner playtest.

## Focused checks

```powershell
.\gradlew.bat test --tests '*Effect*Test'
.\gradlew.bat test --tests '*RealEffectBankTest' '-Dracmc.isoImage=<permitted-local-ISO>'
```

The opt-in real-disc test reads each supported disc in place, checks 41 particle
and 49 FX entries, selected original pixel digests, definitions, seven generic
static moby meshes, no-mesh procedural controllers and an in-memory sidecar
round-trip. No original bytes are committed as fixtures. Never add an ISO, cache,
decoded image or original geometry to Git.
