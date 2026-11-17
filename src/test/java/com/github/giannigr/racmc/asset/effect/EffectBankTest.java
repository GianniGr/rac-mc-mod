package com.github.giannigr.racmc.asset.effect;

import com.github.giannigr.racmc.asset.AssetFormatException;
import com.github.giannigr.racmc.disc.Rac1CoreIndex;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class EffectBankTest {
    public static final String SHA1 = "0123456789012345678901234567890123456789";
    public static EffectBanks synthetic() throws Exception {
        byte[] bytes = new byte[1025];
        bytes[3] = (byte) 128;
        bytes[0] = 64;
        var entries = List.of(new EffectTextureBank.Entry(0, 0, 1024, 1, 1));
        return new EffectBanks(new EffectTextureBank(new EffectAssetRef(SHA1, 1,
                EffectAssetRef.Bank.PARTICLE, 0), entries, bytes),
                new EffectTextureBank(new EffectAssetRef(SHA1, 1, EffectAssetRef.Bank.FX, 0),
                        entries, bytes), new ParticleFrameLists(defs()));
    }
    static byte[] defs() {
        ByteBuffer bytes = ByteBuffer.allocate(25).order(ByteOrder.LITTLE_ENDIAN);
        bytes.putInt(2).putInt(1).putInt(24).putInt(1).putInt(0).putInt(24).put((byte) 0);
        return bytes.array();
    }
    @Test void zeroOffsetAndSharedOffsetsResolveToBlobNotEmpty() throws Exception {
        var definitions = new ParticleFrameLists(defs());
        assertArrayEquals(new byte[]{0}, definitions.storedSpan(0));
        assertArrayEquals(definitions.storedSpan(0), definitions.storedSpan(1));
    }
    @Test void definitionsAreOwnedAndDoNotInventFramesOrFps() throws Exception {
        byte[] source = defs();
        var definitions = new ParticleFrameLists(source);
        source[24] = 99;
        definitions.bytes()[24] = 88;
        assertArrayEquals(new byte[]{0}, definitions.storedSpan(0));
    }
    @Test void originalTableAlignmentPaddingIsPreserved() throws Exception {
        byte[] padded = new byte[33];
        ByteBuffer.wrap(padded).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(2).putInt(1).putInt(32).putInt(1).putInt(0).putInt(32);
        var definitions = new ParticleFrameLists(padded);
        assertArrayEquals(padded, definitions.bytes());
        assertArrayEquals(new byte[]{0}, definitions.storedSpan(0));
    }
    @Test void malformedOffsetsAndIndicesRejected() {
        byte[] bytes = defs();
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(20, Integer.MAX_VALUE);
        assertThrows(AssetFormatException.class, () -> new ParticleFrameLists(bytes));
        byte[] badIndex = defs();
        badIndex[24] = 1;
        assertThrows(AssetFormatException.class, () -> new ParticleFrameLists(badIndex));
    }
    @Test void definitionsRejectHugeAndTruncatedCounts() {
        byte[] bytes = defs();
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(0, Integer.MAX_VALUE);
        assertThrows(AssetFormatException.class, () -> new ParticleFrameLists(bytes));
        assertThrows(AssetFormatException.class, () -> new ParticleFrameLists(new byte[15]));
    }
    @Test void indexedDecodeAndNamespaceStayIndependent() throws Exception {
        EffectBanks banks = synthetic();
        assertEquals(0xff400000, banks.particles().decode(0).pixel(0, 0));
        assertNotEquals(banks.particles().source(), banks.fx().source());
        byte[] copy = banks.particles().bytes();
        copy[0] = 0;
        assertEquals(0xff400000, banks.particles().decode(0).pixel(0, 0));
    }
    @Test void bankRejectsOverflowsAndTruncation() {
        var ref = new EffectAssetRef(SHA1, 1, EffectAssetRef.Bank.PARTICLE, 0);
        assertThrows(AssetFormatException.class, () -> new EffectTextureBank(ref,
                List.of(new EffectTextureBank.Entry(Integer.MAX_VALUE, 0, 0, 1, 1)), new byte[1024]));
        assertThrows(AssetFormatException.class, () -> new EffectTextureBank(ref,
                List.of(new EffectTextureBank.Entry(0, 0, -1, 1, 1)), new byte[1024]));
        assertThrows(AssetFormatException.class, () -> new EffectTextureBank(ref,
                List.of(new EffectTextureBank.Entry(0, 0, 0, 65536, 65536)), new byte[1024]));
    }
    @Test void genericReaderUsesNextCoreBoundaryNotWholeCore() throws Exception {
        byte[] header = new byte[0x200], core = new byte[4096];
        ByteBuffer b = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(0x8c, core.length).putInt(0x50, 1).putInt(0x54, 0xc0)
                .putInt(0x58, 1).putInt(0x5c, 0xd0).putInt(0x64, 1).putInt(0x68, 1026)
                .putInt(0x6c, 0xe0).putInt(0xc0, 0).putInt(0xc8, 1024).putInt(0xcc, 1)
                .putInt(0xd0, 0).putInt(0xd4, 1024).putInt(0xd8, 1).putInt(0xdc, 1);
        System.arraycopy(defs(), 0, header, 0xe0, 25);
        var banks = Rac1EffectBankImporter.read(Rac1CoreIndex.parse(header), core, SHA1, 1);
        assertEquals(1025, banks.particles().bytes().length);
        b.putInt(0xc8, 1025); // Would fit the whole core, but crosses into the next bank.
        assertThrows(AssetFormatException.class,
                () -> Rac1EffectBankImporter.read(Rac1CoreIndex.parse(header), core, SHA1, 1));
    }
    @Test void genericReaderRejectsNegativeCountAndOverflowingTable() throws Exception {
        byte[] header = new byte[0x200];
        ByteBuffer b = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(0x8c, 4096).putInt(0x6c, Integer.MAX_VALUE);
        var index = Rac1CoreIndex.parse(header);
        assertThrows(AssetFormatException.class,
                () -> Rac1EffectBankImporter.read(index, new byte[4096], SHA1, 1));
    }
}
