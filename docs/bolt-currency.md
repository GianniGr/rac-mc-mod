# Bolt currency candidate — issue #10

Originally standalone on crate base `26d9ff5`. The owner-approved bolt/HUD and motion
branches are now combined with vendor/explosives on the local integration branch.
See [integration status](integration.md) for the current candidate and pending independent gates.
Standalone verification notes below are historical, not the combined result.

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
Original ballistic launch, collision/settling phases, launch drag and ground-specific
motion now come from the approved P26 branch; see [motion contract](bolt-motion.md).
Glints and audio remain unimplemented.
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

## Original HUD and cache

The owner-authorized HUD follow-up resolves the prior decoder blocker: original
panel, thirty icon frames, regular font/metrics and layout data import from both
supported revisions into the local converted cache. Temporary native-font text is
removed. See [HUD asset mapping and limitations](hud-assets.md).

The HUD hides 4000 monotonic milliseconds after the last distinct pickup, including
menus, and clears transient state for a new player/disconnect. Original graphics
are not substituted while the cache is incomplete.

The four bolt meshes still use the keyed static-moby map; reusable 2D HUD assets
use a bounded section. P32 schema7/converter7 invalidate older standalone and
combined caches; see [integration.md](integration.md).
Vendor/HUD have separate sections; all four crates, all four bolt meshes, vendor
and HUD are required for readiness.

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
