package com.github.giannigr.racmc.asset.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.giannigr.racmc.asset.AssetFormatException;
import com.github.giannigr.racmc.iso.Rac1DiscImage;
import com.github.giannigr.racmc.iso.Rac1DiscRevision;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GreenShipCanopyTest {
    private static final List<Read> APPROVED = List.of(new Read(3_876_693_432L, 16),
            new Read(3_877_057_800L, 624), new Read(3_877_058_424L, 384),
            new Read(3_877_058_808L, 624));

    @Test
    void exactNtscHeaderAndThreeSlicesAreTheOnlyReadPlan() throws Exception {
        var reads = new ArrayList<Read>();
        var geometry = GreenShipCanopy.readVerified(verified(false, reads, null)).orElseThrow();
        assertEquals(APPROVED, reads);
        assertEquals(39, geometry.points().size());
        assertEquals(39, geometry.normals().size());
        assertEquals(24, geometry.quads().size());
        assertEquals(0x30807060, geometry.tintGs());
        assertEquals(new GreenShipCanopy.Quad(0, 1, 2, 38), geometry.quads().getLast());
    }

    @Test
    void palDoesNotReadAnythingAndCannotAcquireNtscGeometry() throws Exception {
        var reads = new ArrayList<Read>();
        assertTrue(GreenShipCanopy.readVerified(verified(true, reads, null)).isEmpty());
        assertTrue(reads.isEmpty());
    }

    @Test
    void unexpectedSectionNonfiniteVectorsAndBadIndicesStopBeforeTheNextSlice() {
        for (int bad = 0; bad < APPROVED.size(); bad++) {
            var reads = new ArrayList<Read>();
            var image = verified(false, reads, APPROVED.get(bad));
            assertThrows(AssetFormatException.class, () -> GreenShipCanopy.readVerified(image));
            assertEquals(APPROVED.subList(0, bad + 1), reads);
        }
    }

    private record Read(long offset, int length) {}

    private static Rac1DiscImage verified(boolean pal, List<Read> reads, Read corrupt) {
        return (Rac1DiscImage) Proxy.newProxyInstance(Rac1DiscImage.class.getClassLoader(),
                new Class<?>[] {Rac1DiscImage.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "revision" -> pal ? Rac1DiscRevision.PAL_V2_00 : Rac1DiscRevision.NTSC_U_V1_00;
                    case "imageSizeBytes" -> 4_214_095_872L;
                    case "readBytes" -> {
                        var read = new Read((long) args[0], (int) args[1]);
                        assertEquals(APPROVED.get(reads.size()), read);
                        reads.add(read);
                        var bytes = ByteBuffer.allocate(read.length()).order(ByteOrder.LITTLE_ENDIAN);
                        if (read.equals(APPROVED.getFirst())) {
                            bytes.putInt(0x166480).putInt(677_456).putInt(read.equals(corrupt) ? 8 : 1)
                                    .putInt(0x259c40);
                        } else if (read.equals(APPROVED.get(2))) {
                            for (int at = 0; at < read.length(); at += 16) {
                                bytes.putShort(at + 4, (short) 1).putShort(at + 8, (short) 2)
                                        .putShort(at + 12, (short) (read.equals(corrupt) ? 39 : 38));
                            }
                        } else {
                            for (int at = 0; at < read.length(); at += 16) {
                                bytes.putFloat(at, read.equals(corrupt) ? Float.NaN : 0f)
                                        .putFloat(at + 12, 1f);
                            }
                        }
                        yield bytes.array();
                    }
                    default -> throw new AssertionError(method.getName());
                });
    }
}
