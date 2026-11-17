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
 * Handles summation notation: sum[lower][upper](expression) or ∑[lower][upper](expression).
 * Renders the capital Sigma symbol using resolution-independent vector lines,
 * centers the upper limit above and lower limit below, and renders the unencapsulated
 * argument expression along the formula baseline.
 */
public class SummatoriesSuperScript {

    public static class SumRegion {
        public int sumStart = -1;
        public int sumEnd = -1;
        public int lowerOpen = -1;
        public int lowerClose = -1;
        public int upperOpen = -1;
        public int upperClose = -1;
        public int parenOpen = -1;
        public int parenClose = -1;
        public int cachedWidth = -1;
        public int cachedContentW = -1;
        public int cachedContentAscent = -1;
        public int cachedContentDescent = -1;

        public SumRegion() {}

        public SumRegion(SumRegion other) {
            if (other != null) {
                this.sumStart = other.sumStart;
                this.sumEnd = other.sumEnd;
                this.lowerOpen = other.lowerOpen;
                this.lowerClose = other.lowerClose;
                this.upperOpen = other.upperOpen;
                this.upperClose = other.upperClose;
                this.parenOpen = other.parenOpen;
                this.parenClose = other.parenClose;
            }
        }
    }

    public static SumRegion[] findRegions(String text) {
        if (text == null || text.isEmpty()) {
            return new SumRegion[0];
        }

        List<SumRegion> list = new ArrayList<>();
        int i = 0;
        int len = text.length();

        while (i < len) {
            int start = -1;
            int end = -1;

            if (text.charAt(i) == '∑' || text.charAt(i) == '\u2211' || text.charAt(i) == 'Σ' || text.charAt(i) == '\u03A3') {
                start = i;
                end = i + 1;
            } else if (i + 3 <= len && text.substring(i, i + 3).equalsIgnoreCase("sum")) {
                boolean prevLetter = (i > 0 && Character.isLetter(text.charAt(i - 1)));
                if (!prevLetter) {
                    int next = i + 3;
                    while (next < len && text.charAt(next) == ' ') next++;
                    if (next < len && (text.charAt(next) == '[' || text.charAt(next) == '(' || text.charAt(next) == '_')) {
                        start = i;
                        end = i + 3;
                    }
                }
            }

            if (start == -1) {
                i++;
                continue;
            }

            SumRegion r = new SumRegion();
            r.sumStart = start;
            r.sumEnd = end;

            int curr = end;
            while (curr < len && text.charAt(curr) == ' ') curr++;

            // Check brackets
            int b1Open = -1, b1Close = -1;
            int b2Open = -1, b2Close = -1;

            if (curr < len && text.charAt(curr) == '[') {
                b1Open = curr;
                int depth = 1;
                for (int j = curr + 1; j < len; j++) {
                    if (text.charAt(j) == '[') depth++;
                    else if (text.charAt(j) == ']') depth--;
                    if (depth == 0) {
                        b1Close = j;
                        break;
                    }
                }
                if (b1Close != -1) {
                    curr = b1Close + 1;
                    while (curr < len && text.charAt(curr) == ' ') curr++;
                    if (curr < len && text.charAt(curr) == '[') {
                        b2Open = curr;
                        depth = 1;
                        for (int j = curr + 1; j < len; j++) {
                            if (text.charAt(j) == '[') depth++;
                            else if (text.charAt(j) == ']') depth--;
                            if (depth == 0) {
                                b2Close = j;
                                break;
                            }
                        }
                        if (b2Close != -1) {
                            curr = b2Close + 1;
                        }
                    }
                }
            }

            // By default (like integrals): first bracket is upper (top index), second is lower (bottom index).
            // However, if the first bracket explicitly contains '=' (e.g. [i=1]) and the second does not,
            // treat first as lower and second as upper.
            boolean b1HasEq = (b1Open != -1 && b1Close > b1Open + 1 && text.substring(b1Open + 1, b1Close).contains("="));
            boolean b2HasEq = (b2Open != -1 && b2Close > b2Open + 1 && text.substring(b2Open + 1, b2Close).contains("="));
            if (b1HasEq && !b2HasEq) {
                r.lowerOpen = b1Open;
                r.lowerClose = b1Close;
                r.upperOpen = b2Open;
                r.upperClose = b2Close;
            } else {
                r.upperOpen = b1Open;
                r.upperClose = b1Close;
                r.lowerOpen = b2Open;
                r.lowerClose = b2Close;
            }

            while (curr < len && text.charAt(curr) == ' ') curr++;

            // Check argument parentheses (expression)
            if (curr < len && text.charAt(curr) == '(') {
                r.parenOpen = curr;
                int depth = 1;
                for (int j = curr + 1; j < len; j++) {
                    if (text.charAt(j) == '(') depth++;
                    else if (text.charAt(j) == ')') depth--;
                    if (depth == 0) {
                        r.parenClose = j;
                        break;
                    }
                }
            }

            list.add(r);
            i = Math.max(end, curr);
        }

        return list.toArray(new SumRegion[0]);
    }

