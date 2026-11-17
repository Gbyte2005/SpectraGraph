package com.spectragraph.superscript;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles detection, measurement, caret positioning, and rendering for
 * Leibniz-notation derivatives: d/dx(f(x)), d/dt(f(t)), d/dy(f(y)), d/dz(f(z)).
 */
public class DerivativesSuperScript {

    public static class DerivativeRegion {
        public int derivStart = -1;  // index of first 'd'
        public int slashChar = -1;   // index of '/'
        public int denDChar = -1;    // index of second 'd' in denominator
        public int denClose = -1;    // index of ')' closing denominator in (d)/(dx)
        public int varChar = -1;     // index of variable ('x', 'y', 'z', 't')
        public String variable = "";
        public int parenOpen = -1;   // index of '(' for argument f(x)
        public int parenClose = -1;  // index of ')' for argument f(x)
        public int cachedWidth = -1;
        public int cachedArgAscent = -1;
        public int cachedArgDescent = -1;

        public DerivativeRegion() {}

        public DerivativeRegion(DerivativeRegion other) {
            if (other != null) {
                this.derivStart = other.derivStart;
                this.slashChar = other.slashChar;
                this.denDChar = other.denDChar;
                this.denClose = other.denClose;
                this.varChar = other.varChar;
                this.variable = other.variable;
                this.parenOpen = other.parenOpen;
                this.parenClose = other.parenClose;
                this.cachedWidth = other.cachedWidth;
                this.cachedArgAscent = other.cachedArgAscent;
                this.cachedArgDescent = other.cachedArgDescent;
            }
        }

        public int getEnd(int textLength) {
            if (parenClose != -1 && parenClose < textLength) {
                return parenClose + 1;
            }
            if (parenOpen != -1 && parenOpen < textLength) {
                return parenOpen + 1;
            }
            if (denClose != -1 && denClose < textLength) {
                return denClose + 1;
            }
            if (varChar != -1 && varChar < textLength) {
                return varChar + 1;
            }
            if (denDChar != -1 && denDChar < textLength) {
                return denDChar + 1;
            }
            if (slashChar != -1 && slashChar < textLength) {
                return slashChar + 1;
            }
            return Math.min(textLength, derivStart + 1);
        }
    }

    public static DerivativeRegion[] findRegions(String text) {
        if (text == null || text.isEmpty()) return new DerivativeRegion[0];
        List<DerivativeRegion> regions = new ArrayList<>();
        int len = text.length();

        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);

            // Pattern 1: (d)/(dx)(...) or (∂)/(∂t)(...)
            if (c == '(' && i + 6 < len) {
                int slash = text.indexOf('/', i);
                if (slash > i && slash + 3 < len && text.charAt(slash - 1) == ')' && text.charAt(slash + 1) == '(') {
                    String num = text.substring(i + 1, slash - 1).trim();
                    int denEnd = text.indexOf(')', slash + 2);
                    if (denEnd != -1) {
                        String den = text.substring(slash + 2, denEnd).trim();
                        boolean numOk = num.equals("d") || num.equals("\u2202");
                        boolean denOk = (den.startsWith("d") || den.startsWith("\u2202")) && (den.length() == 1 || den.length() == 2);
                        if (numOk && denOk) {
                            DerivativeRegion dr = new DerivativeRegion();
                            dr.derivStart = i; // covers from outer '('
                            dr.slashChar = slash;
                            dr.denDChar = slash + 2;
                            dr.denClose = denEnd;
                            dr.varChar = (den.length() == 2) ? (denEnd - 1) : -1;
                            dr.variable = (den.length() == 2) ? den.substring(1).toLowerCase() : "";

                            int next = denEnd + 1;
                            while (next < len && text.charAt(next) == ' ') next++;
                            if (next < len && text.charAt(next) == '(') {
                                dr.parenOpen = next;
                                int depth = 1;
                                for (int j = next + 1; j < len; j++) {
                                    if (text.charAt(j) == '(') depth++;
                                    else if (text.charAt(j) == ')') {
                                        depth--;
                                        if (depth == 0) {
                                            dr.parenClose = j;
                                            break;
                                        }
                                    }
                                }
                            }
                            regions.add(dr);
                            i = (dr.parenOpen != -1) ? dr.parenOpen : (dr.getEnd(len) - 1);
                            continue;
                        }
                    }
                }
            }

