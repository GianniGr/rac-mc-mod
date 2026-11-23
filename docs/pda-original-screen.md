# Original PDA remote presentation follow-up

Base owner-playtested PDA `62d569d` remains preserved on its original branch.
This isolated follow-up replaces the explicitly adapted widget screen with an
**original-source remote-menu reconstruction candidate**, not native pixel/AV
acceptance. No retail image/audio/mesh asset is bundled or exported.

## What the original path actually is

Pinned ReRAC `ae1e3432a0d5e21bd08d3872d3b04afef98be666` documentation was read
first; it leaves remote presentation unported. Independent bounded Novalis
instruction analysis then establishes:

- `OpenVendorMenu(0)` creates **class11**, positions it relative to the current
  camera, sets remote mode, and **cuts directly to open sequence3**. There is no
  distinct flat “PDA screenshot”/atlas identity established by that path.
- NTSC remote flag is state`0x1ca940+0x40`; PAL state is relocated`+0x280`.
  The original camera-relative vector is **(3.8,0,1.5)** on both regions.
- Remote drawing shares the original class11 monitor/body, class12 portrait,
  class13 product backdrop and class1137 quantity popup paths. It omits the
  above-pad hologram/cone and camera fly-in. The same source font/icon/string
  tables are genuinely referenced by the PDA branch, not borrowed by guess.
- Item-panel original vendor ammo price stays at(118,101), crossed by seven
  source-coloured rectangles (RGBA0x20..0x80,959544); PDA unit price is at(118,85).
  Blaster is1→10. This branch still intentionally displays only the accepted
  Blaster slice; Bomb catalog integration is separate.
- Source60015 frame2 is the ammo strip icon. Original small/regular font metrics,
  font atlases, LED font/ticker references, byte-encoded strings and regional
  Buy/Back/Yes/No/How-many/Maxed roles come from the local original resources.
  Native source HUD documentation pins the bolt counter in mode5.

`Rac1PdaRemoteProgram` verifies six primary region-specific program windows against
independently retained numeric hashes (open, buy, draw, monitors, popup and glass).
`Rac1PdaOriginalSource` uses the existing verified-media lease, reuses the existing
local graphics-sidecar format, and explicitly avoids reading spoken vendor data.
The original item619/icon60032 renderer remains unchanged.

## Exact source cue bindings

Remote call sites use the created class11 object. Both supported-disc mappings,
grain metadata, nonlooping ADPCM frames and hashes have been checked:

| Source role | Local slot | Novalis cue | Grain | Frames |
|---|---:|---:|---:|---:|
| Select / popup cancel |0|68|302|107|
| Cursor / quantity move |1|69|303|61|
| Initial insufficient / Maxed denial |2|70|304|514|
| Open entry |3|71|305|616|
| Screens on |4|72|306|504|
| Back / exit input |5|73|307|589|
| Closed |6|74|308|345|
| Purchase |7|75|309|660|

These are **actual PDA remote dispatches**, even though the original implementation
shares class11. No separate kiosk-world/fold sound is invented or triggered.
No Gadgetron spoken line, weapon pickup sound or vanilla widget click is used.
Source tone rates/gains are retained (close has centre77/fine6,16474.17Hz;
others72/0,22050Hz), not all forcibly played at a common pitch.

The bounded NTSC item32 dispatcher independently resolves jump-table entry
`0x209350` to`0x24145c`; it calls`OpenVendorMenu` at`0x2414a0` with
`a0=0` in the delay slot, then exits the case at`0x2414a8`.
There is no direct class-sound call in this case, and gadget619 declares zero
sound definitions. Two preceding helper calls (`0x220e30`, `0x27b940`) have
not been independently audited here; absence of every possible upstream
activation cue is therefore not certified.
The retained notes and pinned menu documentation do not establish a separate
item-activation sound. That unverified pre-menu activation edge remains silent;
only the independently verified remote-menu roles above are dispatched.

Only a matching accepted server buy receipt can cue purchase. Initial deliberate
Buy denial alone can cue70; no cue when a purchase merely reaches cap, on a
re-quote, later failure, replay, failed inventory fit or an unrelated receipt.

## Explicit Minecraft differences and remaining fidelity gates

- The world stays visible, live/nonpausing and vulnerable; no source-world snapshot,
  global sound-group pause, camera move, hero hiding or physical kiosk motion.
- Private rendering scales the source512×416/448 composition into the Minecraft
  window; the imported bolt HUD uses the existing responsive Minecraft anchor.
- Mouse/keyboard map the original pad actions. Mouse Buy→Yes additionally requires
  release/movement and a500ms read gate; source quantity movement is re-quoted by
  the authoritative server and cannot confirm stale/unrendered totals.
- Source control cues are private Minecraft PCM one-shots, not a recreation of
  the EE/IOP mixer, spatial/reverb/pitch-bend random stream. A deliberate close
  invalidates the session immediately; the8-reference-frame audio separation is
  rounded to3client ticks. Source ready/open and screens-on cues are client-tick
  scheduled. Successful purchase audio waits for the server, never precedes commit.
- The portrait uses an original idle pose without a spoken controller. Ticker
  cadence is a private60Hz-reference cosmetic clock, not certified PAL/native
  frame timing. Network/fit/uncertainty errors appear in a clearly marked MC footer.
- **Not certified:** native pixel equality, complete CRT/glass/static pass assembly,
  the class13 ammo-quantity backdrop at popup+0x500, native weapon/ammo HUD anchors,
  portrait/ticker cadence, and live audio/lifetime acceptance. Those are explicit
  presentation gates, not silently called “unavoidable” adaptations. Current hidden
  GPU checks show source reconstruction/coverage and browse/quote differences,
  not original-console image equivalence. Do not call a failed import a fallback
  original screen; it displays a diagnostic and permits only close.

## Catalog boundary

P44`efe22d2` plus exact P34 gameplay`7cfbf92` is prepared on a separate integration
tree. The Bomb free one-time starter+exact10 is an explicitly marked **Minecraft
acquisition adaptation**, never an authored original retail kiosk offer. That tree
must bind selections and confirmations to `Quote.offer()` and the real P34 registry
before integration. No Blaster price/cap/grant, shared ledger or session mutation
is made by this presentation branch.

## Candidate verification scope

Normal Java build/test/JAR: 1,071 passed,77 conditional skips,zero failures.
Separate opt-in NTSC and PAL verified-media tests positively import remote
program/audio/graphics; hidden GPU tests exercise browse and quote composition.
The narrowed NTSC item32 dispatcher probe also passes. All are offline/hidden
checks, not an interactive Minecraft or original-console comparison. The candidate
requires the user's already configured verified local original media and cache.
No assets are included in the normal JAR. Outstanding rendering gates above
remain visible acceptance limitations, not a claim that the candidate is native.
