package com.spectragraph.superscript;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles detection, measurement, stretchy rendering, and caret positioning
 * for Floor (⌊ ⌋) and Ceiling (⌈ ⌉) mathematical delimiters.
 * The floor symbol is drawn as the ceiling symbol upside down.
 */
public class FloorAndCeilingSuperScript {

    public static class FloorCeilRegion {
        public int openChar = -1;
        public int closeChar = -1;
        public boolean isCeil = false; // false = floor (⌊⌋), true = ceil (⌈⌉)
        public int cachedWidth = -1;

        public FloorCeilRegion() {}

        public FloorCeilRegion(FloorCeilRegion other) {
            if (other != null) {
                this.openChar = other.openChar;
                this.closeChar = other.closeChar;
                this.isCeil = other.isCeil;
            }
        }
    }

    public static FloorCeilRegion[] findRegions(String text) {
        return findRegions(text, null, null, null, null, null, null, null);
    }

    public static FloorCeilRegion[] findRegions(String text, FractionsSuperscript.FractionRegion[] fractions,
            RootsSuperscript.RootRegion[] roots, ExponentsSuperscript.PowerRegion[] powers,
            IntegralsSuperscript.IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations,
            LogsSuperScript.LogRegion[] logs,
            DerivativesSuperScript.DerivativeRegion[] derivatives) {
        if (text == null || text.isEmpty()) {
            return new FloorCeilRegion[0];
        }
        boolean[] reserved = new boolean[text.length()];
        if (fractions != null) {
            for (FractionsSuperscript.FractionRegion f : fractions) {
                if (f == null) continue;
                if (f.numOpen >= 0 && f.numOpen < reserved.length) reserved[f.numOpen] = true;
                if (f.numClose >= 0 && f.numClose < reserved.length) reserved[f.numClose] = true;
                if (f.slashChar >= 0 && f.slashChar < reserved.length) reserved[f.slashChar] = true;
                if (f.denOpen >= 0 && f.denOpen < reserved.length) reserved[f.denOpen] = true;
                if (f.denClose >= 0 && f.denClose < reserved.length) reserved[f.denClose] = true;
            }
        }
        if (roots != null) {
            for (RootsSuperscript.RootRegion r : roots) {
                if (r == null) continue;
                if (r.sqrtChar >= 0 && r.sqrtChar < reserved.length) reserved[r.sqrtChar] = true;
                if (r.bracketOpen >= 0 && r.bracketOpen < reserved.length) reserved[r.bracketOpen] = true;
                if (r.bracketClose >= 0 && r.bracketClose < reserved.length) reserved[r.bracketClose] = true;
                if (r.parenOpen >= 0 && r.parenOpen < reserved.length) reserved[r.parenOpen] = true;
                if (r.parenClose >= 0 && r.parenClose < reserved.length) reserved[r.parenClose] = true;
            }
        }
        if (powers != null) {
            for (ExponentsSuperscript.PowerRegion p : powers) {
                if (p == null) continue;
                if (p.caretChar >= 0 && p.caretChar < reserved.length) reserved[p.caretChar] = true;
                if (p.openParen >= 0 && p.openParen < reserved.length) reserved[p.openParen] = true;
                if (p.closeParen >= 0 && p.closeParen < reserved.length) reserved[p.closeParen] = true;
            }
        }
        if (integrals != null) {
            for (IntegralsSuperscript.IntegralRegion it : integrals) {
                if (it == null) continue;
                if (it.signs != null && !it.signs.isEmpty()) {
                    for (IntegralsSuperscript.IntegralSign s : it.signs) {
                        if (s.intChar >= 0 && s.intChar < reserved.length) reserved[s.intChar] = true;
                        if (s.upperOpen >= 0 && s.upperOpen < reserved.length) reserved[s.upperOpen] = true;
                        if (s.upperClose >= 0 && s.upperClose < reserved.length) reserved[s.upperClose] = true;
                        if (s.lowerOpen >= 0 && s.lowerOpen < reserved.length) reserved[s.lowerOpen] = true;
                        if (s.lowerClose >= 0 && s.lowerClose < reserved.length) reserved[s.lowerClose] = true;
                    }
                } else {
                    if (it.intChar >= 0 && it.intChar < reserved.length) reserved[it.intChar] = true;
                    if (it.upperOpen >= 0 && it.upperOpen < reserved.length) reserved[it.upperOpen] = true;
                    if (it.upperClose >= 0 && it.upperClose < reserved.length) reserved[it.upperClose] = true;
                    if (it.lowerOpen >= 0 && it.lowerOpen < reserved.length) reserved[it.lowerOpen] = true;
                    if (it.lowerClose >= 0 && it.lowerClose < reserved.length) reserved[it.lowerClose] = true;
                }
                if (it.parenOpen >= 0 && it.parenOpen < reserved.length) reserved[it.parenOpen] = true;
                if (it.parenClose >= 0 && it.parenClose < reserved.length) reserved[it.parenClose] = true;
                if (it.dxStart >= 0 && it.dxStart < reserved.length) reserved[it.dxStart] = true;
                if (it.dxEnd > 0 && it.dxEnd <= reserved.length) reserved[it.dxEnd - 1] = true;
            }
        }
        if (summations != null) {
            for (SummatoriesSuperScript.SumRegion s : summations) {
                if (s == null) continue;
                if (s.lowerOpen >= 0 && s.lowerOpen < reserved.length) reserved[s.lowerOpen] = true;
                if (s.lowerClose >= 0 && s.lowerClose < reserved.length) reserved[s.lowerClose] = true;
                if (s.upperOpen >= 0 && s.upperOpen < reserved.length) reserved[s.upperOpen] = true;
                if (s.upperClose >= 0 && s.upperClose < reserved.length) reserved[s.upperClose] = true;
                if (s.parenOpen >= 0 && s.parenOpen < reserved.length) reserved[s.parenOpen] = true;
                if (s.parenClose >= 0 && s.parenClose < reserved.length) reserved[s.parenClose] = true;
            }
        }
        if (logs != null) {
            for (LogsSuperScript.LogRegion lg : logs) {
                if (lg == null) continue;
                if (lg.bracketOpen >= 0 && lg.bracketOpen < reserved.length) reserved[lg.bracketOpen] = true;
                if (lg.bracketClose >= 0 && lg.bracketClose < reserved.length) reserved[lg.bracketClose] = true;
                if (lg.parenOpen >= 0 && lg.parenOpen < reserved.length) reserved[lg.parenOpen] = true;
                if (lg.parenClose >= 0 && lg.parenClose < reserved.length) reserved[lg.parenClose] = true;
            }
        }
        if (derivatives != null) {
            for (DerivativesSuperScript.DerivativeRegion dr : derivatives) {
                if (dr == null) continue;
                if (dr.parenOpen >= 0 && dr.parenOpen < reserved.length) reserved[dr.parenOpen] = true;
                if (dr.parenClose >= 0 && dr.parenClose < reserved.length) reserved[dr.parenClose] = true;
            }
        }

        List<FloorCeilRegion> list = new ArrayList<>();
        int[] floorStack = new int[text.length()];
        int floorTop = 0;
        int[] ceilStack = new int[text.length()];
        int ceilTop = 0;

        for (int i = 0; i < text.length(); i++) {
            if (reserved[i]) continue;
            char ch = text.charAt(i);
            if (ch == '\u230A' || ch == '⌊') {
                floorStack[floorTop++] = i;
            } else if (ch == '\u230B' || ch == '⌋') {
                if (floorTop > 0) {
                    int open = floorStack[--floorTop];
                    FloorCeilRegion r = new FloorCeilRegion();
                    r.openChar = open;
                    r.closeChar = i;
                    r.isCeil = false;
                    list.add(r);
                }
            } else if (ch == '\u2308' || ch == '⌈') {
                ceilStack[ceilTop++] = i;
            } else if (ch == '\u2309' || ch == '⌉') {
                if (ceilTop > 0) {
                    int open = ceilStack[--ceilTop];
                    FloorCeilRegion r = new FloorCeilRegion();
                    r.openChar = open;
                    r.closeChar = i;
                    r.isCeil = true;
                    list.add(r);
                }
            }
        }
        return list.toArray(new FloorCeilRegion[0]);
    }

