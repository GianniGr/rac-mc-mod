# Reached action-audio producer

Follows `c891148`. High-entry cue binding and long-entry pack continuity remain
unchanged; NTSC Hydro mixer/lifecycle/stream/cleanup implementation is untouched.

## Certified host observation route

`Rac1ClankRuntime` has one read-only callback **after** each successful native
collision resolution. It supplies the accepted `Step`, authoritative epoch/
action serial, post-collision vertical source velocity, gravity and touchdown.
No key-down observer, Steve animation, renderer, predicted replay or client
movement authority drives this clock. Three accepted updates per native tick
are advanced individually; a duplicate source update is inert. Gaps, wrong
owner/action, cancellation, unsupported descent or wrap terminate the segment.
The observer does not modify motion, collision feedback, input, grants, saves,
fall protection or the frozen movement/snapshot wire.

The nonrendered clock follows the retained hero key rates/header times and
five-update SetAnim blend; dispatch uses **key-index×16+trunc(t×16)**, not header
times. The completed entry update does not advance the newly set animation.
Retiming follows pinned `hero/physics.rs::anim_speed_for_ticks` and
`hero/jump.rs::tr_jump`, using only accepted native observations:

- Ground long: takeoff5; after6, aim at frame18 using truncated accepted
  velocity/gravity ticks and the source1.5 speed cap.
- High: takeoff9; Heli pre-curve target29 and35..71 curve target50 over51
  source steps; Thruster9..44 target26 over36 steps.
- A velocity/gravity result outside the bounded clock contract stops the segment.
  Host floating-point/native collision values replace PS2 FPU/world observations;
  this is not original-environment trajectory/bit-exact timing parity.

Reached bindings: Heli-long local43→54, Thruster-long local1→23, Heli-high later
local46→55, Thruster-high later local62→63. First high locals49/1 stay on the
reviewed independent path and are explicitly excluded from new dispatch.
Heli-long's second same-time local62 remains shadowed by local43.

The separate **S2C-only** presentation payload carries actor/epoch/serial/tick/
source step/local, fixed48 bytes, no media or movement parameters. It is emitted
only to current tracking clients/self, not stored/replayed for late joins.
The client consumes dedup watermarks even when media/budget denies admission.
Future semantic snapshots may defer an event for at most two ticks, preserving
its original deadline/generation. Old generation/epoch/action/tick events drop.
Local0 is an accepted-touchdown **completion fact**, not a landing cue; it lets
an existing program finish while normally idle. Interruptions/new actions and
native lifecycle loss hard-abort, rather than execute a delayed stale grain.
No complete hidden source transition is reconstructed from a late snapshot.

Separate nearest-two action channels; no changes to high-entry/glide/Hydro
budgets. Dropped channels are retired after the five-client-tick startup grace,
without replay/retry. World/media/resource/session teardown clears pending
events, selectors and owned channels. Each action program renders only its
own verified waves; it cannot access another item's playback.

## Full bounded scripts, not tone substitutions

Runtime extraction reuses the **already validated/hash-checked bank bytes and
hero definition blob** in the existing optional lease job: no additional image
open/read, new level, shared cache or startup gate.
No media was opened or decoded by this implementation's research/tests.

Precision scope for that optional runtime in-memory capability:

| local | cue / grains | samples (bank-relative starts) | controls |
|---|---|---|---|
|1|23 /206|existing high-entry clip reused|single finite tone|
|43|54 /258..260|162704|delay10, tone, wait240→keyoff, wait100→stop|
|46|55 /261..263|162704|delay50, tone, wait220→keyoff, wait160→stop|
|62|63 /290..296|428272,434256|register random0..39; conditional stop; random PB86; anti-repeat selection of exactly one of two tones|

Every scripted raw40-byte grain is compared with the retained NTSC packet
before decoding. ADPCM decode stops at the first bounded end marker; bank hash,
complete program/definition identity and finite local62 waves fail closed.
No library, waveform or asset file is exported or bundled.

The grain31/34/24/27/25/1/41 semantics use pinned public jak-project
`4172dfe7ab66cefb22218cc0da92b23c0342fa98/game/sound/989snd/sfxgrain.{h,cpp}`,
alongside the retained original NTSC sequencer/delay evidence. No new retail
IRX/libsd instructions were inspected. This is a **bounded host-program
adaptation**, not new retail instruction-parity certification for those handlers.
The actual local62 register gate usually stops without a voice; there is no
unconditional first-tone fallback. RNG/register/previous-choice state is
host-local per actor/epoch, not the original global bank/RNG namespace.
PCM is48-kHz mono with linear resampling, authored host ADSR and saturating gain,
the same documented non-bit-exact output choices as Hydro. Script delays use
audio samples at240 Hz, not frame/render counts; device buffers add latency.

New scripted actions are NTSC-only. The existing independently verified
one-tone Thruster reference may serve a PAL ground-long entry as well.
PAL IOP script/stop/release parity is **not** inferred.
The one-tone PAL binding uses the existing native ORIGINAL_60 host adaptation,
not a claim of original PAL50 execution timing.

## Exact missing transitions — no mechanics changed to manufacture them

- Original airborne/short-crouch Thruster long has8-update blend and takeoff+3.
  Native admission is ground-only after more than6 crouch source updates, with
  takeoff5. The implemented producer certifies only that actually admitted
  five-blend route; it does not invent an airborne entry variant.
- Original descent retiming needs `LandEta`: a projected parabolic endpoint/
  ground-line hit and walkable-normal test. The host exposes a different
  vertical ground-distance probe. Substituting it would be an uncertified new
  predictor. The audio clock stops at the first unsupported descent transition.
- Original touchdown can SetAnim the old sequence at `f_land+2` with7-update
  blend; hold frames and subsequent state choices can then reach locals30/31.
  Native `resolved` cancels the action immediately and returns ordinary travel,
  with no corresponding landing animation state. No30/31 clock is fabricated.
- Heli-long's native touchdown retains the reviewed0.8 horizontal feedback,
  but does **not** install original state3, seq5's16-update eased blend or its
  exit conditions. Therefore skid local12 is not emitted. A boolean touchdown
  alone is not evidence of reaching seq5 key2.

Representing those missing states would require an explicit new read-only host
presentation-state adaptation (or mechanics changes, which were not made).
They do not block the four implemented entry/retimed bindings above.
See [the owner-scheduled live checklist](clank-live-audio-checklist.md).

## Automated checkpoint

Fresh isolated normal build: **915 passed,48 skipped,0 failures/errors**
(963 tests). Fresh private native server: **30/30 required tests passed**,
including production-route43/1/46/62 emission, duplicate-tick inertia and
interrupted windup. All93 producer key headers/rate bits and the three complete
program metadata blocks match the retained numeric evidence exactly.
Normal JAR private-fixture/original-payload scan:0.
Hydro/high-entry/glide/pack clocks, motion, saves and existing wires have no diff;
the movement adapter's only edits are the read-only observer and teardown hooks.
No retail research read, live client/device/audible acceptance, owner-world use,
push/publication or merge occurred. The optional real-wave importer was not
exercised by these synthetic/headless tests.
