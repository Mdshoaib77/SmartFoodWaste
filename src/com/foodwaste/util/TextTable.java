package com.foodwaste.util;

import java.util.List;

/** Renders string rows (row 0 = headings) as a plain-ASCII table. */
public final class TextTable {
    private static final int MAX_COL = 36;

    private TextTable() { }

    public static String render(List<String[]> data) {
        if (data == null || data.size() <= 1) return "  (no data)\n";
        int cols = data.get(0).length;
        int[] w = new int[cols];
        for (String[] row : data)
            for (int i = 0; i < cols; i++) w[i] = Math.min(MAX_COL, Math.max(w[i], cell(row[i]).length()));
        StringBuilder sb = new StringBuilder();
        String sep = separator(w);
        sb.append(sep);
        line(sb, data.get(0), w);
        sb.append(sep);
        for (int r = 1; r < data.size(); r++) line(sb, data.get(r), w);
        sb.append(sep);
        return sb.toString();
    }

    private static String cell(String s) {
        if (s == null) return "-";
        s = s.replace('\n', ' ');
        return s.length() > MAX_COL ? s.substring(0, MAX_COL - 2) + ".." : s;
    }

    private static void line(StringBuilder sb, String[] row, int[] w) {
        sb.append('|');
        for (int i = 0; i < w.length; i++) {
            String c = cell(row[i]);
            sb.append(' ').append(c);
            for (int k = c.length(); k < w[i]; k++) sb.append(' ');
            sb.append(" |");
        }
        sb.append('\n');
    }

    private static String separator(int[] w) {
        StringBuilder sb = new StringBuilder("+");
        for (int x : w) {
            for (int k = 0; k < x + 2; k++) sb.append('-');
            sb.append('+');
        }
        return sb.append('\n').toString();
    }
}
