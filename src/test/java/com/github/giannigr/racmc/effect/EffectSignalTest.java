package com.github.giannigr.racmc.effect;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EffectSignalTest {
    @Test void semanticWireRoundTripsAndRejectsMalformedOrTrailingBytes() throws Exception {
        var signal = signal(UUID.randomUUID(), 3, 100);
        byte[] bytes = EffectSignalCodec.write(signal);
        assertEquals(signal, EffectSignalCodec.read(bytes));
        assertTrue(bytes.length <= EffectSignalCodec.MAX_BYTES);
        assertThrows(java.io.IOException.class, () -> EffectSignalCodec.read(new byte[EffectSignalCodec.MAX_BYTES + 1]));
        assertThrows(java.io.IOException.class, () -> EffectSignalCodec.read(java.util.Arrays.copyOf(bytes, bytes.length - 1)));
        assertThrows(java.io.IOException.class, () -> EffectSignalCodec.read(java.util.Arrays.copyOf(bytes, bytes.length + 1)));
        bytes[0] = 99;
        assertThrows(java.io.IOException.class, () -> EffectSignalCodec.read(bytes));
    }
    @Test void impactWireRequiresValidatedOriginalIndependentVectors() throws Exception {
        var impact = new EffectSignal.Impact(new EffectSignal.Point(2, 0, 0), new EffectSignal.Point(-1, 0, 0));
        var signal = new EffectSignal(EffectSignal.Semantic.BLASTER_IMPACT, "minecraft:overworld",
                UUID.randomUUID(), 1, 100, 9, EffectSignal.Op.START, new EffectSignal.Point(1, 2, 3), impact);
        assertEquals(signal, EffectSignalCodec.read(EffectSignalCodec.write(signal)));
        assertThrows(IllegalArgumentException.class, () -> new EffectSignal.Impact(
                new EffectSignal.Point(0, 0, 0), new EffectSignal.Point(0, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> new EffectSignal(EffectSignal.Semantic.BLASTER_IMPACT,
                "minecraft:overworld", UUID.randomUUID(), 1, 100, 9, EffectSignal.Op.START, new EffectSignal.Point(1, 2, 3)));
    }
    private static EffectSignal signal(UUID id, long sequence, long tick) {
        return new EffectSignal(EffectSignal.Semantic.CRATE_TNT_BLAST, "minecraft:overworld", id,
                sequence, tick, 17, EffectSignal.Op.START, new EffectSignal.Point(1, 2, 3));
    }
    @Test void duplicateAndOutOfOrderSequencesDoNotReplay() {
        var gate = new EffectEventGate();
        gate.beginWorld("minecraft:overworld", 1);
        UUID id = UUID.randomUUID();
        assertTrue(gate.accept(signal(id, 2, 100), 100, 1));
        assertFalse(gate.accept(signal(id, 2, 100), 100, 1));
        assertFalse(gate.accept(signal(id, 1, 100), 100, 1));
        assertTrue(gate.accept(signal(id, 3, 101), 101, 1));
    }
    @Test void dimensionGenerationAndAgeRejectStaleEvents() {
        var gate = new EffectEventGate();
        gate.beginWorld("minecraft:the_nether", 1);
        var signal = signal(UUID.randomUUID(), 0, 100);
        assertFalse(gate.accept(signal, 100, 1));
        gate.beginWorld("minecraft:overworld", 2);
        assertFalse(gate.accept(signal, 100, 1));
        assertFalse(gate.accept(signal, 301, 2));
        assertFalse(gate.accept(signal, 79, 2));
        assertTrue(gate.accept(signal, 100, 2));
    }
    @Test void poolPressureNeverEvictsLiveDedupTombstones() {
        var gate = new EffectEventGate();
        gate.beginWorld("minecraft:overworld", 1);
        var first = signal(UUID.randomUUID(), 0, 100);
        assertTrue(gate.accept(first, 100, 1));
        for (int n = 1; n < 4096; n++) assertTrue(gate.accept(signal(UUID.randomUUID(), 0, 100), 100, 1));
        assertFalse(gate.accept(signal(UUID.randomUUID(), 0, 100), 100, 1));
        assertFalse(gate.accept(first, 100, 1));
        assertEquals(4096, gate.size());
    }
    @Test void entityReuseRequiresNewLifecycleAndMalformedValuesFailClosed() {
        var gate = new EffectEventGate();
        gate.beginWorld("minecraft:overworld", 1);
        assertTrue(gate.accept(signal(UUID.randomUUID(), 0, 100), 100, 1));
        assertTrue(gate.accept(signal(UUID.randomUUID(), 0, 100), 100, 1));
        assertThrows(IllegalArgumentException.class, () -> new EffectSignal.Point(Double.NaN, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> signal(UUID.randomUUID(), -1, 100));
    }
}
