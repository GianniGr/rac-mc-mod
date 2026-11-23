# OmniWrench audio

Confirmed ordinary OmniWrench melee, overhead slam impact and the Comet release
loop use original clips imported in memory from the listener's verified disc.
Melee replaces only the qualifying direct vanilla strong/weak sound, with a
vanilla fallback per listener. Native combat, step/landing, hurt and death
sounds and crate, bolt-attraction and Blaster audio are unchanged.

## Behavior reference and disc evidence

Behavior below is from R08's retained, pinned **PAL** OpenRAC/rac1-decomp
`84bd565ff5d079384f1b673ac8e39ea3e06c68b7` (behavioral reference only).
Both discs' local audio metadata and waveform hashes were verified. **NTSC-U
gameplay call sites were not independently traced.** The mod uses the PAL
behavior reference for both supported client media revisions; matching samples
are not evidence of NTSC dispatch parity.

| Event | Retail behavior reference | Minecraft binding |
|---|---|---|
| Normal hit, not swing | `bolts_and_wrench.c:1662–1679,2115–2144`: wrench local 0/1, alternate only via explicit target flag; some targets are excluded or react instead, repeat feedback is gated. Slots 3/4 are not proven swing sounds. | Only a matching admitted primary native `Player.attack` hurt returning success **and** a positive post-damage observation for that exact target and damage source can replace the direct hit cue. The target bounds center is used for the original clip. The iron golem is Minecraft's one explicit alternate category (class 71/local 1); other eligible living targets use the source's default pattern (local 0). This is **not** a claim about retail materials or target IDs. |
| Slam impact, not startup | `bolts_and_wrench.c:2089–2096`: class 71 local 2 on source key 27, before the independent surface probe. The probe does not gate the sound. `hero_animation_slots.c:2656–2667` gives `previousKey <= 27 < nextKey`. | One positional playback at the exact crossing sample's projected wrench head. Not a generic on-ground event, windup cue or damage pulse. |
| Comet Strike release, not catch | `bolts_and_wrench.c:2159–2171,2216–2237`: first outbound update acquires hero class 0 local 14 with flags `4`. The same voice can persist into return. Stop at eligible catch preparation (`<4` updates to hand **with a selected weapon**) or at arrival. The arrival voice call is separate and out of scope. | The accepted release starts one owner-positioned loop for each nearby listener with that action's detached snapshot. It can persist into return and stops when the existing authoritative state reaches catch. Minecraft's wrench action does not represent retail's separate selected-weapon field, so it uses the arrival stop, not the conditional preparation stop. Same-batch release/catch has no sustained episode; no synthetic one-shot is played. |

The bounded metadata probe used the full validator for the two project records
(NTSC-U v1.00 `SCUS_971.99`, PAL v2.00 `SCES_509.16`). Both passed the
whole-image hash. Outside validation it looked up only Novalis L01, using the
compressed core carrier for the two classes and reading only the referenced
bank cue/grain records and frame spans. No overlay or other-level sweep, sample
decoding/extraction/export or playback was performed for this probe.

| Definition | L01 cue / grain | Samples; zero-based loop | ADPCM SHA-256 (both revisions) |
|---|---|---|---|
| Wrench 71 / 0 | 80 / 314 | 8932; none | `e73f19714b116e55199ddde24a74713f49d2015f580a2a6ee7302fb023066c02` |
| Wrench 71 / 1 | 81 / 315 | 6916; none | `9049dee17845794b00ab0f1dad10ae67704338f6de1dd25c9ddd245b5185b4b2` |
| Wrench 71 / 2 | 82 / 316 | 5180; none | `d1173abe94666b5dcfc0a5cf5a4cd3f8eb2f407fdd2e9e1ba0f0d4e5e5e8295d` |
| Hero 0 / 14 | 33 / 219 | 2352; `[28, 2352)` | `848c48579fb263b2a06bf24a36c027ff9ee1d5b8c82f0a833386bad8494dc4a2` |