            // Pattern 2: d/dx(...) or ∂/∂x(...) or d/d(...)
            if ((c == 'd' || c == '\u2202') && i + 2 < len) {
                if (text.charAt(i + 1) == '/') {
                    char denD = text.charAt(i + 2);
                    if ((denD == 'd' || denD == '\u2202')) {
                        boolean hasVar = (i + 3 < len && Character.isLetter(text.charAt(i + 3)));
                        int next = hasVar ? i + 4 : i + 3;
                        if (hasVar && next < len && text.charAt(next) == '^') {
                            continue;
                        }
                        boolean hasParen = false;
                        int check = next;
                        while (check < len && text.charAt(check) == ' ') check++;
                        if (check < len && text.charAt(check) == '(') {
                            hasParen = true;
                        }
                        if (hasVar || hasParen) {
                            DerivativeRegion dr = new DerivativeRegion();
                            dr.derivStart = i;
                            dr.slashChar = i + 1;
                            dr.denDChar = i + 2;
                            dr.varChar = hasVar ? i + 3 : -1;
                            dr.variable = hasVar ? String.valueOf(Character.toLowerCase(text.charAt(i + 3))) : "";

                            // Look for argument in parens after the variable
                            if (check < len && text.charAt(check) == '(') {
                                dr.parenOpen = check;
                                int depth = 1;
                                for (int j = check + 1; j < len; j++) {
                                    if (text.charAt(j) == '(') depth++;
                                    else if (text.charAt(j) == ')') {
                                        depth--;
                                        if (depth == 0) {
                                            dr.parenClose = j;
                                            break;
                                        }
                                    }
                                }
                            }

                            regions.add(dr);
                            i = (dr.parenOpen != -1) ? dr.parenOpen : (dr.getEnd(len) - 1);
                            continue;
                        }
                    }
                }
            }
        }

        return regions.toArray(new DerivativeRegion[0]);
    }


    public static DerivativeRegion getAt(DerivativeRegion[] derivatives, int i) {
        if (derivatives == null) return null;
        for (DerivativeRegion r : derivatives) {
            if (r != null && r.derivStart == i) {
                return r;
            }
        }
        return null;
    }

    public static int getAscent(Graphics2D g2, String text, DerivativeRegion dr, Font font,
                                FractionsSuperscript.FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
                                ExponentsSuperscript.PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
                                SummatoriesSuperScript.SumRegion[] summations, LogsSuperScript.LogRegion[] logs,
                                ParensSuperscript.ParenRegion[] parens, int nestLevel, int end) {
        return getAscent(g2, text, dr, font, fractions, roots, powers, integrals, summations, logs, null, null, null, parens, nestLevel, end);
    }

    public static int getAscent(Graphics2D g2, String text, DerivativeRegion dr, Font font,
                                FractionsSuperscript.FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
                                ExponentsSuperscript.PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
                                SummatoriesSuperScript.SumRegion[] summations, LogsSuperScript.LogRegion[] logs,
                                DerivativeRegion[] derivatives,
                                FloorAndCeilingSuperScript.FloorCeilRegion[] floorCeils,
                                AbsoluteValueSuperScript.AbsoluteValueRegion[] absoluteValues,
                                ParensSuperscript.ParenRegion[] parens, int nestLevel, int end) {
        FontMetrics fm = g2.getFontMetrics(font);
        int lineYOffset = -fm.getAscent() / 2;
        int numBaseOffset = lineYOffset - 3 - fm.getDescent();
        int opAscent = -(numBaseOffset - fm.getAscent());

        int argAscent = opAscent;
        if (dr.parenOpen != -1 && (dr.varChar != -1 || (dr.parenClose != -1 && dr.parenClose > dr.parenOpen + 1))) {
            int argStart = dr.parenOpen + 1;
            int argClose = (dr.parenClose != -1 && dr.parenClose < end) ? dr.parenClose : end;
            if (argClose > argStart) {
                argAscent = MathRenderEngine.getRegionAscent(g2, text, argStart, argClose, font,
                        fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
            }
        }

        return Math.max(opAscent, argAscent);
    }

    public static int getDescent(Graphics2D g2, String text, DerivativeRegion dr, Font font,
                                 FractionsSuperscript.FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
                                 ExponentsSuperscript.PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
                                 SummatoriesSuperScript.SumRegion[] summations, LogsSuperScript.LogRegion[] logs,
                                 ParensSuperscript.ParenRegion[] parens, int nestLevel, int end) {
        return getDescent(g2, text, dr, font, fractions, roots, powers, integrals, summations, logs, null, null, null, parens, nestLevel, end);
    }

    public static int getDescent(Graphics2D g2, String text, DerivativeRegion dr, Font font,
                                 FractionsSuperscript.FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
                                 ExponentsSuperscript.PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
                                 SummatoriesSuperScript.SumRegion[] summations, LogsSuperScript.LogRegion[] logs,
                                 DerivativeRegion[] derivatives,
                                 FloorAndCeilingSuperScript.FloorCeilRegion[] floorCeils,
                                 AbsoluteValueSuperScript.AbsoluteValueRegion[] absoluteValues,
                                 ParensSuperscript.ParenRegion[] parens, int nestLevel, int end) {
        FontMetrics fm = g2.getFontMetrics(font);
        int lineYOffset = -fm.getAscent() / 2;
        int denBaseOffset = lineYOffset + 3 + fm.getAscent();
        int opDescent = denBaseOffset + fm.getDescent();

        int argDescent = opDescent;
        if (dr.parenOpen != -1 && (dr.varChar != -1 || (dr.parenClose != -1 && dr.parenClose > dr.parenOpen + 1))) {
            int argStart = dr.parenOpen + 1;
            int argClose = (dr.parenClose != -1 && dr.parenClose < end) ? dr.parenClose : end;
            if (argClose > argStart) {
                argDescent = MathRenderEngine.getRegionDescent(g2, text, argStart, argClose, font,
                        fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
            }
        }

        return Math.max(opDescent, argDescent);
    }

    public static int render(Graphics2D g2, String text, DerivativeRegion dr, int curX, int baselineY,
                             Font font, boolean draw,
                             FractionsSuperscript.FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
                             ExponentsSuperscript.PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
                             SummatoriesSuperScript.SumRegion[] summations, LogsSuperScript.LogRegion[] logs,
                             ParensSuperscript.ParenRegion[] parens, int nestLevel, int selStart, int selEnd, int end) {
        return render(g2, text, dr, curX, baselineY, font, draw, fractions, roots, powers, integrals, summations, logs,
                null, null, null, parens, nestLevel, selStart, selEnd, end);
    }

    public static int render(Graphics2D g2, String text, DerivativeRegion dr, int curX, int baselineY,
                             Font font, boolean draw,
                             FractionsSuperscript.FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
                             ExponentsSuperscript.PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
                             SummatoriesSuperScript.SumRegion[] summations, LogsSuperScript.LogRegion[] logs,
                             DerivativeRegion[] derivatives,
                             FloorAndCeilingSuperScript.FloorCeilRegion[] floorCeils,
                             AbsoluteValueSuperScript.AbsoluteValueRegion[] absoluteValues,
                             ParensSuperscript.ParenRegion[] parens, int nestLevel, int selStart, int selEnd, int end) {
        int startX = curX;
        FontMetrics fm = g2.getFontMetrics(font);

        // Determine if this is a partial derivative based on the original text character
        boolean isPartial = (dr.derivStart >= 0 && dr.derivStart < text.length()
                && (text.charAt(dr.derivStart) == '\u2202'
                    || (dr.derivStart + 1 < text.length() && text.charAt(dr.derivStart + 1) == '\u2202')));
        String dSymbol = isPartial ? "\u2202" : "d";
        String numStr = dSymbol;
        String denStr = dSymbol + dr.variable;

        int numW = fm.stringWidth(numStr);
        int denW = fm.stringWidth(denStr);
        int opW = Math.max(numW, denW) + 4;

        int lineY = baselineY - fm.getAscent() / 2;
        int numBase = lineY - 3 - fm.getDescent();
        int denBase = lineY + 3 + fm.getAscent();

        int numX = curX + (opW - numW) / 2;
        int denX = curX + (opW - denW) / 2;

        if (draw) {
            Color origColor = g2.getColor();

            // Selection highlight for numerator
            if (selStart <= dr.derivStart && selEnd > dr.derivStart) {
                g2.setColor(new Color(130, 160, 210, 100));
                g2.fillRect(numX, numBase - fm.getAscent(), numW, fm.getHeight());
                g2.setColor(origColor);
            }
            g2.setFont(font);
            g2.drawString(numStr, numX, numBase);

            // Fraction line
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawLine(curX, lineY, curX + opW, lineY);

            // Selection highlight for denominator
            if ((dr.denDChar != -1 && selStart <= dr.denDChar && selEnd > dr.denDChar) ||
                (dr.varChar != -1 && selStart <= dr.varChar && selEnd > dr.varChar)) {
                g2.setColor(new Color(130, 160, 210, 100));
                g2.fillRect(denX, denBase - fm.getAscent(), denW, fm.getHeight());
                g2.setColor(origColor);
            }
            g2.drawString(denStr, denX, denBase);
        }

        curX += opW;
        int postGap = Math.max(4, fm.charWidth(' ') / 2);
        curX += postGap;

        int argAscent = 0;
        int argDescent = 0;

        // Argument f(x)
        if (dr.parenOpen != -1 && (dr.varChar != -1 || (dr.parenClose != -1 && dr.parenClose > dr.parenOpen + 1))) {
            int argStart = dr.parenOpen + 1;
            int argClose = (dr.parenClose != -1 && dr.parenClose < end) ? dr.parenClose : end;

            argAscent = MathRenderEngine.getRegionAscent(g2, text, argStart, argClose, font,
                    fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
            argDescent = MathRenderEngine.getRegionDescent(g2, text, argStart, argClose, font,
                    fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
            int totalArgH = argAscent + argDescent;
            boolean stretchyParen = argAscent > (int) (fm.getAscent() * 1.45f)
                    || argDescent > (int) (fm.getDescent() * 1.45f)
                    || (totalArgH > (int) (fm.getHeight() * 1.45f));
            int topY = baselineY - Math.max(fm.getAscent(), argAscent) - 2;
            int bottomY = baselineY + Math.max(fm.getDescent(), argDescent) + 2;
            int h = bottomY - topY;
            int parenW = stretchyParen ? Math.max(fm.charWidth('('), Math.min(13, 6 + h / 20)) : fm.charWidth('(');

            if (draw) {
                if (stretchyParen) {
                    Color origColor = g2.getColor();
                    if (selStart <= dr.parenOpen && selEnd > dr.parenOpen) {
                        g2.setColor(new Color(130, 160, 210, 100));
                        g2.fillRect(curX, topY, parenW, h);
                        g2.setColor(origColor);
                    }
                    ParensSuperscript.drawStretchyParen(g2, curX, topY, bottomY, parenW, true);
                } else {
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

            if (draw && dr.parenClose != -1) {
                if (stretchyParen) {
                    Color origColor = g2.getColor();
                    if (selStart <= dr.parenClose && selEnd > dr.parenClose) {
                        g2.setColor(new Color(130, 160, 210, 100));
                        g2.fillRect(curX, topY, parenW, h);
                        g2.setColor(origColor);
                    }
                    ParensSuperscript.drawStretchyParen(g2, curX, topY, bottomY, parenW, false);
                } else {
                    g2.drawString(")", curX, baselineY);
                }
            }
            if (dr.parenClose != -1) {
                curX += parenW;
            }
        }

        int totalW = curX - startX;
        if (dr != null) {
            dr.cachedWidth = totalW;
            dr.cachedArgAscent = argAscent;
            dr.cachedArgDescent = argDescent;
        }
        return curX;
    }

    public static int[] getCaretInfo(Graphics2D g2, String text, DerivativeRegion dr, int curX, int targetOffset, Font font,
                                    FractionsSuperscript.FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
                                    ExponentsSuperscript.PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
                                    SummatoriesSuperScript.SumRegion[] summations, LogsSuperScript.LogRegion[] logs,
                                    ParensSuperscript.ParenRegion[] parens, int nestLevel, int end) {
        return getCaretInfo(g2, text, dr, curX, targetOffset, font, fractions, roots, powers, integrals, summations, logs,
                null, null, null, parens, nestLevel, end);
    }

    public static int[] getCaretInfo(Graphics2D g2, String text, DerivativeRegion dr, int curX, int targetOffset, Font font,
                                    FractionsSuperscript.FractionRegion[] fractions, RootsSuperscript.RootRegion[] roots,
                                    ExponentsSuperscript.PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
                                    SummatoriesSuperScript.SumRegion[] summations, LogsSuperScript.LogRegion[] logs,
                                    DerivativeRegion[] derivatives,
                                    FloorAndCeilingSuperScript.FloorCeilRegion[] floorCeils,
                                    AbsoluteValueSuperScript.AbsoluteValueRegion[] absoluteValues,
                                    ParensSuperscript.ParenRegion[] parens, int nestLevel, int end) {
        if (dr == null) return null;
        int drEnd = dr.getEnd(end);
        if (targetOffset < dr.derivStart || targetOffset > drEnd) {
            return null;
        }
        FontMetrics fm = g2.getFontMetrics(font);

        boolean isPartial = (dr.derivStart >= 0 && dr.derivStart < text.length()
                && (text.charAt(dr.derivStart) == '\u2202'
                    || (dr.derivStart + 1 < text.length() && text.charAt(dr.derivStart + 1) == '\u2202')));
        String dSymbol = isPartial ? "\u2202" : "d";
        String numStr = dSymbol;
        String denStr = dSymbol + (dr.variable != null ? dr.variable : "");
        int numW = fm.stringWidth(numStr);
        int denW = fm.stringWidth(denStr);
        int opW = Math.max(numW, denW) + 4;

        int lineYOffset = -fm.getAscent() / 2;
        int numBaseOffset = lineYOffset - 3 - fm.getDescent();
        int denBaseOffset = lineYOffset + 3 + fm.getAscent();

        int numX = curX + (opW - numW) / 2;
        int denX = curX + (opW - denW) / 2;

        if (targetOffset == dr.derivStart) {
            return new int[] { curX, 0, fm.getAscent(), fm.getDescent() };
        }
        if (dr.varChar != -1) {
            int dW = fm.stringWidth(dSymbol);
            if (targetOffset == dr.varChar) {
                return new int[] { denX + dW, denBaseOffset, fm.getAscent(), fm.getDescent() };
            }
            if (targetOffset == dr.varChar + 1 || (dr.denClose != -1 && targetOffset == dr.denClose)) {
                return new int[] { denX + denW, denBaseOffset, fm.getAscent(), fm.getDescent() };
            }
        } else if (dr.denDChar != -1) {
            if (targetOffset == dr.denDChar + 1 || (dr.denClose != -1 && targetOffset == dr.denClose)) {
                return new int[] { denX + denW, denBaseOffset, fm.getAscent(), fm.getDescent() };
            }
        }
        int postGap = Math.max(4, fm.charWidth(' ') / 2);
        if (dr.parenOpen == -1 && targetOffset == dr.getEnd(text.length())) {
            return new int[] { curX + opW + postGap, 0, fm.getAscent(), fm.getDescent() };
        }

        // Argument
        if (dr.parenOpen != -1 && (dr.varChar != -1 || (dr.parenClose != -1 && dr.parenClose > dr.parenOpen + 1))) {
            int afterOpX = curX + opW + postGap;
            int argStart = dr.parenOpen + 1;
            int argClose = (dr.parenClose != -1 && dr.parenClose < end) ? dr.parenClose : end;

            int argAscent = (dr.cachedArgAscent != -1) ? dr.cachedArgAscent
                    : MathRenderEngine.getRegionAscent(g2, text, argStart, argClose, font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
            int argDescent = (dr.cachedArgDescent != -1) ? dr.cachedArgDescent
                    : MathRenderEngine.getRegionDescent(g2, text, argStart, argClose, font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
            int totalArgH = argAscent + argDescent;
            boolean stretchyParen = argAscent > (int) (fm.getAscent() * 1.45f)
                    || argDescent > (int) (fm.getDescent() * 1.45f)
                    || (totalArgH > (int) (fm.getHeight() * 1.45f));
            int parenW = stretchyParen ? Math.max(fm.charWidth('('), Math.min(13, 6 + (argAscent + argDescent + 4) / 20)) : fm.charWidth('(');
            int argStartX = afterOpX + parenW;

            if (targetOffset == argStart) {
                return new int[] { argStartX, 0, fm.getAscent(), fm.getDescent() };
            }

            if (targetOffset > argStart && targetOffset <= argClose) {
                int[] ci = MathRenderEngine.getCaretInfoSubString(g2, text, argStart, argClose, targetOffset, font,
                        fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
                if (ci != null) {
                    return new int[] { argStartX + ci[0], ci[1], ci[2], ci[3] };
                }
            }

            if (dr.parenClose != -1 && targetOffset >= dr.parenClose) {
                int rawArgW = MathRenderEngine.renderSubString(g2, text, argStart, argClose, 0, 0, font, false,
                        fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel, -1, -1);
                int afterParenX = argStartX + rawArgW;
                if (targetOffset == dr.parenClose) {
                    return new int[] { afterParenX, 0, fm.getAscent(), fm.getDescent() };
                }
                return new int[] { afterParenX + parenW, 0, fm.getAscent(), fm.getDescent() };
            }
        } else if (dr.varChar == -1 && dr.parenOpen != -1 && targetOffset >= dr.parenOpen) {
            return new int[] { denX + denW, denBaseOffset, fm.getAscent(), fm.getDescent() };
        }

        return null;
    }

    public static boolean isForbiddenCaret(int c, DerivativeRegion[] derivatives) {
        if (derivatives == null) return false;
        for (DerivativeRegion dr : derivatives) {
            if (dr == null) continue;
            // Forbidden behind numerator 'd' and on slash
            if (c > dr.derivStart && c <= dr.slashChar) {
                return true;
            }
            // Forbidden between slash and denominator 'd' (including on denominator 'd')
            if (dr.denDChar != -1 && c >= dr.slashChar && c <= dr.denDChar) {
                return true;
            }
            // Variable caret handling:
            // c == dr.varChar (before variable) and c == dr.varChar + 1 (after variable) are ALLOWED!
            if (dr.parenOpen != -1) {
                if (dr.varChar == -1 && (dr.parenClose == -1 || dr.parenClose == dr.parenOpen + 1)) {
                    if (c >= dr.parenOpen && c <= (dr.parenClose != -1 ? dr.parenClose : dr.parenOpen + 1)) {
                        return true;
                    }
                }
                if (dr.varChar != -1) {
                    if (c > dr.varChar + 1 && c <= dr.parenOpen) {
                        return true;
                    }
                } else if (dr.denDChar != -1) {
                    if (c > dr.denDChar + 1 && c <= dr.parenOpen) {
                        return true;
                    }
                } else if (c == dr.parenOpen) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isInsideDerivativeOperator(int caret, String text) {
        if (text == null || caret < 0) return false;
        DerivativeRegion[] derivatives = findRegions(text);
        if (derivatives != null) {
            for (DerivativeRegion dr : derivatives) {
                if (dr == null) continue;
                int opEnd = (dr.denClose != -1) ? dr.denClose + 1 : ((dr.parenOpen != -1) ? dr.parenOpen : dr.getEnd(text.length()));
                if (dr.parenOpen == -1 && dr.varChar != -1) {
                    int p = dr.varChar + 1;
                    if (p < text.length() && text.charAt(p) == ')') p++;
                    opEnd = Math.max(opEnd, p);
                }
                if (caret >= dr.derivStart && caret <= opEnd) {
                    return true;
                }
            }
        }
        FractionsSuperscript.FractionRegion[] fracs = FractionsSuperscript.findRegions(text);
        if (fracs != null) {
            for (FractionsSuperscript.FractionRegion fr : fracs) {
                if (fr == null) continue;
                if (fr.denOpen != -1 && fr.denClose != -1 && fr.denClose < text.length()) {
                    String den = text.substring(fr.denOpen + 1, fr.denClose).trim();
                    String num = (fr.numOpen != -1 && fr.numClose != -1 && fr.numClose < text.length())
                            ? text.substring(fr.numOpen + 1, fr.numClose).trim() : "";
                    if ((num.equals("d") || num.equals("\u2202")) && (den.startsWith("d") || den.startsWith("\u2202"))) {
                        if (caret >= fr.numOpen && caret <= fr.denClose + 1) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}
