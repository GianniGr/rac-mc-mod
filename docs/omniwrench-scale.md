# OmniWrench size in Steve's hands

The displayed wrench is **1.18×** its previous size: +18% along all three
axes, applied once about the native grip after the hand's display/wrist
transform. It uses the same multiplier for left/right, first/third person,
held/slam and each Comet flight phase. Existing first-person framing
(`0.70 → 0.826` for the ordinary item display), third-person display
(`1.00 → 1.18`), and the managed pose/viewmodel architecture are otherwise
unchanged. The grip, actor, arms, imported proportions, slam deformation
amplitude, and GUI/ground/fixed transforms do not scale with the mesh.

## Contact and action boundaries

The visual mesh and server action math still start at the same grip socket.
The mesh has cosmetic overhang beyond the old attachment offsets. This is
deliberate: the slam sweep/tip/body queries and native interaction checks
keep their original anchors and dimensions, and the Comet keeps its release
origin, swept and sampled target anchors, flight/range/return laws and bolt
attraction. There is no increase in contact radius, reach or damage, and no
new contact with a target just because a rendered edge touches it. For
typical Steve the retained peak slam grip-to-head measurement of ~1.815
blocks implies ~0.327 blocks of extra head offset; even with the existing
0.085 retraction it fits inside the existing 0.47 tip sphere. This statement
does not assert the same clearance for externally rescaled players or that
every visible vertex is a hitbox.

The existing slam key-27 sound uses the **drawn** head (`grip + 1.18 ×
(original head − grip)` at the matching source sample), not the legacy hit
center. Cue identity, timing, audience rule, playback and all other audio
paths are unchanged. The throw model scales only after its original release
translation, rotation and spin, then uses the same scale at the managed
catch; no new scale or pivot discontinuity is introduced. No action-clock,
snapshot interpolation, catch movement, recipe, cache or wire changes.

## Checks

Automated scope covers the four native hand contexts and unchanged icon
contexts, socket pinning after cube/wrist correction, uniform scale,
source-style slam attachments and landing bounds, the key-27 emitter, and
Comet release/travel/return/catch geometry against unchanged query centers.
An opt-in actual-import rig assertion checks the enlarged head against the
server projection for both hand/skin variants; the asset-free tests do not
stand in for it. Live review still needs both hands/skin types in first and
third person, clipping across normal/slam key poses, and outbound/return/catch
transitions from owner and observer views.
