package com.github.giannigr.racmc.asset.shop;

import com.github.giannigr.racmc.asset.hud.Rac1HudAssets;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.Set;
import com.github.giannigr.racmc.asset.MobyClassReader.ClassMaterialMetadata;

/** Local decoded 2D subset, deliberately not a claim that the complete scene is ready. */
public record Rac1ShopAssets(String discSha1, int language, Rac1HudAssets graphics, Rac1ShopText text,
                             Rac1ShopSceneTables sceneTables, Rac1ShopModels models,
                             Map<Integer, ClassMaterialMetadata> materialMetadata) {
    public static final List<Integer> MATERIAL_CLASSES = List.of(11,12,13,1137,1143);
    public Rac1ShopAssets(String discSha1, int language, Rac1HudAssets graphics, Rac1ShopText text,
                          Rac1ShopSceneTables sceneTables, Rac1ShopModels models) {
        this(discSha1,language,graphics,text,sceneTables,models,Map.of());
    }
    public Rac1ShopAssets(String discSha1, int language, Rac1HudAssets graphics, Rac1ShopText text) {
        this(discSha1, language, graphics, text, null, null);
    }
    public Rac1ShopAssets(String discSha1, int language, Rac1HudAssets graphics, Rac1ShopText text,
                          Rac1ShopSceneTables sceneTables) {
        this(discSha1, language, graphics, text, sceneTables, null);
    }
    public Rac1ShopAssets {
        if (discSha1 == null || !discSha1.matches("[0-9a-f]{40}")
                || !List.of(0, 2, 3, 4, 5).contains(language))
            throw new IllegalArgumentException("Invalid shop source identity");
        Objects.requireNonNull(graphics);
        Objects.requireNonNull(text);
        materialMetadata = Map.copyOf(materialMetadata);
    }

    public List<String> missing2d() {
        var missing = new java.util.ArrayList<String>();
        for (var entry : java.util.Map.of("blaster_icons", 5, "led_atlas", 1,
                "cone", 1, "glass", 1, "noise", 1, "scan_plane", 1, "scan_bar", 1).entrySet()) {
            var frames = graphics.sprites().get(entry.getKey());
            if (frames == null || frames.size() != entry.getValue())
                missing.add(entry.getKey());
            else {
                int expected = switch (entry.getKey()) {
                    case "blaster_icons", "noise" -> 32;
                    case "scan_bar" -> 16;
                    default -> 64;
                };
                for (var frame : frames) {
                    if (frame.width() != expected || frame.height() != expected) {
                        missing.add(entry.getKey() + ":dimensions");
                        break;
                    }
                }
            }
        }
        for (String key : List.of("small", "regular")) {
            var font = graphics.fonts().get(key);
            if (font == null || font.atlas().width() != 256 || font.atlas().height() != 128
                    || font.metrics().length != 928) missing.add("font:" + key);
        }
        if (!text.complete()) missing.add("required_text");
        return List.copyOf(missing);
    }
    public List<String> missingSourceTables() {
        if (sceneTables == null) return List.of("scene_tables");
        if (!text.ids().containsAll(sceneTables.requiredTextIds())) return List.of("ticker_text");
        return List.of();
    }
    public List<String> missingImportedModels() {
        if (models == null) return List.of("shop_models");
        if (!models.ammo().source().discSha1().equals(discSha1)
                || models.ammo().source().sourceLevel() != 1) return List.of("shop_model_identity");
        return List.of();
    }

    /** Shape validation shared by local admission and persistence, without serializing or repairing data. */
    public static void requireMaterialMetadata(Map<Integer, ClassMaterialMetadata> metadata) {
        if (metadata == null || !metadata.keySet().equals(Set.copyOf(MATERIAL_CLASSES)))
            throw new IllegalArgumentException("Shop material metadata must name exactly the five original classes");
        for (int id : MATERIAL_CLASSES) {
            var source = Objects.requireNonNull(metadata.get(id));
            if (source.mode44() < 0 || source.mode44() > 65535 || source.mode47() < 0 || source.mode47() > 255
                    || source.packets().size() > 510)
                throw new IllegalArgumentException("Invalid shop class material metadata");
            Set<Integer> indices = new java.util.HashSet<>();
            int lastNormal = -1, lastMetal = -1;
            boolean metal = false;
            for (var packet : source.packets()) {
                int index = packet.packetIndex(), count = packet.vertexCount();
                if (index < 0 || index > 764 || !indices.add(index) || count < 0 || count > 255
                        || packet.materials().size() > 64
                        || packet.normalMultipliers().length != (packet.metal() ? 0 : count*4))
                    throw new IllegalArgumentException("Invalid shop packet material metadata");
                if (packet.metal()) {
                    if (index <= lastMetal) throw new IllegalArgumentException("Unordered metal packet metadata");
                    metal = true; lastMetal = index;
                } else {
                    if (metal || index <= lastNormal) throw new IllegalArgumentException("Unordered normal packet metadata");
                    lastNormal = index;
                }
                for (var material : packet.materials()) {
                    int selector = material.textureSelector();
                    if (packet.metal() ? selector != -2 && selector != -3 : selector < -1)
                        throw new IllegalArgumentException("Invalid shop packet material selector");
                }
            }
        }
    }

    public long normalMaterialVertexCount(int classId) {
        var metadata = materialMetadata.get(classId);
        if (metadata == null) return -1;
        long sum = 0;
        for (var packet : metadata.packets()) if (!packet.metal()) sum += packet.vertexCount();
        return sum;
    }

    public List<String> missingMaterialMetadata() {
        try { requireMaterialMetadata(materialMetadata); }
        catch (IllegalArgumentException | NullPointerException failure) { return List.of("shop_material_metadata"); }
        if (models != null) {
            for (var model : List.of(models.salesman(),models.backdrop(),models.popup()))
                if (normalMaterialVertexCount(model.classNumber()) != model.mesh().vertexCount())
                    return List.of("shop_material_model_vertex_count_"+model.classNumber());
        }
        return List.of();
    }
}
