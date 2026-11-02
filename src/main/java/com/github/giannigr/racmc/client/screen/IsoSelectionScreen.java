package com.github.giannigr.racmc.client.screen;

import com.github.giannigr.racmc.client.RacMcClient;
import com.github.giannigr.racmc.iso.IsoImagePath;
import com.github.giannigr.racmc.iso.IsoValidationResult;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * The screen where the player points the mod at the disc image of their game.
 *
 * <p>The screen takes the path of a local file, checks it and reports what it found. It does not
 * copy, extract or modify anything, and it is not a requirement to play: it is opened with the key
 * mapping of the mod and can be closed at any time.
 *
 * <p>The check itself runs on a worker thread, because it reads a part of a file that can be
 * several gigabytes large. The result is handed back to the client thread before it is shown.
 */
public class IsoSelectionScreen extends Screen {

    /** The width of the path field, in pixels. */
    private static final int FIELD_WIDTH = 260;

    /** The height of the path field, in pixels. */
    private static final int FIELD_HEIGHT = 20;

    private EditBox pathField;
    private Button checkButton;
    private Component status = Component.empty();
    private boolean busy;

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
        this.pathField.setValue(RacMcClient.selectionService().selectedPath()
                .map(Path::toString)
                .orElse(""));
        addRenderableWidget(this.pathField);

        this.checkButton = addRenderableWidget(Button.builder(
                        Component.translatable("screen.racmc.iso_selection.check"),
                        button -> check())
                .bounds(left, top + FIELD_HEIGHT + 6, 120, 20)
                .build());

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
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

        graphics.drawCenteredString(this.font, this.title, this.width / 2, top - 46, 0xFFFFFF);
        graphics.drawString(this.font,
                Component.translatable("screen.racmc.iso_selection.hint"),
                left, top - 24, 0xA0A0A0);
        drawWrapped(graphics, this.status, left, top + FIELD_HEIGHT + 32, FIELD_WIDTH, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * Checks the file in the path field and shows what it found.
     */
    private void check() {
        if (this.busy) {
            return;
        }
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

        this.busy = true;
        this.status = Component.translatable("screen.racmc.iso_selection.status.checking");
        if (this.checkButton != null) {
            this.checkButton.active = false;
        }

        Minecraft client = this.minecraft;
        CompletableFuture.supplyAsync(() -> RacMcClient.selectionService().select(path))
                .thenAccept(result -> {
                    if (client != null) {
                        client.execute(() -> applyResult(result));
                    }
                })
                .exceptionally(throwable -> {
                    if (client != null) {
                        client.execute(() -> {
                            this.busy = false;
                            if (this.checkButton != null) {
                                this.checkButton.active = true;
                            }
                            this.status = Component.translatable("screen.racmc.iso_selection.status.io_error");
                        });
                    }
                    return null;
                });
    }

    /**
     * Shows the outcome of a check.
     */
    private void applyResult(IsoValidationResult result) {
        this.busy = false;
        if (this.checkButton != null) {
            this.checkButton.active = true;
        }
        this.status = Component.literal(result.message());
    }

    /**
     * Draws a text over several lines and returns nothing, because the text is drawn below the
     * field and does not overlap with anything else.
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
