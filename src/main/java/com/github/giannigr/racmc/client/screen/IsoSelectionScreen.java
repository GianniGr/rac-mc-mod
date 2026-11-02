package com.github.giannigr.racmc.client.screen;

import com.github.giannigr.racmc.RacMcMod;
import com.github.giannigr.racmc.client.RacMcClient;
import com.github.giannigr.racmc.iso.IsoValidationResult;
import com.github.giannigr.racmc.iso.Rac1MediaManager;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * The screen where the player points the mod at the disc image of their game.
 *
 * <p>The screen belongs to the main menu. It lets the player pick the disc image with a file
 * chooser and shows what the check of that file found. The check of the image reads the whole file
 * and runs on a worker thread, so the screen stays usable and shows how far the reading has come.
 *
 * <p>The screen has two modes. In the mandatory mode - which is the mode it is opened in while no
 * verified disc image is configured - it cannot be dismissed: there is no back button, the escape
 * key does nothing and the main menu it replaces never opens. The only way out is a verified disc
 * image, the settings of the game and quitting the game. In the other mode, which is used to
 * change or check the image that is already configured, the screen can be left with the back
 * button.
 */
public class IsoSelectionScreen extends Screen {

    /** The width of the progress bar, in pixels. */
    private static final int BAR_WIDTH = 260;

    /** The height of the progress bar, in pixels. */
    private static final int BAR_HEIGHT = 10;

    private final boolean mandatory;

    private Component status = Component.empty();
    private IsoValidationResult reportedResult;
    private Button pickButton;

    /**
     * Creates the screen in the mode that can be left with the back button.
     */
    public IsoSelectionScreen() {
        this(false);
    }

    /**
     * Creates the screen.
     *
     * @param mandatory whether the screen cannot be dismissed
     */
    public IsoSelectionScreen(boolean mandatory) {
        super(Component.translatable("screen.racmc.iso_selection.title"));
        this.mandatory = mandatory;
        if (mandatory) {
            RacMcMod.LOGGER.info("The disc image picker was opened and cannot be dismissed.");
        }
    }

    @Override
    protected void init() {
        int left = (this.width - BAR_WIDTH) / 2;
        int top = this.height / 2 - 30;

        this.pickButton = addRenderableWidget(Button.builder(
                        Component.translatable("screen.racmc.iso_selection.pick"),
                        button -> pickImage())
                .bounds(left, top, 160, 20)
                .build());

        if (this.mandatory) {
            // The screen cannot be left, so the player needs the settings of the game and a way
            // to quit the game.
            addRenderableWidget(Button.builder(
                            Component.translatable("screen.racmc.iso_selection.options"),
                            button -> this.minecraft.setScreen(
                                    new OptionsScreen(this, this.minecraft.options)))
                    .bounds(left + BAR_WIDTH - 100, top, 100, 20)
                    .build());
            addRenderableWidget(Button.builder(Component.translatable("menu.quit"), button -> this.minecraft.stop())
                    .bounds(left, top + 26, 160, 20)
                    .build());
        } else {
            addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
                    .bounds(left + BAR_WIDTH - 100, top, 100, 20)
                    .build());
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);

        int left = (this.width - BAR_WIDTH) / 2;
        int top = this.height / 2 - 30;

