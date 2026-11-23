# Verified media read lease (portable shared hook)

This hook has no shop, Clank, save, wire or cache-schema dependency. Apply this
shared change independently; each feature supplies its own small loader adapter.
It never replaces complete startup/explicit-selection validation with cache
metadata, a path, a timestamp or a supplied disc key.

## API and use

```java
long generation = media.generation(); // captured when the asset request is made
// On the feature's bounded asset executor, NOT the render thread:
try (var lease = media.lease(selectedPath, expectedDiscKey)) {
    if (lease.generation() != generation) throw new IOException("Selection changed");
    lease.checkValid();
    var asset = existingLoaderThatAcceptsRac1DiscImage(lease);
    lease.checkValid(); // includes source-change and cancellation checks
    // Return asset plus generation; before publishing on the client thread,
    // check media.isReady() and media.generation() == generation again.
}
```

`expectedDiscKey` has the existing `revision|product|whole-image-SHA1` shape. It
only matches the manager's internally established fully verified identity; it
cannot grant permission to open another path. Do not call another
`Rac1DiscValidator.inspect` in the adapted loader, including on a sidecar hit.
Validate the feature's own sidecar/converted assets exactly as before. A corrupt
sidecar is not authority to read a different image.

* `lease(...)` is memory-only and rejects unconfigured/checking/mismatched/closed
  selections. Use/check it on an executor because actual reads and `checkValid()`
  perform filesystem identity/change checks.
* Lease implements the existing `Rac1DiscImage` interface. Every byte/file read is
  capped at64MiB; sector arithmetic and image extents are checked before allocation.
  Existing importer limits still apply, and consumers must not expand source scope.
* All actual reads of the retained image are serialized as indivisible seek/read
  operations. Independent consumers cannot move each other's cursor.
* `openFile` is a bounded memory snapshot, not a shared-cursor stream; closing one
  stream/lease cannot close the manager's image or another lease. Stream reads
  reject an expired lease/generation. Prefer bounded sector/byte reads for assets.
* `isCurrent()` is a memory-only hint, not a replacement for the final worker
  `checkValid()` and client publication-generation guard.
* `invalidateLeases()` increments the generation but keeps the unchanged fully
  verified selection. The portable client lifecycle hook calls it on logout and
  resource reload. Consumers must key/detach in-memory jobs and prepared results
  by generation, not just path or disc key.
* `unset()` unsets the **active session only**, cancels jobs and closes the retained
  selection. It does not delete/rewrite remembered configuration; `start()` must
  still fully verify any remembered selection on a new session.
* `close()` revokes everything, cancels validation, closes retained handles after
  an in-flight bounded read, and shuts down the verifier. It never waits for a
  consumer to voluntarily close an abandoned lease.

## Validation and changes

The manager retains only the image returned by successful complete validation.
Stable file identity/real path/size/creation/modification facts must agree before
and after verification. Reads check those facts before and after I/O. Ordinary
mutation, disappearance, symlink retargeting or same-name replacement revokes the
image and closes the ready gate. File stamps are change guards **after the full
hash**, never persisted validation authority.

The Windows JDK reports null `BasicFileAttributes.fileKey()`. The implementation
uses Minecraft's already supplied JNA Kernel32 read-attributes API to obtain the
volume and128-bit file ID instead; it opens no writable handle and always closes
that temporary metadata handle. No new build dependency. Other providers use their
stable file key; inability to establish identity fails closed rather than falling
back to timestamp-only identity. This is not a defense against a hostile filesystem
or privileged in-place writes that deliberately forge all observed metadata.

Explicit same-path selection revokes old handles and fully hashes again. A failed
different replacement may retain the verified original selection, as before, but
all earlier-generation leases are unusable. While any selection attempt is
checking, leases are unavailable. Newer requests/unset/close cancel older work;
obsolete progress/results cannot overwrite current diagnostics/configuration.
The verifier has one daemon and at most one queued request. Cancellation is checked
at whole-image digest progress boundaries. Startup hashing is never skipped.

Windows may deny replacing a file while its retained read handle exists. That is
safe: the unchanged selection remains usable. Unset/reselect first to close the
old handle, then replace and fully validate; do not force-delete an open image.

## Evidence and portability

ISO-focused tests:71passed,1conditional skip/72, including7new lease tests:
no capability from metadata alone, one startup hash/many leases, restart rehash,
independent concurrent reads/streams, mutation, same-path replacement/revalidation,
failed replacement, resource invalidation, rapid cancellation/unset/close and limits.
Initial Windows null-key and fixture timing/replacement failures are retained.

The manager's pre-change file matches locally observed `origin/main`
`d6dd262aec76d7fe9a197c331318a6907af8fd2c`; the new class paths are absent there.
This is a file-context portability check, not an application/integration on current
main or P36's worktree. P36 must apply and verify its own target. No shop commit is
needed to consume this interface, and no P36 worktree has been edited.

No interactive launch, native audio, new retail ranges, raw bank/PCM export,
publication or deletion is part of this hook. Removing a repeated hash does not
certify live loading latency; profile full startup, first asset, disk sidecar hit,
GPU preparation and first usable frame separately.
