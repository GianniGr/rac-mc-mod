# Survival gadget acquisition (v0.1-beta)

| Gadget | Eligible vanilla loot tables | Chance |
| --- | --- | ---: |
| Bolt Grabber | `minecraft:chests/underwater_ruin_small`, `minecraft:chests/underwater_ruin_big`, `minecraft:chests/shipwreck_treasure` | 5% each |
| PDA | `minecraft:chests/village/village_cartographer`, `minecraft:chests/stronghold_library` | 5% each |

These are exact loot table IDs, not structure, biome, or chest-block matches.
For example, shipwreck supply and map chests and other types of village chest
are not eligible. NeoForge checks the exact table before making one roll.
A successful roll appends one gadget to the vanilla loot list; it does not
replace the table or remove vanilla loot. Vanilla handles fitting and
shuffling the resulting items into the chest inventory.

## Unopened chests

Vanilla leaves many generated chests unopened with only a loot table and
seed saved. It rolls the table when it first unpacks the inventory, which
can happen through an inventory action other than a player opening the GUI.
An older chest whose loot table has **not yet been unpacked** can therefore
use these rules on its first unpack after installation. A chest that already
contains materialized loot is not rerolled, including after a save/reload.
There is no structure scan, retrofit, or change to inventories at world load.

## Combined-build requirement

This loot slice does not define the gadgets or manufacture guessed item
stacks. The owning gadget implementations must bind their exact registry
IDs and canonical fresh Survival stack factories through
`Rac1ChestAcquisition.bind`. A factory runs on a successful roll and must
work even when no player is present, preserving any components/provenance
required by the real gadget. A missing, invalid, or failing binding leaves
vanilla loot unchanged and is logged. The modifier aliases are not item
registry IDs. Live item and persistent chest verification belongs to the
build that combines both real gadget implementations with this loot slice.
