package com.github.giannigr.racmc.cache.shop;

import java.io.IOException;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class Rac1ShopSelectedDiscLoaderTest {
    @TempDir Path temporary;
    @Test void unusableEffectiveRootAndRejectedDiscNeverCreateFallbackOrSidecar() throws Exception {
        Path image=temporary.resolve("synthetic-invalid.iso"); Files.write(image,new byte[2048]);
        Path root=temporary.resolve("missing");
        var selection=new Rac1ShopCachePreparation.Selection(root,image,"synthetic|"+"a".repeat(40),0);
        assertThrows(IOException.class,() -> Rac1ShopSelectedDiscLoader.load(selection));
        assertFalse(Files.exists(root));
        Files.createDirectory(root);
        assertThrows(IOException.class,() -> Rac1ShopSelectedDiscLoader.load(selection));
        try(var paths=Files.list(root)) { assertEquals(0,paths.count()); }
    }
}
