# Hydro-local host playback checkpoint

This follows `d0be354`; the high-entry cue commit `6a3a5c8` and long-entry
pack-phase fix `6beeb9e` remain independent and unchanged.

## Runnable, not audibly verified

With the existing optional, locally validated **NTSC-U v1.00** source selected,
Clank's importer can now construct cue37 from its complete verified ten-grain
program and the two referenced ADPCM waves, decoded in memory only. Invalid
Hydro metadata fails independently of glide/high-entry import. No assets,
waveforms, original library or runtime capture are bundled or exported.
PAL Hydro is refused; PAL glide/high-entry behavior is unchanged.

`Rac1HydroHostMixer` executes the verified NTSC `Rac1HydroProgramClock` on
200-sample boundaries at 48 kHz (240 Hz). It starts226, ramps its pitch, and
ordinary release synchronously keys off that loop and keys on233, with its
own sample cursor/ADSR and zero tone PB range. Release preserves the loop's
current pitch/register state, cancels further ramping and is idempotent.
Forward completion never plays233. Neither voice is a detached guessed clip.

The client observes accepted, fresh Hydro **stage** edges, not raw keys. Start
is allowed only within the first three source steps of a thrust stage. Media
readiness, tracking or channel recovery does not replay an older start. A
phase reset can reveal a retrigger after a skipped drift snapshot; a complete
press/release hidden between snapshots cannot be recovered and is not guessed.
Wet valid stage exit is ordinary release; epoch/world/media/resource loss,
death/spectator/stale/out-of-range/budget displacement are hard aborts.
Dry exit is deliberately a hard abort, not evidence about original release.

There are at most two nearest Hydro actors, each with at most two runs
(one new run and one retiring run; two retiring runs while idle).
A third retrigger aborts the oldest retiring run. Each run owns at most two
synthesized voices in **one** spatial host channel. A dropped/rejected channel
is retired after the existing five-client-tick startup allowance, with no
held-thrust restart. Glide's separate two-channel budget is unchanged.

## Explicit host adaptations

- Host playback time, not packet arrival count/render frequency, clocks the
  program. First admissible snapshot starts a fresh local stream; it does not
  seek backward to an unheard source start. Start latency is bounded by
  native sampling, channel startup and device buffering, not claimed zero.
- 48-kHz signed mono output, linear sample interpolation, finite PCM bounds and
  loop markers. No SPU2 Gaussian interpolation, reverb, modulation hardware,
  original voice allocator, master mix or hardware envelope bit-exactness.
- Base negative-center-note rate reuses the already documented public libsd
  specialization. Dynamic signed Q7 tone bend uses source integer scaling
  (`32768` negative / `32767` positive); conversion to a frequency ratio uses
  `2^(fine/1536)` rather than emulating each hardware pitch-register rounding.
- Authored ADSR fields select integer attack/decay/sustain/release steps.
  The host uses bounded sample periods and saturating levels. This is an
  independently implemented public-envelope adaptation, not measured hardware
  parity, including reserved/infinite-rate edge cases. Only verified cue37
  reaches the runtime importer.
- Loop and tail are summed, gain-scaled from the retained definition/cue/tone
  fields and saturated to signed16. Host spatial attenuation replaces original
  pan/volume routing. The host channel itself stays at pitch/volume1.
- Release enters at the **next unrendered output sample**. PCM already queued
  to OpenAL cannot be withdrawn. Each stream read caps output at2048 frames
  (~42.7 ms); multiple device buffers add latency. Abort stops the owned host
  channel and clears both synthesizer voices. This is not sample-accurate
  synchronization between a server input edge and the device.

## Evidence, no fresh retail-library scope exercised

Original NTSC entry22 control-flow facts and exact source boundaries remain in
the retained `p36-clank-av-verification-20261123/HYDRO-EVIDENCE.md` and
`LIBSD-BOUNDARY.md`. No entry21 content or PAL IOP instructions were read here.

