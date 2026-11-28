package com.github.giannigr.racmc.client.screen;

import com.github.giannigr.racmc.RacMcMod;
import java.awt.AWTError;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

import static com.github.giannigr.racmc.client.screen.Rac1IsoInput.Status.*;

/** Optional client-only desktop integration; never changes JVM AWT settings. */
final class Rac1IsoChooser {
    private static final AtomicBoolean OPEN=new AtomicBoolean();

    private Rac1IsoChooser() {}

    /** One desktop dialog across screen instances. Completion may arrive on any thread. */
    static void open(String title, String filter, Consumer<Rac1IsoInput.Result> done) {
        if (!OPEN.compareAndSet(false,true)) {
            done.accept(Rac1IsoInput.Result.of(BUSY));
            return;
        }
        var failure=attempt(GraphicsEnvironment::isHeadless, () -> {
            SwingUtilities.invokeLater(() -> complete(done,
                    attempt(GraphicsEnvironment::isHeadless, () -> show(title,filter))));
            return null; // Scheduled; the EDT owns completion now.
        });
        if (failure!=null) complete(done,failure);
    }

    private static void complete(Consumer<Rac1IsoInput.Result> done, Rac1IsoInput.Result result) {
        OPEN.set(false);
        done.accept(result);
    }

    static Rac1IsoInput.Result attempt(BooleanSupplier headless, Supplier<Rac1IsoInput.Result> dialog) {
        try {
            return headless.getAsBoolean() ? Rac1IsoInput.Result.of(UNAVAILABLE):dialog.get();
        } catch (HeadlessException unavailable) {
            return Rac1IsoInput.Result.of(UNAVAILABLE);
        } catch (RuntimeException | AWTError | LinkageError failure) {
            RacMcMod.LOGGER.warn("The disc image file chooser failed.",failure);
            return Rac1IsoInput.Result.of(FAILED);
        }
    }

    private static Rac1IsoInput.Result show(String title,String filter) {
        var chooser=new JFileChooser();
        chooser.setDialogTitle(title);
        chooser.setFileFilter(new FileNameExtensionFilter(filter,"iso"));
        chooser.setAcceptAllFileFilterUsed(true);
        var parent=new JDialog((Frame)null,title,true);
        try {
            parent.setAlwaysOnTop(true);
            int code=chooser.showOpenDialog(parent);
            var file=code==JFileChooser.APPROVE_OPTION ? chooser.getSelectedFile():null;
            return outcome(code,file==null ? null:file.toPath());
        } finally {
            parent.dispose();
        }
    }

    static Rac1IsoInput.Result outcome(int code,Path file) {
        if (code==JFileChooser.CANCEL_OPTION) return Rac1IsoInput.Result.of(CANCELLED);
        if (code==JFileChooser.APPROVE_OPTION && file!=null) return new Rac1IsoInput.Result(SELECTED,file);
        return Rac1IsoInput.Result.of(FAILED);
    }
}
