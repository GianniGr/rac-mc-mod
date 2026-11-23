# Hydro outlet alignment follow-up

On isolated `80c6915925d9862b6320e9f016bcaf1b56b9ffe5`, the owner saw Hydro
bubbles, but saw them starting slightly above Clank's water jets. That report
does not establish an audio verdict or settle the separate pose question.

Retained R13 NTSC/PAL class609 metadata identifies list0 `[0,1,2,3,4]` and
list1 `[0,5,6,7,8]`; retained R15 A/V analysis identifies these as the two
animated outlets used by the original emitter. Their terminal joint origins
are the authored landmarks. The retained evidence does not measure the
visible mesh rims independently, so there is no fitted rim offset.

The pinned emitter already resolved those joints, then applied the original
0.15 rearward displacement along the **pack mount's** axis and one-sided
world-axis jitter. The former gains a vertical component when the native
body pitches; the latter always adds nonnegative world Y. This host alignment
change starts bubbles at local `(0,0,0)` of each terminal outlet instead. It
does not apply a world-Y correction or guess a vent axis, and it leaves the
stored-pitch/yaw velocity, displacement, buoyancy and source60 dispatch timer
unchanged.

Third-person/remote capture uses the pose that draws model609 in that render
frame. First person uses an attachment-only native player pose at the frame's
interpolated entity position, with the same torso and back mount; it draws
nothing. The entity layers precede the particle stage, so that stage consumes
the current mouth sample rather than a previous tick's request. No current
outlet sample means no new bubbles for that frame; existing particles age
normally. Neither view changes the player model pose, visual policy, arm
handedness, water state, movement, or audio.

In a fresh isolated instance, wear the pack in deep water. On builds with the
[Hydro toggle](clank-hydro-toggle.md), tap configured Sprint, then hold W to
thrust. In third person, inspect **new** bubble centers
at both jets while level, diving and rising; older bubbles naturally drift
above the mouths. Repeat in first person for continuity, then switch back
while still thrusting to check for an old-view spawn. Repeat with W
press/release if desired, recording vanilla pose and audible audio separately
from bubble placement. No whole-disc reruns or new model reads were used to
make this change. Automated matrix and emitter tests are not a live mesh-rim
or audible verdict.
