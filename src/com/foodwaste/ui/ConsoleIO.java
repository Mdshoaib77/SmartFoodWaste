package com.foodwaste.ui;

import com.foodwaste.util.Fmt;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Scanner;

/** All keyboard input and screen output helpers for the console UI. */
public final class ConsoleIO {
    private static final Scanner IN = new Scanner(System.in);

    private ConsoleIO() { }

    public static String ask(String prompt) {
        System.out.print(prompt + ": ");
        System.out.flush();
        if (!IN.hasNextLine()) {
            System.out.println("\nInput closed. Exiting.");
            System.exit(0);
        }
        return IN.nextLine().trim();
    }

    public static String askRequired(String prompt) {
        while (true) {
            String s = ask(prompt);
            if (!s.isEmpty()) return s;
            error("This field is required.");
        }
    }

    public static int askInt(String prompt) {
        while (true) {
            try {
                return Integer.parseInt(ask(prompt));
            } catch (NumberFormatException e) {
                error("Please enter a whole number.");
            }
        }
    }

    public static int askPositiveInt(String prompt) {
        while (true) {
            int v = askInt(prompt);
            if (v > 0) return v;
            error("The value must be greater than zero.");
        }
    }

    /** Hides typing when run from a real terminal; IDE consoles have no java.io.Console, so input is visible there. */
    public static String askPassword(String prompt) {
        java.io.Console console = System.console();
        if (console != null) {
            char[] pw = console.readPassword(prompt + ": ");
            return pw == null ? "" : new String(pw);
        }
        return ask(prompt + " (visible in this console)");
    }

    /** Accepts "yyyy-MM-dd HH:mm" or a shortcut such as +6h / +90m (from now). */
    public static LocalDateTime askDateTime(String prompt, boolean blankIsNow) {
        while (true) {
            String s = ask(prompt + " [yyyy-MM-dd HH:mm or +6h / +90m" + (blankIsNow ? ", blank = now" : "") + "]");
            if (s.isEmpty() && blankIsNow) return LocalDateTime.now().withSecond(0).withNano(0);
            try {
                if (s.startsWith("+")) {
                    String num = s.substring(1).trim().toLowerCase();
                    boolean minutes = num.endsWith("m");
                    if (num.endsWith("h") || num.endsWith("m")) num = num.substring(0, num.length() - 1).trim();
                    long n = Long.parseLong(num);
                    return LocalDateTime.now().plus(n, minutes ? ChronoUnit.MINUTES : ChronoUnit.HOURS).withSecond(0).withNano(0);
                }
                return LocalDateTime.parse(s, Fmt.DT);
            } catch (RuntimeException e) {
                error("Invalid date/time. Example: 2026-10-12 18:30  or  +5h");
            }
        }
    }

    /** Time slot in HH:mm-HH:mm; asks again until the format is valid (blank allowed only when allowBlank). */
    public static String askSlot(String prompt, boolean allowBlank) {
        while (true) {
            String s = ask(prompt + " [HH:mm-HH:mm, e.g. 16:00-18:00" + (allowBlank ? ", blank = keep requested slot" : "") + "]");
            if (s.isEmpty() && allowBlank) return "";
            try {
                return com.foodwaste.util.TimeSlot.normalise(s);
            } catch (IllegalArgumentException e) {
                error(e.getMessage());
            }
        }
    }

    /** Date yyyy-MM-dd, or null when left blank. */
    public static java.time.LocalDate askDateOrBlank(String prompt) {
        while (true) {
            String s = ask(prompt + " [yyyy-MM-dd, blank = any]");
            if (s.isEmpty()) return null;
            try {
                return java.time.LocalDate.parse(s);
            } catch (RuntimeException e) {
                error("Invalid date. Example: 2026-11-10");
            }
        }
    }

    /** Shows a numbered list and returns the chosen item (number or exact name typed); blank returns null when allowBlank. */
    public static String choose(String prompt, java.util.List<String> items, boolean allowBlank) {
        for (int i = 0; i < items.size(); i++) System.out.println("   " + (i + 1) + ". " + items.get(i));
        while (true) {
            String s = ask(prompt + " [number" + (allowBlank ? ", blank = all" : "") + "]");
            if (s.isEmpty() && allowBlank) return null;
            try {
                int n = Integer.parseInt(s);
                if (n >= 1 && n <= items.size()) return items.get(n - 1);
            } catch (NumberFormatException e) {
                for (String it : items) if (it.equalsIgnoreCase(s)) return it;
            }
            error("Choose one of the listed numbers.");
        }
    }

    /** Numbered area list; a blank answer (or Enter) keeps the given default area. A typed area name also works. */
    public static String chooseArea(String prompt, java.util.List<String> items, String defaultArea) {
        for (int i = 0; i < items.size(); i++) System.out.println("   " + (i + 1) + ". " + items.get(i));
        while (true) {
            String s = ask(prompt + " [number or name, blank = " + defaultArea + "]");
            if (s.isEmpty()) return defaultArea;
            try {
                int n = Integer.parseInt(s);
                if (n >= 1 && n <= items.size()) return items.get(n - 1);
            } catch (NumberFormatException e) {
                for (String it : items) if (it.equalsIgnoreCase(s)) return it;
            }
            error("Choose one of the listed numbers or type the area name.");
        }
    }

    public static boolean confirm(String prompt) {
        return ask(prompt + " (y/n)").toLowerCase().startsWith("y");
    }

    public static void header(String title) {
        System.out.println();
        System.out.println("=== " + title + " ===");
    }

    public static void info(String m) { System.out.println("  " + m); }

    public static void success(String m) { System.out.println("  [OK] " + m); }

    public static void error(String m) { System.out.println("  [!] " + m); }
}
