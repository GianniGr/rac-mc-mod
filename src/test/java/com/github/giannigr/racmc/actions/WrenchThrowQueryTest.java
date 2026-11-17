package com.github.giannigr.racmc.actions;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class WrenchThrowQueryTest {
    @Test void primaryCandidatesSuppressFallbackEvenWhenProtectionWouldRejectThem() {
        ActionVector head = new ActionVector(1, 0, 0);
        var queries = new AtomicInteger();
        var result = WrenchThrowGeometry.query(head, () -> {
            fail("Protection/damage rejection must not enable an extra list0 query");
            return ActionVector.ZERO;
        }, center -> {
            queries.incrementAndGet();
            return List.of("protected", "second-target");
        });
        assertEquals(head, result.center());
        assertEquals(List.of("protected", "second-target"), result.candidates());
        assertEquals(1, queries.get());
    }

    @Test void emptyPrimaryQueriesFallbackOnceAndRetainsAllItsCandidates() {
        ActionVector head = new ActionVector(1, 0, 0);
        var queries = new AtomicInteger();
        var result = WrenchThrowGeometry.query(head, () -> ActionVector.ZERO, center -> {
            queries.incrementAndGet();
            return center.equals(head) ? List.of() : List.of("one", "two");
        });
        assertEquals(ActionVector.ZERO, result.center());
        assertEquals(List.of("one", "two"), result.candidates());
        assertEquals(2, queries.get());
    }
}