        graphics.drawCenteredString(this.font, this.title, this.width / 2, top - 58, 0xFFFFFF);
        graphics.drawString(this.font,
                Component.translatable("screen.racmc.iso_selection.hint"),
                left, top - 38, 0xA0A0A0);
        graphics.drawString(this.font,
                Component.translatable("screen.racmc.iso_selection.mandatory"),
                left, top - 26, 0xFFAA00);
        drawWrapped(graphics, describeState(), left, top + 58, BAR_WIDTH, 0xFFFFFF);
        drawProgress(graphics, left, top + 40);
    }

    @Override
    public void tick() {
        reportResult();
        if (this.pickButton != null) {
            // Only one check runs at a time.
            this.pickButton.active = !RacMcClient.media().isChecking();
        }
        // A verified disc image lets the player leave the screen and go to the main menu.
        if (this.mandatory && RacMcClient.media().isReady()) {
            this.minecraft.setScreen(new TitleScreen());
        }
    }

    /**
     * Writes the outcome of the last check into the log, once.
     */
    private void reportResult() {
        IsoValidationResult result = RacMcClient.media().attemptResult().orElse(null);
        if (result == null || result == this.reportedResult) {
            return;
        }
        this.reportedResult = result;
        if (result.isSupported()) {
            RacMcMod.LOGGER.info("The disc image was verified: {}.", result.revision().label());
        } else {
            RacMcMod.LOGGER.info("The disc image was refused: {}", result.message());
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return !this.mandatory;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.mandatory && keyCode == 256) {
            // The escape key cannot leave the screen while no verified disc image is configured.
            RacMcMod.LOGGER.info("The disc image picker ignored the escape key.");
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (this.mandatory) {
            // The screen cannot be dismissed while no verified disc image is configured.
            RacMcMod.LOGGER.info("The disc image picker refused to close.");
            return;
        }
        super.onClose();
    }

    /**
     * Opens a file chooser and checks the file the player picks.
     *
     * <p>The chooser runs on the thread of the user interface toolkit of Java, the check runs on a
     * worker thread of the media manager. Both hand their result back to the thread of the game.
     */
    private void pickImage() {
        RacMcMod.LOGGER.info("The disc image picker opens the file chooser.");
        Minecraft client = this.minecraft;
        SwingUtilities.invokeLater(() -> {
            RacMcMod.LOGGER.info("The file chooser thread starts, headless={}",
                    GraphicsEnvironment.isHeadless());
            Path chosen = showChooser();
            RacMcMod.LOGGER.info("The file chooser returned.");
            if (chosen != null) {
                client.execute(() -> {
                    RacMcClient.media().select(chosen);
                });
            }
        });
    }

    /**
     * Shows the file chooser of the system.
     *
     * @return the file the player picked, or {@code null} when the chooser was cancelled
     */
    private static Path showChooser() {
        if (GraphicsEnvironment.isHeadless()) {
            RacMcMod.LOGGER.warn("The system has no graphical user interface, so no file chooser "
                    + "can be opened.");
            return null;
        }
        try {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle(Component.translatable("screen.racmc.iso_selection.pick")
                    .getString());
            chooser.setFileFilter(new FileNameExtensionFilter(
                    Component.translatable("screen.racmc.iso_selection.filter").getString(), "iso"));
            chooser.setAcceptAllFileFilterUsed(true);
            RacMcMod.LOGGER.info("The file chooser of the system is opened.");
            int result;
            // The chooser gets its own window that stays in front of the game, so that it is not
            // hidden behind it.
            JDialog dialog = new JDialog((Frame) null,
                    Component.translatable("screen.racmc.iso_selection.pick").getString(), true);
            dialog.setAlwaysOnTop(true);
            try {
                result = chooser.showOpenDialog(dialog);
            } finally {
                dialog.dispose();
            }
            RacMcMod.LOGGER.info("The file chooser of the system was closed with {}.", result);
            if (result == JFileChooser.APPROVE_OPTION) {
                File file = chooser.getSelectedFile();
                return file == null ? null : file.toPath();
            }
        } catch (RuntimeException | Error exception) {
            RacMcMod.LOGGER.warn("The file chooser of the system could not be opened.", exception);
        }
        return null;
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
            // A configured image is verified again after a start of the game. The player does not
            // have to pick it again, so the screen says that the image is being verified.
            Component headline = media.configuredPath().isPresent()
                    ? Component.translatable("screen.racmc.iso_selection.status.revalidating")
                    : Component.translatable("screen.racmc.iso_selection.status.checking");
            return Component.empty().append(configured).append("\n").append(headline);
        }

        IsoValidationResult result = media.attemptResult().orElse(null);
        if (result != null && !result.isSupported()) {
            return Component.empty().append(configured).append("\n")
                    .append(Component.literal(result.message()));
        }
        if (result != null && result.isSupported()) {
            return Component.empty().append(configured).append("\n")
                    .append(Component.translatable("screen.racmc.iso_selection.status.accepted",
                            result.revision().label()));
        }
        return configured;
    }

    /**
     * Draws the progress of the running check.
     */
    private void drawProgress(GuiGraphics graphics, int x, int y) {
        Rac1MediaManager media = RacMcClient.media();
        if (!media.isChecking()) {
            return;
        }
        long total = media.bytesTotal();
        int done = total > 0 ? (int) Math.min(BAR_WIDTH, media.bytesRead() * BAR_WIDTH / total) : 0;
        graphics.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, 0xFF404040);
        graphics.fill(x, y, x + done, y + BAR_HEIGHT, 0xFF55FF55);
        graphics.drawString(this.font,
                Component.translatable("screen.racmc.iso_selection.status.progress",
                        percent(media.bytesRead(), total)),
                x, y - 12, 0xFFFFFF);
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
