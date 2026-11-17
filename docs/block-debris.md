# Imported-material vanilla block debris — bounded P29 correction

Follow-up to integrated `86ca1f7`, conditionally approved by the owner pending this
correction. This is **not** the original sound/effect/particle pipeline (#35).
It changes only standard client mining/destroy debris for all four crates and the
Gadgetron vendor. No gameplay, proximity, world/item transform, HUD, bolt motion,
payout, damage or explosive flash behavior changes.

## Root cause and selected API

The empty block models are deliberately drawn by imported-mesh block-entity
renderers. Their JSON `textures.particle` entries still name Minecraft placeholders;
ammo and vendor explicitly name `minecraft:block/iron_block`. Vanilla
`TerrainParticle` resolves that model icon, not the separately rendered imported
material. Hence their almost-white iron debris. Vanilla `ParticleEngine.crack`
also skips `RenderShape.INVISIBLE`, so merely changing a model icon would not
fix mining hits for these blocks.

NeoForge 21.1.265 exposes
`IClientBlockExtensions.addHitEffects/addDestroyEffects`, registered through
`RegisterClientExtensionsEvent.registerBlock`. One shared extension handles both
hooks for every crate and vendor and suppresses the placeholder fallback.

The particles **extend vanilla `TerrainParticle`**: lifetime, gravity, collision,
size, camera-facing rendering and source-position/world-light fallback remain
vanilla. Hit particles use the ordinary power/size adjustment; destruction uses
the ordinary quarter-block voxel subdivision. RGB retains vanilla's 0.6 shade,
not a biome tint, flat material color or fullbright replacement.

## Texture/UV and lifecycle contract

- `Rac1DebrisMesh` selects an actual imported triangle, weighted by surface area;
  hit selection additionally weights toward the hit face normal. Missing face
  coverage falls back to whole-surface sampling. Vendor uses its existing closed
  posed body; hologram is not physical debris.
- A small texture patch at that triangle's wrapped UV centroid samples its
  original material page. Fully transparent centroid packets and degenerate
  triangles are skipped. This is representative material/face sampling, **not**
  exact impact-to-mesh ray intersection, PS2 fragment geometry or pixel-perfect
  original debris. Legitimately gray/chrome vendor packets remain gray/chrome.
- A material-bound terrain sheet uses the standard particle shader/vertex format,
  blend/depth state and engine lightmap, but binds the cached material texture
  instead of the global Minecraft block atlas. The particle overrides its UVs
  with standalone page coordinates; it does not accidentally use atlas UVs with
  an unrelated texture.
- This hook path needs **no global atlas restitch, generated PNG, dynamic block
  model or cache format change**. Imported content may arrive after the original
  atlas bake; only already-decoded in-memory cache pixels are uploaded lazily
  through existing `Rac1ClientTextures.register`. There is no ISO access, file
  decode or cache load in the hook/render path.
- `Rac1DebrisMaterials` owns only these debris uploads. One upload per material
  per cache generation, not per particle; stable sheet keys/types are reused.
  Client ticks notice cache replacement/loss; the client resource reload listener
  releases owned GPU textures and CPU bindings. Generation checks suppress old
  particles immediately (including rendering before their next tick), then remove
  them during tick. Reload with the same cache reuploads correctly. World/item
  texture owners are not modified or released.
- Missing cache/asset/usable triangle means no debris, never white placeholders.
  This correction does not replace other particle providers (running/falling dust
  or server-authored block particle options), add original explosion effects, or
  change server event/network contracts.
- Schema/converter remain **6/6**. No assets, PNGs or cache bytes are committed.

## Measured P checks

Windows / process-local Corretto Java 21.0.4:

```powershell
.\gradlew.bat test --tests '*Rac1DebrisTest' --tests '*Rac1VendorWorldScaleTest' --tests '*Rac1VendorItemTransformTest' --tests '*Rac1HudTextureRegistryTest' --tests '*Rac1IntegratedCacheTest' --console=plain
```

Compilation and **16/16 focused tests passed**. New checks exercise material and
face selection, wrapped UV patch bounds, transparent/degenerate data, all
crate/vendor bindings, missing cache/asset, lazy upload, cache replacement,
same-content resource reload, resource release, stale-particle generation and
source-level hook/terrain-lighting registration.

One private, hidden/unfocused 1×1 OpenGL context was also run with:

```powershell
$env:RACMC_TEST_SHADER_GPU='1'
$env:RACMC_TEST_DEBRIS_CACHE='<permitted existing converted .racm cache>'
.\gradlew.bat test --tests '*Rac1DebrisTest' --tests '*Rac1DebrisTextureGpuTest' --console=plain
```

**6/6 passed**, including the single GPU check on RTX 5090. It read the existing
local integrated cache without changing it: 24 selected imported UV patches for
each of five blocks at two light multipliers, **240 RGB/alpha readbacks** agreeing
with CPU bilinear original-texture samples. Ammo had 23/24 non-gray samples; vendor
used original materials 0/1/2 with 5/24 non-gray samples (other selected body packets
are naturally neutral). No game assets or screenshots were exported.

This isolated sampling shader tests actual source RGB/UV sampling and shade/light
modulation, **not the Minecraft particle engine, full engine lightmap/fog, live
mining/death packet flow or actual in-world appearance**. Those affected runtime
checks remain for independent T15 and owner confirmation on the new exact commit.
The initial synthetic test failed because its helper packed RGBA instead of the
cache's ARGB byte order; the helper was corrected, not the production converter.
Full base suites were not repeated; T15's earlier `86ca1f7` results are retained.

Logs and GPU result XML are privately retained under
`%LOCALAPPDATA%\Temp\opencode\p29-integration\debris-*`.
The owner's old integrated preview client was neither controlled nor reloaded;
new Java classes require a separately authorized restart to test this candidate.
