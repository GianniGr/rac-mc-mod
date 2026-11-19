package com.github.giannigr.racmc.cache;

import com.github.giannigr.racmc.asset.*;
import java.nio.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Rac1ModelCodecTest {
    @Test void wholeModelWireIdentifierPinsPriorStaticAndAnimatedPayloads() throws Exception {
        assertEquals(1,Rac1CacheCodec.MODEL_ENCODING_VERSION);
        var fixture = SyntheticVendor.content();
        // Payload digests independently extracted with the retained pre-public-seam cd63f78 codec.
        byte[] stat = Rac1CacheCodec.encodeStaticModel(fixture.staticAsset());
        byte[] anim = Rac1CacheCodec.encodeAnimatedModel(fixture.animatedAsset());
        assertEquals(151,stat.length); assertEquals(570,anim.length);
        var hash = java.security.MessageDigest.getInstance("SHA-256");
        assertEquals("f1ae07aba87868f8c147429e16f6ef5606b46c88683322f932d1e8fda431d2a7",
                HexFormat.of().formatHex(hash.digest(stat)));
        assertEquals("fcc0bba3a0c6a1b14376dd13c53a7d1d36d0e91c961520241d2ae09aa527a038",
                HexFormat.of().formatHex(hash.digest(anim)));
        assertArrayEquals(stat,Rac1CacheCodec.encodeStaticModel(Rac1CacheCodec.decodeStaticModel(stat)));
        assertArrayEquals(anim,Rac1CacheCodec.encodeAnimatedModel(Rac1CacheCodec.decodeAnimatedModel(anim)));
    }
    @Test void publicSeamIsByteIdenticalToExistingSectionsAndPinnedPreviousUnion() throws Exception {
        var fixture = SyntheticVendor.content();
        byte[] union = Rac1CacheCodec.write(fixture, 7);
        // Independently generated with the cd63f78 codec compiled into a private JDK-only runner.
        assertEquals("f3a73e7e01d76e48acba7928a1086c1d071732e4bf0b85130aca6db4994c1a12",
                HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(union)));
        assertEquals(2772, union.length);
        assertArrayEquals(section(union, 1), Rac1CacheCodec.encodeStaticModel(fixture.staticAsset()));
        assertArrayEquals(section(union, 2), Rac1CacheCodec.encodeAnimatedModel(fixture.animatedAsset()));
        var stat = Rac1CacheCodec.decodeStaticModel(section(union, 1));
        var anim = Rac1CacheCodec.decodeAnimatedModel(section(union, 2));
        assertArrayEquals(fixture.staticAsset().mesh().positions(), stat.mesh().positions());
        assertArrayEquals(fixture.animatedAsset().animations()[0].rotations(), anim.animations()[0].rotations());
        assertArrayEquals(section(union, 2), Rac1CacheCodec.encodeAnimatedModel(anim));
    }
    private byte[] section(byte[] union, int kind) {
        var b = ByteBuffer.wrap(union).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < b.getInt(20); i++) {
            int at = b.getInt(24) + i * 32;
            if (b.getInt(at) == kind) return Arrays.copyOfRange(union, (int)b.getLong(at + 8),
                    (int)(b.getLong(at + 8) + b.getLong(at + 16)));
        }
        throw new AssertionError("Missing section");
    }
    @Test void malformedCountsReferencesNonfiniteAndTrailingPayloadsReject() throws Exception {
        var fixture = SyntheticVendor.content();
        byte[] stat = Rac1CacheCodec.encodeStaticModel(fixture.staticAsset());
        byte[] anim = Rac1CacheCodec.encodeAnimatedModel(fixture.animatedAsset());
        for (byte[] bad : new byte[][]{new byte[0], Arrays.copyOf(stat, stat.length - 1), Arrays.copyOf(stat, stat.length + 1)})
            assertThrows(java.io.IOException.class, () -> Rac1CacheCodec.decodeStaticModel(bad));
        for (byte[] bad : new byte[][]{new byte[0], Arrays.copyOf(anim, anim.length - 1), Arrays.copyOf(anim, anim.length + 1)})
            assertThrows(java.io.IOException.class, () -> Rac1CacheCodec.decodeAnimatedModel(bad));
        assertThrows(java.io.IOException.class, () -> Rac1CacheCodec.decodeAnimatedModel(new byte[Rac1CacheCodec.MAX_MODEL_BYTES + 1]));
        byte[] nan = stat.clone();
        var b = ByteBuffer.wrap(nan).order(ByteOrder.LITTLE_ENDIAN);
        b.putFloat(20 + b.getInt(4), Float.NaN);
        assertThrows(java.io.IOException.class, () -> Rac1CacheCodec.decodeStaticModel(nan));
        var malformed = SyntheticVendor.asset(12);
        malformed.mesh().indices()[0] = 999;
        var invalidIndex = malformed;
        assertThrows(java.io.IOException.class, () -> Rac1CacheCodec.encodeAnimatedModel(invalidIndex));
        malformed = SyntheticVendor.asset(12);
        malformed.skeleton().parents()[1] = 1;
        var invalidParent = malformed;
        assertThrows(java.io.IOException.class, () -> Rac1CacheCodec.encodeAnimatedModel(invalidParent));
        malformed = SyntheticVendor.asset(12); malformed.mesh().weights()[0] = -1;
        var invalidWeight = malformed;
        assertThrows(java.io.IOException.class, () -> Rac1CacheCodec.encodeAnimatedModel(invalidWeight));
    }
    @Test void untexturedStaticMaterialsRemainOriginalMinusOne() throws Exception {
        var source = SyntheticVendor.content().staticAsset();
        var mesh = new Rac1Mesh(source.mesh().positions(), source.mesh().normals(), source.mesh().uvs(),
                source.mesh().indices(), new int[]{-1});
        var decoded = Rac1CacheCodec.decodeStaticModel(Rac1CacheCodec.encodeStaticModel(
                new Rac1StaticAsset(1006, "untextured-fixture", mesh, new Rac1Texture[0])));
        assertEquals(-1, decoded.mesh().materials()[0]); assertEquals(0, decoded.textures().length);
    }
}
