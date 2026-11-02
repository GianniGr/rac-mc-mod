package com.github.giannigr.racmc.client.screen;

import com.github.giannigr.racmc.RacMcMod;
import com.github.giannigr.racmc.cache.Rac1CacheContent;
import com.github.giannigr.racmc.client.Rac1BlasterDemo;
import com.github.giannigr.racmc.client.RacMcClient;
import com.github.giannigr.racmc.client.render.Rac1AssetPreview;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * The screen that shows the demo of the game: the hero holds his Blaster and fires it.
 *
 * <p>The screen is the in-game test of the import of the assets. It draws the imported geometry,
 * the imported textures and the imported animations of the hero and of the Blaster, and it places
 * the Blaster on the joint of the skeleton of the hero that the game hangs the items of the hand
 * on.
 *
 * <p>The screen is opened with the key of the mod and with the button of the main menu.
 */
public class BlasterTestScreen extends Screen {

    /** The pose of the demo. */
    private final Rac1BlasterDemo demo = new Rac1BlasterDemo();

    /** The drawing of the assets. */
    private final Rac1AssetPreview preview = new Rac1AssetPreview();

    /** The moment of the demo in seconds. */
    private float time;

    /** The content of the cache of the assets. */
    private Rac1CacheContent content;

    /**
     * Creates the screen.
     */
    public BlasterTestScreen() {
        super(Component.translatable("screen.racmc.blaster_test.title"));
    }

    @Override
    protected void init() {
        int left = this.width / 2 - 100;
        addRenderableWidget(Button.builder(Component.translatable("gui.back"),
                        button -> onClose())
                .bounds(left, this.height - 40, 200, 20)
                .build());
    }

    @Override
    public void tick() {
        this.time += 0.05f;
        Optional<Rac1CacheContent> cached = RacMcClient.assets().content();
        if (cached.isEmpty()) {
            return;
        }
        if (cached.get() != this.content) {
            this.content = cached.get();
        }
        this.demo.update(this.content, this.time);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        if (this.content != null) {
            graphics.pose().pushPose();
            try {
                graphics.pose().translate(this.width / 2.0f, this.height / 2.0f + 40.0f, 0.0f);
                graphics.pose().scale(90.0f, 90.0f, 90.0f);
                this.preview.draw(graphics, this.content, this.demo, (this.time * 20.0f) % 360.0f);
            } finally {
                graphics.pose().popPose();
            }
            if (this.preview.failure() != null) {
                graphics.drawCenteredString(this.font,
                        Component.translatable("screen.racmc.blaster_test.failed"), this.width / 2,
                        this.height / 2 + 80, 0xFF5555);
            }
        } else {
            graphics.drawCenteredString(this.font,
                    Component.translatable("screen.racmc.blaster_test.waiting"), this.width / 2,
                    this.height / 2, 0xFFAA00);
        }
        drawWrapped(graphics, Component.translatable("screen.racmc.blaster_test.hint"), 20,
                this.height - 70, this.width - 40, 0xA0A0A0);
    }

    @Override
    public void removed() {
        // The textures of the preview are images in the memory of the texture manager of the game,
        // so they have to be released when the screen is closed.
        this.preview.release();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        RacMcMod.LOGGER.info("The demo of the Blaster was closed.");
        // The screen is left for the main menu, so that the game is never left without a screen.
        if (this.minecraft != null) {
            this.minecraft.setScreen(new net.minecraft.client.gui.screens.TitleScreen());
        }
    }

    /**
     * Draws a text over several lines.
     */
    private void drawWrapped(GuiGraphics graphics, Component text, int x, int y, int width, int color) {
        if (text.getString().isEmpty()) {
            return;
        }
        List<FormattedCharSequence> lines = this.font.split(text, width);
        int line = 0;
        for (FormattedCharSequence sequence : lines) {
            graphics.drawString(this.font, sequence, x, y + line * 10, color);
            line++;
        }
    }
}
