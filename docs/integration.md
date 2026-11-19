# Approved feature-wave integration candidate — P33

**M2 shared-ranged candidate, 2026-11-18:** current work builds on owner-accepted
PR39; earlier T19-pending prose below is historical, not a reopened gate.
The new candidate still requires independent T20 on its exact commit. Shared ranged
definitions/hand/fire/ammo seams, imported-projectile admission/no-shaft rendering,
grounded/airborne crate authorization and verified sustained Blaster audio are
described in [development.md](development.md), [effects.md](effects.md) and
[sound.md](sound.md). RACS is now3; RACM stays unchanged. No shop/vendor or currency
ledger implementation is part of this change. Pose/root/skin and live audiovisual/
two-client limits remain explicit; this is not full #9/#11 acceptance.

**Corrective pass1:** source-pinned vanilla root/final-arm/profile-skin handling and
the narrowly repaired legacy bolt headless account fixture supersede the initial
standing-only adapter. Independent exact-new-tree retest is still required. The
whole-body animated-ring anchor is not owner-approved; B3 presentation/data policy
and T20's remaining live matrix are not waived. No P35 extractor/codec/shop files or
production currency/network services were changed.

**Current P33 nanotech addition:** T18 combined PASS applies to exact
`99b781e87a255927bd1e30de6099d2252ee60033`, before this change. Owner authorized P33
on this same branch/worktree. Health destruction/released and falling visuals,
attraction, one scaled5HP first-arrival heal and original pickup cue are now
implemented; see [nanotech.md](nanotech.md). **Independent T19 is required on the
new exact candidate.** Prior P32/T18 JAR/source archives are obsolete as artifacts
for this revision; rebuild/audit normal non-fixture artifacts before publication.
Historical verification statements below are not new-candidate PASS claims.

**2026-11-18 T18 repair follow-up:** held explosive START now respects an active
item cooldown (no balance change); the falling regression's global-ledger oracle
is replaced with placement UUID/profile accounting plus explicit unrelated-reward
interleaving. No falling gameplay or nanotech production is changed. Independent
T18 retest remains required. See [handoff.md](handoff.md); do not publish the retained
instrumented default `build/` outputs.

Owner decision, 2026-11-17: consolidate **one big PR**, after acceptance of
combined gameplay/material debris, sound `739248a`, effects `7106e9d` and wrench
`afca221`. This is local integration authority only: no push, public PR or provider
merge by P32. T18 must independently verify the exact final committed candidate;
the orchestrator owns publication and the owner alone merges.

Branch: `integration/approved-feature-wave`, exclusive worktree
`C:\Personal Repos\rac\mc\rac-mc-mod-release-integration`.

| Included feature | Approved history / current boundary |
|---|---|
| Crates, vendor, bolts, HUD, motion, explosives, material debris | Combined base `56147b76c546bafbac3e38ee7d382e8972e65b33`; individual heads below |
| Sound | `739248ad66e2c135f91bc0df186bcb5c0c96a235`; verified crate/beep/blast/bolt-attraction mappings only |
| Effects | `7106e9dbd3a0daa0be735c3223c194a1068bee57`; imported banks + accepted procedural preview, not 1:1 original fidelity |
| Wrench | `afca22185bbdda39af269e709c435a3ac4eaec0b`, includes server-local provider `996dc61`; held/slam/thrown crate adapters wired |
| Nanotech | P33/R10: one ledger-owned eight-orb cluster, fixed MC5HP (scaled original1HP), class501/local1 first-arrival cue; T19 pending, fidelity limits retained |

## Final integration choices

- RACM format stays 2, **schema/converter 7/7** invalidate both standalone and old
  combined caches. Sections: static 1, hero 2, Blaster 3, crate/bolt map 4, vendor 5,
  HUD 6, wrench 71. Client and server use one union importer. Client readiness checks
  wrench as well as vendor, HUD and every crate/bolt; incomplete caches rebuild/fail,
  never silently READY. Incomplete synthetic codec fixtures may still represent null.
- Sound now uses independently versioned RACS v2 (adds nanotech501/local1); effects remain independently
  schema/converter-versioned RACEFX. Neither writes a RACM section. Cosmetic RNG
  does not advance payout/fuse RNG. Optional cosmetic transport failure cannot
  suppress the accepted reward or blast.
- Both branches' break hooks coexist **after the one shared reward transaction**.
  Detonation still uses the P24 ledger and placement UUID for grounded/airborne
  self-consumption, neighboring crates and deferred chains. No payout on arming,
  no bolt denominations/profile changes. Sound and effect each emit once on an
  accepted detonation; veto/duplicate/stale callbacks do not create another.
