package com.spectragraph.superscript;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles detection, stretchy paren/bracket drawing, measurement, caret positioning,
 * and rendering for matched delimiters (parentheses and brackets).
 */
public class ParensSuperscript {

    public static class ParenRegion {
        public int openChar = -1;
        public int closeChar = -1;
        public boolean isBracket = false;
        public int cachedAscent = -1;
        public int cachedDescent = -1;
        public int isStretchy = -1; // -1: uncomputed, 0: false, 1: true
        public int cachedWidth = -1;

        public ParenRegion() {}

        public ParenRegion(ParenRegion other) {
            if (other != null) {
                this.openChar = other.openChar;
                this.closeChar = other.closeChar;
                this.isBracket = other.isBracket;
                this.cachedAscent = other.cachedAscent;
                this.cachedDescent = other.cachedDescent;
                this.isStretchy = other.isStretchy;
            }
        }
    }

    public static ParenRegion[] findRegions(String text, FractionsSuperscript.FractionRegion[] fractions,
            RootsSuperscript.RootRegion[] roots, ExponentsSuperscript.PowerRegion[] powers,
            IntegralsSuperscript.IntegralRegion[] integrals) {
        return findRegions(text, fractions, roots, powers, integrals, null, null);
    }

    public static ParenRegion[] findRegions(String text, FractionsSuperscript.FractionRegion[] fractions,
            RootsSuperscript.RootRegion[] roots, ExponentsSuperscript.PowerRegion[] powers,
            IntegralsSuperscript.IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations) {
        return findRegions(text, fractions, roots, powers, integrals, summations, null);
    }

    public static ParenRegion[] findRegions(String text, FractionsSuperscript.FractionRegion[] fractions,
            RootsSuperscript.RootRegion[] roots, ExponentsSuperscript.PowerRegion[] powers,
            IntegralsSuperscript.IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations,
            LogsSuperScript.LogRegion[] logs) {
        return findRegions(text, fractions, roots, powers, integrals, summations, logs, null);
    }

