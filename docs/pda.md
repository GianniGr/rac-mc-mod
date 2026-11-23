# PDA — portable UI, original item resources and P38 contract

## Stable acquisition interface (P38)

- Registry ID: **`racmc:pda`**.
- Java holder: `com.github.giannigr.racmc.item.RacMcItems.PDA`.
- Standard valid stack: `new ItemStack(RacMcItems.PDA.get())` (count 1).
- No UUID, custom data, entitlement, charge, durability or initialization component is required.
  Session identity is ephemeral server state, not loot NBT. Maximum stack size is 1.
- Owner-approved Survival adaptation: **5% additive chance** in village cartographer
  (`minecraft:chests/village/village_cartographer`) and stronghold library
  (`minecraft:chests/stronghold_library`) chests. **P38 owns all acquisition/loot edits.**
  P35 adds no recipe, loot table, modifier, seller or acquisition hook.
- Include PDA in the v0.1-beta combined PR. Standalone handheld right-click; no Belt API.

## Research, not recollection

Pinned reference: ReRAC `ae1e3432a0d5e21bd08d3872d3b04afef98be666`,
`docs/plan/{menus,interaction,gadgets}.md`. Documentation facts only; no third-party
implementation copied or depended upon.

- `menus.md:351–358,362–375`: portable remote mode sells **ammo only for owned
  weapons**; Blaster PDA unit price **10 bolts**, kiosk **1**, cap **200**.
  Prices are per-item, not a universal markup (e.g. Bomb Glove 5→25,
  Pyrocitor 1→5, Mine Glove 5→15, Tesla Claw 1→10, RYNO 20→50).
- Independently retained R12 `original-shop-metadata.jsonl` (2026-11-18), both
  NTSC and PAL rows: item 15 `ammo_pda_unit=10`, `ammo_vendor_unit=1`,
  `max_ammo=200`. Its `ADDENDUM-original-shop.md` §2 B1 records the bounded
  both-disc probe and table locations `0x1C4530`/`0x1C47B0`.
  Re-read the numeric evidence for this checkpoint; **no new retail reads**.
- `interaction.md:118`: Oltanis purchase prompt 1,000 bolts.
  Secondary corroboration: Ratchet Galaxy, “Gadgetron PDA” gadget page;
  community wiki “Gadgetron Personal Delivery Assistant”: sold by Qwark as
  “Steve McQwark”, Gorda City, Oltanis, 1,000 bolts. This is original acquisition
  context, **not** a Minecraft seller implementation.
- `gadgets.md:17,109`: item 32/class 619, **hand slot**, selectable hand gadget;
  not automatic belt-worn equipment.
- `menus.md:351–352,415`: original remote menu spawns a class-11 vendor, no
  camera fly/hologram; PDA price presentation strikes the old price.
  This is not proof that the ordinary kiosk screen can simply be reused unchanged.
  Later detailed `interaction.md` explicitly leaves remote presentation unported.

## Portable UI and sound adaptation

Owner permits narrow purchase/denial handling reuse **after checking PDA cue
evidence**. The pinned documents above do not establish a PDA-specific cue-to-bank
mapping or prove remote-mode cue parity. Do not infer parity from shared entry code.
The supported source path is `HeroPdaGadget` `0x240ed8` → `OpenVendorMenu(0)`
`0x2ae1a0` → remote mode `0x1ca980`; ordinary buy flow is `0x2af7e8`.
`interaction.md` identifies ordinary class11/local2 denial and local7 purchase roles,
but does not prove the remote branch predicates/remaps. The minimal outstanding
original-cue check is those remote buy/deny call sites and selected class sound
bindings, not a scan of all banks. No new original audio binding is inferred.

Owner now explicitly approves a **Minecraft interface adaptation** where original
remote composition is unverified. `Rac1PdaScreen` labels that adaptation on-screen,
uses existing Minecraft buttons/font and visual result messages, and shows the
live world without full-screen dim/blur, snapshot, pause, kiosk, salesman, camera
or unfolding/folding motion. Quantity1–200 → server quote → separately presented
quantity/total → explicit confirmation. Cancel quote reserves/spends nothing;
Close invalidates only this player's PDA session, not a committed purchase.

