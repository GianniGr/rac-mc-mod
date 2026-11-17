# Original RaC1 sound pipeline

The client imports SFX from its own verified NTSC-U v1.00 or PAL v2.00 disc.
The dedicated server needs neither a disc, an audio cache nor audio hardware.
It sends only fixed semantic IDs, a dimension/position and a random seed. No audio, bank
indices, source paths or proprietary bytes are sent to other players.

## Supported events

Shared definitions in `Rac1AudioEvent` choose a curated **Novalis (level 1)**
reference profile, not a Minecraft dimension:

| Event | Original class / local slot |
|---|---|
| Wooden crate break | 500 / 0 (506 explicitly borrows 500) |
| Health crate break | 501 / 0 |
| Ammo crate break | 511 / 0 |
| TNT blast or manual break | 505 / 0 |
| TNT countdown beep | 505 / 1 |
| Bolt attraction begins | 13, 14, 15, 16 / 0 |

These associations follow R07's verified PAL call sites and measured PAL/NTSC
maps. NTSC gameplay machine code was not independently traced. No vendor,
Blaster or wrench audio is bound: their event dispatch still needs research.
Bolt collection is **not** an extra ding. Denominations use the existing
bolt visual class mapping (1/5/20/50).

Server emission follows successful exactly-once destruction, not generic block
removal/falling transfer. TNT self-consumption uses that same destruction hook,
so detonation does not emit a second break sound. Cancelled detonation is silent.
Beep thresholds are crossed once at remaining MC ticks 59, 39, 19 and 4,
approximating the original 60 Hz thresholds 177,117,57,13. Unloaded time is not
replayed. Chain fuses shorter than those thresholds produce no historical beeps.

## Import and cache

`Rac1LevelData.soundBank` uses the existing read-only disc interface. The sound
map is resolved in **class-table order** using each definition's local index;
bank cue IDs are not global sound IDs. A bounded outer-v3/SBlk-v1 reader accepts
only a single opcode-1 tone. Other cue programs fail explicitly; the importer
does not choose a random first waveform from an unsupported program.

The independent Java PS-ADPCM decoder reads low nibble first, uses five public
predictor pairs, rounded prediction and signed16 saturation, and stops at the
first end flag within a waveform's bounded span. Loop flags/start/end are
preserved. It does not copy or depend on a GPL decoder.

`sound/sound-<verified-source-SHA1>-v1.racs` under the configured cache root is a
**separate RACS sidecar**, schema/converter 1. It does not change RACM, its
reserved section IDs, or its asset gate. Its identity includes the whole verified
source hash and converter version; each clip also retains bank/ADPCM SHA-256,
resolved cue, class definition, cue/grain metadata and loop markers. Runtime bank
handles are zeroed. Lossless signed16 mono PCM is checksummed with the metadata.
Reads validate counts, byte lengths, rates, hashes and duplicate IDs; writes
stage, re-read and atomically replace. Failed stages are retained for diagnosis.
A future rate/decoder/profile change must increment the converter version.

Import, decode, hash and cache I/O run on a worker, never render/audio threads.
An immutable complete catalog is published only for the current import generation.
Missing/corrupt audio is rebuilt from verified local media when possible, otherwise
logged once for that import and silent; it does not block gameplay or download assets.
The existing client visual/media gate is unchanged.

## Playback and fidelity limits

This is a bounded PCM prototype, **not an exact PS2 mixer**:

- NeoForge 21.1.265's patched `SoundInstance.getStream` is used with
  `AudioStream`; direct instance resolution supplies a streaming `Sound`.
  Thus no OGG encoder, fake OGG bytes, sentinel resource, OpenAL calls or
  external codec are needed. Compilation verifies the pinned signatures.
- User resource packs may define an ordinary OGG-backed semantic event to
  override the imported clip. Otherwise only the custom PCM path is used.
  Sounds obey Minecraft's BLOCKS/master volume and linear 32-block attenuation.
- Rate derivation v1 is `44100 * 2^((60-center-fine/128)/12)` for nonnegative
  center-note encoding; center72/fine0 gives22050. Negative/SPU2 note encoding
  is refused. This formula is an approximation, not hardware-quantized tuning.
  PAL timing does not rescale the audio rate.
- Gain v1 multiplies near-volume/1024, cue-volume/127 and tone-volume/127,
  clamped to0..1. It never normalizes PCM. The divisor and clamping are a
  conservative presentation policy, not verified PS2 mixer gain.
- Pitch bend, far-volume curve/flags, ADSR, reverb, pan, interpolation and voice
  priority are retained as metadata but are not emulated. Runtime pitch stays1.
  Accurate pitch-bend conversion and gain/rate fidelity require follow-up.
- **No loops are automatically enabled**: v1 source/cue loop semantics are not
  proven. All bound events are one-shots. Stored loop data is not authorization
  to start the unverified Blaster loop. There is consequently no active-loop
  replication/late-join protocol in this candidate.
- Four tracked one-shots at most share vanilla's scarce streaming pool.
  Bolt events are coalesced to one per client game tick. Under pressure only
  audio is dropped, never currency. Native streaming-pool pressure/performance
  requires client acceptance testing.
- Resource reload, world/dimension switch, disconnect and loss of verified media
  stop tracked sounds and close PCM streams. Finished/rejected sounds are
  pruned; stream close is idempotent. Old import completions cannot publish after
  source invalidation. Removing an on-disk sidecar does not invalidate PCM already
  resident in memory; restart/reimport clears that generation.

## Focused verification

`gradlew.bat test --tests "com.github.giannigr.racmc.sound.*" --tests
"com.github.giannigr.racmc.client.sound.*"` runs synthetic parser/map/decoder,
cache, threshold, packet-boundary and stream checks without playback.

`RealSoundDiscTest` is opt-in with the existing `-Dracmc.isoImage=<path>`.
It validates the real disc read-only, verifies R07 ADPCM hashes/sample lengths
and per-level cue remapping, and prints PCM hashes only. No proprietary fixture
or PCM output belongs in Git. Hash agreement does not establish envelope,
tuning, audible fidelity, runtime lifecycle or network acceptance.

Independent exact-tree T verification and owner-authorized auditory/network
acceptance are still required. Do not auto-launch a client or play sounds on
the owner's computer to run these automated checks.
