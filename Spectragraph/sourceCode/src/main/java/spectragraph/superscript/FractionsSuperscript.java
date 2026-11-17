package com.spectragraph.superscript;

import java.awt.BasicStroke;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles detection, measurement, caret positioning, and rendering for fractions.
 */
public class FractionsSuperscript {

    public static class FractionRegion {
        public int numOpen = -1, numClose = -1;
        public int slashChar;
        public int denOpen = -1, denClose = -1;
        public int cachedWidth = -1;
        public int cachedNumW = -1;
        public int cachedDenW = -1;
        public int cachedNumDescent = -1;
        public int cachedDenAscent = -1;

        public FractionRegion() {}

        public FractionRegion(FractionRegion other) {
            if (other != null) {
                this.numOpen = other.numOpen;
                this.numClose = other.numClose;
                this.slashChar = other.slashChar;
                this.denOpen = other.denOpen;
                this.denClose = other.denClose;
                this.cachedWidth = other.cachedWidth;
                this.cachedNumW = other.cachedNumW;
                this.cachedDenW = other.cachedDenW;
                this.cachedNumDescent = other.cachedNumDescent;
                this.cachedDenAscent = other.cachedDenAscent;
            }
        }
    }

    public static FractionRegion[] findRegions(String text) {
        if (text == null || text.isEmpty()) {
            return new FractionRegion[0];
        }

        List<FractionRegion> list = new ArrayList<>();
        int i = 0;
        while ((i = text.indexOf('/', i)) != -1) {
            if (!(i > 0 && text.charAt(i - 1) == ')' && i + 1 < text.length() && text.charAt(i + 1) == '(')) {
                i++;
                continue;
            }

            FractionRegion f = new FractionRegion();
            f.slashChar = i;
            f.numClose = i - 1;
            f.numOpen = -1;

            int depth = 1;
            for (int j = i - 2; j >= 0; j--) {
                if (text.charAt(j) == ')')
                    depth++;
                else if (text.charAt(j) == '(')
                    depth--;
                if (depth == 0) {
                    f.numOpen = j;
                    break;
                }
            }
            if (f.numOpen == -1) {
                i++;
                continue;
            }

            f.denOpen = i + 1;
            f.denClose = -1;
            depth = 1;
            for (int j = i + 2; j < text.length(); j++) {
                if (text.charAt(j) == '(')
                    depth++;
                else if (text.charAt(j) == ')')
                    depth--;
                if (depth == 0) {
                    f.denClose = j;
                    break;
                }
            }
            if (f.denClose == -1) {
                f.denClose = text.length() - 1;
            }

            // Skip derivative fractions (e.g. (d)/(dx), (∂)/(∂t), (d)/(d)) so DerivativesSuperScript handles them
            String num = text.substring(f.numOpen + 1, f.numClose).trim();
            String den = text.substring(f.denOpen + 1, f.denClose).trim();
            boolean numIsD = num.equals("d") || num.equals("\u2202") || num.matches("^[d\u2202]\\^\\(?\\d+\\)?$");
            boolean denIsD = den.startsWith("d") || den.startsWith("\u2202");
            if (numIsD && denIsD) {
                i++;
                continue;
            }

            list.add(f);
            i++;
        }
        return list.toArray(new FractionRegion[0]);
    }

    public static FractionRegion getAt(FractionRegion[] fractions, int i, int end) {
        if (fractions == null) return null;
        for (int k = fractions.length - 1; k >= 0; k--) {
            FractionRegion f = fractions[k];
            if (f != null && f.numOpen == i && f.slashChar < end && f.denClose <= end) {
                return f;
            }
        }
        return null;
    }

    public static int getAscent(Graphics2D g2, String text, FractionRegion f, Font font,
            FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
            ParensSuperscript.ParenRegion[] parens, int nestLevel, int end) {
        return getAscent(g2, text, f, font, fractions, roots, powers, integrals, null, null, null, null, null, parens, nestLevel, end);
    }

    public static int getAscent(Graphics2D g2, String text, FractionRegion f, Font font,
            FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations,
            ParensSuperscript.ParenRegion[] parens, int nestLevel, int end) {
        return getAscent(g2, text, f, font, fractions, roots, powers, integrals, summations, null, null, null, null, parens, nestLevel, end);
    }

