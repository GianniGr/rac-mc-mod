package com.github.giannigr.racmc.client.pda;

import com.github.giannigr.racmc.asset.pda.Rac1PdaRemoteAudio.Role;
import com.github.giannigr.racmc.client.RacMcClient;
import com.github.giannigr.racmc.client.shop.*;
import com.github.giannigr.racmc.item.RacMcItems;
import com.github.giannigr.racmc.shop.*;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/** Original verified remote-mode scene/RTTs; live nonpausing MC world and authoritative commerce. */
public final class Rac1PdaOriginalScreen extends Screen {
    private final Rac1PdaClientFlow flow;
    private final Rac1PdaScreenCommerce commerce;
    private final Rac1CommerceSelection catalog;
    private boolean catalogPresented;
    private final InteractionHand hand;
    private final Object owner,level;
    private final ItemStack stack;
    private final int slot;
    private final long generation;
    private CompletableFuture<Rac1PdaOriginalSource> loading;
    private Rac1PdaOriginalSource source;
    private ResourceLocation scene,presenterImage,tickerImage;
    private Rac1PdaOriginalPanels panels;
    private final Rac1ShopMouseConfirmation consent=new Rac1ShopMouseConfirmation();
    private final List<Rac1PdaOriginalSound> sounds=new ArrayList<>();
    private String error;
    private long frames;
    private int held,heldTicks;
    private boolean dirty=true,removed,deliberateQuote;
    public Rac1PdaOriginalScreen(UUID token,InteractionHand hand) {
        super(Component.literal("Gadgetron PDA"));
        this.hand=hand;
        var bomb=com.github.giannigr.racmc.item.weapon.Rac1AmmoPolicy.BOMB_GLOVE;
        commerce=new Rac1PdaScreenCommerce(token,bomb.weapon().isPresent() && bomb.ammo().isPresent());
        flow=commerce.flow(); catalog=commerce.catalog();
        var mc=Minecraft.getInstance();
        owner=mc.player; level=mc.level; slot=mc.player.getInventory().selected;
        stack=mc.player.getItemInHand(hand); generation=RacMcClient.media().generation();
    }
    public UUID token() { return flow.token(); }
    @Override protected void init() {
        KeyMapping.releaseAll(); Rac1PdaClosingAudio.clear();
        if (loading==null && error==null) try { loading=Rac1PdaOriginalSource.request(); }
        catch (RuntimeException failure) { fail(failure); }
    }
    private boolean context() {
        return minecraft!=null && minecraft.player==owner && minecraft.level==level && minecraft.player!=null
                && minecraft.player.isAlive() && !minecraft.player.isRemoved() && !minecraft.player.isSpectator()
                && minecraft.player.containerMenu==minecraft.player.inventoryMenu
                && minecraft.player.getInventory().selected==slot && minecraft.player.getItemInHand(hand)==stack
                && stack.is(RacMcItems.PDA.get()) && stack.getCount()==1 && RacMcClient.media().isReady()
                && !RacMcClient.media().isChecking() && RacMcClient.media().generation()==generation;
    }
    private void play(Role role) {
        if (source==null || !context() || !minecraft.isWindowActive()) return;
        try {
            var manager=minecraft.getSoundManager();
            sounds.removeIf(sound -> { if (manager.isActive(sound)) return false; sound.close(); return true; });
            if (sounds.size()>=16) { var old=sounds.removeFirst(); old.close(); manager.stop(old); }
            var sound=new Rac1PdaOriginalSound(source.audio().clips().get(role));
            sounds.add(sound); manager.play(sound);
        } catch (RuntimeException failure) { fail(failure); }
    }
    public void reply(Rac1PdaNetwork.Reply packet) {
        var previous=flow.mode();
        if (!commerce.receive(packet)) return;
        org.slf4j.LoggerFactory.getLogger(Rac1PdaOriginalScreen.class).info(
                "Original PDA receipt session={} seq={} selected={} result={} quotedOffer={}",
                token(),packet.sequence(),catalog.offer(),flow.outcome(),
                packet.result().quote()==null ? null:packet.result().quote().offer());
        if (flow.mode()==Rac1PdaClientFlow.Mode.CLOSED) { onClose(); return; }
        var cue=Rac1PdaOriginalCuePolicy.receipt(previous,flow.outcome(),deliberateQuote,packet.result().replay());
        if (cue!=null) play(cue);
        if (flow.mode()!=Rac1PdaClientFlow.Mode.WAIT_QUOTE) deliberateQuote=false;
        consent.clear(); catalogPresented=false; dirty=true;
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics graphics,int x,int y,float partial) {}
    @Override public void tick() {
        if (!context()) { onClose(); return; }
        var before=flow.mode();
        Rac1PdaClientPackets.send(flow.tick());
        if (before!=flow.mode()) { dirty=true; consent.clear(); }
        if (!minecraft.isWindowActive()) { held=heldTicks=0; stopSounds(); consent.clear(); }
        if (source==null && error==null && loading!=null && loading.isDone()) {
            try {
                source=loading.join();
                if (source.graphics().voice()!=null) throw new IllegalStateException("PDA speech import forbidden");
                panels=new Rac1PdaOriginalPanels(source,bound -> java.util.concurrent.ThreadLocalRandom.current().nextInt(bound));
                play(Role.OPEN); dirty=true;
            } catch (RuntimeException failure) { fail(failure); }
        }
        if (source!=null && error==null) {
            if (frames==3) play(Role.SCREENS_ON);
            frames+=3; panels.advance(3);
            if (held!=0 && (++heldTicks>16 || heldTicks>5 && heldTicks%3==0)) adjust(held);
            try {
                // Like the kiosk, update RTTs on the screen tick, before GUI submission.
                presenterImage=upload(presenterImage,panels.presenterFrame(),"pda_remote_presenter");
                tickerImage=upload(tickerImage,panels.tickerFrame(),"pda_remote_ticker");
            } catch (RuntimeException failure) { fail(failure); }
        }
    }
    private void fail(Throwable failure) {
        error="Original PDA presentation unavailable; Esc closes. No fallback claimed.";
        org.slf4j.LoggerFactory.getLogger(Rac1PdaOriginalScreen.class).warn(error,failure);
        stopSounds();
    }
    private Rac1ShopPreviewDrawing.Presentation presentation() {
        var mode=switch (flow.mode()) {
            case CONFIRM,WAIT_BUY -> Rac1ShopClientFlow.Mode.CONFIRM;
            case FAILURE -> flow.outcome()==Rac1ShopPurchases.Status.INSUFFICIENT ? Rac1ShopClientFlow.Mode.DENIED
                    :flow.outcome()==Rac1ShopPurchases.Status.STOCK_CAP ? Rac1ShopClientFlow.Mode.MAXED:Rac1ShopClientFlow.Mode.FAILURE;
            default -> Rac1ShopClientFlow.Mode.BROWSE;
        };
        var q=flow.quote();
        return new Rac1ShopPreviewDrawing.Presentation(mode,q==null ? commerce.quantity():q.quantity(),q==null ? 0:q.price());
    }
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partial) {
        if (!context()) return;
        if (source!=null && error==null) try {
            if (dirty || scene==null) {
                graphics.flush(); // raw offscreen GL must not overtake queued GUI work
                scene=upload(scene,catalog.blaster() ? Rac1ShopPreviewDrawing.renderRemote(source.graphics(),presentation())
                        :Rac1ShopPreviewDrawing.renderCatalog(source.graphics(),presentation(),true),"pda_remote_scene"); dirty=false;
            }
            int sh=source.graphics().pal() ? 448:416;
            double scale=Math.min(width/512.0,Math.max(1,height-footerHeight())/(double)sh);
            int w=(int)(512*scale),h=(int)(sh*scale),x=(width-w)/2,y=(height-footerHeight()-h)/2;
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
            graphics.blit(scene,x,y,w,h,0,0,512,sh,512,sh);
            for (int screen:new int[]{0,2}) {
                var rect=Rac1ShopPreviewDrawing.monitor(source.graphics(),screen);
                graphics.blit(screen==0 ? tickerImage:presenterImage,x+(int)(rect.x()*scale),y+(int)(rect.y()*scale),
                        (int)(rect.width()*scale),(int)(rect.height()*scale),0,0,
                        Math.min(512,(int)Math.ceil(rect.width())),Math.min(128,(int)Math.ceil(rect.height())),512,128);
            }
            com.mojang.blaze3d.systems.RenderSystem.disableBlend();
            drawPinnedBolts(graphics);
            graphics.fill(0,height-footerHeight(),width,height,0xa0181818);
            catalogPresented=Rac1CommerceCatalogDrawing.drawAt(graphics,font,catalog,flow.quote(),switchable(),
                    height-footerHeight(),footerHeight()-font.lineHeight-4);
            if (catalogPresented && flow.quote()!=null) {
                flow.presented(flow.quote().id()); consent.presented(flow.quote().id(),net.minecraft.Util.getMillis());
            }
        } catch (RuntimeException failure) { fail(failure); }
        String status=error!=null ? error:source==null ? "Loading verified original PDA; Esc closes"
                :"MC: live world / private confirmation | "+flow.mode()
                +(flow.outcome()==null ? "":" / "+flow.outcome())+" | bolts "+minecraft.player.getPersistentData().getLong("racmcBolts")
                +(flow.outcome()==Rac1ShopPurchases.Status.STALE_OFFER ? " | Requires selected weapon; or inventory changed. Backspace: browse":"");
        graphics.drawString(font,status,4,height-font.lineHeight-2,0xffffff);
    }
    public boolean ownsPinnedBolts() { return source!=null && error==null && context(); }
    private void drawPinnedBolts(GuiGraphics graphics) {
        var content=RacMcClient.assets().content().orElse(null);
        if (content==null || !com.github.giannigr.racmc.asset.hud.Rac1BoltHudDefinition.complete(content.hud())) return;
        var p=content.hud().properties();
        String value=Long.toString(minecraft.player.getPersistentData().getLong("racmcBolts"));
        int width=p.get("digitWidth")*value.length(),x=graphics.guiWidth()*2-32,y=p.get("top"),middle=x-28-width;
        graphics.pose().pushPose(); graphics.pose().scale(.5f,.5f,1);
        try {
            com.github.giannigr.racmc.client.render.Rac1HudDrawing.sprite(graphics,content,"bolt_panel",1,x-12,y,32,32,true,.7f);
            com.github.giannigr.racmc.client.render.Rac1HudDrawing.sprite(graphics,content,"bolt_panel",0,middle,y,width+16,32,false,.7f);
            com.github.giannigr.racmc.client.render.Rac1HudDrawing.sprite(graphics,content,"bolt_panel",1,middle-32,y,32,32,false,.7f);
            com.github.giannigr.racmc.client.render.Rac1HudDrawing.sprite(graphics,content,"bolt",(int)(frames%60)/2,x-32,y,32,32,false,1);
            int tx=x+p.get("textX"),ty=y+p.get("textY");
            com.github.giannigr.racmc.client.render.Rac1HudDrawing.textRight(graphics,content,"regular",value,tx+1,ty+1,0x80000000);
            com.github.giannigr.racmc.client.render.Rac1HudDrawing.textRight(graphics,content,"regular",value,tx,ty,p.get("textOpaque"));
            boolean dot=com.github.giannigr.racmc.client.Rac1BoltHudLayout.dot(minecraft.options.languageCode);
            for (int k=3;k<value.length();k+=3) {
                int sx=x+p.get(dot ? "dotX":"separatorX")-p.get("digitWidth")*k,sy=y+p.get(dot ? "dotY":"separatorY");
                com.github.giannigr.racmc.client.render.Rac1HudDrawing.textRight(graphics,content,"regular",dot ? ".":"'",sx+2,sy+2,0x80000000);
                com.github.giannigr.racmc.client.render.Rac1HudDrawing.textRight(graphics,content,"regular",dot ? ".":"'",sx,sy,p.get("textOpaque"));
            }
        } finally { graphics.pose().popPose(); }
    }
    private ResourceLocation upload(ResourceLocation previous,Rac1ShopOrderedGpu.Readback frame,String name) {
        var image=new NativeImage(frame.width(),frame.height(),false);
        DynamicTexture texture=null;
        try {
            byte[] rgba=frame.rgba();
            for (int y=0;y<frame.height();y++) for (int x=0;x<frame.width();x++) {
                int p=(y*frame.width()+x)*4;
                image.setPixelRGBA(x,y,(rgba[p+3]&255)<<24|(rgba[p+2]&255)<<16|(rgba[p+1]&255)<<8|(rgba[p]&255));
            }
            texture=new DynamicTexture(image);
            var next=minecraft.getTextureManager().register(name,texture);
            if (previous!=null) minecraft.getTextureManager().release(previous);
            return next;
        } catch (RuntimeException failure) { if (texture!=null) texture.close(); else image.close(); throw failure; }
    }
    private void activate(boolean mouse) {
        if (source==null || error!=null || !context() || !catalogPresented) return;
        if (flow.mode()==Rac1PdaClientFlow.Mode.FAILURE) { cancelQuote(); return; }
        if (flow.editable()) {
            deliberateQuote=true; play(Role.SELECT);
            Rac1PdaClientPackets.send(commerce.request()); dirty=true;
        } else if (flow.canConfirm() && (!mouse || consent.approve(flow.quote().id(),net.minecraft.Util.getMillis()))) {
            Rac1PdaClientPackets.send(commerce.confirm()); dirty=true;
        }
    }
    private void cancelQuote() {
        if (flow.mode()==Rac1PdaClientFlow.Mode.CONFIRM || flow.mode()==Rac1PdaClientFlow.Mode.FAILURE) {
            play(Role.SELECT); flow.cancelQuote(); consent.clear(); dirty=true;
        }
    }
    private void adjust(int delta) {
        if (source==null || flow.mode()!=Rac1PdaClientFlow.Mode.CONFIRM) return;
        var command=commerce.adjust(delta,minecraft.player.getPersistentData().getLong("racmcBolts"));
        if (command==null) return;
        consent.clear(); deliberateQuote=false;
        play(Role.CURSOR); Rac1PdaClientPackets.send(command); catalogPresented=false; dirty=true;
    }
    private boolean switchable() {
        return flow.mode()==Rac1PdaClientFlow.Mode.BROWSE;
    }
    private int footerHeight() {
        return Math.min(Rac1CommerceCatalogDrawing.height(font,width,catalog,flow.quote(),switchable())
                +font.lineHeight+4,Math.max(1,height/2));
    }
    private void moveCatalog(int direction) {
        if (!switchable() || source==null || error!=null || !commerce.move(direction)) return;
        held=heldTicks=0;
        consent.clear(); catalogPresented=false; deliberateQuote=false; dirty=true;
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers) {
        if (key==GLFW.GLFW_KEY_ESCAPE) { closeDeliberately(); return true; }
        if (key==GLFW.GLFW_KEY_ENTER || key==GLFW.GLFW_KEY_KP_ENTER) {
            if (flow.activationDown(key)) activate(false); return true;
        }
        if (key==GLFW.GLFW_KEY_LEFT || key==GLFW.GLFW_KEY_RIGHT) {
            if (flow.activationDown(key)) {
                int direction=key==GLFW.GLFW_KEY_LEFT ? -1:1;
                if (switchable()) moveCatalog(direction);
                else if (held==0) { held=direction; heldTicks=0; adjust(held); }
            }
            return true;
        }
        if (key==GLFW.GLFW_KEY_BACKSPACE) { if (flow.activationDown(key)) cancelQuote(); return true; }
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public boolean keyReleased(int key,int scan,int modifiers) {
        flow.release(key);
        if (key==GLFW.GLFW_KEY_LEFT || key==GLFW.GLFW_KEY_RIGHT) held=heldTicks=0;
        return super.keyReleased(key,scan,modifiers);
    }
    private boolean inside(int monitor,double mx,double my) {
        if (source==null) return false;
        int sh=source.graphics().pal() ? 448:416;
        double scale=Math.min(width/512.0,Math.max(1,height-footerHeight())/(double)sh);
        var r=Rac1ShopPreviewDrawing.monitor(source.graphics(),monitor);
        double x=(width-512*scale)/2+r.x()*scale,y=(height-footerHeight()-sh*scale)/2+r.y()*scale;
        return mx>=x && my>=y && mx<x+r.width()*scale && my<y+r.height()*scale;
    }
    @Override public void mouseMoved(double x,double y) {
        if (!inside(3,x,y)) consent.movedOutsideBuy(); super.mouseMoved(x,y);
    }
    @Override public boolean mouseClicked(double x,double y,int button) {
        if (button!=0 || !consent.press()) return super.mouseClicked(x,y,button);
        if (inside(3,x,y)) { activate(true); return true; }
        if (inside(4,x,y)) {
            if (flow.mode()==Rac1PdaClientFlow.Mode.CONFIRM || flow.mode()==Rac1PdaClientFlow.Mode.FAILURE) cancelQuote();
            else closeDeliberately();
            return true;
        }
        return super.mouseClicked(x,y,button);
    }
    @Override public boolean mouseReleased(double x,double y,int button) {
        if (button==0) consent.release(); return super.mouseReleased(x,y,button);
    }
    private void stopSounds() {
        if (minecraft==null) return;
        var manager=minecraft.getSoundManager();
        for (var sound:sounds) { sound.close(); manager.stop(sound); } sounds.clear();
    }
    private void closeDeliberately() {
        var audio=source==null ? null:source.audio();
        boolean valid=context() && audio!=null;
        onClose();
        if (valid) Rac1PdaClosingAudio.start(audio,() -> context() && Minecraft.getInstance().isWindowActive()
                && Minecraft.getInstance().screen==null);
    }
    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public void onClose() { removed(); super.onClose(); }
    @Override public void removed() {
        if (removed) return;
        removed=true; Rac1PdaClientPackets.send(flow.close()); stopSounds();
        if (panels!=null) { panels.close(); panels=null; }
        if (minecraft!=null) for (var texture:new ResourceLocation[]{scene,presenterImage,tickerImage})
            if (texture!=null) minecraft.getTextureManager().release(texture);
        source=null; scene=presenterImage=tickerImage=null;
    }
}
