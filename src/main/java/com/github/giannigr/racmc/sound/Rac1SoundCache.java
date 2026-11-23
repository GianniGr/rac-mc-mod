package com.github.giannigr.racmc.sound;

import java.io.*;
import java.nio.file.*;
import java.util.EnumMap;
import java.util.Optional;

/** Independent RACS sidecar; P43's physical fourteen-entry profile uses v5, never overwrites v4/RACM. */
public final class Rac1SoundCache {
    public static final int VERSION = 5;
    private static final int MAX_FILE = 64 * 1024 * 1024;
    private final Path root;
    public Rac1SoundCache(Path root) { this.root = root.toAbsolutePath().normalize(); }
    private Path file(String sourceHash) {
        if (!sourceHash.matches("[0-9a-f]{40}")) throw new IllegalArgumentException("Expected verified source SHA-1");
        return root.resolve("sound-" + sourceHash + "-v" + VERSION + ".racs");
    }
    public Optional<Rac1AudioCatalog> read(String sourceHash) throws IOException {
        Path file = file(sourceHash);
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Sound sidecar is not a regular file");
        if (Files.size(file) > MAX_FILE) throw new IOException("Oversized sound sidecar");
        try (var in = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.of(decode(in.readNBytes(MAX_FILE + 1), sourceHash));
        }
    }
    public void write(String sourceHash, Rac1AudioCatalog catalog) throws IOException {
        Path target = file(sourceHash);
        Files.createDirectories(root);
        byte[] data = encode(sourceHash, catalog);
        decode(data, sourceHash);
        Path staging = Files.createTempFile(root, "sound-", ".staging");
        // A failed staging/atomic move is retained for diagnosis, not a reason to discard a valid cache.
        Files.write(staging, data);
        try (var channel = java.nio.channels.FileChannel.open(staging, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
        decode(Files.readAllBytes(staging), sourceHash);
        Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
    static byte[] encode(String hash, Rac1AudioCatalog catalog) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            out.writeInt(0x52414353); out.writeInt(VERSION); out.writeUTF(hash);
            var clips = catalog.clips();
            if (!clips.keySet().equals(java.util.Set.copyOf(Rac1AudioEvent.IMPORTED))) {
                throw new IOException("Incomplete or nonphysical sound catalog");
            }
            out.writeInt(clips.size());
            for (var event : Rac1AudioEvent.IMPORTED) {
                var clip = clips.get(event);
                out.writeUTF(event.id()); out.writeFloat(clip.rate());
                out.write(clip.definition()); out.write(clip.cue()); out.write(clip.grain());
                out.writeUTF(clip.bankHash()); out.writeUTF(clip.adpcmHash()); out.writeInt(clip.cueId());
                out.writeInt(clip.loopStart()); out.writeInt(clip.loopEnd()); out.writeBoolean(clip.repeat());
                byte[] pcm = clip.pcm(); out.writeInt(pcm.length); out.write(pcm);
            }
        }
        byte[] body = bytes.toByteArray();
        bytes.writeBytes(SoundBytes.hash(body).getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        return bytes.toByteArray();
    }
    static Rac1AudioCatalog decode(byte[] data, String hash) throws IOException {
        if (data.length < 80 || data.length > MAX_FILE) throw new IOException("Invalid sound sidecar size");
        int size = data.length - 64;
        String digest = new String(data, size, 64, java.nio.charset.StandardCharsets.US_ASCII);
        if (!SoundBytes.hash(java.util.Arrays.copyOf(data, size)).equals(digest)) throw new IOException("Sound sidecar checksum");
        try (var in = new DataInputStream(new ByteArrayInputStream(data, 0, size))) {
            if (in.readInt() != 0x52414353 || in.readInt() != VERSION || !in.readUTF().equals(hash)) {
                throw new IOException("Sound sidecar identity/version mismatch");
            }
            int count = in.readInt();
            if (count != Rac1AudioEvent.IMPORTED.size()) throw new IOException("Incomplete sound catalog");
            var clips = new EnumMap<Rac1AudioEvent, Rac1AudioCatalog.Clip>(Rac1AudioEvent.class);
            for (int n = 0; n < count; n++) {
                String id = in.readUTF();
                var event = Rac1AudioEvent.IMPORTED.stream().filter(e -> e.id().equals(id))
                        .findFirst().orElseThrow(() -> new IOException("Unknown semantic sound"));
                float rate = in.readFloat();
                byte[] definition = in.readNBytes(32), cue = in.readNBytes(12), grain = in.readNBytes(40);
                String bank = in.readUTF(), adpcm = in.readUTF();
                int cueId = in.readInt(), loopStart = in.readInt(), loopEnd = in.readInt();
                boolean repeat = in.readBoolean();
                int length = in.readInt();
                if (length <= 0 || length > PsAdpcm.MAX_FRAMES * 56 || length > in.available()) throw new IOException("Invalid PCM length");
                var clip = new Rac1AudioCatalog.Clip(in.readNBytes(length), rate, definition, cue, grain, bank, adpcm,
                        cueId, loopStart, loopEnd, repeat);
                if (clips.put(event, clip) != null) throw new IOException("Duplicate semantic sound");
            }
            if (in.available() != 0) throw new IOException("Trailing sound data");
            return new Rac1AudioCatalog(clips);
        } catch (IllegalArgumentException ex) { throw new IOException("Invalid sound metadata", ex); }
    }
}
