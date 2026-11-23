# Bomb Glove v0.1-beta gameplay

This branch provides server gameplay and registration. The approved free,
one-time Gadgetron claim with ten rounds is an integration contract for the
shop/catalog lane, not a second grant path here. Ammo crates retain their
existing Blaster reward policy. Original local-media presentation is separate;
the gameplay branch intentionally registers a no-op bomb renderer until it is
replaced by the presentation lane.

## Throw and payment

The registered weapon is `racmc:bomb_glove` (single item); its only ammo is
`racmc:bomb_glove_ammo` (stack 64). `BombGloveAmmo.count` counts main inventory
and offhand once; it does not enforce the future commerce cap. The server starts
native use in the main hand and snapshots world, selected slot and exact stack.
The sixth native use tick is the one release marker. Let-go before it, a stale
stack or slot, death, logout or a world change ends or invalidates the windup.
Keeping use held after this marker does not repeat.

Release uses the mirrored world-space palm socket in `BombGlovePose`, not the
camera ray or the original physical release offset. The server sweeps the bomb's
entire release volume from the player root to that palm against collision shapes.
Obstruction rejects without a blast, payment or cooldown. A refused entity
spawn refunds exactly the one reserved item. An admitted throw costs one round
offhand-first in Survival/Adventure, is free in Creative, and starts a
seven-tick cooldown. It never enters the Blaster firing session.

## Flight and blast

R19's pinned Novalis trace supports class 121's lob, swept contact (not dry
bounce), descending water entry and sink, roughly five-second flight timeout,
and the dry radius changing from 0.5 to 2.5. Minecraft adapts the original
horizontal speed and gravity at 20 ticks/second, derives the vertical component
from player look instead of source auto-aim, and does not simulate original
carrier inheritance. Flight persists with its remaining tick budget; unloaded
collision boundaries do not force chunks. Dry contact or timeout starts the
five-tick expanding wave; shallow contact still uses the dry path, but terminal
contact/timeout after seven sinking ticks is a deep burst with no dry damage
wave. Saving and reloading a terminal phase does not replay it.

Direct contact uses native projectile damage; the wave uses native explosion
damage attribution without instantiating a vanilla terrain `Explosion`.
Both start at two Minecraft HP before native mitigation. Direct damage and
wave share a per-bomb deduplication set; only accepted damage adds a modest
outward/upward impulse. Owner splash and direct contact are excluded. The
original aged direct self-hit is distinct from splash and is deferred. Other
players must pass the server's PvP and scoreboard friendly-fire checks. Mob
damage uses native damage events and a solid-block line-of-sight check for
the wave, a Minecraft protection adapter not claimed for the original.

The only block mutations are through the existing ordinary crate hook with
permission checks, native `BreakEvent` and the shared reward path, including
registered falling crates. The bomb cannot arm TNT crates: their existing
secondary blast does not carry the thrower's PvP or owner-immunity policy.
Vendor blocks, ordinary terrain and fire are never changed by this attack.

## Presentation seam

`BombGlovePose.windingUp` and `palm` expose the common hand timing and socket;
the server-only `BombGloveActionEvent` exposes accepted `RELEASE`, `WATER_ENTRY`,
`BLAST`, and `DEEP_BURST`. `Rac1Bomb.phase`, `blastStep` and `blastRadius` expose
tracked state for renderers and late observers. These are presentation inputs,
never client authority. No proprietary art, sound or effects are bundled.

For the held-to-flight handoff, renderers use `BombGloveClientTracking.heldBomb`
and `flight` rather than predicting from native use. A server `READY` snapshot
reserves a throw ID and `WINDUP` keeps that ID. The projectile receives the
matching owner/world/session/throw key only after admission and payment commit.
An abort cannot produce drawable flight. The snapshot contains the owner entity
ID, a revision and a server phase tick; late tracking gets the current snapshot
without replaying events. A committed flight suppresses matching or older held
state even if its metadata arrives first or the entity is later removed. If
the release arrives first, held hides and flight waits for its metadata. Either
ordering can briefly show neither, but it cannot show both for that throw.
