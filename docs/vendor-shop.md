# Original vendor shop foundations (P35)

Current owner-approved wire/Buy/quantity/confirmation candidate and its runtime
evidence/limitations: [vendor-shop-wired.md](vendor-shop-wired.md).

Owner-approved transaction rules and the historical **unwired server milestone**
are documented in [vendor-shop-transactions.md](vendor-shop-transactions.md).
The wired candidate above supersedes that milestone; interactive playtesting is
still unverified.

This is **not the complete original shop or completion of #12**. The original
[placed-vendor preview](vendor-shop-preview.md) was read-only; the current candidate
wires server-authoritative purchases to its source UI subset. No permanent
entitlement, audio mapping or changed public world animation is installed.
Existing world/item scale and local approach-logo behavior are untouched.
The foundation measurements/options below are historical unless explicitly updated
by the current wired-candidate report.

## Frozen APIs and boundaries

- `Rac1ShopOrderedGpu`: owner-approved **first-proof C1-A prototype only**, with
  ordered integer color/shared logical Z24 destination reads and explicit GS masks.
  `Rac1ShopNormalGeometry` adds source-unit posing without changing shared decoding.
  [Prototype scope/evidence](vendor-shop-prototype.md) lists viewable source slices
   and all fixture-only inputs/omissions. The wired candidate uses that drawing
   program; complete original-scene admission remains unclaimed.
- `Rac1ShopSceneLightSource`: bounded immutable Novalis key-light, placed vendor3
  ambient/bank and world-fog import, explicit gameplay variant, no runtime defaults.
  `Rac1ShopLightingProgram` separates normal popup shading from metal and computes
  original off-axis E; `Rac1ShopGsPixelProgram` models raw-byte /128 blending,
  RGB_ONLY alpha failure and destination-alpha/FRAME/Z masks on CPU only.
  [Draw contract C1](vendor-shop-draw-contract.md) is the complete proposed local
  consumer, including RTT shared Z24, source lighting and explicit approximations.
  Only the isolated first-proof C1-A GPU prototype is now approved; no runtime
  scene/persistence/shared consumer is installed.
- `Rac1ShopEnvironmentMaps.read(novalis, validatedDiscSha1)`: immutable, in-memory
  original chrome128² / glass-environment64² resources, selected by -2/-3.
  Resolves bounded GS upload descriptors and reuses the compatible CSM1 decoder,
  retaining original alpha0..128. Not FX0x19, persistence or renderer binding.
  `Rac1ShopEnvironmentState` validates observed GS state and models alpha-test
  write masks on the CPU only. Exact facts and the **pending** new-draw proposal:
  [vendor-shop-environment-proposal.md](vendor-shop-environment-proposal.md).
- `Rac1AssetExtractor.readAnimatedAsset(level, classId, decodedExternalStream)`:
  explicit sixteen-offset external sequence source, preserving source slot names.
  Class12 uses this route, never Ratchet fallback. The ordinary importer now only
  selects core Ratchet sequences for class0; resident-less other classes return
  no clips. Existing resident and gadget imports are unchanged.
- `Rac1ShopModelImporter.read(validatedImage, novalis, sha1)`: salesman12,
  backdrop13, popup1137 and rigid ammo1006. The last reuses
  `Rac1EffectMeshImporter`, not `readSkeleton` or the scenery/tie path.
  Main-cache body11/logo1143/Blaster168/bolt HUD are not duplicated.
- `Rac1ShopImporter.read(validatedImage, novalis, sha1, languageSlot)`:
  five original60015 icon frames;59701 LED atlas; FX18/19/1A/1B/1C;
  regular/small original fonts/928-byte metrics; eleven required interface IDs
  plus the twelve distinct ticker IDs referenced by24 weighted entries. Imports
  LED cells/advances, margins, Blaster panel/hologram vectors and manipulator
  targets, along with the additional scene models. No text/art is bundled.
