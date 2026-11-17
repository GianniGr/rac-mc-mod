package com.github.giannigr.racmc.client;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.asset.hud.Rac1BoltHudDefinition;
import com.github.giannigr.racmc.client.render.Rac1HudDrawing;
import com.github.giannigr.racmc.client.render.Rac1HudTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/** Imported original panel, icon frames and ordinary glyph atlas. Owner-specific four-second timeout. */
@EventBusSubscriber(modid = RacMcConstants.MOD_ID, value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public final class Rac1BoltHud {
    private static final Rac1BoltHudAnimation ANIMATION = new Rac1BoltHudAnimation();
    private static Object playerIdentity;
    private Rac1BoltHud() {}
    public static void reset() { ANIMATION.reset(); playerIdentity = null; }
    @SubscribeEvent public static void register(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(RacMcConstants.MOD_ID, "bolts"),
                (graphics, delta) -> {
                    var mc = Minecraft.getInstance();
                    if (mc.player == null || mc.options.hideGui) return;
                    var data = mc.player.getPersistentData();
                    if (!Rac1BoltHudTime.visible(data.getLong("racmcBoltPickupNanos"), System.nanoTime())) return;
                    var content = RacMcClient.assets().content().orElse(null);
                    // No original art is claimed or substituted while assets are unavailable.
                    if (content == null || !Rac1BoltHudDefinition.complete(content.hud())) return;
                    var properties = content.hud().properties();
                    if (playerIdentity != mc.player) { reset(); playerIdentity = mc.player; }
                    long now = System.nanoTime();
                    var phase = ANIMATION.update(now, data.getLong("racmcBoltPickupNanos"),
                            properties.get("openSteps"), properties.get("textSteps"),
                            1_000_000_000L / properties.get("uiHz"));
                    if (phase.panel() == 0) return;
                    String text = Long.toString(data.getLong("racmcBolts"));
                    int digits = text.length(), digitWidth = properties.get("digitWidth");
                    // Responsive Minecraft anchor: original PS2-relative pixel dimensions at half scale.
                    graphics.pose().pushPose();
                    graphics.pose().scale(.5F, .5F, 1);
                    int x = graphics.guiWidth() * 2 - 32, y = properties.get("top");
                    try {
                        int slide = (int) (digitWidth * digits * phase.panel());
                        int middle = x - 28 - slide;
                        float alpha = (int) ((int) (phase.panel() * 128) * .7F) / 128F;
                        Rac1HudDrawing.sprite(graphics, content, "bolt_panel", 1,
                                x - 12, y, 32, 32, true, alpha);
                        Rac1HudDrawing.sprite(graphics, content, "bolt_panel", 0,
                                middle, y, slide + 16, 32, false, alpha);
                        Rac1HudDrawing.sprite(graphics, content, "bolt_panel", 1,
                                middle - 32, y, 32, 32, false, alpha);
                        // Original modulo-60/shift-one indexing at nominal regional cadence.
                        int frame = Rac1BoltHudLayout.frame(now, properties.get("uiHz"));
                        Rac1HudDrawing.sprite(graphics, content, "bolt", frame,
                                x - 32, y, 32, 32, false, 1);
                        int color = Rac1BoltHudAnimation.tween(properties.get("textClear"),
                                properties.get("textOpaque"), phase.text());
                        int shadow = Rac1BoltHudAnimation.tween(0, 0x80000000, phase.text());
                        int tx = x + properties.get("textX"), ty = y + properties.get("textY");
                        Rac1HudDrawing.textRight(graphics, content, "regular", text, tx + 1, ty + 1, shadow);
                        Rac1HudDrawing.textRight(graphics, content, "regular", text, tx, ty, color);
                        boolean dot = Rac1BoltHudLayout.dot(mc.options.languageCode);
                        String separator = dot ? "." : "'";
                        for (int group = 3; group < digits; group += 3) {
                            int sx = x + properties.get(dot ? "dotX" : "separatorX") - digitWidth * group;
                            int sy = y + properties.get(dot ? "dotY" : "separatorY");
                            Rac1HudDrawing.textRight(graphics, content, "regular", separator,
                                    sx + 2, sy + 2, shadow);
                            Rac1HudDrawing.textRight(graphics, content, "regular", separator, sx, sy, color);
                        }
                    } finally { graphics.pose().popPose(); }
                });
    }
    @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> Rac1HudTextures.release());
    }
}
