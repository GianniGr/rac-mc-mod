# Bolt Grabber attraction

Bolt Grabber passively extends ordinary bolt pickup range **while equipped on the
Belt**. A loose item does nothing. The server tests a cylinder around each
eligible player's feet: strictly less than **12 blocks horizontally** and
**4.5 blocks vertically**, versus **3** and **1.75** without the gadget.

The bolt tests this range on falling contact, using its resolved collision
position, and during idle before the visual hop. It does not admit by range
while settling or during unchecked free flight. Falling strictly more than two
blocks below both the launch height and a player's feet retains the independent
low-fall bypass; the Belt does not extend or gate it. At contact, a player who
passes the proximity test takes priority over bypass-only candidates. Within a
tier, the nearest eligible player wins, with canonical UUID text order breaking
ties. The admission test and accepted trajectory share one collision solve.

## Minecraft adaptation

The original RaC1 upgrade is passive while **owned**; this mod uses effective
server-confirmed Belt equipment instead. Unequip affects only new admissions:
an already-admitted bolt follows the same player's moving body, even after
unequip. If the target becomes invalid, the bolt returns to idle without
awarding value, and any later admission checks current equipment. Admission
does not require line of sight; wall collision during scatter and existing
through-wall homing are unchanged. Credit is issued by the existing ledger only
when the bolt arrives. This does not extend returning OmniWrench attraction,
retarget a locked flight, or affect other pickups.

The range and phase rules are based on retained R18 evidence for both
RaC1 PS2 revisions (NTSC-U SCUS-97199 v1.00 and PAL SCES-50916 v2.00). See the
[NTSC hero range update](https://github.com/lombyte-project/Lombyte/blob/5497cf4394bb60bac5d8a82504df76c04656c186/src/overlays/shared/hero/update_hero_and_vehicle_mode_effects.c#L568-L585)
and [PAL bolt state update](https://github.com/OpenRAC/rac1-decomp/blob/f911d6e4b9711071f0daaad95b4a04c2c8aa3c61/src/overlays/shared/bolts_and_wrench.c#L958-L1051).
Multiplayer arbitration and the equipped-only rule are mod behavior, not claims
about the original game.

## Verification

This gameplay slice builds with the Belt implementation supplying
`Rac1BeltServer.isEquipped(ServerPlayer, Rac1BeltGadget.BOLT_GRABBER)`.
On a tree containing both slices, run the focused unit tests and the opt-in
headless GameTest in a fresh private directory:

```powershell
.\gradlew.bat test --tests "com.github.giannigr.racmc.loot.Rac1Bolt*Test"
.\gradlew.bat --no-configuration-cache -I src/test/fixtures/bolt-grabber.init.gradle "-PboltGrabberDirectory=C:\approved\private\bolt-grabber-case" runGameTestServer
```

The native fixture uses synthetic connected server players, registered Belt
payload dispatch and ordinary ledger-backed bolt entities. It does not open a
client, exercise remote wire transport, or require original media.
