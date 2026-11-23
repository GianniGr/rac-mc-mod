# Clank AV follow-up to owner-tested `236a3f4`

## Hydro bubble visibility

Two concrete suppression paths were present:

- Emission incorrectly required a water surface found within a bounded32-block
  probe. Deep/covered water now emits normally; an unknown surface disables
  only surface-pop checks. Lifetime, buoyancy, scale, cadence, imported type34
  sprite and two609 outlet chains are unchanged. No invented pop plane.
- First person does not render the wearer's body layer, so no nozzle capture
  could exist. A private attachment-only `PlayerRenderer` model now samples the
  local native torso/root using native rotation/scale/swim/crouch methods and the
  same imported609 pose and attachment chains. It never calls render, emits
  vertices, draws a duplicate skin/pack or changes the shared player renderer.
  Third-person/remote endpoints still come from their actually rendered pose.

Auto-equip/access, dry/wading/surface/submerged classification, control, pitch,
air, physics and protocol are unchanged. New bubbles require the existing fresh,
matching-owner/epoch, worn/access-authorized **controlled submerged THRUST**
predicate. Wading, surface swap/swim, idle/stroke, stale state and mere key-down
do not emit thrust bubbles. Already emitted particles finish their bounded life.
Missing original sprite/model/attachment data remains unavailable, not replaced
with vanilla bubbles or guessed mouth/hand offsets.

Clank debug logs now report changes in Hydro water/stage/visual/control,
thrust selection, captured outlet count and imported sprite availability. These
distinguish expected surface/wading silence from an unavailable renderer/asset.
Unit evidence is not actual underwater GPU/first-person visual acceptance.

## Verified Heli/Thruster audio

The optional catalog still contains only original class0/local2→24 Heli glide
and local18→36 Thruster glide, with signed SPU2 rates15,984.375/22,019.53125Hz
and original loop spans `[28,20440)` / `[168,13468)`. No jump effort/motor,
touchdown, water-landing or unrelated sound is substituted.

The old optional importer started another whole-image `validator.inspect(path)`
and identified selections only by filename. The Clank-local audio worker now
uses P35's existing fully verified typed read-only lease and captures normalized
path, expected main disc key and media generation. It checks before/after import,
closes only its consumer lease and rejects obsolete publications, including
same-path revalidation. One active plus one latest queued job is bounded.
Reload/logout retire owned voices and pending imports. No shared sound manager,
Blaster catalog/cache, startup gate or P35 manager implementation is modified.

Cue readiness/import duration is logged once; debug logs distinguish owned
voice requests, retirements and delayed channel retries. Those are not proof
of audible output. Existing nearest-two wearer voices,32-block range, volume,
original stream/loop ownership, landing/action-exit stops and retry budget stay.

## Source-backed bindings and Hydro program at `02b53cb` (historical)

`Rac1ClankAuthoredSoundKeys` implements only the verified hero dispatch rule:
pre-advance blend suppresses; first authored word with
`before16 < signed_time16 <= after16` wins; no loop-wrap replay or trigger on a
held clock. The ordered words for high/long jumps and reached skid/landing clips
are still absent. This helper is **not bound to input/state age** and cannot
invent those tables. No additional verified aerial cue binding was supplied.

`Rac1HydroCueProgram` begins the separate scripted implementation by reading the
complete local19→cue37 ten-grain metadata, retaining all240-Hz grain delays,
control operands, both distinct tone references and the opcode43 marker.
It does not call the single-tone reader or export samples. This is a strict
metadata-only component, **not executable Hydro audio** or a faithful release VM.

Retail IOP stop-command0x15 / grain43 dispatch, PB execution and key-off/ADSR
handoff are not proven by the mirrored public interpreters. Playback remains
explicitly gated rather than loop tone226 alone, synthesize a ramp in literal
Hz, or claim tone233 is an evidenced stop sample. The actual waves still need
their verified hashes/loop decode checked under approved read scope before any
playback integration. Equal retained NTSC/PAL rows do not prove IOP parity.

