package com.github.giannigr.racmc.client.shop;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Installation/gating guards complement transaction tests; not live Minecraft rendering proof. */
class Rac1CommerceSelectorWiringTest {
    private String source(String path) throws Exception {
        return Files.readString(Path.of(System.getProperty("racmc.projectDir"),
                "src/main/java/com/github/giannigr/racmc/client",path+".java"));
    }
    @Test void bothOriginalScreensSelectTypedOfferAndRequireCatalogPresentation() throws Exception {
        for (var path:new String[]{"shop/Rac1ShopPreviewScreen","pda/Rac1PdaOriginalScreen"}) {
            var s=source(path);
            assertFalse(s.contains("GLFW.GLFW_KEY_TAB"));
            assertTrue(s.contains("if (flow.activationDown(key))"));
            assertTrue(s.contains("if (switchable()) moveCatalog(direction)"));
            assertFalse(s.contains("cycleCatalog"));
            assertTrue(s.contains("catalog=commerce.catalog()"));
            assertTrue(s.contains("commerce.request()"));
            assertTrue(s.contains("Rac1CommerceCatalogDrawing.draw"));
            assertTrue(s.contains("!catalogPresented"));
            assertTrue(s.contains("Rac1ShopPreviewDrawing.renderCatalog"));
            assertTrue(s.contains("private boolean switchable()"));
        }
        assertTrue(source("shop/Rac1ShopClientPackets").contains("clicked,packet.starterAvailable()"));
    }
    @Test void supplementalCatalogCannotSubstituteBlasterProductArtOrSpeech() throws Exception {
        var draw=source("shop/Rac1ShopPreviewDrawing");
        assertTrue(draw.contains("!remote && !catalog"));
        assertTrue(draw.contains("catalog && (s==1 || s==5)"));
        assertTrue(draw.contains("ui.popup() && !catalog"));
        var screen=source("shop/Rac1ShopPreviewScreen");
        assertTrue(screen.contains("presenterSession.tick(3,minecraft.isWindowActive())"));
        assertFalse(screen.contains("error==null && catalog.blaster() && flow.mode()"));
        assertFalse(screen.substring(screen.indexOf("private void moveCatalog"),
                screen.indexOf("private void releaseTexture")).contains("presenter.suspend()"));
        assertTrue(screen.contains("commerce.request()"));
        assertTrue(source("shop/Rac1ShopScreenCommerce").contains("flow.beginBuy(catalog.offer())"));
        var ui=source("shop/Rac1CommerceCatalogDrawing");
        assertTrue(ui.contains("return false;"));
        assertTrue(ui.contains("availableHeight<panelHeight"));
        assertFalse(ui.contains("g.fill(0,0"));
        assertTrue(ui.contains("int y=top+4"));
    }
}
