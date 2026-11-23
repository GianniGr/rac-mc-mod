# Original Blaster equip audio

The original sound is a **two-part Blaster take-out**, not a generic switch sound.
The pinned Novalis NTSC swap trace shows the new hand item being made on its
sequence 0; its two key-time crossings request positioned class sounds with flags
0. The put-away (sequence 2) has no sound triggers. The swap and quick-select
routines themselves do not request the Blaster sounds. See
[swap dispatch](https://github.com/re-rac/rerac/blob/ae1e3432a0d5e21bd08d3872d3b04afef98be666/docs/plan/hero_gameplay.md#L870-L887)
and [hand-item animation advance](https://github.com/re-rac/rerac/blob/ae1e3432a0d5e21bd08d3872d3b04afef98be666/crates/rc-game/src/hero/weapons.rs).

| Part | Class / slot | Sequence 0 key time (sixteenths) | Novalis cue |
|---|---|---:|---:|
| Start | 168 / 2 | 16 | 102 |
| Finish | 168 / 3 | 88 | 103 |

R11 retained sequence records and R07 retained Novalis remap and bank records
verify these data on both supported discs. The two programs are single opcode-1
tones with no waveform loop. The PAL gameplay call chain was not independently
traced. This is separate from local 1 (firing), class 213 (ammo collection),
and vendor unlock/purchase audio.

Minecraft plays the first cue on the server's first observed non-Blaster →
Blaster **main-hand** transition, at the player's position, only for that
player. The second is sent two player ticks later if the Blaster remains held.
This gap is a Minecraft approximation, not a recreation of PS2 animation
speed. An offhand-only Blaster is silent; moving it into an otherwise
non-Blaster main hand counts. Selecting another Blaster or modifying/replacing
the held stack without changing its item identity does not redraw. An inventory
grant alone is silent unless it actually changes the server-observed main hand.
Same-tick changes that do not survive to observation and client-only slot
corrections are silent. Switching away cancels the pending second cue. Initial
observations (including reconnect, respawn, or dimension change) seed silently
and do not replay a draw. Client media/resource reload does not create an equip
transition or replay a cue.

Both parts use the existing optional local verified-media audio path and its
voice budget; the server sends no audio data. The v5 local sound sidecar adds
the two clips and leaves older sidecars and the main asset cache alone.
