package com.spectragraph.superscript;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * Handles detection, measurement, stretchy rendering, and caret positioning
 * for Absolute Value (| ... |) mathematical delimiters.
 * As the argument gets bigger (e.g. fractions, roots, powers), the vertical bars stretch dynamically.
 */
public class AbsoluteValueSuperScript {

    public static class AbsoluteValueRegion {
        public int openChar = -1;
        public int closeChar = -1;
        public int cachedWidth = -1;

        public AbsoluteValueRegion() {}

        public AbsoluteValueRegion(AbsoluteValueRegion other) {
            if (other != null) {
                this.openChar = other.openChar;
                this.closeChar = other.closeChar;
            }
        }
    }

    public static AbsoluteValueRegion[] findRegions(String text) {
        return findRegions(text, null, null, null, null, null, null, null);
    }

    public static AbsoluteValueRegion[] findRegions(String text, FractionsSuperscript.FractionRegion[] fractions,
            RootsSuperscript.RootRegion[] roots, ExponentsSuperscript.PowerRegion[] powers,
            IntegralsSuperscript.IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations,
            LogsSuperScript.LogRegion[] logs,
            DerivativesSuperScript.DerivativeRegion[] derivatives) {
        if (text == null || text.isEmpty()) {
            return new AbsoluteValueRegion[0];
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

        List<AbsoluteValueRegion> list = new ArrayList<>();
        Stack<Integer> openStack = new Stack<>();

        int i = 0;
        while (i < text.length()) {
            if (reserved[i] || text.charAt(i) != '|') {
                i++;
                continue;
            }

            int runStart = i;
            while (i < text.length() && !reserved[i] && text.charAt(i) == '|') {
                i++;
            }
            int runEnd = i;
            int k = runEnd - runStart;

            int prevIdx = runStart - 1;
            while (prevIdx >= 0 && Character.isWhitespace(text.charAt(prevIdx))) {
                prevIdx--;
            }
            char prevChar = prevIdx >= 0 ? text.charAt(prevIdx) : '\0';
            boolean prevIsOpOrOpen = (prevIdx < 0 || isOpOrOpenChar(prevChar));

            int nextIdx = runEnd;
            while (nextIdx < text.length() && Character.isWhitespace(text.charAt(nextIdx))) {
                nextIdx++;
            }
            char nextChar = nextIdx < text.length() ? text.charAt(nextIdx) : '\0';
            boolean nextIsOpOrClose = (nextIdx >= text.length() || isOpOrCloseChar(nextChar));

            if (prevIsOpOrOpen && nextIsOpOrClose) {
                // Case 1: Empty or nested-empty bars, e.g. "||" or "||||"
                int numOpen = k / 2;
                int numClose = k - numOpen;
                for (int b = 0; b < numOpen; b++) {
                    openStack.push(runStart + b);
                }
                for (int b = 0; b < numClose; b++) {
                    if (!openStack.isEmpty()) {
                        int open = openStack.pop();
                        AbsoluteValueRegion r = new AbsoluteValueRegion();
                        r.openChar = open;
                        r.closeChar = runStart + numOpen + b;
                        list.add(r);
                    }
                }
            } else if (prevIsOpOrOpen) {
                // Case 2: Preceded by operator/open, followed by operand -> All are OPEN
                for (int b = 0; b < k; b++) {
                    openStack.push(runStart + b);
                }
            } else if (nextIsOpOrClose) {
                // Case 3: Preceded by operand, followed by operator/close -> All are CLOSE
                for (int b = 0; b < k; b++) {
                    if (!openStack.isEmpty()) {
                        int open = openStack.pop();
                        AbsoluteValueRegion r = new AbsoluteValueRegion();
                        r.openChar = open;
                        r.closeChar = runStart + b;
                        list.add(r);
                    }
                }
            } else {
                // Case 4: Preceded by operand and followed by operand, e.g. "|x||y|"
                int closes = Math.min(k, openStack.size());
                for (int b = 0; b < closes; b++) {
                    int open = openStack.pop();
                    AbsoluteValueRegion r = new AbsoluteValueRegion();
                    r.openChar = open;
                    r.closeChar = runStart + b;
                    list.add(r);
                }
                for (int b = closes; b < k; b++) {
                    openStack.push(runStart + b);
                }
            }
        }
        return list.toArray(new AbsoluteValueRegion[0]);
    }

    private static boolean isOpOrOpenChar(char c) {
        return c == '+' || c == '-' || c == '*' || c == '/' || c == '^' || c == '=' || c == ','
                || c == '(' || c == '[' || c == '{' || c == '\u230A' || c == '⌊' || c == '\u2308' || c == '⌈';
    }

    private static boolean isOpOrCloseChar(char c) {
        return c == '+' || c == '-' || c == '*' || c == '/' || c == '^' || c == '=' || c == ','
                || c == ')' || c == ']' || c == '}' || c == '\u230B' || c == '⌋' || c == '\u2309' || c == '⌉';
    }

    public static AbsoluteValueRegion getAt(AbsoluteValueRegion[] regions, int i, int end) {
        if (regions == null) return null;
        for (AbsoluteValueRegion r : regions) {
            if (r != null && r.openChar == i && r.closeChar < end) {
                return r;
            }
        }
        return null;
    }

    public static boolean isStretchy(Graphics2D g2, String text, AbsoluteValueRegion avr, Font font,
            FractionsSuperscript.FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations, LogsSuperScript.LogRegion[] logs,
            DerivativesSuperScript.DerivativeRegion[] derivatives,
            FloorAndCeilingSuperScript.FloorCeilRegion[] floorCeils,
            AbsoluteValueRegion[] absoluteValues,
            ParensSuperscript.ParenRegion[] parens, int nestLevel) {
        FontMetrics fm = g2.getFontMetrics(font);
        int cAscent = MathRenderEngine.getRegionAscent(g2, text, avr.openChar + 1, avr.closeChar,
                font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
        int cDescent = MathRenderEngine.getRegionDescent(g2, text, avr.openChar + 1, avr.closeChar,
                font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
        return cAscent > (int) (fm.getAscent() * 1.15f) || cDescent > (int) (fm.getDescent() * 1.15f);
    }

    /**
     * Draws a vertical bar for absolute value delimiters (|).
     */
    public static void drawStretchyBar(Graphics2D g2, int x, int topY, int bottomY, int width) {
        Stroke oldStroke = g2.getStroke();
        g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int barX = x + width / 2;
        g2.drawLine(barX, topY, barX, bottomY);
        g2.setStroke(oldStroke);
    }

    public static int getAscent(Graphics2D g2, String text, AbsoluteValueRegion avr, Font font,
            FractionsSuperscript.FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations, LogsSuperScript.LogRegion[] logs,
            DerivativesSuperScript.DerivativeRegion[] derivatives,
            FloorAndCeilingSuperScript.FloorCeilRegion[] floorCeils,
            AbsoluteValueRegion[] absoluteValues,
            ParensSuperscript.ParenRegion[] parens, int nestLevel) {
        FontMetrics fm = g2.getFontMetrics(font);
        int cAscent = MathRenderEngine.getRegionAscent(g2, text, avr.openChar + 1, avr.closeChar,
                font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
        return Math.max(fm.getAscent(), cAscent) + 2;
    }

    public static int getDescent(Graphics2D g2, String text, AbsoluteValueRegion avr, Font font,
            FractionsSuperscript.FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations, LogsSuperScript.LogRegion[] logs,
            DerivativesSuperScript.DerivativeRegion[] derivatives,
            FloorAndCeilingSuperScript.FloorCeilRegion[] floorCeils,
            AbsoluteValueRegion[] absoluteValues,
            ParensSuperscript.ParenRegion[] parens, int nestLevel) {
        FontMetrics fm = g2.getFontMetrics(font);
        int cDescent = MathRenderEngine.getRegionDescent(g2, text, avr.openChar + 1, avr.closeChar,
                font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
        return Math.max(fm.getDescent(), cDescent) + 2;
    }

    public static int render(Graphics2D g2, String text, AbsoluteValueRegion avr, int curX, int baselineY,
            Font font, boolean draw, FractionsSuperscript.FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations, LogsSuperScript.LogRegion[] logs,
            DerivativesSuperScript.DerivativeRegion[] derivatives,
            FloorAndCeilingSuperScript.FloorCeilRegion[] floorCeils,
            AbsoluteValueRegion[] absoluteValues,
            ParensSuperscript.ParenRegion[] parens, int nestLevel, int selStart, int selEnd) {
        FontMetrics fm = g2.getFontMetrics(font);
        int cAscent = MathRenderEngine.getRegionAscent(g2, text, avr.openChar + 1, avr.closeChar,
                font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
        int cDescent = MathRenderEngine.getRegionDescent(g2, text, avr.openChar + 1, avr.closeChar,
                font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);

        int topY = baselineY - Math.max(fm.getAscent(), cAscent) - 2;
        int bottomY = baselineY + Math.max(fm.getDescent(), cDescent) + 2;

        int h = bottomY - topY;
        int barW = Math.max(fm.charWidth('|'), Math.min(10, 6 + h / 30));
        int contentW = MathRenderEngine.renderSubString(g2, text, avr.openChar + 1, avr.closeChar, 0, 0,
                font, false, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel, -1, -1);
        int totalW = barW + contentW + barW;

        if (draw) {
            drawStretchyBar(g2, curX, topY, bottomY, barW);

            MathRenderEngine.renderSubString(g2, text, avr.openChar + 1, avr.closeChar, curX + barW, baselineY,
                    font, true, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel, selStart, selEnd);

            drawStretchyBar(g2, curX + barW + contentW, topY, bottomY, barW);

            if (selStart <= avr.openChar && selEnd > avr.openChar) {
                g2.setColor(new Color(130, 160, 210, 100));
                g2.fillRect(curX, topY, barW, bottomY - topY);
                g2.setColor(Color.WHITE);
            }
            if (selStart <= avr.closeChar && selEnd > avr.closeChar) {
                g2.setColor(new Color(130, 160, 210, 100));
                g2.fillRect(curX + barW + contentW, topY, barW, bottomY - topY);
                g2.setColor(Color.WHITE);
            }
        }

        if (avr != null) avr.cachedWidth = totalW;
        return curX + totalW;
    }

    public static int[] getCaretInfo(Graphics2D g2, String text, AbsoluteValueRegion avr, int curX,
            int targetOffset, Font font, FractionsSuperscript.FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations, LogsSuperScript.LogRegion[] logs,
            DerivativesSuperScript.DerivativeRegion[] derivatives,
            FloorAndCeilingSuperScript.FloorCeilRegion[] floorCeils,
            AbsoluteValueRegion[] absoluteValues,
            ParensSuperscript.ParenRegion[] parens, int nestLevel) {
        if (avr == null || targetOffset < avr.openChar || targetOffset > avr.closeChar) {
            return null;
        }
        FontMetrics fm = g2.getFontMetrics(font);
        int cAscent = MathRenderEngine.getRegionAscent(g2, text, avr.openChar + 1, avr.closeChar,
                font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
        int cDescent = MathRenderEngine.getRegionDescent(g2, text, avr.openChar + 1, avr.closeChar,
                font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);

        int asc = Math.max(fm.getAscent(), cAscent) + 2;
        int desc = Math.max(fm.getDescent(), cDescent) + 2;
        int topY = -asc;
        int bottomY = desc;

        int h = bottomY - topY;
        int barW = Math.max(fm.charWidth('|'), Math.min(10, 6 + h / 30));
        int contentW = MathRenderEngine.renderSubString(g2, text, avr.openChar + 1, avr.closeChar, 0, 0,
                font, false, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel, -1, -1);

        if (targetOffset == avr.openChar) {
            return new int[] { curX, 0, asc, desc };
        }
        if (targetOffset > avr.openChar && targetOffset <= avr.closeChar) {
            if (targetOffset == avr.closeChar) {
                return new int[] { curX + barW + contentW, 0, asc, desc };
            }
            int[] sub = MathRenderEngine.getCaretInfoSubString(g2, text, avr.openChar + 1, avr.closeChar,
                    targetOffset, font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
            if (sub != null) {
                return new int[] { curX + barW + sub[0], sub[1], sub[2], sub[3] };
            }
            return new int[] { curX + barW, 0, fm.getAscent(), fm.getDescent() };
        }
        return null;
    }
}
