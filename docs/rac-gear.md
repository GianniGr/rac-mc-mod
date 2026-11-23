# RaC Gear and the Clank item

Open **RaC Gear** from the normal inventory, or from the personal inventory
page in Creative. **Inventory** or Escape returns to the normal view. Put down
any stack carried by the cursor before switching. On narrow Survival windows,
close the recipe book to see the tab.

**Back** holds Clank. **Aerial** reserves one future position for either Heli
or Thruster; **Hydro** is a separate reservation. The first **Belt** position
fits Bolt Grabber only; the second is reserved. Display-only positions read
**Coming later** and are not inventory slots or keyboard stops. The starter
Heli-Pack still works through **Back**; it does not need the Aerial position.

## Bolt Grabber Belt slot

Bring a Bolt Grabber in main inventory or offhand and choose **Equip** in
Belt. A chest or cursor stack does not qualify. The server moves that one
stack, with its components, into a separate Belt save; the item does not
unlock Clank or touch Back. A loose gadget never enables the effect.
**Unequip** disables it immediately and returns the stack to main inventory
or drops it at the player if full. A rejected drop retains the exact item in
escrow while the server retries. Belt wear and pending returns survive
reconnect, dimensions, death and normal save/reload independently of Back.
Abrupt cross-file crash atomicity is not guaranteed.

The item has a Creative test entry. With verified local art ready, inventory,
Creative and dropped items use the original jointless **Quartu (L15) pickup,
class 1388**, with a uniform fit and Minecraft display angles. While that
art is unavailable the item remains usable but has no image; there is no
compass substitute. The approved Survival source is a 5% additive roll in
both ocean ruin chest variants and shipwreck treasure chests, implemented
separately. This Belt slice does not grant that loot or implement bolt
attraction.

When equipped, Bolt Grabber is visible in third person to the wearer and
players tracking them. The server syncs only a worn bit on that player
entity, not the Gear menu, inventory, or ownership. The visual follows
Steve’s posed lower torso on both skin types; the front-center waist
placement and armor clearance are Minecraft adaptations, not a claim
about Ratchet’s exact anatomical attachment.

The in-world layer uses original worn **class 618** from **Novalis (L01)**
on a verified local RaC1 disc. Both supported revisions decoded the same
mesh counts and static pose. The Quartu pickup, class 1388, is not a
substitute. Only the locally leased mesh and texture are used; the layer
does not bundle or export either, and hides the gadget when verified local
art is unavailable. This is a static textured presentation, not a replica
of the original glow behavior.

## Rescue, equip, and gift

- A valid natural Overworld pod rescue unlocks Clank and Heli-Pack and awards
  one real Clank item. It does not auto-equip. The pod's native marker and
  interaction checks apply in Survival and Creative; Spectators cannot rescue.
  A full personal inventory does not lose or drop the rescue reward. Make room
  in main inventory and the server will deliver it once.
- **Equip Clank** moves one valid Clank item from main inventory or offhand to
  the server's **Back** slot. Right-clicking a valid held Clank does the same
  thing and never toggles him off. A locked gift recipient unlocks and equips
  in one action; the item moves to Back and no new reward or rescue cue occurs.
- **Unequip Clank** stops backpack abilities and returns the same token. If
  main inventory is full the server drops the item at the player. If spawning
  fails, the Back slot is unworn and the server keeps retrying the return. It
  will not equip another item while a return is pending.
- Gift Clank after unequipping by physically transferring the loose item.
  The giver keeps the permanent unlock, but cannot wear Clank without another
  legitimate item. A chest item, a stack held on the UI cursor and the
  temporary test packs do not qualify.
- This candidate lets each reward unlock **one new gift recipient**. The same
  item can subsequently move between already-unlocked players, but forwarding
  it does not unlock a third player. A player already unlocked through a gift
  does not later receive a second token from a pod. Both are conservative
  anti-mint rules, not a trading or ownership economy.

The equipped token is persisted separately from the player's loose inventory.
Back therefore stays equipped after reconnect, ordinary save/restart,
dimension travel, and death—even with `keepInventory` disabled. An unequipped
item follows ordinary Minecraft inventory and drop rules. Movement and effects
are not persisted. Temporary Creative Heli/Thruster items and operator test
commands remain session-only; they cannot mint Clank or grant Gear. Automatic
Hydro still applies only to those test sessions. The Clank reward is not in the
Creative catalog; use a real container or the in-world drop key to transfer
it in Creative. The Creative catalog's client-authored slot edits and clone
gestures cannot move or copy a legitimate reward.

Clients see their own Gear snapshot only. A pod treats missing state as not
ready and hides its occupant once that player's unlock is known; existing
Clank tracking shows only the effective worn projection to others. The item
uses original class-601 Clank artwork from verified local media where
supported, with a neutral vanilla placeholder if the local model is unavailable.
No original asset is bundled or required on the server.

## Saves and verification

Normal saves persist unlock, token issuance, Back escrow, and pending returns
in `racmc_gear.dat`. A return preserves the token but rotates its server-only
seal so a stale loose copy cannot equip. The item stream never transmits the
seal. Transaction steps are server-thread authoritative and full-inventory
spawn rejection leaves a retryable pending return. Separate vanilla files
hold inventory and dropped entities, so abrupt interruption during their
writes is not a cross-file atomic durability guarantee.

Version-1 saves predate tokens and are left unchanged, not silently converted.
Proposed migration, subject to separate approval and a backup: use authenticated
v1 rescue records to provision one pending item each, clear legacy worn (there
is no stack to put in escrow), keep ownership, and require manual equip after
delivery. Do not fabricate an escrow token from fixture state.

Scoped automated tests exercise the real `Inventory`/`ItemStack` transfer,
token disk and wire behavior, unlock/replay, escrow round-trip, failed drop
retry and private view ordering. Dedicated native interaction, real reconnect
and restart, and live menu/renderer behavior need integrated verification.
The opt-in `gear-escrow.init.gradle` fixture exercises the native item-use and
Creative slot handlers, with a packet sink for the self snapshot (not a
Minecraft client). Run it with `integration.init.gradle`, a new private
`-PintegrationDirectory`, and `-PintegrationNamespace=racmc_gear_escrow`.