- `Rac1ShopText`: bounded byte strings, ID lookup, defensive copies, explicit
  supported language slots0/2/3/4/5. Unsupported Minecraft locale maps to English;
  corrupt/missing source records fail with diagnostics, not invented strings.
- `Rac1ShopGlyphLayout.line`: original 16x16 ordinary/accent and24x16 grey pad
  glyph primitives, advance-only spaces, original color control/reset bytes.
  Colors remain GS AABBGGRR. Invalid atlas extents/bytes fail.
- `Rac1ShopTextWindow.layout`: source byte-newline/space/control breaks, long-word
  splitting,16-pixel balancing steps, remembered color state, horizontal/vertical
  centering and scissor/line-skip metadata. No native font wrapping. Narrow
  pathological windows are bounded explicitly; source PS2 snapshot parity is not claimed.
- `Rac1ShopTickerLayout.line`: original18-space prefix,9x9 LED cells drawn18x18,
  imported advances and blink marker. Original high-byte filtering is retained:
  bytes above `Z` skip without advance, except ASCII `b` arms the blink flag.
  No translated substitute glyph or gameplay RNG is used. Blink phase/clock
  scheduling and ticker line-choice integration remain separate.
- `Rac1ShopLayout.monitorVendor`: `(4s,4s+1,4s+2)` are **joint-list IDs**, not
  raw joint indices. Imported attachment-chain endpoints supply their positions.
  `monitor` is only a low-level flattened point-palette helper. Original imported
  margins remain in model units, before the accepted world fit.
  `Rac1ShopProjection` projects corner/opposite corner with a supplied clip
  matrix; no guessed FOV, camera policy or fixed panel rectangles.
- `Rac1ShopKioskPose`: vendor-local sampled frames, source half-speed unfold and
  reverse-fold starting at original frame9, local hemisphere cross-sequence
  blends and Hamilton-product modifiers before hierarchy evaluation. Reuses the
  existing evaluator for one-frame hierarchy/skinning. Source consecutive-key
  quaternion interpolation is plain lerp, not forced normalization. Floating
  snapshots are not PS2 re-encoded/quantized snapshots; post-scale linking quirks,
   runtime animation bridge remain unverified. Both-disc count binding is local
   translation, not rotation: `(7-count)*1665` packed Y units for count<8, zero
   otherwise, signs `+ - + -` at lists20..23. `monitorArms` converts with imported
   class scale/1024 and existing axis/unit conversion before any outer fit.
- `Rac1ShopResources`: backend-neutral allocation owner and lifecycle. Register
  each texture/target immediately with `own`; partial creation, resize, identity
  replacement, reload and logout close resources in reverse order. Repeated
  frames reuse allocations; exceptions do not prevent other resources closing.
  `Rac1ShopClientResources` self-registers client-only reload/logout/cache-loss/
  window-resize disposal. It cannot allocate a scene before full readiness.
- `Rac1ShopGpuBackend`: shop-private native texture/target ownership and2D draw
  path with per-texture sampler variants and scoped GL-state restoration.
  512x128 scratch extent matches original monitor targets. LED noise uses
  POINT+REPEAT; panel noise LINEAR+REPEAT; glass/fonts CLAMP. GS equations0x44,
  0x64 and0x68 are distinct, including fixed-factor noise RGB independent of
  texture alpha. No shared FX/HUD texture, RenderType or shader state is changed.
  RGBA8/depth24 targets and source-alpha destination-alpha handling are explicit
  Minecraft backend choices, not PS2 framebuffer/alpha-test emulation. No scene
  compositor, popup/chrome mesh backend or render acceptance is implied.
- `Rac1ShopSessions`: ephemeral, owning-thread-only admission foundation.
  Authenticated owner, random session token, dimension/position/incarnation,
  monotonic bounded sequence and exact-intent replay/conflict checks. Opening
  another kiosk replaces only that player's nonce. The only observer aggregate
  is viewer count; private selections/tokens/requests are not exposed publicly.

