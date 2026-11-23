package com.github.giannigr.racmc.asset.rescue;

import static org.junit.jupiter.api.Assertions.*;
import com.github.giannigr.racmc.asset.AssetFormatException;
import com.github.giannigr.racmc.iso.Rac1DiscRevision;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;

class Rac1RescueScenePoseTest {
    private static final int ACTOR = 0x50;
    private static final int SEQUENCE = ACTOR + 0x10;
    private static final int FRAME = SEQUENCE + 0x24;
    private static final int TRACK = FRAME + 16 + 288;
    private static final Rac1DiscRevision NTSC = Rac1DiscRevision.NTSC_U_V1_00;
    private static final Rac1DiscRevision PAL = Rac1DiscRevision.PAL_V2_00;

    @Test
    void firstChunkUsesOnlyKnownRegionBoundsAndRejectsBadTable() throws Exception {
        for (var revision : new Rac1DiscRevision[]{NTSC, PAL}) {
            int at = 0x184 + 0x250 + (revision == NTSC ? 0x18 : 0x134);
            int first = revision == NTSC ? 1618767 : 1618986;
            int sentinel = revision == NTSC ? 1618875 : 1619079;
            var header = ByteBuffer.allocate(0x2800).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < 6; i++) header.putInt(at + i * 4, first + i * 16);
            header.putInt(at + 24, sentinel);
            assertEquals(new Rac1RescueScenePose.Span(first, 16),
                    Rac1RescueScenePose.firstChunk(header.array(), revision, 4_215_000_000L));
            header.putInt(at + 4, first + 25);
            assertThrows(AssetFormatException.class, () ->
                    Rac1RescueScenePose.firstChunk(header.array(), revision, 4_215_000_000L));
            header.putInt(at + 4, first + 16);
            assertThrows(AssetFormatException.class, () ->
                    Rac1RescueScenePose.firstChunk(header.array(), revision, sentinel * 2048L - 1));
        }
    }

    @Test
    void staticFrameUsesOnlyFrameZeroAndConvertsSparseTranslationAndScale() throws Exception {
        byte[] decoded = fixture();
        var pose = Rac1RescueScenePose.frameZero(decoded, NTSC, .5f);
        assertEquals(1, pose.keyCount());
        assertFalse(pose.loop());
        assertArrayEquals(new float[]{0}, pose.times());
        assertArrayEquals(new int[]{2}, pose.translationJoints());
        assertArrayEquals(new float[]{.5f, 1.5f, -1}, pose.translationValues(), 1e-6f);
        assertArrayEquals(new int[]{1}, pose.scaleJoints());
        assertArrayEquals(new float[]{1, 2, 3}, pose.scaleValues(), 1e-6f);
        assertArrayEquals(new boolean[]{true}, pose.scaleInherited());
        assertEquals(34 * 4, pose.rotations().length);
    }

    @Test
    void failsClosedOnWrongActorRigTruncationOrEscapingFrame() {
        for (int bad : new int[]{-1, Integer.MAX_VALUE, 0x1c, TRACK - SEQUENCE}) {
            byte[] data = fixture();
            wrap(data).putInt(SEQUENCE + 0x1c, bad);
            assertThrows(AssetFormatException.class, () -> Rac1RescueScenePose.frameZero(data, NTSC, 1));
        }
        byte[] wrongClass = fixture();
        wrap(wrongClass).putInt(ACTOR, 601);
        assertThrows(AssetFormatException.class, () -> Rac1RescueScenePose.frameZero(wrongClass, NTSC, 1));
        byte[] wrongRig = fixture();
        wrap(wrongRig).putShort(FRAME + 8, (short) (33 * 8));
        assertThrows(AssetFormatException.class, () -> Rac1RescueScenePose.frameZero(wrongRig, NTSC, 1));
        byte[] zeroQuaternion = fixture();
        wrap(zeroQuaternion).putShort(FRAME + 22, (short) 0);
        assertThrows(AssetFormatException.class, () -> Rac1RescueScenePose.frameZero(zeroQuaternion, NTSC, 1));
        assertThrows(AssetFormatException.class, () -> Rac1RescueScenePose.frameZero(new byte[10], NTSC, 1));
    }

    private static byte[] fixture() {
        var data = wrap(new byte[TRACK + 32]);
        data.putShort(0, (short) 538);
        data.putShort(0x0c, (short) 2);
        data.putInt(0x14, 0x30);
        data.putInt(0x18, ACTOR);
        data.putInt(ACTOR, 1365);
        data.putInt(ACTOR + 0x0c, TRACK);
        data.put(SEQUENCE + 0x10, (byte) 2);
        data.put(SEQUENCE + 0x12, (byte) 255);
        data.putInt(SEQUENCE + 0x1c, FRAME - SEQUENCE);
        data.putInt(SEQUENCE + 0x20, Integer.MAX_VALUE); // frame one must never be decoded
        data.putShort(FRAME + 4, (short) 0xff01);
        data.putShort(FRAME + 6, (short) 18);
        data.putShort(FRAME + 8, (short) 272);
        data.putShort(FRAME + 10, (short) 1);
        data.putShort(FRAME + 12, (short) 280);
        data.putShort(FRAME + 14, (short) 1);
        for (int i = 0; i < 34; i++) data.putShort(FRAME + 16 + i * 8 + 6, (short) 32767);
        int scale = FRAME + 16 + 272;
        data.putShort(scale, (short) 4096).putShort(scale + 2, (short) 8192)
                .putShort(scale + 4, (short) 12288).put(scale + 6, (byte) 1).put(scale + 7, (byte) 128);
        int translation = scale + 8;
        data.putShort(translation, (short) 1024).putShort(translation + 2, (short) 2048)
                .putShort(translation + 4, (short) 3072).put(translation + 6, (byte) 2);
        data.putFloat(TRACK, 172.5f); // ignored source world position
        return data.array();
    }

    private static ByteBuffer wrap(byte[] data) {
        return ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
    }
}
