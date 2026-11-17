package com.spectragraph.superscript;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles detection, measurement, caret positioning, and rendering for subscripts: X_{sub} or X_(sub) or X_0.
 */
public class SubscriptsSuperscript {

    public static class SubscriptRegion {
        public int subChar = -1;      // index of '_'
        public int openParen = -1;    // index of '(' or '{' or '['
        public int closeParen = -1;   // index of ')' or '}' or ']' or end of token
        public int nestingLevel = 1;
        public boolean isOperatorSub = false; // true for ⊗_c, ⊗_k, *_c, *_k (cursor forbidden inside)
        public int cachedWidth = -1;

        public SubscriptRegion() {}

        public SubscriptRegion(SubscriptRegion other) {
            if (other != null) {
                this.subChar = other.subChar;
                this.openParen = other.openParen;
                this.closeParen = other.closeParen;
                this.nestingLevel = other.nestingLevel;
                this.isOperatorSub = other.isOperatorSub;
            }
        }
    }

    /**
     * Returns true only for characters that can mathematically receive a subscript.
     * Valid: Latin letters (a-z, A-Z), Greek variable letters (α-ω, Γ, Θ, Λ, etc.),
     *        Hebrew math cardinals (ℵ, ℶ), Cyrillic letters, Hebrew alphabet letters.
     * Invalid: numbers, operators, parentheses, brackets, set symbols (ℝℂℤℕℚ),
     *          Greek math operators (Σ = summation, Δ = delta), math symbols (∑∫∇∞).
     */
    public static boolean isValidSubscriptBase(char c) {
        // Latin letters a-z, A-Z
        if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) return true;

        // Greek lowercase (α-ω, U+03B1-U+03C9) — used as variables
        if (c >= '\u03B1' && c <= '\u03C9') return true;

        // Greek uppercase EXCEPT Σ (U+03A3, summation) and Δ (U+0394, delta operator)
        if (c >= '\u0391' && c <= '\u03A9' && c != '\u03A3' && c != '\u0394') return true;

        // Hebrew math cardinals: ℵ (U+2135), ℶ (U+2136)
        if (c == '\u2135' || c == '\u2136') return true;

        // Cyrillic: А-я (U+0410-U+044F), Ё (U+0401), ё (U+0451)
        if ((c >= '\u0410' && c <= '\u044F') || c == '\u0401' || c == '\u0451') return true;

        // Hebrew alphabet: א-ת (U+05D0-U+05EA)
        if (c >= '\u05D0' && c <= '\u05EA') return true;