    public static ParenRegion[] findRegions(String text, FractionsSuperscript.FractionRegion[] fractions,
            RootsSuperscript.RootRegion[] roots, ExponentsSuperscript.PowerRegion[] powers,
            IntegralsSuperscript.IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations,
            LogsSuperScript.LogRegion[] logs,
            DerivativesSuperScript.DerivativeRegion[] derivatives) {
        if (text == null || text.isEmpty())
            return new ParenRegion[0];
        boolean[] reserved = new boolean[text.length()];
        if (fractions != null) {
            for (FractionsSuperscript.FractionRegion f : fractions) {
                if (f == null) continue;
                if (f.numOpen >= 0 && f.numOpen < reserved.length)
                    reserved[f.numOpen] = true;
                if (f.numClose >= 0 && f.numClose < reserved.length)
                    reserved[f.numClose] = true;
                if (f.slashChar >= 0 && f.slashChar < reserved.length)
                    reserved[f.slashChar] = true;
                if (f.denOpen >= 0 && f.denOpen < reserved.length)
                    reserved[f.denOpen] = true;
                if (f.denClose >= 0 && f.denClose < reserved.length)
                    reserved[f.denClose] = true;
            }
        }
        if (roots != null) {
            for (RootsSuperscript.RootRegion r : roots) {
                if (r == null) continue;
                if (r.sqrtChar >= 0 && r.sqrtChar < reserved.length)
                    reserved[r.sqrtChar] = true;
                if (r.bracketOpen >= 0 && r.bracketOpen < reserved.length)
                    reserved[r.bracketOpen] = true;
                if (r.bracketClose >= 0 && r.bracketClose < reserved.length)
                    reserved[r.bracketClose] = true;
                if (r.parenOpen >= 0 && r.parenOpen < reserved.length)
                    reserved[r.parenOpen] = true;
                if (r.parenClose >= 0 && r.parenClose < reserved.length)
                    reserved[r.parenClose] = true;
            }
        }
        if (powers != null) {
            for (ExponentsSuperscript.PowerRegion p : powers) {
                if (p == null) continue;
                if (p.caretChar >= 0 && p.caretChar < reserved.length)
                    reserved[p.caretChar] = true;
                if (p.openParen != -1) {
                    if (p.openParen >= 0 && p.openParen < reserved.length)
                        reserved[p.openParen] = true;
                    if (p.closeParen >= 0 && p.closeParen < reserved.length)
                        reserved[p.closeParen] = true;
                }
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
                if (it.parenOpen >= 0 && it.parenOpen < reserved.length)
                    reserved[it.parenOpen] = true;
                if (it.parenClose >= 0 && it.parenClose < reserved.length)
                    reserved[it.parenClose] = true;
                if (it.dxStart >= 0 && it.dxStart < reserved.length)
                    reserved[it.dxStart] = true;
                if (it.dxEnd > 0 && it.dxEnd <= reserved.length)
                    reserved[it.dxEnd - 1] = true;

                // Also reserve any empty () that immediately precedes dxStart
                if (it.dxStart != -1) {
                    int p = it.dxStart - 1;
                    while (p >= 0 && Character.isWhitespace(text.charAt(p))) p--;
                    if (p >= 1 && text.charAt(p) == ')' && text.charAt(p - 1) == '(') {
                        reserved[p - 1] = true;
                        reserved[p] = true;
                    }
                }
            }
        }
        if (summations != null) {
            for (SummatoriesSuperScript.SumRegion s : summations) {
                if (s == null) continue;
                if (s.lowerOpen >= 0 && s.lowerOpen < reserved.length)
                    reserved[s.lowerOpen] = true;
                if (s.lowerClose >= 0 && s.lowerClose < reserved.length)
                    reserved[s.lowerClose] = true;
                if (s.upperOpen >= 0 && s.upperOpen < reserved.length)
                    reserved[s.upperOpen] = true;
                if (s.upperClose >= 0 && s.upperClose < reserved.length)
                    reserved[s.upperClose] = true;
                if (s.parenOpen >= 0 && s.parenOpen < reserved.length)
                    reserved[s.parenOpen] = true;
                if (s.parenClose >= 0 && s.parenClose < reserved.length)
                    reserved[s.parenClose] = true;
            }
        }
        if (logs != null) {
            for (LogsSuperScript.LogRegion lg : logs) {
                if (lg == null) continue;
                if (lg.bracketOpen >= 0 && lg.bracketOpen < reserved.length)
                    reserved[lg.bracketOpen] = true;
                if (lg.bracketClose >= 0 && lg.bracketClose < reserved.length)
                    reserved[lg.bracketClose] = true;
                if (lg.parenOpen >= 0 && lg.parenOpen < reserved.length)
                    reserved[lg.parenOpen] = true;
                if (lg.parenClose >= 0 && lg.parenClose < reserved.length)
                    reserved[lg.parenClose] = true;
            }
        }
        if (derivatives != null) {
            for (DerivativesSuperScript.DerivativeRegion dr : derivatives) {
                if (dr == null) continue;
                if (dr.parenOpen >= 0 && dr.parenOpen < reserved.length)
                    reserved[dr.parenOpen] = true;
                if (dr.parenClose >= 0 && dr.parenClose < reserved.length)
                    reserved[dr.parenClose] = true;
            }
        }

        List<ParenRegion> list = new ArrayList<>();
        int[] parenStack = new int[text.length()];
        int parenTop = 0;
        int[] bracketStack = new int[text.length()];
        int bracketTop = 0;

        for (int i = 0; i < text.length(); i++) {
            if (reserved[i])
                continue;
            char ch = text.charAt(i);
            if (ch == '(') {
                parenStack[parenTop++] = i;
            } else if (ch == ')') {
                if (parenTop > 0) {
                    int open = parenStack[--parenTop];
                    ParenRegion pr = new ParenRegion();
                    pr.openChar = open;
                    pr.closeChar = i;
                    pr.isBracket = false;
                    list.add(pr);
                }
            } else if (ch == '[') {
                bracketStack[bracketTop++] = i;
            } else if (ch == ']') {
                if (bracketTop > 0) {
                    int open = bracketStack[--bracketTop];
                    ParenRegion pr = new ParenRegion();
                    pr.openChar = open;
                    pr.closeChar = i;
                    pr.isBracket = true;
                    list.add(pr);
                }
            }
        }
        return list.toArray(new ParenRegion[0]);
    }

    public static ParenRegion getAt(ParenRegion[] parens, int i, int end) {
        if (parens == null) return null;
        for (ParenRegion pr : parens) {
            if (pr != null && pr.openChar == i && pr.closeChar < end) {
                return pr;
            }
        }
        return null;
    }

    public static void ensureMetrics(Graphics2D g2, String text, ParenRegion pr, Font font,
            FractionsSuperscript.FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
            ParenRegion[] parens, int nestLevel) {
        ensureMetrics(g2, text, pr, font, fractions, roots, powers, integrals,
                null, null, null, null, null, parens, nestLevel);
    }

