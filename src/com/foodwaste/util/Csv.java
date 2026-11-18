package com.foodwaste.util;

import java.util.List;

/** Minimal RFC-4180 CSV writer helpers (quotes fields that contain comma, quote or line break). */
public final class Csv {
    private Csv() { }

    public static String field(String s) {
        if (s == null) return "";
        // neutralise spreadsheet formula injection for text that starts with = + - @
        String v = s;
        if (!v.isEmpty() && "=+@".indexOf(v.charAt(0)) >= 0) v = "'" + v;
        if (v.indexOf(',') >= 0 || v.indexOf('"') >= 0 || v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0)
            return "\"" + v.replace("\"", "\"\"") + "\"";
        return v;
    }

    public static void row(StringBuilder sb, String... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(field(cells[i]));
        }
        sb.append("\r\n");
    }

    /** Appends a titled section (title line, then the table whose row 0 is the heading), followed by a blank line. */
    public static void section(StringBuilder sb, String title, List<String[]> table) {
        row(sb, title);
        for (String[] r : table) row(sb, r);
        sb.append("\r\n");
    }
}
