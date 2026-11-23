package com.github.giannigr.racmc.client.pda;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Static install/lifetime guards, not live raster/audio acceptance. */
class Rac1PdaOriginalWiringTest {
    private final Path root=Path.of(System.getProperty("racmc.projectDir"));
    private String source(String path) throws Exception {
        return Files.readString(root.resolve("src/main/java/com/github/giannigr/racmc/"+path+".java"));
    }
    @Test void originalScreenInstalledWithoutModernWidgetOrSpokenVendorFallback() throws Exception {
        assertTrue(source("client/pda/Rac1PdaClientPackets").contains("new Rac1PdaOriginalScreen(packet.token(),packet.hand())"));
        var screen=source("client/pda/Rac1PdaOriginalScreen");
        assertFalse(screen.contains("Button.builder"));
        assertFalse(screen.contains("SoundEvents"));
        assertTrue(screen.contains("source.graphics().voice()!=null"));
        assertTrue(screen.contains("Rac1ShopPreviewDrawing.renderRemote"));
        assertTrue(screen.contains("Original PDA presentation unavailable"));
    }
    @Test void privateContextAndIndividualCancellationDoNotPauseOrHideTheWorld() throws Exception {
        var screen=source("client/pda/Rac1PdaOriginalScreen");
        assertTrue(screen.contains("boolean isPauseScreen() { return false; }"));
        assertTrue(screen.contains("void renderBackground(GuiGraphics graphics,int x,int y,float partial) {}"));
        assertTrue(screen.contains("getItemInHand(hand)==stack"));
        assertTrue(screen.contains("Rac1PdaClientPackets.send(flow.close())"));
        assertTrue(screen.contains("flow.presented(flow.quote().id())"));
        assertTrue(screen.contains("consent.approve"));
        assertFalse(screen.contains("Rac1ShopTransactionServer"));
        assertFalse(screen.contains("Rac1ShopObserverClient"));
    }
    @Test void originalRemoteLoaderUsesTypedLeaseAndNoSpokenPayloadWhileOldShopEntryRemainsExplicit() throws Exception {
        var loader=source("client/pda/Rac1PdaOriginalSource");
        assertTrue(loader.contains("RacMcClient.media().lease"));
        assertTrue(loader.contains("Rac1PdaRemoteProgram.read(lease)"));
        assertTrue(loader.contains("Rac1PdaRemoteAudio.read(lease,program)"));
        assertFalse(loader.contains("Rac1DiscValidator"));
        var shared=source("client/shop/Rac1ShopPreviewSource");
        assertTrue(shared.contains("cacheRoot,true)"));
        assertTrue(shared.contains("cacheRoot,false)"));
        var drawing=source("client/shop/Rac1ShopPreviewDrawing");
        assertTrue(drawing.contains("ui,optimized,false)"));
        assertTrue(drawing.contains("if (!remote && !catalog) Rac1ShopActorDrawing.draw"));
    }
    @Test void actualScreenUsesTheTestedConstructionBeforeOpenAndTickerUsesCompleteSceneText() throws Exception {
        var screen=source("client/pda/Rac1PdaOriginalScreen");
        assertTrue(screen.contains("panels=new Rac1PdaOriginalPanels(source,"));
        assertTrue(screen.indexOf("panels=new Rac1PdaOriginalPanels")<screen.indexOf("play(Role.OPEN)"));
        assertFalse(screen.contains("new Rac1ShopTicker("));
        assertTrue(screen.contains("panels.presenterFrame()"));
        assertTrue(screen.contains("panels.tickerFrame()"));
        assertTrue(screen.contains("panels.close()"));
        var panels=source("client/pda/Rac1PdaOriginalPanels");
        assertTrue(panels.contains("graphics.shop().sceneTables().led(),graphics.shop().text()"));
        assertTrue(panels.indexOf("ticker=new Rac1ShopTicker")<panels.indexOf("presenter=new"));
        assertTrue(source("client/pda/Rac1PdaOriginalSource").contains("return load(lease,main.discKey(),main.blaster(),root.get())"));
    }
}
