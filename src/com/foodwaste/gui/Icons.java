package com.foodwaste.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.FlatteningPathIterator;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.swing.Icon;

/** One consistent set of line icons drawn with Java2D (no image files, works offline, scales without blur). */
public final class Icons {
    private Icons() { }

    public enum Kind {
        LEAF, CHECK, CROSS, WARN, INFO, QUESTION, TRASH, DASH, PLUS, LIST, INBOX, USERS, MAIL, LOCK, SEARCH,
        REFRESH, LOGOUT, SHIELD, BOWL, SEND, ACTIVITY, FILE, PIN, CLOCK, USER, CHEVRON_DOWN, ARROW_RIGHT
    }

    /** Shapes of one icon on a 24 x 24 grid: stroked outlines and filled dots. */
    private static final class Def {
        final List<Shape> strokes = new ArrayList<>();
        final List<Shape> dots = new ArrayList<>();
        final List<Float> lengths = new ArrayList<>();

        void stroke(Shape s) { strokes.add(s); lengths.add(length(s)); }

        void dot(double cx, double cy, double r) { dots.add(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r)); }
    }

    private static final Map<Kind, Def> CACHE = new EnumMap<>(Kind.class);

    private static synchronized Def def(Kind k) {
        Def d = CACHE.get(k);
        if (d == null) { d = build(k); CACHE.put(k, d); }
        return d;
    }

    // ------------------------------------------------------------------ drawing

    /** Draws the icon inside the square (x, y, size). progress 0..1 animates the outline being drawn. */
    public static void paint(Graphics2D g0, Kind k, double x, double y, double size, Color c, float progress, double strokeWidth) {
        if (progress <= 0f) return;
        Def d = def(k);
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.translate(x, y);
        double s = size / 24.0;
        g.scale(s, s);
        g.setColor(c);
        for (int i = 0; i < d.strokes.size(); i++) {
            if (progress >= 1f) {
                g.setStroke(new BasicStroke((float) strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            } else {
                float a = d.lengths.get(i) + 0.5f;
                g.setStroke(new BasicStroke((float) strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f,
                    new float[] {a, a}, a * (1f - progress)));
            }
            g.draw(d.strokes.get(i));
        }
        if (progress > 0.6f) {
            int alpha = Math.min(255, (int) (255 * (progress - 0.6f) / 0.4f));
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha * c.getAlpha() / 255));
            for (Shape dot : d.dots) g.fill(dot);
        }
        g.dispose();
    }

    public static void paint(Graphics2D g, Kind k, double x, double y, double size, Color c) {
        paint(g, k, x, y, size, c, 1f, 1.9);
    }

    public static Icon icon(final Kind k, final int size, final Color c) {
        return new Icon() {
            @Override public void paintIcon(Component comp, Graphics g, int x, int y) { Icons.paint((Graphics2D) g, k, x, y, size, c); }
            @Override public int getIconWidth() { return size; }
            @Override public int getIconHeight() { return size; }
        };
    }

    /** Rounded green tile with a white leaf: used as the window icon. */
    public static BufferedImage appIcon(int px) {
        BufferedImage img = new BufferedImage(px, px, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setPaint(new java.awt.GradientPaint(0, 0, new Color(0x22C55E), px, px, new Color(0x166534)));
        g.fill(new RoundRectangle2D.Double(0, 0, px, px, px * 0.28, px * 0.28));
        paint(g, Kind.LEAF, px * 0.2, px * 0.2, px * 0.6, Color.WHITE, 1f, 2.0);
        g.dispose();
        return img;
    }

    // ------------------------------------------------------------------ shape helpers

    private static float length(Shape s) {
        FlatteningPathIterator it = new FlatteningPathIterator(s.getPathIterator(null), 0.05);
        double[] c = new double[6];
        double lx = 0, ly = 0, sx = 0, sy = 0, len = 0;
        while (!it.isDone()) {
            int t = it.currentSegment(c);
            if (t == java.awt.geom.PathIterator.SEG_MOVETO) { lx = sx = c[0]; ly = sy = c[1]; }
            else if (t == java.awt.geom.PathIterator.SEG_LINETO) { len += Math.hypot(c[0] - lx, c[1] - ly); lx = c[0]; ly = c[1]; }
            else if (t == java.awt.geom.PathIterator.SEG_CLOSE) { len += Math.hypot(sx - lx, sy - ly); lx = sx; ly = sy; }
            it.next();
        }
        return (float) len;
    }

    private static Path2D poly(double... p) {
        Path2D.Double path = new Path2D.Double();
        path.moveTo(p[0], p[1]);
        for (int i = 2; i < p.length; i += 2) path.lineTo(p[i], p[i + 1]);
        return path;
    }

    private static Path2D closed(double... p) { Path2D path = poly(p); path.closePath(); return path; }

    private static Shape line(double x1, double y1, double x2, double y2) { return new Line2D.Double(x1, y1, x2, y2); }

    private static Shape circle(double cx, double cy, double r) { return new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r); }

    private static Shape rrect(double x, double y, double w, double h, double r) { return new RoundRectangle2D.Double(x, y, w, h, 2 * r, 2 * r); }

    private static Def build(Kind k) {
        Def d = new Def();
        switch (k) {
            case LEAF: {
                Path2D p = new Path2D.Double();
                p.moveTo(4.5, 19.5);
                p.curveTo(3.2, 10.5, 9, 4.6, 20, 4);
                p.curveTo(20.6, 15, 14.8, 20.8, 4.5, 19.5);
                p.closePath();
                d.stroke(p);
                d.stroke(line(4.5, 19.5, 13.5, 10.5));
                break;
            }
            case CHECK: d.stroke(poly(5, 12.5, 10, 17.5, 19, 7)); break;
            case CROSS: d.stroke(line(6, 6, 18, 18)); d.stroke(line(18, 6, 6, 18)); break;
            case WARN:
                d.stroke(closed(12, 3.8, 21.2, 19.8, 2.8, 19.8));
                d.stroke(line(12, 10, 12, 14));
                d.dot(12, 16.9, 1.1);
                break;
            case INFO:
                d.stroke(circle(12, 12, 9));
                d.stroke(line(12, 11, 12, 16.5));
                d.dot(12, 7.9, 1.1);
                break;
            case QUESTION: {
                d.stroke(circle(12, 12, 9));
                Path2D q = new Path2D.Double();
                q.moveTo(9.3, 9.6);
                q.curveTo(9.3, 7.9, 10.6, 7.1, 12, 7.1);
                q.curveTo(13.5, 7.1, 14.8, 8.1, 14.8, 9.5);
                q.curveTo(14.8, 11.5, 12, 11.5, 12, 13.6);
                d.stroke(q);
                d.dot(12, 16.8, 1.1);
                break;
            }
            case TRASH:
                d.stroke(line(4, 7, 20, 7));
                d.stroke(poly(6, 7, 7, 20, 17, 20, 18, 7));
                d.stroke(poly(9, 7, 9, 4, 15, 4, 15, 7));
                d.stroke(line(10, 11, 10, 16));
                d.stroke(line(14, 11, 14, 16));
                break;
            case DASH:
                d.stroke(rrect(3, 3, 7.5, 9, 2)); d.stroke(rrect(13.5, 3, 7.5, 5, 2));
                d.stroke(rrect(13.5, 11, 7.5, 10, 2)); d.stroke(rrect(3, 15, 7.5, 6, 2));
                break;
            case PLUS:
                d.stroke(circle(12, 12, 9)); d.stroke(line(12, 8, 12, 16)); d.stroke(line(8, 12, 16, 12));
                break;
            case LIST:
                d.stroke(line(9, 6, 20, 6)); d.stroke(line(9, 12, 20, 12)); d.stroke(line(9, 18, 20, 18));
                d.dot(4.5, 6, 1.2); d.dot(4.5, 12, 1.2); d.dot(4.5, 18, 1.2);
                break;
            case INBOX:
                d.stroke(poly(3, 13, 7, 13, 9, 16, 15, 16, 17, 13, 21, 13));
                d.stroke(poly(6, 5, 18, 5, 21, 13, 21, 19, 3, 19, 3, 13, 6, 5));
                break;
            case USERS: {
                d.stroke(circle(9, 8, 3.6));
                Path2D s1 = new Path2D.Double();
                s1.moveTo(2.5, 20); s1.curveTo(2.5, 16.4, 5.5, 14.4, 9, 14.4); s1.curveTo(12.5, 14.4, 15.5, 16.4, 15.5, 20);
                d.stroke(s1);
                d.stroke(circle(17, 8.6, 2.6));
                Path2D s2 = new Path2D.Double();
                s2.moveTo(17.6, 14.4); s2.curveTo(20, 14.8, 21.5, 16.8, 21.5, 19.6);
                d.stroke(s2);
                break;
            }
            case MAIL:
                d.stroke(rrect(3, 5, 18, 14, 3));
                d.stroke(poly(4, 7.5, 12, 13, 20, 7.5));
                break;
            case LOCK: {
                d.stroke(rrect(5, 11, 14, 10, 2.5));
                Path2D sh = new Path2D.Double();
                sh.moveTo(8, 11); sh.lineTo(8, 8); sh.curveTo(8, 5.8, 9.8, 4, 12, 4); sh.curveTo(14.2, 4, 16, 5.8, 16, 8); sh.lineTo(16, 11);
                d.stroke(sh);
                d.dot(12, 16, 1.2);
                break;
            }
            case SEARCH:
                d.stroke(circle(10.5, 10.5, 6.5)); d.stroke(line(15.5, 15.5, 20, 20));
                break;
            case REFRESH: {
                d.stroke(new java.awt.geom.Arc2D.Double(4.5, 4.5, 15, 15, 60, 270, java.awt.geom.Arc2D.OPEN));
                // arrow head at the end of the arc (angle 330 degrees)
                double ex = 12 + 7.5 * Math.cos(Math.toRadians(330)), ey = 12 - 7.5 * Math.sin(Math.toRadians(330));
                d.stroke(poly(ex + 0.5, ey - 4.2, ex, ey, ex + 4.2, ey - 0.6));
                break;
            }
            case LOGOUT:
                d.stroke(poly(10, 4, 5, 4, 5, 20, 10, 20));
                d.stroke(line(10, 12, 20, 12));
                d.stroke(poly(16, 8, 20, 12, 16, 16));
                break;
            case SHIELD: {
                Path2D s = new Path2D.Double();
                s.moveTo(12, 3); s.lineTo(20, 6); s.lineTo(20, 12); s.curveTo(20, 17, 16, 20, 12, 21.5);
                s.curveTo(8, 20, 4, 17, 4, 12); s.lineTo(4, 6); s.closePath();
                d.stroke(s);
                d.stroke(poly(8.5, 12, 11, 14.5, 15.5, 9.5));
                break;
            }
            case BOWL: {
                d.stroke(line(3, 11, 21, 11));
                Path2D b = new Path2D.Double();
                b.moveTo(4, 11); b.curveTo(4, 17, 8, 20, 12, 20); b.curveTo(16, 20, 20, 17, 20, 11);
                d.stroke(b);
                d.stroke(line(9, 4, 9, 8)); d.stroke(line(15, 4, 15, 8));
                break;
            }
            case SEND:
                d.stroke(closed(21, 3, 3, 10.5, 10, 13.5, 13, 21));
                d.stroke(line(10, 13.5, 21, 3));
                break;
            case ACTIVITY: d.stroke(poly(2, 12, 6, 12, 9, 4, 15, 20, 18, 12, 22, 12)); break;
            case FILE:
                d.stroke(poly(14, 3, 6, 3, 6, 21, 19, 21, 19, 8, 14, 3, 14, 8, 19, 8));
                d.stroke(line(9.5, 13, 15.5, 13)); d.stroke(line(9.5, 17, 15.5, 17));
                break;
            case PIN: {
                Path2D p = new Path2D.Double();
                p.moveTo(12, 21); p.curveTo(8, 17, 5, 13.5, 5, 9.5); p.curveTo(5, 5.6, 8.1, 3, 12, 3);
                p.curveTo(15.9, 3, 19, 5.6, 19, 9.5); p.curveTo(19, 13.5, 16, 17, 12, 21);
                d.stroke(p);
                d.stroke(circle(12, 9.5, 2.6));
                break;
            }
            case CLOCK:
                d.stroke(circle(12, 12, 9)); d.stroke(poly(12, 7, 12, 12, 15.5, 14));
                break;
            case USER: {
                d.stroke(circle(12, 8, 4));
                Path2D s = new Path2D.Double();
                s.moveTo(4.5, 20.5); s.curveTo(4.5, 16.5, 8, 14.5, 12, 14.5); s.curveTo(16, 14.5, 19.5, 16.5, 19.5, 20.5);
                d.stroke(s);
                break;
            }
            case CHEVRON_DOWN: d.stroke(poly(7, 10, 12, 15, 17, 10)); break;
            case ARROW_RIGHT: d.stroke(line(5, 12, 19, 12)); d.stroke(poly(13, 6, 19, 12, 13, 18)); break;
            default: break;
        }
        return d;
    }
}