    public static FloorCeilRegion getAt(FloorCeilRegion[] regions, int i, int end) {
        if (regions == null) return null;
        for (FloorCeilRegion r : regions) {
            if (r != null && r.openChar == i && r.closeChar < end) {
                return r;
            }
        }
        return null;
    }

    public static boolean isStretchy(Graphics2D g2, String text, FloorCeilRegion fcr, Font font,
            FractionsSuperscript.FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations, LogsSuperScript.LogRegion[] logs,
            DerivativesSuperScript.DerivativeRegion[] derivatives,
            FloorCeilRegion[] floorCeils,
            AbsoluteValueSuperScript.AbsoluteValueRegion[] absoluteValues,
            ParensSuperscript.ParenRegion[] parens, int nestLevel) {
        FontMetrics fm = g2.getFontMetrics(font);
        int cAscent = MathRenderEngine.getRegionAscent(g2, text, fcr.openChar + 1, fcr.closeChar,
                font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
        int cDescent = MathRenderEngine.getRegionDescent(g2, text, fcr.openChar + 1, fcr.closeChar,
                font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
        return cAscent > (int) (fm.getAscent() * 1.15f) || cDescent > (int) (fm.getDescent() * 1.15f);
    }

    /**
     * Draws a floor bracket (⌊ or ⌋): vertical bar with horizontal tick at bottom pointing inward.
     * This is exactly the ceiling bracket drawn upside down.
     */
    public static void drawStretchyFloor(Graphics2D g2, int x, int topY, int bottomY, int width, boolean isOpen) {
        Stroke oldStroke = g2.getStroke();
        g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int tickW = Math.max(4, width - 3);
        if (isOpen) { // ⌊
            g2.drawLine(x + 2, topY, x + 2, bottomY);
            g2.drawLine(x + 2, bottomY, x + tickW, bottomY);
        } else { // ⌋
            g2.drawLine(x + width - 2, topY, x + width - 2, bottomY);
            g2.drawLine(x + width - 2, bottomY, x + width - tickW, bottomY);
        }
        g2.setStroke(oldStroke);
    }

    /**
     * Draws a ceiling bracket (⌈ or ⌉): vertical bar with horizontal tick at top pointing inward.
     */
    public static void drawStretchyCeil(Graphics2D g2, int x, int topY, int bottomY, int width, boolean isOpen) {
        Stroke oldStroke = g2.getStroke();
        g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int tickW = Math.max(4, width - 3);
        if (isOpen) { // ⌈
            g2.drawLine(x + 2, topY, x + tickW, topY);
            g2.drawLine(x + 2, topY, x + 2, bottomY);
        } else { // ⌉
            g2.drawLine(x + width - tickW, topY, x + width - 2, topY);
            g2.drawLine(x + width - 2, topY, x + width - 2, bottomY);
        }
        g2.setStroke(oldStroke);
    }

    public static int getAscent(Graphics2D g2, String text, FloorCeilRegion fcr, Font font,
            FractionsSuperscript.FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations, LogsSuperScript.LogRegion[] logs,
            DerivativesSuperScript.DerivativeRegion[] derivatives,
            FloorCeilRegion[] floorCeils,
            AbsoluteValueSuperScript.AbsoluteValueRegion[] absoluteValues,
            ParensSuperscript.ParenRegion[] parens, int nestLevel) {
        FontMetrics fm = g2.getFontMetrics(font);
        int cAscent = MathRenderEngine.getRegionAscent(g2, text, fcr.openChar + 1, fcr.closeChar,
                font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
        return Math.max(fm.getAscent(), cAscent) + 2;
    }

    public static int getDescent(Graphics2D g2, String text, FloorCeilRegion fcr, Font font,
            FractionsSuperscript.FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations, LogsSuperScript.LogRegion[] logs,
            DerivativesSuperScript.DerivativeRegion[] derivatives,
            FloorCeilRegion[] floorCeils,
            AbsoluteValueSuperScript.AbsoluteValueRegion[] absoluteValues,
            ParensSuperscript.ParenRegion[] parens, int nestLevel) {
        FontMetrics fm = g2.getFontMetrics(font);
        int cDescent = MathRenderEngine.getRegionDescent(g2, text, fcr.openChar + 1, fcr.closeChar,
                font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
        return Math.max(fm.getDescent(), cDescent) + 2;
    }

    public static int render(Graphics2D g2, String text, FloorCeilRegion fcr, int curX, int baselineY,
            Font font, boolean draw, FractionsSuperscript.FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations, LogsSuperScript.LogRegion[] logs,
            DerivativesSuperScript.DerivativeRegion[] derivatives,
            FloorCeilRegion[] floorCeils,
            AbsoluteValueSuperScript.AbsoluteValueRegion[] absoluteValues,
            ParensSuperscript.ParenRegion[] parens, int nestLevel, int selStart, int selEnd) {
        FontMetrics fm = g2.getFontMetrics(font);
        int cAscent = MathRenderEngine.getRegionAscent(g2, text, fcr.openChar + 1, fcr.closeChar,
                font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
        int cDescent = MathRenderEngine.getRegionDescent(g2, text, fcr.openChar + 1, fcr.closeChar,
                font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);

        int topY = baselineY - Math.max(fm.getAscent(), cAscent) - 2;
        int bottomY = baselineY + Math.max(fm.getDescent(), cDescent) + 2;

        int h = bottomY - topY;
        int parenW = Math.max(fm.charWidth(text.charAt(fcr.openChar)), Math.min(13, 6 + h / 20));
        int contentW = MathRenderEngine.renderSubString(g2, text, fcr.openChar + 1, fcr.closeChar, 0, 0,
                font, false, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel, -1, -1);
        int totalW = parenW + contentW + parenW;

        if (draw) {
            if (fcr.isCeil) {
                drawStretchyCeil(g2, curX, topY, bottomY, parenW, true);
            } else {
                drawStretchyFloor(g2, curX, topY, bottomY, parenW, true);
            }

            MathRenderEngine.renderSubString(g2, text, fcr.openChar + 1, fcr.closeChar, curX + parenW, baselineY,
                    font, true, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel, selStart, selEnd);

            if (fcr.isCeil) {
                drawStretchyCeil(g2, curX + parenW + contentW, topY, bottomY, parenW, false);
            } else {
                drawStretchyFloor(g2, curX + parenW + contentW, topY, bottomY, parenW, false);
            }

            if (selStart <= fcr.openChar && selEnd > fcr.openChar) {
                g2.setColor(new Color(130, 160, 210, 100));
                g2.fillRect(curX, topY, parenW, bottomY - topY);
                g2.setColor(Color.WHITE);
            }
            if (selStart <= fcr.closeChar && selEnd > fcr.closeChar) {
                g2.setColor(new Color(130, 160, 210, 100));
                g2.fillRect(curX + parenW + contentW, topY, parenW, bottomY - topY);
                g2.setColor(Color.WHITE);
            }
        }

        if (fcr != null) fcr.cachedWidth = totalW;
        return curX + totalW;
    }

    public static int[] getCaretInfo(Graphics2D g2, String text, FloorCeilRegion fcr, int curX,
            int targetOffset, Font font, FractionsSuperscript.FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations, LogsSuperScript.LogRegion[] logs,
            DerivativesSuperScript.DerivativeRegion[] derivatives,
            FloorCeilRegion[] floorCeils,
            AbsoluteValueSuperScript.AbsoluteValueRegion[] absoluteValues,
            ParensSuperscript.ParenRegion[] parens, int nestLevel) {
        if (fcr == null || targetOffset < fcr.openChar || targetOffset > fcr.closeChar) {
            return null;
        }
        FontMetrics fm = g2.getFontMetrics(font);
        int cAscent = MathRenderEngine.getRegionAscent(g2, text, fcr.openChar + 1, fcr.closeChar,
                font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
        int cDescent = MathRenderEngine.getRegionDescent(g2, text, fcr.openChar + 1, fcr.closeChar,
                font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);

        int asc = Math.max(fm.getAscent(), cAscent) + 2;
        int desc = Math.max(fm.getDescent(), cDescent) + 2;
        int topY = -asc;
        int bottomY = desc;

        int h = bottomY - topY;
        int parenW = Math.max(fm.charWidth(text.charAt(fcr.openChar)), Math.min(13, 6 + h / 20));
        int contentW = MathRenderEngine.renderSubString(g2, text, fcr.openChar + 1, fcr.closeChar, 0, 0,
                font, false, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel, -1, -1);

        if (targetOffset == fcr.openChar) {
            return new int[] { curX, 0, asc, desc };
        }
        if (targetOffset > fcr.openChar && targetOffset <= fcr.closeChar) {
            if (targetOffset == fcr.closeChar) {
                return new int[] { curX + parenW + contentW, 0, asc, desc };
            }
            int[] sub = MathRenderEngine.getCaretInfoSubString(g2, text, fcr.openChar + 1, fcr.closeChar,
                    targetOffset, font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
            if (sub != null) {
                return new int[] { curX + parenW + sub[0], sub[1], sub[2], sub[3] };
            }
            return new int[] { curX + parenW, 0, fm.getAscent(), fm.getDescent() };
        }
        return null;
    }
}