All four cues contain one opcode-1 grain with zero delay, center note 72 and
fine 0; only the throw has repeat/end flags. Its whole grain **differs** between
PAL and NTSC-U even though the selected audio span matches. Runtime independently
pins each revision's definition/cue/grain hashes and the span/loop for each
bound cue. The mod follows [the existing PCM backend's](sound.md) approximate
rate/gain and does not apply the unverified PS2 bend conversion (including
  Comet's fixed raw bend −1524). No ADSR, mixer or hardware fidelity is claimed.

## Normal hit replacement and fallback

- In NeoForge 21.1.265 `Player.attack`, the wrap distinguishes the one primary
  `Entity.hurt` invocation from secondary hits and matches its exact target and
  `DamageSource` in native incoming and `LivingDamageEvent.Post` events.
  Ambiguous reentrant reuse of the same target/source leaves feedback native.
  It offers only that call's
  subsequent `PLAYER_ATTACK_STRONG`/`WEAK`. The existing same-tick admission
  token is consumed once. No media choice changes native damage, cooldown,
  knockback or the return value of `hurt`.
- The exact `ServerLevel.playSeededSound` invocation consumes the offer before
  firing the native `AtPosition` hook. Unrelated or reentrant sounds don't inherit
  it. Server event cancellation and changes to the sound, category, volume or
  pitch remain native. There is no event-bus cancellation or coordinate-based
  matching. Listener admission uses vanilla's strict radius/dimension/exclusion;
  the original hit plays at the impact rather than the attacker and uses the
  fallback's volume category.
- Listeners without the optional melee channel receive the original vanilla
  packet. A capable listener receives one packet offering either the verified
  one-shot or that **exact** vanilla packet (position, seed, category and gain
  included). Missing/invalid media, absent clip, shared budget exhaustion or
  synchronous stream admission failure uses the embedded fallback. A client
  `PlaySoundEvent` cancellation/replacement before resolution is intentional
  and does not trigger a second sound. Failure after engine channel admission
  is not retried. The hit PCM does not accept an OGG override; the native
  fallback still uses the native client sound path and resource packs.
- No hit substitute for miss, shield/no-positive-damage, nonliving target,
  armor stand, managed actions, crit or sweep sound branches, or auto-spin
  attacks. Sprint attacks retain both native direct sounds: their separate
  knockback cue is issued **before** damage resolves. Replacing only their
  strong cue would retain two direct sounds; replacing both would require a
  separate delayed pair transaction preserving both native hooks and fallback
  packets. This checkpoint does neither.

## Transport and verification boundaries

- Slam and Comet send only an optional S2C semantic edge with emitter/action
  identity, dimension, tick and position. Listeners must be in the same
  dimension, within 32 blocks of the slam head or 25 blocks of the Comet owner
  (Minecraft linear attenuation policy), including the owner when in range.
- A release edge cannot replay its intro on subsequent motion snapshots, on
  return/catch, or for late listeners. Its own flight state follows the owner,
  and ends on catch, invalid owner/item, stale or matching-flight loss, range,
  world switch, resource reload, or media loss. The Blaster does not own this
  loop. The shared PCM stream budget is still four total/two sustained.
- Import runs on the existing verified-media worker, reusing its already-open
  validator result without a second validation or a new path. Only the four
  pinned clips enter this optional in-memory catalog; missing melee clips do
  not disable slam or Comet. No RACS/RACM version change,
  persisted decoded clip, game asset bundle or network media transfer is added.
- Independently tracing NTSC behavior would require a separately bounded
  call-site scope, not more sample extraction. No subjective listening,
  connected-client timing, actual audio-device pressure or live acceptance
  is claimed.

Targeted tests verify the NeoForge mixin application, exact damage witness,
fallback codec/policy and vanilla listener boundary. The opt-in
`src/test/fixtures/wrench-melee/java` headless GameTest runs real `Player.attack`
and uses packet sinks for the owner and nearby capable/vanilla and far
listeners. The fixture is not a normal source set; add it only to an isolated
GameTest build with namespace `racmc_melee` and an empty test structure
`data/racmc_melee/structure/wrench_melee_empty.nbt` (7×6×7). It does not
negotiate an actual connection, start an interactive client or use media.
