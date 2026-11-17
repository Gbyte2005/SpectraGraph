package com.spectragraph.superscript;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.Map;

/**
 * Handles detection, lift calculation, measurement, caret positioning, and rendering for exponents/powers.
 */
public class ExponentsSuperscript {

    public static class PowerRegion {
        public int caretChar;
        public int openParen = -1, closeParen = -1;
        public int nestingLevel = 1;
        public int cachedWidth = -1;
        public int cachedLift = Integer.MIN_VALUE;

        public PowerRegion() {}

        public PowerRegion(PowerRegion other) {
            if (other != null) {
                this.caretChar = other.caretChar;
                this.openParen = other.openParen;
                this.closeParen = other.closeParen;
                this.nestingLevel = other.nestingLevel;
                this.cachedWidth = other.cachedWidth;
                this.cachedLift = other.cachedLift;
            }
        }
    }

    public static int consumeToken(String text, int end) {
        if (end < text.length() && (text.charAt(end) == '-' || text.charAt(end) == '+')) {
            end++;
        }
        if (end < text.length() && (text.charAt(end) == '√' || text.charAt(end) == '\u221A')) {
            end++;
            if (end < text.length() && text.charAt(end) == '[') {
                int closeBrk = MathRenderEngine.findMatchingChar(text, end, '[', ']');
                end = (closeBrk != -1) ? closeBrk + 1 : end;
            }
            if (end < text.length() && text.charAt(end) == '(') {
                int closePrn = MathRenderEngine.findMatchingChar(text, end, '(', ')');
                end = (closePrn != -1) ? closePrn + 1 : end;
            }
            return end;
        }
        while (end < text.length()) {
            char c = text.charAt(end);
            if (Character.isLetterOrDigit(c) || c == '.') {
                end++;
            } else {
                break;
            }
        }
        if (end < text.length() && text.charAt(end) == '[') {
            int match = MathRenderEngine.findMatchingChar(text, end, '[', ']');
            if (match != -1) {
                end = match + 1;
            }
        }
        if (end < text.length() && (text.charAt(end) == '(' || text.charAt(end) == '{')) {
            char openChar = text.charAt(end);
            char closeChar = (openChar == '(') ? ')' : '}';
            int match = MathRenderEngine.findMatchingChar(text, end, openChar, closeChar);
            if (match != -1) {
                end = match + 1;
            }
        }
        return end;
    }

