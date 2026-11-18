# Server-local wrench data: startup and fallback contract

Owner-approved option1: original server-authoritative animation data is imported
from the server operator's own locally configured supported disc. No assets are
bundled, downloaded, uploaded from clients or sent in action packets.

**Fallback chosen before startup wiring:** missing, invalid or still-loading data
disables only managed wrench actions, with an explicit local availability status.
It does **not** prevent the server from starting or unrelated mod content from
working. Native core melee/ordinary crate behavior is unchanged. The existing
mandatory **client** ISO/world-entry gate is unchanged.

P28 owns sampler installation, action capability/network status, clocks, damage,
calibrated Steve poses and actual draw hooks. This module makes original local
data available; readiness of the data is not proof those integrations are complete.

## Configuration (operator-local, no GUI)

Dedicated server: `config/racmc/racmc-server-wrench.properties`:

```properties
# Literal paths: Windows backslashes do not need escaping.
iso=
cacheDirectory=server-wrench-cache
```

- `iso`: local ISO filename, blank/missing means MISSING (managed actions disabled).
  Absolute or relative to `config/racmc`, not process working directory. No path
  is received from a player or network. Unsupported/missing/corrupt ISO fails closed.
- `cacheDirectory`: converted cache directory, default `server-wrench-cache`.
  Absolute or relative to `config/racmc`. Unusable values fail closed rather than
  silently writing elsewhere. A changed setting never moves/deletes the old cache.
- Both are strings with no numeric range/step. First duplicate key wins; paths
  use literal text. Empty cache directory uses the default; empty ISO never trusts
  a cache alone. Settings are read once per server startup, not live-reloaded.
- No new screen, focus/caret control or interactive UI is introduced.

Integrated singleplayer, **only if this explicit override file is absent**, reuses
the legitimate common `racmc-media.properties` path. The common configured cache
may be read and integrity-checked without any Minecraft client singleton. The
provider still validates the ISO itself. It never repairs/writes the shared
client cache: imports/recovery go to its separate server cache. An explicit blank
override disables this fallback intentionally.
Shared and writable server cache directories must not alias or nest inside each
other (existing junction/symlink ancestors are resolved for this check).

## Safety and API

One asynchronous pure-Java provider per server identity; no ISO/hash/parser work
on action/render/tick threads. Missing/failure is status, not a server-start crash.
Shutdown invalidates availability and cancels/joins only its own import worker.
Local diagnostics can contain paths; P28 must send only an availability code to
players, never those diagnostic strings, original assets, joint arrays or cache files.

`serverassets.Rac1WrenchServerData.data(server)` returns optional
`Rac1WrenchData(discKey, hero, wrench)`. `status(server)` returns
`Rac1WrenchDataStatus(state, detail, origin)`.
The direct pure-Java service is `Rac1WrenchDataProvider.open(modConfigDirectory,
dedicated)`, with `data()`, `status()` and `close()`.

READY requires the original supported full-image validation plus cache schema,
converter, disc identity and checksums, and required original hero26/43,
child1/6/10, event-time metadata and actual hand/head attachment chains.
Reads of this bounded catalog's cache are limited to 128 MiB before whole-file
allocation; oversized caches are refused/rebuilt rather than exhausting server
memory. Future catalog expansion must review this bound as well as its versions.
The module reuses the existing complete converted asset cache/catalog instead
of inventing a second binary animation format. That cache also includes existing
visual assets; they remain local and are never networked.
Historical standalone cache schema6/converter5 are superseded by P32's union7/7.
The shared importer includes vendor, bolts, HUD and wrench; see [integration.md](integration.md).
The original consolidation caution was: when combining vendor/bolt/other
feature catalogs later, reconcile their additions in `Rac1CacheImporter` and bump
the combined versions; do not resurrect competing client/server builders.

The lifecycle adapter automatically opens one provider at `ServerAboutToStartEvent`
and closes/removes it at `ServerStoppedEvent`. Data becomes available asynchronously:
P28 must observe readiness dynamically, not capture a permanently empty optional
at startup. `close()` invalidates availability and joins its positively owned
worker; no background polling/retry service remains after the one startup task.

Decoding is the existing independently authored Java ISO/WAD/moby/animation
implementation, extended by P27's raw timing/list preservation. Bounded game-format
facts come from the recorded ReRAC ISC documentation/R08 evidence; no GPL
implementation is copied/transliterated and no dependency is added.
