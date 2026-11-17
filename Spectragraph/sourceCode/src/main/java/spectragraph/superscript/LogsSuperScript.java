package com.spectragraph.superscript;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles detection, measurement, caret positioning, and rendering for
 * logarithm subscript bases and arguments: log[base](arg).
 */
public class LogsSuperScript {

    public static class LogRegion {
        public int logStart = -1;    // index of 'l' in "log"
        public int logEnd = -1;      // index after 'g' (logStart + 3)
        public int bracketOpen = -1; // index of '[' for base
        public int bracketClose = -1;// index of ']' for base
        public int parenOpen = -1;   // index of '(' for arg
        public int parenClose = -1;  // index of ')' for arg
        public int cachedWidth = -1;

        public LogRegion() {}

        public LogRegion(LogRegion other) {
            if (other != null) {
                this.logStart = other.logStart;
                this.logEnd = other.logEnd;
                this.bracketOpen = other.bracketOpen;
                this.bracketClose = other.bracketClose;
                this.parenOpen = other.parenOpen;
                this.parenClose = other.parenClose;
            }
        }

        public int getEnd(int textLength) {
            if (parenClose != -1 && parenClose < textLength) {
                return parenClose + 1;
            }
            if (bracketClose != -1 && bracketClose < textLength) {
                return bracketClose + 1;
            }
            return Math.min(textLength, logEnd != -1 ? logEnd : logStart + 3);
        }
    }

