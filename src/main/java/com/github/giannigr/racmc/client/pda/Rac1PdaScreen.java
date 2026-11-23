package com.github.giannigr.racmc.client.pda;

import com.github.giannigr.racmc.client.RacMcClient;
import com.github.giannigr.racmc.client.screen.Rac1ScreenLayout;
import com.github.giannigr.racmc.item.RacMcItems;
import com.github.giannigr.racmc.item.weapon.BlasterAmmo;
import com.github.giannigr.racmc.shop.*;
import java.util.UUID;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * Explicit Minecraft UI adaptation, not a replica of the original remote vendor.
 * World stays live/visible. No kiosk, camera, motion, speech or imported cue dependencies.
 */
public final class Rac1PdaScreen extends Screen {
    private final Rac1PdaClientFlow flow;
    private final InteractionHand hand;
    private final Object player,level;
    private final ItemStack stack;
    private final int slot;
    private final long mediaGeneration;
    private Button minus,plus,request,confirm,cancel;
    private int quantity=1,left,top,panelWidth;
    private boolean removed;

    public Rac1PdaScreen(UUID token,InteractionHand hand) {
        super(Component.translatable("screen.racmc.pda.title"));
        this.flow=new Rac1PdaClientFlow(token); this.hand=hand;
        var mc=Minecraft.getInstance();
        this.player=mc.player; this.level=mc.level; this.slot=mc.player.getInventory().selected;
        this.stack=mc.player.getItemInHand(hand); this.mediaGeneration=RacMcClient.media().generation();
    }
    public UUID token() { return flow.token(); }
    public void reply(Rac1PdaNetwork.Reply packet) {
        if (flow.receive(packet)) {
            if (flow.mode()==Rac1PdaClientFlow.Mode.CLOSED) onClose();
            else updateButtons();
        }
    }
    @Override protected void init() {
        KeyMapping.releaseAll();
        panelWidth=Rac1ScreenLayout.contentWidth(width);
        left=(width-panelWidth)/2; top=Math.max(0,(height-228)/2);
        int half=(panelWidth-18)/2;
        minus=addRenderableWidget(Button.builder(Component.literal("−"),b -> adjust(-1)).bounds(left+6,top+94,32,20).build());
        plus=addRenderableWidget(Button.builder(Component.literal("+"),b -> adjust(1)).bounds(left+panelWidth-38,top+94,32,20).build());
        request=addRenderableWidget(Button.builder(Component.translatable("screen.racmc.pda.quote"),
                b -> { Rac1PdaClientPackets.send(flow.request(quantity)); updateButtons(); })
                .bounds(left+6,top+178,half,20).build());
        confirm=addRenderableWidget(Button.builder(Component.translatable("screen.racmc.pda.confirm"),
                b -> { Rac1PdaClientPackets.send(flow.confirm()); updateButtons(); })
                .bounds(left+12+half,top+178,half,20).build());
        cancel=addRenderableWidget(Button.builder(Component.translatable("screen.racmc.pda.cancel_quote"),
                b -> { flow.cancelQuote(); updateButtons(); }).bounds(left+6,top+202,half,20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.racmc.pda.close"),
                b -> onClose()).bounds(left+12+half,top+202,half,20).build());
        setInitialFocus(request);
        updateButtons();
    }
    private void adjust(int amount) {
        if (flow.editable()) quantity=Math.max(1,Math.min(200,quantity+amount));
        updateButtons();
    }
    private void updateButtons() {
        if (request==null) return;
        minus.active=flow.editable() && quantity>1;
        plus.active=flow.editable() && quantity<200;
        request.active=flow.editable();
        confirm.active=flow.canConfirm();
        cancel.active=flow.mode()==Rac1PdaClientFlow.Mode.CONFIRM || flow.mode()==Rac1PdaClientFlow.Mode.FAILURE;
    }
    private boolean valid() {
        return minecraft!=null && minecraft.player==player && minecraft.level==level
                && minecraft.player!=null && minecraft.player.isAlive() && !minecraft.player.isRemoved()
                && !minecraft.player.isSpectator() && minecraft.player.containerMenu==minecraft.player.inventoryMenu
                && minecraft.player.getInventory().selected==slot && minecraft.player.getItemInHand(hand)==stack
                && stack.is(RacMcItems.PDA.get()) && stack.getCount()==1
                && RacMcClient.media().isReady() && !RacMcClient.media().isChecking()
                && RacMcClient.media().generation()==mediaGeneration;
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void tick() {
        if (!valid()) { onClose(); return; }
        Rac1PdaClientPackets.send(flow.tick()); updateButtons();
    }
    /** Deliberately no full-screen dim/blur or world snapshot. */
    @Override public void renderBackground(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) {}
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) {
        if (!valid()) return;
        graphics.fill(left,top,left+panelWidth,top+228,0xe8202830);
        graphics.drawCenteredString(font,title,width/2,top+7,0xffffff);
        line(graphics,Component.translatable("screen.racmc.pda.adaptation"),20,0xffd878);
        line(graphics,Component.translatable("screen.racmc.pda.product"),40,0xffffff);
        line(graphics,Component.translatable("screen.racmc.pda.balance",
                minecraft.player.getPersistentData().getLong("racmcBolts")),54,0xe0e0e0);
        line(graphics,Component.translatable("screen.racmc.pda.ammo",BlasterAmmo.count(minecraft.player)),66,0xe0e0e0);
        line(graphics,Component.translatable("screen.racmc.pda.unit",Rac1PdaPurchases.UNIT_PRICE),78,0xe0e0e0);
        graphics.drawCenteredString(font,Component.translatable("screen.racmc.pda.quantity",quantity),width/2,top+100,0xffffff);
        var q=flow.quote();
        if (q!=null) line(graphics,Component.translatable("screen.racmc.pda.total",q.quantity(),q.price()),122,0xffd878);
        else line(graphics,Component.translatable("screen.racmc.pda.estimate",quantity*(long)Rac1PdaPurchases.UNIT_PRICE),122,0xaaaaaa);
        int y=top+138;
        for (var text:font.split(status(),panelWidth-12)) {
            if (y+font.lineHeight>top+174) break;
            graphics.drawString(font,text,left+6,y,0xffffff); y+=font.lineHeight;
        }
        super.render(graphics,mouseX,mouseY,partialTick);
        // Confirmation is never available before this exact server quote has been submitted.
        if (q!=null) flow.presented(q.id());
        updateButtons();
    }
    private void line(GuiGraphics graphics,Component text,int y,int color) {
        graphics.drawString(font,font.substrByWidth(text,panelWidth-12).getString(),left+6,top+y,color);
    }
    private Component status() {
        return switch (flow.mode()) {
            case WAIT_QUOTE,WAIT_BUY -> Component.translatable("screen.racmc.pda.wait");
            case UNCERTAIN -> Component.translatable("screen.racmc.pda.uncertain");
            case CONFIRM -> Component.translatable("screen.racmc.pda.review");
            case CLOSED -> Component.translatable("screen.racmc.pda.closed");
            default -> flow.lastPurchase()!=null
                    ? Component.translatable("screen.racmc.pda.purchased",flow.lastPurchase().quantity(),flow.lastPurchase().price())
                    : flow.outcome()!=null ? Component.translatable("screen.racmc.pda.result."+flow.outcome().name().toLowerCase(java.util.Locale.ROOT))
                    : Component.translatable("screen.racmc.pda.ready");
        };
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers) {
        if ((key==GLFW.GLFW_KEY_ENTER || key==GLFW.GLFW_KEY_KP_ENTER || key==GLFW.GLFW_KEY_SPACE)
                && !flow.activationDown(key)) return true;
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public boolean keyReleased(int key,int scan,int modifiers) {
        flow.release(key); return super.keyReleased(key,scan,modifiers);
    }
    @Override public void removed() {
        if (!removed) { removed=true; Rac1PdaClientPackets.send(flow.close()); }
    }
    @Override public void onClose() {
        removed(); super.onClose();
    }
}
