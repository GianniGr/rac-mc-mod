package com.github.giannigr.racmc.client.render;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Rac1HudTextureRegistryTest {
    @Test void noPerFrameAllocationAndReloadContentChangeReleaseAll() {
        List<Integer> released = new ArrayList<>();
        var registry = new Rac1HudTextureRegistry<Integer>(released::add);
        var ids = new AtomicInteger();
        Object first = new Object(), second = new Object();
        for (int i = 0; i < 100; i++) assertEquals(1, registry.get(first, "icon", ids::incrementAndGet));
        assertEquals(2, registry.get(first, "font", ids::incrementAndGet));
        assertEquals(3, registry.get(second, "icon", ids::incrementAndGet));
        assertEquals(List.of(1, 2), released);
        registry.clear(); registry.clear();
        assertEquals(List.of(1, 2, 3), released);
        assertEquals(4, registry.get(second, "icon", ids::incrementAndGet));
    }
}
