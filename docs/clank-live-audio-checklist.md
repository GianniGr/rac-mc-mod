# Clank live checklist — owner-scheduled only

Not executed by automated validation. Use an approved fresh isolated instance,
not an owner world; wait for the owner/playtest slot. No media capture/export.

Use the [current Hydro toggle retest](clank-hydro-toggle.md) and the earlier
[capped diagnostics](clank-hydro-live-defect.md#capped-opt-in-retest-diagnostics).
Import readiness or an armed toggle alone does not prove active thrust.

## NTSC optional audio

- Select the already validated NTSC source; confirm optional imports complete.
  With missing media, action/gameplay and other items must still work silently.
- High jump: Heli and Thruster first-entry cues remain single, unchanged.
  Release early, interrupt windup, and repeat: no duplicate or delayed catch-up.
- Ground long jump: Heli reaches local43→cue54; Thruster reaches local1→23.
  Heli43 is its full delayed/key-off/stop program, **not the glide motor**.
- Sustained high jumps: reached Heli local46→55 and Thruster local62→63.
  Local62's register gate is normally silent (**39/40**); do not expect a grunt
  on every jump or force a substitute. Audible branches alternate the two
  referenced tones in the host-local previous-choice namespace.
- Land normally: an already-started action program may finish; no fabricated
  landing/skid sound. New action, hurt/teleport/module loss, wet transition or
  stale/displaced actor must retire old action voices, including delayed starts.
- Remote: join/track someone already jumping. No old entry replay; only future
  reached events. Repeat under native three-update batching and channel pressure.
  Restore media/range/readiness mid-action: no missed event should replay.

## Hydro host playback and preserved gameplay

- Submerged armed forward thrust: one start, audible pitch ramp, then sustained loop.
  W release should ordinarily release once; holding W again while armed starts a new run.
  Camera rendering/frame rate must not restart the script.
- Ordinary valid wet release: old loop gets its own key-off envelope while233
  starts independently. Try release immediately, during ramp, and after sustain.
  Retrigger rapidly: at most the documented bounded retiring/new runs.
- Abort: dry/source/world/epoch loss, logout, reload, death/spectator, stale actor
  or budget displacement must stop owned voices, **without a synthesized233**.
- Check deep-water bubbles (including first person), attachments and teardown.
  Check pitch/dive/rise, low-air behavior, propulsion, high-jump height/travel,
  glide behavior and intentional body echoes against the reviewed baseline.
  Thruster-long first-frame pack/nozzle continuity should remain unchanged.
- Reload/logout/rejoin while action and Hydro audio are active: no stuck loop,
  delayed grain, cross-world voice, stale import or sound-device resource leak.

## PAL distinction

Existing PAL high-entry/glide and the independently verified one-tone Thruster
long reference remain available. New scripted action programs and **Hydro
release/playback are NTSC-only** here. Equal PAL cue metadata does not prove
PAL IOP stop/program behavior; PAL silence is not an audio failure claim.

Record candidate hash, source revision, action, first/third-person context,
start vs release vs abort, and observed issue. Buffered host output is not
bit-exact SPU2 timing; do not label a headless test as audible acceptance.
