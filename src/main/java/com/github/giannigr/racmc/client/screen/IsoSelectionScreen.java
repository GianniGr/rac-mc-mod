package com.github.giannigr.racmc.client.screen;

import com.github.giannigr.racmc.RacMcMod;
import com.github.giannigr.racmc.cache.Rac1CacheContent;
import com.github.giannigr.racmc.client.Rac1BlasterDemo;
import com.github.giannigr.racmc.cache.Rac1CacheState;
import com.github.giannigr.racmc.client.RacMcClient;
import com.github.giannigr.racmc.client.render.Rac1AssetPreview;
import com.github.giannigr.racmc.iso.IsoValidationResult;
import com.github.giannigr.racmc.iso.Rac1MediaManager;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
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
import net.minecraft.resources.ResourceLocation;
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
 *
 * <p>The report of the screen - the path of the disc image, the outcome of its check and the
 * state of the cache of the assets - stands in a panel that fills the room under the buttons. A
 * report that is too long for the panel is not cut off: the panel scrolls, so that every line of
 * the report, the long path of the image and the reason of a failure included, can be read. The
 * places of the buttons, of the panel and of the drawing of the preview are held by
 * {@link Rac1ScreenLayout}, so that they fit into the narrowest window of the game.
 */
public class IsoSelectionScreen extends Screen {

    /** The width of the progress bar, in pixels. */
    private static final int BAR_WIDTH = 260;

    /** The height of the progress bar, in pixels. */
    private static final int BAR_HEIGHT = 10;

    /** The number of lines the report is scrolled by one step of the mouse wheel. */
    private static final int SCROLL_LINES = 3;

    private final boolean mandatory;

    private Component status = Component.empty();
    private IsoValidationResult reportedResult;
    private Button pickButton;
    private Button retryButton;

    /** The pose of the demo of the Blaster. */
    private final Rac1BlasterDemo demo = new Rac1BlasterDemo();

    /** The drawing of the assets of the game. */
    private final Rac1AssetPreview preview = new Rac1AssetPreview();

    /** The moment of the animation of the preview, in seconds. */
    private float previewTime;

    /** The width of the text of the screen. */
    private int contentWidth;

    /** The left edge of the text of the screen. */
    private int contentLeft;

    /** The upper edge of the title of the screen. */
    private int titleTop;

    /** The upper edge of the hint of the screen. */
    private int hintTop;

    /** The upper edge of the line that tells why the screen cannot be left. */
    private int mandatoryTop;

    /** The lower edge of the last button of the screen. */
    private int buttonsBottom;

    /** The line of the report that is shown first. */
    private int reportScroll;

    /** The places of the last frame. */
    private Rac1ScreenLayout.Layout layout = Rac1ScreenLayout.layout(0, 0, 0, 0, false);

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
        this.contentWidth = Rac1ScreenLayout.contentWidth(this.width);
        this.contentLeft = Rac1ScreenLayout.contentLeft(this.width);

        int y = Rac1ScreenLayout.MARGIN;
        this.titleTop = y;
        y += 12;
        this.hintTop = y;
        y += linesOf(Component.translatable("screen.racmc.iso_selection.hint"))
                * Rac1ScreenLayout.LINE_HEIGHT;
        y += 2;
        this.mandatoryTop = y;
        y += linesOf(Component.translatable("screen.racmc.iso_selection.mandatory"))
                * Rac1ScreenLayout.LINE_HEIGHT;
        y += 6;

        int row = y;
        this.pickButton = addRenderableWidget(Button.builder(
                        Component.translatable("screen.racmc.iso_selection.pick"),
                        button -> pickImage())
                .bounds(this.contentLeft, row, Rac1ScreenLayout.PICK_BUTTON_WIDTH,
                        Rac1ScreenLayout.BUTTON_HEIGHT)
                .build());

