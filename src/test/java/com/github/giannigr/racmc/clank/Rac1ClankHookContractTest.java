package com.github.giannigr.racmc.clank;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

class Rac1ClankHookContractTest {
    private static Path root(){return Path.of(System.getProperty("racmc.projectDir"));}
    private static String read(String path)throws Exception{return Files.readString(root().resolve(path));}
    @Test void newCommonMixinsHaveOneAdditiveRegistrationAndRequiredDescriptors()throws Exception {
        String metadata=read("src/main/templates/META-INF/neoforge.mods.toml");
        assertEquals(1,metadata.split("config=\"racmc.clank-moveset.mixins.json\"",-1).length-1);
        assertEquals(1,metadata.split("config=\"racmc.wrench-actions.mixins.json\"",-1).length-1);
        String config=read("src/main/resources/racmc.clank-moveset.mixins.json");
        assertTrue(config.contains("\"required\": true"));
        assertTrue(config.contains("\"defaultRequire\": 1"));
        assertTrue(config.contains("ClankPlayerTravelMixin"));
        assertTrue(config.contains("ClankMovePacketMixin"));
        String packet=read("src/main/java/com/github/giannigr/racmc/clank/mixin/ClankMovePacketMixin.java");
        assertTrue(packet.contains("updateAwaitingTeleport()Z"));
        assertTrue(packet.contains("containsInvalidValues(DDDFF)Z"));
        assertTrue(packet.contains("require=1"));
        assertFalse(packet.contains("aboveGroundTickCount"));
        assertFalse(packet.contains("allowFlying"));
        assertFalse(packet.contains("mayfly"));
    }
    @Test void commonRuntimeAndMixinsHaveNoClientImportsOrRenderRootMutation()throws Exception {
        Path common=root().resolve("src/main/java/com/github/giannigr/racmc/clank");
        try(var files=Files.walk(common)) {
            for(Path file:files.filter(p->p.toString().endsWith(".java")).toList()) {
                String code=Files.readString(file);
                assertFalse(code.contains("import net.minecraft.client"),file.toString());
                assertFalse(code.contains("applyModelRoot"),file.toString());
                assertFalse(code.contains("setNoGravity("),file.toString());
            }
        }
    }
}