    public static int getAscent(Graphics2D g2, String text, FractionRegion f, Font font,
            FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations, LogsSuperScript.LogRegion[] logs,
            DerivativesSuperScript.DerivativeRegion[] derivatives,
            FloorAndCeilingSuperScript.FloorCeilRegion[] floorCeils,
            AbsoluteValueSuperScript.AbsoluteValueRegion[] absoluteValues,
            ParensSuperscript.ParenRegion[] parens, int nestLevel, int end) {
        FontMetrics fm = g2.getFontMetrics(font);
        int numClose = Math.min(f.numClose, end);
        int numAscent = MathRenderEngine.getRegionAscent(g2, text, f.numOpen + 1, numClose, font, fractions, roots, powers,
                integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
        int numDescent = MathRenderEngine.getRegionDescent(g2, text, f.numOpen + 1, numClose, font, fractions, roots, powers,
                integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
        int gap = 3;
        int lineYOffset = -fm.getAscent() / 2;
        int numBaseOffset = lineYOffset - gap - numDescent;
        return -(numBaseOffset - numAscent);
    }

    public static int getDescent(Graphics2D g2, String text, FractionRegion f, Font font,
            FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
            ParensSuperscript.ParenRegion[] parens, int nestLevel, int end) {
        return getDescent(g2, text, f, font, fractions, roots, powers, integrals, null, null, null, null, null, parens, nestLevel, end);
    }

    public static int getDescent(Graphics2D g2, String text, FractionRegion f, Font font,
            FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations,
            ParensSuperscript.ParenRegion[] parens, int nestLevel, int end) {
        return getDescent(g2, text, f, font, fractions, roots, powers, integrals, summations, null, null, null, null, parens, nestLevel, end);
    }

    public static int getDescent(Graphics2D g2, String text, FractionRegion f, Font font,
            FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations, LogsSuperScript.LogRegion[] logs,
            DerivativesSuperScript.DerivativeRegion[] derivatives,
            FloorAndCeilingSuperScript.FloorCeilRegion[] floorCeils,
            AbsoluteValueSuperScript.AbsoluteValueRegion[] absoluteValues,
            ParensSuperscript.ParenRegion[] parens, int nestLevel, int end) {
        FontMetrics fm = g2.getFontMetrics(font);
        int denOpen = Math.min(f.denOpen, end);
        int denClose = Math.min(f.denClose, end);
        int denAscent = MathRenderEngine.getRegionAscent(g2, text, denOpen + 1, denClose, font, fractions, roots, powers,
                integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
        int denDescent = MathRenderEngine.getRegionDescent(g2, text, denOpen + 1, denClose, font, fractions, roots, powers,
                integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
        int gap = 3;
        int lineYOffset = -fm.getAscent() / 2;
        int denBaseOffset = lineYOffset + gap + denAscent;
        return denBaseOffset + denDescent;
    }

    public static int render(Graphics2D g2, String text, FractionRegion f, int curX, int baselineY,
            Font font, boolean draw, FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
            ParensSuperscript.ParenRegion[] parens, int nestLevel, int selStart, int selEnd, int end) {
        return render(g2, text, f, curX, baselineY, font, draw, fractions, roots, powers, integrals, null, null, null, null, null, parens, nestLevel, selStart, selEnd, end);
    }

    public static int render(Graphics2D g2, String text, FractionRegion f, int curX, int baselineY,
            Font font, boolean draw, FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations,
            ParensSuperscript.ParenRegion[] parens, int nestLevel, int selStart, int selEnd, int end) {
        return render(g2, text, f, curX, baselineY, font, draw, fractions, roots, powers, integrals, summations, null, null, null, null, parens, nestLevel, selStart, selEnd, end);
    }

    public static int render(Graphics2D g2, String text, FractionRegion f, int curX, int baselineY,
            Font font, boolean draw, FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations, LogsSuperScript.LogRegion[] logs,
            DerivativesSuperScript.DerivativeRegion[] derivatives,
            FloorAndCeilingSuperScript.FloorCeilRegion[] floorCeils,
            AbsoluteValueSuperScript.AbsoluteValueRegion[] absoluteValues,
            ParensSuperscript.ParenRegion[] parens, int nestLevel, int selStart, int selEnd, int end) {
        FontMetrics fm = g2.getFontMetrics(font);
        int numClose = Math.min(f.numClose, end);
        int denOpen = Math.min(f.denOpen, end);
        int denClose = Math.min(f.denClose, end);

        int numW = MathRenderEngine.renderSubString(g2, text, f.numOpen + 1, numClose, curX, baselineY,
                font, false, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel, -1, -1);
        int denW = MathRenderEngine.renderSubString(g2, text, denOpen + 1, denClose, curX, baselineY,
                font, false, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel, -1, -1);
        int maxW = Math.max(numW, denW);
        int totalW = maxW + 4;
        int denAscent = MathRenderEngine.getRegionAscent(g2, text, denOpen + 1, denClose,
                font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
        int numDescent = MathRenderEngine.getRegionDescent(g2, text, f.numOpen + 1, numClose,
                font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
        if (f != null) {
            f.cachedWidth = totalW;
            f.cachedNumW = numW;
            f.cachedDenW = denW;
            f.cachedDenAscent = denAscent;
            f.cachedNumDescent = numDescent;
        }
        if (draw) {
            int gap = 3;
            int lineY = baselineY - fm.getAscent() / 2;
            int numBase = lineY - gap - numDescent;
            int denBase = lineY + gap + denAscent;
            int numX = curX + (totalW - numW) / 2;
            int denX = curX + (totalW - denW) / 2;
            MathRenderEngine.renderSubString(g2, text, f.numOpen + 1, numClose, numX, numBase,
                    font, true, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel, selStart, selEnd);
            MathRenderEngine.renderSubString(g2, text, denOpen + 1, denClose, denX, denBase,
                    font, true, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel, selStart, selEnd);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawLine(curX, lineY, curX + totalW, lineY);
        }
        return curX + totalW;
    }

    public static int[] getCaretInfo(Graphics2D g2, String text, FractionRegion f, int curX,
            int targetOffset, Font font, FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
            ParensSuperscript.ParenRegion[] parens, int nestLevel, int end) {
        return getCaretInfo(g2, text, f, curX, targetOffset, font, fractions, roots, powers, integrals, null, null, null, null, null, parens, nestLevel, end);
    }

    public static int[] getCaretInfo(Graphics2D g2, String text, FractionRegion f, int curX,
            int targetOffset, Font font, FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations,
            ParensSuperscript.ParenRegion[] parens, int nestLevel, int end) {
        return getCaretInfo(g2, text, f, curX, targetOffset, font, fractions, roots, powers, integrals, summations, null, null, null, null, parens, nestLevel, end);
    }

    public static int[] getCaretInfo(Graphics2D g2, String text, FractionRegion f, int curX,
            int targetOffset, Font font, FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations, LogsSuperScript.LogRegion[] logs,
            DerivativesSuperScript.DerivativeRegion[] derivatives,
            FloorAndCeilingSuperScript.FloorCeilRegion[] floorCeils,
            AbsoluteValueSuperScript.AbsoluteValueRegion[] absoluteValues,
            ParensSuperscript.ParenRegion[] parens, int nestLevel, int end) {
        if (f == null) return null;
        int denClose = Math.min(f.denClose, end);
        if (targetOffset < f.numOpen || targetOffset > denClose) {
            return null;
        }
        FontMetrics fm = g2.getFontMetrics(font);
        int numClose = Math.min(f.numClose, end);
        int denOpen = Math.min(f.denOpen, end);

        int numW, denW, totalW, denAscent, numDescent;
        if (f.cachedNumW != -1) {
            numW = f.cachedNumW;
            denW = f.cachedDenW;
            totalW = f.cachedWidth;
            denAscent = f.cachedDenAscent;
            numDescent = f.cachedNumDescent;
        } else {
            numW = MathRenderEngine.renderSubString(g2, text, f.numOpen + 1, numClose, 0, 0,
                    font, false, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel, -1, -1);
            denW = MathRenderEngine.renderSubString(g2, text, denOpen + 1, denClose, 0, 0,
                    font, false, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel, -1, -1);
            totalW = Math.max(numW, denW) + 4;
            denAscent = MathRenderEngine.getRegionAscent(g2, text, denOpen + 1, denClose,
                    font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
            numDescent = MathRenderEngine.getRegionDescent(g2, text, f.numOpen + 1, numClose,
                    font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
            f.cachedWidth = totalW;
            f.cachedNumW = numW;
            f.cachedDenW = denW;
            f.cachedDenAscent = denAscent;
            f.cachedNumDescent = numDescent;
        }
        int gap = 3;
        int lineYOffset = -fm.getAscent() / 2;
        int numBaseOffset = lineYOffset - gap - numDescent;
        int denBaseOffset = lineYOffset + gap + denAscent;
        int numX = curX + (totalW - numW) / 2;
        int denX = curX + (totalW - denW) / 2;

        if (targetOffset >= f.numOpen && targetOffset <= numClose) {
            if (targetOffset == f.numOpen) {
                return new int[] { curX, 0, fm.getAscent(), fm.getDescent() };
            }
            int[] sub = MathRenderEngine.getCaretInfoSubString(g2, text, f.numOpen + 1, numClose,
                    targetOffset, font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
            if (sub != null)
                return new int[] { numX + sub[0], numBaseOffset + sub[1], sub[2], sub[3] };
        }
        if (targetOffset == f.slashChar) {
            return new int[] { numX + numW, numBaseOffset, fm.getAscent(), fm.getDescent() };
        }
        if (targetOffset == denOpen) {
            return new int[] { denX, denBaseOffset, fm.getAscent(), fm.getDescent() };
        }
        if (targetOffset > denOpen && targetOffset <= denClose) {
            int[] sub = MathRenderEngine.getCaretInfoSubString(g2, text, denOpen + 1, denClose,
                    targetOffset, font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
            if (sub != null)
                return new int[] { denX + sub[0], denBaseOffset + sub[1], sub[2], sub[3] };
        }
        return null;
    }

    public static boolean isForbiddenCaret(int c, FractionRegion[] fractions) {
        if (fractions == null) return false;
        for (FractionRegion f : fractions) {
            if (f == null) continue;
            if (c == f.numClose + 1)
                return true; // between ) and /
            if (c == f.denOpen)
                return true; // between / and (
        }
        return false;
    }
}
