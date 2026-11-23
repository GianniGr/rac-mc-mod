package com.github.giannigr.racmc.asset.shop;

import com.github.giannigr.racmc.asset.*;
import com.github.giannigr.racmc.asset.hud.Rac1BoltHudDefinition;
import com.github.giannigr.racmc.cache.Rac1CacheContent;
import java.util.*;

/** Full scene admission inventory. A valid subset cache must never imply original-shop readiness.
 * No client-provided capability bit or network flag can waive a missing scene requirement. */
public final class Rac1ShopReadiness {
    private Rac1ShopReadiness() {}
    public record Report(List<String> missingResources, List<String> pendingSceneComponents) {
        public Report { missingResources = List.copyOf(missingResources); pendingSceneComponents = List.copyOf(pendingSceneComponents); }
        public boolean ready() { return missingResources.isEmpty() && pendingSceneComponents.isEmpty(); }
        public void requireReady() {
            if (!ready()) throw new IllegalStateException("Original shop unavailable; resources="
                    + missingResources + "; scene=" + pendingSceneComponents);
        }
    }
    public static Report inspect(Rac1ShopAssets shop, Rac1CacheContent main) {
        List<String> missing = new ArrayList<>();
        if (shop == null) missing.add("shop_sidecar");
        else {
            missing.addAll(shop.missing2d()); missing.addAll(shop.missingSourceTables()); missing.addAll(shop.missingImportedModels());
            missing.addAll(shop.missingMaterialMetadata());
            if (main != null && !main.discKey().endsWith("|"+shop.discSha1())) missing.add("main_shop_disc_identity");
        }
        if (main == null || main.vendor() == null) missing.add("vendor_body11_logo1143");
        else {
            if (main.vendor().body().mesh().vertexCount() == 0 || main.vendor().body().textures().length == 0
                    || main.vendor().hologram().mesh().vertexCount() == 0 || main.vendor().hologram().textures().length == 0)
                missing.add("vendor_body_logo_geometry_textures");
            for (int i = 0; i < 5; i++) {
                boolean found = false;
                for (var clip : main.vendor().body().animations()) {
                    found |= clip.name().equals("sequence_" + i) && clip.keyCount() > 0;
                    if (clip.name().equals("sequence_4") && !clip.sourceTiming().available())
                        missing.add("vendor_fold_source_timing");
                }
                if (!found) missing.add("vendor_sequence_" + i);
            }
            if (shop != null && shop.sceneTables() != null)
                for (int target : shop.sceneTables().monitorTargets())
                    if (target >= main.vendor().body().skeleton().jointCount()) missing.add("vendor_manipulator_target");
            if (shop != null) {
                for (var model : List.of(main.vendor().body(),main.vendor().hologram()))
                    if (shop.normalMaterialVertexCount(model.classNumber()) != model.mesh().vertexCount())
                        missing.add("referenced_material_model_vertex_count_"+model.classNumber());
            }
        }
        if (main == null || main.blaster() == null || main.blaster().classNumber() != 168
                || main.blaster().mesh().vertexCount() == 0 || main.blaster().textures().length == 0)
            missing.add("referenced_blaster168");
        if (main == null || !Rac1BoltHudDefinition.complete(main.hud())) missing.add("referenced_bolt_hud");
        // Explicitly maintained implementation inventory, not opt-in approximations.
        List<String> pending = List.of("snapshot_quantisation_and_post_scale_linking",
                "unfold_reverse_fold_scene_bridge", "original_scene_and_popup_compositor",
                "untextured_packet_colors_and_chrome", "camera_capture_damage_input_policy",
                "verified_vendor_audio_UNBOUND");
        return new Report(missing, pending);
    }
}
