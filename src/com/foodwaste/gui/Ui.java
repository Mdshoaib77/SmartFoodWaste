package com.foodwaste.gui;

import com.foodwaste.service.ServiceException;
import com.foodwaste.util.Fmt;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Window;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

/** Dialogs (all rendered by {@link Modal}), background execution and input parsing shared by all screens. */
public final class Ui {
    private Ui() { }

    /** Two values returned together from one background task. */
    public static final class Pair<A, B> {
        public final A a;
        public final B b;
        public Pair(A a, B b) { this.a = a; this.b = b; }
    }

    // ------------------------------------------------------------------ dialogs

    /** Information the user must acknowledge. */
    public static void info(Component p, String m) { Modal.alert(p, Modal.Type.INFO, "Information", m); }

    /** Non-blocking hint (small toast), e.g. "select a row first". */
    public static void hint(Component p, String m) { Modal.toast(p, Modal.Type.INFO, m); }

    public static void warn(Component p, String m) { Modal.alert(p, Modal.Type.WARNING, "Please check", m); }

    public static void error(Component p, String m) { Modal.alert(p, Modal.Type.ERROR, "Something went wrong", m); }

    /** Success message after an action; the caller's next line (reload, redirect...) runs after the user closes it. */
    public static void success(Component p, String title, String m) { Modal.alert(p, Modal.Type.SUCCESS, title, m); }

    public static boolean confirm(Component p, String m) { return confirm(p, "Please confirm", m, "Confirm"); }

    public static boolean confirm(Component p, String title, String m, String yes) {
        return Modal.confirm(p, Modal.Type.CONFIRM, title, m, yes, "Cancel");
    }

    /** Destructive confirmation (delete, reject, cancel) - red button, no accidental Enter. */
    public static boolean confirmDanger(Component p, String title, String m, String yes, String no) {
        return Modal.confirm(p, Modal.Type.DELETE, title, m, yes, no);
    }

    /** Large, easy-to-read verification code (donor hands it to the receiver). */
    public static void showCode(Component parent, String headline, String code) {
        Modal.code(parent, "Pickup verification code", headline.replace("<br>", "\n"), code);
    }

    /** Asks for a time slot (HH:mm-HH:mm) until it is valid; returns null when cancelled. */
    public static String askSlot(Component p, String message, String initial) {
        String value = initial;
        while (true) {
            String in = Modal.prompt(p, "Pickup time slot", message, "Time slot (HH:mm-HH:mm)", value, "Confirm");
            if (in == null) return null;
            value = in;
            try {
                return com.foodwaste.util.TimeSlot.normalise(value);
            } catch (IllegalArgumentException e) {
                warn(p, e.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------ background tasks

    /** The styled button that has the keyboard focus (the one just clicked), if it belongs to the window. */
    private static Skin.Btn clickedButton(Window w) {
        Component f = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (f instanceof Skin.Btn && w != null && SwingUtilities.getWindowAncestor(f) == w) return (Skin.Btn) f;
        return null;
    }

    /** Runs the task off the UI thread, shows errors as dialogs, and calls ok on the UI thread. */
    public static <T> void run(final Component parent, final Callable<T> task, final Consumer<T> ok) {
        final Window w = parent instanceof Window ? (Window) parent : SwingUtilities.getWindowAncestor(parent);
        if (w != null) w.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        final Runnable endBusy = Modal.busy(parent);
        final Skin.Btn btn = clickedButton(w);
        if (btn != null) btn.setBusy(true);
        new SwingWorker<T, Void>() {
            @Override protected T doInBackground() throws Exception { return task.call(); }

            @Override protected void done() {
                if (w != null) w.setCursor(Cursor.getDefaultCursor());
                endBusy.run();
                if (btn != null) {
                    btn.setBusy(false);
                    Component f = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
                    if (btn.isShowing() && !(f instanceof javax.swing.text.JTextComponent)) btn.requestFocusInWindow();
                }
                T result;
                try {
                    result = get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (ExecutionException e) {
                    Throwable c = e.getCause() == null ? e : e.getCause();
                    String m = c.getMessage() == null ? c.toString() : c.getMessage();
                    if (c instanceof ServiceException) warn(parent, m);
                    else error(parent, "Unexpected error: " + m);
                    return;
                }
                if (ok != null) ok.accept(result);
            }
        }.execute();
    }

    // ------------------------------------------------------------------ input parsing

    /** Accepts "yyyy-MM-dd HH:mm" or a shortcut such as +6h / +90m (from now). */
    public static LocalDateTime parseDateTime(String text, boolean blankIsNow) {
        String s = text == null ? "" : text.trim();
        if (s.isEmpty()) {
            if (blankIsNow) return LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
            throw new IllegalArgumentException("Please enter a date and time (yyyy-MM-dd HH:mm).");
        }
        try {
            if (s.startsWith("+")) {
                String num = s.substring(1).trim().toLowerCase();
                boolean minutes = num.endsWith("m");
                if (num.endsWith("h") || num.endsWith("m")) num = num.substring(0, num.length() - 1).trim();
                long n = Long.parseLong(num);
                return LocalDateTime.now().plus(n, minutes ? ChronoUnit.MINUTES : ChronoUnit.HOURS).truncatedTo(ChronoUnit.MINUTES);
            }
            return LocalDateTime.parse(s, Fmt.DT);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid date/time. Use yyyy-MM-dd HH:mm (e.g. 2026-10-16 18:30) or +5h.");
        }
    }
}
