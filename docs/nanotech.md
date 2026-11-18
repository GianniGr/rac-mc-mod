# RaC1 nanotech pickups

One actually destroyed blue health crate (class501) releases **one persistent
cluster with eight cosmetic orbs**. One original HP is mapped to **5 Minecraft
health points (2.5 hearts)**, clamped to the receiver's current maximum. This is
the approved Minecraft scaling from original default4HP to Minecraft20HP, not a
claim that the original pickup heals5HP. Modified maximum health does not change
the fixed5HP heal. No bolts, XP, item, hunger, absorption or RaC2 upgrade is granted.

## Original behavior and explicit Minecraft adaptations

- Intact crates, ordinary falling/landing transfers, TNT arming and cancelled or
  rejected destruction grant nothing. Mining, held/slam/thrown wrench contacts and
  accepted blasts enter the same successful-destruction transaction. Grounded and
  falling placement UUIDs are preserved and tombstoned exactly once.
- Full health prevents **new** attraction. Admission uses
  `health + 5 * incomingClusters < maxHealth`, not eight reservations per cluster.
  A locked receiver filled by another source still consumes/clamps at first arrival
  and emits one pickup cue, even when no additional HP can be added.
- R10 verifies original distance strictly<10 original units and a clear sight check.
  The MC adaptation is strictly<10 blocks to feet, collision-shape ray from both
  endpoints+.5up, fluids ignored. There is no bolt-grabber radius.
- Original single-player has no multiplayer winner policy. MC chooses the nearest
  eligible living same-dimension nonspectator, stable lexical UUID tie-break,
  locked during flight. Injured Creative players are eligible; full Creative
  players do not consume. Occlusion/movement after lock does not cancel.
- Death, disconnect, dimension change or spectator before commitment cancels the
  reservation and returns the unconsumed cluster to its last safe position.
  This deliberately differs from original death-in-flight consumption and never
  resurrects or heals a respawned replacement.
- Eight deterministic seeded durations sample inclusive20..80 canonical60Hz units.
  The server commits at `ceil(minDuration/3)` Minecraft ticks, then only cosmetic
  paths finish. This is an explicit MC timing/RNG adaptation, **not** exact PAL
  nested ticks or verified original RNG endpoint emulation.
- Free clusters drop vertically, settle on voxel collision and breathe; they do
  not use the radial bolt spill. Voxel gravity/collision and void recovery to last
  safe position are conservative MC adaptations, not original platform/hazard
  fidelity claims. No age expiry, lava/fire deletion, damage, explosion knockback
  or re-chaining. Original FREE update has no examined expiry/generic hit branch;
  original global hazard cleanup remains unverified.

## Ownership, persistence and bounded views

`Rac1NanotechLedger` is overworld SavedData (`racmc_nanotech`) containing source
tombstones and uncommitted clusters across dimensions. One stable derived UUID
identifies each disposable `Rac1NanotechEntity` view. Failed/capped spawn admission
retains the record, never mints a replacement or consumes health. No source chunk
is force-loaded. Up to128 views and128 round-robin record updates per dimension
per tick; excess pending records remain durable and may wait for view capacity.
Under overload this intentionally delays attraction/motion; it does not create
eight server entities or erase rewards.

Normal save preserves free position/velocity/seed. Saving or unloading an
uncommitted flight recovers it FREE at its last safe location; committed clusters
persist only their terminal source tombstone, with no historical cue replay.
Malformed supported NBT is quarantined in a disabled ledger, logged and retained,
not replaced with a fresh reward owner. Invalid/duplicate IDs, conflicting
ownership, invalid dimensions, nonfinite/out-of-range motion and unsupported
schemas are rejected. Unreadable/corrupt outer world storage still requires normal
Minecraft/world-backup recovery; this is not a repair tool.

**No hard-crash atomicity guarantee:** player HP, block state and SavedData are
separately persisted. Graceful-save/restart idempotency is covered; fsync/power-loss
transactionality is not claimed.

## Local original presentation and audio

The shared EFFECT pipeline uses imported particle39 orbs, particle33 type62 trails,
FX21 hemisphere/glass and FX11 halo. Grounded and falling intact interiors render
from their anchors; after destruction only the released cluster remains. Released
controllers omit intact glass; attraction uses eight cubic fan paths toward the
moving chest target. Rendering is capped at16 nearby controllers, within the
existing shared2048 effect pool. Missing catalogs, reload or pool exhaustion never
heal or consume. Falling vanilla carriers may use entity-identity cosmetic phase
when their private block NBT is unavailable client-side.

The semantic `NANOTECH_FIRST_ARRIVAL` maps through shared **RACS v2** to class501
local1, curated level1 cue175/grain452. The importer still resolves the actual
class remap, not a globally hardcoded cue index. NTSC/PAL ADPCM SHA256:
`e6b42259a967ba7ce01b1ed796aea3c8753b175bf2f6b671c173bf1996d9bd87`.
Class501/local0 break and class13/local0 bolt attraction remain separate.
There is one authoritative consume cue, none on release/attraction and not eight
arrival dings. Missing/unsupported sound is silent, never a substituted cue.
RACS v1 is retained on disk but rebuilt into v2; RACM/schema7 is unchanged.

Each client uses its own local verified ISO/cache; no assets, filenames or ISO paths
are networked. Only ordinary entity position and bounded seed/receiver/age/commit
metadata are synchronized. Server health has no client request packet. Late
tracking receives current state, never historical pickup audio.

Remaining fidelity: type59/60 heal ring/glints are not implemented; cubic/voxel
motion, canonical timing and original amplitude/pitch/envelope/material fidelity
are not exact PS2 emulation. Hidden-context GPU sampling is not an actual in-game
visual or audible listening pass. Independent T19 must verify the exact new
candidate, dedicated-server/two-client synchronization, lifecycle/reload and
in-world visual transitions. No all-M2-complete or issue-closure claim.

## Focused opt-in checks

```powershell
.\gradlew.bat test --tests '*Rac1NanotechTest' --tests '*Rac1NanotechVisualTest' --tests '*Rac1SoundPipelineTest' --console=plain
.\gradlew.bat test --tests '*RealSoundDiscTest' "-Dracmc.isoImage=<authorized local ISO>" --console=plain
$env:RACMC_TEST_SHADER_GPU='1'
.\gradlew.bat test --tests '*Rac1NanotechGpuTest' "-Dracmc.isoImage=<authorized local ISO>" --console=plain
.\gradlew.bat --no-configuration-cache -I src/test/fixtures/nanotech.init.gradle "-PnanotechDirectory=<private directory>" runGameTestServer --console=plain
$env:RACMC_NANOTECH_RESTART_PHASE='read'
# Repeat the same headless command against the same private directory to check real world restart.
```

The nanotech fixture compiles only into its private output directory and adds no
classes/resources to normal publication builds. Its synthetic packet-sink profiles
exercise real server health, not actual connected network clients.
