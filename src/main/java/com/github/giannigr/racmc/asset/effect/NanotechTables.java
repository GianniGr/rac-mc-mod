package com.github.giannigr.racmc.asset.effect;

import com.github.giannigr.racmc.asset.AssetFormatException;
import java.nio.*;

/** Original overlay geometry, not a fabricated sphere. Coordinates remain source XYZ-up. */
public record NanotechTables(float[] hemisphere, float[] st, int[] colours, float[] halo,
                             int[] haloQuads, float[] glass, int[] glassQuads, float[] offset) {
    public NanotechTables {
        hemisphere = hemisphere.clone(); st = st.clone(); colours = colours.clone();
        halo = halo.clone(); haloQuads = haloQuads.clone(); glass = glass.clone();
        glassQuads = glassQuads.clone(); offset = offset.clone();
        if (hemisphere.length != 290 * 3 || st.length != 290 * 2 || colours.length != 290
                || halo.length != 96 * 3 || haloQuads.length != 32 * 4
                || glass.length != 16 * 3 || glassQuads.length != 4 * 4 || offset.length != 3)
            throw new IllegalArgumentException("Invalid nanotech table counts");
        for (float[] values : new float[][]{hemisphere, st, halo, glass, offset})
            for (float value : values)
                if (!Float.isFinite(value) || Math.abs(value) > 16)
                    throw new IllegalArgumentException("Invalid nanotech geometry");
        for (int id : haloQuads) if (id < 0 || id >= 96) throw new IllegalArgumentException("Halo index");
        for (int id : glassQuads) if (id < 0 || id >= 16) throw new IllegalArgumentException("Glass index");
    }
    public static float[] vectors(byte[] bytes, int count) {
        ByteBuffer input = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        float[] values = new float[count * 3];
        for (int id = 0; id < count; id++)
            for (int axis = 0; axis < 3; axis++) values[id * 3 + axis] = input.getFloat(id * 16 + axis * 4);
        return values;
    }
    public static float[] floats(byte[] bytes) {
        var input = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer();
        float[] values = new float[input.remaining()]; input.get(values); return values;
    }
    public static int[] words(byte[] bytes, boolean shorts) {
        var input = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int[] values = new int[bytes.length / (shorts ? 2 : 4)];
        for (int i = 0; i < values.length; i++) values[i] = shorts ? input.getShort() & 65535 : input.getInt();
        return values;
    }
}
