package com.spectragraph.superscript;

import java.awt.BasicStroke;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles detection, sign drawing, limit positioning, measurement, caret
 * positioning, and rendering for single, double, triple, and N-tuple integrals.
 * Multiple integral signs share a common argument and stacked differentials (e.g. dx dy dz).
 */
public class IntegralsSuperscript {

    public static class IntegralSign {
        public int intChar;
        public int upperOpen = -1, upperClose = -1;
        public int lowerOpen = -1, lowerClose = -1;

        public IntegralSign() {}

        public IntegralSign(int intChar, int uOpen, int uClose, int lOpen, int lClose) {
            this.intChar = intChar;
            this.upperOpen = uOpen;
            this.upperClose = uClose;
            this.lowerOpen = lOpen;
            this.lowerClose = lClose;
        }
    }

    public static class IntegralRegion {
        public int intChar;
        public int upperOpen = -1, upperClose = -1;
        public int lowerOpen = -1, lowerClose = -1;
        public int parenOpen = -1, parenClose = -1;
        public int dxStart = -1, dxEnd = -1;
        public List<IntegralSign> signs = new ArrayList<>();
        public boolean anySignHasLimits = false;
        public ChainMetrics chainMetrics = null;
        public int cachedDiffsWidth = -1;
        public Font cachedDiffsFont = null;
        public int cachedWidth = -1;
        public int cachedContentW = -1;

        public IntegralRegion() {
        }

        public IntegralRegion(IntegralRegion other) {
            if (other != null) {
                this.intChar = other.intChar;
                this.upperOpen = other.upperOpen;
                this.upperClose = other.upperClose;
                this.lowerOpen = other.lowerOpen;
                this.lowerClose = other.lowerClose;
                this.parenOpen = other.parenOpen;
                this.parenClose = other.parenClose;
                this.dxStart = other.dxStart;
                this.dxEnd = other.dxEnd;
                this.anySignHasLimits = other.anySignHasLimits;
                this.chainMetrics = other.chainMetrics;
                this.cachedDiffsWidth = other.cachedDiffsWidth;
                this.cachedDiffsFont = other.cachedDiffsFont;
                for (IntegralSign s : other.signs) {
                    this.signs.add(new IntegralSign(s.intChar, s.upperOpen, s.upperClose, s.lowerOpen, s.lowerClose));
                }
            }
        }

        public boolean hasSignAt(int pos) {
            for (IntegralSign s : signs) {
                if (s.intChar == pos) return true;
            }
            return false;
        }

        public int getSignIndexAt(int pos) {
            for (int k = 0; k < signs.size(); k++) {
                if (signs.get(k).intChar == pos) return k;
            }
            return -1;
        }
    }