    public static PowerRegion[] findRegions(String text) {
        if (text == null || text.isEmpty()) {
            return new PowerRegion[0];
        }
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf('^', idx)) != -1) {
            count++;
            idx++;
        }

        PowerRegion[] res = new PowerRegion[count];
        int i = 0;
        int arrayIdx = 0;
        boolean checkDeriv = text.contains("d") || text.contains("∂");
        while ((i = text.indexOf('^', i)) != -1) {
            if (checkDeriv && DerivativesSuperScript.isInsideDerivativeOperator(i, text)) {
                i++;
                continue;
            }
            PowerRegion p = new PowerRegion();
            p.caretChar = i;

            if (i + 1 < text.length() && (text.charAt(i + 1) == '(' || text.charAt(i + 1) == '[' || text.charAt(i + 1) == '{')) {
                char openChar = text.charAt(i + 1);
                char closeChar = (openChar == '(') ? ')' : (openChar == '[') ? ']' : '}';
                p.openParen = i + 1;
                int depth = 1;
                for (int j = i + 2; j < text.length(); j++) {
                    if (text.charAt(j) == openChar)
                        depth++;
                    else if (text.charAt(j) == closeChar)
                        depth--;
                    if (depth == 0) {
                        p.closeParen = j;
                        break;
                    }
                }

            } else {
                int end = consumeToken(text, i + 1);
                while (end < text.length() && text.charAt(end) == '^') {
                    end++;
                    if (end < text.length() && (text.charAt(end) == '(' || text.charAt(end) == '[' || text.charAt(end) == '{')) {
                        char openChar = text.charAt(end);
                        char closeChar = (openChar == '(') ? ')' : (openChar == '[') ? ']' : '}';
                        int match = MathRenderEngine.findMatchingChar(text, end, openChar, closeChar);
                        end = (match != -1) ? match + 1 : text.length();
                    } else {
                        end = consumeToken(text, end);
                    }
                }
                p.closeParen = end;
            }
            if (p.closeParen == -1)
                p.closeParen = text.length();

            // Calculate nesting level by counting how many ^ regions enclose this one
            int level = 1;
            for (int k = 0; k < arrayIdx; k++) {
                PowerRegion outer = res[k];
                if (outer != null) {
                    if (i > outer.caretChar && i < outer.closeParen) {
                        level = Math.max(level, outer.nestingLevel + 1);
                    } else if (i == outer.closeParen + 1) {
                        level = Math.max(level, outer.nestingLevel + 1);
                    }
                }
            }
            p.nestingLevel = level;

            res[arrayIdx++] = p;
            i++;
        }
        return (arrayIdx == res.length) ? res : java.util.Arrays.copyOf(res, arrayIdx);
    }

    public static PowerRegion getAt(PowerRegion[] powers, int i) {
        if (powers == null) return null;
        for (PowerRegion p : powers) {
            if (p != null && p.caretChar == i) {
                return p;
            }
        }
        return null;
    }

    public static Font getExponentFont(Font font, PowerRegion p, int nestLevel) {
        if (font == null) return null;
        if ((p != null && p.nestingLevel > 1) || nestLevel > 0) {
            return font;
        }
        float newSize = Math.max(font.getSize2D() * 0.72f, 9f);
        return font.deriveFont(newSize);
    }

    public static Font getExponentFont(Font font, PowerRegion p) {
        return getExponentFont(font, p, 0);
    }

    public static int getPowerLift(Graphics2D g2, String text, int caretCharIdx, int contentDescent, Font font,
            FractionsSuperscript.FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
            PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
            ParensSuperscript.ParenRegion[] parens, int nestLevel) {
        return getPowerLift(g2, text, caretCharIdx, contentDescent, font, fractions, roots, powers, integrals,
                null, null, null, null, null, parens, nestLevel);
    }

    public static int getPowerLift(Graphics2D g2, String text, int caretCharIdx, int contentDescent, Font font,
            FractionsSuperscript.FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
            PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations, LogsSuperScript.LogRegion[] logs,
            DerivativesSuperScript.DerivativeRegion[] derivatives,
            FloorAndCeilingSuperScript.FloorCeilRegion[] floorCeils,
            AbsoluteValueSuperScript.AbsoluteValueRegion[] absoluteValues,
            ParensSuperscript.ParenRegion[] parens, int nestLevel) {
        long key = MathRenderEngine.makeMetricKey(3, caretCharIdx, contentDescent, font.getSize2D());
        Map<Long, Integer> cache = MathRenderEngine.getMetricCache();
        Integer cached = cache.get(key);
        if (cached != null)
            return cached;

        FontMetrics fm = g2.getFontMetrics(font);
        float liftFactor = (nestLevel == 0) ? 0.75f : 0.70f;
        int baseLift = Math.max((int) (fm.getAscent() * liftFactor), 5);

        PowerRegion p = getAt(powers, caretCharIdx);
        Font expFont = getExponentFont(font, p, nestLevel);
        FontMetrics expFm = g2.getFontMetrics(expFont);
        int extraDescent = Math.max(0, contentDescent - expFm.getDescent());


        int baseStart = -1;
        int i = caretCharIdx;
        if (i > 0) {
            boolean isFractionBase = false;
            if (fractions != null) {
                for (FractionsSuperscript.FractionRegion f : fractions) {
                    if (f != null && f.denClose == i - 1) {
                        baseStart = f.numOpen;
                        isFractionBase = true;
                        break;
                    }
                }
            }
            if (!isFractionBase && (text.charAt(i - 1) == ')' || text.charAt(i - 1) == ']')) {
                int depthB = 1;
                for (int j = i - 2; j >= 0; j--) {
                    char c = text.charAt(j);
                    if (c == ')' || c == ']')
                        depthB++;
                    else if (c == '(' || c == '[')
                        depthB--;
                    if (depthB == 0) {
                        baseStart = j;
                        break;
                    }
                }
                if (baseStart > 0 && text.charAt(baseStart - 1) == '√') {
                    baseStart--;
                    if (baseStart > 0 && text.charAt(baseStart - 1) == ']') {
                        int bDepth = 1;
                        for (int j = baseStart - 2; j >= 0; j--) {
                            char c = text.charAt(j);
                            if (c == ']')
                                bDepth++;
                            else if (c == '[')
                                bDepth--;
                            if (bDepth == 0) {
                                baseStart = j;
                                break;
                            }
                        }
                    }
                } else if (baseStart > 0 && text.charAt(baseStart - 1) == '_') {
                    int subBase = baseStart - 1;
                    while (subBase > 0 && Character.isWhitespace(text.charAt(subBase - 1))) subBase--;
                    if (subBase > 0) {
                        int bStart = subBase - 1;
                        if (text.charAt(bStart) == ')' || text.charAt(bStart) == ']') {
                            int d = 1;
                            bStart--;
                            while (bStart >= 0) {
                                char c = text.charAt(bStart);
                                if (c == ')' || c == ']') d++;
                                else if (c == '(' || c == '[') d--;
                                if (d == 0) break;
                                bStart--;
                            }
                            baseStart = Math.max(0, bStart);
                        } else {
                            while (bStart >= 0 && (Character.isLetterOrDigit(text.charAt(bStart)) || text.charAt(bStart) == '.')) {
                                bStart--;
                            }
                            baseStart = bStart + 1;
                        }
                    }
                }
            } else if (!isFractionBase) {
                baseStart = i - 1;
                while (baseStart >= 0
                        && (Character.isLetterOrDigit(text.charAt(baseStart)) || text.charAt(baseStart) == '.')) {
                    baseStart--;
                }
                baseStart++;
                if (baseStart > 0 && text.charAt(baseStart - 1) == '_') {
                    int subBase = baseStart - 1;
                    while (subBase > 0 && Character.isWhitespace(text.charAt(subBase - 1))) subBase--;
                    if (subBase > 0) {
                        int bStart = subBase - 1;
                        if (text.charAt(bStart) == ')' || text.charAt(bStart) == ']') {
                            int d = 1;
                            bStart--;
                            while (bStart >= 0) {
                                char c = text.charAt(bStart);
                                if (c == ')' || c == ']') d++;
                                else if (c == '(' || c == '[') d--;
                                if (d == 0) break;
                                bStart--;
                            }
                            baseStart = Math.max(0, bStart);
                        } else {
                            while (bStart >= 0 && (Character.isLetterOrDigit(text.charAt(bStart)) || text.charAt(bStart) == '.')) {
                                bStart--;
                            }
                            baseStart = bStart + 1;
                        }
                    }
                }
            }

            while (baseStart > 0 && text.charAt(baseStart - 1) == '^') {
                int prevCaret = baseStart - 1;
                if (prevCaret > 0) {
                    if (text.charAt(prevCaret - 1) == ')' || text.charAt(prevCaret - 1) == ']') {
                        int d = 1;
                        int j = prevCaret - 2;
                        while (j >= 0) {
                            char c = text.charAt(j);
                            if (c == ')' || c == ']')
                                d++;
                            else if (c == '(' || c == '[')
                                d--;
                            if (d == 0) {
                                baseStart = j;
                                break;
                            }
                            j--;
                        }
                        if (j < 0)
                            break;
                        if (baseStart > 0 && text.charAt(baseStart - 1) == '√') {
                            baseStart--;
                            if (baseStart > 0 && text.charAt(baseStart - 1) == ']') {
                                int bDepth = 1;
                                for (int k = baseStart - 2; k >= 0; k--) {
                                    char c = text.charAt(k);
                                    if (c == ']')
                                        bDepth++;
                                    else if (c == '[')
                                        bDepth--;
                                    if (bDepth == 0) {
                                        baseStart = k;
                                        break;
                                    }
                                }
                            }
                        }
                    } else {
                        int b = prevCaret - 1;
                        while (b >= 0 && (Character.isLetterOrDigit(text.charAt(b)) || text.charAt(b) == '.'
                                || text.charAt(b) == '√')) {
                            b--;
                        }
                        baseStart = b + 1;
                    }
                } else {
                    break;
                }
            }

            if (baseStart >= 0 && baseStart < i) {
                int baseAscent = MathRenderEngine.getRegionAscent(g2, text, baseStart, i, font, fractions, roots, powers, integrals,
                        summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
                if (baseAscent > fm.getAscent() * 1.2) {
                    baseLift = Math.max(baseLift, baseAscent - fm.getAscent() / 3);
                }
            }
        }
        int expContentStart = (caretCharIdx + 1 < text.length() && (text.charAt(caretCharIdx + 1) == '('
                || text.charAt(caretCharIdx + 1) == '[' || text.charAt(caretCharIdx + 1) == '{'))
                ? caretCharIdx + 2 : caretCharIdx + 1;
        while (expContentStart < text.length() && text.charAt(expContentStart) == ' ') {
            expContentStart++;
        }
        int scan = expContentStart;
        if (scan < text.length() && (text.charAt(scan) == '-' || text.charAt(scan) == '+')) {
            scan++;
            while (scan < text.length() && text.charAt(scan) == ' ') {
                scan++;
            }
        }
        boolean isFractionExp = false;
        if (fractions != null) {
            for (FractionsSuperscript.FractionRegion f : fractions) {
                if (f != null && (f.numOpen == caretCharIdx + 1 || f.numOpen == expContentStart || f.numOpen == scan)) {
                    isFractionExp = true;
                    break;
                }
            }
        }
        boolean isTallExponent = false;
        if (!isTallExponent && integrals != null) {
            for (IntegralsSuperscript.IntegralRegion it : integrals) {
                if (it != null && (it.intChar == expContentStart || it.intChar == scan)) {
                    isTallExponent = true;
                    break;
                }
            }
        }
        if (!isTallExponent && roots != null) {
            for (RootsSuperscript.RootRegion r : roots) {
                if (r != null && (r.sqrtChar == caretCharIdx + 1 || r.sqrtChar == expContentStart || r.sqrtChar == scan
                        || (r.bracketOpen != -1 && (r.bracketOpen == caretCharIdx + 1 || r.bracketOpen == expContentStart || r.bracketOpen == scan)))) {
                    isTallExponent = true;
                    break;
                }
            }
        }
        if (!isTallExponent && scan < text.length() && (text.charAt(scan) == '√' || text.charAt(scan) == '\u221A')) {
            isTallExponent = true;
        }

        boolean hasExplicitParens = (caretCharIdx + 1 < text.length() && text.charAt(caretCharIdx + 1) == '(');
        int powStart = hasExplicitParens ? caretCharIdx + 2 : caretCharIdx + 1;
        int powClose = text.length();
        if (hasExplicitParens) {
            int match = MathRenderEngine.findMatchingChar(text, caretCharIdx + 1, '(', ')');
            if (match != -1) powClose = match;
        } else {
            powClose = consumeToken(text, powStart);
        }
        boolean hasParens = hasExplicitParens || needsAutoParens(text, powStart, powClose);

        boolean isTowerIntermediateBase = false;
        int check = baseStart - 1;
        while (check >= 0 && (text.charAt(check) == '(' || text.charAt(check) == '[' || text.charAt(check) == ' ')) {
            check--;
        }
        if (check >= 0 && text.charAt(check) == '^') {
            isTowerIntermediateBase = true;
        }

        int lift = baseLift;
        if (isFractionExp) {
            lift = Math.max(baseLift, baseLift + contentDescent + 2);
        } else if (isTallExponent) {
            lift = Math.max(lift, baseLift + contentDescent + 2);
        } else if (needsAutoParens(text, powStart, powClose)) {
            lift = Math.max(lift, baseLift + contentDescent + 2);
        }

        cache.put(key, lift);
        return lift;
    }

    public static int getAscent(Graphics2D g2, String text, PowerRegion p, Font font,
            FractionsSuperscript.FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
            PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
            ParensSuperscript.ParenRegion[] parens, int nestLevel, int end) {
        return getAscent(g2, text, p, font, fractions, roots, powers, integrals, null, null, null, null, null, parens, nestLevel, end);
    }

    public static int getAscent(Graphics2D g2, String text, PowerRegion p, Font font,
            FractionsSuperscript.FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
            PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations, LogsSuperScript.LogRegion[] logs,
            DerivativesSuperScript.DerivativeRegion[] derivatives,
            FloorAndCeilingSuperScript.FloorCeilRegion[] floorCeils,
            AbsoluteValueSuperScript.AbsoluteValueRegion[] absoluteValues,
            ParensSuperscript.ParenRegion[] parens, int nestLevel, int end) {
        int close = p.closeParen != -1 && p.closeParen < end ? p.closeParen : end;
        int powStart = p.openParen != -1 ? p.openParen + 1 : p.caretChar + 1;
        Font nextFont = getExponentFont(font, p, nestLevel);
        int subAscent = MathRenderEngine.getRegionAscent(g2, text, powStart, close, nextFont, fractions, roots, powers,
                integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel + 1);
        int contentDescent = MathRenderEngine.getRegionDescent(g2, text, powStart, close, nextFont, fractions, roots, powers,
                integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel + 1);
        int lift = getPowerLift(g2, text, p.caretChar, contentDescent, font, fractions, roots, powers, integrals,
                summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
        return subAscent + lift;
    }

    public static int getDescent(Graphics2D g2, String text, PowerRegion p, Font font,
            FractionsSuperscript.FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
            PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
            ParensSuperscript.ParenRegion[] parens, int nestLevel, int end) {
        return getDescent(g2, text, p, font, fractions, roots, powers, integrals, null, null, null, null, null, parens, nestLevel, end);
    }

    public static int getDescent(Graphics2D g2, String text, PowerRegion p, Font font,
            FractionsSuperscript.FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
            PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations, LogsSuperScript.LogRegion[] logs,
            DerivativesSuperScript.DerivativeRegion[] derivatives,
            FloorAndCeilingSuperScript.FloorCeilRegion[] floorCeils,
            AbsoluteValueSuperScript.AbsoluteValueRegion[] absoluteValues,
            ParensSuperscript.ParenRegion[] parens, int nestLevel, int end) {
        int close = p.closeParen != -1 && p.closeParen < end ? p.closeParen : end;
        int powStart = p.openParen != -1 ? p.openParen + 1 : p.caretChar + 1;
        Font nextFont = getExponentFont(font, p, nestLevel);
        int subDescent = MathRenderEngine.getRegionDescent(g2, text, powStart, close, nextFont, fractions, roots, powers,
                integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel + 1);
        int lift = getPowerLift(g2, text, p.caretChar, subDescent, font, fractions, roots, powers, integrals,
                summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
        return Math.max(0, subDescent - lift);
    }

    public static boolean isFractionOver3VerticalCells(String text, int start, int end) {
        if (text == null || start >= end) return false;
        while (start < end && text.charAt(start) == ' ') start++;
        while (end > start && text.charAt(end - 1) == ' ') end--;
        if (start < end && text.charAt(start) == '(') {
            int closeParen = MathRenderEngine.findMatchingChar(text, start, '(', ')');
            if (closeParen != -1 && closeParen + 1 < end && text.charAt(closeParen + 1) == '/'
                    && closeParen + 2 < end && text.charAt(closeParen + 2) == '(') {
                int denClose = MathRenderEngine.findMatchingChar(text, closeParen + 2, '(', ')');
                if (denClose == end - 1) {
                    return RootsSuperscript.takesMoreThanOneVerticalCell(text, start + 1, closeParen)
                            || RootsSuperscript.takesMoreThanOneVerticalCell(text, closeParen + 3, denClose);
                }
            }
        }
        return false;
    }

    public static boolean isPurePower(String text, int start, int end) {
        if (text == null || start >= end) return false;
        while (start < end && text.charAt(start) == ' ') start++;
        while (end > start && text.charAt(end - 1) == ' ') end--;
        int i = start;
        int next = consumeToken(text, i);
        if (next == i) return false;
        if (next < end && text.charAt(next) == '^') {
            int expStart = next + 1;
            if (expStart >= end) return false;
            if (text.charAt(expStart) == '(') {
                int match = MathRenderEngine.findMatchingChar(text, expStart, '(', ')');
                if (match == end - 1) {
                    return true;
                }
            }
            int curr = expStart;
            while (curr < end) {
                int tEnd = consumeToken(text, curr);
                if (tEnd == curr) return false;
                curr = tEnd;
                if (curr < end && text.charAt(curr) == '^') {
                    curr++;
                } else {
                    break;
                }
            }
            return curr == end;
        }
        return false;
    }

    public static boolean needsAutoParens(String text, int start, int end) {
        if (text == null || start >= end) return false;
        if (RootsSuperscript.alreadyHasOuterParens(text, start, end)) return false;
        if (RootsSuperscript.isSingleFunctionCall(text, start, end)) return false;
        if (isPurePower(text, start, end)) return false;

        return RootsSuperscript.getHorizontalColumns(text, start, end) > 3;
    }

    public static int render(Graphics2D g2, String text, PowerRegion p, int curX, int baselineY,
            Font font, boolean draw, FractionsSuperscript.FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
            PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
            ParensSuperscript.ParenRegion[] parens, int nestLevel, int selStart, int selEnd, int end) {
        return render(g2, text, p, curX, baselineY, font, draw, fractions, roots, powers, integrals,
                null, null, null, null, null, parens, nestLevel, selStart, selEnd, end);
    }

    public static int render(Graphics2D g2, String text, PowerRegion p, int curX, int baselineY,
            Font font, boolean draw, FractionsSuperscript.FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
            PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations, LogsSuperScript.LogRegion[] logs,
            DerivativesSuperScript.DerivativeRegion[] derivatives,
            FloorAndCeilingSuperScript.FloorCeilRegion[] floorCeils,
            AbsoluteValueSuperScript.AbsoluteValueRegion[] absoluteValues,
            ParensSuperscript.ParenRegion[] parens, int nestLevel, int selStart, int selEnd, int end) {
        int close = p.closeParen != -1 && p.closeParen < end ? p.closeParen : end;
        int powStart = p.openParen != -1 ? p.openParen + 1 : p.caretChar + 1;
        Font nextFont = getExponentFont(font, p, nestLevel);
        FontMetrics nextFm = g2.getFontMetrics(nextFont);
        int contentDescent = MathRenderEngine.getRegionDescent(g2, text, powStart, close, nextFont, fractions, roots, powers,
                integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel + 1);
        int lift = getPowerLift(g2, text, p.caretChar, contentDescent, font, fractions, roots, powers, integrals,
                summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);

        boolean needsParens = needsAutoParens(text, powStart, close);
        int parenW = needsParens ? Math.max(4, Math.min(8, nextFm.charWidth('('))) : 0;
        int parenGap = needsParens ? 1 : 0;

        curX += 1; // consistent gap before exponent
        int rawPowW = MathRenderEngine.renderSubString(g2, text, powStart, close, curX + (needsParens ? (parenW + parenGap) : 0),
                baselineY - lift, nextFont, draw, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel + 1, selStart, selEnd);

        if (draw && needsParens) {
            int subAscent = MathRenderEngine.getRegionAscent(g2, text, powStart, close, nextFont, fractions, roots, powers,
                    integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel + 1);
            int pTop = (baselineY - lift) - subAscent - 1;
            int pBottom = (baselineY - lift) + contentDescent + 1;
            ParensSuperscript.drawStretchyParen(g2, curX, pTop, pBottom, parenW, true);
            ParensSuperscript.drawStretchyParen(g2, curX + parenW + parenGap + rawPowW + parenGap, pTop, pBottom, parenW, false);
        }

        int totalPowW = rawPowW + (needsParens ? (2 * parenW + 2 * parenGap) : 0);
        if (p != null) {
            p.cachedWidth = totalPowW + 1;
            p.cachedLift = lift;
        }
        return curX + totalPowW + 1;
    }

    public static int[] getCaretInfo(Graphics2D g2, String text, PowerRegion p, int curX,
            int targetOffset, Font font, FractionsSuperscript.FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
            PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
            ParensSuperscript.ParenRegion[] parens, int nestLevel, int end) {
        return getCaretInfo(g2, text, p, curX, targetOffset, font, fractions, roots, powers, integrals,
                null, null, null, null, null, parens, nestLevel, end);
    }

    public static int[] getCaretInfo(Graphics2D g2, String text, PowerRegion p, int curX,
            int targetOffset, Font font, FractionsSuperscript.FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
            PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations, LogsSuperScript.LogRegion[] logs,
            DerivativesSuperScript.DerivativeRegion[] derivatives,
            FloorAndCeilingSuperScript.FloorCeilRegion[] floorCeils,
            AbsoluteValueSuperScript.AbsoluteValueRegion[] absoluteValues,
            ParensSuperscript.ParenRegion[] parens, int nestLevel, int end) {
        if (p == null) return null;
        int close = p.closeParen != -1 && p.closeParen < end ? p.closeParen : end;
        if (targetOffset < p.caretChar || targetOffset > close) {
            return null;
        }
        FontMetrics fm = g2.getFontMetrics(font);
        int powStart = p.openParen != -1 ? p.openParen + 1 : p.caretChar + 1;
        Font nextFont = getExponentFont(font, p, nestLevel);
        FontMetrics nextFm = g2.getFontMetrics(nextFont);
        int lift;
        if (p.cachedLift != Integer.MIN_VALUE) {
            lift = p.cachedLift;
        } else {
            int contentDescent = MathRenderEngine.getRegionDescent(g2, text, powStart, close, nextFont, fractions, roots, powers,
                    integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel + 1);
            lift = getPowerLift(g2, text, p.caretChar, contentDescent, font, fractions, roots, powers, integrals,
                    parens, nestLevel);
        }
        curX += 1; // consistent gap before exponent

        boolean needsParens = needsAutoParens(text, powStart, close);
        int parenW = needsParens ? Math.max(4, Math.min(8, nextFm.charWidth('('))) : 0;
        int parenGap = needsParens ? 1 : 0;
        int expContentX = curX + (needsParens ? (parenW + parenGap) : 0);

        if (targetOffset == p.caretChar) {
            return new int[] { curX, 0, fm.getAscent(), fm.getDescent() };
        }
        if (targetOffset == p.openParen) {
            return new int[] { curX, -lift, nextFm.getAscent(), nextFm.getDescent() };
        }
        if (targetOffset >= powStart && targetOffset <= close) {
            int[] sub = MathRenderEngine.getCaretInfoSubString(g2, text, powStart, close,
                    targetOffset, nextFont, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel + 1);
            if (sub != null)
                return new int[] { expContentX + sub[0], -lift + sub[1], sub[2], sub[3] };
        }
        return null;
    }

    public static boolean isForbiddenCaret(int c, PowerRegion[] powers) {
        if (powers == null) return false;
        for (PowerRegion p : powers) {
            if (p != null && c == p.openParen)
                return true; // between ^ and (
        }
        return false;
    }
}