    public static LogRegion[] findRegions(String text) {
        if (text == null || text.length() < 3) {
            return new LogRegion[0];
        }

        List<LogRegion> list = new ArrayList<>();
        int len = text.length();
        int i = 0;
        while (i <= len - 3) {
            if ((text.charAt(i) == 'l' || text.charAt(i) == 'L')
                    && (text.charAt(i + 1) == 'o' || text.charAt(i + 1) == 'O')
                    && (text.charAt(i + 2) == 'g' || text.charAt(i + 2) == 'G')) {
                // Must be standalone word (not preceded by letter or digit)
                if (i > 0 && Character.isLetterOrDigit(text.charAt(i - 1))) {
                    i += 3;
                    continue;
                }
                // Also check it's not followed by another letter, e.g. "logical"
                if (i + 3 < len && Character.isLetter(text.charAt(i + 3))) {
                    i += 3;
                    continue;
                }

                LogRegion r = new LogRegion();
                r.logStart = i;
                r.logEnd = i + 3;

                int next = i + 3;
                while (next < len && text.charAt(next) == ' ') next++;

                boolean hasUnderscore = false;
                if (next < len && text.charAt(next) == '_') {
                    hasUnderscore = true;
                    next++;
                    while (next < len && text.charAt(next) == ' ') next++;
                }

                if (next < len && (text.charAt(next) == '[' || (hasUnderscore && text.charAt(next) == '('))) {
                    char openBracket = text.charAt(next);
                    char closeBracket = (openBracket == '[') ? ']' : ')';
                    r.bracketOpen = next;
                    int depth = 1;
                    for (int j = next + 1; j < len; j++) {
                        char c = text.charAt(j);
                        if (c == openBracket) depth++;
                        else if (c == closeBracket) {
                            depth--;
                            if (depth == 0) {
                                r.bracketClose = j;
                                break;
                            }
                        }
                    }
                    if (r.bracketClose != -1) {
                        int afterB = r.bracketClose + 1;
                        while (afterB < len && text.charAt(afterB) == ' ') afterB++;
                        if (afterB < len && text.charAt(afterB) == '(') {
                            r.parenOpen = afterB;
                            int pDepth = 1;
                            for (int j = afterB + 1; j < len; j++) {
                                char c = text.charAt(j);
                                if (c == '(') pDepth++;
                                else if (c == ')') {
                                    pDepth--;
                                    if (pDepth == 0) {
                                        r.parenClose = j;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                } else if (next < len && text.charAt(next) == '(') {
                    r.parenOpen = next;
                    int pDepth = 1;
                    for (int j = next + 1; j < len; j++) {
                        char c = text.charAt(j);
                        if (c == '(') pDepth++;
                        else if (c == ')') {
                            pDepth--;
                            if (pDepth == 0) {
                                r.parenClose = j;
                                break;
                            }
                        }
                    }
                }

                list.add(r);
                i += 3;
            } else {
                i++;
            }
        }

        return list.toArray(new LogRegion[0]);
    }

    public static LogRegion getAt(LogRegion[] logs, int i) {
        if (logs == null) return null;
        for (LogRegion r : logs) {
            if (r != null && r.logStart == i) {
                return r;
            }
        }
        return null;
    }

    public static Font getBaseFont(Font font, int nestLevel) {
        if (font == null) return null;
        if (nestLevel > 0) {
            return font;
        }
        float newSize = Math.max(font.getSize2D() * 0.72f, 9.0f);
        return font.deriveFont(newSize);
    }

    public static boolean isLargeBase(String text, int start, int end) {
        if (text == null || start >= end) return false;
        while (start < end && text.charAt(start) == ' ') start++;
        while (end > start && text.charAt(end - 1) == ' ') end--;
        if (start >= end) return false;

        if (alreadyHasOuterBrackets(text, start, end)) {
            return false;
        }

        for (int i = start; i < end; i++) {
            char c = text.charAt(i);
            if (c == '+' || c == '-' || c == '*' || c == '/' || c == '^' ||
                c == '√' || c == '\u221A' || c == '∫' || c == '\u222B' ||
                c == '∑' || c == '\u2211' || c == '(' || c == ')' || c == '=' || c == ',') {
                return true;
            }
        }

        return (end - start) > 2;
    }

    public static boolean alreadyHasOuterBrackets(String text, int start, int end) {
        if (text == null || start >= end) return false;
        while (start < end && text.charAt(start) == ' ') start++;
        while (end > start && text.charAt(end - 1) == ' ') end--;
        if (start < end && (text.charAt(start) == '[' || text.charAt(start) == '(')) {
            char open = text.charAt(start);
            char close = (open == '[') ? ']' : ')';
            int match = MathRenderEngine.findMatchingChar(text, start, open, close);
            return match == end - 1;
        }
        return false;
    }

    public static int getAscent(Graphics2D g2, String text, LogRegion lg, Font font,
                                FractionsSuperscript.FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
                                ExponentsSuperscript.PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
                                SummatoriesSuperScript.SumRegion[] summations, ParensSuperscript.ParenRegion[] parens,
                                int nestLevel, int end) {
        return getAscent(g2, text, lg, font, fractions, roots, powers, integrals, summations, null, null, null, null, parens, nestLevel, end);
    }

    public static int getAscent(Graphics2D g2, String text, LogRegion lg, Font font,
                                FractionsSuperscript.FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
                                ExponentsSuperscript.PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
                                SummatoriesSuperScript.SumRegion[] summations, LogRegion[] logs,
                                DerivativesSuperScript.DerivativeRegion[] derivatives,
                                FloorAndCeilingSuperScript.FloorCeilRegion[] floorCeils,
                                AbsoluteValueSuperScript.AbsoluteValueRegion[] absoluteValues,
                                ParensSuperscript.ParenRegion[] parens,
                                int nestLevel, int end) {
        FontMetrics fm = g2.getFontMetrics(font);
        int logAscent = fm.getAscent();

        int argAscent = logAscent;
        if (lg.parenOpen != -1) {
            int argStart = lg.parenOpen + 1;
            int argClose = (lg.parenClose != -1 && lg.parenClose < end) ? lg.parenClose : end;
            if (argStart < argClose) {
                argAscent = MathRenderEngine.getRegionAscent(g2, text, argStart, argClose, font,
                        fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
                int argDescent = MathRenderEngine.getRegionDescent(g2, text, argStart, argClose, font,
                        fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
                int totalArgH = argAscent + argDescent;
                boolean stretchyParen = argAscent > (int) (fm.getAscent() * 1.15f)
                        || argDescent > (int) (fm.getDescent() * 1.15f)
                        || (totalArgH > fm.getHeight() + 2);
                if (stretchyParen) {
                    argAscent = Math.max(fm.getAscent(), argAscent) + 2;
                }
            }
        }

        return Math.max(logAscent, argAscent);
    }

    public static int getDescent(Graphics2D g2, String text, LogRegion lg, Font font,
                                 FractionsSuperscript.FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
                                 ExponentsSuperscript.PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
                                 SummatoriesSuperScript.SumRegion[] summations, ParensSuperscript.ParenRegion[] parens,
                                 int nestLevel, int end) {
        return getDescent(g2, text, lg, font, fractions, roots, powers, integrals, summations, null, null, null, null, parens, nestLevel, end);
    }

    public static int getDescent(Graphics2D g2, String text, LogRegion lg, Font font,
                                 FractionsSuperscript.FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
                                 ExponentsSuperscript.PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
                                 SummatoriesSuperScript.SumRegion[] summations, LogRegion[] logs,
                                 DerivativesSuperScript.DerivativeRegion[] derivatives,
                                 FloorAndCeilingSuperScript.FloorCeilRegion[] floorCeils,
                                 AbsoluteValueSuperScript.AbsoluteValueRegion[] absoluteValues,
                                 ParensSuperscript.ParenRegion[] parens,
                                 int nestLevel, int end) {
        FontMetrics fm = g2.getFontMetrics(font);
        int baseDescent = fm.getDescent();

        int argDescent = baseDescent;
        if (lg.parenOpen != -1) {
            int argStart = lg.parenOpen + 1;
            int argClose = (lg.parenClose != -1 && lg.parenClose < end) ? lg.parenClose : end;
            if (argStart < argClose) {
                int argAscent = MathRenderEngine.getRegionAscent(g2, text, argStart, argClose, font,
                        fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
                argDescent = MathRenderEngine.getRegionDescent(g2, text, argStart, argClose, font,
                        fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
                int totalArgH = argAscent + argDescent;
                boolean stretchyParen = argAscent > (int) (fm.getAscent() * 1.15f)
                        || argDescent > (int) (fm.getDescent() * 1.15f)
                        || (totalArgH > fm.getHeight() + 2);
                if (stretchyParen) {
                    argDescent = Math.max(fm.getDescent(), argDescent) + 2;
                }
            }
        }

        int baseBottomRel = 0;
        if (lg.bracketOpen != -1) {
            Font baseFont = getBaseFont(font, nestLevel);
            FontMetrics baseFm = g2.getFontMetrics(baseFont);
            int subAscent = baseFm.getAscent();
            int subDescent = baseFm.getDescent();
            int bStart = lg.bracketOpen + 1;
            int bClose = (lg.bracketClose != -1 && lg.bracketClose < end) ? lg.bracketClose : end;
            if (bStart < bClose) {
                subAscent = MathRenderEngine.getRegionAscent(g2, text, bStart, bClose, baseFont,
                        fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel + 1);
                subDescent = MathRenderEngine.getRegionDescent(g2, text, bStart, bClose, baseFont,
                        fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel + 1);
            }
            int subShift = Math.max((int) (fm.getAscent() * 0.40f), subAscent - (int) (fm.getAscent() * 0.20f));
            baseBottomRel = subShift + subDescent + 2;
        }

        return Math.max(baseDescent, Math.max(argDescent, baseBottomRel));
    }

    public static int render(Graphics2D g2, String text, LogRegion lg, int curX, int baselineY, Font font, boolean draw,
                             FractionsSuperscript.FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
                             ExponentsSuperscript.PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
                             SummatoriesSuperScript.SumRegion[] summations, ParensSuperscript.ParenRegion[] parens,
                             int nestLevel, int selStart, int selEnd, int end) {
        return render(g2, text, lg, curX, baselineY, font, draw, fractions, roots, powers, integrals, summations,
                null, null, null, null, parens, nestLevel, selStart, selEnd, end);
    }

    public static int render(Graphics2D g2, String text, LogRegion lg, int curX, int baselineY, Font font, boolean draw,
                             FractionsSuperscript.FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
                             ExponentsSuperscript.PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
                             SummatoriesSuperScript.SumRegion[] summations, LogRegion[] logs,
                             DerivativesSuperScript.DerivativeRegion[] derivatives,
                             FloorAndCeilingSuperScript.FloorCeilRegion[] floorCeils,
                             AbsoluteValueSuperScript.AbsoluteValueRegion[] absoluteValues,
                             ParensSuperscript.ParenRegion[] parens,
                             int nestLevel, int selStart, int selEnd, int end) {
        int startX = curX;
        FontMetrics fm = g2.getFontMetrics(font);

        // 1. Draw "log"
        if (draw) {
            g2.setFont(font);
            g2.drawString("log", curX, baselineY);
        }
        curX += fm.stringWidth("log");

        // 2. Base subscript
        if (lg.bracketOpen != -1) {
            curX += 1; // Small gap after "log"
            Font baseFont = getBaseFont(font, nestLevel);
            FontMetrics baseFm = g2.getFontMetrics(baseFont);

            int bStart = lg.bracketOpen + 1;
            int bClose = (lg.bracketClose != -1 && lg.bracketClose < end) ? lg.bracketClose : end;
            boolean hasBaseText = (bClose > bStart);

            int subAscent = baseFm.getAscent();
            int subDescent = baseFm.getDescent();
            if (hasBaseText) {
                subAscent = MathRenderEngine.getRegionAscent(g2, text, bStart, bClose, baseFont,
                        fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel + 1);
                subDescent = MathRenderEngine.getRegionDescent(g2, text, bStart, bClose, baseFont,
                        fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel + 1);
            }

            int subShift = Math.max((int) (fm.getAscent() * 0.40f), subAscent - (int) (fm.getAscent() * 0.20f));
            int baseBaseline = baselineY + subShift;

            boolean isLarge = hasBaseText && isLargeBase(text, bStart, bClose);
            int bracketW = isLarge ? Math.max(3, (int) (baseFm.charWidth('[') * 0.75f)) : 0;
            int bracketGap = isLarge ? 1 : 0;

            if (isLarge && draw) {
                int bTop = baseBaseline - subAscent;
                int bBottom = baseBaseline + subDescent;
                ParensSuperscript.drawStretchyBracket(g2, curX, bTop, bBottom, bracketW, true);
            }
            curX += bracketW + bracketGap;

            int rawBaseW = 0;
            if (hasBaseText) {
                rawBaseW = MathRenderEngine.renderSubString(g2, text, bStart, bClose, curX, baseBaseline, baseFont, draw,
                        fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel + 1, selStart, selEnd);
            } else {
                // Empty base placeholder space
                rawBaseW = Math.max(3, (int) (baseFm.charWidth(' ') * 0.8f));
            }
            curX += rawBaseW;

            if (isLarge && draw) {
                int bTop = baseBaseline - subAscent;
                int bBottom = baseBaseline + subDescent;
                ParensSuperscript.drawStretchyBracket(g2, curX + bracketGap, bTop, bBottom, bracketW, false);
            }
            curX += bracketGap + bracketW;
        }

        // 3. Argument
        if (lg.parenOpen != -1) {
            curX += 2;
            int argStart = lg.parenOpen + 1;
            int argClose = (lg.parenClose != -1 && lg.parenClose < end) ? lg.parenClose : end;

            int argAscent = MathRenderEngine.getRegionAscent(g2, text, argStart, argClose, font,
                    fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
            int argDescent = MathRenderEngine.getRegionDescent(g2, text, argStart, argClose, font,
                    fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
            int totalArgH = argAscent + argDescent;
            boolean stretchyParen = argAscent > (int) (fm.getAscent() * 1.15f)
                    || argDescent > (int) (fm.getDescent() * 1.15f)
                    || (totalArgH > fm.getHeight() + 2);
            int topY = baselineY - Math.max(fm.getAscent(), argAscent) - 2;
            int bottomY = baselineY + Math.max(fm.getDescent(), argDescent) + 2;
            int h = bottomY - topY;
            int parenW = stretchyParen ? Math.max(fm.charWidth('('), Math.min(13, 6 + h / 20)) : fm.charWidth('(');

            if (draw) {
                if (selStart <= lg.parenOpen && selEnd > lg.parenOpen) {
                    g2.setColor(new Color(130, 160, 210, 100));
                    int pTop = stretchyParen ? topY : (baselineY - fm.getAscent());
                    int pH = stretchyParen ? h : fm.getHeight();
                    g2.fillRect(curX, pTop, parenW, pH);
                    g2.setColor(Color.WHITE);
                }
                if (stretchyParen) {
                    ParensSuperscript.drawStretchyParen(g2, curX, topY, bottomY, parenW, true);
                } else {
                    g2.setFont(font);
                    g2.drawString("(", curX, baselineY);
                }
            }
            curX += parenW;

            int rawArgW = 0;
            if (argClose > argStart) {
                rawArgW = MathRenderEngine.renderSubString(g2, text, argStart, argClose, curX, baselineY, font, draw,
                        fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel, selStart, selEnd);
            }
            curX += rawArgW;

            if (draw && lg.parenClose != -1) {
                if (selStart <= lg.parenClose && selEnd > lg.parenClose) {
                    g2.setColor(new Color(130, 160, 210, 100));
                    int pTop = stretchyParen ? topY : (baselineY - fm.getAscent());
                    int pH = stretchyParen ? h : fm.getHeight();
                    g2.fillRect(curX, pTop, parenW, pH);
                    g2.setColor(Color.WHITE);
                }
                if (stretchyParen) {
                    ParensSuperscript.drawStretchyParen(g2, curX, topY, bottomY, parenW, false);
                } else {
                    g2.setFont(font);
                    g2.drawString(")", curX, baselineY);
                }
            }
            if (lg.parenClose != -1) {
                curX += parenW;
            }
        }

        int totalW = curX - startX;
        if (lg != null) lg.cachedWidth = totalW;
        return curX;
    }

    public static int[] getCaretInfo(Graphics2D g2, String text, LogRegion lg, int curX, int targetOffset, Font font,
                                    FractionsSuperscript.FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
                                    ExponentsSuperscript.PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
                                    SummatoriesSuperScript.SumRegion[] summations, ParensSuperscript.ParenRegion[] parens,
                                    int nestLevel, int end) {
        return getCaretInfo(g2, text, lg, curX, targetOffset, font, fractions, roots, powers, integrals, summations,
                null, null, null, null, parens, nestLevel, end);
    }

    public static int[] getCaretInfo(Graphics2D g2, String text, LogRegion lg, int curX, int targetOffset, Font font,
                                    FractionsSuperscript.FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
                                    ExponentsSuperscript.PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
                                    SummatoriesSuperScript.SumRegion[] summations, LogRegion[] logs,
                                    DerivativesSuperScript.DerivativeRegion[] derivatives,
                                    FloorAndCeilingSuperScript.FloorCeilRegion[] floorCeils,
                                    AbsoluteValueSuperScript.AbsoluteValueRegion[] absoluteValues,
                                    ParensSuperscript.ParenRegion[] parens,
                                    int nestLevel, int end) {
        if (lg == null) return null;
        int lgEnd = lg.getEnd(end);
        if (targetOffset < lg.logStart || targetOffset > lgEnd) {
            return null;
        }
        FontMetrics fm = g2.getFontMetrics(font);

        if (targetOffset == lg.logStart) {
            return new int[] { curX, 0, fm.getAscent(), fm.getDescent() };
        }

        int afterLogX = curX + fm.stringWidth("log");

        // Base
        if (lg.bracketOpen != -1) {
            Font baseFont = getBaseFont(font, nestLevel);
            FontMetrics baseFm = g2.getFontMetrics(baseFont);

            int bStart = lg.bracketOpen + 1;
            int bClose = (lg.bracketClose != -1 && lg.bracketClose < end) ? lg.bracketClose : end;
            boolean hasBaseText = (bClose > bStart);

            int subAscent = baseFm.getAscent();
            int subDescent = baseFm.getDescent();
            if (hasBaseText) {
                subAscent = MathRenderEngine.getRegionAscent(g2, text, bStart, bClose, baseFont,
                        fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel + 1);
                subDescent = MathRenderEngine.getRegionDescent(g2, text, bStart, bClose, baseFont,
                        fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel + 1);
            }
            int subShift = Math.max((int) (fm.getAscent() * 0.40f), subAscent - (int) (fm.getAscent() * 0.20f));

            boolean isLarge = hasBaseText && isLargeBase(text, bStart, bClose);
            int bracketW = isLarge ? Math.max(3, (int) (baseFm.charWidth('[') * 0.75f)) : 0;
            int bracketGap = isLarge ? 1 : 0;
            int baseStartX = afterLogX + 1 + bracketW + bracketGap;

            if (targetOffset > lg.bracketOpen && targetOffset <= bClose) {
                if (hasBaseText) {
                    int[] ci = MathRenderEngine.getCaretInfoSubString(g2, text, bStart, bClose, targetOffset, baseFont,
                            fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel + 1);
                    if (ci != null) {
                        return new int[] { baseStartX + ci[0], subShift + ci[1], ci[2], ci[3] };
                    }
                } else {
                    return new int[] { baseStartX, subShift, baseFm.getAscent(), baseFm.getDescent() };
                }
            }
        }

        // Argument
        if (lg.parenOpen != -1) {
            int argStart = lg.parenOpen + 1;
            int argClose = (lg.parenClose != -1 && lg.parenClose < end) ? lg.parenClose : end;

            // Measure base total width to locate argument start
            int baseTotalW = 0;
            if (lg.bracketOpen != -1) {
                Font baseFont = getBaseFont(font, nestLevel);
                FontMetrics baseFm = g2.getFontMetrics(baseFont);
                int bStart = lg.bracketOpen + 1;
                int bClose = (lg.bracketClose != -1 && lg.bracketClose < end) ? lg.bracketClose : end;
                boolean hasBaseText = (bClose > bStart);
                boolean isLarge = hasBaseText && isLargeBase(text, bStart, bClose);
                int bracketW = isLarge ? Math.max(3, (int) (baseFm.charWidth('[') * 0.75f)) : 0;
                int bracketGap = isLarge ? 1 : 0;
                int rawBaseW = hasBaseText ? MathRenderEngine.renderSubString(g2, text, bStart, bClose, 0, 0, baseFont, false,
                        fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel + 1, -1, -1)
                        : Math.max(3, (int) (baseFm.charWidth(' ') * 0.8f));
                baseTotalW = 1 + (2 * bracketW + 2 * bracketGap) + rawBaseW;
            }

            int argAscent = MathRenderEngine.getRegionAscent(g2, text, argStart, argClose, font,
                    fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
            int argDescent = MathRenderEngine.getRegionDescent(g2, text, argStart, argClose, font,
                    fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
            int totalArgH = argAscent + argDescent;
            boolean stretchyParen = argAscent > (int) (fm.getAscent() * 1.15f)
                    || argDescent > (int) (fm.getDescent() * 1.15f)
                    || (totalArgH > fm.getHeight() + 2);
            int h = Math.max(fm.getAscent(), argAscent) + Math.max(fm.getDescent(), argDescent) + 4;
            int parenW = stretchyParen ? Math.max(fm.charWidth('('), Math.min(13, 6 + h / 20)) : fm.charWidth('(');

            int argStartX = afterLogX + baseTotalW + 2 + parenW;

            if (targetOffset > lg.parenOpen && targetOffset <= argClose) {
                if (argClose > argStart) {
                    int[] ci = MathRenderEngine.getCaretInfoSubString(g2, text, argStart, argClose, targetOffset, font,
                            fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
                    if (ci != null) {
                        return new int[] { argStartX + ci[0], ci[1], ci[2], ci[3] };
                    }
                } else {
                    return new int[] { argStartX, 0, fm.getAscent(), fm.getDescent() };
                }
            }

            if (targetOffset == lg.parenClose + 1) {
                int rawArgW = (argClose > argStart) ? MathRenderEngine.renderSubString(g2, text, argStart, argClose, 0, 0, font, false,
                        fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel, -1, -1) : 0;
                return new int[] { argStartX + rawArgW + parenW, 0, fm.getAscent(), fm.getDescent() };
            }
        }

        return null;
    }

    public static boolean isForbiddenCaret(int c, LogRegion[] logs) {
        if (logs == null) return false;
        for (LogRegion r : logs) {
            if (r == null) continue;
            // Forbidden inside the word "log" and on '['
            if (r.bracketOpen != -1) {
                if (c > r.logStart && c <= r.bracketOpen) {
                    return true;
                }
                if (c == r.parenOpen) {
                    return true; // Between ']' and '('
                }
            } else if (r.parenOpen != -1) {
                if (c > r.logStart && c <= r.parenOpen) {
                    return true;
                }
            } else {
                if (c > r.logStart && c < r.logEnd) {
                    return true;
                }
            }
        }
        return false;
    }
}