- Git's unconflicted duplicate `onDestroyedByPlayer` methods were consciously
  unified. Held ordinary instant breaks and native slam/thrown harvests all enter
  `Rac1WrenchCrateHits` then `Rac1CrateRewards`, not superclass/direct removal.
  Non-wrench destruction retains the combined reward path.
- Direct held explosive START is checked for loaded/border/build height, reach,
  visibility, mode, cooldown, interaction permission and the real BreakEvent.
  The captured state/BE/held lifecycle is rechecked before the same installed
  short-fuse adapter used by authoritative slam/thrown contacts. Explosives are
  never wrench-mined. Original 5–15 updates at 60 Hz (~0.08–0.25 s) are rounded
  **up** to 2–5 server ticks (0.10–0.25 s). Re-hits may shorten but never extend,
  resurrect or pay. NBT retains reward UUID/base/consumed + fuse/processed tick.
- Vendor registry identity is a hard deny even in creative, for every wrench mode;
  datapack tags cannot allow it. Vanilla blocks retain native mining. Damage
  remains normal/throw 5.1 HP, slam 10.2 HP, no extra crit; authoritative source
  sampling and the 37-source-update throw receiver gate are unchanged.
- Client registration is a union: wrench singleton, vendor, bolt, crate and falling
  renderers coexist. Effect world drawing/reload and wrench pose/detached hooks are
  separate subscribers; neither replaces the other's renderer or duplicates payload IDs.

## Known limits and explicitly excluded next work

Owner accepted wrench extension amplitude **1.0**; it remains a known visual
limitation, not a secretly applied 0.4 correction. Returning-wrench bolt attraction
is research-ready but **not approved or implemented**. Vendor/Blaster/wrench audio
mappings stay **UNBOUND**, not guessed. Existing procedural FX/material/PS2 fidelity
limits in [effects.md](effects.md) remain; this is not 1:1 emulation.

**Nanotech implemented, independent verification pending:** released/falling
clusters, original pickup sound and scaled healing; type59/60 healing ring/glints,
exact PAL timing and PS2 presentation/audio fidelity remain excluded. No broad Blaster #11 refactor,
shop/unfold feature, GPL implementation/dependency or proprietary Git assets added.

Live dedicated/client synchronization, remote observations/late join, ground/fall
transitions, HUD scale/reload and new combined in-world visuals are not proven by
headless tests. Prior owner branch playtests are acceptance of those branches, not
automatic validation of the merged candidate. See [handoff.md](handoff.md)
for P32 commands/results and the remaining T18 gate.

## Historical P29 integration record

**Bounded follow-up:** standard imported-material mining/destroy debris replaces
the white model-placeholder path; see [block-debris.md](block-debris.md). T15's base
`86ca1f7` verification is retained; affected independent verification and owner
confirmation of the new commit are still pending.

Local branch `integration/crates-vendor-bolts`, crate base `26d9ff5`.
The earlier combined base merged these owner-approved tips, preserving histories:

| Feature | Exact approved tip |
|---|---|
| Vendor visuals | `17773dc2704df4c258800b060c690fcd212ca378` |
| Bolt currency and imported HUD | `a9279fda56dd214dca7c97c1b4ef01d025ff3730` |
| Bolt motion, tests and documentation | `a6f45618669b7f53f8dc4ce5af292d25af2b8094` (production `ee1237a`) |
| Explosive behavior and temporal flash correction | `c4b2cca7d9f836acf5f0fb5caf60a72ccf31aee5` |

Original branches/worktrees remain untouched. The P29 record below predates the
final sound/effects/wrench consolidation above; its 6/6 versions and exclusions
are historical, not the current release matrix.

## Conflict and integration decisions

- Cache schema/converter are **6/6**. Vendor remains section 5, HUD uses section 6;
  neither section overwrites the other. Both are serialized/decoded with the shared
  keyed static-moby map containing all four crates and four bolt meshes.
- Manager readiness validates vendor, complete HUD and each required crate/bolt
  identity/nonempty geometry/textures for both loaded and newly built content.
  A structurally valid current-version cache missing any required feature triggers
  rebuild, not READY. Missing source media then fails closed.
- Client entity/block-entity/item registrations and reload listeners are the union
  of the approved features, including both vanilla falling-block and custom falling
  explosive renderers. Vendor 2× world fit and separate item fit are unchanged.
- Crate BE persists reward UUID/base/consumed alongside fuse/last-processed tick.
  The combined scheduled block tick captures original BE data for ordinary vanilla
  falls and uses the custom explosive carrier for explosive falls. The carrier
  updates its NBT overlay before vanilla's landing overlay; fuse timestamp still
  prevents double decrement. Rejected explosive spawn now preserves the source
  incarnation/fuse rather than deleting its only representation.
