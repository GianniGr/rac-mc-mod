package com.github.giannigr.racmc.client.screen;

import com.github.giannigr.racmc.client.RacMcClient;
import com.github.giannigr.racmc.iso.IsoImagePath;
import com.github.giannigr.racmc.iso.IsoValidationResult;
import com.github.giannigr.racmc.iso.Rac1MediaManager;
import com.github.giannigr.racmc.iso.Rac1MediaState;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * The screen where the player points the mod at the disc image of their game.
 *
 * <p>The screen belongs to the main menu. It takes the path of a local file, checks it in the
 * background and reports what it found. It never copies, extracts or modifies anything.
 *
 * <p>The screen cannot be used to remove the configured disc image: the path of an image is only
 * written to the configuration after the image was verified completely, and a failed attempt leaves
 * the previous image in place.
 */
public class IsoSelectionScreen extends Screen {

    /** The width of the path field, in pixels. */
    private static final int FIELD_WIDTH = 260;

    /** The height of the path field, in pixels. */
    private static final int FIELD_HEIGHT = 20;

    private EditBox pathField;
    private Button checkButton;
    private Component status = Component.empty();

    /**
     * Creates the screen.
     */
    public IsoSelectionScreen() {
        super(Component.translatable("screen.racmc.iso_selection.title"));
    }

    @Override
    protected void init() {
        int left = (this.width - FIELD_WIDTH) / 2;
        int top = this.height / 2 - 30;

        this.pathField = new EditBox(this.font, left, top, FIELD_WIDTH, FIELD_HEIGHT,
                Component.translatable("screen.racmc.iso_selection.path"));
        this.pathField.setMaxLength(IsoImagePath.MAX_LENGTH);
        this.pathField.setHint(Component.translatable("screen.racmc.iso_selection.path_hint"));
        this.pathField.setValue(RacMcClient.media().configuredPath()
                .map(Path::toString)
                .orElse(""));
        addRenderableWidget(this.pathField);

        this.checkButton = addRenderableWidget(Button.builder(
                        Component.translatable("screen.racmc.iso_selection.check"),
                        button -> check())
                .bounds(left, top + FIELD_HEIGHT + 6, 120, 20)
                .build());

        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
                .bounds(left + FIELD_WIDTH - 120, top + FIELD_HEIGHT + 6, 120, 20)
                .build());

        setInitialFocus(this.pathField);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);

        int left = (this.width - FIELD_WIDTH) / 2;
        int top = this.height / 2 - 30;

        graphics.drawCenteredString(this.font, this.title, this.width / 2, top - 58, 0xFFFFFF);
        graphics.drawString(this.font,
                Component.translatable("screen.racmc.iso_selection.hint"),
                left, top - 38, 0xA0A0A0);
        graphics.drawString(this.font,
                Component.translatable("screen.racmc.iso_selection.mandatory"),
                left, top - 26, 0xFFAA00);
        drawWrapped(graphics, describeState(), left, top + FIELD_HEIGHT + 34, FIELD_WIDTH, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * Starts the check of the file in the path field.
     *
     * <p>The check runs on a worker thread of the media manager, so the screen stays usable while
     * the image is read.
     */
    private void check() {
        String text = this.pathField.getValue().trim();
        if (text.isEmpty()) {
            this.status = Component.translatable("screen.racmc.iso_selection.status.no_path");
            return;
        }
        Path path;
        try {
            path = Path.of(text);
        } catch (InvalidPathException exception) {
            this.status = Component.translatable("screen.racmc.iso_selection.status.invalid_path");
            return;
        }
        this.status = Component.translatable("screen.racmc.iso_selection.status.checking");
        RacMcClient.media().select(path);
    }

    /**
     * Describes the state of the configured disc image and of the running check.
     */
    private Component describeState() {
        Rac1MediaManager media = RacMcClient.media();
        Component configured = media.configuredPath()
                .map(path -> Component.translatable("screen.racmc.iso_selection.configured",
                        path.toString()))
                .orElse(Component.translatable("screen.racmc.iso_selection.not_configured"));

        if (media.isChecking()) {
            long total = media.bytesTotal();
            Component progress = total > 0
                    ? Component.translatable("screen.racmc.iso_selection.status.progress",
                            percent(media.bytesRead(), total))
                    : Component.translatable("screen.racmc.iso_selection.status.checking");
            return Component.empty().append(configured).append("\n").append(progress);
        }

        IsoValidationResult result = media.attemptResult().orElse(null);
        if (result != null && !result.isSupported()) {
            return Component.empty().append(configured).append("\n")
                    .append(Component.literal(result.message()));
        }
        return configured;
    }

    /**
     * The share of the work that is done, in percent.
     */
    private static int percent(long read, long total) {
        if (total <= 0) {
            return 0;
        }
        return (int) Math.min(100L, read * 100L / total);
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
