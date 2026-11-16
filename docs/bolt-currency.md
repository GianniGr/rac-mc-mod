# Bolt currency candidate — issue #10

Standalone on crate base `26d9ff5`, with no vendor/explosive activation dependency.
**Not independently verified or owner-accepted.**

## Currency versus items

The four Creative bolt/nut items are named **Cosmetic**. They use original imported
meshes/textures, do nothing on use, and cannot be redeemed. Dropping, cloning,
storing or destroying them never changes currency. Cosmetic ItemEntities remain
ordinary items; they are not currency.

World currency is a separate non-item entity worth 1/5/20/50, with no XP,
inventory, hopper or stack conversion. Overworld SavedData `Rac1BoltLedger` owns
accounts and every uncollected value. Entity UUIDs identify disposable views of
ledger records. Collection consumes one record and credits one account once.
Balances survive death, respawn, logout, dimension changes and normal save/restart.
There are no corpse drops or automatic deductions.

Conservative Minecraft adaptations:
- Any living nonspectator player, including Creative, may collect. Nearest wins
  acquisition, UUID breaks ties; a valid acquired target stays selected.
- Homing passes walls. No expiry/distance deletion. Unloaded chunks defer emission;
  rejected spawns retry exactly the same value without choosing a beneficiary.
- Currency ignores damage/fire and **all repeated standard explosions**, overriding
  `ignoreExplosion` and damage policy. This deliberately protects other hazards too.
- Void recovery clamps the view above minimum world height. Entity removal can be
  repaired from the record. Arbitrary mods deleting SavedData are outside the contract.
- Only the ledger saves views, not separate chunk entity files; position persists,
  transient target/speed reacquire. This does **not** promise fsync/crash atomicity.
  Overflow rejects collection without consuming its record.

## Original rules and deliberate limits

R05 measured 794 wooden and 88 explosive placed records per disc across original
levels 0/1/2/4/9/14/18. Placement samples that measured distribution by instance
count and stores its first authored amount plus unique incarnation in block NBT.
Equivalent amount profiles are aggregated in the selection table. Falling
transfers both unchanged; reloading/mining cannot reroll a placed profile.

Fresh ordinary payouts use original inclusive spread: A/4 for A>=8, one for
2<=A<8, zero otherwise. No invented 5–150 cap. Health/ammo give no currency.
Explosive crates are eligible although this branch does not detonate them.
Metal crates are not registered in the base and are not added here.

Verified non-greedy allocation: 50 while remainder>=300, 20 while >=200,
20+5 pairs while >=30, 5 while >=7, then ones. Spawn order 50/20/5/1.
Low-pool approximation is deliberately replaced by exact pending records.
Challenge, mission, alternate/revisit and tracked-source save modifiers have
no invented Minecraft equivalents.

Attraction uses strict horizontal radius 3 and vertical reach 1.75 every server
tick. Homing speed approaches 48*dt in increments of 16*dt², dt=.05.
Views currently start in idle proximity mode: original ballistic launch,
collision/settling phases, launch drag, ground-specific motion, glints and audio
are **not implemented**. This is not a complete original drop-motion port.
Original denomination scales .65/.70/.75/.60 preserve class geometry; no crate
cube normalization is applied.

## Integration seams

```java
Rac1CrateRewards.destroy(ServerLevel level, BlockPos pos,
                       BlockState expectedState, @Nullable Entity cause)
```

Checks current state/BE, reserves the incarnation, removes the block once and
records its one randomized reward. Does **not** detonate. Player breaking and
ordinary explosions use it; `getDrops` never awards. Canceled upstream destruction
must not call it. Call synchronously for the current incarnation, never with an
expected state retained across replacement at that position.

P25 may invoke it for destroyed nonexplosive neighbors and exploded self.
P25 owns activation/fuse/falling mechanics. Consolidate overlapping block/BE
changes only after separate T verification and owner playtesting of each branch.

Future vendors must adopt `Rac1BoltLedger.balance(UUID)` and
`tryDebit(ServerPlayer,long)`, not a second balance. Server main-thread callers
only; no client purchase/credit request exists here. Owner-only one-way sync
carries total/revision/pickup cause; login/respawn/spending do not trigger the HUD.

## HUD blocker and cache

The current **temporary Minecraft-font text** is not original art. It hides
4000 monotonic milliseconds after the last distinct successful pickup, including
time in menus. New player/disconnect clears transient timing. Owner acceptance of
this temporary presentation remains pending.

A bounded read-only probe confirms both discs' HUD header and five bank routes.
Current importer still lacks proven icon-ID-to-frame/bank mapping, palette/alpha/
UV decoding, and ordinary HUD font/metrics. Original panel/icon/font import is
**blocked**, not proven impossible. No fake original art or broad new pipeline.

Existing keyed static-moby cache encoding stores four extra bolt assets.
Converter 4, unchanged schema. Missing/wrong/empty bolt assets invalidate readiness.
Vendor cache changes are **not integrated**; later consolidation must reconcile
converter invalidation and required asset sets.

## Verification status

P checks passed: compile; focused payout/HUD-clock/ledger/cache checks; read-only
real-disc bolt conversion on NTSC-U and PAL v2.00. No extracted assets in Git.

Headless GameTests supplied: repeated TNT/creeper/standard mod blasts and direct
explosion damage, reward/collection exactly once, ledger roundtrip, player-break
eligibility and falling profile retention. **Not executed successfully**:
runner cannot find synthetic `racmc:empty`; in-memory BeforeBatch initialization
is too late for structure sizing. Three bounded fixture corrections exhausted;
further attempts stopped for escalation. Gradle even returned success once while
the server said "No test functions were given"; that was not counted as passing.

Separate T must repair/verify the fixture before runtime claims, then verify
save/restart, partial/rejected admission, two-player contention/retargeting,
idle/attracted explosions, cancellation and client sync/rendering. Actual
Creative/first-/third-person and HUD playtesting remains pending. Do not consolidate
or present this candidate as finished gameplay.