Session limits are implementation safety limits, not retail product rules:
4096 concurrent sessions,256 retained admissions/session,8 requests/server tick,
1200 server ticks idle expiry. Action0..3, selection0..63 and quantity0..65535
are transport bounds only; they do **not** establish an allowed catalog,
quantity policy, purchase authorization or receipt. At request exhaustion reopen
with a new nonce; no old sequence is evicted/re-executed. A future server adapter
must validate loaded kiosk identity, permissions, player life/mode/dimension/range
before admission and invalidate on logout/death/dimension/range/removal/unload.
No adapter, replay-safe purchase receipt or automatic lifecycle event hook exists yet.

## Local sidecar, not a second media manager

`Rac1ShopCacheCodec` schema5/converter5 stores converted sprites/fonts/selected
byte strings, numeric scene tables, salesman/backdrop/popup/ammo models and the
raw source packet metadata for classes11/12/13/1137/1143.
Disc SHA1/language identity,32MiB total extent,24MiB model section, CRC and
mandatory imported-subresource checks apply. `Rac1ShopCacheStore` uses:

`<existing cache root>/<sha1>/shop/5-5/language-<slot>.racshop`

Version5 adds only the reviewed metadata section after model bytes and before
strings; scene-table4 and complete-model wire1 stay unchanged. Old4/4 sidecars
are neither interpreted nor deleted. Metadata section1 is big endian, bounded8MiB,
fixed Novalis1 and exactly five ordered classes, with strict packet/material counts,
flags/reserved bits, ordering/unique indices, multiplier extents, exact consumption
and outer CRC. Full layout is in `docs/vendor-shop-reader-proposal.md`.
The reader/API and persistence reviews were by the orchestrator acting within
owner-approved implementation scope, not direct owner review of every API/schema.
The only entrypoint addition is P34's identical reviewed11-line
`RacMcClient.assetCacheDirectory()` getter from cf006da. It exposes only the usable
startup-resolved local directory; it is not a live validation or readiness gate.

Publication uses a uniquely named, verified staging file and atomic replace.
Failed staging remains for diagnosis; no non-atomic fallback or old-cache
deletion. Main RACM7/7 payload format is unchanged. `Rac1CacheCodec` exposes
`encode/decodeAnimatedModel` and `encode/decodeStaticModel` using its existing
serialization, with64MiB per-model bounds and strict structural/finite/reference/
trailing-data checks. No duplicated model serializer. A synthetic full-union
digest generated with the prior cd63f78 codec remains byte-identical.
`Rac1CacheCodec.MODEL_ENCODING_VERSION = 1` is the public complete-model wire
identifier for typed consumers (including Clank), independent of all envelope
versions. Static151-byte and animated570-byte synthetic payload digests are pinned
against the prior codec; this identifier adds no wire bytes.

No shared startup/media gate is changed. `Rac1ShopClientCache.prepare(sourceLanguage)`
is an explicit client-only request using the existing configured selected path,
ready main-content identity and `assetCacheDirectory()` effective root. It chooses
no default language and has no installed caller/Screen/key/commerce policy.
`importedResources()` is an imported subset, **not full scene readiness**.

`Rac1ShopCachePreparation` permits one in-flight job and no queued selections.
Generation changes discard stale completions (including A -> reload -> A).
Failure is terminal for that generation, not retried every tick. Reload/logout,
media loss, root loss, path change and main-content replacement invalidate CPU
admission; existing native resource hooks also drop a lost/changed selection.
An already-running read may finish and preserve its correctly namespaced converted
sidecar on disk, but cannot publish stale resources into the current client.

`Rac1ShopSelectedDiscLoader` revalidates the full supported disc off-thread and
requires its exact revision/product/SHA1 key to match the main cache before any
sidecar read/import/write. Missing5/5 sidecars use the existing bounded importer
and atomic store. Corrupt/read-failed sidecars fail closed and remain unchanged;
no silent rebuild, fallback directory, config reload or second media manager.
No raw ISO/container copies, source paths, textures, strings or fonts belong on
the network.