    public static SumRegion getAt(SumRegion[] summations, int i) {
        if (summations == null) return null;
        for (SumRegion s : summations) {
            if (s != null && s.sumStart == i) {
                return s;
            }
        }
        return null;
    }

    public static void drawSummationSign(Graphics2D g2, int x, int topY, int bottomY, int width) {
        if (bottomY <= topY) return;
        float h = bottomY - topY;
        float strokeW = Math.max(1.4f, Math.min(2.4f, h * 0.048f));
        Stroke oldStroke = g2.getStroke();
        g2.setStroke(new BasicStroke(strokeW, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        float midY = (topY + bottomY) / 2.0f;
        float leftX = x + 2.0f;
        float rightX = x + width - 2.0f;
        float midX = leftX + (rightX - leftX) * 0.52f;

        Path2D.Double path = new Path2D.Double();
        // Top bar
        path.moveTo(rightX, topY);
        path.lineTo(leftX, topY);
        // Upper diagonal to vertex
        path.lineTo(midX, midY);
        // Lower diagonal from vertex
        path.lineTo(leftX, bottomY);
        // Bottom bar
        path.lineTo(rightX, bottomY);

        g2.draw(path);
        g2.setStroke(oldStroke);
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
        int baseSignHalf = (int) (fm.getAscent() * 1.15f);
        int contAboveAxis = Math.max(0, contentAscent - axisRel);
        return Math.max(baseSignHalf, contAboveAxis + 3) + axisRel;
    }

    public static int getSignDescent(FontMetrics fm, int contentDescent) {
        int axisRel = (int) (fm.getAscent() * 0.45f);
        int baseSignHalf = (int) (fm.getAscent() * 1.15f);
        int contBelowAxis = Math.max(0, contentDescent + axisRel);
        return Math.max(baseSignHalf, contBelowAxis + 3) - axisRel;
    }

    public static int getArgumentStart(SumRegion s) {
        if (s.parenOpen != -1) return s.parenOpen + 1;
        int lastClose = Math.max(s.upperClose, s.lowerClose);
        if (lastClose != -1) return lastClose + 1;
        return s.sumEnd;
    }

    public static int getArgumentEnd(SumRegion s, int fallbackEnd) {
        if (s.parenClose != -1 && s.parenClose < fallbackEnd) return s.parenClose;
        return fallbackEnd;
    }

    public static int getAscent(Graphics2D g2, String text, SumRegion s, Font font,
            FractionsSuperscript.FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
            SumRegion[] summations, ParensSuperscript.ParenRegion[] parens, int nestLevel, int end) {
        return getAscent(g2, text, s, font, fractions, roots, powers, integrals, summations, null, null, null, null, parens, nestLevel, end);
    }

    public static int getAscent(Graphics2D g2, String text, SumRegion s, Font font,
            FractionsSuperscript.FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
            SumRegion[] summations, LogsSuperScript.LogRegion[] logs,
            DerivativesSuperScript.DerivativeRegion[] derivatives,
            FloorAndCeilingSuperScript.FloorCeilRegion[] floorCeils,
            AbsoluteValueSuperScript.AbsoluteValueRegion[] absoluteValues,
            ParensSuperscript.ParenRegion[] parens, int nestLevel, int end) {
        FontMetrics fm = g2.getFontMetrics(font);
        int argStart = getArgumentStart(s);
        int argEnd = getArgumentEnd(s, end);
        int contAscent = fm.getAscent();
        if (argStart < argEnd) {
            contAscent = MathRenderEngine.getRegionAscent(g2, text, argStart, argEnd, font, fractions, roots, powers,
                    integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
        }

        Font limitFont = getLimitFont(font, nestLevel);
        FontMetrics limitFm = g2.getFontMetrics(limitFont);
        int upperAscent = limitFm.getAscent();
        int upperDescent = limitFm.getDescent();
        boolean upperHasText = (s.upperOpen != -1 && s.upperClose > s.upperOpen + 1);
        if (upperHasText) {
            upperAscent = MathRenderEngine.getRegionAscent(g2, text, s.upperOpen + 1, s.upperClose, limitFont,
                    fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel + 1);
            upperDescent = MathRenderEngine.getRegionDescent(g2, text, s.upperOpen + 1, s.upperClose, limitFont,
                    fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel + 1);
        }

        int signAscent = getSignAscent(fm, contAscent);
        int gap = 6;
        int upperTotalH = (s.upperOpen != -1) ? (upperAscent + upperDescent + gap) : 0;
        return Math.max(contAscent, signAscent + upperTotalH);
    }

    public static int getDescent(Graphics2D g2, String text, SumRegion s, Font font,
            FractionsSuperscript.FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
            SumRegion[] summations, ParensSuperscript.ParenRegion[] parens, int nestLevel, int end) {
        return getDescent(g2, text, s, font, fractions, roots, powers, integrals, summations, null, null, null, null, parens, nestLevel, end);
    }

    public static int getDescent(Graphics2D g2, String text, SumRegion s, Font font,
            FractionsSuperscript.FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
            SumRegion[] summations, LogsSuperScript.LogRegion[] logs,
            DerivativesSuperScript.DerivativeRegion[] derivatives,
            FloorAndCeilingSuperScript.FloorCeilRegion[] floorCeils,
            AbsoluteValueSuperScript.AbsoluteValueRegion[] absoluteValues,
            ParensSuperscript.ParenRegion[] parens, int nestLevel, int end) {
        FontMetrics fm = g2.getFontMetrics(font);
        int argStart = getArgumentStart(s);
        int argEnd = getArgumentEnd(s, end);
        int contDescent = fm.getDescent();
        if (argStart < argEnd) {
            contDescent = MathRenderEngine.getRegionDescent(g2, text, argStart, argEnd, font, fractions, roots, powers,
                    integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
        }

        Font limitFont = getLimitFont(font, nestLevel);
        FontMetrics limitFm = g2.getFontMetrics(limitFont);
        int lowerAscent = limitFm.getAscent();
        int lowerDescent = limitFm.getDescent();
        boolean lowerHasText = (s.lowerOpen != -1 && s.lowerClose > s.lowerOpen + 1);
        if (lowerHasText) {
            lowerAscent = MathRenderEngine.getRegionAscent(g2, text, s.lowerOpen + 1, s.lowerClose, limitFont,
                    fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel + 1);
            lowerDescent = MathRenderEngine.getRegionDescent(g2, text, s.lowerOpen + 1, s.lowerClose, limitFont,
                    fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel + 1);
        }

        int signDescent = getSignDescent(fm, contDescent);
        int gap = 6;
        int lowerTotalH = (s.lowerOpen != -1) ? (lowerAscent + lowerDescent + gap) : 0;
        return Math.max(contDescent, signDescent + lowerTotalH);
    }

    public static int render(Graphics2D g2, String text, SumRegion s, int curX, int baselineY,
            Font font, boolean draw, FractionsSuperscript.FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
            SumRegion[] summations, ParensSuperscript.ParenRegion[] parens, int nestLevel, int selStart, int selEnd, int end) {
        return render(g2, text, s, curX, baselineY, font, draw, fractions, roots, powers, integrals,
                summations, null, null, null, null, parens, nestLevel, selStart, selEnd, end);
    }

    public static int render(Graphics2D g2, String text, SumRegion s, int curX, int baselineY,
            Font font, boolean draw, FractionsSuperscript.FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
            SumRegion[] summations, LogsSuperScript.LogRegion[] logs,
            DerivativesSuperScript.DerivativeRegion[] derivatives,
            FloorAndCeilingSuperScript.FloorCeilRegion[] floorCeils,
            AbsoluteValueSuperScript.AbsoluteValueRegion[] absoluteValues,
            ParensSuperscript.ParenRegion[] parens, int nestLevel, int selStart, int selEnd, int end) {
        FontMetrics fm = g2.getFontMetrics(font);
        int argStart = getArgumentStart(s);
        int argEnd = getArgumentEnd(s, end);
        boolean isEmptyArg = (s.parenOpen != -1 && s.parenClose == s.parenOpen + 1) || (argStart >= argEnd);
        int emptyW = isEmptyArg ? Math.max(14, (int) (fm.charWidth(' ') * 1.5f)) : 0;

        int contentW = MathRenderEngine.renderSubString(g2, text, argStart, argEnd, 0, 0,
                font, false, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel, -1, -1);
        if (isEmptyArg) {
            contentW = Math.max(contentW, emptyW);
        }
        int contentAscent = MathRenderEngine.getRegionAscent(g2, text, argStart, argEnd,
                font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
        int contentDescent = MathRenderEngine.getRegionDescent(g2, text, argStart, argEnd,
                font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
        if (isEmptyArg) {
            contentAscent = Math.max(contentAscent, fm.getAscent());
            contentDescent = Math.max(contentDescent, fm.getDescent());
        }

        Font limitFont = getLimitFont(font, nestLevel);
        FontMetrics limitFm = g2.getFontMetrics(limitFont);

        int upperW = 0;
        int upperAscent = limitFm.getAscent();
        int upperDescent = limitFm.getDescent();
        boolean upperHasText = (s.upperOpen != -1 && s.upperClose > s.upperOpen + 1);
        boolean upperMultiCell = upperHasText && RootsSuperscript.isMultiCellIndex(text, s.upperOpen + 1, s.upperClose);
        boolean upperHasOuterBrackets = upperMultiCell && RootsSuperscript.alreadyHasOuterBrackets(text, s.upperOpen + 1, s.upperClose);
        boolean upperNeedsAutoBrackets = upperMultiCell && !upperHasOuterBrackets;
        int upperBracketW = upperNeedsAutoBrackets ? Math.max(3, (int) (limitFm.charWidth('[') * 0.75f)) : 0;

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
        int upperTotalW = upperW + (upperNeedsAutoBrackets ? 2 * upperBracketW + 2 : 0);

        int lowerW = 0;
        int lowerAscent = limitFm.getAscent();
        int lowerDescent = limitFm.getDescent();
        boolean lowerHasText = (s.lowerOpen != -1 && s.lowerClose > s.lowerOpen + 1);
        boolean lowerMultiCell = lowerHasText && RootsSuperscript.isMultiCellIndex(text, s.lowerOpen + 1, s.lowerClose);
        boolean lowerHasOuterBrackets = lowerMultiCell && RootsSuperscript.alreadyHasOuterBrackets(text, s.lowerOpen + 1, s.lowerClose);
        boolean lowerNeedsAutoBrackets = lowerMultiCell && !lowerHasOuterBrackets;
        int lowerBracketW = lowerNeedsAutoBrackets ? Math.max(3, (int) (limitFm.charWidth('[') * 0.75f)) : 0;

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
        int lowerTotalW = lowerW + (lowerNeedsAutoBrackets ? 2 * lowerBracketW + 2 : 0);

        int signAscent = getSignAscent(fm, contentAscent);
        int signDescent = getSignDescent(fm, contentDescent);
        int topY = baselineY - signAscent;
        int bottomY = baselineY + signDescent;
        int totalH = bottomY - topY;
        int signW = Math.max(16, (int) (fm.getAscent() * 1.15f + Math.max(0, totalH - fm.getAscent() * 2.3f) * 0.16f));

        int signX = curX;
        int upperX = upperNeedsAutoBrackets ? curX : curX + 2;
        int lowerX = lowerNeedsAutoBrackets ? curX : curX + 2;

        int gap = 6;
        int upperBase = topY - gap - upperDescent;
        int lowerBase = bottomY + gap + lowerAscent;

        int argGap = Math.max(4, (int) (fm.charWidth(' ') * 0.45f));
        int argX = curX + signW + argGap;
        int maxX = Math.max(argX + contentW, Math.max(upperX + upperTotalW, lowerX + lowerTotalW));
        int totalW = (maxX - curX) + 2;

        if (draw) {
            // 1. Draw Sigma Symbol
            drawSummationSign(g2, signX, topY, bottomY, signW);

            // 2. Draw Upper Limit (above Sigma)
            if (upperHasText || (selStart > s.upperOpen && selStart <= s.upperClose)) {
                int uContentX = upperX + (upperNeedsAutoBrackets ? upperBracketW + 1 : 0);
                if (upperNeedsAutoBrackets) {
                    ParensSuperscript.drawStretchyBracket(g2, upperX, upperBase - upperAscent, upperBase + upperDescent, upperBracketW, true);
                }
                MathRenderEngine.renderSubString(g2, text, s.upperOpen + 1, s.upperClose, uContentX, upperBase,
                        limitFont, true, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel + 1,
                        selStart, selEnd);
                if (upperNeedsAutoBrackets) {
                    ParensSuperscript.drawStretchyBracket(g2, uContentX + upperW + 1, upperBase - upperAscent, upperBase + upperDescent, upperBracketW, false);
                }
            }

            // 3. Draw Lower Limit (below Sigma)
            if (lowerHasText || (selStart > s.lowerOpen && selStart <= s.lowerClose)) {
                int lContentX = lowerX + (lowerNeedsAutoBrackets ? lowerBracketW + 1 : 0);
                if (lowerNeedsAutoBrackets) {
                    ParensSuperscript.drawStretchyBracket(g2, lowerX, lowerBase - lowerAscent, lowerBase + lowerDescent, lowerBracketW, true);
                }
                MathRenderEngine.renderSubString(g2, text, s.lowerOpen + 1, s.lowerClose, lContentX, lowerBase,
                        limitFont, true, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel + 1,
                        selStart, selEnd);
                if (lowerNeedsAutoBrackets) {
                    ParensSuperscript.drawStretchyBracket(g2, lContentX + lowerW + 1, lowerBase - lowerAscent, lowerBase + lowerDescent, lowerBracketW, false);
                }
            }

            // 4. Draw Argument Expression (unencapsulated)
            MathRenderEngine.renderSubString(g2, text, argStart, argEnd, argX, baselineY,
                    font, true, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel, selStart, selEnd);
        }

        if (s != null) {
            s.cachedWidth = totalW;
            s.cachedContentW = contentW;
            s.cachedContentAscent = contentAscent;
            s.cachedContentDescent = contentDescent;
        }
        return curX + totalW;
    }

    public static int[] getCaretInfo(Graphics2D g2, String text, SumRegion s, int curX,
            int targetOffset, Font font, FractionsSuperscript.FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
            SumRegion[] summations, ParensSuperscript.ParenRegion[] parens, int nestLevel, int end) {
        return getCaretInfo(g2, text, s, curX, targetOffset, font, fractions, roots, powers, integrals, summations,
                null, null, null, null, parens, nestLevel, end);
    }

    public static int[] getCaretInfo(Graphics2D g2, String text, SumRegion s, int curX,
            int targetOffset, Font font, FractionsSuperscript.FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
            SumRegion[] summations, LogsSuperScript.LogRegion[] logs,
            DerivativesSuperScript.DerivativeRegion[] derivatives,
            FloorAndCeilingSuperScript.FloorCeilRegion[] floorCeils,
            AbsoluteValueSuperScript.AbsoluteValueRegion[] absoluteValues,
            ParensSuperscript.ParenRegion[] parens, int nestLevel, int end) {
        if (s == null) return null;
        int nextI = (s.parenClose != -1 && s.parenClose < end) ? s.parenClose + 1 : getArgumentEnd(s, end);
        if (targetOffset < s.sumStart || targetOffset > nextI) {
            return null;
        }
        FontMetrics fm = g2.getFontMetrics(font);
        int argStart = getArgumentStart(s);
        int argEnd = getArgumentEnd(s, end);
        boolean isEmptyArg = (s.parenOpen != -1 && s.parenClose == s.parenOpen + 1) || (argStart >= argEnd);
        int emptyW = isEmptyArg ? Math.max(14, (int) (fm.charWidth(' ') * 1.5f)) : 0;

        int contentW;
        int contentAscent;
        int contentDescent;
        if (s.cachedContentW != -1) {
            contentW = s.cachedContentW;
            contentAscent = s.cachedContentAscent;
            contentDescent = s.cachedContentDescent;
        } else {
            contentW = MathRenderEngine.renderSubString(g2, text, argStart, argEnd, 0, 0,
                    font, false, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel, -1, -1);
            if (isEmptyArg) {
                contentW = Math.max(contentW, emptyW);
            }
            contentAscent = MathRenderEngine.getRegionAscent(g2, text, argStart, argEnd,
                    font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
            contentDescent = MathRenderEngine.getRegionDescent(g2, text, argStart, argEnd,
                    font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
            if (isEmptyArg) {
                contentAscent = Math.max(contentAscent, fm.getAscent());
                contentDescent = Math.max(contentDescent, fm.getDescent());
            }
        }

        Font limitFont = getLimitFont(font, nestLevel);
        FontMetrics limitFm = g2.getFontMetrics(limitFont);

        int upperW = 0;
        int upperAscent = limitFm.getAscent();
        int upperDescent = limitFm.getDescent();
        boolean upperHasText = (s.upperOpen != -1 && s.upperClose > s.upperOpen + 1);
        boolean upperMultiCell = upperHasText && RootsSuperscript.isMultiCellIndex(text, s.upperOpen + 1, s.upperClose);
        boolean upperHasOuterBrackets = upperMultiCell && RootsSuperscript.alreadyHasOuterBrackets(text, s.upperOpen + 1, s.upperClose);
        boolean upperNeedsAutoBrackets = upperMultiCell && !upperHasOuterBrackets;
        int upperBracketW = upperNeedsAutoBrackets ? Math.max(3, (int) (limitFm.charWidth('[') * 0.75f)) : 0;

        if (upperHasText) {
            upperW = MathRenderEngine.renderSubString(g2, text, s.upperOpen + 1, s.upperClose, 0, 0,
                    limitFont, false, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel + 1, -1, -1);
            upperAscent = MathRenderEngine.getRegionAscent(g2, text, s.upperOpen + 1, s.upperClose, limitFont, fractions,
                    roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel + 1);
            upperDescent = MathRenderEngine.getRegionDescent(g2, text, s.upperOpen + 1, s.upperClose, limitFont, fractions,
                    roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel + 1);
        }

        int lowerW = 0;
        int lowerAscent = limitFm.getAscent();
        int lowerDescent = limitFm.getDescent();
        boolean lowerHasText = (s.lowerOpen != -1 && s.lowerClose > s.lowerOpen + 1);
        boolean lowerMultiCell = lowerHasText && RootsSuperscript.isMultiCellIndex(text, s.lowerOpen + 1, s.lowerClose);
        boolean lowerHasOuterBrackets = lowerMultiCell && RootsSuperscript.alreadyHasOuterBrackets(text, s.lowerOpen + 1, s.lowerClose);
        boolean lowerNeedsAutoBrackets = lowerMultiCell && !lowerHasOuterBrackets;
        int lowerBracketW = lowerNeedsAutoBrackets ? Math.max(3, (int) (limitFm.charWidth('[') * 0.75f)) : 0;

        if (lowerHasText) {
            lowerW = MathRenderEngine.renderSubString(g2, text, s.lowerOpen + 1, s.lowerClose, 0, 0,
                    limitFont, false, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel + 1, -1, -1);
            lowerAscent = MathRenderEngine.getRegionAscent(g2, text, s.lowerOpen + 1, s.lowerClose, limitFont, fractions,
                    roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel + 1);
            lowerDescent = MathRenderEngine.getRegionDescent(g2, text, s.lowerOpen + 1, s.lowerClose, limitFont, fractions,
                    roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel + 1);
        }

        int signAscent = getSignAscent(fm, contentAscent);
        int signDescent = getSignDescent(fm, contentDescent);
        int topY = -signAscent;
        int bottomY = signDescent;
        int totalH = bottomY - topY;
        int signW = Math.max(16, (int) (fm.getAscent() * 1.15f + Math.max(0, totalH - fm.getAscent() * 2.3f) * 0.16f));
        int upperTotalW = upperW + (upperNeedsAutoBrackets ? 2 * upperBracketW + 2 : 0);
        int lowerTotalW = lowerW + (lowerNeedsAutoBrackets ? 2 * lowerBracketW + 2 : 0);
        int signX = curX;
        int upperX = upperNeedsAutoBrackets ? curX : curX + 2;
        int lowerX = lowerNeedsAutoBrackets ? curX : curX + 2;

        int gap = 6;
        int upperBase = topY - gap - upperDescent;
        int lowerBase = bottomY + gap + lowerAscent;

        int argGap = Math.max(4, (int) (fm.charWidth(' ') * 0.45f));
        int argX = curX + signW + argGap;

        if (targetOffset >= s.sumStart && targetOffset <= s.sumEnd) {
            return new int[] { curX, 0, signAscent, signDescent };
        }

        // Caret in Lower Limit
        if (s.lowerOpen != -1 && targetOffset > s.lowerOpen && targetOffset <= s.lowerClose) {
            int lContentX = lowerX + (lowerNeedsAutoBrackets ? lowerBracketW + 1 : 0);
            if (targetOffset == s.lowerClose && lowerHasText) {
                return new int[] { lContentX + lowerW, lowerBase, limitFm.getAscent(), limitFm.getDescent() };
            }
            int[] sub = MathRenderEngine.getCaretInfoSubString(g2, text, s.lowerOpen + 1, s.lowerClose,
                    targetOffset, limitFont, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel + 1);
            if (sub != null) {
                return new int[] { lContentX + sub[0], lowerBase + sub[1], sub[2], sub[3] };
            }
            return new int[] { lContentX, lowerBase, limitFm.getAscent(), limitFm.getDescent() };
        }

        // Caret in Upper Limit
        if (s.upperOpen != -1 && targetOffset > s.upperOpen && targetOffset <= s.upperClose) {
            int uContentX = upperX + (upperNeedsAutoBrackets ? upperBracketW + 1 : 0);
            if (targetOffset == s.upperClose && upperHasText) {
                return new int[] { uContentX + upperW, upperBase, limitFm.getAscent(), limitFm.getDescent() };
            }
            int[] sub = MathRenderEngine.getCaretInfoSubString(g2, text, s.upperOpen + 1, s.upperClose,
                    targetOffset, limitFont, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel + 1);
            if (sub != null) {
                return new int[] { uContentX + sub[0], upperBase + sub[1], sub[2], sub[3] };
            }
            return new int[] { uContentX, upperBase, limitFm.getAscent(), limitFm.getDescent() };
        }

        // Caret in Argument
        if (targetOffset >= argStart && targetOffset <= argEnd) {
            if (isEmptyArg) {
                return new int[] { argX + 2, 0, fm.getAscent(), fm.getDescent() };
            }
            int[] sub = MathRenderEngine.getCaretInfoSubString(g2, text, argStart, argEnd,
                    targetOffset, font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
            if (sub != null) {
                return new int[] { argX + sub[0], sub[1], sub[2], sub[3] };
            }
            return new int[] { argX + contentW, 0, fm.getAscent(), fm.getDescent() };
        }

        if (s.parenClose != -1 && (targetOffset == s.parenClose || (targetOffset == s.parenClose + 1 && targetOffset >= text.length()))) {
            return new int[] { argX + contentW, 0, fm.getAscent(), fm.getDescent() };
        }

        return null;
    }

    public static boolean isForbiddenCaret(int c, SumRegion[] summations) {
        if (summations == null) return false;
        for (SumRegion s : summations) {
            if (s == null) continue;
            if (c > s.sumStart && c <= s.sumEnd) return true;
            if (s.lowerOpen != -1 && c == s.lowerOpen) return true;
            if (s.upperOpen != -1 && c == s.upperOpen) return true;
            if (s.parenOpen != -1 && c == s.parenOpen) return true;
        }
        return false;
    }
}
