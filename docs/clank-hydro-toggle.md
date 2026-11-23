# Hydro boost toggle — isolated beta retest

- In deep still water with Clank worn, release configured **Sprint** (Left Ctrl
  by default), then tap it once. The latch arms silently. Holding or releasing
  Sprint does not toggle it again. **W** means the configured Forward binding.
- Hold W for thrust. Check the loop and the two previously approved bubble
  origins. Release W: no new thrust bubbles, ordinary audio release, and short
  braking of Hydro's forward carry. At full speed in still water it stops within
  three native ticks after the released sample is applied, with under half a
  block of Hydro carry. The next W starts thrust without another Sprint press.
- Tap Sprint during thrust to disarm. Repeat with hold and toggle Sprint
  options and a remapped binding. On dry land and outside Hydro eligibility,
  normal vanilla Sprint still applies.
- Hold Sprint across leaving and reentering eligible water or an unequip/re-equip
  boundary. W alone must not restart Hydro: release and make a fresh submerged
  Sprint press. Check pitch, Jump/Sneak, air, and a native current separately.
- During a normal W/Sprint release in valid wet presentation, the audio uses
  its existing release tail. On dry/session invalidation it hard-stops rather
  than synthesizing another tail. Record audible results independently of the
  hold/toggle effects and bubble placement.

Client and server must both use this Hydro test protocol revision. A detached
native key mapping with synthetic device state and server GameTests do not
substitute for a real keyboard, GPU, or audio device retest.