- Grounded detonation and blast-destroyed ordinary crates invoke the existing exact
  `Rac1CrateRewards.destroy(ServerLevel, BlockPos, BlockState, @Nullable Entity)`
  entry point **after** blast event/identity authorization. Its carrier overload
  consumes airborne emitters and blast-destroyed nonexplosive carriers using the
  same persisted placement UUID/base and ledger. One shared private award method
  owns the randomized roll and denomination emission.
- Arming/re-hitting/representation transfer never awards. Actual destruction
  reserves consumption before callbacks; repeated calls cannot pay the same
  incarnation twice. Start veto/denied emitter destruction does not pay or emit
  blast effects. Health/ammo remain zero-currency, without healing/ammo rewards.
- Original measured explosive base profiles (5–89), original spread/denomination
  policy, server-owned motion/ledger, flat 5 HP × exposure player-2/mob-3 damage,
  TNT-strength 8-block impulse, item/bolt immunity, terrain/fire exclusion and
  `c4b2cca` all-face partial-tick flash behavior remain unchanged.

## P29 measured checks (Windows / Corretto 21.0.4)

Set process-local `JAVA_HOME=C:\Users\giann\.jdks\corretto-21.0.4`.
No client was launched, controlled, restarted or closed. No ISO or extracted
asset data was needed for these synthetic checks.

```powershell
.\gradlew.bat compileJava compileTestJava --console=plain

.\gradlew.bat test --tests '*Rac1IntegratedCacheTest' --tests '*Rac1AssetCacheManagerTest' --tests '*Rac1CacheCodecTest' --tests '*Rac1VendorCacheTest' --tests '*Rac1Hud*Test' --tests '*Rac1BoltPayoutTest' --tests '*Rac1BoltMotionPersistenceTest' --tests '*Rac1ExplosiveTest' --tests '*Rac1CrateBlastPolicyTest' --tests '*Rac1CrateFallTest' --tests '*Rac1VendorItemTransformTest' --tests '*Rac1VendorWorldScaleTest' --tests '*Rac1CrateFlashTemporalTest' --tests '*Rac1CrateFlashTest' --tests '*Rac1MeshRendererTest' --console=plain

.\gradlew.bat --no-configuration-cache -I src/test/fixtures/integration.init.gradle "-PintegrationDirectory=<absolute private scratch directory outside Git>" runGameTestServer --console=plain
```

- Compilation: passed. Focused tests: **114 passed, 1 opt-in GPU test skipped**.
  No claim of running the entire final T suite.
- New cache regressions roundtrip/reload the combined sections twice and check
  missing vendor, missing HUD and each of eight required crate/bolt assets.
- New isolated headless namespace `racmc_integration`: **4/4 required tests passed**:
  deferred grounded chain/ordinary-neighbor rewards, repeated callbacks, zero-value
  health/ammo, existing launched bolt survival, exact ledger/motion roundtrip and
  pending-to-account value conservation; Start/self-consumption veto and rejected
  falling admission; combined falling/landing/save-load metadata; airborne
  self/nonexplosive-carrier rewards and repeated-callback idempotence.
- Fixture corrections are recorded honestly: first headless run passed 2/4.
  Comparing the entire moving ledger across 30 ticks was an invalid no-payout
  oracle; it now checks stable value/reward identity. The engine's mock login
  lacks a negotiated mod payload channel, so collection now uses an isolated
  server account with a packet sink, **not a client-sync test**. Second run passed
  4/4; final run after the shared award refactor and spawn-veto regression passed
  4/4 again. Initial standalone vendor cache tests also needed their old hardcoded
  section count/version/payload offset updated for the six-section format.

Synthetic structure generation is opt-in via the committed init script; its NBT
and runtime worlds remain outside Git. Logs/failed fixtures are retained privately
under `%LOCALAPPDATA%\Temp\opencode\p29-integration`.

## Outstanding gates

Independent T must verify the exact committed combined candidate and relevant
full suites. Prior individual T results and owner approvals remain historical
evidence, not automatic combined passes.

Live dedicated-server/client bolt and knockback synchronization, ground/fall
network transitions, HUD scale/end-to-end resource reload and combined-world
visual/playtest coverage remain explicitly **unverified here**. Shader GPU checks
were not repeated. This candidate does not establish arbitrary claim-plugin
compatibility, crash/fsync atomicity, or new Linux/macOS runtime results.
The historical standalone notes in feature docs do not override this status.

All integration evidence/worktree artifacts are retained for independent T and
owner review; no deletion, push, provider PR creation or provider merge is authorized
or performed by P29.