Next evidence needed is bounded original989snd entry22 stop/sequencer/grain43
metadata/disassembly (with locator/decompression scope and exact instruction
bounds approved first), or a labeled original early/sustained release/abort
trace. No new retail image/library/sample reads were performed in this slice.

## First-frame Thruster blur at `02b53cb` (historical)

The owner now sees roughly one initial frame. The source-history regression
confirms no idle/previous-action ghosts at entry, no placement per repeated
render within one source update, and intentional long-jump body echoes starting
at the next source placement. This does not reproduce or resolve pack blur.
No trail removal, clock/timing change, redesign or approved high-jump change is
made without the separately bounded renderer reproduction.

## Subsequent scoped verification

`6beeb9e` reproduced a first-frame **pack pose/attachment** discontinuity with
empty body-trail history: unchanged601/608 idle clips were rebased to phase0
on Thruster-long entry. Their running idle phase now remains continuous on
LONG_JUMP only. Approved HIGH_JUMP/glide/Heli clocks and intended body echoes
are unchanged. Failing-before/passing-after CPU probes check both pose and
nozzle chains. This is not a live GPU confirmation of the owner's blur.

Fresh full-validated NTSC/PAL L01 reads establish actual ordered class0 triggers:
0x12=`8:43,8:62,208:31`;0x15=`8:49,80:46,440:30,464:31`;
0x26=`8:1`;0x11=`8:1,96:62,320:31,352:30`;5=`32:12`.
These are **key-index*16+trunc(t*16)** intervals, not stored header timestamps.
All five have nonzero counts. First-in-authored-order semantics shadows the
second time8 word in Heli-long. No zero-table or silent-state assertion.

The first high-jump entries are now independent optional one-shots:
Heli local49→58/grain267 and Thruster local1→23/grain206. Their actual first
key rates after the original five-update snapshot blend cross trigger8 before
takeoff/retiming; the nonrendered clock consumes accepted source updates with
the entry-update offset, not raw key-down or Steve time. Native three-update
batching is supported; late joins, large gaps, interruptions and invalidation
do not replay. The two-entry one-shot budget is separate from the unchanged
nearest-two glide loop budget. Imports fail independently and use the same
verified bank/lease job, no new mandatory gate or persistent cache.
Both source revisions' rows/key rates were independently read. Scheduling uses
the mod's existing native ORIGINAL_60 source-step adaptation for either asset
revision; this is not a claim of original PAL50 timing or IOP release parity.
Definition bend uses signed Q7 source scaling with per-owner/serial local RNG;
native mixer pitch/interpolation remains a host adaptation, not bit-exact SPU2.

Later retimed/landing/skid events and long-entry blend5/8 variants remain
unbound until their actual reached source-clock transitions are represented.
Their metadata is now known; no phase-guess, guessed grunt or glide-loop
substitution is used, and Hydro does not gate the two supported entry cues.

NTSC-only original989snd v2.09 entry22 static analysis now proves the separate
StopSound marker path: key-off loop, clear owned mask, synchronously dispatch233,
preserve PB/register. Ordinary forward opcode43 skips233. The source240-Hz
control clock is implemented and tested, including signed rounding that leaves
PB−1 after63 ramp increments. **Runtime Hydro audio remains gated**: the current
constant-rate PCM backend cannot represent dynamic pitch plus independent
key-off/ADSR/tail ownership faithfully. Analysis stopped at libsd ordinals5/7/13;
entry21 contents and PAL release instructions were not accessed. No first-tone
fallback, waveform export, bundled audio, original-code execution or live client.

### Subsequent Hydro host-adaptation checkpoint

See [clank-hydro-host-audio.md](clank-hydro-host-audio.md). The verified NTSC
script now has a separate optional host mixer with dynamic pitch, independent
key-off/tail envelopes and owned hard aborts. This supersedes the blanket
runtime-backend gate above for the explicitly documented host adaptation only;
PAL release, bit-exact hardware output and audible acceptance remain unverified.
The approved high-entry bindings and long-entry pack-phase fix are unchanged.
