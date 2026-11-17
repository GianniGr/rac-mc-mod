package com.github.giannigr.racmc.asset.hud;

import com.github.giannigr.racmc.asset.AssetFormatException;
import com.github.giannigr.racmc.asset.Rac1Texture;
import com.github.giannigr.racmc.asset.Rac1TextureReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/** Original HUD header/frame references and bank-relative indexed pages.
 * Only format facts were consulted in upstream decompilation; no upstream code is used. */
public final class Rac1HudBankDecoder {
    private final byte[] header;
    private final byte[][] banks;
    private final int definitions, frames, palettes, images, frameCount;

    public Rac1HudBankDecoder(byte[] header, byte[][] banks) throws AssetFormatException {
        this.header = header;
        this.banks = banks;
        range(header, 0, 0xB4);
        if (banks.length != 5) throw bad("Expected five HUD banks");
        definitions = word(header, 4);
        frames = word(header, 8);
        palettes = word(header, 12);
        images = word(header, 16);
        int definitionCount = unsignedShort(header, 0);
        frameCount = unsignedShort(header, 2);
        if (definitionCount < 1 || definitionCount > 4096 || frameCount < 1
                || definitions != 0xB4 || frames != definitions + definitionCount * 8
                || palettes != frames + frameCount * 4 || images < palettes
                || (images - palettes) % 8 != 0)
            throw bad("Invalid HUD table layout");
        range(header, definitions, definitionCount * 8);
        range(header, frames, frameCount * 4);
        int previousPalette = 0, previousImage = 0;
        for (int bank = 0; bank < 5; bank++) {
            int paletteEnd = word(header, 0x14 + bank * 4);
            int imageEnd = word(header, 0x34 + bank * 4);
            if (paletteEnd < previousPalette || imageEnd < previousImage
                    || paletteEnd > 4096 || imageEnd > 4096
                    || word(header, 0x54 + bank * 4) != banks[bank].length)
                throw bad("Invalid HUD bank extent");
            previousPalette = paletteEnd;
            previousImage = imageEnd;
        }
        if (images != palettes + previousPalette * 8) throw bad("HUD palette count mismatch");
        range(header, images, previousImage * 8);
        if (unsignedShort(header, frames - 8) != 0xFFFF) throw bad("Missing HUD definition terminator");
    }

    public List<Rac1Texture> decode(int iconId) throws AssetFormatException {
        for (int at = definitions; at < frames; at += 8) {
            int id = unsignedShort(header, at);
            if (id == 0xFFFF) break;
            if (id != iconId) continue;
            int count = unsignedShort(header, at + 2), first = unsignedShort(header, at + 4);
            if (count < 1 || count > 512 || first > frameCount - count)
                throw bad("HUD frame range outside reference table");
            List<Rac1Texture> result = new ArrayList<>();
            for (int index = first; index < first + count; index++) {
                int palette = unsignedShort(header, frames + index * 4);
                int image = unsignedShort(header, frames + index * 4 + 2);
                Page clut = page(palettes, 0x14, palette);
                Page pixels = page(images, 0x34, image);
                result.add(indexed(pixels.data(), pixels.offset(), pixels.width(), pixels.height(),
                        clut.data(), clut.offset()));
            }
            return List.copyOf(result);
        }
        throw bad("Original HUD icon not found: " + Integer.toHexString(iconId));
    }

    private Page page(int table, int ends, int index) throws AssetFormatException {
        int bank = 0;
        while (bank < 5 && index >= word(header, ends + bank * 4)) bank++;
        if (bank == 5) throw bad("HUD page index outside bank table");
        int at = table + index * 8;
        range(header, at, 8);
        int offset = word(header, at) & 0x7FFFFFFF; // disk relocation flag, not an address
        int widthLog = header[at + 6] & 255, heightLog = header[at + 7] & 255;
        if (widthLog > 10 || heightLog > 10) throw bad("Oversized HUD page");
        return new Page(banks[bank], offset, 1 << widthLog, 1 << heightLog);
    }

    /** GS CSM1 CLUT index order, linear indexed texels, PS2 alpha 0..128 -> 0..255. */
    public static Rac1Texture indexed(byte[] pixels, int offset, int width, int height,
                                     byte[] palette, int paletteOffset) throws AssetFormatException {
        if (width < 1 || height < 1 || width > 1024 || height > 1024)
            throw bad("Invalid indexed HUD dimensions");
        range(pixels, offset, width * height);
        range(palette, paletteOffset, 1024);
        byte[] argb = new byte[width * height * 4];
        for (int pixel = 0; pixel < width * height; pixel++) {
            int index = pixels[offset + pixel] & 255;
            int clutIndex = (index & ~24) | ((index & 8) << 1) | ((index & 16) >> 1);
            int source = paletteOffset + clutIndex * 4, dest = pixel * 4;
            argb[dest] = (byte) Rac1TextureReader.alpha(palette[source + 3] & 255);
            System.arraycopy(palette, source, argb, dest + 1, 3);
        }
        return new Rac1Texture(width, height, argb);
    }

    public static int word(byte[] bytes, int offset) throws AssetFormatException {
        range(bytes, offset, 4);
        return ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }
    private static int unsignedShort(byte[] bytes, int offset) throws AssetFormatException {
        range(bytes, offset, 2);
        return (bytes[offset] & 255) | ((bytes[offset + 1] & 255) << 8);
    }
    public static void range(byte[] bytes, int offset, int length) throws AssetFormatException {
        if (offset < 0 || length < 0 || offset > bytes.length - length) throw bad("HUD byte range out of bounds");
    }
    private static AssetFormatException bad(String message) { return new AssetFormatException(message); }
    private record Page(byte[] data, int offset, int width, int height) {}
}