`missing2d()`, `missingSourceTables()` and `missingImportedModels()` validate
their respective imported subsets only. `Rac1ShopReadiness.inspect` inventories
all imported and referenced resources, disc identity and unfinished mandatory
scene components, including UNBOUND audio. `requireReady()` currently refuses
scene allocation. A valid cache must never enable incomplete original UI.

## Readiness matrix and remaining gates

| Resource/behavior | Measured state |
| --- | --- |
| Salesman12 external animations | Both discs:10 clips,92 joints; start/midpoint finite joint poses |
| Ammo1006 rigid model | Both discs:132 triangles through existing rigid importer |
| Popup1137 | Both discs:92 vertices,64 triangles,zero textures,material−1; original untextured packet support is required, not a missing atlas |
| Body11 monitor triplets | Both discs: imported joint-list chain endpoints; six nondegenerate open quads. Direct raw-joint indexing is not the source route |
| Fonts/icons/selected text | Both discs:2D conversion; selected IDs/glyph bounds across all five supported languages |
| Sidecar models/tables/2D | Both discs:in-memory roundtrip; synthetic corruption/version/identity/missing-resource/store checks |
| LED cell/advance tables and24 ticker references | Both discs: unique matching PAL+0x280 table mapping; imported fixed12.4 UV cells and weighted ID references |
| Product/hologram placement tables | Both discs: Blaster panel stride48, hologram stride64, ammo class1006; no player grip/muzzle transforms |
| Arm targets, local pose blending, reverse fold | Both discs: list20..23 target joints3,2,5,4; verified alternating count-driven translation, closed fit and exact frame9 regression. Quantization and runtime visuals remain pending |
| Original text-window/ticker/material primitives | Bounded components implemented/tested; full original content compositor not yet implemented |
| Class13 chrome, popup untextured render, original scene lighting | Backend not implemented; generic textured renderer is insufficient |
| Camera/capture/input/damage-close policy | Owner decisions pending; no teleport, hide, freeze or pause code introduced |
| Vendor cues/salesman voice | UNBOUND; no Blaster firing cue borrowed |
| Full readiness and GPU disposal hooks | Fail-closed inventory and client-only hooks implemented; actual scene and non-pausing Screen remain uninstalled |
| Shared open/close observers and lifecycle networking | Viewer-count foundation only; no world/session bridge installed |
| Commerce/catalog/inventory/ledger | Held; no money or item mutations |

Accepted owner rules remain: original prices/presentation, weapon present means
ammo offer and absent means weapon offer, no permanent unlock/regrant, complete
delivery capacity before charging with no world drops, independent simultaneous
players and normal vulnerability/non-pausing even in integrated singleplayer.

Unresolved: initial catalog/progression; ordinary-arrow mapping and shop200 cap;
initial100 application/overflow; inventory membership/stack-capacity edges;
camera/background/input/damage behavior. Original Blaster facts are2500 weapon,
1 per ammo unit,200 cap,100 initial field,25 pickup;25 is **not** a shop bundle.
No commerce decision is implied by importing a Blaster icon/ammo prop.

P34's read-only identity/count contract is known at77eb592 (not merged here):
`RangedWeapons.definition(ItemStack)`, `BlasterAmmo.matches(ItemStack)` and
`BlasterAmmo.count(Player)`. This is the temporary ordinary-arrow PoC, not shop
cap/initial-ammo authorization. No consumption methods or player grip math are used.

The canonical account remains `Rac1BoltLedger`. Proposed future shared seam,
**not edited or approved here**: a narrow no-notification purchase debit/result
preserving existing `tryDebit`. Complete capacity preflight, inventory edits and
terminal receipt must commit before outbound sends. No second ledger, refund/
credit endpoint or durable entitlement. Normal separate inventory/SavedData saves
do not imply hard-crash atomicity.

## Provenance and checks

