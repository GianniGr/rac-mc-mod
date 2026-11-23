# Hydro silent/no-bubbles correction after owner-tested `96b09c0`

This is a historical checkpoint. On the later toggle candidate use the
[current Hydro input and retest](clank-hydro-toggle.md); its controls supersede
the held-Sprint instructions below. The diagnostic/audio boundary still applies.

Heli/Thruster audio was owner-approved on that exact NTSC candidate. Do not
change its cues, clocks, movement, or the approved bubble/attachment algorithm.

## Proven defect, owner evidence boundary

The authorized owner log contains two INFO messages saying the NTSC Hydro
script is ready. That proves optional import, **not** submerged thrust,
voice/channel admission, nonzero output or audible playback. The supplied log
does not record those states, so the owner's exact input/admission is unknown.

The old audio and bubble adapters required the aerial Clank snapshot to be
fresh within4 ticks. Aerial travel is ineligible underwater and may stop
refreshing that snapshot while authoritative Hydro snapshots continue each
tick. Thus a fresh matching SUBMERGED/controlled/THRUST snapshot could be
rejected by both adapters. An intentionally failing probe against the exact
baseline classes reproduces it with aerial age20 and fresh Hydro age0.

`Rac1HydroPresentation.fresh` now permits **wet Hydro presentation only** using
Hydro's own local receipt age≤4, matching actor and Clank epoch, worn Clank and
Hydro access/wet state. Death/spectator/range/session/world guards still apply.
Surface/no-control/native/drift do not become thrust. Stale/wrong-actor/
wrong-epoch Hydro is rejected. Dry-pack freshness and all existing controls,
motion, bubble emission cadence/geometry, nozzles and appearance policy remain
unchanged. No sound is forced simply because import is ready.

## Forward input and pose are separate questions

The reviewed controller can begin thrust with physical Sprint and no W.
When starting from moving ordinary swim with directional input, the existing
stroke gate waits strictly more than20 source updates. Once THRUST is accepted,
releasing W does not release physical Sprint or require forward input to
continue propulsion, sound or bubbles. Those controls/gates were not revised.

The isolated NeoForge/Minecraft dependency source explains the pose transition:
`LocalPlayer.aiStep` can clear vanilla sprint when forward impulse stops;
`Entity.updateSwimming` follows vanilla sprint, and `Player.updatePlayerPose`
then chooses SWIMMING or STANDING. Hydro reads the physical binding separately.
A native fixture can model these vanilla flags; it is not a genuine local
keyboard/rendering reproduction.

**No forced pose, rotation, swim flag or horizontal/vertical appearance decision
is made. Coordinator confirmation of the desired orientation is still required.**
The retest logs actual pose, vanilla flags and Hydro state independently.
Near a surface, a pose/eye-height change can also change actual submersion;
test well underwater to distinguish it from a legitimate surface handoff.

## Backend checks, not device acceptance

The retained local19 definition has near gain614/1024, not zero. Tests exercise
the actual Hydro sound resolve→getStream→48-kHz mono little-endian byte adapter
using a synthetic signal at that gain: nonzero samples, partition consistency,
ordinary release draining and immediate abort/EOF. PCM/script/gain behavior is
not replaced or boosted. The adapter was factored into a headless-testable
Hydro-only stream; other item/pack streams are untouched.

These checks cannot certify the owner's device, sliders, real-wave output or
GPU attachments. No media was opened/decoded, no interactive client launched,
and no owner input/source/save or running instance was accessed or modified.
Only the explicitly authorized owner `latest.log` was read. PAL Hydro playback/
release remains unverified and gated.

## Capped opt-in retest diagnostics

Coordinator may add **`-Dracmc.hydro.diagnostics=true`** to a *fresh isolated*
candidate JVM. Default is off; INFO transition logging is capped at160 messages
and32 channels per process, across client/server/audio threads. Unchanged
states do not log every tick. Only requested Hydro input/state transitions and
scalar sample statistics enter the normal log: no sample data, unrelated keys,
payload dumps or extra files. Do not alter the owner's current running instance.

Look for `[Hydro diag n/160]`:

| boundary | useful evidence |
|---|---|
|`client-input`|physicalSprint, forward, active gameplay, water/propulsion, actual pose/swimming/vanillaSprint|
|`server-input`, `server-state`|consumed input versus actual accepted controlled/stage/phase/stroke, water, air/pitch context|
|`client-snapshot`, `client-audio-state`|matching accepted state; `aerialFresh=false` is allowed while wet Hydro is fresh; actor validity, catalog and budget|
|`audio-admission`|admit-thrust versus late-start-consumed/media-not-ready; release or invalidation reason|
|`audio-request`, `voice-create/resolve`|actual play request, PLAYERS/MASTER sliders, host gain/pitch|
|`stream-start`, `audio-channel`|actual getStream call and SoundManager isActive after startup allowance|
|`stream`|samples/nonzero/peak, gains, envelope levels, bend/ramp completion; no waveform dump|
|`client-emitter`|selected thrust, capture/model609/outlets/sprite34 and actual pose|
|`voice-release/abort`, `stream-close`|ordinary release versus owned hard teardown|

Interpret the first missing boundary, not the ready-import message alone.
SUBMERGED with `propulsion=false` is a rejected water/status context, not an
audio device diagnosis. `physicalSprint=false` is not accepted thrust. A late
join/start is consumed rather than replayed; release and re-press normally.
Nonzero PCM with an active channel still requires owner audible confirmation.

## Exact owner-scheduled retest

1. Preserve the running owner instance. Schedule the new candidate separately,
   NTSC source, fresh isolated world/instance, diagnostics flag above. Allow
   optional imports to finish; keep PLAYERS and MASTER volumes audible.
2. Wear the approved pack. Use deep still water, with the eyes below the surface
   even in STANDING pose; avoid current/bubble-column/status incompatibilities.
3. Physically hold the **configured Sprint binding**, initially without W.
   Confirm logs show physicalSprint=true → SUBMERGED/controlled/THRUST →
   admit-thrust/play/getStream → active channel and nonzero PCM.
   Confirm thrust, bubbles and sound by observation, not just the model swap.
4. While still physically holding Sprint, press W then release W. Record pose,
   stage, sound and bubble continuity separately. Do not infer desired
   orientation from the word “vertical.” Repeat W-first if needed, allowing the
   existing stroke admission interval; controls must stay unchanged.
5. Release physical Sprint underwater: check DRIFT/ordinary release and233.
   Re-press for another start. Surface/no-control must not start a voice; a
   fresh wet transition from THRUST may ordinarily release the existing run.
   Dry exit, reload/logout or invalidation must hard-abort without a new233.
6. Spot-check pitch/dive/rise/air and the approved Heli/Thruster sounds. If still
   silent, retain the capped diagnostic portion and report the first missing
   boundary plus pose and water state. No capture/export is required.

## Automated checkpoint

Fresh normal builds with diagnostics disabled and opt-in tests enabled each
passed **923 tests,48 skipped,0 failures/errors** (971 total). The final fresh
private server passed **31/31** required tests. Its real snapshot regression
records two starts, one ordinary release, two aborts, and36 emitted type34
bubbles across the exercised windows, while reproducing the old stale-aerial
gate. Emitter cadence was not changed to require a batch every tick.
The actual sound/stream synthetic-signal trace records gain0.5996094,
nonzero PCM, ramp bend−1, independent release voices and zero samples after
abort. No real-wave/device/GPU or owner audible verification is implied.
Normal JAR private-fixture/original-payload scan:0.
