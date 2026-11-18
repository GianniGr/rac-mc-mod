package com.github.giannigr.racmc.client.render;

import static org.junit.jupiter.api.Assertions.*;
import com.github.giannigr.racmc.asset.SyntheticVendor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class Rac1VendorTexturesTest {
    @Test
    void sharedPairReleasedOnChangeLossAndReload() {
        List<String> registered = new ArrayList<>(), released = new ArrayList<>();
        Rac1VendorTextures<String> textures = new Rac1VendorTextures<>((name, texture) -> {
            String id = name + registered.size();
            registered.add(id);
            return id;
        }, released::add);
        var first = SyntheticVendor.content();
        var pair = textures.of(first);
        assertSame(pair, textures.of(first), "Item/world share the same registrations");
        assertEquals(2, registered.size());
        textures.of(SyntheticVendor.content());
        assertEquals(2, released.size());
        textures.synchronize(null);
        assertEquals(4, released.size());
        assertNull(textures.of(null));
        textures.clear();
        assertEquals(4, released.size(), "No double release");
        textures.of(first);
        textures.clear(); // resource reload
        assertEquals(6, released.size());
        textures.of(first);
        assertEquals(8, registered.size(), "Same-content reload must re-register");
        textures.clear();
        assertEquals(registered, released);
    }

    @Test
    void partialRegistrationFailureReleasesAlreadyOwnedTexturesAndCanRetry() {
        AtomicInteger calls = new AtomicInteger();
        List<String> released = new ArrayList<>();
        Rac1VendorTextures<String> textures = new Rac1VendorTextures<>((name, texture) -> {
            if (calls.incrementAndGet() == 2) {
                throw new IllegalStateException("synthetic registration failure");
            }
            return name;
        }, released::add);
        var content = SyntheticVendor.content();
        assertThrows(IllegalStateException.class, () -> textures.of(content));
        assertEquals(List.of("vendor_body_0"), released);
        assertNotNull(textures.of(content));
        textures.clear();
        assertEquals(3, released.size());
    }
}
