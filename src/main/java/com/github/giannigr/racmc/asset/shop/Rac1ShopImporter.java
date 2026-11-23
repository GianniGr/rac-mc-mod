package com.github.giannigr.racmc.asset.shop;

import com.github.giannigr.racmc.asset.*;
import com.github.giannigr.racmc.asset.hud.*;
import com.github.giannigr.racmc.disc.*;
import com.github.giannigr.racmc.iso.*;
import com.github.giannigr.racmc.wad.*;
import java.io.IOException;
import java.util.*;
import static com.github.giannigr.racmc.asset.hud.Rac1HudBankDecoder.*;

/** Bounded local 2D importer. No catalog, arrow adaptation or voice mapping is inferred. */
public final class Rac1ShopImporter {
    private Rac1ShopImporter() {}

    public static Rac1ShopAssets read(Rac1DiscImage image, Rac1LevelAssets level,
                                     String sha1, int language)
            throws IOException, DiscFormatException, WadFormatException, AssetFormatException {
        if (level.level() != 1) throw new AssetFormatException("Shop source must be Novalis");
        byte[] toc = Rac1DiscTables.read(image).bytes();
        byte[] header = sectors(image, toc, 0x14f8, 64 * 1024);
        byte[][] banks = new byte[5][];
        for (int i = 0; i < banks.length; i++) {
            int size = word(header, 0x54 + i * 4);
            if (size < 0 || size > 16 * 1024 * 1024) throw new AssetFormatException("Invalid shop HUD bank size");
            banks[i] = WadDecoder.decode(sectors(image, toc, 0x1500 + i * 8, 4 * 1024 * 1024), size);
        }
        var decoder = new Rac1HudBankDecoder(header, banks);
        Map<String, List<Rac1Texture>> sprites = new TreeMap<>();
        sprites.put("blaster_icons", decoder.decode(60015));
        sprites.put("led_atlas", decoder.decode(59701));
        int[] fx = {0x18, 0x19, 0x1a, 0x1b, 0x1c};
        String[] names = {"cone", "glass", "noise", "scan_plane", "scan_bar"};
        for (int i = 0; i < fx.length; i++) sprites.put(names[i], List.of(effect(level, fx[i], false)));

        long sector = Rac1DiscTables.of(toc).levelSector(1);
        var levelHeader = Rac1LevelHeader.read(image, sector);
        long base = levelHeader.dataSector() * 2048L;
        byte[] container = image.readBytes(base, 0x58);
        int offset = word(container, 0), size = word(container, 4);
        if (offset < 0 || size <= 0 || size > 4 * 1024 * 1024)
            throw new AssetFormatException("Invalid shop overlay range");
        byte[] overlay = image.readBytes(base + offset, size);
        if (WadDecoder.isWad(overlay)) overlay = WadDecoder.decode(overlay, -1);
        boolean pal = image.revision() == Rac1DiscRevision.PAL_V2_00;
        Map<String, Rac1HudFont> fonts = Map.of(
                "regular", new Rac1HudFont(effect(level, 1, true),
                        Rac1HudImporter.overlayRange(overlay, pal ? 0x1c38d0 : 0x1c35d0, 928)),
                "small", new Rac1HudFont(effect(level, 2, true),
                        Rac1HudImporter.overlayRange(overlay, pal ? 0x1c3c70 : 0x1c3970, 928)));
        byte[] text = sectors(image, toc, 0x1528, 8 * 1024 * 1024);
        if (WadDecoder.isWad(text)) text = WadDecoder.decode(text, -1);
        var tables = Rac1ShopSceneTables.read(overlay, pal, level);
        Map<Integer, MobyClassReader.ClassMaterialMetadata> materials = new TreeMap<>();
        for (int id : Rac1ShopAssets.MATERIAL_CLASSES) {
            var entry = level.coreIndex().mobyClass(id);
            if (entry == null || entry.offset() <= 0) throw new AssetFormatException("Missing shop material class "+id);
            var reader = MobyClassReader.of(level.classBlob(entry.offset()));
            var metadata = reader.readMaterialMetadata();
            long normalVertices = 0;
            for (var packet : metadata.packets()) if (!packet.metal()) normalVertices += packet.vertexCount();
            if (normalVertices != reader.readMesh().vertexCount())
                throw new AssetFormatException("Shop source packet/model vertex mismatch for class "+id);
            materials.put(id,metadata);
        }
        var result = new Rac1ShopAssets(sha1, language, new Rac1HudAssets(sprites, fonts, Map.of()),
                Rac1ShopText.read(text, language, tables.requiredTextIds()), tables,
                Rac1ShopModelImporter.read(image, level, sha1), materials);
        if (!result.missing2d().isEmpty())
            throw new AssetFormatException("Incomplete original shop 2D resources: " + result.missing2d());
        if (!result.missingMaterialMetadata().isEmpty())
            throw new AssetFormatException("Incomplete original shop material metadata: " + result.missingMaterialMetadata());
        return result;
    }

    private static Rac1Texture effect(Rac1LevelAssets level, int id, boolean font)
            throws AssetFormatException {
        byte[] index = level.coreIndex().bytes(), core = level.coreData();
        int count = word(index, 0x58), table = word(index, 0x5c), base = word(index, 0x68);
        if (count < 1 || count > 256 || id >= count) throw new AssetFormatException("Missing shop FX " + id);
        range(index, table, count * 16);
        int at = table + id * 16, palette = word(index, at), pixels = word(index, at + 4);
        if (base < 0 || palette < 0 || pixels < 0 || base > core.length
                || palette > core.length - base || pixels > core.length - base)
            throw new AssetFormatException("Shop FX offsets outside core");
        Rac1Texture texture = indexed(core, base + pixels, word(index, at + 8), word(index, at + 12),
                core, base + palette);
        if (!font) return texture;
        byte[] normalized = texture.pixels().clone();
        for (int i = 0; i < normalized.length; i++)
            if (i % 4 != 0) normalized[i] = (byte) Math.min(255, (normalized[i] & 255) * 2);
        return new Rac1Texture(texture.width(), texture.height(), normalized);
    }

    private static byte[] sectors(Rac1DiscImage image, byte[] toc, int at, int limit)
            throws IOException, AssetFormatException {
        int sector = word(toc, at), count = word(toc, at + 4);
        if (sector <= 0 || count <= 0 || count > limit / 2048)
            throw new AssetFormatException("Invalid shop sector range at " + at);
        return image.readSectors(sector, count);
    }
}
