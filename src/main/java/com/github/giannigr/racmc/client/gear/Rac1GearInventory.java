package com.github.giannigr.racmc.client.gear;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.gear.Rac1BeltGadget;
import com.github.giannigr.racmc.gear.Rac1BeltState.Availability;
import com.github.giannigr.racmc.gear.Rac1GearState;
import java.util.List;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;

/** Two views in the same vanilla inventory screen and menu; Back is server-held escrow. */
@EventBusSubscriber(modid = RacMcConstants.MOD_ID, value = Dist.CLIENT)
public final class Rac1GearInventory {
    private static final WeakHashMap<AbstractContainerScreen<?>, Panel> PANELS = new WeakHashMap<>();
    private static final int TAB_WIDTH = 86;

    private Rac1GearInventory() {}

    @SubscribeEvent
    public static void init(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)
                || !(screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen)) return;
        // Don't attach to the transient Survival screen during the vanilla Creative replacement.
        if (Minecraft.getInstance().screen != screen) return;
        Panel panel = PANELS.computeIfAbsent(screen, unused -> new Panel());
        event.addListener(panel.gearTab);
        event.addListener(panel.inventoryTab);
        event.addListener(panel.back);
        event.addListener(panel.boltGrabber);
        panel.layout(screen);
    }

    @SubscribeEvent
    public static void render(ScreenEvent.Render.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        Panel panel = PANELS.get(screen);
        if (panel == null) return;
        panel.layout(screen);
        if (!panel.open) return;
        event.setCanceled(true);
        screen.renderTransparentBackground(event.getGuiGraphics());
        panel.render(event.getGuiGraphics(), event.getMouseX(), event.getMouseY(), event.getPartialTick());
    }

    @SubscribeEvent
    public static void press(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        Panel panel = PANELS.get(screen);
        if (panel == null) return;
        panel.layout(screen);
        if (!panel.open && !overTab(panel.gearTab, event.getMouseX(), event.getMouseY())) return;
        // Bypass vanilla container mouse handling for the tab and for all of the Gear view.
        event.setCanceled(true);
        panel.consumedButton = event.getButton();
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;
        for (Button button : panel.open ? panel.gearControls : panel.inventoryControls) {
            if (button.mouseClicked(event.getMouseX(), event.getMouseY(), event.getButton())) {
                panel.layout(screen);
                screen.setFocused(panel.open
                        ? (panel.gearControls.contains(button) && button.active ? button : panel.inventoryTab)
                        : panel.gearTab);
                break;
            }
        }
    }

    @SubscribeEvent
    public static void release(ScreenEvent.MouseButtonReleased.Pre event) {
        Panel panel = PANELS.get(event.getScreen());
        if (panel == null) return;
        if (panel.open || panel.consumedButton == event.getButton()) event.setCanceled(true);
        if (panel.consumedButton == event.getButton()) panel.consumedButton = -1;
    }

    @SubscribeEvent
    public static void drag(ScreenEvent.MouseDragged.Pre event) {
        Panel panel = PANELS.get(event.getScreen());
        if (panel != null && (panel.open || panel.consumedButton == event.getMouseButton()))
            event.setCanceled(true);
    }

    @SubscribeEvent
    public static void scroll(ScreenEvent.MouseScrolled.Pre event) {
        if (gearOpen(event)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void type(ScreenEvent.CharacterTyped.Pre event) {
        if (gearOpen(event)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void key(ScreenEvent.KeyPressed.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        Panel panel = PANELS.get(screen);
        if (panel == null) return;
        panel.layout(screen);
        int key = event.getKeyCode();
        if (panel.consumedKey == key) {
            event.setCanceled(true);
            return;
        }
        if (!panel.open) {
            // On the native inventory view, leave navigation to Minecraft and only intercept
            // explicit activation of the focused Gear tab.
            if (screen.getFocused() == panel.gearTab && panel.gearTab.visible
                    && activateKey(key)) {
                event.setCanceled(true);
                if (panel.gearTab.keyPressed(key, event.getScanCode(), event.getModifiers())) {
                    panel.consumedKey = key;
                    panel.layout(screen);
                    screen.setFocused(panel.inventoryTab);
                }
            }
            return;
        }
        event.setCanceled(true);
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            panel.consumedKey = key;
            panel.open = false;
        } else if (Minecraft.getInstance().options.keyInventory.matches(key, event.getScanCode())) {
            screen.onClose();
            return;
        } else if (key == GLFW.GLFW_KEY_TAB || key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_UP) {
            boolean reverse = key == GLFW.GLFW_KEY_UP || (key == GLFW.GLFW_KEY_TAB
                    && (event.getModifiers() & GLFW.GLFW_MOD_SHIFT) != 0);
            screen.setFocused(panel.nextFocus(screen.getFocused(), reverse));
        } else if (activateKey(key) && screen.getFocused() instanceof Button focused
                && panel.gearControls.contains(focused)) {
            if (focused.keyPressed(key, event.getScanCode(), event.getModifiers())) panel.consumedKey = key;
        }
        if (!panel.open) {
            panel.layout(screen);
            screen.setFocused(panel.gearTab);
        }
    }

    @SubscribeEvent
    public static void keyReleased(ScreenEvent.KeyReleased.Pre event) {
        Panel panel = PANELS.get(event.getScreen());
        if (panel != null && panel.consumedKey == event.getKeyCode()) {
            panel.consumedKey = Integer.MIN_VALUE;
            event.setCanceled(true);
        }
    }

    private static boolean activateKey(int key) {
        return key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE;
    }

    private static boolean overTab(Button tab, double x, double y) {
        return tab.visible && x >= tab.getX() && x < tab.getX() + tab.getWidth()
                && y >= tab.getY() && y < tab.getY() + tab.getHeight();
    }

    private static boolean gearOpen(ScreenEvent event) {
        Panel panel = PANELS.get(event.getScreen());
        return panel != null && panel.open;
    }

    private static Component text(String key) {
        return Component.translatable("screen.racmc.gear." + key);
    }

    static Component backStatus(Rac1GearState state) {
        if (state == null) return text("waiting");
        switch (state.fixture()) {
            case HELI -> {
                return Component.translatableWithFallback("screen.racmc.gear.test_worn", "%s (temporary test pack)",
                        Component.translatable("item.racmc.clank_heli_pack"));
            }
            case THRUSTER -> {
                return Component.translatableWithFallback("screen.racmc.gear.test_worn", "%s (temporary test pack)",
                        Component.translatable("item.racmc.clank_thruster_pack"));
            }
            case NONE -> {}
        }
        if (!state.clankUnlocked()) return text("locked");
        return text(state.back().worn() ? "worn" : state.returnPending() ? "return_pending"
                : state.clankInInventory() ? "available" : state.rewardPending() ? "reward_pending" : "missing_clank");
    }

    static boolean backEnabled(Rac1GearState state, boolean eligiblePlayer) {
        return eligiblePlayer && state != null && state.allowsBackRequest(!state.wearingClank());
    }

    private static final class Panel {
        private static final Component AERIAL = text("aerial");
        private static final Component AERIAL_OPTIONS = text("aerial_options");
        private static final Component HYDRO = text("hydro");
        private static final Component BELT = text("belt");
        private static final Component BOLT_GRABBER = Component.translatable("item.racmc.bolt_grabber");
        private static final Component BELT_SECOND = Component.translatable("screen.racmc.gear.belt_slot", 2);
        private static final Component COMING_LATER = text("coming_later");

        boolean open;
        int consumedButton = -1;
        int consumedKey = Integer.MIN_VALUE;
        Rac1GearState state;
        Availability boltAvailability;
        Component status;
        Rac1GearPanelLayout layout;
        int laterHeight;
        final Button gearTab = Button.builder(text("title"), button -> open = true)
                .bounds(0, 0, TAB_WIDTH, 20).build();
        final Button inventoryTab = Button.builder(text("inventory"), button -> open = false)
                .bounds(0, 0, TAB_WIDTH, 20).build();
        final Button back = Button.builder(text("equip"), button -> {
            if (state != null) Rac1GearClient.requestBack(state, !state.wearingClank());
        }).bounds(0, 0, 144, 20).build();
        final Button boltGrabber = Button.builder(beltText("waiting"), button ->
                Rac1BeltClient.request(Rac1BeltGadget.BOLT_GRABBER, boltAvailability == Availability.READY))
                .createNarration(message -> Component.empty().append(BOLT_GRABBER).append(": ").append(message.get()))
                .bounds(0, 0, Rac1GearPanelLayout.SLOT_TEXT_WIDTH, Rac1GearPanelLayout.BELT_BUTTON_HEIGHT).build();
        final List<Button> inventoryControls = List.of(gearTab);
        // Reserved slots are labels/boxes drawn below, not listeners, widgets, or menu slots.
        final List<Button> gearControls = List.of(inventoryTab, back, boltGrabber);

        private Button nextFocus(Object current, boolean reverse) {
            int from = gearControls.indexOf(current);
            if (from < 0) return inventoryTab;
            for (int offset = 1; offset <= gearControls.size(); offset++) {
                int index = Math.floorMod(from + (reverse ? -offset : offset), gearControls.size());
                var button = gearControls.get(index);
                if (button.active) return button;
            }
            return inventoryTab;
        }

        void layout(AbstractContainerScreen<?> screen) {
            boolean creative = screen instanceof CreativeModeInventoryScreen;
            gearTab.setPosition(screen.getGuiLeft() + 90, Math.max(0, screen.getGuiTop() - (creative ? 49 : 20)));
            gearTab.visible = !open && (creative ? ((CreativeModeInventoryScreen) screen).isInventoryOpen()
                    : !(screen.width < 379 && ((InventoryScreen) screen).getRecipeBookComponent().isVisible()));
            gearTab.active = screen.getMenu().getCarried().isEmpty();
            inventoryTab.visible = open;
            back.visible = open;
            boltGrabber.visible = open;
            if (!open) return;

            state = Rac1GearClient.selfState().orElse(null);
            status = backStatus(state);
            var minecraft = Minecraft.getInstance();
            var player = minecraft.player;
            boolean eligible = player != null && player.isAlive() && !player.isSpectator();
            back.active = backEnabled(state, eligible);
            back.setMessage(text(state != null && state.wearingClank() ? "unequip" : "equip"));
            var belt = Rac1BeltClient.selfState();
            boltAvailability = belt == null ? null : belt.boltGrabber().availability();
            boltGrabber.active = eligible && (boltAvailability == Availability.READY
                    || boltAvailability == Availability.EQUIPPED);
            boltGrabber.setMessage(beltText(boltAvailability == null ? "waiting" : switch (boltAvailability) {
                case MISSING -> "missing";
                case READY -> "equip";
                case EQUIPPED -> "unequip";
                case RETURNING -> "returning";
            }));
            if (!(screen.getFocused() instanceof Button focus) || !gearControls.contains(focus) || !focus.active)
                screen.setFocused(inventoryTab);

            Font font = minecraft.font;
            int laterLines = slotLines(font, COMING_LATER);
            laterHeight = laterLines * font.lineHeight;
            layout = Rac1GearPanelLayout.of(screen.width, screen.height, font.lineHeight,
                    Math.max(3, font.split(status, Rac1GearPanelLayout.BACK_TEXT_WIDTH).size()),
                    Math.max(slotLines(font, HYDRO), slotLines(font, AERIAL) + slotLines(font, AERIAL_OPTIONS)),
                    font.split(BELT, Rac1GearPanelLayout.BACK_TEXT_WIDTH).size(),
                    Math.max(slotLines(font, BOLT_GRABBER), slotLines(font, BELT_SECOND)), laterLines);
            inventoryTab.setPosition(layout.panel().x(), layout.panel().y() - Rac1GearPanelLayout.TAB_HEIGHT);
            back.setPosition(layout.backButton().x(), layout.backButton().y());
            boltGrabber.setPosition(layout.beltButton().x(), layout.beltButton().y());
        }

        private static Component beltText(String key) {
            return text("belt_" + key);
        }

        private static int slotLines(Font font, Component label) {
            return font.split(label, Rac1GearPanelLayout.SLOT_TEXT_WIDTH).size();
        }

        void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            var font = Minecraft.getInstance().font;
            var panel = layout.panel();
            box(graphics, panel, 0xff202732, 0xff718197);
            graphics.drawCenteredString(font, text("title"), panel.x() + panel.width() / 2, panel.y() + 8, 0xffffff);
            var backBox = layout.back();
            box(graphics, backBox, 0xff181e26, 0xff536173);
            int textX = layout.backButton().x();
            graphics.drawString(font, text("back"), textX, backBox.y() + 5, 0xffffff);
            graphics.drawWordWrap(font, status, textX, backBox.y() + Rac1GearPanelLayout.BACK_STATUS_OFFSET,
                    Rac1GearPanelLayout.BACK_TEXT_WIDTH, 0xd4dce6);

            reserved(graphics, font, layout.aerial(), AERIAL, AERIAL_OPTIONS);
            reserved(graphics, font, layout.hydro(), HYDRO, null);
            graphics.drawWordWrap(font, BELT, textX, layout.beltTitleY(), Rac1GearPanelLayout.BACK_TEXT_WIDTH, 0xffffff);
            var boltBox = layout.beltFirst();
            box(graphics, boltBox, 0xff181e26, 0xff536173);
            graphics.drawWordWrap(font, BOLT_GRABBER, boltBox.x() + Rac1GearPanelLayout.SLOT_TEXT_INSET,
                    boltBox.y() + Rac1GearPanelLayout.SLOT_TEXT_INSET, Rac1GearPanelLayout.SLOT_TEXT_WIDTH, 0xffffff);
            reserved(graphics, font, layout.beltSecond(), BELT_SECOND, null);
            for (Button button : gearControls) button.render(graphics, mouseX, mouseY, delta);
        }

        private void reserved(GuiGraphics graphics, Font font, Rac1GearPanelLayout.Box slot,
                              Component title, Component details) {
            box(graphics, slot, 0xff1b212a, 0xff475160);
            int x = slot.x() + Rac1GearPanelLayout.SLOT_TEXT_INSET;
            int y = slot.y() + Rac1GearPanelLayout.SLOT_TEXT_INSET;
            int width = Rac1GearPanelLayout.SLOT_TEXT_WIDTH;
            graphics.drawWordWrap(font, title, x, y, width, 0xc8d0da);
            if (details != null)
                graphics.drawWordWrap(font, details, x, y + slotLines(font, title) * font.lineHeight, width, 0xa3aebd);
            graphics.drawWordWrap(font, COMING_LATER, x,
                    slot.bottom() - Rac1GearPanelLayout.SLOT_TEXT_INSET - laterHeight, width, 0xa3aebd);
        }

        private static void box(GuiGraphics graphics, Rac1GearPanelLayout.Box box, int fill, int outline) {
            graphics.fill(box.x(), box.y(), box.right(), box.bottom(), fill);
            graphics.renderOutline(box.x(), box.y(), box.width(), box.height(), outline);
        }
    }
}
