package com.github.giannigr.racmc.client.screen;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Game-thread input boundary. Non-selections never reach the media manager. */
final class Rac1IsoInput {
    enum Status {
        NONE(""), OPENING("picker_opening"), SELECTED("picker_selected"),
        CANCELLED("picker_cancelled"), UNAVAILABLE("picker_unavailable"), FAILED("picker_failed"),
        BUSY("picker_busy"), NO_PATH("no_path"), INVALID_PATH("invalid_path");

        final String key;
        Status(String key) { this.key=key; }
    }

    record Result(Status status, Path path) {
        Result {
            Objects.requireNonNull(status);
            if ((status==Status.SELECTED)!=(path!=null)) throw new IllegalArgumentException("Invalid picker result");
        }
        static Result of(Status status) { return new Result(status,null); }
    }

    private long serial, pending;
    private Status status=Status.NONE;
    private long focusIntent;
    private boolean dispatchingClick;
    private Runnable focusAfterClick;

    Status status() { return status; }
    boolean enabled(boolean checking) { return pending==0 && !checking; }

    long focusIntent() { return focusIntent; }

    void noteFocusInput() { focusIntent++; }

    boolean dispatchClick(BooleanSupplier vanillaClick) {
        noteFocusInput();
        dispatchingClick = true;
        try {
            boolean handled = vanillaClick.getAsBoolean();
            // The container assigns focus to the clicked widget after its onPress callback.
            if (focusAfterClick != null) focusAfterClick.run();
            return handled;
        } finally {
            dispatchingClick = false;
            focusAfterClick = null;
        }
    }

    void focusFallback(long intent, Runnable focus) {
        Runnable ifCurrent = () -> {
            if (intent == focusIntent) focus.run();
        };
        if (dispatchingClick) {
            focusAfterClick = ifCurrent;
        } else {
            ifCurrent.run();
        }
    }

    static boolean submitsPath(boolean fieldFocused,int keyCode) {
        return fieldFocused && (keyCode==org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
                || keyCode==org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER);
    }

    long begin(boolean checking) {
        if (!enabled(checking)) return 0;
        status=Status.OPENING;
        return pending=++serial;
    }

    boolean finish(long ticket, Result result, boolean current, boolean checking, Consumer<Path> select) {
        if (ticket==0 || ticket!=pending) return false;
        pending=0;
        if (!current) return false;
        status=result.status()==Status.SELECTED && checking ? Status.BUSY:result.status();
        if (status==Status.SELECTED) select.accept(result.path());
        return true;
    }

    void close() {
        noteFocusInput();
        focusAfterClick = null;
        if (pending!=0) status=Status.NONE;
        pending=0;
    }

    void manual(String raw, boolean checking, Consumer<Path> select) {
        if (!enabled(checking)) return;
        String value=raw.strip();
        // Windows Explorer's Copy as path uses surrounding quotes.
        if (value.length()>=2 && value.startsWith("\"") && value.endsWith("\""))
            value=value.substring(1,value.length()-1);
        if (value.isBlank()) { status=Status.NO_PATH; return; }
        Path path;
        try { path=Path.of(value); }
        catch (InvalidPathException invalid) { status=Status.INVALID_PATH; return; }
        if (!path.isAbsolute()) { status=Status.INVALID_PATH; return; }
        status=Status.NONE;
        select.accept(path); // No filesystem probing on the game thread.
    }
}