        // Everything else: numbers, operators, parentheses, set symbols (ℝℂℤℕℚ),
        // math operators (∑∫∇∞), fraction lines, etc. → NOT valid
        return false;
    }

    public static int consumeToken(String text, int end) {
        if (text == null || end >= text.length()) return end;
        while (end < text.length()) {
            char c = text.charAt(end);
            if (Character.isLetterOrDigit(c) || c == '.') {
                end++;
            } else {
                break;
            }
        }
        return end;
    }

    public static SubscriptRegion[] findRegions(String text,
                                                IntegralsSuperscript.IntegralRegion[] integrals,
                                                SummatoriesSuperScript.SumRegion[] summations) {
        return findRegions(text, integrals, summations, null);
    }

    public static SubscriptRegion[] findRegions(String text,
                                                IntegralsSuperscript.IntegralRegion[] integrals,
                                                SummatoriesSuperScript.SumRegion[] summations,
                                                ExponentsSuperscript.PowerRegion[] powers) {
        if (text == null || text.indexOf('_') == -1) {
            return new SubscriptRegion[0];
        }
        if (powers == null && text.indexOf('^') != -1) {
            powers = ExponentsSuperscript.findRegions(text);
        }

        // Mark indices of '_' used by integrals or summations
        boolean[] used = new boolean[text.length()];
        if (integrals != null) {
            for (IntegralsSuperscript.IntegralRegion it : integrals) {
                if (it == null) continue;
                if (it.signs != null) {
                    for (IntegralsSuperscript.IntegralSign sign : it.signs) {
                        if (sign == null) continue;
                        if (sign.lowerOpen != -1 && sign.lowerOpen > 0 && text.charAt(sign.lowerOpen - 1) == '_') {
                            used[sign.lowerOpen - 1] = true;
                        }
                    }
                }
                if (it.lowerOpen != -1 && it.lowerOpen > 0 && text.charAt(it.lowerOpen - 1) == '_') {
                    used[it.lowerOpen - 1] = true;
                }
            }
        }
        if (summations != null) {
            for (SummatoriesSuperScript.SumRegion sm : summations) {
                if (sm == null) continue;
                if (sm.lowerOpen != -1 && sm.lowerOpen > 0 && text.charAt(sm.lowerOpen - 1) == '_') {
                    used[sm.lowerOpen - 1] = true;
                }
            }
        }

        List<SubscriptRegion> list = new ArrayList<>();
        int len = text.length();
        int i = 0;
        while ((i = text.indexOf('_', i)) != -1) {
            if (used[i]) {
                i++;
                continue;
            }
            if (i == 0) {
                i++;
                continue;
            }
            if ((i >= 3 && text.substring(0, i).toLowerCase().endsWith("log"))
                    || (i >= 2 && text.substring(0, i).toLowerCase().endsWith("ln"))) {
                i++;
                continue;
            }
            char prevChar = text.charAt(i - 1);
            boolean isOpSub = (prevChar == '\u2297' || prevChar == '\u229B' || prevChar == '*') && i + 1 < len
                    && (text.charAt(i + 1) == 'c' || text.charAt(i + 1) == 'k');
            boolean isValidBase = isValidSubscriptBase(prevChar);
            if (!isValidBase && !isOpSub && powers != null) {
                for (ExponentsSuperscript.PowerRegion p : powers) {
                    if (p != null) {
                        int pClose = (p.closeParen != -1 && p.closeParen < len) ? p.closeParen : len;
                        int pEnd = (p.openParen != -1) ? pClose + 1 : pClose;
                        if (pEnd == i && p.caretChar > 0) {
                            char powerBase = text.charAt(p.caretChar - 1);
                            if (isValidSubscriptBase(powerBase)) {
                                isValidBase = true;
                                break;
                            }
                        }
                    }
                }
            }
            if (!isValidBase && !isOpSub) {
                i++;
                continue;
            }

            SubscriptRegion s = new SubscriptRegion();
            s.subChar = i;

            if (i + 1 < len && (text.charAt(i + 1) == '(' || text.charAt(i + 1) == '[' || text.charAt(i + 1) == '{')) {
                char openChar = text.charAt(i + 1);
                char closeChar = (openChar == '(') ? ')' : (openChar == '[') ? ']' : '}';
                s.openParen = i + 1;
                int match = MathRenderEngine.findMatchingChar(text, i + 1, openChar, closeChar);
                s.closeParen = (match != -1) ? match : len;
            } else {
                int end = consumeToken(text, i + 1);
                s.closeParen = (end > i + 1) ? end : i + 1;
            }

            if (s.closeParen == -1) {
                s.closeParen = len;
            }

            s.isOperatorSub = isOpSub;

            list.add(s);
            i++;
        }

        return list.toArray(new SubscriptRegion[0]);
    }

    public static SubscriptRegion getAt(SubscriptRegion[] subscripts, int i) {
        if (subscripts == null) return null;
        for (SubscriptRegion s : subscripts) {
            if (s != null && s.subChar == i) {
                return s;
            }
        }
        return null;
    }

    public static Font getSubscriptFont(Font font, SubscriptRegion s) {
        return getSubscriptFont(font, s, null);
    }

    public static Font getSubscriptFont(Font font, SubscriptRegion s, String text) {
        if (font == null) return null;
        if (s != null && text != null) {
            int subStart = (s.openParen != -1) ? s.openParen + 1 : s.subChar + 1;
            int close = (s.closeParen != -1 && s.closeParen <= text.length()) ? s.closeParen : text.length();
            if (subStart < close && text.substring(subStart, close).contains("/")) {
                float newSize = Math.max(font.getSize2D() * 0.62f, 9f);
                return font.deriveFont(newSize);
            }
        }
        float newSize = Math.max(font.getSize2D() * 0.72f, 9f);
        return font.deriveFont(newSize);
    }

    public static int getSubscriptDrop(Graphics2D g2, Font font) {
        if (g2 == null || font == null) return 4;
        FontMetrics fm = g2.getFontMetrics(font);
        return Math.max(2, Math.round(fm.getAscent() * 0.35f));
    }

    public static int calculateSubscriptDrop(Graphics2D g2, Font font, Font nextFont, int subAscent) {
        int baseDrop = getSubscriptDrop(g2, font);
        if (g2 == null || nextFont == null) return baseDrop;
        FontMetrics nextFm = g2.getFontMetrics(nextFont);
        if (subAscent > nextFm.getAscent()) {
            return baseDrop + (subAscent - nextFm.getAscent());
        }
        return baseDrop;
    }

    public static boolean needsAutoParens(String text, int start, int end) {
        if (text == null || start >= end) return false;
        if (RootsSuperscript.alreadyHasOuterParens(text, start, end)) return false;
        if (RootsSuperscript.isSingleFunctionCall(text, start, end)) return false;

        String sub = text.substring(start, end);
        // If it contains a fraction or tall radical -> vertically > 1 character
        if (sub.contains("/") || sub.contains("√") || sub.contains("\u221A")) {
            return true;
        }

        // Horizontally > 3 characters (columns)
        if (RootsSuperscript.getHorizontalColumns(text, start, end) > 3) {
            return true;
        }

        return false;
    }

    public static int render(Graphics2D g2, String text, SubscriptRegion s, int curX, int baselineY,
                             Font font, boolean draw,
                             FractionsSuperscript.FractionRegion[] fractions,
                             RootsSuperscript.RootRegion[] roots,
                             ExponentsSuperscript.PowerRegion[] powers,
                             IntegralsSuperscript.IntegralRegion[] integrals,
                             SummatoriesSuperScript.SumRegion[] summations,
                             LogsSuperScript.LogRegion[] logs,
                             DerivativesSuperScript.DerivativeRegion[] derivatives,
                             FloorAndCeilingSuperScript.FloorCeilRegion[] floorCeils,
                             AbsoluteValueSuperScript.AbsoluteValueRegion[] absoluteValues,
                             ParensSuperscript.ParenRegion[] parens,
                             int nestLevel, int selStart, int selEnd, int end) {
        int close = (s.closeParen != -1 && s.closeParen < end) ? s.closeParen : end;
        int subStart = (s.openParen != -1) ? s.openParen + 1 : s.subChar + 1;
        Font nextFont = getSubscriptFont(font, s, text);
        FontMetrics nextFm = g2.getFontMetrics(nextFont);

        int subAscent = MathRenderEngine.getRegionAscent(g2, text, subStart, close, nextFont,
                fractions, roots, powers, integrals, summations, logs, derivatives,
                floorCeils, absoluteValues, parens, nestLevel + 1);
        int subDescent = MathRenderEngine.getRegionDescent(g2, text, subStart, close, nextFont,
                fractions, roots, powers, integrals, summations, logs, derivatives,
                floorCeils, absoluteValues, parens, nestLevel + 1);
        int drop = calculateSubscriptDrop(g2, font, nextFont, subAscent);

        boolean needsParens = needsAutoParens(text, subStart, close);
        int parenW = needsParens ? Math.max(4, Math.min(8, nextFm.charWidth('('))) : 0;
        int parenGap = needsParens ? 1 : 0;
        int subContentX = curX + (needsParens ? (parenW + parenGap) : 0);

        curX += 1; // consistent slight gap
        int rawW = MathRenderEngine.renderSubString(g2, text, subStart, close, subContentX + 1,
                baselineY + drop, nextFont, draw,
                fractions, roots, powers, integrals, summations, logs, derivatives,
                floorCeils, absoluteValues, parens, nestLevel + 1, selStart, selEnd);

        if (draw && needsParens) {
            int pTop = (baselineY + drop) - subAscent - 1;
            int pBottom = (baselineY + drop) + subDescent + 1;
            ParensSuperscript.drawStretchyParen(g2, curX, pTop, pBottom, parenW, true);
            ParensSuperscript.drawStretchyParen(g2, curX + parenW + parenGap + rawW + parenGap, pTop, pBottom, parenW, false);
        }

        int totalW = rawW + (needsParens ? (2 * parenW + 2 * parenGap) : 0);
        if (s != null) s.cachedWidth = totalW;
        return curX + totalW;
    }

    public static int[] getCaretInfo(Graphics2D g2, String text, SubscriptRegion s, int curX,
                                     int targetOffset, Font font,
                                     FractionsSuperscript.FractionRegion[] fractions,
                                     RootsSuperscript.RootRegion[] roots,
                                     ExponentsSuperscript.PowerRegion[] powers,
                                     IntegralsSuperscript.IntegralRegion[] integrals,
                                     SummatoriesSuperScript.SumRegion[] summations,
                                     LogsSuperScript.LogRegion[] logs,
                                     DerivativesSuperScript.DerivativeRegion[] derivatives,
                                     FloorAndCeilingSuperScript.FloorCeilRegion[] floorCeils,
                                     AbsoluteValueSuperScript.AbsoluteValueRegion[] absoluteValues,
                                     ParensSuperscript.ParenRegion[] parens,
                                     int nestLevel, int end) {
        if (s == null) return null;
        int close = (s.closeParen != -1 && s.closeParen < end) ? s.closeParen : end;
        if (targetOffset < s.subChar || targetOffset > close) {
            return null;
        }
        FontMetrics fm = g2.getFontMetrics(font);
        int subStart = (s.openParen != -1) ? s.openParen + 1 : s.subChar + 1;
        Font nextFont = getSubscriptFont(font, s, text);
        FontMetrics nextFm = g2.getFontMetrics(nextFont);

        int subAscent = MathRenderEngine.getRegionAscent(g2, text, subStart, close, nextFont,
                fractions, roots, powers, integrals, summations, logs, derivatives,
                floorCeils, absoluteValues, parens, nestLevel + 1);
        int drop = calculateSubscriptDrop(g2, font, nextFont, subAscent);

        boolean needsParens = needsAutoParens(text, subStart, close);
        int parenW = needsParens ? Math.max(4, Math.min(8, nextFm.charWidth('('))) : 0;
        int parenGap = needsParens ? 1 : 0;

        curX += 1;
        int subContentX = curX + (needsParens ? (parenW + parenGap) : 0);

        if (s.isOperatorSub) {
            int endSub = (s.openParen != -1) ? s.closeParen + 1 : s.closeParen;
            if (targetOffset >= s.subChar && targetOffset <= endSub) {
                int rawW = MathRenderEngine.renderSubString(g2, text, subStart, close, subContentX + 1,
                        0, nextFont, false,
                        fractions, roots, powers, integrals, summations, logs, derivatives,
                        floorCeils, absoluteValues, parens, nestLevel + 1, -1, -1);
                int totalW = rawW + (needsParens ? (2 * parenW + 2 * parenGap) : 0);
                return new int[] { curX + totalW + 2, 0, fm.getAscent(), fm.getDescent() };
            }
            return null;
        }

        if (targetOffset == s.subChar) {
            return new int[] { curX, 0, fm.getAscent(), fm.getDescent() };
        }
        if (targetOffset == s.openParen) {
            return new int[] { curX, drop, nextFm.getAscent(), nextFm.getDescent() };
        }
        if (targetOffset >= subStart && targetOffset <= close) {
            int[] sub = MathRenderEngine.getCaretInfoSubString(g2, text, subStart, close,
                    targetOffset, nextFont, fractions, roots, powers, integrals, summations, logs,
                    derivatives, floorCeils, absoluteValues, parens, nestLevel + 1);
            if (sub != null) {
                return new int[] { subContentX + 1 + sub[0], drop + sub[1], sub[2], sub[3] };
            }
        }
        return null;
    }

    public static int getDescent(Graphics2D g2, String text, SubscriptRegion s, Font font,
                                 FractionsSuperscript.FractionRegion[] fractions,
                                 RootsSuperscript.RootRegion[] roots,
                                 ExponentsSuperscript.PowerRegion[] powers,
                                 IntegralsSuperscript.IntegralRegion[] integrals,
                                 SummatoriesSuperScript.SumRegion[] summations,
                                 LogsSuperScript.LogRegion[] logs,
                                 DerivativesSuperScript.DerivativeRegion[] derivatives,
                                 FloorAndCeilingSuperScript.FloorCeilRegion[] floorCeils,
                                 AbsoluteValueSuperScript.AbsoluteValueRegion[] absoluteValues,
                                 ParensSuperscript.ParenRegion[] parens,
                                 int nestLevel) {
        Font nextFont = getSubscriptFont(font, s, text);
        int subStart = (s.openParen != -1) ? s.openParen + 1 : s.subChar + 1;
        int close = (s.closeParen != -1) ? s.closeParen : text.length();

        int contentAscent = MathRenderEngine.getRegionAscent(g2, text, subStart, close, nextFont,
                fractions, roots, powers, integrals, summations, logs, derivatives,
                floorCeils, absoluteValues, parens, nestLevel + 1);
        int contentDescent = MathRenderEngine.getRegionDescent(g2, text, subStart, close, nextFont,
                fractions, roots, powers, integrals, summations, logs, derivatives,
                floorCeils, absoluteValues, parens, nestLevel + 1);
        int drop = calculateSubscriptDrop(g2, font, nextFont, contentAscent);
        boolean needsParens = needsAutoParens(text, subStart, close);
        return contentDescent + drop + (needsParens ? 2 : 0);
    }

    public static int getAscent(Graphics2D g2, String text, SubscriptRegion s, Font font,
                                FractionsSuperscript.FractionRegion[] fractions,
                                RootsSuperscript.RootRegion[] roots,
                                ExponentsSuperscript.PowerRegion[] powers,
                                IntegralsSuperscript.IntegralRegion[] integrals,
                                SummatoriesSuperScript.SumRegion[] summations,
                                LogsSuperScript.LogRegion[] logs,
                                DerivativesSuperScript.DerivativeRegion[] derivatives,
                                FloorAndCeilingSuperScript.FloorCeilRegion[] floorCeils,
                                AbsoluteValueSuperScript.AbsoluteValueRegion[] absoluteValues,
                                ParensSuperscript.ParenRegion[] parens,
                                int nestLevel) {
        Font nextFont = getSubscriptFont(font, s, text);
        int subStart = (s.openParen != -1) ? s.openParen + 1 : s.subChar + 1;
        int close = (s.closeParen != -1) ? s.closeParen : text.length();

        int contentAscent = MathRenderEngine.getRegionAscent(g2, text, subStart, close, nextFont,
                fractions, roots, powers, integrals, summations, logs, derivatives,
                floorCeils, absoluteValues, parens, nestLevel + 1);
        int drop = calculateSubscriptDrop(g2, font, nextFont, contentAscent);
        return Math.max(0, contentAscent - drop);
    }

    public static boolean isForbiddenCaret(int c, SubscriptRegion[] subscripts) {
        return isForbiddenCaret(c, subscripts, null);
    }

    public static boolean isForbiddenCaret(int c, SubscriptRegion[] subscripts, String text) {
        if (subscripts == null) return false;
        for (SubscriptRegion s : subscripts) {
            if (s == null) continue;
            if (s.openParen != -1 && c == s.openParen) {
                return true; // between _ and (
            }
            // For operator subscripts (⊗_c, ⊗_k, *_c, *_k), forbid caret inside the subscript
            if (s.isOperatorSub) {
                int endSub = (s.openParen != -1) ? s.closeParen : s.closeParen - 1;
                if (c >= s.subChar && c <= endSub) {
                    return true;
                }
            }
        }
        return false;
    }
}