Independent MIT code uses existing project readers and format/behavior facts
from ReRAC documentation (ISC), pinned revision
`ae1e3432a0d5e21bd08d3872d3b04afef98be666`:
`docs/plan/interaction.md` sections9.2–9.5, `docs/plan/hud_text.md`
sections3/5, `moby_animation.md` sections3/4/6/9 and the R12 addendum. P35's bounded
both-disc table probe independently distinguishes panel48/hologram64 strides and
verifies PAL relocation; an LED control-flow probe verified high-byte filtering.
No upstream implementation,
GPL dependency, proprietary golden fixture or extracted asset is included.

Java21 compilation and199 focused synthetic/extraction/cache regressions passed
(30 opt-in checks skipped); expanded read-only
`RealDiscShopTest`, closed vendor and hero/Blaster conversion checks passed
separately on both fully validated supported discs (four opt-in checks per disc).
Source-independent tests cover generic codec byte compatibility/bounds, source
tables, glyph/window/ticker/pose/projection/material math, private sessions and
resource lifecycle. Failed popup readiness, strict LED rejection, compile-constant
binding and time-sampled fold-oracle runs are retained privately, not called passes.
The corrected fold oracle uses exact raw source frame9 without widening tolerance.

Continuation regression:721 passed,48 opt-ins skipped,zero failures across the full
base-branch suite, including P34's unmodified getter test supplied privately.
Both-disc shop import and packet-material checks passed separately (two each).
The first new translation test incorrectly expected sibling/root vertices to move;
the retained corrected oracle checks only the vertex weighted to the target joint.

`Rac1ShopGpuBackendTest` is opt-in with `RACMC_TEST_SHOP_GPU=1`, in one OWN
never-shown/unfocused1x1 context and synthetic pixels only. Approved execution passed
on NVIDIA RTX5090, OpenGL3.3.0 NVIDIA621.14/GLSL3.30. Measured POINT+REPEAT and
LINEAR+CLAMP samplers, FIX32/128 readback `[102,102,102,102]` from source204,
alpha0/destination51, scoped state restoration and native disposal passed.
The first run's post-replacement deleted-name oracle failed because GL may reuse
names; the corrected check proves deletion before allocation and rejects retired
owner access. Both runs are retained. This is synthetic2D GPU evidence only.

Read-only `RealDiscShopMaterialTest` passed on both discs: normal packet multiplier
bytes are all neutral128 for classes11/12/13/1137/1143. Popup1137 has368 such bytes,
material−1 and mode0/255; no hidden color atlas was found. Backdrop13 has two metal
packets/156 vertices/selector−3; logo1143 has seven metal packets/selector−2,
576 vertices NTSC and573 PAL (normal vertices741/724 respectively). These omitted
packets and original runtime shading remain fidelity blockers, not missing textures.

The approved additive `MobyClassReader.readMaterialMetadata()` now exposes immutable
class/packet/material source records with defensive multiplier bytes, original
packet indices, separate metal ranges, raw signed selectors/TEX1/CLAMP/glow/modes.
Malformed ranges/counts/unpack shapes/full blocks reject. Existing mesh decoder
methods are untouched. Generic prior-reader digest and independently generated
both-disc per-class digests pin all seven old decoded arrays before/after metadata.
Metadata is now persisted by the shop-only5/5 codec, but is **not rendered**.
Import validates all five normal-packet vertex sums against actual decoded source
meshes; cache admission validates salesman12/backdrop13/popup1137 sums against the
stored models, and full readiness validates body11/logo1143 sums against main-cache
references. Metal counts are never treated as included geometry. Missing/mismatched
metadata fails closed; no inferred colors or hidden repairs.
The metadata section measures22,672 bytes NTSC/22,604 PAL; the region difference is
preserved. No source carry/index-gap conflict required relaxing the reviewed bounds.