        boolean side = Rac1ScreenLayout.sideBySide(this.contentWidth);
        int sideX = this.contentLeft + this.contentWidth - Rac1ScreenLayout.SIDE_BUTTON_WIDTH;
        if (this.mandatory) {
            // The screen cannot be left, so the player needs the settings of the game and a way
            // to quit the game. The buttons stand in two rows, so that the report of the screen
            // keeps its room in a small window.
            if (side) {
                addRenderableWidget(Button.builder(
                                Component.translatable("screen.racmc.iso_selection.options"),
                                button -> this.minecraft.setScreen(
                                        new OptionsScreen(this, this.minecraft.options)))
                        .bounds(sideX, row, Rac1ScreenLayout.SIDE_BUTTON_WIDTH,
                                Rac1ScreenLayout.BUTTON_HEIGHT)
                        .build());
                row += Rac1ScreenLayout.BUTTON_HEIGHT + Rac1ScreenLayout.BUTTON_GAP;
            } else {
                row += Rac1ScreenLayout.BUTTON_HEIGHT + Rac1ScreenLayout.BUTTON_GAP;
                addRenderableWidget(Button.builder(
                                Component.translatable("screen.racmc.iso_selection.options"),
                                button -> this.minecraft.setScreen(
                                        new OptionsScreen(this, this.minecraft.options)))
                        .bounds(this.contentLeft, row, Rac1ScreenLayout.SIDE_BUTTON_WIDTH,
                                Rac1ScreenLayout.BUTTON_HEIGHT)
                        .build());
                row += Rac1ScreenLayout.BUTTON_HEIGHT + Rac1ScreenLayout.BUTTON_GAP;
            }
            addRenderableWidget(Button.builder(Component.translatable("menu.quit"),
                            button -> this.minecraft.stop())
                    .bounds(this.contentLeft, row, Rac1ScreenLayout.PICK_BUTTON_WIDTH,
                            Rac1ScreenLayout.BUTTON_HEIGHT)
                    .build());
            if (side) {
                this.retryButton = addRenderableWidget(Button.builder(
                                Component.translatable("screen.racmc.iso_selection.retry"),
                                button -> {
                                    RacMcMod.LOGGER.info("The cache of the assets is built again.");
                                    RacMcClient.assets().retry();
                                })
                        .bounds(sideX, row, Rac1ScreenLayout.SIDE_BUTTON_WIDTH,
                                Rac1ScreenLayout.BUTTON_HEIGHT)
                        .build());
                row += Rac1ScreenLayout.BUTTON_HEIGHT;
            } else {
                row += Rac1ScreenLayout.BUTTON_HEIGHT + Rac1ScreenLayout.BUTTON_GAP;
                this.retryButton = addRenderableWidget(Button.builder(
                                Component.translatable("screen.racmc.iso_selection.retry"),
                                button -> {
                                    RacMcMod.LOGGER.info("The cache of the assets is built again.");
                                    RacMcClient.assets().retry();
                                })
                        .bounds(this.contentLeft, row, Rac1ScreenLayout.PICK_BUTTON_WIDTH,
                                Rac1ScreenLayout.BUTTON_HEIGHT)
                        .build());
                row += Rac1ScreenLayout.BUTTON_HEIGHT;
            }
            this.retryButton.visible = false;
        } else {
            if (side) {
                addRenderableWidget(Button.builder(Component.translatable("gui.back"),
                                button -> onClose())
                        .bounds(sideX, row, Rac1ScreenLayout.SIDE_BUTTON_WIDTH,
                                Rac1ScreenLayout.BUTTON_HEIGHT)
                        .build());
                row += Rac1ScreenLayout.BUTTON_HEIGHT + Rac1ScreenLayout.BUTTON_GAP;
            } else {
                row += Rac1ScreenLayout.BUTTON_HEIGHT + Rac1ScreenLayout.BUTTON_GAP;
                addRenderableWidget(Button.builder(Component.translatable("gui.back"),
                                button -> onClose())
                        .bounds(this.contentLeft, row, Rac1ScreenLayout.SIDE_BUTTON_WIDTH,
                                Rac1ScreenLayout.BUTTON_HEIGHT)
                        .build());
                row += Rac1ScreenLayout.BUTTON_HEIGHT;
            }
        }
        this.buttonsBottom = row;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        // The buttons of the screen are drawn here. The text of the screen is drawn after them,
        // because the screen of the game draws the background of the screen - and with it the
        // blur of the background - once more while the buttons are drawn. Text that is drawn
        // before that would end up in the blur and would not be readable.
        super.render(graphics, mouseX, mouseY, partialTick);

        List<FormattedCharSequence> report = this.font.split(describeState(), reportWidth());
        boolean progress = progressActive();
        int reportTop = Rac1ScreenLayout.reportTop(this.buttonsBottom, progress);
        this.layout = Rac1ScreenLayout.layout(this.width, this.height, reportTop,
                report.size(), RacMcClient.assets().state() == Rac1CacheState.READY);

