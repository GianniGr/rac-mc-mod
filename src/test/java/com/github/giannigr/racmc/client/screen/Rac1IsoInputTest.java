package com.github.giannigr.racmc.client.screen;

import java.awt.AWTError;
import java.awt.HeadlessException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JFileChooser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lwjgl.glfw.GLFW;

import static com.github.giannigr.racmc.client.screen.Rac1IsoInput.Status.*;
import static org.junit.jupiter.api.Assertions.*;

class Rac1IsoInputTest {
    @TempDir Path temp;

    @Test void desktopOutcomesAreDistinctWithoutOpeningADialog() {
        var file=temp.resolve("Disc with spaces & é.iso");
        assertEquals(UNAVAILABLE,Rac1IsoChooser.attempt(() -> true,() -> fail("Headless must not open Swing")).status());
        assertEquals(UNAVAILABLE,Rac1IsoChooser.attempt(() -> false,() -> { throw new HeadlessException(); }).status());
        assertEquals(FAILED,Rac1IsoChooser.attempt(() -> false,() -> { throw new AWTError("No display"); }).status());
        assertEquals(FAILED,Rac1IsoChooser.attempt(() -> false,() -> { throw new IllegalStateException("Broken chooser"); }).status());
        assertEquals(CANCELLED,Rac1IsoChooser.outcome(JFileChooser.CANCEL_OPTION,file).status());
        assertEquals(FAILED,Rac1IsoChooser.outcome(JFileChooser.ERROR_OPTION,file).status());
        assertEquals(FAILED,Rac1IsoChooser.outcome(JFileChooser.APPROVE_OPTION,null).status());
        assertEquals(new Rac1IsoInput.Result(SELECTED,file),Rac1IsoChooser.outcome(JFileChooser.APPROVE_OPTION,file));
    }

    @Test void onlyCurrentSuccessfulSelectionSubmitsExactlyOnce() {
        var input=new Rac1IsoInput();
        var selected=new ArrayList<Path>();
        var file=temp.resolve("exact.iso");
        for (var nonSelection:List.of(CANCELLED,UNAVAILABLE,FAILED,BUSY)) {
            long ticket=input.begin(false);
            assertTrue(ticket>0);
            assertEquals(0,input.begin(false));
            assertFalse(input.enabled(false));
            input.manual(file.toString(),false,selected::add);
            assertTrue(input.finish(ticket,Rac1IsoInput.Result.of(nonSelection),true,false,selected::add));
            assertEquals(nonSelection,input.status());
            assertTrue(input.enabled(false));
        }
        assertTrue(selected.isEmpty());
        assertEquals(0,input.begin(true));
        var result=new Rac1IsoInput.Result(SELECTED,file);
        long closed=input.begin(false);
        input.close();
        long reopened=input.begin(false);
        assertFalse(input.finish(closed,result,true,false,selected::add));
        assertFalse(input.finish(reopened,result,false,false,selected::add));
        assertTrue(input.finish(input.begin(false),result,true,true,selected::add));
        assertEquals(BUSY,input.status());
        assertTrue(selected.isEmpty());
        long active=input.begin(false);
        assertTrue(input.finish(active,result,true,false,selected::add));
        assertFalse(input.finish(active,result,true,false,selected::add));
        assertEquals(List.of(file),selected);
        assertEquals(SELECTED,input.status());
    }

    @Test void typedAndCopiedPathsUseSameSinkAndInvalidInputNeverSelects() {
        var input=new Rac1IsoInput();
        var selected=new ArrayList<Path>();
        var file=temp.resolve("Disc with spaces & é.iso");
        input.manual("   ",false,selected::add);
        assertEquals(NO_PATH,input.status());
        input.manual("\"\"",false,selected::add);
        assertEquals(NO_PATH,input.status());
        input.manual("relative.iso",false,selected::add);
        assertEquals(INVALID_PATH,input.status());
        input.manual(file+"\0",false,selected::add);
        assertEquals(INVALID_PATH,input.status());
        input.manual(file.toString(),true,selected::add);
        assertTrue(selected.isEmpty());
        input.manual(file.toString(),false,selected::add);
        input.manual("  \""+file+"\"  ",false,selected::add);
        assertEquals(List.of(file,file),selected);
    }

    @Test void onlyEnterOnTheFocusedFieldSubmitsWithoutStealingPasteOrTab() {
        assertTrue(Rac1IsoInput.submitsPath(true,GLFW.GLFW_KEY_ENTER));
        assertTrue(Rac1IsoInput.submitsPath(true,GLFW.GLFW_KEY_KP_ENTER));
        assertFalse(Rac1IsoInput.submitsPath(false,GLFW.GLFW_KEY_ENTER));
        for (int key:new int[]{GLFW.GLFW_KEY_TAB,GLFW.GLFW_KEY_V,GLFW.GLFW_KEY_ESCAPE})
            assertFalse(Rac1IsoInput.submitsPath(true,key));
    }

    @Test void inlineUnavailableFocusRunsAfterVanillaAssignsClickFocus() {
        var input = new Rac1IsoInput();
        var focusChanges = new ArrayList<String>();
        assertTrue(input.dispatchClick(() -> {
            long ticket = input.begin(false);
            assertTrue(input.finish(ticket, Rac1IsoInput.Result.of(UNAVAILABLE), true, false,
                    path -> fail("Unavailable must not submit")));
            input.focusFallback(input.focusIntent(), () -> focusChanges.add("path field"));
            assertTrue(focusChanges.isEmpty());
            focusChanges.add("clicked picker"); // ContainerEventHandler.setFocused after onPress.
            return true;
        }));
        assertEquals(List.of("clicked picker", "path field"), focusChanges);
    }

    @Test void newerInputAndCloseInvalidateFocusButKeyboardNeedsNoClickHandoff() {
        var input = new Rac1IsoInput();
        input.noteFocusInput(); // Keyboard activation.
        long keyboardIntent = input.focusIntent();
        var focusChanges = new ArrayList<String>();
        input.focusFallback(keyboardIntent, () -> focusChanges.add("keyboard fallback"));
        assertEquals(List.of("keyboard fallback"), focusChanges);

        assertFalse(input.dispatchClick(() -> false)); // New mouse input.
        input.focusFallback(keyboardIntent, () -> fail("Newer input must win"));
        assertTrue(input.dispatchClick(() -> {
            input.focusFallback(input.focusIntent(), () -> fail("Closed screen must not gain focus"));
            input.close();
            return true;
        }));
        long beforeClose = input.focusIntent();
        input.close();
        input.focusFallback(beforeClose, () -> fail("Delayed focus after close"));
        assertEquals(List.of("keyboard fallback"), focusChanges);
    }
}
