package com.github.giannigr.racmc.client.shop;

import java.util.Objects;

/** Inspected arithmetic; provisional playback explicitly supplies Java conversion, not verified PS2 stubs. */
public final class Rac1ShopVoiceHelpers {
    @FunctionalInterface public interface IntegerConversion { float convert(int signedWord); }
    @FunctionalInterface public interface FloatConversion { int convert(float value); }
    public record CursorFields(int index50,int index51,int id52,int id53,float fraction54,
                               short header68Time16,short header6cTime16) {
        public CursorFields {
            for (int value:new int[]{index50,index51,id52,id53})
                if (value<0 || value>255) throw new IllegalArgumentException("Source byte field required");
        }
    }
    private Rac1ShopVoiceHelpers() {}
    public static float cursor(CursorFields fields,IntegerConversion conversion) {
        Objects.requireNonNull(fields); Objects.requireNonNull(conversion);
        short selected=fields.id52()==255 ? fields.header6cTime16():fields.header68Time16();
        if (fields.fraction54()==0)
            return conversion.convert(selected)*.0625f;
        if (fields.id52()==fields.id53() && fields.index51()<fields.index50())
            return conversion.convert(selected)*.0625f+fields.fraction54();
        float left=conversion.convert(selected)*(1-fields.fraction54());
        float right=conversion.convert(fields.header6cTime16())*fields.fraction54();
        return (left+right)*.0625f;
    }
    /** Only selector4 is used/admitted by the presenter predicate. No guessed cast/rounding default. */
    public static float selector4(float value,int selector,FloatConversion conversion) {
        Objects.requireNonNull(conversion);
        if (selector!=4) throw new IllegalArgumentException("Only verified presenter selector4 is admitted");
        float factor=10000;
        float bias=1/(factor+factor);
        int converted=conversion.convert((value+bias)*factor);
        return (float)converted/factor;
    }
}