        drawHeader(graphics);
        drawProgress(graphics);
        drawReport(graphics, report);
        drawPreview(graphics);
    }

    /**
     * Whether the bar of the progress is drawn under the buttons of the screen.
     *
     * <p>The room of the bar is only kept when there is a bar to draw, so that the report of the
     * screen keeps the room of the bar in a small window.
     *
     * @return {@code true} while the disc image is read or the cache is built
     */
    private boolean progressActive() {
        return RacMcClient.media().isChecking()
                || RacMcClient.assets().state() == Rac1CacheState.BUILDING;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0 && this.layout.report().contains(mouseX, mouseY)) {
            this.reportScroll -= (int) Math.signum(scrollY) * SCROLL_LINES;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void tick() {
        // The preview of the assets moves with the clock of the game, so that the animation of
        // the hero runs at the same speed whatever the number of pictures per second is.
        this.previewTime += 0.05f;
        reportResult();
        if (this.pickButton != null) {
            // Only one check runs at a time.
            this.pickButton.active = !RacMcClient.media().isChecking();
        }
        if (this.retryButton != null) {
            // The button is only there when the cache could not be built.
            this.retryButton.visible = RacMcClient.assets().state() == Rac1CacheState.FAILED;
        }
        // A verified disc image and a cache that holds the assets let the player leave the screen
        // and go to the main menu.
        if (this.mandatory && RacMcClient.allowsWorldEntry()) {
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
        // The screen is left for the main menu, so that the game is never left without a screen.
        if (this.minecraft != null) {
            this.minecraft.setScreen(new TitleScreen());
        }
    }

    @Override
    public void removed() {
        // The textures of the preview are images in the memory of the texture manager of the game,
        // so they have to be released when the screen is closed.
        this.preview.release();
        super.removed();
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
     * Describes the state of the configured disc image, of the running check and of the cache of the
     * assets of the game.
     */
    private Component describeState() {
        Rac1MediaManager media = RacMcClient.media();
        Component configured = media.configuredPath()
                .map(path -> Component.translatable("screen.racmc.iso_selection.configured",
                        path.toString()))
                .orElse(Component.translatable("screen.racmc.iso_selection.not_configured"));

        Component assets = describeAssets();
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
                            result.revision().label()))
                    .append("\n").append(assets);
        }
        return configured;
    }

    /**
     * Describes the state of the cache of the assets of the game.
     */
    private Component describeAssets() {
        var cache = RacMcClient.assets();
        switch (cache.state()) {
            case BUILDING:
                return Component.translatable("screen.racmc.iso_selection.assets.building",
                        cache.progress());
            case READY:
                return Component.translatable("screen.racmc.iso_selection.assets.ready");
            case FAILED:
                return Component.translatable("screen.racmc.iso_selection.assets.failed",
                        cache.error().orElse(""));
            default:
                return Component.translatable("screen.racmc.iso_selection.assets.missing");
        }
    }

    /**
     * Draws the title of the screen, the hint of it and the line that tells why the screen cannot
     * be left.
     */
    private void drawHeader(GuiGraphics graphics) {
        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.titleTop, 0xFFFFFF);
        drawWrapped(graphics, Component.translatable("screen.racmc.iso_selection.hint"),
                this.contentLeft, this.hintTop, this.contentWidth, 0xA0A0A0);
        drawWrapped(graphics, Component.translatable("screen.racmc.iso_selection.mandatory"),
                this.contentLeft, this.mandatoryTop, this.contentWidth, 0xFFAA00);
    }

    /**
     * Draws the progress of the running check and of the running build of the cache.
     */
    private void drawProgress(GuiGraphics graphics) {
        Rac1MediaManager media = RacMcClient.media();
        int x = this.contentLeft;
        int top = this.buttonsBottom + Rac1ScreenLayout.BUTTON_GAP;
        int y = top + 12;
        // The bar never reaches over the edge of the window.
        int width = Math.min(BAR_WIDTH, this.contentWidth);
        if (media.isChecking()) {
            long total = media.bytesTotal();
            int done = total > 0 ? (int) Math.min(width, media.bytesRead() * width / total) : 0;
            graphics.fill(x, y, x + width, y + BAR_HEIGHT, 0xFF404040);
            graphics.fill(x, y, x + done, y + BAR_HEIGHT, 0xFF55FF55);
            graphics.drawString(this.font,
                    Component.translatable("screen.racmc.iso_selection.status.progress",
                            percent(media.bytesRead(), total)),
                    x, this.buttonsBottom + Rac1ScreenLayout.BUTTON_GAP, 0xFFFFFF);
            return;
        }
        var cache = RacMcClient.assets();
        if (cache.state() != Rac1CacheState.BUILDING) {
            return;
        }
        int done = cache.progress() * width / 100;
        graphics.fill(x, y, x + width, y + BAR_HEIGHT, 0xFF404040);
        graphics.fill(x, y, x + done, y + BAR_HEIGHT, 0xFF55FF55);
        graphics.drawString(this.font, Component.translatable("screen.racmc.iso_selection.assets.progress", cache.progress(),
                cache.step()),
                x, this.buttonsBottom + Rac1ScreenLayout.BUTTON_GAP, 0xFFFFFF);
    }

    /**
     * Draws the report of the screen in its panel.
     *
     * <p>The panel shows the lines of the report that fit into it and scrolls them when the
     * report is longer. The text is cut off by the frame of the panel, so that it never reaches
     * over the buttons of the screen or over the drawing of the preview.
     *
     * @param graphics the graphics of the screen
     * @param report the lines of the report
     */
    private void drawReport(GuiGraphics graphics, List<FormattedCharSequence> report) {
        Rac1ScreenLayout.Area area = this.layout.report();
        if (area.height() <= 0) {
            return;
        }
        graphics.fill(area.left(), area.top(), area.right(), area.bottom(), 0xFF404040);
        graphics.fill(area.left() + 1, area.top() + 1, area.right() - 1, area.bottom() - 1,
                0xC0000000);

        int scroll = Math.max(0, Math.min(this.reportScroll, this.layout.maxScroll()));
        this.reportScroll = scroll;

        graphics.enableScissor(area.left() + 1, area.top() + 1, area.right() - 1, area.bottom() - 1);
        int y = area.top() + Rac1ScreenLayout.PADDING;
        for (int line = 0; line < this.layout.visibleLines(); line++) {
            int index = scroll + line;
            if (index >= report.size()) {
                break;
            }
            graphics.drawString(this.font, report.get(index),
                    area.left() + Rac1ScreenLayout.PADDING, y, 0xFFFFFF);
            y += Rac1ScreenLayout.LINE_HEIGHT;
        }
        graphics.disableScissor();

        if (this.layout.maxScroll() > 0) {
            // The report is longer than the panel, so the player is told that it can be scrolled
            // and a bar shows how far the report is scrolled.
            graphics.drawString(this.font,
                    Component.translatable("screen.racmc.iso_selection.scroll"),
                    area.left() + Rac1ScreenLayout.PADDING,
                    area.bottom() - Rac1ScreenLayout.PADDING - Rac1ScreenLayout.LINE_HEIGHT + 2,
                    0xA0A0A0);
            int trackTop = area.top() + Rac1ScreenLayout.PADDING;
            int trackHeight = area.height() - 2 * Rac1ScreenLayout.PADDING;
            int thumbHeight = Math.max(6, trackHeight * this.layout.visibleLines()
                    / Math.max(1, report.size()));
            int thumbTop = trackTop + (trackHeight - thumbHeight) * scroll
                    / Math.max(1, this.layout.maxScroll());
            graphics.fill(area.right() - 3, trackTop, area.right() - 1,
                    trackTop + trackHeight, 0xFF202020);
            graphics.fill(area.right() - 3, thumbTop, area.right() - 1, thumbTop + thumbHeight,
                    0xFFA0A0A0);
        }
    }

    /**
     * Draws the assets of the game, so that the player can see that they were converted.
     *
     * <p>The preview shows the hero with his Blaster in his hand, the same pose as the demo of the
     * Blaster. A failure of the drawing is reported once by the preview and the screen stays
     * usable. The preview is only drawn when there is room for it beside the report of the
     * screen, so that the two never cover each other.
     */
    private void drawPreview(GuiGraphics graphics) {
        if (!this.layout.hasPreview()) {
            return;
        }
        if (RacMcClient.assets().state() != Rac1CacheState.READY) {
            return;
        }
        Optional<Rac1CacheContent> content = RacMcClient.assets().content();
        if (content.isEmpty()) {
            return;
        }
        Rac1ScreenLayout.Area area = this.layout.preview();
        this.demo.update(content.get(), this.previewTime);
        this.preview.draw(graphics, content.get(), this.demo, (this.previewTime * 20.0f) % 360.0f,
                area.left(), area.top(), area.width(), area.height());
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
     * The number of lines of a text in the width of the text of the screen.
     */
    private int linesOf(Component text) {
        return this.font.split(text, this.contentWidth).size();
    }

    /**
     * The width of the lines of the report.
     *
     * <p>The width leaves room for the frame of the panel and for the bar that shows how far the
     * report is scrolled.
     */
    private int reportWidth() {
        return Math.max(20, this.contentWidth - 2 * Rac1ScreenLayout.PADDING - 4);
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
            graphics.drawString(this.font, sequence, x, y + line * Rac1ScreenLayout.LINE_HEIGHT, color);
            line++;
        }
    }
}
