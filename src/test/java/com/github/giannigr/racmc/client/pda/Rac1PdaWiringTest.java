package com.github.giannigr.racmc.client.pda;

import com.google.gson.JsonParser;
import com.github.giannigr.racmc.client.screen.Rac1ScreenLayout;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Static wiring/resource guards, explicitly not a rendered/live GUI acceptance test. */
class Rac1PdaWiringTest {
    private final Path root=Path.of(System.getProperty("racmc.projectDir"));
    private String source(String name) throws Exception {
        return Files.readString(root.resolve("src/main/java/com/github/giannigr/racmc/client/pda/"+name+".java"));
    }
    @Test void builtinItemModelSelectsActualPdaRendererInsteadOfGenericFallbackArtwork() throws Exception {
        var model=JsonParser.parseString(Files.readString(root.resolve("src/main/resources/assets/racmc/models/item/pda.json"))).getAsJsonObject();
        assertEquals("minecraft:builtin/entity",model.get("parent").getAsString());
        assertFalse(model.has("textures"),"No vanilla compass/book/retail texture is bundled as fallback");
        assertTrue(source("Rac1PdaVisualRegistration").contains("new Rac1PdaItemRenderer()"));
        assertTrue(source("Rac1PdaVisualRegistration").contains("RacMcItems.PDA.get()"));
        var renderer=source("Rac1PdaItemRenderer");
        assertTrue(renderer.contains("resources.icons().getFirst()"));
        assertTrue(renderer.contains("resources.handheld()"));
        assertFalse(renderer.contains("Rac1ShopPreview"));
    }
    @Test void portableScreenIsNonpausingAndDoesNotImportKioskPresentationOrAudio() throws Exception {
        var screen=source("Rac1PdaScreen");
        assertTrue(screen.contains("boolean isPauseScreen() { return false; }"));
        assertTrue(screen.contains("void renderBackground(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) {}"));
        for (String forbidden:new String[]{"Rac1ShopPreview","Rac1ShopPresenter","Rac1ShopDenied","SoundEvents","Rac1Vendor","Rac1ShopObserver"})
            assertFalse(screen.contains(forbidden),forbidden);
        assertTrue(screen.contains("flow.presented(q.id())"));
        assertTrue(screen.contains("Rac1PdaClientPackets.send(flow.close())"));
        assertTrue(source("Rac1PdaVisuals").contains("RacMcClient.media().lease(selected,key)"));
        assertFalse(source("Rac1PdaVisuals").contains("Rac1DiscValidator"));
    }
    @Test void adaptedPanelFitsMinimumMinecraftWindowAndEveryTerminalFailureIsTranslated() throws Exception {
        assertTrue(Rac1ScreenLayout.contentWidth(320)<=320-12);
        assertTrue(228<=240-12);
        var strings=JsonParser.parseString(Files.readString(root.resolve("src/main/resources/assets/racmc/lang/en_us.json"))).getAsJsonObject();
        assertTrue(strings.get("screen.racmc.pda.adaptation").getAsString().contains("Minecraft"));
        for (String status:new String[]{"insufficient","stock_cap","full","stale_offer","stale_balance","stale_quote",
                "commit_rejected","invalid_quantity","stale_request","conflict","exhausted"})
            assertTrue(strings.has("screen.racmc.pda.result."+status),status);
    }
}