**Sound adaptation:** standard Minecraft widget activation clicks only; these are
input feedback, not purchase-success or denial cues. Success/denial/Maxed/full are
visual messages based on the matched server result. No imported PDA purchase/denial
sound, vendor speech, physical unfold/fold sound or pickup sound. Any future original
cue reuse still requires evidence or an explicit documented adaptation.

## Original item visual source and release gate

Pinned `gadgets.md:13–17,120–130` identifies item32's own hand-gadget **619** and
HUD icon **60032**, not a vendor asset. Bounded CPU-only imports on **both supported
images** independently found gadget619 with4joints/1071normal vertices/1815indices/
1texture/3clips, and icon60032 with two32×32 frames, matching decoded pixel hashes.
No retail images, meshes, palettes, audio or converted caches are bundled in Git.

`Rac1PdaImporter` consumes the existing verified-media **lease**, not a second
validator or direct file-open fallback; `Rac1PdaVisuals` prepares on one bounded
asset executor and reuses the result per selected-media/reload generation. Item
rendering never waits for media reads. Unsupported/absent resources draw nothing,
not generic compass/book art, vendor geometry or missing-texture stand-ins.
Inventory/Creative GUI uses original60032/frame0; other item contexts use the
original619 mesh sampled at sequence1/time0 (the documented original item-preview
sequence). Vanilla `builtin/entity` JSON only wires the custom renderer/transforms.

**Still a release gate:** visible original inventory/Creative, first/third-person,
held-left/right and dropped-item presentation must be owner-reviewed. Source
identity/CPU conversion is verified; exact original icon grid variant-base choice,
native held pose/grip, material/edge/color fidelity and Minecraft fitting/orientation
are not certified by CPU tests. Fitting/orientation are explicitly provisional
Minecraft adaptations (also stated in the item tooltip), not native appearance parity.
Visual import readiness does not block the separately adapted purchase screen.

## Portable commerce boundary

Right-click client expectation accepts only a matching server-issued token for the
same player/level/hand/slot/exact stack within5seconds; unsolicited/late opens close
their own token. Screen replacement, Esc/Close, logout, resource reload, media
generation, death/dimension/held-stack/menu changes invalidate the private session.
Uncertain requests retry only their exact sequence twice, then block fresh spending
until the matching receipt or close. No optimistic inventory or bolt mutation.

Server-authoritative, ammo-only Blaster slice. Dedicated `BLASTER_AMMO`, cap 200
including offhand; present Blaster required. No weapon sale or other unsupported
weapon stock. Separate item-context tokens (no fabricated placed-vendor position).
Validate living owner, hand, selected slot, exact held stack lifetime, dimension,
inventory-menu eligibility and expiry before requests **including replay**.
Confirmation consumes a server quote; complete inventory-fit staging and the existing
atomic inventory/ledger commit provide no debit or dropped remainder on failure.
No shared kiosk presentation/voice/clock behavior changes are intended.

### Verification limits

JVM tests use real vanilla inventory operations with Bow/Paper stand-ins for the
unbound mod registry. They cover exact PDA price, cap/offhand, ownership, fit,
confirmation/replay, invalidation/expiry, independent owners, kiosk/PDA quote
separation, ledger failure handling, unchanged held-stack reference preservation
and bounded portable codecs. They do **not** prove real registered-item loot,
network delivery, game event lifecycle, imported appearance, AV or live usability.
No game client/server launch is part of this checkpoint.

Initial server-only checkpoint `524e0da`: **1,050 passed /73 skips /0failures**,
including23PDA tests. UI adaptation is separate commit `8e89c31`.
Final follow-up suite: **1,065 passed /74 conditional skips /0failures/errors**,
including38PDA flow/commerce/wiring tests. Separate opt-in resource/pose/lease
checks passed on both supported discs (no interactive/game-server launch).
Interactive/live network, AV and visible-item acceptance remain untested here.
Original Gadgetron client/shop presentation,
sessions, purchases, network and atomic commit sources remain unchanged at `708b94a`;
the sole shared commerce edit is the ammo staging price parameter with the
ordinary kiosk path still explicitly fixed at 1 bolt/unit.
