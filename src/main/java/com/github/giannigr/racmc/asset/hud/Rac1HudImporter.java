package com.github.giannigr.racmc.asset.hud;

import com.github.giannigr.racmc.asset.AssetFormatException;
import com.github.giannigr.racmc.asset.Rac1Texture;
import com.github.giannigr.racmc.disc.*;
import com.github.giannigr.racmc.iso.*;
import com.github.giannigr.racmc.wad.*;
import java.io.IOException;
import java.util.*;
import static com.github.giannigr.racmc.asset.hud.Rac1HudBankDecoder.*;

/** Bounded original HUD import from an already validated local disc. */
public final class Rac1HudImporter {
    private Rac1HudImporter() {}

    public static Rac1HudAssets read(Rac1DiscImage image, Rac1LevelAssets level)
            throws IOException, AssetFormatException, DiscFormatException, WadFormatException {
        if (level.level() != 1) throw new AssetFormatException("HUD font source must be Novalis");
        byte[] toc = Rac1DiscTables.read(image).bytes();
        byte[] header = sectorRange(image, toc, 0x14F8, 64 * 1024);
        byte[][] banks = new byte[5][];
        for (int bank = 0; bank < banks.length; bank++) {
            int expected = word(header, 0x54 + bank * 4);
            if (expected < 0 || expected > 16 * 1024 * 1024)
                throw new AssetFormatException("Oversized HUD bank");
            banks[bank] = WadDecoder.decode(sectorRange(image, toc, 0x1500 + bank * 8,
                    4 * 1024 * 1024), expected);
        }
        var decoder = new Rac1HudBankDecoder(header, banks);
        Map<String, List<Rac1Texture>> sprites = Map.of(
                "bolt", decoder.decode(Rac1BoltHudDefinition.ICON),
                "bolt_panel", decoder.decode(Rac1BoltHudDefinition.PANEL));

        boolean pal = image.revision() == Rac1DiscRevision.PAL_V2_00;
        byte[] boot = image.readFile(pal ? "SCES_509.16" : "SCUS_971.99", 8 * 1024 * 1024);
        byte[] metrics = elfRange(boot, pal ? 0x1DF3D0 : 0x1DF050, 0x3A0);
        byte[] index = level.coreIndex().bytes(), core = level.coreData();
        // The ordinary font is original effect texture slot 1. Not the global debug font.
        int count = word(index, 0x58), table = word(index, 0x5C), base = word(index, 0x68);
        if (count < 2 || count > 1024) throw new AssetFormatException("Missing regular font effect texture");
        range(index, table, count * 16);
        int palette = word(index, table + 16), pixels = word(index, table + 20);
        if (base < 0 || palette < 0 || pixels < 0 || base > core.length
                || palette > core.length - base || pixels > core.length - base)
            throw new AssetFormatException("Font offsets outside core");
        Rac1Texture atlas = indexed(core, base + pixels, word(index, table + 24),
                word(index, table + 28), core, base + palette);
        // GS text modulation uses neutral RGB 128, unlike Minecraft's neutral 255.
        byte[] normalized = atlas.pixels().clone();
        for (int at = 0; at < normalized.length; at++) {
            if (at % 4 != 0) normalized[at] = (byte) Math.min(255, (normalized[at] & 255) * 2);
        }
        var font = new Rac1HudFont(new Rac1Texture(atlas.width(), atlas.height(), normalized), metrics);

        long levelSector = Rac1DiscTables.of(toc).levelSector(1);
        var levelHeader = Rac1LevelHeader.read(image, levelSector);
        long dataBase = levelHeader.dataSector() * 2048L;
        byte[] container = image.readBytes(dataBase, 0x58);
        int codeOffset = word(container, 0), codeSize = word(container, 4);
        if (codeOffset < 0 || codeSize <= 0 || codeSize > 4 * 1024 * 1024)
            throw new AssetFormatException("Invalid HUD overlay range");
        byte[] overlay = image.readBytes(dataBase + codeOffset, codeSize);
        if (WadDecoder.isWad(overlay)) overlay = WadDecoder.decode(overlay, -1);
        byte[] layout = overlayRange(overlay, pal ? 0x15F90C : 0x15F82C, 0x38);
        byte[] steps = overlayRange(overlay, pal ? 0x15F904 : 0x15F824, 8);
        Map<String, Integer> properties = new LinkedHashMap<>();
        for (int i = 0; i < Rac1BoltHudDefinition.PROPERTIES.length; i++)
            properties.put(Rac1BoltHudDefinition.PROPERTIES[i], word(layout, i * 4));
        // Require the verified original separator strings, not an inferred locale glyph.
        if (layout[0x2C] != '\'' || layout[0x34] != '.')
            throw new AssetFormatException("Unexpected bolt separator definitions");
        properties.put("top", pal ? 10 : 18);
        properties.put("openSteps", word(steps, 0));
        properties.put("textSteps", word(steps, 4));
        properties.put("uiHz", pal ? 50 : 60);
        var result = new Rac1HudAssets(sprites, Map.of(Rac1BoltHudDefinition.FONT, font), properties);
        if (!Rac1BoltHudDefinition.complete(result)) throw new AssetFormatException("Incomplete original bolt HUD");
        return result;
    }

    private static byte[] sectorRange(Rac1DiscImage image, byte[] toc, int at, int limit)
            throws IOException, AssetFormatException {
        int sector = word(toc, at), count = word(toc, at + 4);
        if (sector <= 0 || count <= 0 || count > limit / 2048)
            throw new AssetFormatException("Invalid HUD sector range");
        return image.readSectors(sector, count);
    }

    /** Bounds-checked virtual-address lookup in a little-endian ELF32 PT_LOAD segment. */
    public static byte[] elfRange(byte[] elf, int address, int length) throws AssetFormatException {
        range(elf, 0, 52);
        if (word(elf, 0) != 0x464C457F || elf[4] != 1 || elf[5] != 1)
            throw new AssetFormatException("Unsupported font ELF");
        int offset = word(elf, 28), size = (elf[42] & 255) | ((elf[43] & 255) << 8);
        int count = (elf[44] & 255) | ((elf[45] & 255) << 8);
        if (size < 32 || count > 128) throw new AssetFormatException("Invalid ELF program table");
        range(elf, offset, size * count);
        for (int i = 0; i < count; i++) {
            int at = offset + size * i;
            if (word(elf, at) != 1) continue;
            int file = word(elf, at + 4), start = word(elf, at + 8), bytes = word(elf, at + 16);
            range(elf, file, bytes);
            long relative = (long) address - start;
            if (relative >= 0 && length >= 0 && relative <= (long) bytes - length)
                return Arrays.copyOfRange(elf, file + (int) relative, file + (int) relative + length);
        }
        throw new AssetFormatException("Font virtual range not file-backed");
    }

    public static byte[] overlayRange(byte[] overlay, int address, int length) throws AssetFormatException {
        for (int at = 0; at <= overlay.length - 16;) {
            int start = word(overlay, at), bytes = word(overlay, at + 4);
            range(overlay, at + 16, bytes);
            long relative = (long) address - start;
            if (relative >= 0 && length >= 0 && relative <= (long) bytes - length)
                return Arrays.copyOfRange(overlay, at + 16 + (int) relative,
                        at + 16 + (int) relative + length);
            at += 16 + bytes;
        }
        throw new AssetFormatException("HUD constants not in overlay segments");
    }
}
