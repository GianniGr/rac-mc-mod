package com.github.giannigr.racmc.client.gear;

import static com.github.giannigr.racmc.gear.Rac1BeltState.Availability.*;
import static org.junit.jupiter.api.Assertions.*;

import com.github.giannigr.racmc.gear.Rac1BeltState;
import org.junit.jupiter.api.Test;

class Rac1BeltViewTest {
    @Test void delayedAndForeignContextStateCannotActivateTheMenu() {
        var view = new Rac1BeltView();
        var first = new Rac1BeltView.Context(new Object(), new Object(), new Object());
        var dimension = new Rac1BeltView.Context(first.connection(), new Object(), new Object());
        var ready = new Rac1BeltState(1, new Rac1BeltState.Slot(1, READY));
        var worn = new Rac1BeltState(1, new Rac1BeltState.Slot(2, EQUIPPED));
        view.receive(first, ready);
        view.receive(first, worn);
        view.receive(first, ready);
        assertEquals(worn, view.state(first));
        assertNull(view.state(dimension));
        view.receive(dimension, worn);
        assertNull(view.state(dimension));
        var fresh = new Rac1BeltState(2, new Rac1BeltState.Slot(3, RETURNING));
        view.receive(dimension, fresh);
        view.receive(dimension, new Rac1BeltState(3, new Rac1BeltState.Slot(2, EQUIPPED)));
        assertEquals(fresh, view.state(dimension));
        view.clear();
        assertNull(view.state(dimension));
    }
}
