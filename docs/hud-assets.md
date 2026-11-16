# Original bolt HUD import

The HUD uses assets imported from the player's validated disc, not bundled art,
Minecraft font glyphs or drawn substitute panels. The existing currency, pickup
revision/notification protocol and four-second timeout are unchanged.

## Reusable 2D path

- `Rac1HudBankDecoder`: bounded HUD definition/frame/page lookup and linear 8-bit
  indexed texels with GS CSM1 palette ordering, RGBA and PS2 alpha conversion.
- `Rac1HudFont`: ordinary atlas plus original u/v/top/advance records.
- `Rac1HudAssets`: named sprite sequences, fonts and numeric properties.
- `Rac1BoltHudDefinition`: this HUD's selectors and completeness requirements.
- `Rac1HudImporter`: validated-disc extraction of the required bank assets, regular
  font and retail layout values; no raw source container in the converted cache.
- `Rac1HudCacheCodec`: bounded, versioned 2D cache section. Outer cache schema **5**,
  converter **5**, new section kind **5**. Old/incomplete caches rebuild.
- Shared client drawing/texture lifecycle supports cache replacement, resource
  reload and logout; textures are not allocated per frame.

Nothing is added to native texture/font resources, and no external dependency or
upstream source code is copied. Upstream GPL-marked decompilation was consulted
only for format/behavior facts, then checked against the authorized retail bytes.

## Measured mapping — both supported revisions

Both HUD headers and all five decompressed banks match byte-for-byte. The ToC
header route is 0x14F8; five bank ranges start at 0x1500. Decompressed bank lengths
are 131072, 326912, 0, 0, 70656.

The header has 57 definition records (including terminator), 326 frame references,
42 palette pages and 236 image pages:

| Field | Offset / representation |
|---|---|
| Definition/frame counts | packed u16/u16 at 0 |
| Definitions / frame refs / palettes / images | pointers at 4/8/12/16 |
| Per-bank cumulative palette/image ends | 0x14 / 0x34 |
| Expected decompressed bank lengths | 0x54 |
| Definition | u16 id/count/base, flags/padding, eight bytes |
| Frame reference | u16 palette index, u16 image index |
| Page | bank-relative offset with relocation high bit, GS runtime slot, width/height logs |

`0x754F` selects exactly **30** 32×32 bolt frames, reference indices 261–290.
Palette 19 lives in bank1; images span bank0/bank1. The first/last image indices
are 83/204. No old 60-asset claim is used.

`0x7580` selects two 32×32 panel frames, references 305/306, palette17 and
images218/219 in bank1. The renderer draws right cap, stretched middle, left cap.
The original “flipped” right-cap strip reverses **both** UV axes (180 degrees),
not just a horizontal mirror. All frames use their full image UV extent.

The ordinary font is **Novalis effect texture slot1**, index table at core header
0x5C, payload base at 0x68. Its palette/pixel offsets are 2048/3072 and atlas size
256×128. It is not ToC's debug font.

Original regular metrics are read through ELF32 PT_LOAD address translation:
NTSC boot VA 0x1DF050, PAL boot VA 0x1DF3D0, 0x3A0 bytes each. Metrics and atlas
match between discs. Digits use u=0,16,…144, v=64, top=0, advance=13. Apostrophe
uses (64,80), top=-8, advance4; dot uses (0,80), top0, advance6.
Font RGB is normalized from GS neutral128 to Minecraft neutral255, preserving alpha.

Novalis overlay segments supply layout data at NTSC VA0x15F824/PAL VA0x15F904:
eight panel-opening steps, eight text-fade steps; clear/opaque PS2 colors
0x00E08060/0x80E08060; text offset(-32,8); apostrophe(-30,21);
dot(-29,10); digit width13. Digit strings are right-aligned with shadow offset(1,1);
grouping marks use the original separate placement/shadow(2,2).

Retail initialization maps PS2 German4 to game3 and Italian5 to game5; those
languages use dots. English/French/Spanish use apostrophes. Unsupported Minecraft
languages use the original English grouping policy.

## Minecraft adaptations and remaining verification

Artwork, glyph UVs/metrics, colors, local panel layout and frame selection are
imported/verified, not guessed. Screen anchoring is responsive: half-size original
pixel units, top-right with 32 original-pixel anchor inset. This is a Minecraft
layout adaptation, not a claim of identical PS2 screen-coordinate placement.

Original modulo60/shift-one icon indexing and eight/eight visibility counters use
nominal regional 60/50Hz monotonic UI steps. No emulator timing trace is claimed.
Closing is moved *inside* the owner's four-second deadline, with full invisibility
at that deadline. Another distinct pickup extends the deadline without collapsing
an open panel; a pickup during closing reverses it. Time in menus counts.

No original graphics are substituted or claimed while the converted HUD is
unavailable: the HUD layer draws nothing, and the existing importer/cache gate
reports an incomplete or failed import. The old plain-text HUD is removed.

Focused tests cover indexed/CLUT/alpha/frame lookup, malformed bounds, ELF/overlay
mapping, cache version/roundtrip/truncation/missing data, texture release/reload
lifecycle, language/index policies, repeated pickup and exact timeout boundaries.
Read-only real-disc tests validate both revisions, all 32 required frames, font
and layout, reference pixel/metric digests and converted-cache roundtrip.

Actual Minecraft overlay placement, blending, readability at GUI scales, resource
reload under the graphics driver and pickup-driven presentation remain **runtime
unverified**. Separate T13 and owner playtesting are required before integration.
This work adds no sounds or original bolt launch motion and combines no branches.