    public static void ensureMetrics(Graphics2D g2, String text, ParenRegion pr, Font font,
            FractionsSuperscript.FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations, LogsSuperScript.LogRegion[] logs,
            DerivativesSuperScript.DerivativeRegion[] derivatives,
            FloorAndCeilingSuperScript.FloorCeilRegion[] floorCeils,
            AbsoluteValueSuperScript.AbsoluteValueRegion[] absoluteValues,
            ParenRegion[] parens, int nestLevel) {
        if (pr != null && pr.cachedAscent != -1 && pr.cachedDescent != -1 && pr.isStretchy != -1) {
            return;
        }
        FontMetrics fm = g2.getFontMetrics(font);
        int cAscent = MathRenderEngine.getRegionAscent(g2, text, pr.openChar + 1, pr.closeChar,
                font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
        int cDescent = MathRenderEngine.getRegionDescent(g2, text, pr.openChar + 1, pr.closeChar,
                font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
        if (pr != null) {
            pr.cachedAscent = cAscent;
            pr.cachedDescent = cDescent;
            pr.isStretchy = (cAscent > (int) (fm.getAscent() * 1.15f) || cDescent > (int) (fm.getDescent() * 1.15f)) ? 1 : 0;
        }
    }

    public static boolean isStretchy(Graphics2D g2, String text, ParenRegion pr, Font font,
            FractionsSuperscript.FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
            ParenRegion[] parens, int nestLevel) {
        return isStretchy(g2, text, pr, font, fractions, roots, powers, integrals,
                null, null, null, null, null, parens, nestLevel);
    }

    public static boolean isStretchy(Graphics2D g2, String text, ParenRegion pr, Font font,
            FractionsSuperscript.FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations, LogsSuperScript.LogRegion[] logs,
            DerivativesSuperScript.DerivativeRegion[] derivatives,
            FloorAndCeilingSuperScript.FloorCeilRegion[] floorCeils,
            AbsoluteValueSuperScript.AbsoluteValueRegion[] absoluteValues,
            ParenRegion[] parens, int nestLevel) {
        ensureMetrics(g2, text, pr, font, fractions, roots, powers, integrals,
                summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
        return pr != null && pr.isStretchy == 1;
    }

    public static void drawStretchyParen(Graphics2D g2, int x, int topY, int bottomY, int width, boolean isOpen) {
        Stroke oldStroke = g2.getStroke();
        g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D p = new Path2D.Double();
        int h = bottomY - topY;
        int hookH = Math.min(14, Math.max(6, h / 5));
        int hookW = width - 3;

        if (isOpen) { // '('
            p.moveTo(x + width - 1, topY);
            p.curveTo(x + width - 1 - hookW * 0.75, topY + hookH * 0.2,
                    x + 2, topY + hookH * 0.7,
                    x + 2, topY + hookH);
            p.lineTo(x + 2, bottomY - hookH);
            p.curveTo(x + 2, bottomY - hookH * 0.7,
                    x + width - 1 - hookW * 0.75, bottomY - hookH * 0.2,
                    x + width - 1, bottomY);
        } else { // ')'
            p.moveTo(x + 1, topY);
            p.curveTo(x + 1 + hookW * 0.75, topY + hookH * 0.2,
                    x + width - 2, topY + hookH * 0.7,
                    x + width - 2, topY + hookH);
            p.lineTo(x + width - 2, bottomY - hookH);
            p.curveTo(x + width - 2, bottomY - hookH * 0.7,
                    x + 1 + hookW * 0.75, bottomY - hookH * 0.2,
                    x + 1, bottomY);
        }
        g2.draw(p);
        g2.setStroke(oldStroke);
    }

    public static void drawStretchyBracket(Graphics2D g2, int x, int topY, int bottomY, int width, boolean isOpen) {
        Stroke oldStroke = g2.getStroke();
        g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int tickW = Math.max(4, width - 3);
        if (isOpen) { // '['
            g2.drawLine(x + tickW, topY, x + 2, topY);
            g2.drawLine(x + 2, topY, x + 2, bottomY);
            g2.drawLine(x + 2, bottomY, x + tickW, bottomY);
        } else { // ']'
            g2.drawLine(x + width - tickW, topY, x + width - 2, topY);
            g2.drawLine(x + width - 2, topY, x + width - 2, bottomY);
            g2.drawLine(x + width - 2, bottomY, x + width - tickW, bottomY);
        }
        g2.setStroke(oldStroke);
    }

    public static boolean isPrecededByFunctionName(String text, int openChar) {
        if (text == null || openChar <= 0) return false;
        int idx = openChar - 1;
        while (idx >= 0 && Character.isWhitespace(text.charAt(idx))) {
            idx--;
        }
        if (idx < 0) return false;
        char ch = text.charAt(idx);
        if (!Character.isLetter(ch)) return false;
        int start = idx;
        while (start >= 0 && Character.isLetter(text.charAt(start))) {
            start--;
        }
        String name = text.substring(start + 1, idx + 1);
        return name.length() > 1;
    }

    public static boolean isPrecededByText(String text, int openChar) {
        return isPrecededByFunctionName(text, openChar);
    }

    public static int getAscent(Graphics2D g2, String text, ParenRegion pr, Font font,
            FractionsSuperscript.FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
            ParenRegion[] parens, int nestLevel) {
        return getAscent(g2, text, pr, font, fractions, roots, powers, integrals,
                null, null, null, null, null, parens, nestLevel);
    }

    public static int getAscent(Graphics2D g2, String text, ParenRegion pr, Font font,
            FractionsSuperscript.FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations, LogsSuperScript.LogRegion[] logs,
            DerivativesSuperScript.DerivativeRegion[] derivatives,
            FloorAndCeilingSuperScript.FloorCeilRegion[] floorCeils,
            AbsoluteValueSuperScript.AbsoluteValueRegion[] absoluteValues,
            ParenRegion[] parens, int nestLevel) {
        ensureMetrics(g2, text, pr, font, fractions, roots, powers, integrals,
                summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
        FontMetrics fm = g2.getFontMetrics(font);
        int cAscent = pr != null ? pr.cachedAscent : 0;
        int cDescent = pr != null ? pr.cachedDescent : 0;
        boolean isFuncParen = pr != null && !pr.isBracket && isPrecededByFunctionName(text, pr.openChar);
        int contentShiftY = isFuncParen ? Math.max(0, cDescent - fm.getDescent()) : 0;
        return Math.max(fm.getAscent(), cAscent + contentShiftY) + 2;
    }

    public static int getDescent(Graphics2D g2, String text, ParenRegion pr, Font font,
            FractionsSuperscript.FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
            ParenRegion[] parens, int nestLevel) {
        return getDescent(g2, text, pr, font, fractions, roots, powers, integrals,
                null, null, null, null, null, parens, nestLevel);
    }

    public static int getDescent(Graphics2D g2, String text, ParenRegion pr, Font font,
            FractionsSuperscript.FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations, LogsSuperScript.LogRegion[] logs,
            DerivativesSuperScript.DerivativeRegion[] derivatives,
            FloorAndCeilingSuperScript.FloorCeilRegion[] floorCeils,
            AbsoluteValueSuperScript.AbsoluteValueRegion[] absoluteValues,
            ParenRegion[] parens, int nestLevel) {
        FontMetrics fm = g2.getFontMetrics(font);
        boolean isFuncParen = pr != null && !pr.isBracket && isPrecededByFunctionName(text, pr.openChar);
        if (isFuncParen) {
            return fm.getDescent() + 2;
        }
        ensureMetrics(g2, text, pr, font, fractions, roots, powers, integrals,
                summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
        int cDescent = pr != null ? pr.cachedDescent : 0;
        return Math.max(fm.getDescent(), cDescent) + 2;
    }

    public static int render(Graphics2D g2, String text, ParenRegion pr, int curX, int baselineY,
            Font font, boolean draw, FractionsSuperscript.FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
            ParenRegion[] parens, int nestLevel, int selStart, int selEnd) {
        return render(g2, text, pr, curX, baselineY, font, draw, fractions, roots, powers, integrals,
                null, null, null, null, null, parens, nestLevel, selStart, selEnd);
    }

    public static int render(Graphics2D g2, String text, ParenRegion pr, int curX, int baselineY,
            Font font, boolean draw, FractionsSuperscript.FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations, LogsSuperScript.LogRegion[] logs,
            DerivativesSuperScript.DerivativeRegion[] derivatives,
            FloorAndCeilingSuperScript.FloorCeilRegion[] floorCeils,
            AbsoluteValueSuperScript.AbsoluteValueRegion[] absoluteValues,
            ParenRegion[] parens, int nestLevel, int selStart, int selEnd) {
        FontMetrics fm = g2.getFontMetrics(font);
        ensureMetrics(g2, text, pr, font, fractions, roots, powers, integrals,
                summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
        int cAscent = pr != null ? pr.cachedAscent : 0;
        int cDescent = pr != null ? pr.cachedDescent : 0;

        boolean isFuncParen = !pr.isBracket && isPrecededByFunctionName(text, pr.openChar);
        int contentShiftY = isFuncParen ? Math.max(0, cDescent - fm.getDescent()) : 0;

        int topY = baselineY - Math.max(fm.getAscent(), cAscent + contentShiftY) - 2;
        int bottomY = isFuncParen 
                ? baselineY + fm.getDescent() + 2
                : baselineY + Math.max(fm.getDescent(), cDescent) + 2;

        int h = bottomY - topY;
        int parenW = Math.max(fm.charWidth(text.charAt(pr.openChar)), Math.min(13, 6 + h / 20));
        int contentW = MathRenderEngine.renderSubString(g2, text, pr.openChar + 1, pr.closeChar, 0, 0,
                font, false, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel, -1, -1);
        int totalW = parenW + contentW + parenW;
        if (pr != null) pr.cachedWidth = totalW;

        if (draw) {
            if (pr.isBracket) {
                drawStretchyBracket(g2, curX, topY, bottomY, parenW, true);
            } else {
                drawStretchyParen(g2, curX, topY, bottomY, parenW, true);
            }

            MathRenderEngine.renderSubString(g2, text, pr.openChar + 1, pr.closeChar, curX + parenW, baselineY - contentShiftY,
                    font, true, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel, selStart,
                    selEnd);

            if (pr.isBracket) {
                drawStretchyBracket(g2, curX + parenW + contentW, topY, bottomY, parenW, false);
            } else {
                drawStretchyParen(g2, curX + parenW + contentW, topY, bottomY, parenW, false);
            }

            if (selStart <= pr.openChar && selEnd > pr.openChar) {
                g2.setColor(new Color(130, 160, 210, 100));
                g2.fillRect(curX, topY, parenW, bottomY - topY);
                g2.setColor(Color.WHITE);
            }
            if (selStart <= pr.closeChar && selEnd > pr.closeChar) {
                g2.setColor(new Color(130, 160, 210, 100));
                g2.fillRect(curX + parenW + contentW, topY, parenW, bottomY - topY);
                g2.setColor(Color.WHITE);
            }
        }

        return curX + totalW;
    }

    public static int[] getCaretInfo(Graphics2D g2, String text, ParenRegion pr, int curX,
            int targetOffset, Font font, FractionsSuperscript.FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
            ParenRegion[] parens, int nestLevel) {
        return getCaretInfo(g2, text, pr, curX, targetOffset, font, fractions, roots, powers, integrals,
                null, null, null, null, null, parens, nestLevel);
    }

    public static int[] getCaretInfo(Graphics2D g2, String text, ParenRegion pr, int curX,
            int targetOffset, Font font, FractionsSuperscript.FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations, LogsSuperScript.LogRegion[] logs,
            DerivativesSuperScript.DerivativeRegion[] derivatives,
            FloorAndCeilingSuperScript.FloorCeilRegion[] floorCeils,
            AbsoluteValueSuperScript.AbsoluteValueRegion[] absoluteValues,
            ParenRegion[] parens, int nestLevel) {
        if (pr == null || targetOffset < pr.openChar || targetOffset > pr.closeChar) {
            return null;
        }
        FontMetrics fm = g2.getFontMetrics(font);
        ensureMetrics(g2, text, pr, font, fractions, roots, powers, integrals,
                summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
        int cAscent = pr != null ? pr.cachedAscent : 0;
        int cDescent = pr != null ? pr.cachedDescent : 0;

        boolean isFuncParen = !pr.isBracket && isPrecededByFunctionName(text, pr.openChar);
        int contentShiftY = isFuncParen ? Math.max(0, cDescent - fm.getDescent()) : 0;

        int asc = Math.max(fm.getAscent(), cAscent + contentShiftY) + 2;
        int desc = isFuncParen ? fm.getDescent() + 2 : Math.max(fm.getDescent(), cDescent) + 2;
        int topY = -asc;
        int bottomY = desc;

        int h = bottomY - topY;
        int parenW = Math.max(fm.charWidth(text.charAt(pr.openChar)), Math.min(13, 6 + h / 20));

        if (targetOffset == pr.openChar) {
            return new int[] { curX, 0, asc, desc };
        }
        int contentW = MathRenderEngine.renderSubString(g2, text, pr.openChar + 1, pr.closeChar, 0, 0,
                font, false, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel, -1, -1);
        if (targetOffset == pr.closeChar) {
            return new int[] { curX + parenW + contentW, 0, asc, desc };
        }
        int[] sub = MathRenderEngine.getCaretInfoSubString(g2, text, pr.openChar + 1, pr.closeChar,
                targetOffset, font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
        if (sub != null) {
            return new int[] { curX + parenW + sub[0], sub[1] - contentShiftY, sub[2], sub[3] };
        }
        return new int[] { curX + parenW, -contentShiftY, fm.getAscent(), fm.getDescent() };
    }

    private static int baselineYOffset(FontMetrics fm) {
        return -fm.getAscent() - 2;
    }
}