Pinned public references:

- ReRAC `ae1e3432a0d5e21bd08d3872d3b04afef98be666`,
  `crates/rc-formats/src/vag.rs` and the retained source-boundary record.
- OpenGOAL jak-project `4172dfe7ab66cefb22218cc0da92b23c0342fa98`,
  `game/sound/989snd/util.cpp`: public note2pitch, signed tone bend and the
  positive/negative center-note distinction.
- The same pinned revision, `game/sound/common/envelope.cpp`:
  ADSR field semantics, exponential/linear step behavior and independent
  Attack/Release/Stop lifecycle (ISC, copyright2021–2024 Ziemas).
  This source itself warns that reserved/infinite-duration handling is not
  hardware-verified; the host implementation makes no stronger claim.

Ordinals5/7 are the already verified entry22 parameter/keyoff handoff. Their
external wrapper bodies are unnecessary for this explicitly non-bit-exact
host backend. Ordinal13 has public retained/pinned arithmetic coverage. If
retail-instruction parity becomes necessary, the prior precise proposal remains:
NTSC directory entry21 `[0x78f00,0x7fe9d)` enclosing range; **only** ELF/module/
export metadata first to locate libsd5/7/13 bodies, followed by separately
agreed exact function bounds. The semantic questions would be note/fine
rounding and parameter/switch selector routing, not a broad library survey.
No fresh permission is inferred from that proposal.

## Later jump lane — dispatcher tested, runtime producer still missing

`Rac1ClankReachedAudio` consumes consecutive, owner-fenced **actual source
animation advances** for only0x12/0x15/0x26/0x11/5. It preserves authored
first-word order, blend suppression and no wrap/gap/interruption replay.
Its tests exercise long, retimed and landing/skid intervals. Those are
dispatcher-contract traces, **not evidence that a runtime action reached them**.
It is intentionally not connected to the approved high-entry clock.

Pinned ReRAC `hero/jump.rs` and `hero/anim.rs` confirm the missing producer
requirements: long-entry5/8 blend and changed takeoff; speed changes after
takeoff based on current animation and velocity/gravity; scripted high curves;
descent `land_eta`; touchdown SetAnim jumps/blends; and actual skid entry.
Existing native action/phase snapshots omit these reached hero animation fields
and skid/landing transitions. Pack component clocks are not hero clocks.
The current host action ending on ground is not evidence of original skid
selection. No later long/high/skid/landing sound was bound to action age or a
guessed landing hook, and scripted local43/46/62 were not replaced by one tone.

Next safe step is a separately reviewable, read-only nonrendered hero-transition
producer with an explicit host-observation contract, without changing motion,
permissions, saves or the frozen aerial wire. Until then these later cues are
not runnable; the existing first high-entry cues remain runnable independently.

**Subsequent continuation:** [reached action audio](clank-reached-action-audio.md)
now implements the accepted-collision producer and four bounded long/retimed
bindings. The historical producer gap above is resolved for those segments,
not for original landing/skid/airborne-entry states. Hydro playback is unchanged.

No interactive client/device/audio acceptance has occurred at this checkpoint.
Automated tests use synthetic signals/metadata. The optional real-bank importer
has compiled but has not been exercised against retail waves in this run.

## Automated checkpoint

Fresh isolated offline normal build: **904 passed,48 skipped,0 failures/errors**
(952 tests). Fresh private native server: **29/29 required regressions passed**.
Tests cover PCM/read partition invariance, early/sustained release, simultaneous
loop-keyoff/tail lifetime, abort/EOF/idempotence, rapid retrigger budgets,
missing readiness/late join, duplicate/backward snapshots, dropped channels,
epoch/retain/clear teardown, and reached-key interruption/ordering/gaps.
Protected movement/wire/approved component clocks/high-entry/glide/shared
playback diff:0. Normal JAR private-fixture/original-payload scan:0.
No interactive client, owner-world use, push or merge.
