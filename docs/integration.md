# Approved gameplay integration candidate — P29

**Bounded follow-up:** standard imported-material mining/destroy debris replaces
the white model-placeholder path; see [block-debris.md](block-debris.md). T15's base
`86ca1f7` verification is retained; affected independent verification and owner
confirmation of the new commit are still pending.

Local branch `integration/crates-vendor-bolts`, crate base `26d9ff5`.
Only the following owner-approved tips are merged, preserving their histories:

| Feature | Exact approved tip |
|---|---|
| Vendor visuals | `17773dc2704df4c258800b060c690fcd212ca378` |
| Bolt currency and imported HUD | `a9279fda56dd214dca7c97c1b4ef01d025ff3730` |
| Bolt motion, tests and documentation | `a6f45618669b7f53f8dc4ce5af292d25af2b8094` (production `ee1237a`) |
| Explosive behavior and temporal flash correction | `c4b2cca7d9f836acf5f0fb5caf60a72ccf31aee5` |

Individual owner acceptance is not independent verification of this combined tree.
Original branches/worktrees are untouched. No wrench, sound/particle pipeline,
shop/unfold feature or Blaster refactor is included.

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