    public static IntegralRegion[] findRegions(String text) {
        if (text == null || text.isEmpty()) {
            return new IntegralRegion[0];
        }

        List<IntegralRegion> list = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            if (text.charAt(i) != '∫') {
                i++;
                continue;
            }

            IntegralRegion r = new IntegralRegion();
            int curr = i;

            // Parse consecutive stacked ∫ signs and their limits
            while (curr < text.length() && text.charAt(curr) == '∫') {
                int signChar = curr;
                int uOpen = -1, uClose = -1;
                int lOpen = -1, lClose = -1;
                curr++; // past '∫'

                if (curr < text.length() && text.charAt(curr) == '∫') {
                    r.signs.add(new IntegralSign(signChar, -1, -1, -1, -1));
                    continue;
                }

                int scanBracket = curr;
                while (scanBracket < text.length() && Character.isWhitespace(text.charAt(scanBracket))) scanBracket++;
                if (scanBracket < text.length() && text.charAt(scanBracket) == '[') {
                    curr = scanBracket;
                    uOpen = curr;
                    int depth = 1;
                    for (int j = curr + 1; j < text.length(); j++) {
                        if (text.charAt(j) == '[') depth++;
                        else if (text.charAt(j) == ']') depth--;
                        if (depth == 0) {
                            uClose = j;
                            break;
                        }
                    }
                    if (uClose != -1) {
                        curr = uClose + 1;
                        if (curr < text.length() && text.charAt(curr) == '[') {
                            lOpen = curr;
                            depth = 1;
                            for (int j = curr + 1; j < text.length(); j++) {
                                if (text.charAt(j) == '[') depth++;
                                else if (text.charAt(j) == ']') depth--;
                                if (depth == 0) {
                                    lClose = j;
                                    break;
                                }
                            }
                            if (lClose != -1) {
                                curr = lClose + 1;
                            }
                        }
                    }
                }
                if ((uOpen != -1 && uClose > uOpen + 1) || (lOpen != -1 && lClose > lOpen + 1)) {
                    r.anySignHasLimits = true;
                }
                r.signs.add(new IntegralSign(signChar, uOpen, uClose, lOpen, lClose));
            }

            // After all stacked signs, parse shared argument '('
            // Allows gap text (e.g. user typing 'i', 'in', etc. before '(')
            int scanParen = curr;
            while (scanParen < text.length()) {
                char c = text.charAt(scanParen);
                if (c == '(') {
                    break;
                }
                if ((c == 'd' || c == 'D') && scanParen + 1 < text.length() && Character.isLetter(text.charAt(scanParen + 1))) {
                    break;
                }
                if (Character.isWhitespace(c) || Character.isLetter(c)) {
                    scanParen++;
                } else {
                    break;
                }
            }
            if (scanParen < text.length() && text.charAt(scanParen) == '(') {
                curr = scanParen;
                r.parenOpen = curr;
                int depth = 1;
                for (int j = curr + 1; j < text.length(); j++) {
                    if (text.charAt(j) == '(') depth++;
                    else if (text.charAt(j) == ')') depth--;
                    if (depth == 0) {
                        r.parenClose = j;
                        break;
                    }
                }
                if (r.parenClose != -1) {
                    curr = r.parenClose + 1;
                }
            } else {
                // If no immediate '(', scan forward at depth 0 to find differentials d[xyztwu]
                int p = curr;
                int pDepth = 0;
                int bDepth = 0;
                while (p < text.length()) {
                    char c = text.charAt(p);
                    if (c == '(') pDepth++;
                    else if (c == ')') { if (pDepth > 0) pDepth--; }
                    else if (c == '[') bDepth++;
                    else if (c == ']') { if (bDepth > 0) bDepth--; }
                    else if (pDepth == 0 && bDepth == 0) {
                        if ((c == 'd' || c == 'D') && p + 1 < text.length() && Character.isLetter(text.charAt(p + 1))) {
                            break;
                        }
                    }
                    p++;
                }
                if (p < text.length()) {
                    curr = p;
                }
            }

            // After argument, parse differentials (e.g. dx, dx dy, dx dy dz, dxdydz, etc.)
            int scanDx = curr;
            while (scanDx < text.length() && Character.isWhitespace(text.charAt(scanDx))) {
                scanDx++;
            }
            if (scanDx < text.length() && (text.charAt(scanDx) == 'd' || text.charAt(scanDx) == 'D')
                    && !text.regionMatches(true, scanDx, "diff", 0, 4)
                    && !text.regionMatches(true, scanDx, "dirac", 0, 5)
                    && !text.regionMatches(true, scanDx, "drc", 0, 3)) {
                r.dxStart = scanDx;
                int p = scanDx;
                int lastValid = scanDx + 1;
                while (p < text.length()) {
                    while (p < text.length() && Character.isWhitespace(text.charAt(p))) p++;
                    if (p < text.length() && (text.charAt(p) == 'd' || text.charAt(p) == 'D')
                            && !text.regionMatches(true, p, "diff", 0, 4)
                            && !text.regionMatches(true, p, "dirac", 0, 5)
                            && !text.regionMatches(true, p, "drc", 0, 3)) {
                        p++;
                        while (p < text.length() && Character.isLetterOrDigit(text.charAt(p))) p++;
                        lastValid = p;
                    } else {
                        break;
                    }
                }
                r.dxEnd = lastValid;
                curr = r.dxEnd;
            }

            // Sync primary fields: find sign with limits (prefer first with limits, else last, else first)
            if (!r.signs.isEmpty()) {
                IntegralSign first = r.signs.get(0);
                r.intChar = first.intChar;
                IntegralSign withLimits = first;
                for (IntegralSign s : r.signs) {
                    if (s.upperOpen != -1 || s.lowerOpen != -1) {
                        withLimits = s;
                        break;
                    }
                }
                r.upperOpen = withLimits.upperOpen;
                r.upperClose = withLimits.upperClose;
                r.lowerOpen = withLimits.lowerOpen;
                r.lowerClose = withLimits.lowerClose;
            }

            // Fallback: single limit without paren
            if (r.signs.size() == 1 && r.upperOpen != -1 && r.lowerOpen == -1 && r.parenOpen == -1 && r.dxStart != -1) {
                r.parenOpen = r.upperOpen;
                r.parenClose = r.upperClose;
                r.upperOpen = -1;
                r.upperClose = -1;
                r.signs.get(0).upperOpen = -1;
                r.signs.get(0).upperClose = -1;
            }

            list.add(r);
            IntegralSign lastSign = r.signs.get(r.signs.size() - 1);
            i = lastSign.intChar + 1;
        }
        return list.toArray(new IntegralRegion[0]);
    }

    public static IntegralRegion getAt(IntegralRegion[] integrals, int i) {
        if (integrals == null)
            return null;
        for (IntegralRegion it : integrals) {
            if (it != null && it.intChar == i) {
                return it;
            }
        }
        return null;
    }

    private static BasicStroke cachedStroke = null;
    private static float cachedStrokeW = -1f;
    private static Path2D.Float cachedSignPath = null;
    private static int cachedTopY = -1, cachedBottomY = -1, cachedWidth = -1;

    public static void drawIntegralSign(Graphics2D g2, int x, int topY, int bottomY, int width) {
        if (bottomY <= topY) return;
        if (cachedSignPath == null || cachedTopY != topY || cachedBottomY != bottomY || cachedWidth != width) {
            cachedTopY = topY;
            cachedBottomY = bottomY;
            cachedWidth = width;
            float h = bottomY - topY;
            float hookH = Math.min(h * 0.25f, 11f);
            float hookW = width * 0.46f;
            float midX = width * 0.42f;
            float rightX = midX + hookW;
            float leftX = midX - hookW;

            float stemTopY = topY + hookH;
            float stemBotY = bottomY - hookH;

            Path2D.Float path = new Path2D.Float();
            // Top hook: start at serif tip, round over the top, arrive at stemTop with vertical tangent
            path.moveTo(rightX, topY + hookH * 0.35f);
            path.curveTo(rightX, topY,
                         midX + hookW * 0.30f, topY,
                         midX, stemTopY);
            // Stem (vertical)
            path.lineTo(midX, stemBotY);
            // Bottom hook: leave stemBot with vertical tangent, round under the bottom, end at serif tip
            path.curveTo(midX, bottomY,
                         leftX + hookW * 0.30f, bottomY,
                         leftX, bottomY - hookH * 0.35f);
            cachedSignPath = path;

            float strokeW = Math.max(1.2f, Math.min(1.8f, h * 0.045f));
            if (cachedStroke == null || cachedStrokeW != strokeW) {
                cachedStrokeW = strokeW;
                cachedStroke = new BasicStroke(strokeW, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
            }
        }

        g2.setStroke(cachedStroke);
        g2.translate(x, 0);
        g2.draw(cachedSignPath);
        g2.translate(-x, 0);
    }

    public static Font getLimitFont(Font font, int nestLevel) {
        if (font == null) return null;
        if (nestLevel > 0) {
            return font;
        }
        float newSize = Math.max(font.getSize2D() * 0.72f, 9.0f);
        return font.deriveFont(newSize);
    }

    public static int getSignAscent(FontMetrics fm, int contentAscent) {
        int axisRel = (int) (fm.getAscent() * 0.45f);
        int baseSignHalf = (int) (fm.getAscent() * 1.35f);
        int contAboveAxis = Math.max(0, contentAscent - axisRel);
        return Math.max(baseSignHalf, contAboveAxis + 4) + axisRel;
    }

    public static int getSignDescent(FontMetrics fm, int contentDescent) {
        int axisRel = (int) (fm.getAscent() * 0.45f);
        int baseSignHalf = (int) (fm.getAscent() * 1.35f);
        int contBelowAxis = Math.max(0, contentDescent + axisRel);
        return Math.max(baseSignHalf, contBelowAxis + 4) - axisRel;
    }

    public static int getIntegrandStart(IntegralRegion it) {
        if (it.parenOpen != -1) return it.parenOpen + 1;
        if (it.lowerClose != -1) return it.lowerClose + 1;
        if (it.upperClose != -1) return it.upperClose + 1;
        return it.intChar + 1;
    }

    public static int getIntegrandEnd(IntegralRegion it, int fallbackEnd) {
        if (it.parenClose != -1 && it.parenClose < fallbackEnd) return it.parenClose;
        if (it.dxStart != -1 && it.dxStart < fallbackEnd) return it.dxStart;
        return fallbackEnd;
    }

    public static class ChainMetrics {
        public int baseSignAscent;
        public int baseSignDescent;
        public int signAscent;
        public int signDescent;
        public int totalAscent;
        public int totalDescent;
    }

    public static IntegralRegion findParentIntegral(IntegralRegion[] integrals, IntegralRegion child, String text) {
        if (integrals == null || child == null || text == null) return null;
        if (child.signs == null || child.signs.isEmpty()) return null;
        int cStart = child.signs.get(0).intChar;
        for (IntegralRegion p : integrals) {
            if (p != null && p != child && p.parenOpen != -1 && p.parenClose != -1) {
                if (p.parenOpen < cStart && cStart < p.parenClose) {
                    return p;
                }
            }
        }
        return null;
    }

    public static List<IntegralRegion> getIteratedChain(IntegralRegion[] integrals, IntegralRegion it, String text) {
        if (integrals == null || it == null) return java.util.Collections.singletonList(it);
        IntegralRegion root = it;
        while (true) {
            IntegralRegion p = findParentIntegral(integrals, root, text);
            if (p == null) break;
            root = p;
        }
        List<IntegralRegion> chain = new ArrayList<>();
        IntegralRegion curr = root;
        while (curr != null) {
            chain.add(curr);
            IntegralRegion child = null;
            for (IntegralRegion c : integrals) {
                if (c != null && findParentIntegral(integrals, c, text) == curr) {
                    child = c;
                    break;
                }
            }
            curr = child;
        }
        return chain;
    }

    public static ChainMetrics getChainMetrics(Graphics2D g2, String text, IntegralRegion it, Font font,
            FractionsSuperscript.FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers, IntegralRegion[] integrals,
            ParensSuperscript.ParenRegion[] parens, int nestLevel) {
        return getChainMetrics(g2, text, it, font, fractions, roots, powers, integrals, null, null, null, null, null, parens, nestLevel);
    }

    public static ChainMetrics getChainMetrics(Graphics2D g2, String text, IntegralRegion it, Font font,
            FractionsSuperscript.FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers, IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations, LogsSuperScript.LogRegion[] logs,
            DerivativesSuperScript.DerivativeRegion[] derivatives,
            FloorAndCeilingSuperScript.FloorCeilRegion[] floorCeils,
            AbsoluteValueSuperScript.AbsoluteValueRegion[] absoluteValues,
            ParensSuperscript.ParenRegion[] parens, int nestLevel) {
        if (it != null && it.chainMetrics != null) {
            return it.chainMetrics;
        }
        FontMetrics fm = g2.getFontMetrics(font);
        int intStart = getIntegrandStart(it);
        int intEnd = getIntegrandEnd(it, text.length());
        boolean innerEmpty = (it.parenOpen != -1 && it.parenClose == it.parenOpen + 1) || (intStart >= intEnd);

        int contentAscent = fm.getAscent();
        int contentDescent = fm.getDescent();
        if (!innerEmpty && intStart < intEnd) {
            contentAscent = Math.max(contentAscent, MathRenderEngine.getRegionAscent(g2, text, intStart, intEnd, font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel));
            contentDescent = Math.max(contentDescent, MathRenderEngine.getRegionDescent(g2, text, intStart, intEnd, font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel));
        }

        int baseSignAscent = getSignAscent(fm, contentAscent);
        int baseSignDescent = getSignDescent(fm, contentDescent);

        if (it != null && !it.anySignHasLimits) {
            ChainMetrics m = new ChainMetrics();
            m.baseSignAscent = baseSignAscent;
            m.baseSignDescent = baseSignDescent;
            m.signAscent = baseSignAscent;
            m.signDescent = baseSignDescent;
            m.totalAscent = Math.max(contentAscent, baseSignAscent + 2);
            m.totalDescent = Math.max(contentDescent, baseSignDescent + 2);
            it.chainMetrics = m;
            return m;
        }

        Font limitFont = getLimitFont(font, nestLevel);
        FontMetrics limitFm = g2.getFontMetrics(limitFont);

        List<IntegralSign> signList = (it.signs != null && !it.signs.isEmpty()) ? it.signs : java.util.Collections.singletonList(
                new IntegralSign(it.intChar, it.upperOpen, it.upperClose, it.lowerOpen, it.lowerClose));

        int unifiedSignAscent = baseSignAscent;
        int unifiedSignDescent = baseSignDescent;
        int maxLimitTop = unifiedSignAscent + 2;
        int maxLimitBottom = unifiedSignDescent + 2;

        for (IntegralSign s : signList) {
            boolean upperHasText = (s.upperOpen != -1 && s.upperClose > s.upperOpen + 1);
            if (upperHasText) {
                int upperAscent = MathRenderEngine.getRegionAscent(g2, text, s.upperOpen + 1, s.upperClose, limitFont,
                        fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel + 1);
                int upperDescent = MathRenderEngine.getRegionDescent(g2, text, s.upperOpen + 1, s.upperClose, limitFont,
                        fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel + 1);
                int extraUpperDescent = Math.max(0, upperDescent - limitFm.getDescent());
                int upperBaseRel = baseSignAscent + extraUpperDescent - (int) (limitFm.getAscent() * 0.35f);
                unifiedSignAscent = Math.max(unifiedSignAscent, upperBaseRel + limitFm.getAscent());
                maxLimitTop = Math.max(maxLimitTop, upperBaseRel + upperAscent + 2);
            }

            boolean lowerHasText = (s.lowerOpen != -1 && s.lowerClose > s.lowerOpen + 1);
            if (lowerHasText) {
                int lowerAscent = MathRenderEngine.getRegionAscent(g2, text, s.lowerOpen + 1, s.lowerClose, limitFont,
                        fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel + 1);
                int lowerDescent = MathRenderEngine.getRegionDescent(g2, text, s.lowerOpen + 1, s.lowerClose, limitFont,
                        fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel + 1);
                int extraLowerAscent = Math.max(0, lowerAscent - limitFm.getAscent());
                int lowerBaseRel = baseSignDescent + extraLowerAscent + (int) (limitFm.getAscent() * 0.65f);
                unifiedSignDescent = Math.max(unifiedSignDescent, lowerBaseRel + limitFm.getDescent());
                maxLimitBottom = Math.max(maxLimitBottom, lowerBaseRel + lowerDescent + 2);
            }
        }

        ChainMetrics m = new ChainMetrics();
        m.baseSignAscent = baseSignAscent;
        m.baseSignDescent = baseSignDescent;
        m.signAscent = unifiedSignAscent;
        m.signDescent = unifiedSignDescent;
        m.totalAscent = Math.max(contentAscent, maxLimitTop);
        m.totalDescent = Math.max(contentDescent, maxLimitBottom);
        if (it != null) it.chainMetrics = m;
        return m;
    }

    public static int getAscent(Graphics2D g2, String text, IntegralRegion it, Font font,
            FractionsSuperscript.FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers, IntegralRegion[] integrals,
            ParensSuperscript.ParenRegion[] parens, int nestLevel) {
        return getAscent(g2, text, it, font, fractions, roots, powers, integrals, null, null, null, null, null, parens, nestLevel);
    }

    public static int getAscent(Graphics2D g2, String text, IntegralRegion it, Font font,
            FractionsSuperscript.FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers, IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations, LogsSuperScript.LogRegion[] logs,
            DerivativesSuperScript.DerivativeRegion[] derivatives,
            FloorAndCeilingSuperScript.FloorCeilRegion[] floorCeils,
            AbsoluteValueSuperScript.AbsoluteValueRegion[] absoluteValues,
            ParensSuperscript.ParenRegion[] parens, int nestLevel) {
        ChainMetrics m = getChainMetrics(g2, text, it, font, fractions, roots, powers, integrals,
                summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
        return m.totalAscent;
    }

    public static int getDescent(Graphics2D g2, String text, IntegralRegion it, Font font,
            FractionsSuperscript.FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers, IntegralRegion[] integrals,
            ParensSuperscript.ParenRegion[] parens, int nestLevel) {
        return getDescent(g2, text, it, font, fractions, roots, powers, integrals, null, null, null, null, null, parens, nestLevel);
    }

    public static int getDescent(Graphics2D g2, String text, IntegralRegion it, Font font,
            FractionsSuperscript.FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers, IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations, LogsSuperScript.LogRegion[] logs,
            DerivativesSuperScript.DerivativeRegion[] derivatives,
            FloorAndCeilingSuperScript.FloorCeilRegion[] floorCeils,
            AbsoluteValueSuperScript.AbsoluteValueRegion[] absoluteValues,
            ParensSuperscript.ParenRegion[] parens, int nestLevel) {
        ChainMetrics m = getChainMetrics(g2, text, it, font, fractions, roots, powers, integrals,
                summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
        return m.totalDescent;
    }

    public static int render(Graphics2D g2, String text, IntegralRegion it, int curX, int baselineY,
            Font font, boolean draw, FractionsSuperscript.FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers, IntegralRegion[] integrals,
            ParensSuperscript.ParenRegion[] parens, int nestLevel, int selStart, int selEnd, int end) {
        return render(g2, text, it, curX, baselineY, font, draw, fractions, roots, powers, integrals,
                null, null, null, null, null, parens, nestLevel, selStart, selEnd, end);
    }

    public static int render(Graphics2D g2, String text, IntegralRegion it, int curX, int baselineY,
            Font font, boolean draw, FractionsSuperscript.FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers, IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations, LogsSuperScript.LogRegion[] logs,
            DerivativesSuperScript.DerivativeRegion[] derivatives,
            FloorAndCeilingSuperScript.FloorCeilRegion[] floorCeils,
            AbsoluteValueSuperScript.AbsoluteValueRegion[] absoluteValues,
            ParensSuperscript.ParenRegion[] parens, int nestLevel, int selStart, int selEnd, int end) {
        FontMetrics fm = g2.getFontMetrics(font);
        int intStart = getIntegrandStart(it);
        int intEnd = getIntegrandEnd(it, end);
        boolean isEmptyArg = (it.parenOpen != -1 && it.parenClose == it.parenOpen + 1) || (intStart >= intEnd);
        int emptyW = isEmptyArg ? Math.max(14, (int) (fm.charWidth(' ') * 1.5f)) : 0;
        int contentW = MathRenderEngine.renderSubString(g2, text, intStart, intEnd, 0, 0,
                font, false, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel, -1, -1);
        if (isEmptyArg) {
            contentW = Math.max(contentW, emptyW);
        }
        int contentAscent = fm.getAscent();
        int contentDescent = fm.getDescent();
        if (!isEmptyArg && intStart < intEnd) {
            contentAscent = Math.max(contentAscent, MathRenderEngine.getRegionAscent(g2, text, intStart, intEnd,
                    font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel));
            contentDescent = Math.max(contentDescent, MathRenderEngine.getRegionDescent(g2, text, intStart, intEnd,
                    font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel));
        }
        if (isEmptyArg) {
            contentAscent = Math.max(contentAscent, fm.getAscent());
            contentDescent = Math.max(contentDescent, fm.getDescent());
        }

        Font limitFont = getLimitFont(font, nestLevel);
        FontMetrics limitFm = g2.getFontMetrics(limitFont);

        ChainMetrics cm = getChainMetrics(g2, text, it, font, fractions, roots, powers, integrals,
                summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
        int unifiedSignAscent = cm.signAscent;
        int unifiedSignDescent = cm.signDescent;

        int topY = baselineY - unifiedSignAscent;
        int bottomY = baselineY + unifiedSignDescent;

        int intSignW = Math.max(12, (int) (fm.getAscent() * 0.90f));
        int indexGap = 2;
        int limitGap = Math.max(6, (int) (fm.charWidth(' ') * 0.6f));

        List<IntegralSign> signList = (it.signs != null && !it.signs.isEmpty()) ? it.signs : java.util.Collections.singletonList(
                new IntegralSign(it.intChar, it.upperOpen, it.upperClose, it.lowerOpen, it.lowerClose));

        int unifiedDrawTopY = baselineY - unifiedSignAscent;
        int unifiedDrawBottomY = baselineY + unifiedSignDescent;

        boolean startsWithIntegral = false;
        if (intStart < text.length()) {
            int p = intStart;
            while (p < text.length() && Character.isWhitespace(text.charAt(p))) p++;
            if (p < text.length() && text.charAt(p) == '∫') {
                startsWithIntegral = true;
            }
        }

        int x = curX;
        boolean anySignHasLimits = it.anySignHasLimits;

        int defaultSignW = (int) (intSignW * 0.70f);
        int lastSignW = (int) (intSignW * 0.75f);

        if (!anySignHasLimits && !draw) {
            int totalSignsW = (signList.size() > 1 ? (signList.size() - 1) * defaultSignW : 0) + lastSignW;
            x += totalSignsW;
        } else if (!anySignHasLimits) {
            java.awt.Rectangle clip = g2.getClipBounds();
            int sIdx = 0;
            if (clip != null && x + defaultSignW < clip.x && defaultSignW > 0) {
                int skip = Math.min(signList.size() - 1, (clip.x - x) / defaultSignW);
                if (skip > 0) {
                    x += skip * defaultSignW;
                    sIdx += skip;
                }
            }
            for (; sIdx < signList.size(); sIdx++) {
                int signW = (sIdx < signList.size() - 1) ? defaultSignW : lastSignW;
                if (clip == null || (x + signW >= clip.x && x <= clip.x + clip.width)) {
                    drawIntegralSign(g2, x, unifiedDrawTopY, unifiedDrawBottomY, intSignW);
                } else if (clip != null && x > clip.x + clip.width) {
                    int remainingCount = signList.size() - sIdx;
                    int remainingW = (remainingCount - 1) * defaultSignW + lastSignW;
                    x += remainingW;
                    break;
                }
                x += signW;
            }
        } else {
            for (int sIdx = 0; sIdx < signList.size(); sIdx++) {
                IntegralSign s = signList.get(sIdx);

                int upperW = 0;
                int upperAscent = limitFm.getAscent();
                int upperDescent = limitFm.getDescent();
                boolean upperHasText = (s.upperOpen != -1 && s.upperClose > s.upperOpen + 1);
                if (upperHasText) {
                    upperW = MathRenderEngine.renderSubString(g2, text, s.upperOpen + 1, s.upperClose, 0, 0,
                            limitFont, false, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel + 1, -1, -1);
                    upperAscent = MathRenderEngine.getRegionAscent(g2, text, s.upperOpen + 1, s.upperClose, limitFont, fractions,
                            roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel + 1);
                    upperDescent = MathRenderEngine.getRegionDescent(g2, text, s.upperOpen + 1, s.upperClose, limitFont, fractions,
                            roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel + 1);
                } else if (selStart > s.upperOpen && selStart <= s.upperClose) {
                    upperW = Math.max(upperW, 7);
                }

                int lowerW = 0;
                int lowerAscent = limitFm.getAscent();
                int lowerDescent = limitFm.getDescent();
                boolean lowerHasText = (s.lowerOpen != -1 && s.lowerClose > s.lowerOpen + 1);
                if (lowerHasText) {
                    lowerW = MathRenderEngine.renderSubString(g2, text, s.lowerOpen + 1, s.lowerClose, 0, 0,
                            limitFont, false, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel + 1, -1, -1);
                    lowerAscent = MathRenderEngine.getRegionAscent(g2, text, s.lowerOpen + 1, s.lowerClose, limitFont, fractions,
                            roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel + 1);
                    lowerDescent = MathRenderEngine.getRegionDescent(g2, text, s.lowerOpen + 1, s.lowerClose, limitFont, fractions,
                            roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel + 1);
                } else if (selStart > s.lowerOpen && selStart <= s.lowerClose) {
                    lowerW = Math.max(lowerW, 7);
                }

                float midX = x + intSignW * 0.40f;
                float rightX = midX + intSignW * 0.48f;
                int upperX = (int) Math.round(rightX) + indexGap;
                int lowerX = (int) Math.round(midX) + indexGap;
                int upperExtra = (upperW > 0 ? (upperX - (x + intSignW)) + upperW : 0);
                int lowerExtra = (lowerW > 0 ? (lowerX - (x + intSignW)) + lowerW : 0);
                int limitsW = Math.max(0, Math.max(upperExtra, lowerExtra));

                int extraUpperDescent = Math.max(0, upperDescent - limitFm.getDescent());
                int extraLowerAscent = Math.max(0, lowerAscent - limitFm.getAscent());
                int upperBaseRel = cm.baseSignAscent + extraUpperDescent - (int) (limitFm.getAscent() * 0.35f);
                int lowerBaseRel = cm.baseSignDescent + extraLowerAscent + (int) (limitFm.getAscent() * 0.65f);
                int upperBase = baselineY - upperBaseRel;
                int lowerBase = baselineY + lowerBaseRel;

                if (draw) {
                    drawIntegralSign(g2, x, unifiedDrawTopY, unifiedDrawBottomY, intSignW);

                    if (upperHasText) {
                        MathRenderEngine.renderSubString(g2, text, s.upperOpen + 1, s.upperClose, upperX, upperBase,
                                limitFont, true, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel + 1,
                                selStart, selEnd);
                    }

                    if (lowerHasText) {
                        MathRenderEngine.renderSubString(g2, text, s.lowerOpen + 1, s.lowerClose, lowerX, lowerBase,
                                limitFont, true, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel + 1,
                                selStart, selEnd);
                    }
                }

                int signW;
                if (limitsW > 0) {
                    signW = intSignW + limitsW + limitGap;
                } else {
                    if (sIdx < signList.size() - 1) {
                        signW = (int) (intSignW * 0.70f);
                    } else {
                        signW = (int) (intSignW * 0.75f);
                    }
                }
                x += signW;
            }
        }

        // Draw any intermediate text in the gap between limits and parenOpen (e.g. typing "i" or "in" for stacking)
        IntegralSign lastSign = signList.get(signList.size() - 1);
        int gapStart = (lastSign.lowerClose != -1 ? lastSign.lowerClose + 1 : (lastSign.upperClose != -1 ? lastSign.upperClose + 1 : lastSign.intChar + 1));
        int gapEnd = (it.parenOpen != -1) ? it.parenOpen : gapStart;
        if (gapEnd > gapStart && gapEnd <= text.length()) {
            String gapText = text.substring(gapStart, gapEnd);
            if (gapText.matches("^[a-zA-Z\\s]+$")) {
                if (draw) {
                    g2.setFont(font);
                    g2.drawString(gapText, x + 1, baselineY);
                }
                x += fm.stringWidth(gapText) + 2;
            }
        }

        boolean lastSignHasLimits = (lastSign.upperOpen != -1 && lastSign.upperClose > lastSign.upperOpen + 1)
                || (lastSign.lowerOpen != -1 && lastSign.lowerClose > lastSign.lowerOpen + 1);
        int argGap;
        if (startsWithIntegral) {
            argGap = lastSignHasLimits
                    ? Math.max(5, (int) (fm.charWidth(' ') * 0.50f))
                    : Math.max(6, (int) (fm.charWidth(' ') * 0.65f));
        } else {
            argGap = lastSignHasLimits
                    ? Math.max(4, (int) (fm.charWidth(' ') * 0.45f))
                    : Math.max(3, (int) (fm.charWidth(' ') * 0.35f));
        }
        int integrandX = x + argGap;
        int dxGap = Math.max(6, (int) (fm.charWidth(' ') * 0.7f));
        int dxX = integrandX + contentW + dxGap;

        int dxW = 0;
        if (it.dxStart != -1 && it.dxEnd <= end) {
            if (it.cachedDiffsWidth != -1 && it.cachedDiffsFont != null && it.cachedDiffsFont.equals(font)) {
                dxW = it.cachedDiffsWidth;
            } else {
                dxW = getDiffsWidth(g2, font, text.substring(it.dxStart, it.dxEnd));
                it.cachedDiffsWidth = dxW;
                it.cachedDiffsFont = font;
            }
        }

        if (draw) {
            MathRenderEngine.renderSubString(g2, text, intStart, intEnd, integrandX, baselineY,
                    font, true, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel, selStart, selEnd);

            if (dxW > 0) {
                drawDiffs(g2, font, text.substring(it.dxStart, it.dxEnd), dxX, baselineY);
            }
        }

        int totalW = (dxW > 0) ? (dxX - curX) + dxW + 2 : (integrandX - curX) + contentW + 2;
        if (it != null) {
            it.cachedWidth = totalW;
            it.cachedContentW = contentW;
            it.chainMetrics = cm;
        }
        return curX + totalW;
    }

    public static int[] getCaretInfo(Graphics2D g2, String text, IntegralRegion it, int curX,
            int targetOffset, Font font, FractionsSuperscript.FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers, IntegralRegion[] integrals,
            ParensSuperscript.ParenRegion[] parens, int nestLevel, int end) {
        return getCaretInfo(g2, text, it, curX, targetOffset, font, fractions, roots, powers, integrals,
                null, null, null, null, null, parens, nestLevel, end);
    }

    public static int[] getCaretInfo(Graphics2D g2, String text, IntegralRegion it, int curX,
            int targetOffset, Font font, FractionsSuperscript.FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers, IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations, LogsSuperScript.LogRegion[] logs,
            DerivativesSuperScript.DerivativeRegion[] derivatives,
            FloorAndCeilingSuperScript.FloorCeilRegion[] floorCeils,
            AbsoluteValueSuperScript.AbsoluteValueRegion[] absoluteValues,
            ParensSuperscript.ParenRegion[] parens, int nestLevel, int end) {
        if (it == null) return null;
        int itEnd = (it.dxEnd != -1 && it.dxEnd <= end) ? it.dxEnd
                : ((it.parenClose != -1 && it.parenClose < end) ? it.parenClose + 1 : end);
        if (targetOffset < it.intChar || targetOffset > itEnd) {
            return null;
        }
        FontMetrics fm = g2.getFontMetrics(font);
        int intStart = getIntegrandStart(it);
        int intEnd = getIntegrandEnd(it, end);
        boolean isEmptyArg = (it.parenOpen != -1 && it.parenClose == it.parenOpen + 1) || (intStart >= intEnd);
        int emptyW = isEmptyArg ? Math.max(14, (int) (fm.charWidth(' ') * 1.5f)) : 0;
        int contentW;
        if (it.cachedContentW != -1) {
            contentW = it.cachedContentW;
        } else {
            contentW = MathRenderEngine.renderSubString(g2, text, intStart, intEnd, 0, 0,
                    font, false, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel, -1, -1);
        }
        if (isEmptyArg) {
            contentW = Math.max(contentW, emptyW);
        }
        Font limitFont = getLimitFont(font, nestLevel);
        FontMetrics limitFm = g2.getFontMetrics(limitFont);

        ChainMetrics cm = (it.chainMetrics != null) ? it.chainMetrics
                : getChainMetrics(g2, text, it, font, fractions, roots, powers, integrals,
                        summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
        int unifiedSignAscent = cm.signAscent;
        int unifiedSignDescent = cm.signDescent;

        int intSignW = Math.max(12, (int) (fm.getAscent() * 0.90f));
        int indexGap = 2;
        int limitGap = Math.max(6, (int) (fm.charWidth(' ') * 0.6f));

        List<IntegralSign> signList = (it.signs != null && !it.signs.isEmpty()) ? it.signs : java.util.Collections.singletonList(
                new IntegralSign(it.intChar, it.upperOpen, it.upperClose, it.lowerOpen, it.lowerClose));

        boolean startsWithIntegral = false;
        if (intStart < text.length()) {
            int p = intStart;
            while (p < text.length() && Character.isWhitespace(text.charAt(p))) p++;
            if (p < text.length() && text.charAt(p) == '∫') {
                startsWithIntegral = true;
            }
        }

        int x = curX;
        boolean anySignHasLimits = it.anySignHasLimits;

        int defaultSignW = (int) (intSignW * 0.70f);
        int lastSignW = (int) (intSignW * 0.75f);

        if (!anySignHasLimits) {
            for (int sIdx = 0; sIdx < signList.size(); sIdx++) {
                IntegralSign s = signList.get(sIdx);
                int signX = curX + sIdx * defaultSignW;
                float midX = signX + intSignW * 0.40f;
                float rightX = midX + intSignW * 0.48f;
                int upperX = (int) Math.round(rightX) + indexGap;
                int lowerX = (int) Math.round(midX) + indexGap;
                int upperBase = -(unifiedSignAscent - (int) (limitFm.getAscent() * 0.35f));
                int lowerBase = unifiedSignDescent + (int) (limitFm.getAscent() * 0.65f);

                if (targetOffset == s.intChar) {
                    return new int[] { signX, 0, unifiedSignAscent, unifiedSignDescent };
                }
                if (s.upperOpen != -1 && targetOffset >= s.upperOpen + 1 && targetOffset <= s.upperClose) {
                    return new int[] { upperX, upperBase, limitFm.getAscent(), limitFm.getDescent() };
                }
                if (s.lowerOpen != -1 && targetOffset >= s.lowerOpen + 1 && targetOffset <= s.lowerClose) {
                    return new int[] { lowerX, lowerBase, limitFm.getAscent(), limitFm.getDescent() };
                }
            }
            int totalSignsW = (signList.size() > 1 ? (signList.size() - 1) * defaultSignW : 0) + lastSignW;
            x += totalSignsW;
        } else {
            for (int sIdx = 0; sIdx < signList.size(); sIdx++) {
                IntegralSign s = signList.get(sIdx);

                int upperW = 0;
                int upperAscent = limitFm.getAscent();
                int upperDescent = limitFm.getDescent();
                boolean upperHasText = (s.upperOpen != -1 && s.upperClose > s.upperOpen + 1);
                if (upperHasText) {
                    upperW = MathRenderEngine.renderSubString(g2, text, s.upperOpen + 1, s.upperClose, 0, 0,
                            limitFont, false, fractions, roots, powers, integrals, parens, nestLevel + 1, -1, -1);
                    upperAscent = MathRenderEngine.getRegionAscent(g2, text, s.upperOpen + 1, s.upperClose, limitFont, fractions,
                            roots, powers, integrals, parens, nestLevel + 1);
                    upperDescent = MathRenderEngine.getRegionDescent(g2, text, s.upperOpen + 1, s.upperClose, limitFont, fractions,
                            roots, powers, integrals, parens, nestLevel + 1);
                } else if (targetOffset > s.upperOpen && targetOffset <= s.upperClose) {
                    upperW = Math.max(upperW, 7);
                }

                int lowerW = 0;
                int lowerAscent = limitFm.getAscent();
                int lowerDescent = limitFm.getDescent();
                boolean lowerHasText = (s.lowerOpen != -1 && s.lowerClose > s.lowerOpen + 1);
                if (lowerHasText) {
                    lowerW = MathRenderEngine.renderSubString(g2, text, s.lowerOpen + 1, s.lowerClose, 0, 0,
                            limitFont, false, fractions, roots, powers, integrals, parens, nestLevel + 1, -1, -1);
                    lowerAscent = MathRenderEngine.getRegionAscent(g2, text, s.lowerOpen + 1, s.lowerClose, limitFont, fractions,
                            roots, powers, integrals, parens, nestLevel + 1);
                    lowerDescent = MathRenderEngine.getRegionDescent(g2, text, s.lowerOpen + 1, s.lowerClose, limitFont, fractions,
                            roots, powers, integrals, parens, nestLevel + 1);
                } else if (targetOffset > s.lowerOpen && targetOffset <= s.lowerClose) {
                    lowerW = Math.max(lowerW, 7);
                }

                float midX = x + intSignW * 0.40f;
                float rightX = midX + intSignW * 0.48f;
                int upperX = (int) Math.round(rightX) + indexGap;
                int lowerX = (int) Math.round(midX) + indexGap;
                int upperExtra = (upperW > 0 ? (upperX - (x + intSignW)) + upperW : 0);
                int lowerExtra = (lowerW > 0 ? (lowerX - (x + intSignW)) + lowerW : 0);
                int limitsW = Math.max(0, Math.max(upperExtra, lowerExtra));

                int extraUpperDescent = Math.max(0, upperDescent - limitFm.getDescent());
                int extraLowerAscent = Math.max(0, lowerAscent - limitFm.getAscent());
                int upperBaseRel = cm.baseSignAscent + extraUpperDescent - (int) (limitFm.getAscent() * 0.35f);
                int lowerBaseRel = cm.baseSignDescent + extraLowerAscent + (int) (limitFm.getAscent() * 0.65f);
                int upperBase = -upperBaseRel;
                int lowerBase = lowerBaseRel;

                if (targetOffset == s.intChar) {
                    return new int[] { x, 0, unifiedSignAscent, unifiedSignDescent };
                }

                if (s.upperOpen != -1 && targetOffset >= s.upperOpen + 1 && targetOffset <= s.upperClose) {
                    int[] sub = MathRenderEngine.getCaretInfoSubString(g2, text, s.upperOpen + 1, s.upperClose, targetOffset,
                            limitFont, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel + 1);
                    if (sub != null) {
                        return new int[] { upperX + sub[0], upperBase + sub[1], sub[2], sub[3] };
                    }
                    return new int[] { upperX, upperBase, limitFm.getAscent(), limitFm.getDescent() };
                }

                if (s.lowerOpen != -1 && targetOffset >= s.lowerOpen + 1 && targetOffset <= s.lowerClose) {
                    int[] sub = MathRenderEngine.getCaretInfoSubString(g2, text, s.lowerOpen + 1, s.lowerClose, targetOffset,
                            limitFont, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel + 1);
                    if (sub != null) {
                        return new int[] { lowerX + sub[0], lowerBase + sub[1], sub[2], sub[3] };
                    }
                    return new int[] { lowerX, lowerBase, limitFm.getAscent(), limitFm.getDescent() };
                }

                int signW;
                if (limitsW > 0) {
                    signW = intSignW + limitsW + limitGap;
                } else {
                    if (sIdx < signList.size() - 1) {
                        signW = (int) (intSignW * 0.70f);
                    } else {
                        signW = (int) (intSignW * 0.75f);
                    }
                }

                // Caret in the gap right after this sign's limits (or before the next sign/argument)
                int boundaryAfterLimits = (s.lowerClose != -1 ? s.lowerClose + 1 : (s.upperClose != -1 ? s.upperClose + 1 : s.intChar + 1));
                if (targetOffset == boundaryAfterLimits) {
                    return new int[] { x + signW, 0, fm.getAscent(), fm.getDescent() };
                }

                x += signW;
            }
        }

        // Caret in the gap between limits and parenOpen (e.g. typing "i" or "in" for stacking)
        IntegralSign lastSign = signList.get(signList.size() - 1);
        int gapStart = (lastSign.lowerClose != -1 ? lastSign.lowerClose + 1 : (lastSign.upperClose != -1 ? lastSign.upperClose + 1 : lastSign.intChar + 1));
        int gapEnd = (it.parenOpen != -1) ? it.parenOpen : gapStart;
        if (targetOffset >= gapStart && targetOffset <= gapEnd) {
            int subW = (targetOffset > gapStart && targetOffset <= text.length()) ? fm.stringWidth(text.substring(gapStart, targetOffset)) : 0;
            return new int[] { x + 1 + subW, 0, fm.getAscent(), fm.getDescent() };
        }
        if (gapEnd > gapStart && gapEnd <= text.length()) {
            String gapText = text.substring(gapStart, gapEnd);
            if (gapText.matches("^[a-zA-Z\\s]+$")) {
                x += fm.stringWidth(gapText) + 2;
            }
        }

        boolean lastSignHasLimits = (lastSign.upperOpen != -1 && lastSign.upperClose > lastSign.upperOpen + 1)
                || (lastSign.lowerOpen != -1 && lastSign.lowerClose > lastSign.lowerOpen + 1);
        int argGap;
        if (startsWithIntegral) {
            argGap = lastSignHasLimits
                    ? Math.max(5, (int) (fm.charWidth(' ') * 0.50f))
                    : Math.max(6, (int) (fm.charWidth(' ') * 0.65f));
        } else {
            argGap = lastSignHasLimits
                    ? Math.max(4, (int) (fm.charWidth(' ') * 0.45f))
                    : Math.max(3, (int) (fm.charWidth(' ') * 0.35f));
        }
        int integrandX = x + argGap;
        int dxGap = Math.max(6, (int) (fm.charWidth(' ') * 0.7f));
        int dxX = integrandX + contentW + dxGap;

        int dxW = 0;
        if (it.dxStart != -1 && it.dxEnd <= end) {
            if (it.cachedDiffsWidth != -1 && it.cachedDiffsFont != null && it.cachedDiffsFont.equals(font)) {
                dxW = it.cachedDiffsWidth;
            } else {
                dxW = getDiffsWidth(g2, font, text.substring(it.dxStart, it.dxEnd));
                it.cachedDiffsWidth = dxW;
                it.cachedDiffsFont = font;
            }
        }

        // Caret inside argument
        if (targetOffset >= intStart && targetOffset <= intEnd) {
            if (isEmptyArg) {
                return new int[] { integrandX, 0, fm.getAscent(), fm.getDescent() };
            }
            int[] sub = MathRenderEngine.getCaretInfoSubString(g2, text, intStart, intEnd, targetOffset,
                    font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
            if (sub != null) {
                return new int[] { integrandX + sub[0], sub[1], sub[2], sub[3] };
            }
            return new int[] { integrandX, 0, fm.getAscent(), fm.getDescent() };
        }

        // Caret at/after differentials
        if (it.dxStart != -1 && targetOffset >= it.dxStart && targetOffset <= it.dxEnd) {
            int subW = getDiffsSubWidth(g2, font, text.substring(it.dxStart, it.dxEnd), targetOffset - it.dxStart);
            return new int[] { dxX + subW, 0, fm.getAscent(), fm.getDescent() };
        }

        return null;
    }

    public static boolean isForbiddenCaret(int c, IntegralRegion[] integrals) {
        if (integrals == null) return false;
        for (IntegralRegion it : integrals) {
            if (it == null) continue;
            List<IntegralSign> signList = (it.signs != null && !it.signs.isEmpty()) ? it.signs : java.util.Collections.singletonList(
                    new IntegralSign(it.intChar, it.upperOpen, it.upperClose, it.lowerOpen, it.lowerClose));
            for (IntegralSign s : signList) {
                if (s.upperOpen != -1 && c == s.upperOpen)
                    return true; // between ∫ and [
                if (s.lowerOpen != -1 && c == s.lowerOpen)
                    return true; // between ] and [
            }
            if (it.dxStart != -1) {
                if (c == it.dxStart)
                    return true; // between ) and d
            }
        }
        return false;
    }

    public static class DiffVarRegion {
        public int dPos;
        public int varStart;
        public int varEnd;
        public String varName;
    }

    public static List<DiffVarRegion> getDiffVarRegions(String text, IntegralRegion it) {
        List<DiffVarRegion> list = new ArrayList<>();
        if (text == null || it == null || it.dxStart == -1 || it.dxEnd <= it.dxStart || it.dxEnd > text.length()) {
            return list;
        }
        int p = it.dxStart;
        while (p < it.dxEnd) {
            while (p < it.dxEnd && Character.isWhitespace(text.charAt(p))) p++;
            if (p < it.dxEnd && (text.charAt(p) == 'd' || text.charAt(p) == 'D')) {
                DiffVarRegion dvr = new DiffVarRegion();
                dvr.dPos = p;
                p++;
                dvr.varStart = p;
                while (p < it.dxEnd && Character.isLetterOrDigit(text.charAt(p))) p++;
                dvr.varEnd = p;
                dvr.varName = text.substring(dvr.varStart, dvr.varEnd);
                list.add(dvr);
            } else {
                p++;
            }
        }
        return list;
    }

    public static class DiffPiece {
        public final String text;
        public final boolean isSubscript;
        public final int start;
        public final int end;

        public DiffPiece(String text, boolean isSubscript, int start, int end) {
            this.text = text;
            this.isSubscript = isSubscript;
            this.start = start;
            this.end = end;
        }
    }

    public static List<DiffPiece> parseDiffPieces(String diffsText) {
        List<DiffPiece> list = new ArrayList<>();
        if (diffsText == null || diffsText.isEmpty()) return list;

        int len = diffsText.length();
        int i = 0;
        while (i < len) {
            char c = diffsText.charAt(i);
            if (Character.isWhitespace(c)) {
                int start = i;
                while (i < len && Character.isWhitespace(diffsText.charAt(i))) i++;
                list.add(new DiffPiece(diffsText.substring(start, i), false, start, i));
            } else if ((c == 'd' || c == 'D') && i + 1 < len && Character.isLetter(diffsText.charAt(i + 1))) {
                int start = i;
                i++; // past 'd'
                while (i < len && Character.isLetter(diffsText.charAt(i))) i++;
                int baseEnd = i;
                int pedixStart = i;
                while (i < len && Character.isLetterOrDigit(diffsText.charAt(i))) i++;
                int pedixEnd = i;
                list.add(new DiffPiece(diffsText.substring(start, baseEnd), false, start, baseEnd));
                if (pedixEnd > pedixStart) {
                    list.add(new DiffPiece(diffsText.substring(pedixStart, pedixEnd), true, pedixStart, pedixEnd));
                }
            } else {
                int start = i;
                while (i < len && !Character.isWhitespace(diffsText.charAt(i))) i++;
                list.add(new DiffPiece(diffsText.substring(start, i), false, start, i));
            }
        }
        return list;
    }

    private static Font cachedDiffBaseFont = null;
    private static Font cachedDiffSubFont = null;
    private static FontMetrics cachedDiffFmNormal = null;
    private static FontMetrics cachedDiffFmSub = null;
    private static int cachedDiffNormalW = -1;
    private static int cachedDiffSubW = -1;

    private static synchronized void ensureDiffFonts(Graphics2D g2, Font font) {
        if (cachedDiffBaseFont == null || !cachedDiffBaseFont.equals(font)) {
            cachedDiffBaseFont = font;
            cachedDiffSubFont = font.deriveFont(font.getSize2D() * 0.72f);
            cachedDiffFmNormal = (g2 != null) ? g2.getFontMetrics(cachedDiffBaseFont) : null;
            cachedDiffFmSub = (g2 != null) ? g2.getFontMetrics(cachedDiffSubFont) : null;
            if (cachedDiffFmNormal != null && cachedDiffFmSub != null) {
                cachedDiffNormalW = cachedDiffFmNormal.charWidth('x');
                cachedDiffSubW = cachedDiffFmSub.charWidth('0');
            } else {
                cachedDiffNormalW = -1;
                cachedDiffSubW = -1;
            }
        } else if (g2 != null && cachedDiffFmNormal == null) {
            cachedDiffFmNormal = g2.getFontMetrics(cachedDiffBaseFont);
            cachedDiffFmSub = g2.getFontMetrics(cachedDiffSubFont);
            cachedDiffNormalW = cachedDiffFmNormal.charWidth('x');
            cachedDiffSubW = cachedDiffFmSub.charWidth('0');
        }
    }

    public static boolean hasSubscripts(String diffsText) {
        if (diffsText == null) return false;
        for (int i = 0; i < diffsText.length(); i++) {
            char c = diffsText.charAt(i);
            if (c >= '0' && c <= '9') return true;
        }
        return false;
    }

    public static int getDiffsWidth(Graphics2D g2, Font font, String diffsText) {
        if (diffsText == null || diffsText.isEmpty()) return 0;
        ensureDiffFonts(g2, font);
        FontMetrics fmNormal = (cachedDiffFmNormal != null) ? cachedDiffFmNormal : g2.getFontMetrics(font);
        if (!hasSubscripts(diffsText)) {
            return fmNormal.stringWidth(diffsText);
        }
        FontMetrics fmSub = (cachedDiffFmSub != null) ? cachedDiffFmSub : g2.getFontMetrics(cachedDiffSubFont != null ? cachedDiffSubFont : font.deriveFont(font.getSize2D() * 0.72f));

        int normalCharW = cachedDiffNormalW;
        int subCharW = cachedDiffSubW;
        if (normalCharW > 0 && subCharW > 0 && normalCharW == fmNormal.charWidth(' ') && subCharW == fmSub.charWidth('e')) {
            int normalCount = 0;
            int subCount = 0;
            int len = diffsText.length();
            int i = 0;
            while (i < len) {
                char c = diffsText.charAt(i);
                if (Character.isWhitespace(c)) {
                    normalCount++;
                    i++;
                } else if ((c == 'd' || c == 'D') && i + 1 < len && Character.isLetter(diffsText.charAt(i + 1))) {
                    normalCount += 2;
                    i += 2;
                    while (i < len && Character.isLetter(diffsText.charAt(i))) {
                        normalCount++;
                        i++;
                    }
                    while (i < len && Character.isLetterOrDigit(diffsText.charAt(i))) {
                        subCount++;
                        i++;
                    }
                } else {
                    normalCount++;
                    i++;
                }
            }
            return normalCount * normalCharW + subCount * subCharW;
        }

        List<DiffPiece> pieces = parseDiffPieces(diffsText);
        int w = 0;
        for (DiffPiece p : pieces) {
            w += (p.isSubscript ? fmSub : fmNormal).stringWidth(p.text);
        }
        return w;
    }

    public static void drawDiffs(Graphics2D g2, Font font, String diffsText, int x, int baselineY) {
        if (diffsText == null || diffsText.isEmpty()) return;
        java.awt.Rectangle clip = g2.getClipBounds();
        if (clip != null && x > clip.x + clip.width) return;

        ensureDiffFonts(g2, font);
        FontMetrics fmNormal = (cachedDiffFmNormal != null) ? cachedDiffFmNormal : g2.getFontMetrics(font);
        if (!hasSubscripts(diffsText)) {
            int w = fmNormal.stringWidth(diffsText);
            if (clip == null || (x + w >= clip.x && x <= clip.x + clip.width)) {
                g2.setFont(font);
                g2.drawString(diffsText, x, baselineY);
            }
            return;
        }

        Font subFont = (cachedDiffSubFont != null) ? cachedDiffSubFont : font.deriveFont(font.getSize2D() * 0.72f);
        FontMetrics fmSub = (cachedDiffFmSub != null) ? cachedDiffFmSub : g2.getFontMetrics(subFont);
        int subShift = (int) (fmNormal.getAscent() * 0.28f);

        int normalCharW = cachedDiffNormalW;
        int subCharW = cachedDiffSubW;
        boolean isMono = (normalCharW > 0 && subCharW > 0 && normalCharW == fmNormal.charWidth(' ') && subCharW == fmSub.charWidth('e'));

        int len = diffsText.length();
        int curX = x;
        int i = 0;

        if (isMono && clip != null && curX + normalCharW < clip.x) {
            int skipPixels = clip.x - curX;
            int skipChars = Math.max(0, (skipPixels / Math.max(normalCharW, subCharW)) - 20);
            if (skipChars > 0 && skipChars < len) {
                int scanned = 0;
                while (i < len && scanned < skipChars) {
                    char c = diffsText.charAt(i);
                    if (Character.isWhitespace(c)) {
                        curX += normalCharW;
                        i++;
                        scanned++;
                    } else if ((c == 'd' || c == 'D') && i + 1 < len && Character.isLetter(diffsText.charAt(i + 1))) {
                        curX += normalCharW * 2;
                        i += 2;
                        scanned += 2;
                        while (i < len && Character.isLetter(diffsText.charAt(i))) {
                            curX += normalCharW;
                            i++;
                            scanned++;
                        }
                        while (i < len && Character.isLetterOrDigit(diffsText.charAt(i))) {
                            curX += subCharW;
                            i++;
                            scanned++;
                        }
                    } else {
                        curX += normalCharW;
                        i++;
                        scanned++;
                    }
                }
            }
        }

        while (i < len) {
            if (clip != null && curX > clip.x + clip.width) {
                break;
            }
            char c = diffsText.charAt(i);
            if (Character.isWhitespace(c)) {
                int start = i;
                while (i < len && Character.isWhitespace(diffsText.charAt(i))) i++;
                int count = i - start;
                int w = count * normalCharW;
                curX += w;
            } else if ((c == 'd' || c == 'D') && i + 1 < len && Character.isLetter(diffsText.charAt(i + 1))) {
                int start = i;
                i++; // past 'd'
                while (i < len && Character.isLetter(diffsText.charAt(i))) i++;
                int baseCount = i - start;
                int baseW = baseCount * normalCharW;
                if (clip == null || (curX + baseW >= clip.x && curX <= clip.x + clip.width)) {
                    g2.setFont(font);
                    g2.drawString(diffsText.substring(start, i), curX, baselineY);
                }
                curX += baseW;

                int pStart = i;
                while (i < len && Character.isLetterOrDigit(diffsText.charAt(i))) i++;
                if (i > pStart) {
                    int subCount = i - pStart;
                    int subW = subCount * subCharW;
                    if (clip == null || (curX + subW >= clip.x && curX <= clip.x + clip.width)) {
                        g2.setFont(subFont);
                        g2.drawString(diffsText.substring(pStart, i), curX, baselineY + subShift);
                    }
                    curX += subW;
                }
            } else {
                int start = i;
                while (i < len && !Character.isWhitespace(diffsText.charAt(i))) i++;
                String str = diffsText.substring(start, i);
                int w = fmNormal.stringWidth(str);
                if (clip == null || (curX + w >= clip.x && curX <= clip.x + clip.width)) {
                    g2.setFont(font);
                    g2.drawString(str, curX, baselineY);
                }
                curX += w;
            }
        }
        g2.setFont(font);
    }

    public static int getDiffsSubWidth(Graphics2D g2, Font font, String diffsText, int targetOffsetWithinDiffs) {
        if (diffsText == null || diffsText.isEmpty() || targetOffsetWithinDiffs <= 0) return 0;
        ensureDiffFonts(g2, font);
        FontMetrics fmNormal = (cachedDiffFmNormal != null) ? cachedDiffFmNormal : g2.getFontMetrics(font);
        if (!hasSubscripts(diffsText)) {
            int len = Math.min(diffsText.length(), targetOffsetWithinDiffs);
            return fmNormal.stringWidth(diffsText.substring(0, len));
        }
        Font subFont = (cachedDiffSubFont != null) ? cachedDiffSubFont : font.deriveFont(font.getSize2D() * 0.72f);
        FontMetrics fmSub = (cachedDiffFmSub != null) ? cachedDiffFmSub : g2.getFontMetrics(subFont);

        int normalCharW = cachedDiffNormalW;
        int subCharW = cachedDiffSubW;
        if (normalCharW > 0 && subCharW > 0 && normalCharW == fmNormal.charWidth(' ') && subCharW == fmSub.charWidth('e')) {
            int target = Math.min(diffsText.length(), targetOffsetWithinDiffs);
            int normalCount = 0;
            int subCount = 0;
            int i = 0;
            while (i < target) {
                char c = diffsText.charAt(i);
                if (Character.isWhitespace(c)) {
                    normalCount++;
                    i++;
                } else if ((c == 'd' || c == 'D') && i + 1 < target && Character.isLetter(diffsText.charAt(i + 1))) {
                    normalCount += 2;
                    i += 2;
                    while (i < target && Character.isLetter(diffsText.charAt(i))) {
                        normalCount++;
                        i++;
                    }
                    while (i < target && Character.isLetterOrDigit(diffsText.charAt(i))) {
                        subCount++;
                        i++;
                    }
                } else {
                    normalCount++;
                    i++;
                }
            }
            return normalCount * normalCharW + subCount * subCharW;
        }

        List<DiffPiece> pieces = parseDiffPieces(diffsText);
        int w = 0;
        for (DiffPiece p : pieces) {
            if (targetOffsetWithinDiffs <= p.start) {
                break;
            }
            if (targetOffsetWithinDiffs >= p.end) {
                w += (p.isSubscript ? fmSub : fmNormal).stringWidth(p.text);
            } else {
                String sub = p.text.substring(0, targetOffsetWithinDiffs - p.start);
                w += (p.isSubscript ? fmSub : fmNormal).stringWidth(sub);
                break;
            }
        }
        return w;
    }
}