Metadata-seam full regression:728 passed,zero failures;49 skipped including the
private prior-reader disc oracle. Final shop-import/metadata tests passed2/2 on
each supported disc. Prior-reader digest generation also passed independently
on each disc using isolated90bc536 classes, with no source asset written to disk.
Selected-disc hook continuation:736 passed,zero failures;50 skipped including
private prior-reader oracle. All three shop opt-ins passed on each disc, including
sidecar import/cache-hit, exact-main-key rejection and corrupt-file preservation.
Approved own hidden synthetic GPU rerun also passed; this remains2D evidence only.
Shop5/5 persistence regression:744 passed,zero failures;50 opt-ins skipped
(including the private prior-reader oracle). Final import/material/selected-loader
checks passed3/3 on **each** supported disc. Malformed tests include every small
section truncation, count/flag/order/selector limits, CRC-valid malformed sections,
CRC-valid shape-valid stored-mesh mismatches and old namespace retention.
The prior generic reader outputs and complete-model/union byte digests still pass.
No new GPU/scene acceptance is claimed for this metadata-only continuation.

Nov19 source/material continuation: independent both-disc probes recover the
omitted metal topology (class13:156 vertices/52 triangles on both; logo1143:
576 vertices NTSC/573 PAL,698 triangles each). All required metal vertices directly
use local joint0 or1 with count1. Later packets carry the first packet's AD-GIF
binding; source no-kick corners, pipeline tail and mixed winding must be preserved.
`docs/vendor-shop-metal-proposal.md` gives the exact additive raw access API,
vertex/weight/strip/normal/transform mapping and compatibility proof reviewed by the
orchestrator. The additive raw API is now implemented; existing normal geometry,
model/extractor/renderer/schema behavior is unchanged.

The new shop-local `Rac1ShopMetalProgram` implements the source metal-light/
sphere-map algebra only, using explicit caller lighting/E inputs, unnormalised
normals, per-light truncation, byte wrap, signed4.12 ST and the original shine gate.
Both-disc primary driver/trig checks pin source constants/field loads and entries.
No actual renderer consumes it yet; it is not a fake flat popup shader or a
cycle-accurate VU emulator. Envmap import/binding, glow and popup shading remain held.
Regression:750 passed,51 opt-ins skipped,zero failures;4/4 shop checks EACH disc.
Shared old mesh/model/union byte pins remain unchanged. No game/GPU launch or
new subjective visual acceptance occurred.

The approved `MobyClassReader.readMetalPackets()` returns immutable raw vertices,
draw corners and original AD-GIF references, including list-local material carry.
It preserves the signed packed coordinates/unsigned angles/direct joint bytes and
count0/1 versus2/3 semantics, validates header packing/weights/joints/ranges/pad/VIF/
secrets/trailer, and removes exactly three pipeline tail corners. Existing readMesh
outputs remain pinned before and after raw-metal access.

`Rac1ShopMetalGeometry.read(level,classId)` now directly consumes that API for
Novalis13/1143 in memory. Explicit class-local F=P*S palettes produce quantized
packed positions BEFORE classScale/1024/axis/unit conversion, unnormalised normals
and literal no-kick/material-tagged triangles. No UV/color/map is fabricated.
Literal independent P*S and C^-1*Fmc*C oracles pin unit/order behavior; both-disc
raw geometry hashes and controlled posed palettes exercise the real count1 data.
Synthetic0/1/2/3 tests do not imply retail multi-weight coverage or cycle-accurate
original pose acceptance. Nothing persists this metal geometry or draws it yet.
Envmap layout/import/binding, original popup/glow/GS state and compositor remain
required; selected-disc prepare caller and commerce remain uninstalled, voice_UNBOUND.
Raw/posed milestone regression:761 passed,52 opt-ins skipped,zero failures; final
5/5 shop checks passed on each supported disc. The first focused literal conjugation
oracle expected+0 where the reflection produces IEEE−0; retained failure was fixed
only by pinning the two literal zero signs, without production/tolerance changes.
Header/stream/range/palette malformed cases and1,600 required-byte truncations
reject. No metal persistence, schema/model-wire bump, shared renderer or new
hardware/game launch occurred.

Independent exact combined M2/M3 T21 verification, actual original visuals,
driver-backed resize/reload, singleplayer damage, two simultaneous clients,
network privacy/authority, transaction/save behavior and owner playtesting remain
pending. These results are not M2 compatibility, pixel/PS2 parity or #12 acceptance.
