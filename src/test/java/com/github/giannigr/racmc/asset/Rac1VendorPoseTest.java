package com.github.giannigr.racmc.asset;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class Rac1VendorPoseTest {
    @Test
    void closedIsTheImportedSequenceZeroOnly() {
        Rac1VendorAsset vendor = SyntheticVendor.vendor();
        assertArrayEquals(vendor.body().mesh().positions(),
                Rac1VendorPose.closed(vendor).positions(), 1e-6f);
        assertArrayEquals(Rac1VendorPose.closed(vendor).positions(),
                Rac1VendorPose.closed(vendor).positions(), 0);
    }

    @Test
    void independentLogoJointsCounterRotateWithoutMutatingSource() {
        Rac1VendorAsset vendor = SyntheticVendor.vendor();
        float[] source = vendor.hologram().mesh().positions().clone();
        float seconds = (float) (Math.PI / 2 / .6);
        float[] positions = Rac1VendorPose.hologram(vendor, seconds).positions();
        // Disc-quaternion convention of the shared evaluator: opposite turns on joints 1/2.
        assertEquals(2, positions[0], 1e-5);
        assertEquals(-2, positions[2], 1e-5);
        assertEquals(2, positions[3], 1e-5);
        assertEquals(-2, positions[5], 1e-5);
        assertArrayEquals(source, vendor.hologram().mesh().positions(), 0);
        assertArrayEquals(source, Rac1VendorPose.hologram(vendor, 0).positions(), 1e-6f);
    }

    @Test
    void rejectsWrongPairAndMissingClosedPose() {
        assertThrows(IllegalArgumentException.class, () -> new Rac1VendorAsset(
                SyntheticVendor.asset(0), SyntheticVendor.asset(1143)));
        Rac1AnimatedAsset body = SyntheticVendor.asset(11);
        Rac1AnimatedAsset missing = new Rac1AnimatedAsset(11, body.name(), body.mesh(),
                body.skeleton(), new Rac1Animation[0], body.textures());
        assertThrows(IllegalArgumentException.class,
                () -> new Rac1VendorAsset(missing, SyntheticVendor.asset(1143)));
    }
}
