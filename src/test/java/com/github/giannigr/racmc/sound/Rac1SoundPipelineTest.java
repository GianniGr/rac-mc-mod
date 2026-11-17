package com.github.giannigr.racmc.sound;

import java.io.IOException;
import java.nio.file.*;
import java.util.EnumMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class Rac1SoundPipelineTest {
    @TempDir Path directory;
    static byte[] bank() {
        byte[] data = new byte[192];
        var b = SoundBytes.view(data);
        b.putInt(0, 3).putInt(4, 2).putInt(8, 24).putInt(12, 152).putInt(16, 176).putInt(20, 16);
        b.putInt(24, 0x6b6c4253).putInt(28, 1);
        b.putShort(24 + 22, (short) 1).putShort(24 + 24, (short) 1);
        b.putInt(24 + 28, 64).putInt(24 + 32, 80);
        data[88] = 127; data[92] = 1;
        b.putInt(104, 1); data[113] = 127; data[114] = 72; data[177] = 1;
        return data;
    }
    @Test void bankDecodeAndUnsupportedProgramsAreExplicit() throws Exception {
        var bank = new Rac1SoundBank(bank());
        var tone = bank.tone(0);
        assertEquals(22050, tone.sampleRate()); assertEquals(28, tone.decoded().samples());
        assertThrows(IOException.class, () -> bank.tone(1));
        byte[] bytes = bank(); bytes[92] = 2;
        byte[] layered = bytes;
        assertThrows(IOException.class, () -> new Rac1SoundBank(layered).tone(0));
        bytes = bank(); SoundBytes.view(bytes).putInt(104, 30);
        byte[] script = bytes;
        assertThrows(IOException.class, () -> new Rac1SoundBank(script).tone(0));
    }
    @Test void refusesMalformedBankOffsetsAndOverflow() {
        assertThrows(IOException.class, () -> new Rac1SoundBank(new byte[12]));
        for (int offset : new int[]{8, 12, 16, 20, 52, 56}) {
            byte[] data = bank(); SoundBytes.view(data).putInt(offset, Integer.MAX_VALUE);
            assertThrows(IOException.class, () -> new Rac1SoundBank(data));
        }
    }
    @Test void mapUsesClassOrderLocalIndexAndClearsRuntimeHandle() throws Exception {
        byte[] index = new byte[300], blob = new byte[128];
        var b = SoundBytes.view(index);
        b.putInt(24, 2).putInt(28, 128).putInt(0x70, 200);
        b.putInt(132, 505).putInt(164, 500);
        b.putShort(208, (short) 32).putShort(210, (short) 2);
        b.putShort(212, (short) 40).putShort(214, (short) 1);
        b.putShort(232, (short) 178).putShort(236, (short) 179).putShort(240, (short) 173);
        blob[13] = 2;
        SoundBytes.view(blob).putInt(40, 64).putShort(64 + 26, (short) 1).putInt(64 + 28, 999);
        var resolved = Rac1SoundMap.resolve(index, blob, 505, 0);
        assertEquals(179, resolved.cue()); assertEquals(0, SoundBytes.view(resolved.metadata()).getInt(28));
        assertThrows(IOException.class, () -> Rac1SoundMap.resolve(index, blob, 777, 0));
        assertThrows(IOException.class, () -> Rac1SoundMap.resolve(index, blob, 505, 2));
        b.putShort(210, (short) 1);
        assertThrows(IOException.class, () -> Rac1SoundMap.resolve(index, blob, 505, 0));
    }
    static Rac1AudioCatalog catalog() {
        var clips = new EnumMap<Rac1AudioEvent, Rac1AudioCatalog.Clip>(Rac1AudioEvent.class);
        for (var event : Rac1AudioEvent.values()) {
            byte[] definition = new byte[32], cue = new byte[12], grain = new byte[40];
            SoundBytes.view(definition).putInt(12, 1024); cue[0] = 127; grain[9] = 127;
            clips.put(event, new Rac1AudioCatalog.Clip(new byte[56], 22050, definition, cue, grain,
                    "a".repeat(64), "b".repeat(64), 173, -1, 28, false));
        }
        return new Rac1AudioCatalog(clips);
    }
    @Test void sidecarRoundTripIdentityCorruptionAndAtomicStorage() throws Exception {
        var store = new Rac1SoundCache(directory);
        String source = "a".repeat(40);
        assertTrue(store.read(source).isEmpty());
        store.write(source, catalog());
        assertEquals(Rac1AudioEvent.values().length, store.read(source).orElseThrow().clips().size());
        try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
        assertTrue(store.read("b".repeat(40)).isEmpty());
        byte[] bytes = Rac1SoundCache.encode(source, catalog());
        assertThrows(IOException.class, () -> Rac1SoundCache.decode(bytes, "b".repeat(40)));
        bytes[20] ^= 1;
        assertThrows(IOException.class, () -> Rac1SoundCache.decode(bytes, source));
        assertThrows(IllegalArgumentException.class, () -> store.read("../escape"));
        assertThrows(IOException.class, () -> Rac1SoundCache.decode(new byte[2], source));
    }
    @Test void catalogDefensiveCopies() {
        var clip = catalog().clips().get(Rac1AudioEvent.CRATE_BREAK);
        clip.pcm()[0] = 99; clip.definition()[0] = 99;
        assertEquals(0, clip.pcm()[0]); assertEquals(0, clip.definition()[0]);
        assertThrows(UnsupportedOperationException.class, () -> catalog().clips().clear());
    }
    @Test void importGenerationRejectsStaleCompletionsAndClearsCapability() {
        var state = new Rac1AudioGeneration();
        long first = state.invalidate(), replacement = state.invalidate();
        assertFalse(state.publish(first, catalog())); assertNull(state.current());
        assertTrue(state.publish(replacement, catalog())); assertNotNull(state.current());
        state.invalidate();
        assertNull(state.current()); assertFalse(state.publish(replacement, catalog()));
    }
    @Test void semanticDefinitionsUseVerifiedClassSlotsNotUnknownWeapons() {
        assertEquals(Rac1AudioEvent.CRATE_BREAK, Rac1AudioEvent.crateBreak(506));
        assertEquals(Rac1AudioEvent.HEALTH_CRATE_BREAK, Rac1AudioEvent.crateBreak(501));
        assertEquals(Rac1AudioEvent.AMMO_CRATE_BREAK, Rac1AudioEvent.crateBreak(511));
        assertNull(Rac1AudioEvent.crateBreak(168)); assertNull(Rac1AudioEvent.crateBreak(11));
        assertEquals(14, Rac1AudioEvent.attraction(5).classId());
        assertEquals(15, Rac1AudioEvent.attraction(20).classId());
        assertEquals(16, Rac1AudioEvent.attraction(50).classId());
        for (var event : Rac1AudioEvent.values()) assertEquals(1, event.level());
    }
    @Test void beepThresholdsAreDiscreteNoDuplicateTickOrTerminalPlayback() {
        assertTrue(Rac1AudioSignal.beep(60, 59)); assertTrue(Rac1AudioSignal.beep(40, 39));
        assertTrue(Rac1AudioSignal.beep(20, 19)); assertTrue(Rac1AudioSignal.beep(5, 4));
        assertFalse(Rac1AudioSignal.beep(59, 59)); assertFalse(Rac1AudioSignal.beep(59, 58));
        assertFalse(Rac1AudioSignal.beep(1, -2)); assertFalse(Rac1AudioSignal.beep(-1, 60));
    }
    @Test void packetRejectsNonfiniteAndOutOfWorldPositions() {
        var dimension = net.minecraft.resources.ResourceLocation.withDefaultNamespace("overworld");
        assertThrows(IllegalArgumentException.class, () -> new Rac1AudioSignal(Rac1AudioEvent.CRATE_BREAK, Double.NaN, 0, 0, 1, dimension));
        assertThrows(IllegalArgumentException.class, () -> new Rac1AudioSignal(Rac1AudioEvent.CRATE_BREAK, 0, 0, 30000001, 1, dimension));
    }
    @Test void packetRoundTripIsBoundedSemanticPositionSeedAndDimensionOnly() {
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(
                io.netty.buffer.Unpooled.buffer(), net.minecraft.core.RegistryAccess.EMPTY);
        try {
            var signal = new Rac1AudioSignal(Rac1AudioEvent.TNT_BEEP, 1.25, 64, -2.5, 123,
                    net.minecraft.resources.ResourceLocation.withDefaultNamespace("overworld"));
            Rac1AudioSignal.CODEC.encode(buffer, signal);
            assertTrue(buffer.readableBytes() < 100);
            assertEquals(signal, Rac1AudioSignal.CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }
}
