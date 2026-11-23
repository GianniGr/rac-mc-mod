# Clank Creative readiness: isolated preparation slice

Owner reports3–5 seconds before Clank Creative icons appear. This performance
branch starts at exact AV checkpoint `0c8a410`; it does not alter the long-jump
blur investigation, approved high jump, glide audio, or any movement behavior.

## Baseline path (`0c8a410`), inspected before optimization

1. `Rac1ClankResources.update` is lazy. The ordinary tick calls it only in a
   world, with `VIEWS.anyWorn`; item rendering requests it on first draw.
2. Background work reads the existing bounded `.clnk` sidecar. A miss calls
   `Rac1ClankImporter.read(Path)`, which revalidates/hashes the full image before
   importing level1 classes601/607/608 and atomically writing the existing cache.
3. Sidecar decode validates and reconstructs an immutable encoded assembly.
   The completed future is joined on the client thread, then `copyModel` decodes
   all three models again into consumer-owned arrays on that thread.
4. The first item draw samples idle sequence1/time0 and fits each icon on the
   rendering thread. Body601 is sampled separately for the two icons.
5. The first role's texture request registers its dynamic textures there too.

These are code-path facts, not an attribution of all observed delay to a single
stage. Independent cache-only CPU measurements and their limitations are in
the worker evidence; GPU/upload and click-to-visible timings need a separately
scheduled client test. P35's shop whole-image timings are not Clank measurements.

Cache-only NTSC measurements used one existing1,084,028-byte converted sidecar,
read-only, in three sequential fresh test JVMs (four iterations each). First
read/decode was52.139–79.466ms; repeated reads15.075–29.186ms. The existing
consumer model-copy stage was3.289–6.002ms first, and existing idle pose/fit
preparation for **both icons combined**11.008–25.731ms first. Both icons' prepared positions/normals/fits
matched the existing route exactly for this sidecar as well as synthetic tests.

These measured CPU stages do not by themselves explain the reported3–5 seconds.
Cold-miss validation/import, lazy-request delay, native texture upload and real
UI/frame timing were not measured. OS/file cache, other machine activity, GC and
JIT warm-up were uncontrolled. New preparation ran after the baseline stages,
so its8.657–12.192ms first invocation is **not a fair cold speedup ratio**.
Prepared-object lookup timing is not UI readiness. No ISO was opened, no GPU
registration ran, and no converted payload was copied or changed on disk.

## Prepared CPU handoff

`Rac1ClankPreparedAssets` prepares detached model copies, the three unchanged
idle poses and both unchanged icon fits off-thread. Both icons reuse the same
prepared body pose. Model/pose arrays belong to one consumer generation; the
encoded assembly/cache remains immutable. Optional stage timings contain only
durations. There is no file, ISO, GPU, Minecraft singleton or background task
launched by this class, and no persistent prepared cache/schema.

The result is tagged with disc identity and resource generation for rejection
after invalidation. That tag is **not validation authority** or a complete
publication fence. The future lease's validity and the current request must
also be checked before publishing. Tests cover identity/generation mismatch,
detached ownership, exact existing icon geometry/fit, worker execution and the
unchanged sidecar format.

The first independent preparation-only commit was `4d302ef`. P35 then supplied
the standalone shared hook `0bc6ecf`, consumed alone as `df793f1` without its shop
ancestors/adapter. No further shared media-manager changes were made here.

## Clank-local adapter after P35 coordination

Optional preparation now starts on client ticks **after the existing verified
media+asset gate is ready**, including the menu, without waiting for a worn
pack or first Creative render. It never becomes an entry/startup prerequisite.

The worker acquires P35's typed read-only lease for the captured path, expected
main disc key and media generation. Existing `.clnk` is tried first, unchanged.
A miss uses the same bounded level1 classes601/607/608 importer through that
lease, not another `validator.inspect(Path)`. Legacy path/Hydro import methods
retain their original validation. Leases are checked before/after reading,
importing and preparing; the worker closes only its consumer lease.

Consumer model decoding, all three idle poses and both icon fits finish on the
single Clank worker. The executor permits one active plus one latest queued
request. The client slot accepts a result only for its current request, disc/
cache owner and resource generation; reload/logout/source changes detach old
requests. Failures report once per request, not every render. Cache-write
failure can leave validated in-memory assets usable; any old-key atomic cache
write already underway can finish, but cannot publish resources for a newer
selection or write under another key.

Texture registration stays on the client/render thread, **one role per client
tick**, using the existing texture leases. Models/items become visible together
only after all three roles' textures are ready. The item draw path now performs
memory lookups and the existing mesh draws only—no sidecar/ISO read, model decode,
pose/fit preparation or first texture registration.

Debug timings distinguish sidecar/import/cache write/CPU stages, role texture
registration, ready publication, and first item draw callback. A draw callback
is **not** a displayed frame or click-to-visible measurement. Actual GPU and
live cold/prepared/warm icon acceptance remain unmeasured; the3–5s report is not
claimed fixed from headless tests or cache-only CPU measurements.

## Required integration contract

The coordinated portable API is documented in `docs/media-read-leases.md`.
Preserve these boundaries in further work:

- Keep full required startup verification. Obtain a typed, generation-scoped,
  read-only capability from that verified selection, with actual identity and
  invalidation/close ownership. Never substitute a key, path, file timestamp or
  sidecar checksum for verification.
- Use existing `.clnk` first. On a miss, run the bounded importer under the
  capability's serialized/isolated read contract, without a second whole-image
  scan. Stale jobs must neither publish resources nor write under another disc.
- Begin optional Clank-local preparation after verified assets are ready; do
  not extend `allowsWorldEntry` or add a mandatory startup gate. Keep GPU texture
  registration on the client/render thread, separately measured and budgeted.
- Reuse the current generation-scoped texture leases. Reject stale completions
  and invalidate CPU/icon/texture references on source/world/reload lifecycle
  as appropriate. Do not create another shared media manager or cache.
- Only two existing Heli/Thruster Creative entries. No Hydro Creative item,
  Hydro preload, permission/grant/save/wire changes or copied proprietary assets.
