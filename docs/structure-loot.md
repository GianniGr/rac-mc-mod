# Natural RaC supplies near Minecraft structures

Newly generated Minecraft structures in the Overworld can have RaC supplies.
This places the existing blocks; it does not change the RaC Gear tab or add a
Gadgetron shop.

## Placement contract

- All 29 vanilla Overworld structure IDs are eligible, including buried treasure,
  underground structures and ocean variants. See the `racmc:structure_supplies`
  structure tag for the complete allowlist. The Nether and End are not affected.
- Every eligible start **attempts crates (100%)**. The provisional target is
  **3–5 separate groups of 3–5 crates**, not a guaranteed block count. A cramped
  start can place fewer/smaller groups, one isolated crate, or nothing if it
  has no safe site.
- There is a separate **20% roll per start** for one bonus group of **12–16**
  crates. A smaller bonus can fit down to six crates; if that cannot fit, the
  bonus is skipped. Normal attempts still happen. Crate type weights are
  **60% bolt, 15% health, 15% ammo, 10% explosive**. Only the bonus has a
  **three-explosive cap**; later explosive rolls are redistributed among the
  other types in their original relative proportions. Defaults are in
  `StructureSupplyPlan`; there is no live settings screen.
- Non-villages keep their independent **15%** vendor roll and at most one
  vendor per start.
- Each village requests **one vendor**, without a probability roll. Exhausting
  the safe-site search is recorded in the server log with the structure ID and
  owning chunk; no destructive fallback is used. For example, a Superflat
  setup that is solid up to the build ceiling can register a vanilla village
  without leaving space for any block.
- In Survival, an iron-tier or better pickaxe or a vanilla golden pickaxe can
  recover one placeable vendor. Wrench and Blaster interactions are unchanged.

## Placement and save rules

The unit of placement is the vanilla structure ID and its start chunk, not
each intersecting chunk. Each start gets its own seeded draws from the world
seed, ID and start chunk coordinates. Adjacent start chunks own disjoint
placement areas. Vendor sites are reserved first. If multiple starts share a
chunk, villages go first, then starts are visited in ID order within each
placement pass. Every start gets its first ordinary attempt before any bonus
or later group. Bonuses are attempted before additional ordinary groups so the
smaller groups do not fill or divide all the remaining space.

The search uses the inner 12×12 columns of the start chunk. The two-block
border lets the planner verify an access lane and door clearance without
querying another chunk. It needs stable support, an empty target and headroom,
and space to stand or swim without blocking the local approach. It avoids dirt
paths and the area immediately around doors, gates and trapdoors. The vendor
search is unchanged: villages exhaust the buildable height in those columns,
and non-villages use their existing piece/surface/seafloor anchor.

For crates, every family can use dry or submerged sites. Exposed families
search around the local surface on land or the floor beneath source water.
Interior families search around the starting piece height for dry or flooded
floors, rather than moving an underground cluster up to a distant surface.
Both searches stay within 12 vertical blocks of their reference height.
Underwater placement replaces only individual source-water cells on stable
support, with space to swim past. It does not drain an area or displace
construction or loot.

Ordinary groups use short rows, compact two-wide shapes or connected stepped
layouts; bonuses use compact two-wide formations with access on their outside.
At similar heights, groups are separated by at least one empty column between
their edges. The packer protects the headroom and approach for every block
already planned and considers at most 192 available candidates per group
attempt. A large structure does not expand placement into neighboring chunks
to meet its target; terrain and other starts can reduce the count.

Only fresh vanilla structure starts write a pending decision marker. The
marker lives in the owning chunk and can survive a save while that chunk is
still a protochunk. A server tick consumes it and places the blocks together
when that chunk is already available; neither the planner nor the scheduler
force-loads chunks. Loading an old structure without this marker does
nothing. Once consumed, the decision is not rerolled, even if all the
supplies are broken, and neither the vendor nor the crates respawn.
