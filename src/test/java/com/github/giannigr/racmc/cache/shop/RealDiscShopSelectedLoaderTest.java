package com.github.giannigr.racmc.cache.shop;

import com.github.giannigr.racmc.cache.Rac1DiscIdentity;
import com.github.giannigr.racmc.iso.Rac1DiscValidator;
import java.io.IOException;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Only converted sidecar bytes are retained under private test temp, never source containers. */
@EnabledIfSystemProperty(named="racmc.isoImage",matches=".+")
class RealDiscShopSelectedLoaderTest {
    @TempDir Path temporary;
    @Test void selectedDiscBuildCacheHitMismatchAndCorruptPreservation() throws Exception {
        Path image=Path.of(System.getProperty("racmc.isoImage"));
        var validator=new Rac1DiscValidator();
        String key, sha1;
        try(var outcome=validator.inspect(image)) {
            assertTrue(outcome.isSupported());
            var profile=validator.catalog().profiles().stream()
                    .filter(p -> p.revision()==outcome.result().revision()).findFirst().orElseThrow();
            key=Rac1DiscIdentity.key(outcome.result().revision(),profile); sha1=profile.imageSha1();
        }
        var selection=new Rac1ShopCachePreparation.Selection(temporary,image,key,0);
        var first=Rac1ShopSelectedDiscLoader.load(selection);
        var store=new Rac1ShopCacheStore(temporary);
        Path sidecar=store.path(sha1,0);
        byte[] prior=Files.readAllBytes(sidecar);
        assertEquals(sha1,first.discSha1()); assertTrue(first.missingImportedModels().isEmpty());
        assertTrue(first.missingMaterialMetadata().isEmpty());
        var cached=Rac1ShopSelectedDiscLoader.load(selection);
        assertArrayEquals(first.models().ammo().mesh().positions(),cached.models().ammo().mesh().positions());
        assertArrayEquals(prior,Files.readAllBytes(sidecar));
        assertTrue(cached.missingMaterialMetadata().isEmpty());
        assertArrayEquals(Rac1ShopMaterialCodec.write(first.materialMetadata()),
                Rac1ShopMaterialCodec.write(cached.materialMetadata()));
        var wrong=new Rac1ShopCachePreparation.Selection(temporary,image,"wrong|"+"a".repeat(40),0);
        assertThrows(IOException.class,() -> Rac1ShopSelectedDiscLoader.load(wrong));
        assertFalse(Files.exists(store.path("a".repeat(40),0)));
        byte[] corrupt={1,2,3}; Files.write(sidecar,corrupt);
        assertThrows(IOException.class,() -> Rac1ShopSelectedDiscLoader.load(selection));
        assertArrayEquals(corrupt,Files.readAllBytes(sidecar));
    }
}
