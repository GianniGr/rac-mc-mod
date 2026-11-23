package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.block.Rac1VendorBlockEntity;
import com.github.giannigr.racmc.client.RacMcClient;
import com.github.giannigr.racmc.item.RacMcItems;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.UUID;
import com.github.giannigr.racmc.shop.*;
import org.lwjgl.glfw.GLFW;

/** Per-viewer original menu drawing/input; all commerce goes through server-issued quotes. */
public final class Rac1ShopPreviewScreen extends Screen {
    private static final org.slf4j.Logger LOGGER=org.slf4j.LoggerFactory.getLogger(Rac1ShopPreviewScreen.class);
    private final Rac1VendorBlockEntity vendor;
    private final Path path;
    private final Object cache;
    private final String discKey;
    private final long mediaGeneration;
    private CompletableFuture<Rac1ShopPreviewSource> loading;
    private Rac1ShopPreviewSource source;
    private Rac1ShopPreviewOffer offer;
    private Rac1ShopPreviewDrawing.Presentation drawnUi;
    private final Rac1ShopQuantity quantity;
    private long quantityTicks;
    private int drawnQuantity;
    private Rac1ShopPreviewDrawing.QuantityTarget quantityTarget;
    private ResourceLocation quantityTexture;
    private ResourceLocation texture;
    private ResourceLocation tickerTexture;
    private ResourceLocation presenterTexture;
    private long presenterFrames;
    private Rac1ShopTicker ticker;
    private Rac1ShopTickerDrawing.Target tickerTarget;
    private Rac1ShopActorDrawing.PresenterTarget presenterTarget;
    private UUID textureQuote;
    private String error;
    private final Rac1ShopClientFlow flow;
    private final Rac1CommerceSelection catalog;
    private boolean catalogPresented;
    private final Rac1ShopMouseConfirmation mouseConfirmation=new Rac1ShopMouseConfirmation();
    private final Rac1ShopVoicePlayback voice;
    private Rac1ShopPresenter presenter;
    private boolean pendingVoiceReceipt;
    private String voiceIssue;
    private boolean dirty=true;
    private int held,heldFrames;
    private final long clickNanos;
    private long sourceReadyNanos,gpuPreparationNanos,gpuPanelsReadyNanos,textureReadyNanos;
    private boolean preparedAtOpen,usableLogged;
    public Rac1ShopPreviewScreen(Rac1VendorBlockEntity vendor,Path path,Object cache,String discKey,UUID token,long clickNanos) {
        super(Component.literal("Gadgetron"));
        this.vendor=vendor; this.path=path; this.cache=cache; this.discKey=discKey;
        this.mediaGeneration=RacMcClient.media().generation();
        this.clickNanos=clickNanos;
        var bomb=com.github.giannigr.racmc.item.weapon.Rac1AmmoPolicy.BOMB_GLOVE;
        commerce=new Rac1ShopScreenCommerce(token,bomb.weapon().isPresent() && bomb.ammo().isPresent());
        flow=commerce.flow(); catalog=commerce.catalog(); quantity=commerce.quantity();
        // Owner-accepted provisional clock; all sound stays owned by this private screen.
        this.presenterSession=new Rac1ShopPresenterSession(() -> minecraft!=null && minecraft.screen==this
                && error==null && flow.mode()!=Rac1ShopClientFlow.Mode.CLOSED && valid(),
                Rac1ShopVoiceSound.backend(() -> minecraft.getSoundManager()));
        this.voice=presenterSession.voice();
    }
    public UUID token() { return flow.token(); }
    private final Rac1ShopPresenterSession presenterSession;
    private final Rac1ShopScreenCommerce commerce;
    private Rac1ShopDeniedSound deniedSound;
    private void stopDenied() {
        if (deniedSound!=null) {
            var sound=deniedSound; deniedSound=null;
            sound.close(); minecraft.getSoundManager().stop(sound);
        }
    }
    private void deniedOpening() {
        stopDenied();
        if (!valid() || !minecraft.isWindowActive() || source==null || source.deniedCue()==null) return;
        try {
            deniedSound=new Rac1ShopDeniedSound(source.deniedCue());
            minecraft.getSoundManager().play(deniedSound);
        } catch (RuntimeException failure) {
            stopDenied();
            LOGGER.warn("Original denied cue unavailable; not retrying or substituting",failure);
        }
    }
    Rac1VendorBlockEntity animationTarget() { return valid() ? vendor:null; }
    private boolean compositionVisible() {
        return texture!=null && error==null && Rac1ShopObserverClient.unfolded(vendor);
    }
    public static boolean suppressesWorld(Rac1VendorBlockEntity drawn) {
        var mc=net.minecraft.client.Minecraft.getInstance();
        return mc.screen instanceof Rac1ShopPreviewScreen shop
                && Rac1ShopAudience.suppressWorld(drawn,shop.vendor,shop.compositionVisible());
    }
    public void reply(Rac1ShopNetwork.Reply packet) {
        var previous=flow.mode();
        long denied=flow.deniedOpening();
        if (commerce.receive(packet,minecraft.player.getPersistentData().getLong("racmcBolts"))) {
            if (flow.deniedOpening()!=denied && catalog.blaster()) deniedOpening();
            if (Rac1ShopPresenter.purchased(previous,flow.mode(),flow.outcome())) {
                if (catalog.blaster()) {
                    if (presenter!=null) presenter.purchaseAccepted();
                    else pendingVoiceReceipt=true;
                }
            }
            mouseConfirmation.clear(); catalogPresented=false;
            dirty=true; if (flow.mode()==Rac1ShopClientFlow.Mode.CLOSED) onClose();
        }
    }
    @Override protected void init() {
        if (loading==null && error==null) Rac1ShopFrameProfile.open();
        KeyMapping.releaseAll();
        if (loading==null && error==null) {
            try {
                loading=Rac1ShopPreviewPreparation.request(path,
                        (com.github.giannigr.racmc.cache.Rac1CacheContent)cache,discKey);
                preparedAtOpen=loading.isDone() && !loading.isCompletedExceptionally();
            } catch (RejectedExecutionException busy) {
                error="Previous preview import still finishing. Esc closes; retry after it finishes.";
            }
        }
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void tick() {
        long profile=Rac1ShopFrameProfile.begin();
        try { tickPanels(); }
        finally { Rac1ShopFrameProfile.end(Rac1ShopFrameProfile.Stage.SCREEN_TICK,profile); }
    }
    private void tickPanels() {
        if (deniedSound!=null && !minecraft.getSoundManager().isActive(deniedSound)) stopDenied();
        voice.tick();
        if (!valid()) { onClose(); return; }
        var previous=flow.mode();
        Rac1ShopClientPackets.send(flow.tick());
        if (flow.mode()!=previous) dirty=true;
        if (flow.mode()==Rac1ShopClientFlow.Mode.CLOSED) { onClose(); return; }
        quantityTicks++;
        if (flow.mode()!=Rac1ShopClientFlow.Mode.CONFIRM && flow.mode()!=Rac1ShopClientFlow.Mode.WAIT_QUOTE
                && flow.mode()!=Rac1ShopClientFlow.Mode.WAIT_BUY) quantity.clear();
        if (!minecraft.isWindowActive()) {
            stopDenied();
            voice.cancel();
            pendingVoiceReceipt=false;
            if (presenter!=null) presenter.suspend();
            held=0; heldFrames=0; mouseConfirmation.clear(); mouseConfirmation.release();
        }
        if (held!=0) for (int i=0;i<3;i++) {
            heldFrames++;
            if (heldFrames>47 || heldFrames>15 && heldFrames%8==0) adjust(held);
        }
        if (flow.mode()==Rac1ShopClientFlow.Mode.CONFIRM) {
            var command=commerce.requote(quantityTicks);
            if (command!=null) {
                textureQuote=null; mouseConfirmation.clear();
                Rac1ShopClientPackets.send(command); dirty=true;
            }
        }
        if (source==null && error==null && loading!=null && loading.isDone()) {
            try { source=loading.join(); sourceReadyNanos=System.nanoTime(); }
            catch (CompletionException failure) {
                LOGGER.warn("Selected-disc Gadgetron preview import failed",failure.getCause());
                error="Selected-disc preview unavailable. Close and review import diagnostics.";
            }
        }
        if (source!=null && error==null) {
            try {
                if (ticker==null) {
                    long panelsProfile=Rac1ShopFrameProfile.begin();
                    gpuPreparationNanos=System.nanoTime();
                    var random=new java.util.SplittableRandom(flow.token().getLeastSignificantBits());
                    ticker=new Rac1ShopTicker(source.shop().sceneTables().led(),source.shop().text(),
                            source.shop().sceneTables().tickerIds(),random::nextInt);
                    tickerTarget=new Rac1ShopTickerDrawing.Target(source.led());
                    presenterTarget=new Rac1ShopActorDrawing.PresenterTarget(source);
                    gpuPanelsReadyNanos=System.nanoTime();
                    Rac1ShopFrameProfile.end(Rac1ShopFrameProfile.Stage.PANEL_CREATE,panelsProfile);
                    try {
                        presenter=presenterSession.attach(source.presenter().animated().animations(),source.voice(),random::nextInt);
                        if (pendingVoiceReceipt) { presenter.purchaseAccepted(); pendingVoiceReceipt=false; }
                        LOGGER.info("Gadgetron provisional header clock: {} English voices, texture barrier={}",
                                source.voice().clips().size(),presenterTarget.usesTextureBarrier());
                    } catch (IllegalArgumentException unavailable) {
                        voice.cancel();
                        voiceIssue=source.voice().issue()!=null ? source.voice().issue():unavailable.getMessage();
                        LOGGER.warn("Provisional Gadgetron voice unavailable: {}",voiceIssue);
                    }
                }
                ticker.advance(3);
                long tickerProfile=Rac1ShopFrameProfile.begin();
                var tickerFrame=tickerTarget.frame(ticker.line());
                Rac1ShopFrameProfile.end(Rac1ShopFrameProfile.Stage.TICKER,tickerProfile);
                var nextTicker=uploadImage(tickerFrame,"racmc_shop_ticker");
                if (tickerTexture!=null) minecraft.getTextureManager().release(tickerTexture);
                tickerTexture=nextTicker;
                presenterFrames+=3;
                Rac1ShopOrderedGpu.Readback presenterFrame;
                long presenterProfile=Rac1ShopFrameProfile.begin();
                if (presenter!=null) {
                    var frame=presenterSession.tick(3,minecraft.isWindowActive());
                    presenterFrame=presenterTarget.frame(frame.sequence(),frame.seconds());
                } else presenterFrame=presenterTarget.frame(presenterFrames);
                Rac1ShopFrameProfile.end(Rac1ShopFrameProfile.Stage.PRESENTER,presenterProfile);
                var nextPresenter=uploadImage(presenterFrame,"racmc_shop_presenter");
                if (presenterTexture!=null) minecraft.getTextureManager().release(presenterTexture);
                presenterTexture=nextPresenter;
            } catch (RuntimeException failure) {
                LOGGER.warn("Original Gadgetron dynamic panel drawing failed",failure);
                error="Menu panel draw failed. Esc closes; pending purchases remain server-authoritative.";
                voice.cancel();
            }
            boolean has=false;
            var inventory=minecraft.player.getInventory();
            for (int i=0;i<inventory.getContainerSize();i++) if (inventory.getItem(i).is(RacMcItems.BLASTER.get())) { has=true; break; }
            var q=flow.quote();
            var next=new Rac1ShopPreviewOffer(q!=null ? q.kind()==Rac1ShopDelivery.Kind.AMMO:has);
            if (!next.equals(offer) || dirty) {
                var uiMode=quantity.active() && flow.mode()==Rac1ShopClientFlow.Mode.WAIT_QUOTE
                        ? Rac1ShopClientFlow.Mode.CONFIRM:flow.mode();
                var ui=new Rac1ShopPreviewDrawing.Presentation(uiMode,quantity.active() ? 1:q==null ? 1:q.quantity(),
                        quantity.active() ? 1:q==null ? 0:q.price());
                try {
                    if (!next.equals(offer) || !ui.equals(drawnUi)) {
                        long compositionProfile=Rac1ShopFrameProfile.begin();
                        var composed=catalog.blaster() ? Rac1ShopPreviewDrawing.render(source,next,ui)
                                :Rac1ShopPreviewDrawing.renderCatalog(source,ui,false);
                        Rac1ShopFrameProfile.end(Rac1ShopFrameProfile.Stage.COMPOSITION,compositionProfile);
                        upload(composed);
                        drawnUi=ui;
                    }
                    if (textureReadyNanos==0) textureReadyNanos=System.nanoTime();
                    offer=next; dirty=false;
                }
                catch (RuntimeException failure) {
                    LOGGER.warn("Original Gadgetron preview drawing failed",failure);
                    error="Menu draw failed. Esc closes; check inventory/balance for any pending purchase."; voice.cancel(); releaseTexture();
                }
            }
            if (catalog.blaster() && quantity.active() && drawnQuantity!=quantity.desired() && error==null) {
                try {
                    if (quantityTarget==null) quantityTarget=new Rac1ShopPreviewDrawing.QuantityTarget(source);
                    var nextQuantity=uploadImage(quantityTarget.frame(quantity.desired(),quantity.desired()),"racmc_shop_quantity");
                    if (quantityTexture!=null) minecraft.getTextureManager().release(quantityTexture);
                    quantityTexture=nextQuantity; drawnQuantity=quantity.desired();
                } catch (RuntimeException failure) {
                    LOGGER.warn("Original quantity text drawing failed",failure);
                    error="Quantity draw failed. Esc closes; no predicted purchase."; voice.cancel();
                }
            }
            textureQuote=flow.mode()==Rac1ShopClientFlow.Mode.CONFIRM && q!=null && quantity.matches(q)
                    && (!catalog.blaster() || !quantity.active() || drawnQuantity==q.quantity()) && error==null ? q.id():null;
        }
    }
    private void activate() {
        if (!compositionVisible() || !catalogPresented) return;
        switch (flow.mode()) {
            case BROWSE -> {
                Rac1ShopClientPackets.send(commerce.request());
            }
            case CONFIRM -> {
                if (held==0 && quantity.matches(flow.quote()) && flow.quote().id().equals(textureQuote))
                    Rac1ShopClientPackets.send(flow.confirm());
            }
            case DENIED, MAXED, FAILURE -> flow.cancel();
            default -> { }
        }
        dirty=true;
    }
    private void adjust(int delta) {
        if (commerce.adjust(delta)) { textureQuote=null; mouseConfirmation.clear(); dirty=true; }
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers) {
        if (key==GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if (key==GLFW.GLFW_KEY_ENTER || key==GLFW.GLFW_KEY_KP_ENTER || key==GLFW.GLFW_KEY_SPACE) {
            if (flow.activationDown(key)) activate();
            return true;
        }
        if (key==GLFW.GLFW_KEY_BACKSPACE) { flow.cancel(); mouseConfirmation.clear(); dirty=true; return true; }
        if (key==GLFW.GLFW_KEY_LEFT || key==GLFW.GLFW_KEY_RIGHT) {
            if (flow.activationDown(key)) {
                int direction=key==GLFW.GLFW_KEY_LEFT ? -1:1;
                if (switchable()) moveCatalog(direction);
                else if (held==0) { held=direction; heldFrames=0; adjust(held); }
            }
            return true;
        }
        return true; // never pass movement, attack, look or inventory shortcuts through
    }
    @Override public boolean keyReleased(int key,int scan,int modifiers) {
        flow.release(key);
        if (key==GLFW.GLFW_KEY_LEFT || key==GLFW.GLFW_KEY_RIGHT) { held=0; heldFrames=0; }
        return true;
    }
    @Override public boolean mouseClicked(double mouseX,double mouseY,int button) {
        if (button==0 && !mouseConfirmation.press()) return true;
        if (button!=0 || !compositionVisible()) return true;
        int footer=footerHeight();
        int sh=source.pal() ? 448:416; double scale=Math.min(width/512.0,Math.max(1,height-footer)/(double)sh);
        int w=(int)(512*scale),h=(int)(sh*scale);
        double x=(mouseX-(width-w)/2)/scale,y=(mouseY-(height-footer-h)/2)/scale;
        if (inside(Rac1ShopPreviewDrawing.monitor(source,3),x,y)) activateMouse();
        else if (inside(Rac1ShopPreviewDrawing.monitor(source,4),x,y)) {
            if (flow.mode()==Rac1ShopClientFlow.Mode.BROWSE || flow.mode()==Rac1ShopClientFlow.Mode.WAIT_QUOTE
                    || flow.mode()==Rac1ShopClientFlow.Mode.UNCERTAIN) onClose();
            else { flow.cancel(); mouseConfirmation.clear(); dirty=true; }
        } else if (flow.mode()==Rac1ShopClientFlow.Mode.CONFIRM) {
            var rect=Rac1ShopPreviewDrawing.popup(source);
            if (inside(rect,x,y)) {
                double px=x-rect.x(),py=y-rect.y();
                if (flow.quote().kind()==Rac1ShopDelivery.Kind.AMMO) {
                    if (py>=40 && py<=68 && px<24) adjust(-1);
                    else if (py>=40 && py<=68 && px>=56) adjust(1);
                } else if (py>=26 && py<56) activateMouse();
                else if (py>=56 && py<88) { flow.cancel(); mouseConfirmation.clear(); dirty=true; }
            }
        }
        return true;
    }
    private void activateMouse() {
        if (flow.mode()==Rac1ShopClientFlow.Mode.CONFIRM
                && !mouseConfirmation.approve(flow.quote().id(),net.minecraft.Util.getMillis())) return;
        activate();
    }
    @Override public boolean mouseReleased(double x,double y,int button) {
        if (button==0) mouseConfirmation.release();
        return true;
    }
    @Override public void mouseMoved(double mouseX,double mouseY) {
        if (source==null || error!=null || flow.mode()!=Rac1ShopClientFlow.Mode.CONFIRM) return;
        int sh=source.pal() ? 448:416,footer=footerHeight();
        double scale=Math.min(width/512.0,Math.max(1,height-footer)/(double)sh);
        int w=(int)(512*scale),h=(int)(sh*scale);
        double x=(mouseX-(width-w)/2)/scale,y=(mouseY-(height-footer-h)/2)/scale;
        if (!inside(Rac1ShopPreviewDrawing.monitor(source,3),x,y)) mouseConfirmation.movedOutsideBuy();
    }
    private static boolean inside(Rac1ShopLayout.Rectangle r,double x,double y) {
        return x>=r.x() && y>=r.y() && x<r.x()+r.width() && y<r.y()+r.height();
    }
    private boolean valid() {
        if (minecraft==null || minecraft.player==null || minecraft.level==null) return false;
        boolean loaded=minecraft.level.hasChunkAt(vendor.getBlockPos());
        return new Rac1ShopPreviewLifetime(minecraft.player.isAlive(),minecraft.player.isSpectator(),
                vendor.getLevel()==minecraft.level,loaded,
                loaded && !vendor.isRemoved() && minecraft.level.getBlockEntity(vendor.getBlockPos())==vendor,
                minecraft.player.canInteractWithBlock(vendor.getBlockPos(),0),
                RacMcClient.assets().isReady() && RacMcClient.assets().content().orElse(null)==cache,
                RacMcClient.media().isReady() && !RacMcClient.media().isChecking() && RacMcClient.media().generation()==mediaGeneration
                        && RacMcClient.media().configuredPath().filter(path::equals).isPresent()).open();
    }
    private void upload(Rac1ShopOrderedGpu.Readback frame) {
        var next=uploadImage(frame,"racmc_shop_preview");
        releaseTexture();
        texture=next;
    }
    private ResourceLocation uploadImage(Rac1ShopOrderedGpu.Readback frame,String name) {
        long imageProfile=Rac1ShopFrameProfile.begin();
        var image=new NativeImage(frame.width(),frame.height(),false);
        DynamicTexture dynamic=null;
        try {
            byte[] b=frame.rgba();
            for (int y=0;y<frame.height();y++) for (int x=0;x<frame.width();x++) {
                int at=(y*frame.width()+x)*4;
                image.setPixelRGBA(x,y,((b[at+3]&255)<<24)|((b[at+2]&255)<<16)|((b[at+1]&255)<<8)|(b[at]&255));
            }
            Rac1ShopFrameProfile.end(Rac1ShopFrameProfile.Stage.IMAGE_CONVERT,imageProfile);
            long uploadProfile=Rac1ShopFrameProfile.begin();
            try {
                dynamic=new DynamicTexture(image);
                return minecraft.getTextureManager().register(name,dynamic);
            } finally { Rac1ShopFrameProfile.end(Rac1ShopFrameProfile.Stage.TEXTURE_UPLOAD,uploadProfile); }
        } catch (RuntimeException | Error failure) {
            if (dynamic!=null) dynamic.close(); else image.close();
            throw failure;
        }
    }
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) {
        long profile=Rac1ShopFrameProfile.begin();
        try { renderProfiled(graphics,mouseX,mouseY,partialTick); }
        finally { Rac1ShopFrameProfile.end(Rac1ShopFrameProfile.Stage.GUI_SUBMIT,profile); }
    }
    private void renderProfiled(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) {
        // No vanilla background/slots/buttons stand in for original product art.
        int footer=footerHeight();
        graphics.fill(0,height-footer,width,height,0xa0181818); // local diagnostic footer only
        if (compositionVisible()) {
            int sh=source.pal() ? 448:416;
            double scale=Math.min(width/512.0,Math.max(1,height-footer)/(double)sh);
            int w=(int)(512*scale),h=(int)(sh*scale);
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
            graphics.blit(texture,(width-w)/2,(height-footer-h)/2,w,h,0,0,512,sh,512,sh);
            if (presenterTexture!=null) {
                var rect=Rac1ShopPreviewDrawing.monitor(source,2);
                graphics.blit(presenterTexture,(width-w)/2+(int)(rect.x()*scale),(height-footer-h)/2+(int)(rect.y()*scale),
                        (int)(rect.width()*scale),(int)(rect.height()*scale),0,0,
                        Math.min(512,(int)Math.ceil(rect.width())),Math.min(128,(int)Math.ceil(rect.height())),512,128);
            }
            if (tickerTexture!=null) {
                var rect=Rac1ShopPreviewDrawing.monitor(source,0);
                graphics.blit(tickerTexture,(width-w)/2+(int)(rect.x()*scale),(height-footer-h)/2+(int)(rect.y()*scale),
                        (int)(rect.width()*scale),(int)(rect.height()*scale),0,0,
                        Math.min(512,(int)Math.ceil(rect.width())),Math.min(128,(int)Math.ceil(rect.height())),512,128);
            }
            if (catalog.blaster() && quantity.active() && quantityTexture!=null && drawnUi!=null && drawnUi.popup()) {
                var rect=Rac1ShopPreviewDrawing.popup(source);
                graphics.blit(quantityTexture,(width-w)/2+(int)(rect.x()*scale),(height-footer-h)/2+(int)(rect.y()*scale),
                        (int)(rect.width()*scale),(int)(rect.height()*scale),0,0,
                        Math.min(512,(int)Math.ceil(rect.width())),Math.min(128,(int)Math.ceil(rect.height())),512,128);
            }
            com.mojang.blaze3d.systems.RenderSystem.disableBlend();
            catalogPresented=Rac1CommerceCatalogDrawing.drawAt(graphics,font,catalog,flow.quote(),switchable(),
                    height-footer,footer-font.lineHeight-4);
            if (catalogPresented) flow.presented(textureQuote); // exact original frame AND complete MC row/quote submitted
            mouseConfirmation.presented(flow.mode()==Rac1ShopClientFlow.Mode.CONFIRM
                    && flow.quote()!=null && flow.quote().id().equals(textureQuote) ? textureQuote:null,
                    net.minecraft.Util.getMillis());
            if (!usableLogged) {
                usableLogged=true;
                LOGGER.info("Gadgetron click-to-first-usable submitted ms={} source-observed={} GPU-start={} GPU-panels-ready={} texture-ready={} CPU-prepared-at-open={}; unfold unchanged; not GPU-present timing",
                        (System.nanoTime()-clickNanos)/1e6,(sourceReadyNanos-clickNanos)/1e6,
                        (gpuPreparationNanos-clickNanos)/1e6,(gpuPanelsReadyNanos-clickNanos)/1e6,
                        (textureReadyNanos-clickNanos)/1e6,preparedAtOpen);
            }
        }
        String status=error!=null ? error:source==null ? "Loading selected-disc menu; Esc closes"
                : quantity.active() && !quantity.matches(flow.quote()) ? "Updating quantity quote; Yes disabled"
                : flow.mode()+(flow.outcome()==null ? "":" / "+flow.outcome())
                +(flow.mode()==Rac1ShopClientFlow.Mode.UNCERTAIN ? ": await receipt or close; do not repurchase":"");
        int y=height-font.lineHeight-2;
        for (String text:new String[]{status})
            for (var line:font.split(Component.literal(text),Math.max(1,width-8))) {
                if (y+font.lineHeight>height) break;
                graphics.drawString(font,line,4,y,0xffffff); y+=font.lineHeight;
            }
    }
    private int footerHeight() {
        return Math.min(Rac1CommerceCatalogDrawing.height(font,width,catalog,flow.quote(),switchable())
                +font.lineHeight+4,Math.max(1,height/2));
    }
    private boolean switchable() {
        return flow.mode()==Rac1ShopClientFlow.Mode.BROWSE;
    }
    private void moveCatalog(int direction) {
        if (!switchable() || !compositionVisible() || !commerce.move(direction)) return;
        drawnQuantity=0;
        textureQuote=null; drawnUi=null; catalogPresented=false;
        held=heldFrames=0; mouseConfirmation.clear(); stopDenied(); presenterSession.select(catalog.blaster());
        pendingVoiceReceipt=false;
        dirty=true;
    }
    private void releaseTexture() {
        if (texture!=null) { minecraft.getTextureManager().release(texture); texture=null; }
    }
    @Override public void removed() {
        stopDenied();
        if (presenter!=null) { presenter.close(); presenter=null; }
        pendingVoiceReceipt=false;
        voice.close();
        Rac1ShopClientPackets.send(flow.close()); Rac1ShopClientPackets.clear();
        releaseTexture(); source=null;
        if (tickerTexture!=null) { minecraft.getTextureManager().release(tickerTexture); tickerTexture=null; }
        ticker=null;
        if (tickerTarget!=null) { tickerTarget.close(); tickerTarget=null; }
        if (presenterTarget!=null) { presenterTarget.close(); presenterTarget=null; }
        if (presenterTexture!=null) { minecraft.getTextureManager().release(presenterTexture); presenterTexture=null; }
        if (quantityTexture!=null) { minecraft.getTextureManager().release(quantityTexture); quantityTexture=null; }
        if (quantityTarget!=null) { quantityTarget.close(); quantityTarget=null; }
        quantity.clear(); drawnQuantity=0;
        loading=null; // Shared CPU preparation belongs to the selected generation, never this screen.
    }
    @Override public void onClose() {
        stopDenied();
        voice.close();
        super.onClose();
    }
}
