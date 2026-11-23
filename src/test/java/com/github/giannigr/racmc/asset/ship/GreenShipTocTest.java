package com.github.giannigr.racmc.asset.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.giannigr.racmc.disc.DiscFormatException;
import com.github.giannigr.racmc.iso.Rac1DiscImage;
import com.github.giannigr.racmc.iso.Rac1DiscRevision;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GreenShipTocTest {
    private static final long TOC = 1500L * 2048;

    @Test
    void bothRevisionsReadOnlyTheirThreeRecords() throws Exception {
        List<Long> calls = new ArrayList<>();
        var ntsc = GreenShipToc.read(image(Rac1DiscRevision.NTSC_U_V1_00, calls, 3000));
        assertEquals(new GreenShipToc.Extent(3000L * 2048, 4096), ntsc.hull());
        assertEquals(List.of(TOC + 0x12d0, TOC + 0x12f0, TOC + 0x1310), calls);
        calls.clear();
        GreenShipToc.read(image(Rac1DiscRevision.PAL_V2_00, calls, 3000));
        assertEquals(List.of(TOC + 0x12d0, TOC + 0x1340, TOC + 0x1360), calls);
    }

    @Test
    void hullLoaderLookupDoesNotReadSceneRecordsAndRejectsBadExtents() throws Exception {
        List<Long> calls = new ArrayList<>();
        GreenShipToc.readHull(image(Rac1DiscRevision.PAL_V2_00, calls, 3000));
        assertEquals(List.of(TOC + 0x12d0), calls);
        for (int sector : new int[] {0, -1, 4000}) {
            assertThrows(DiscFormatException.class,
                    () -> GreenShipToc.readHull(image(Rac1DiscRevision.PAL_V2_00, calls, sector)));
        }
    }

    private static Rac1DiscImage image(Rac1DiscRevision revision, List<Long> calls, int sector) {
        return (Rac1DiscImage) Proxy.newProxyInstance(Rac1DiscImage.class.getClassLoader(),
                new Class<?>[] {Rac1DiscImage.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "revision" -> revision;
                    case "imageSizeBytes" -> 4000L * 2048;
                    case "readBytes" -> {
                        calls.add((long) args[0]);
                        assertEquals(8, (int) args[1]);
                        yield ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
                                .putInt(sector).putInt(2).array();
                    }
                    default -> throw new AssertionError(method.getName());
                });
    }
}
