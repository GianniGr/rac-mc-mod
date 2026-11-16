# Development handoff — vendor candidate 2026-11-16

## State

Owner resumed STANDARD vendor implementation on 2026-11-16: P22 followed by
separate T. Branch: `feature/issue12-vendor-visual`, based on `26d9ff5`.
Crate PR #38 remains separate/open; its worktree and branch are not modified.
P22's vendor candidate is implemented; independent exact-tree T verification
and in-game acceptance remain pending. No publication/merge is implied.

## Implemented — crate visual/physics slice (#10)

- Original RaC1 bolt (500), blue health (501), ammo (511), explosive (505) crates.
- Placeable one-block models, Creative items, shared extraction/cache/rendering.
- GUI/held transform corrections, including vanilla item-cube origin regression tests.
- Mining visibility/crack-buffer correction; wooden-plank hardness, no drops.
- Vanilla falling-block physics, custom falling-crate rendering, intact landing.
- Explosive crate remains visual-only. No bolt/health/ammo rewards or explosions.
- Purple health crate excluded: RaC2, not this RaC1 slice.

P20 reported build success and 432 tests: 407 passed, 25 skipped, zero failures.
These are worker-reported checks, not final independent acceptance. T09 tested
the earlier candidate and recorded failures; owner stopped T09 before final fixes.
Owner observed placed blocks working and reported GUI/held/mining defects that
were subsequently corrected in code. Latest GUI/held/mining/falling behavior still
needs explicit in-game acceptance. Last client exited cleanly; clean exit is not
proof of feature correctness. Sand/gravel renderer delegation needs runtime coverage.

## Vendor candidate and deferred features

### Gadgetron vendor (#12)

P22 implemented body class 11 and hologram class 1143 through the shared animated
pipeline, schema 5/converter 4 cache, Creative block item, grounded collision and
client-local proximity visuals. Uniform closed footprint fits one block; body
remains closed and the logo grows/counter-spins. Shop, right-click and unfold
remain deferred. See [vendor.md](vendor.md) for exact local defaults and known
chrome/material/callback beam/glow/manipulator fidelity gaps.

P22 measured Java compilation plus 39 focused passing checks, including one
read-only NTSC real-disc extraction/pose/in-memory cache round-trip check. No
full-suite, PAL or client/dedicated-server runtime acceptance is claimed.
Initial compiler API mistakes and one synthetic quaternion-convention expectation
were corrected before that passing run. Final independent T owns broader
regression/runtime verification on the exact committed candidate.

The initial specialist-register blocker was resolved by the canonical workflow
repair, not by a P22 bypass. Resumed worktree registration/initial-use claim
succeeded at private revision 61; evidence-directory registration at revision 62.
Private claims/dispositions are reported through the documented wrapper at handoff;
historical frozen cleanup incidents remain outside this feature's scope.

### Bolts (#10 / #11)

R03 confirmed class/value mapping: 13=1, 14=5, 15=20, 16=50. Original payout
varies around an instance-specific base, split into denomination pickups. Base
value is unresolved; owner's 5–150 total is a fallback, not verified original rule.
Original bolt crates pay bolts; health/ammo/explosive crates do not.
Owner wants holdable non-functional bolt item, collectible world bolts, original
HUD presentation hidden four seconds after last pickup, and mandatory explosion
immunity. None implemented. Decide payout policy and item-vs-currency semantics.

### Explosive behavior (#10)

R04 researched contact/landing activation, flashing and approximately three-second
fuse (60 Minecraft ticks, NOT 179 Minecraft ticks). Owner requires player/mob
touch and genuine falling landing to activate; ordinary adjacent blocks do not.
Blast may destroy all RaC crates; explosive crates chain-arm. Non-crate Minecraft
blocks and dropped bolts must survive. No implementation. Resolve blast radius,
entity damage/knockback, occlusion and reward policy before coding. Research
recommends original no-occlusion sphere behavior; this is not owner acceptance.

## Private evidence (outside Git)

Under `C:\Users\giann\AppData\Local\Temp\opencode`:
- `r02-vendor-research\HANDOFF.md`
- `r03-bolts-research\R03-HANDOFF.md`
- `r04-explosive-research\R04-HANDOFF.md`
- `p21-diag2` vendor diagnostics; `racmc-research` private diagnostic helpers.
- `pr37-cleanup-archive-20261110` retained unique worlds/config/logs/screenshots.

No diagnostic research package belongs in shipped source. No ISO, extracted game
assets or generated cache belongs in Git. Old PR37 worktrees/local branches were
removed after merge checks; unique data was archived first. Main checkout's
modified `docs/development.md` is unrelated protected user work.

## Resume

Read this handoff and issue #1; obtain owner resume/scope decision. Keep separate
research, programming and independent test roles. Do not restart T09 implicitly.
Blaster fixes/shared weapon cleanup remain deferred to #11; PR #37's accepted
limitations were not fixed by crate work. Owner retains merge authority.
