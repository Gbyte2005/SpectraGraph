package com.spectragraph.superscript;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import javax.swing.JTextField;
import java.util.HashMap;
import java.util.Map;

/**
 * Orchestrates text measurement, layout, and rendering by delegating non-linear mathematical
 * structures to their dedicated superscript renderer classes.
 */
public class MathRenderEngine {

    @SuppressWarnings("unchecked")
    private static final Map<?, ?> CACHED_DESKTOP_HINTS;
    static {
        Object hintsObj = Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints");
        CACHED_DESKTOP_HINTS = (hintsObj instanceof Map<?, ?>) ? (Map<?, ?>) hintsObj : null;
    }

    /**
     * Scratch surface used by hit-testing when no live Graphics is available.
     * Reused so that dragging never allocates a BufferedImage per mouse event.
     */
    private static final BufferedImage SCRATCH_IMG = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    private static final Graphics2D SCRATCH_G2;
    static {
        SCRATCH_G2 = SCRATCH_IMG.createGraphics();
        SCRATCH_G2.setComposite(AlphaComposite.Src);
        applyRenderingHints(SCRATCH_G2);
    }

    /**
     * Returns a shared Graphics2D configured with the math rendering hints.
     * Intended for measurement / hit-testing only: callers must not dispose it
     * and must not assume any previous state (it is reset on each call).
     */
    public static Graphics2D getScratchGraphics(Font font) {
        if (font != null) {
            SCRATCH_G2.setFont(font);
        }
        return SCRATCH_G2;
    }

    public static void disposeScratchGraphics() {
        // Intentionally empty: the scratch graphics is process-wide and reused.
    }

    private static final ThreadLocal<RegionContext> CURRENT_REGIONS = new ThreadLocal<>();

    /**
     * Graphics used by the measurement helpers when the caller passes {@code null}.
     * It is the same shared scratch surface returned by {@link #getScratchGraphics}.
     */
    public static final Graphics2D FALLBACK_G2 = SCRATCH_G2;

    private static final ThreadLocal<Map<Long, Integer>> METRIC_CACHE = ThreadLocal.withInitial(HashMap::new);

    public static class RegionContext {
        public String text;
        public FractionsSuperscript.FractionRegion[] fractions;
        public RootsSuperscript.RootRegion[] roots;
        public ExponentsSuperscript.PowerRegion[] powers;
        public IntegralsSuperscript.IntegralRegion[] integrals;
        public SummatoriesSuperScript.SumRegion[] summations;
        public SubscriptsSuperscript.SubscriptRegion[] subscripts;
        public LogsSuperScript.LogRegion[] logs;
        public DerivativesSuperScript.DerivativeRegion[] derivatives;
        public FloorAndCeilingSuperScript.FloorCeilRegion[] floorCeils;
        public AbsoluteValueSuperScript.AbsoluteValueRegion[] absoluteValues;
        public ParensSuperscript.ParenRegion[] parens;
    }

    public static Map<Long, Integer> getMetricCache() {
        return METRIC_CACHE.get();
    }

    public static void clearCache() {
        METRIC_CACHE.get().clear();
        CURRENT_REGIONS.remove();
    }

    public static long makeMetricKey(int type, int start, int end, float fontSize) {
        long fs = (long) (fontSize * 10);
        return ((long) type << 48) | ((long) (start & 0xFFFF) << 32) | ((long) (end & 0xFFFF) << 16) | (fs & 0xFFFFL);
    }

    private static final SummatoriesSuperScript.SumRegion[] EMPTY_SUMS = new SummatoriesSuperScript.SumRegion[0];
    private static final LogsSuperScript.LogRegion[] EMPTY_LOGS = new LogsSuperScript.LogRegion[0];
    private static final DerivativesSuperScript.DerivativeRegion[] EMPTY_DERIVS = new DerivativesSuperScript.DerivativeRegion[0];
    private static final FloorAndCeilingSuperScript.FloorCeilRegion[] EMPTY_FLOORS = new FloorAndCeilingSuperScript.FloorCeilRegion[0];
    private static final AbsoluteValueSuperScript.AbsoluteValueRegion[] EMPTY_ABS = new AbsoluteValueSuperScript.AbsoluteValueRegion[0];
    private static final FractionsSuperscript.FractionRegion[] EMPTY_FRACTIONS = new FractionsSuperscript.FractionRegion[0];
    private static final RootsSuperscript.RootRegion[] EMPTY_ROOTS = new RootsSuperscript.RootRegion[0];
    private static final ExponentsSuperscript.PowerRegion[] EMPTY_POWERS = new ExponentsSuperscript.PowerRegion[0];
    private static final IntegralsSuperscript.IntegralRegion[] EMPTY_INTEGRALS = new IntegralsSuperscript.IntegralRegion[0];
    private static final SubscriptsSuperscript.SubscriptRegion[] EMPTY_SUBSCRIPTS = new SubscriptsSuperscript.SubscriptRegion[0];
    private static final ParensSuperscript.ParenRegion[] EMPTY_PARENS = new ParensSuperscript.ParenRegion[0];

    public static boolean hasSumChar(String text) {
        if (text == null) return false;
        return text.indexOf('∑') != -1 || text.indexOf('\u2211') != -1
                || text.indexOf('Σ') != -1 || text.indexOf('\u03A3') != -1
                || text.toLowerCase().contains("sum");
    }

    private static final Font FALLBACK_SANS = new Font("SansSerif", Font.PLAIN, 14);

    public static Font getFontForChar(Font baseFont, char ch) {
        if (baseFont != null && baseFont.canDisplay(ch)) {
            return baseFont;
        }
        if (FALLBACK_SANS.canDisplay(ch)) {
            return FALLBACK_SANS.deriveFont(baseFont != null ? baseFont.getSize2D() : 14f);
        }
        return baseFont;
    }

    public static int getCharWidth(Graphics2D g2, Font font, FontMetrics fm, char ch) {
        if (ch == '\u2297' || ch == '\u229B') {
            return Math.max(fm.charWidth('0') + 2, (int) (fm.getAscent() * 0.9f));
        }
        if (!font.canDisplay(ch)) {
            Font fb = getFontForChar(font, ch);
            if (fb != null && fb != font) {
                FontMetrics fbm = (g2 != null) ? g2.getFontMetrics(fb) : (FALLBACK_G2 != null ? FALLBACK_G2.getFontMetrics(fb) : null);
                if (fbm != null) return fbm.charWidth(ch);
            }
        }
        return fm.charWidth(ch);
    }

    public static void drawSpecialChar(Graphics2D g2, char ch, int curX, int baselineY, Font font, FontMetrics fm, int charW) {
        if (ch == '\u2297') { // ⊗ circled times
            int size = (int) (fm.getAscent() * 0.78f);
            if (size % 2 != 0) size++;
            int cx = curX + charW / 2;
            int cy = baselineY - fm.getAscent() / 2;
            int r = size / 2;

            java.awt.Stroke origStroke = g2.getStroke();
            Object origAntialias = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            float strokeW = Math.max(1.2f, font.getSize2D() * 0.08f);
            g2.setStroke(new java.awt.BasicStroke(strokeW, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));

            g2.drawOval(cx - r, cy - r, size, size);
            int innerR = (int) (r * 0.65f);
            g2.drawLine(cx - innerR, cy - innerR, cx + innerR, cy + innerR);
            g2.drawLine(cx - innerR, cy + innerR, cx + innerR, cy - innerR);

            g2.setStroke(origStroke);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, origAntialias);
        } else if (ch == '\u229B') { // ⊛ circled asterisk
            int size = (int) (fm.getAscent() * 0.78f);
            if (size % 2 != 0) size++;
            int cx = curX + charW / 2;
            int cy = baselineY - fm.getAscent() / 2;
            int r = size / 2;

            java.awt.Stroke origStroke = g2.getStroke();
            Object origAntialias = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            float strokeW = Math.max(1.2f, font.getSize2D() * 0.08f);
            g2.setStroke(new java.awt.BasicStroke(strokeW, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));

            g2.drawOval(cx - r, cy - r, size, size);
            int innerR = (int) (r * 0.60f);
            g2.drawLine(cx, cy - innerR, cx, cy + innerR);
            g2.drawLine(cx - innerR, cy, cx + innerR, cy);
            int diag = (int) (innerR * 0.707f);
            g2.drawLine(cx - diag, cy - diag, cx + diag, cy + diag);
            g2.drawLine(cx - diag, cy + diag, cx + diag, cy - diag);

            g2.setStroke(origStroke);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, origAntialias);
        }
    }

    public static int findMatchingChar(String text, int openIdx, char open, char close) {
        if (text == null || openIdx < 0 || openIdx >= text.length()) return -1;
        int depth = 1;
        for (int i = openIdx + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == open) depth++;
            else if (c == close) depth--;
            if (depth == 0) return i;
        }
        return -1;
    }

    public static int findMatchingCharBackward(String text, int closeIdx, char close, char open) {
        if (text == null || closeIdx < 0 || closeIdx >= text.length()) return -1;
        int depth = 1;
        for (int i = closeIdx - 1; i >= 0; i--) {
            char c = text.charAt(i);
            if (c == close) depth++;
            else if (c == open) depth--;
            if (depth == 0) return i;
        }
        return -1;
    }

    public static int renderSubString(Graphics2D g2, String text, int start, int end, int x, int baselineY,
            Font font, boolean draw, FractionsSuperscript.FractionRegion[] fractions,
            RootsSuperscript.RootRegion[] roots, ExponentsSuperscript.PowerRegion[] powers,
            IntegralsSuperscript.IntegralRegion[] integrals, ParensSuperscript.ParenRegion[] parens,
            int nestLevel, int selStart, int selEnd) {
        SummatoriesSuperScript.SumRegion[] summations = hasSumChar(text) ? SummatoriesSuperScript.findRegions(text) : EMPTY_SUMS;
        return renderSubString(g2, text, start, end, x, baselineY, font, draw,
                fractions, roots, powers, integrals, summations, parens, nestLevel, selStart, selEnd);
    }

    public static int renderSubString(Graphics2D g2, String text, int start, int end, int x, int baselineY,
            Font font, boolean draw, FractionsSuperscript.FractionRegion[] fractions,
            RootsSuperscript.RootRegion[] roots, ExponentsSuperscript.PowerRegion[] powers,
            IntegralsSuperscript.IntegralRegion[] integrals, SummatoriesSuperScript.SumRegion[] summations,
            ParensSuperscript.ParenRegion[] parens, int nestLevel, int selStart, int selEnd) {
        LogsSuperScript.LogRegion[] logs = (text != null && (text.contains("log") || text.contains("ln"))) ? LogsSuperScript.findRegions(text) : EMPTY_LOGS;
        return renderSubString(g2, text, start, end, x, baselineY, font, draw,
                fractions, roots, powers, integrals, summations, logs, parens, nestLevel, selStart, selEnd);
    }

    public static int renderSubString(Graphics2D g2, String text, int start, int end, int x, int baselineY,
            Font font, boolean draw, FractionsSuperscript.FractionRegion[] fractions,
            RootsSuperscript.RootRegion[] roots, ExponentsSuperscript.PowerRegion[] powers,
            IntegralsSuperscript.IntegralRegion[] integrals, SummatoriesSuperScript.SumRegion[] summations,
            LogsSuperScript.LogRegion[] logs, ParensSuperscript.ParenRegion[] parens, int nestLevel, int selStart, int selEnd) {
        DerivativesSuperScript.DerivativeRegion[] derivatives = (text != null && (text.contains("d") || text.contains("∂"))) ? DerivativesSuperScript.findRegions(text) : EMPTY_DERIVS;
        return renderSubString(g2, text, start, end, x, baselineY, font, draw,
                fractions, roots, powers, integrals, summations, logs, derivatives, parens, nestLevel, selStart, selEnd);
    }

    public static int renderSubString(Graphics2D g2, String text, int start, int end, int x, int baselineY,
            Font font, boolean draw, FractionsSuperscript.FractionRegion[] fractions,
            RootsSuperscript.RootRegion[] roots, ExponentsSuperscript.PowerRegion[] powers,
            IntegralsSuperscript.IntegralRegion[] integrals, SummatoriesSuperScript.SumRegion[] summations,
            LogsSuperScript.LogRegion[] logs, DerivativesSuperScript.DerivativeRegion[] derivatives,
            ParensSuperscript.ParenRegion[] parens, int nestLevel, int selStart, int selEnd) {
        FloorAndCeilingSuperScript.FloorCeilRegion[] floorCeils = (text != null && (text.contains("⌊") || text.contains("⌈") || text.contains("floor") || text.contains("ceil")))
                ? FloorAndCeilingSuperScript.findRegions(text, fractions, roots, powers, integrals, summations, logs, derivatives) : EMPTY_FLOORS;
        AbsoluteValueSuperScript.AbsoluteValueRegion[] absoluteValues = (text != null && text.contains("|"))
                ? AbsoluteValueSuperScript.findRegions(text, fractions, roots, powers, integrals, summations, logs, derivatives) : EMPTY_ABS;
        return renderSubString(g2, text, start, end, x, baselineY, font, draw,
                fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel, selStart, selEnd);
    }

    public static int renderSubString(Graphics2D g2, String text, int start, int end, int x, int baselineY,
            Font font, boolean draw, FractionsSuperscript.FractionRegion[] fractions,
            RootsSuperscript.RootRegion[] roots, ExponentsSuperscript.PowerRegion[] powers,
            IntegralsSuperscript.IntegralRegion[] integrals, SummatoriesSuperScript.SumRegion[] summations,
            LogsSuperScript.LogRegion[] logs, DerivativesSuperScript.DerivativeRegion[] derivatives,
            FloorAndCeilingSuperScript.FloorCeilRegion[] floorCeils,
            AbsoluteValueSuperScript.AbsoluteValueRegion[] absoluteValues,
            ParensSuperscript.ParenRegion[] parens, int nestLevel, int selStart, int selEnd) {
        if (g2 == null) g2 = FALLBACK_G2;
        if (text == null || start >= end) {
            return 0;
        }
        if (!draw && selStart == -1 && selEnd == -1) {
            long key = makeMetricKey(0, start, end, font.getSize2D());
            Map<Long, Integer> cache = METRIC_CACHE.get();
            Integer cached = cache.get(key);
            if (cached != null)
                return cached;
        }
        FontMetrics fm = g2.getFontMetrics(font);
        RegionContext rcSub = CURRENT_REGIONS.get();
        SubscriptsSuperscript.SubscriptRegion[] subscripts = (rcSub != null && rcSub.text.equals(text))
                ? rcSub.subscripts
                : ((text != null && text.indexOf('_') != -1)
                        ? SubscriptsSuperscript.findRegions(text, integrals, summations, powers) : EMPTY_SUBSCRIPTS);
        int curX = x;
        int i = start;
        while (i < end) {
            char c = text.charAt(i);

            // 1. Fractions
            if (c == '(' && fractions != null && fractions.length > 0) {
                FractionsSuperscript.FractionRegion f = FractionsSuperscript.getAt(fractions, i, end);
                if (f != null) {
                    curX = FractionsSuperscript.render(g2, text, f, curX, baselineY, font, draw,
                            fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel, selStart, selEnd, end);
                    i = Math.min(f.denClose, end) + 1;
                    continue;
                }
            }

            // 2. Roots / Radicals
            if ((c == '√' || c == '\u221A') && roots != null && roots.length > 0) {
                RootsSuperscript.RootRegion r = RootsSuperscript.getAt(roots, i);
                if (r != null) {
                    curX = RootsSuperscript.render(g2, text, r, curX, baselineY, font, draw,
                            fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel, selStart, selEnd, end);
                    int close = (r.parenClose != -1 && r.parenClose < end) ? r.parenClose : end;
                    i = (r.parenOpen != -1 && close < end) ? close + 1 : end;
                    continue;
                }
            }

            // 3. Exponents / Powers
            if (c == '^' && powers != null && powers.length > 0) {
                ExponentsSuperscript.PowerRegion p = ExponentsSuperscript.getAt(powers, i);
                if (p != null) {
                    int pClose = (p.closeParen != -1 && p.closeParen < end) ? p.closeParen : end;
                    int pNextI = (p.openParen != -1) ? pClose + 1 : pClose;
                    SubscriptsSuperscript.SubscriptRegion pairedSub = null;
                    if (pNextI < end && text.charAt(pNextI) == '_' && subscripts != null && subscripts.length > 0) {
                        pairedSub = SubscriptsSuperscript.getAt(subscripts, pNextI);
                        if (pairedSub != null) {
                            int subStart = (pairedSub.openParen != -1) ? pairedSub.openParen + 1 : pairedSub.subChar + 1;
                            int subClose = (pairedSub.closeParen != -1 && pairedSub.closeParen < end) ? pairedSub.closeParen : end;
                            if (SubscriptsSuperscript.needsAutoParens(text, subStart, subClose)) {
                                pairedSub = null;
                            }
                        }
                    }
                    if (pairedSub != null) {
                        int startX = curX;
                        int endPowX = ExponentsSuperscript.render(g2, text, p, startX, baselineY, font, draw,
                                fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel, selStart, selEnd, end);
                        int endSubX = SubscriptsSuperscript.render(g2, text, pairedSub, startX, baselineY, font, draw,
                                fractions, roots, powers, integrals, summations, logs, derivatives,
                                floorCeils, absoluteValues, parens, nestLevel, selStart, selEnd, end);
                        curX = startX + Math.max(endPowX - startX, endSubX - startX);
                        int subClose = (pairedSub.closeParen != -1 && pairedSub.closeParen < end) ? pairedSub.closeParen : end;
                        i = (pairedSub.openParen != -1) ? subClose + 1 : subClose;
                        continue;
                    }

                    curX = ExponentsSuperscript.render(g2, text, p, curX, baselineY, font, draw,
                            fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel, selStart, selEnd, end);
                    int close = (p.closeParen != -1 && p.closeParen < end) ? p.closeParen : end;
                    i = (p.openParen != -1) ? close + 1 : close;
                    continue;
                }
            }

            // 3b. Subscripts
            if (c == '_' && subscripts != null && subscripts.length > 0) {
                SubscriptsSuperscript.SubscriptRegion s = SubscriptsSuperscript.getAt(subscripts, i);
                if (s != null) {
                    int sClose = (s.closeParen != -1 && s.closeParen < end) ? s.closeParen : end;
                    int sNextI = (s.openParen != -1) ? sClose + 1 : sClose;
                    ExponentsSuperscript.PowerRegion pairedPow = null;
                    if (sNextI < end && text.charAt(sNextI) == '^' && powers != null && powers.length > 0) {
                        int subStart = (s.openParen != -1) ? s.openParen + 1 : s.subChar + 1;
                        if (!SubscriptsSuperscript.needsAutoParens(text, subStart, sClose)) {
                            pairedPow = ExponentsSuperscript.getAt(powers, sNextI);
                        }
                    }
                    if (pairedPow != null) {
                        int startX = curX;
                        int endSubX = SubscriptsSuperscript.render(g2, text, s, startX, baselineY, font, draw,
                                fractions, roots, powers, integrals, summations, logs, derivatives,
                                floorCeils, absoluteValues, parens, nestLevel, selStart, selEnd, end);
                        int endPowX = ExponentsSuperscript.render(g2, text, pairedPow, startX, baselineY, font, draw,
                                fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel, selStart, selEnd, end);
                        curX = startX + Math.max(endSubX - startX, endPowX - startX);
                        int powClose = (pairedPow.closeParen != -1 && pairedPow.closeParen < end) ? pairedPow.closeParen : end;
                        i = (pairedPow.openParen != -1) ? powClose + 1 : powClose;
                        continue;
                    }

                    curX = SubscriptsSuperscript.render(g2, text, s, curX, baselineY, font, draw,
                            fractions, roots, powers, integrals, summations, logs, derivatives,
                            floorCeils, absoluteValues, parens, nestLevel, selStart, selEnd, end);
                    int close = (s.closeParen != -1 && s.closeParen < end) ? s.closeParen : end;
                    i = (s.openParen != -1) ? close + 1 : close;
                    continue;
                }
            }

            // 4. Integrals
            if ((c == '∫' || c == '\u222B') && integrals != null && integrals.length > 0) {
                IntegralsSuperscript.IntegralRegion it = IntegralsSuperscript.getAt(integrals, i);
                if (it != null) {
                    curX = IntegralsSuperscript.render(g2, text, it, curX, baselineY, font, draw,
                            fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel, selStart, selEnd, end);
                    i = (it.dxEnd != -1 && it.dxEnd <= end) ? it.dxEnd
                            : ((it.parenClose != -1 && it.parenClose < end) ? it.parenClose + 1 : end);
                    continue;
                }
            }

            // 5. Summations
            if ((c == '∑' || c == '\u2211' || c == 'Σ' || c == '\u03A3') && summations != null && summations.length > 0) {
                SummatoriesSuperScript.SumRegion sm = SummatoriesSuperScript.getAt(summations, i);
                if (sm != null) {
                    curX = SummatoriesSuperScript.render(g2, text, sm, curX, baselineY, font, draw,
                            fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel, selStart, selEnd, end);
                    int nextI = SummatoriesSuperScript.getArgumentEnd(sm, end);
                    if (sm.parenClose != -1 && sm.parenClose < end) {
                        nextI = sm.parenClose + 1;
                    }
                    i = nextI;
                    continue;
                }
            }

            // 5b. Logarithms
            if ((c == 'l' || c == 'L') && logs != null && logs.length > 0) {
                LogsSuperScript.LogRegion lg = LogsSuperScript.getAt(logs, i);
                if (lg != null) {
                    curX = LogsSuperScript.render(g2, text, lg, curX, baselineY, font, draw,
                            fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel, selStart, selEnd, end);
                    i = lg.getEnd(end);
                    continue;
                }
            }

            // 5c. Derivatives
            if ((c == 'd' || c == 'D' || c == '∂' || c == '\u2202' || c == '(') && derivatives != null && derivatives.length > 0) {
                DerivativesSuperScript.DerivativeRegion dr = DerivativesSuperScript.getAt(derivatives, i);
                if (dr != null) {
                    curX = DerivativesSuperScript.render(g2, text, dr, curX, baselineY, font, draw,
                            fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel, selStart, selEnd, end);
                    i = dr.getEnd(end);
                    continue;
                }
            }

            // 5d. Floor and Ceiling
            if ((c == '⌊' || c == '⌈') && floorCeils != null && floorCeils.length > 0) {
                FloorAndCeilingSuperScript.FloorCeilRegion fcr = FloorAndCeilingSuperScript.getAt(floorCeils, i, end);
                if (fcr != null) {
                    curX = FloorAndCeilingSuperScript.render(g2, text, fcr, curX, baselineY, font, draw,
                            fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel, selStart, selEnd);
                    i = fcr.closeChar + 1;
                    continue;
                }
            }

            // 5e. Absolute Value
            if (c == '|' && absoluteValues != null && absoluteValues.length > 0) {
                AbsoluteValueSuperScript.AbsoluteValueRegion avr = AbsoluteValueSuperScript.getAt(absoluteValues, i, end);
                if (avr != null) {
                    curX = AbsoluteValueSuperScript.render(g2, text, avr, curX, baselineY, font, draw,
                            fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel, selStart, selEnd);
                    i = avr.closeChar + 1;
                    continue;
                }
            }

            // 6. Parentheses and Brackets
            if ((c == '(' || c == '[') && parens != null && parens.length > 0) {
                ParensSuperscript.ParenRegion pr = ParensSuperscript.getAt(parens, i, end);
                if (pr != null && ParensSuperscript.isStretchy(g2, text, pr, font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel)) {
                    curX = ParensSuperscript.render(g2, text, pr, curX, baselineY, font, draw,
                            fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel, selStart, selEnd);
                    i = pr.closeChar + 1;
                    continue;
                }
            }

            // Default character handling
            char ch = text.charAt(i);
            if (ch == ')' || ch == ']') {
                boolean hasMatchingOpen = false;
                if (parens != null) {
                    for (ParensSuperscript.ParenRegion item : parens) {
                        if (item != null && item.closeChar == i) {
                            hasMatchingOpen = true;
                            break;
                        }
                    }
                }
                if (!hasMatchingOpen) {
                    i++;
                    continue;
                }
            }

            int charW = getCharWidth(g2, font, fm, ch);
            if (draw) {
                if (i >= selStart && i < selEnd) {
                    g2.setColor(new Color(130, 160, 210, 100));
                    g2.fillRect(curX, baselineY - fm.getAscent(), charW, fm.getHeight());
                    g2.setColor(Color.WHITE);
                }
                if (ch == '\u2297' || ch == '\u229B') {
                    drawSpecialChar(g2, ch, curX, baselineY, font, fm, charW);
                } else {
                    Font drawFont = getFontForChar(font, ch);
                    g2.setFont(drawFont);
                    g2.drawString(text.substring(i, i + 1), curX, baselineY);
                }
            }
            curX += charW;
            i++;
        }
        if (!draw && selStart == -1 && selEnd == -1) {
            long key = makeMetricKey(0, start, end, font.getSize2D());
            METRIC_CACHE.get().put(key, curX - x);
        }
        return curX - x;
    }

    public static int getRegionAscent(Graphics2D g2, String text, int start, int end,
            Font font, FractionsSuperscript.FractionRegion[] fractions,
            RootsSuperscript.RootRegion[] roots, ExponentsSuperscript.PowerRegion[] powers,
            IntegralsSuperscript.IntegralRegion[] integrals, ParensSuperscript.ParenRegion[] parens,
            int nestLevel) {
        if (text == null || start >= end)
            return (g2 != null ? g2 : FALLBACK_G2).getFontMetrics(font).getAscent();
        long key = makeMetricKey(1, start, end, font.getSize2D());
        Integer cached = METRIC_CACHE.get().get(key);
        if (cached != null)
            return cached;
        RegionContext rc = CURRENT_REGIONS.get();
        if (rc != null && rc.text.equals(text)) {
            return getRegionAscent(g2, text, start, end, font, fractions, roots, powers, integrals, rc.summations, rc.logs, rc.derivatives, rc.floorCeils, rc.absoluteValues, parens, nestLevel);
        }
        SummatoriesSuperScript.SumRegion[] summations = hasSumChar(text) ? SummatoriesSuperScript.findRegions(text) : EMPTY_SUMS;
        return getRegionAscent(g2, text, start, end, font, fractions, roots, powers, integrals, summations, parens, nestLevel);
    }

    public static int getRegionAscent(Graphics2D g2, String text, int start, int end,
            Font font, FractionsSuperscript.FractionRegion[] fractions,
            RootsSuperscript.RootRegion[] roots, ExponentsSuperscript.PowerRegion[] powers,
            IntegralsSuperscript.IntegralRegion[] integrals, SummatoriesSuperScript.SumRegion[] summations,
            ParensSuperscript.ParenRegion[] parens, int nestLevel) {
        LogsSuperScript.LogRegion[] logs = (text != null && (text.contains("log") || text.contains("ln"))) ? LogsSuperScript.findRegions(text) : EMPTY_LOGS;
        return getRegionAscent(g2, text, start, end, font, fractions, roots, powers, integrals, summations, logs, parens, nestLevel);
    }

    public static int getRegionAscent(Graphics2D g2, String text, int start, int end,
            Font font, FractionsSuperscript.FractionRegion[] fractions,
            RootsSuperscript.RootRegion[] roots, ExponentsSuperscript.PowerRegion[] powers,
            IntegralsSuperscript.IntegralRegion[] integrals, SummatoriesSuperScript.SumRegion[] summations,
            LogsSuperScript.LogRegion[] logs, ParensSuperscript.ParenRegion[] parens, int nestLevel) {
        DerivativesSuperScript.DerivativeRegion[] derivatives = (text != null && (text.contains("d") || text.contains("∂"))) ? DerivativesSuperScript.findRegions(text) : EMPTY_DERIVS;
        return getRegionAscent(g2, text, start, end, font, fractions, roots, powers, integrals, summations, logs, derivatives, parens, nestLevel);
    }

    public static int getRegionAscent(Graphics2D g2, String text, int start, int end,
            Font font, FractionsSuperscript.FractionRegion[] fractions,
            RootsSuperscript.RootRegion[] roots, ExponentsSuperscript.PowerRegion[] powers,
            IntegralsSuperscript.IntegralRegion[] integrals, SummatoriesSuperScript.SumRegion[] summations,
            LogsSuperScript.LogRegion[] logs, DerivativesSuperScript.DerivativeRegion[] derivatives,
            ParensSuperscript.ParenRegion[] parens, int nestLevel) {
        FloorAndCeilingSuperScript.FloorCeilRegion[] floorCeils = (text != null && (text.contains("⌊") || text.contains("⌈") || text.contains("floor") || text.contains("ceil")))
                ? FloorAndCeilingSuperScript.findRegions(text, fractions, roots, powers, integrals, summations, logs, derivatives) : EMPTY_FLOORS;
        AbsoluteValueSuperScript.AbsoluteValueRegion[] absoluteValues = (text != null && text.contains("|"))
                ? AbsoluteValueSuperScript.findRegions(text, fractions, roots, powers, integrals, summations, logs, derivatives) : EMPTY_ABS;
        return getRegionAscent(g2, text, start, end, font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
    }

    public static int getRegionAscent(Graphics2D g2, String text, int start, int end,
            Font font, FractionsSuperscript.FractionRegion[] fractions,
            RootsSuperscript.RootRegion[] roots, ExponentsSuperscript.PowerRegion[] powers,
            IntegralsSuperscript.IntegralRegion[] integrals, SummatoriesSuperScript.SumRegion[] summations,
            LogsSuperScript.LogRegion[] logs, DerivativesSuperScript.DerivativeRegion[] derivatives,
            FloorAndCeilingSuperScript.FloorCeilRegion[] floorCeils,
            AbsoluteValueSuperScript.AbsoluteValueRegion[] absoluteValues,
            ParensSuperscript.ParenRegion[] parens, int nestLevel) {
        if (g2 == null) g2 = FALLBACK_G2;
        if (text == null || start >= end)
            return g2.getFontMetrics(font).getAscent();
        long key = makeMetricKey(1, start, end, font.getSize2D());
        Map<Long, Integer> cache = METRIC_CACHE.get();
        Integer cached = cache.get(key);
        if (cached != null)
            return cached;

        int maxAscent = g2.getFontMetrics(font).getAscent();
        FontMetrics fm = g2.getFontMetrics(font);
        RegionContext rcSub = CURRENT_REGIONS.get();
        SubscriptsSuperscript.SubscriptRegion[] subscripts = (rcSub != null && rcSub.text.equals(text))
                ? rcSub.subscripts
                : ((text != null && text.indexOf('_') != -1)
                        ? SubscriptsSuperscript.findRegions(text, integrals, summations, powers) : EMPTY_SUBSCRIPTS);
        for (int i = start; i < end;) {
            char c = text.charAt(i);

            // Fractions
            if (c == '(' && fractions != null && fractions.length > 0) {
                FractionsSuperscript.FractionRegion f = FractionsSuperscript.getAt(fractions, i, end);
                if (f != null) {
                    int asc = FractionsSuperscript.getAscent(g2, text, f, font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel, end);
                    maxAscent = Math.max(maxAscent, asc);
                    i = Math.min(f.denClose, end) + 1;
                    continue;
                }
            }

            // Roots
            if ((c == '√' || c == '\u221A') && roots != null && roots.length > 0) {
                RootsSuperscript.RootRegion r = RootsSuperscript.getAt(roots, i);
                if (r != null) {
                    int asc = RootsSuperscript.getAscent(g2, text, r, font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel, end);
                    maxAscent = Math.max(maxAscent, asc);
                    int close = (r.parenClose != -1 && r.parenClose < end) ? r.parenClose : end;
                    i = (r.parenOpen != -1 && close < end) ? close + 1 : end;
                    continue;
                }
            }

            // Exponents
            if (c == '^' && powers != null && powers.length > 0) {
                ExponentsSuperscript.PowerRegion p = ExponentsSuperscript.getAt(powers, i);
                if (p != null) {
                    int asc = ExponentsSuperscript.getAscent(g2, text, p, font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel, end);
                    maxAscent = Math.max(maxAscent, asc);
                    int close = (p.closeParen != -1 && p.closeParen < end) ? p.closeParen : end;
                    i = (p.openParen != -1) ? close + 1 : close;
                    continue;
                }
            }

            // Subscripts
            if (c == '_' && subscripts != null && subscripts.length > 0) {
                SubscriptsSuperscript.SubscriptRegion s = SubscriptsSuperscript.getAt(subscripts, i);
                if (s != null) {
                    int asc = SubscriptsSuperscript.getAscent(g2, text, s, font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
                    maxAscent = Math.max(maxAscent, asc);
                    int close = (s.closeParen != -1 && s.closeParen < end) ? s.closeParen : end;
                    i = (s.openParen != -1) ? close + 1 : close;
                    continue;
                }
            }

            // Integrals
            if ((c == '∫' || c == '\u222B') && integrals != null && integrals.length > 0) {
                IntegralsSuperscript.IntegralRegion it = IntegralsSuperscript.getAt(integrals, i);
                if (it != null) {
                    int asc = IntegralsSuperscript.getAscent(g2, text, it, font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
                    maxAscent = Math.max(maxAscent, asc);
                    i = (it.dxEnd != -1 && it.dxEnd <= end) ? it.dxEnd
                            : ((it.parenClose != -1 && it.parenClose < end) ? it.parenClose + 1 : end);
                    continue;
                }
            }

            // Summations
            if ((c == '∑' || c == '\u2211' || c == 'Σ' || c == '\u03A3') && summations != null && summations.length > 0) {
                SummatoriesSuperScript.SumRegion sm = SummatoriesSuperScript.getAt(summations, i);
                if (sm != null) {
                    int asc = SummatoriesSuperScript.getAscent(g2, text, sm, font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel, end);
                    maxAscent = Math.max(maxAscent, asc);
                    int nextI = SummatoriesSuperScript.getArgumentEnd(sm, end);
                    if (sm.parenClose != -1 && sm.parenClose < end) {
                        nextI = sm.parenClose + 1;
                    }
                    i = nextI;
                    continue;
                }
            }

            // Logs
            if ((c == 'l' || c == 'L') && logs != null && logs.length > 0) {
                LogsSuperScript.LogRegion lg = LogsSuperScript.getAt(logs, i);
                if (lg != null) {
                    int asc = LogsSuperScript.getAscent(g2, text, lg, font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel, end);
                    maxAscent = Math.max(maxAscent, asc);
                    i = lg.getEnd(end);
                    continue;
                }
            }

            // Derivatives
            if ((c == 'd' || c == 'D' || c == '∂' || c == '\u2202' || c == '(') && derivatives != null && derivatives.length > 0) {
                DerivativesSuperScript.DerivativeRegion dr = DerivativesSuperScript.getAt(derivatives, i);
                if (dr != null) {
                    int asc = DerivativesSuperScript.getAscent(g2, text, dr, font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel, end);
                    maxAscent = Math.max(maxAscent, asc);
                    i = dr.getEnd(end);
                    continue;
                }
            }

            // Floor and Ceil
            if ((c == '⌊' || c == '⌈') && floorCeils != null && floorCeils.length > 0) {
                FloorAndCeilingSuperScript.FloorCeilRegion fcr = FloorAndCeilingSuperScript.getAt(floorCeils, i, end);
                if (fcr != null) {
                    int asc = FloorAndCeilingSuperScript.getAscent(g2, text, fcr, font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
                    maxAscent = Math.max(maxAscent, asc);
                    i = fcr.closeChar + 1;
                    continue;
                }
            }

            // Absolute Value
            if (c == '|' && absoluteValues != null && absoluteValues.length > 0) {
                AbsoluteValueSuperScript.AbsoluteValueRegion avr = AbsoluteValueSuperScript.getAt(absoluteValues, i, end);
                if (avr != null) {
                    int asc = AbsoluteValueSuperScript.getAscent(g2, text, avr, font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
                    maxAscent = Math.max(maxAscent, asc);
                    i = avr.closeChar + 1;
                    continue;
                }
            }

            // Parens
            if ((c == '(' || c == '[') && parens != null && parens.length > 0) {
                ParensSuperscript.ParenRegion pr = ParensSuperscript.getAt(parens, i, end);
                if (pr != null && ParensSuperscript.isStretchy(g2, text, pr, font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel)) {
                    int asc = ParensSuperscript.getAscent(g2, text, pr, font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
                    maxAscent = Math.max(maxAscent, asc);
                    i = pr.closeChar + 1;
                    continue;
                }
            }

            i++;
        }
        cache.put(key, maxAscent);
        return maxAscent;
    }

    public static int getRegionDescent(Graphics2D g2, String text, int start, int end,
            Font font, FractionsSuperscript.FractionRegion[] fractions,
            RootsSuperscript.RootRegion[] roots, ExponentsSuperscript.PowerRegion[] powers,
            IntegralsSuperscript.IntegralRegion[] integrals, ParensSuperscript.ParenRegion[] parens,
            int nestLevel) {
        if (text == null || start >= end)
            return (g2 != null ? g2 : FALLBACK_G2).getFontMetrics(font).getDescent();
        long key = makeMetricKey(2, start, end, font.getSize2D());
        Integer cached = METRIC_CACHE.get().get(key);
        if (cached != null)
            return cached;
        RegionContext rc = CURRENT_REGIONS.get();
        if (rc != null && rc.text.equals(text)) {
            return getRegionDescent(g2, text, start, end, font, fractions, roots, powers, integrals, rc.summations, rc.logs, rc.derivatives, rc.floorCeils, rc.absoluteValues, parens, nestLevel);
        }
        SummatoriesSuperScript.SumRegion[] summations = hasSumChar(text) ? SummatoriesSuperScript.findRegions(text) : EMPTY_SUMS;
        return getRegionDescent(g2, text, start, end, font, fractions, roots, powers, integrals, summations, parens, nestLevel);
    }

    public static int getRegionDescent(Graphics2D g2, String text, int start, int end,
            Font font, FractionsSuperscript.FractionRegion[] fractions,
            RootsSuperscript.RootRegion[] roots, ExponentsSuperscript.PowerRegion[] powers,
            IntegralsSuperscript.IntegralRegion[] integrals, SummatoriesSuperScript.SumRegion[] summations,
            ParensSuperscript.ParenRegion[] parens, int nestLevel) {
        LogsSuperScript.LogRegion[] logs = (text != null && (text.contains("log") || text.contains("ln"))) ? LogsSuperScript.findRegions(text) : EMPTY_LOGS;
        return getRegionDescent(g2, text, start, end, font, fractions, roots, powers, integrals, summations, logs, parens, nestLevel);
    }

    public static int getRegionDescent(Graphics2D g2, String text, int start, int end,
            Font font, FractionsSuperscript.FractionRegion[] fractions,
            RootsSuperscript.RootRegion[] roots, ExponentsSuperscript.PowerRegion[] powers,
            IntegralsSuperscript.IntegralRegion[] integrals, SummatoriesSuperScript.SumRegion[] summations,
            LogsSuperScript.LogRegion[] logs, ParensSuperscript.ParenRegion[] parens, int nestLevel) {
        DerivativesSuperScript.DerivativeRegion[] derivatives = (text != null && (text.contains("d") || text.contains("∂"))) ? DerivativesSuperScript.findRegions(text) : EMPTY_DERIVS;
        return getRegionDescent(g2, text, start, end, font, fractions, roots, powers, integrals, summations, logs, derivatives, parens, nestLevel);
    }

    public static int getRegionDescent(Graphics2D g2, String text, int start, int end,
            Font font, FractionsSuperscript.FractionRegion[] fractions,
            RootsSuperscript.RootRegion[] roots, ExponentsSuperscript.PowerRegion[] powers,
            IntegralsSuperscript.IntegralRegion[] integrals, SummatoriesSuperScript.SumRegion[] summations,
            LogsSuperScript.LogRegion[] logs, DerivativesSuperScript.DerivativeRegion[] derivatives,
            ParensSuperscript.ParenRegion[] parens, int nestLevel) {
        FloorAndCeilingSuperScript.FloorCeilRegion[] floorCeils = (text != null && (text.contains("⌊") || text.contains("⌈") || text.contains("floor") || text.contains("ceil")))
                ? FloorAndCeilingSuperScript.findRegions(text, fractions, roots, powers, integrals, summations, logs, derivatives) : EMPTY_FLOORS;
        AbsoluteValueSuperScript.AbsoluteValueRegion[] absoluteValues = (text != null && text.contains("|"))
                ? AbsoluteValueSuperScript.findRegions(text, fractions, roots, powers, integrals, summations, logs, derivatives) : EMPTY_ABS;
        return getRegionDescent(g2, text, start, end, font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
    }

    public static int getRegionDescent(Graphics2D g2, String text, int start, int end,
            Font font, FractionsSuperscript.FractionRegion[] fractions,
            RootsSuperscript.RootRegion[] roots, ExponentsSuperscript.PowerRegion[] powers,
            IntegralsSuperscript.IntegralRegion[] integrals, SummatoriesSuperScript.SumRegion[] summations,
            LogsSuperScript.LogRegion[] logs, DerivativesSuperScript.DerivativeRegion[] derivatives,
            FloorAndCeilingSuperScript.FloorCeilRegion[] floorCeils,
            AbsoluteValueSuperScript.AbsoluteValueRegion[] absoluteValues,
            ParensSuperscript.ParenRegion[] parens, int nestLevel) {
        if (g2 == null) g2 = FALLBACK_G2;
        if (text == null || start >= end)
            return g2.getFontMetrics(font).getDescent();
        long key = makeMetricKey(2, start, end, font.getSize2D());
        Map<Long, Integer> cache = METRIC_CACHE.get();
        Integer cached = cache.get(key);
        if (cached != null)
            return cached;

        int maxDescent = g2.getFontMetrics(font).getDescent();
        SubscriptsSuperscript.SubscriptRegion[] subscripts = (text != null && text.indexOf('_') != -1)
                ? SubscriptsSuperscript.findRegions(text, integrals, summations, powers) : EMPTY_SUBSCRIPTS;
        for (int i = start; i < end;) {
            char c = text.charAt(i);

            // Fractions
            if (c == '(' && fractions != null && fractions.length > 0) {
                FractionsSuperscript.FractionRegion f = FractionsSuperscript.getAt(fractions, i, end);
                if (f != null) {
                    int desc = FractionsSuperscript.getDescent(g2, text, f, font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel, end);
                    maxDescent = Math.max(maxDescent, desc);
                    i = Math.min(f.denClose, end) + 1;
                    continue;
                }
            }

            // Roots
            if ((c == '√' || c == '\u221A') && roots != null && roots.length > 0) {
                RootsSuperscript.RootRegion r = RootsSuperscript.getAt(roots, i);
                if (r != null) {
                    int desc = RootsSuperscript.getDescent(g2, text, r, font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel, end);
                    maxDescent = Math.max(maxDescent, desc);
                    int close = (r.parenClose != -1 && r.parenClose < end) ? r.parenClose : end;
                    i = (r.parenOpen != -1 && close < end) ? close + 1 : end;
                    continue;
                }
            }

            // Exponents
            if (c == '^' && powers != null && powers.length > 0) {
                ExponentsSuperscript.PowerRegion p = ExponentsSuperscript.getAt(powers, i);
                if (p != null) {
                    int desc = ExponentsSuperscript.getDescent(g2, text, p, font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel, end);
                    maxDescent = Math.max(maxDescent, desc);
                    int close = (p.closeParen != -1 && p.closeParen < end) ? p.closeParen : end;
                    i = (p.openParen != -1) ? close + 1 : close;
                    continue;
                }
            }

            // Subscripts
            if (c == '_' && subscripts != null && subscripts.length > 0) {
                SubscriptsSuperscript.SubscriptRegion s = SubscriptsSuperscript.getAt(subscripts, i);
                if (s != null) {
                    int desc = SubscriptsSuperscript.getDescent(g2, text, s, font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
                    maxDescent = Math.max(maxDescent, desc);
                    int close = (s.closeParen != -1 && s.closeParen < end) ? s.closeParen : end;
                    i = (s.openParen != -1) ? close + 1 : close;
                    continue;
                }
            }

            // Integrals
            if ((c == '∫' || c == '\u222B') && integrals != null && integrals.length > 0) {
                IntegralsSuperscript.IntegralRegion it = IntegralsSuperscript.getAt(integrals, i);
                if (it != null) {
                    int desc = IntegralsSuperscript.getDescent(g2, text, it, font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
                    maxDescent = Math.max(maxDescent, desc);
                    i = (it.dxEnd != -1 && it.dxEnd <= end) ? it.dxEnd
                            : ((it.parenClose != -1 && it.parenClose < end) ? it.parenClose + 1 : end);
                    continue;
                }
            }

            // Summations
            if ((c == '∑' || c == '\u2211' || c == 'Σ' || c == '\u03A3') && summations != null && summations.length > 0) {
                SummatoriesSuperScript.SumRegion sm = SummatoriesSuperScript.getAt(summations, i);
                if (sm != null) {
                    int desc = SummatoriesSuperScript.getDescent(g2, text, sm, font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel, end);
                    maxDescent = Math.max(maxDescent, desc);
                    int nextI = SummatoriesSuperScript.getArgumentEnd(sm, end);
                    if (sm.parenClose != -1 && sm.parenClose < end) {
                        nextI = sm.parenClose + 1;
                    }
                    i = nextI;
                    continue;
                }
            }

            // Logs
            if ((c == 'l' || c == 'L') && logs != null && logs.length > 0) {
                LogsSuperScript.LogRegion lg = LogsSuperScript.getAt(logs, i);
                if (lg != null) {
                    int desc = LogsSuperScript.getDescent(g2, text, lg, font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel, end);
                    maxDescent = Math.max(maxDescent, desc);
                    i = lg.getEnd(end);
                    continue;
                }
            }

            // Derivatives
            if ((c == 'd' || c == 'D' || c == '∂' || c == '\u2202' || c == '(') && derivatives != null && derivatives.length > 0) {
                DerivativesSuperScript.DerivativeRegion dr = DerivativesSuperScript.getAt(derivatives, i);
                if (dr != null) {
                    int desc = DerivativesSuperScript.getDescent(g2, text, dr, font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel, end);
                    maxDescent = Math.max(maxDescent, desc);
                    i = dr.getEnd(end);
                    continue;
                }
            }

            // Floor and Ceil
            if ((c == '⌊' || c == '⌈') && floorCeils != null && floorCeils.length > 0) {
                FloorAndCeilingSuperScript.FloorCeilRegion fcr = FloorAndCeilingSuperScript.getAt(floorCeils, i, end);
                if (fcr != null) {
                    int desc = FloorAndCeilingSuperScript.getDescent(g2, text, fcr, font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
                    maxDescent = Math.max(maxDescent, desc);
                    i = fcr.closeChar + 1;
                    continue;
                }
            }

            // Absolute Value
            if (c == '|' && absoluteValues != null && absoluteValues.length > 0) {
                AbsoluteValueSuperScript.AbsoluteValueRegion avr = AbsoluteValueSuperScript.getAt(absoluteValues, i, end);
                if (avr != null) {
                    int desc = AbsoluteValueSuperScript.getDescent(g2, text, avr, font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
                    maxDescent = Math.max(maxDescent, desc);
                    i = avr.closeChar + 1;
                    continue;
                }
            }

            // Parens
            if ((c == '(' || c == '[') && parens != null && parens.length > 0) {
                ParensSuperscript.ParenRegion pr = ParensSuperscript.getAt(parens, i, end);
                if (pr != null && ParensSuperscript.isStretchy(g2, text, pr, font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel)) {
                    int desc = ParensSuperscript.getDescent(g2, text, pr, font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
                    maxDescent = Math.max(maxDescent, desc);
                    i = pr.closeChar + 1;
                    continue;
                }
            }

            i++;
        }
        int finalDescent = Math.max(0, maxDescent);
        cache.put(key, finalDescent);
        return finalDescent;
    }

    public static int[] getCaretInfoSubString(Graphics2D g2, String text, int start, int end,
            int targetOffset, Font font, FractionsSuperscript.FractionRegion[] fractions,
            RootsSuperscript.RootRegion[] roots, ExponentsSuperscript.PowerRegion[] powers,
            IntegralsSuperscript.IntegralRegion[] integrals, ParensSuperscript.ParenRegion[] parens,
            int nestLevel) {
        SummatoriesSuperScript.SumRegion[] summations = SummatoriesSuperScript.findRegions(text);
        return getCaretInfoSubString(g2, text, start, end, targetOffset, font, fractions, roots, powers, integrals, summations, parens, nestLevel);
    }

    public static int[] getCaretInfoSubString(Graphics2D g2, String text, int start, int end,
            int targetOffset, Font font, FractionsSuperscript.FractionRegion[] fractions,
            RootsSuperscript.RootRegion[] roots, ExponentsSuperscript.PowerRegion[] powers,
            IntegralsSuperscript.IntegralRegion[] integrals, SummatoriesSuperScript.SumRegion[] summations,
            ParensSuperscript.ParenRegion[] parens, int nestLevel) {
        LogsSuperScript.LogRegion[] logs = LogsSuperScript.findRegions(text);
        return getCaretInfoSubString(g2, text, start, end, targetOffset, font, fractions, roots, powers, integrals, summations, logs, parens, nestLevel);
    }

    public static int[] getCaretInfoSubString(Graphics2D g2, String text, int start, int end,
            int targetOffset, Font font, FractionsSuperscript.FractionRegion[] fractions,
            RootsSuperscript.RootRegion[] roots, ExponentsSuperscript.PowerRegion[] powers,
            IntegralsSuperscript.IntegralRegion[] integrals, SummatoriesSuperScript.SumRegion[] summations,
            LogsSuperScript.LogRegion[] logs, ParensSuperscript.ParenRegion[] parens, int nestLevel) {
        DerivativesSuperScript.DerivativeRegion[] derivatives = DerivativesSuperScript.findRegions(text);
        return getCaretInfoSubString(g2, text, start, end, targetOffset, font, fractions, roots, powers, integrals, summations, logs, derivatives, parens, nestLevel);
    }

    public static int[] getCaretInfoSubString(Graphics2D g2, String text, int start, int end,
            int targetOffset, Font font, FractionsSuperscript.FractionRegion[] fractions,
            RootsSuperscript.RootRegion[] roots, ExponentsSuperscript.PowerRegion[] powers,
            IntegralsSuperscript.IntegralRegion[] integrals, SummatoriesSuperScript.SumRegion[] summations,
            LogsSuperScript.LogRegion[] logs, DerivativesSuperScript.DerivativeRegion[] derivatives,
            ParensSuperscript.ParenRegion[] parens, int nestLevel) {
        FloorAndCeilingSuperScript.FloorCeilRegion[] floorCeils = FloorAndCeilingSuperScript.findRegions(text, fractions, roots, powers, integrals, summations, logs, derivatives);
        AbsoluteValueSuperScript.AbsoluteValueRegion[] absoluteValues = AbsoluteValueSuperScript.findRegions(text, fractions, roots, powers, integrals, summations, logs, derivatives);
        return getCaretInfoSubString(g2, text, start, end, targetOffset, font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
    }

    public static int[] getCaretInfoSubString(Graphics2D g2, String text, int start, int end,
            int targetOffset, Font font, FractionsSuperscript.FractionRegion[] fractions,
            RootsSuperscript.RootRegion[] roots, ExponentsSuperscript.PowerRegion[] powers,
            IntegralsSuperscript.IntegralRegion[] integrals, SummatoriesSuperScript.SumRegion[] summations,
            LogsSuperScript.LogRegion[] logs, DerivativesSuperScript.DerivativeRegion[] derivatives,
            FloorAndCeilingSuperScript.FloorCeilRegion[] floorCeils,
            AbsoluteValueSuperScript.AbsoluteValueRegion[] absoluteValues,
            ParensSuperscript.ParenRegion[] parens, int nestLevel) {
        if (g2 == null) g2 = FALLBACK_G2;
        if (targetOffset < start) {
            return null;
        }
        FontMetrics fm = g2.getFontMetrics(font);
        RegionContext rcSub = CURRENT_REGIONS.get();
        SubscriptsSuperscript.SubscriptRegion[] subscripts = (rcSub != null && rcSub.text.equals(text))
                ? rcSub.subscripts
                : ((text != null && text.indexOf('_') != -1)
                        ? SubscriptsSuperscript.findRegions(text, integrals, summations, powers) : EMPTY_SUBSCRIPTS);
        int curX = 0;
        int i = start;
        while (i < end) {
            if (i > targetOffset) {
                return null;
            }
            char c = text.charAt(i);

            // Fractions
            if (c == '(' && fractions != null && fractions.length > 0) {
                FractionsSuperscript.FractionRegion f = FractionsSuperscript.getAt(fractions, i, end);
                if (f != null) {
                    int numClose = Math.min(f.numClose, end);
                    int denOpen = Math.min(f.denOpen, end);
                    int denClose = Math.min(f.denClose, end);
                    if (targetOffset <= denClose) {
                        int[] ci = FractionsSuperscript.getCaretInfo(g2, text, f, curX, targetOffset, font,
                                fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel, end);
                        if (ci != null) return ci;
                    }

                    if (f.cachedWidth != -1) {
                        curX += f.cachedWidth;
                    } else {
                        int numW = renderSubString(g2, text, f.numOpen + 1, numClose, 0, 0, font, false,
                                fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel, -1, -1);
                        int denW = renderSubString(g2, text, denOpen + 1, denClose, 0, 0, font, false,
                                fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel, -1, -1);
                        f.cachedWidth = Math.max(numW, denW) + 4;
                        curX += f.cachedWidth;
                    }
                    i = denClose + 1;
                    if (i == targetOffset)
                        return new int[] { curX, 0, fm.getAscent(), fm.getDescent() };
                    continue;
                }
            }

            // Roots
            if ((c == '√' || c == '\u221A') && roots != null && roots.length > 0) {
                RootsSuperscript.RootRegion r = RootsSuperscript.getAt(roots, i);
                if (r != null) {
                    int close = (r.parenClose != -1 && r.parenClose < end) ? r.parenClose : end;
                    if (targetOffset <= close) {
                        int[] ci = RootsSuperscript.getCaretInfo(g2, text, r, curX, targetOffset, font,
                                fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel, end);
                        if (ci != null) return ci;
                    }

                    if (r.cachedWidth != -1) {
                        curX += r.cachedWidth;
                    } else {
                        curX = RootsSuperscript.render(g2, text, r, curX, 0, font, false,
                                fractions, roots, powers, integrals, parens, nestLevel, -1, -1, end);
                    }
                    i = (r.parenOpen != -1 && close < end) ? close + 1 : end;
                    if (i == targetOffset)
                        return new int[] { curX, 0, fm.getAscent(), fm.getDescent() };
                    continue;
                }
            }

            // Exponents
            if (c == '^' && powers != null && powers.length > 0) {
                ExponentsSuperscript.PowerRegion p = ExponentsSuperscript.getAt(powers, i);
                if (p != null) {
                    int pClose = (p.closeParen != -1 && p.closeParen < end) ? p.closeParen : end;
                    int pNextI = (p.openParen != -1) ? pClose + 1 : pClose;
                    SubscriptsSuperscript.SubscriptRegion pairedSub = null;
                    if (pNextI < end && text.charAt(pNextI) == '_' && subscripts != null && subscripts.length > 0) {
                        pairedSub = SubscriptsSuperscript.getAt(subscripts, pNextI);
                        if (pairedSub != null) {
                            int subStart = (pairedSub.openParen != -1) ? pairedSub.openParen + 1 : pairedSub.subChar + 1;
                            int subClose = (pairedSub.closeParen != -1 && pairedSub.closeParen < end) ? pairedSub.closeParen : end;
                            if (SubscriptsSuperscript.needsAutoParens(text, subStart, subClose)) {
                                pairedSub = null;
                            }
                        }
                    }
                    if (pairedSub != null) {
                        int startX = curX;
                        int subClose = (pairedSub.closeParen != -1 && pairedSub.closeParen < end) ? pairedSub.closeParen : end;
                        int subEnd = (pairedSub.openParen != -1) ? subClose + 1 : subClose;

                        if (targetOffset >= p.caretChar && targetOffset < pNextI) {
                            int[] ci = ExponentsSuperscript.getCaretInfo(g2, text, p, startX, targetOffset, font,
                                    fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel, end);
                            if (ci != null) return ci;
                        }
                        if (targetOffset >= pairedSub.subChar && targetOffset <= subEnd) {
                            int[] ci = SubscriptsSuperscript.getCaretInfo(g2, text, pairedSub, startX, targetOffset, font,
                                    fractions, roots, powers, integrals, summations, logs, derivatives,
                                    floorCeils, absoluteValues, parens, nestLevel, end);
                            if (ci != null) return ci;
                        }

                        if (p.cachedWidth != -1 && pairedSub.cachedWidth != -1) {
                            curX = startX + Math.max(p.cachedWidth, pairedSub.cachedWidth);
                        } else {
                            int endPowX = ExponentsSuperscript.render(g2, text, p, startX, 0, font, false,
                                    fractions, roots, powers, integrals, parens, nestLevel, -1, -1, end);
                            int endSubX = SubscriptsSuperscript.render(g2, text, pairedSub, startX, 0, font, false,
                                    fractions, roots, powers, integrals, summations, logs, derivatives,
                                    floorCeils, absoluteValues, parens, nestLevel, -1, -1, end);
                            curX = startX + Math.max(endPowX - startX, endSubX - startX);
                        }
                        i = subEnd;
                        if (i == targetOffset)
                            return new int[] { curX, 0, fm.getAscent(), fm.getDescent() };
                        continue;
                    }

                    int close = (p.closeParen != -1 && p.closeParen < end) ? p.closeParen : end;
                    if (targetOffset <= close) {
                        int[] ci = ExponentsSuperscript.getCaretInfo(g2, text, p, curX, targetOffset, font,
                                fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel, end);
                        if (ci != null) return ci;
                    }

                    if (p.cachedWidth != -1) {
                        curX += p.cachedWidth;
                    } else {
                        curX = ExponentsSuperscript.render(g2, text, p, curX, 0, font, false,
                                fractions, roots, powers, integrals, parens, nestLevel, -1, -1, end);
                    }
                    i = (p.openParen != -1) ? close + 1 : close;
                    if (i == targetOffset)
                        return new int[] { curX, 0, fm.getAscent(), fm.getDescent() };
                    continue;
                }
            }

            // Subscripts
            if (c == '_' && subscripts != null && subscripts.length > 0) {
                SubscriptsSuperscript.SubscriptRegion s = SubscriptsSuperscript.getAt(subscripts, i);
                if (s != null) {
                    int sClose = (s.closeParen != -1 && s.closeParen < end) ? s.closeParen : end;
                    int sNextI = (s.openParen != -1) ? sClose + 1 : sClose;
                    ExponentsSuperscript.PowerRegion pairedPow = null;
                    if (sNextI < end && text.charAt(sNextI) == '^' && powers != null && powers.length > 0) {
                        int subStart = (s.openParen != -1) ? s.openParen + 1 : s.subChar + 1;
                        if (!SubscriptsSuperscript.needsAutoParens(text, subStart, sClose)) {
                            pairedPow = ExponentsSuperscript.getAt(powers, sNextI);
                        }
                    }
                    if (pairedPow != null) {
                        int startX = curX;
                        int powClose = (pairedPow.closeParen != -1 && pairedPow.closeParen < end) ? pairedPow.closeParen : end;
                        int powEnd = (pairedPow.openParen != -1) ? powClose + 1 : powClose;

                        if (targetOffset >= s.subChar && targetOffset < sNextI) {
                            int[] ci = SubscriptsSuperscript.getCaretInfo(g2, text, s, startX, targetOffset, font,
                                    fractions, roots, powers, integrals, summations, logs, derivatives,
                                    floorCeils, absoluteValues, parens, nestLevel, end);
                            if (ci != null) return ci;
                        }
                        if (targetOffset >= pairedPow.caretChar && targetOffset <= powEnd) {
                            int[] ci = ExponentsSuperscript.getCaretInfo(g2, text, pairedPow, startX, targetOffset, font,
                                    fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel, end);
                            if (ci != null) return ci;
                        }

                        if (s.cachedWidth != -1 && pairedPow.cachedWidth != -1) {
                            curX = startX + Math.max(s.cachedWidth, pairedPow.cachedWidth);
                        } else {
                            int endSubX = SubscriptsSuperscript.render(g2, text, s, startX, 0, font, false,
                                    fractions, roots, powers, integrals, summations, logs, derivatives,
                                    floorCeils, absoluteValues, parens, nestLevel, -1, -1, end);
                            int endPowX = ExponentsSuperscript.render(g2, text, pairedPow, startX, 0, font, false,
                                    fractions, roots, powers, integrals, parens, nestLevel, -1, -1, end);
                            curX = startX + Math.max(endSubX - startX, endPowX - startX);
                        }
                        i = powEnd;
                        if (i == targetOffset)
                            return new int[] { curX, 0, fm.getAscent(), fm.getDescent() };
                        continue;
                    }

                    int close = (s.closeParen != -1 && s.closeParen < end) ? s.closeParen : end;
                    if (targetOffset <= close) {
                        int[] ci = SubscriptsSuperscript.getCaretInfo(g2, text, s, curX, targetOffset, font,
                                fractions, roots, powers, integrals, summations, logs, derivatives,
                                floorCeils, absoluteValues, parens, nestLevel, end);
                        if (ci != null) return ci;
                    }

                    if (s.cachedWidth != -1) {
                        curX += s.cachedWidth;
                    } else {
                        curX = SubscriptsSuperscript.render(g2, text, s, curX, 0, font, false,
                                fractions, roots, powers, integrals, summations, logs, derivatives,
                                floorCeils, absoluteValues, parens, nestLevel, -1, -1, end);
                    }
                    i = (s.openParen != -1) ? close + 1 : close;
                    if (i == targetOffset)
                        return new int[] { curX, 0, fm.getAscent(), fm.getDescent() };
                    continue;
                }
            }

            // Integrals
            if ((c == '∫' || c == '\u222B') && integrals != null && integrals.length > 0) {
                IntegralsSuperscript.IntegralRegion it = IntegralsSuperscript.getAt(integrals, i);
                if (it != null) {
                    int itEnd = (it.dxEnd != -1 && it.dxEnd <= end) ? it.dxEnd
                            : ((it.parenClose != -1 && it.parenClose < end) ? it.parenClose + 1 : end);
                    if (targetOffset <= itEnd) {
                        int[] ci = IntegralsSuperscript.getCaretInfo(g2, text, it, curX, targetOffset, font,
                                fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel, end);
                        if (ci != null) return ci;
                    }

                    if (it.cachedWidth != -1) {
                        curX += it.cachedWidth;
                    } else {
                        curX = IntegralsSuperscript.render(g2, text, it, curX, 0, font, false,
                                fractions, roots, powers, integrals, parens, nestLevel, -1, -1, end);
                    }
                    i = itEnd;
                    if (i == targetOffset) {
                        return new int[] { curX, 0, fm.getAscent(), fm.getDescent() };
                    }
                    continue;
                }
            }

            // Summations
            if ((c == '∑' || c == '\u2211' || c == 'Σ' || c == '\u03A3') && summations != null && summations.length > 0) {
                SummatoriesSuperScript.SumRegion sm = SummatoriesSuperScript.getAt(summations, i);
                if (sm != null) {
                    int nextI = (sm.parenClose != -1 && sm.parenClose < end) ? sm.parenClose + 1
                            : SummatoriesSuperScript.getArgumentEnd(sm, end);
                    if (targetOffset <= nextI) {
                        int[] ci = SummatoriesSuperScript.getCaretInfo(g2, text, sm, curX, targetOffset, font,
                                fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel, end);
                        if (ci != null) return ci;
                    }

                    if (sm.cachedWidth != -1) {
                        curX += sm.cachedWidth;
                    } else {
                        curX = SummatoriesSuperScript.render(g2, text, sm, curX, 0, font, false,
                                fractions, roots, powers, integrals, summations, parens, nestLevel, -1, -1, end);
                    }
                    i = nextI;
                    if (i == targetOffset) {
                        return new int[] { curX, 0, fm.getAscent(), fm.getDescent() };
                    }
                    continue;
                }
            }

            // Logs
            if ((c == 'l' || c == 'L') && logs != null && logs.length > 0) {
                LogsSuperScript.LogRegion lg = LogsSuperScript.getAt(logs, i);
                if (lg != null) {
                    int lgEnd = lg.getEnd(end);
                    if (targetOffset <= lgEnd) {
                        int[] ci = LogsSuperScript.getCaretInfo(g2, text, lg, curX, targetOffset, font,
                                fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel, end);
                        if (ci != null) return ci;
                    }

                    if (lg.cachedWidth != -1) {
                        curX += lg.cachedWidth;
                    } else {
                        curX = LogsSuperScript.render(g2, text, lg, curX, 0, font, false,
                                fractions, roots, powers, integrals, summations, parens, nestLevel, -1, -1, end);
                    }
                    i = lgEnd;
                    if (i == targetOffset) {
                        return new int[] { curX, 0, fm.getAscent(), fm.getDescent() };
                    }
                    continue;
                }
            }

            // Derivatives
            if ((c == 'd' || c == 'D' || c == '∂' || c == '\u2202' || c == '(') && derivatives != null && derivatives.length > 0) {
                DerivativesSuperScript.DerivativeRegion dr = DerivativesSuperScript.getAt(derivatives, i);
                if (dr != null) {
                    int drEnd = dr.getEnd(end);
                    if (targetOffset <= drEnd) {
                        int[] ci = DerivativesSuperScript.getCaretInfo(g2, text, dr, curX, targetOffset, font,
                                fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel, end);
                        if (ci != null) return ci;
                    }

                    if (dr.cachedWidth != -1) {
                        curX += dr.cachedWidth;
                    } else {
                        curX = DerivativesSuperScript.render(g2, text, dr, curX, 0, font, false,
                                fractions, roots, powers, integrals, summations, logs, parens, nestLevel, -1, -1, end);
                    }
                    i = drEnd;
                    if (i == targetOffset) {
                        return new int[] { curX, 0, fm.getAscent(), fm.getDescent() };
                    }
                    continue;
                }
            }

            // Floor and Ceil
            if ((c == '⌊' || c == '⌈') && floorCeils != null && floorCeils.length > 0) {
                FloorAndCeilingSuperScript.FloorCeilRegion fcr = FloorAndCeilingSuperScript.getAt(floorCeils, i, end);
                if (fcr != null) {
                    if (targetOffset <= fcr.closeChar) {
                        int[] ci = FloorAndCeilingSuperScript.getCaretInfo(g2, text, fcr, curX, targetOffset, font,
                                fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
                        if (ci != null) return ci;
                    }

                    if (fcr.cachedWidth != -1) {
                        curX += fcr.cachedWidth;
                    } else {
                        int cAscent = getRegionAscent(g2, text, fcr.openChar + 1, fcr.closeChar,
                                font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
                        int cDescent = getRegionDescent(g2, text, fcr.openChar + 1, fcr.closeChar,
                                font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
                        int h = cAscent + cDescent;
                        int parenW = Math.max(fm.charWidth(text.charAt(i)), Math.min(13, 6 + h / 20));
                        int contentW = renderSubString(g2, text, fcr.openChar + 1, fcr.closeChar, 0, 0,
                                font, false, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel, -1, -1);
                        int totalW = parenW + contentW + parenW;
                        fcr.cachedWidth = totalW;
                        curX += totalW;
                    }
                    i = fcr.closeChar + 1;
                    if (i == targetOffset) {
                        return new int[] { curX, 0, fm.getAscent(), fm.getDescent() };
                    }
                    continue;
                }
            }

            // Absolute Value
            if (c == '|' && absoluteValues != null && absoluteValues.length > 0) {
                AbsoluteValueSuperScript.AbsoluteValueRegion avr = AbsoluteValueSuperScript.getAt(absoluteValues, i, end);
                if (avr != null) {
                    if (targetOffset <= avr.closeChar) {
                        int[] ci = AbsoluteValueSuperScript.getCaretInfo(g2, text, avr, curX, targetOffset, font,
                                fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
                        if (ci != null) return ci;
                    }

                    if (avr.cachedWidth != -1) {
                        curX += avr.cachedWidth;
                    } else {
                        int cAscent = getRegionAscent(g2, text, avr.openChar + 1, avr.closeChar,
                                font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
                        int cDescent = getRegionDescent(g2, text, avr.openChar + 1, avr.closeChar,
                                font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
                        int h = cAscent + cDescent;
                        int barW = Math.max(fm.charWidth('|'), Math.min(10, 6 + h / 30));
                        int contentW = renderSubString(g2, text, avr.openChar + 1, avr.closeChar, 0, 0,
                                font, false, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel, -1, -1);
                        int totalW = barW + contentW + barW;
                        avr.cachedWidth = totalW;
                        curX += totalW;
                    }
                    i = avr.closeChar + 1;
                    if (i == targetOffset) {
                        return new int[] { curX, 0, fm.getAscent(), fm.getDescent() };
                    }
                    continue;
                }
            }

            // Parens
            if ((c == '(' || c == '[') && parens != null && parens.length > 0) {
                ParensSuperscript.ParenRegion pr = ParensSuperscript.getAt(parens, i, end);
                if (pr != null && ParensSuperscript.isStretchy(g2, text, pr, font, fractions, roots, powers, integrals, parens, nestLevel)) {
                    if (targetOffset <= pr.closeChar) {
                        int[] ci = ParensSuperscript.getCaretInfo(g2, text, pr, curX, targetOffset, font,
                                fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
                        if (ci != null) return ci;
                    }

                    if (pr.cachedWidth != -1) {
                        curX += pr.cachedWidth;
                    } else {
                        int cAscent = pr.cachedAscent;
                        int cDescent = pr.cachedDescent;
                        int h = cAscent + cDescent;
                        int parenW = Math.max(fm.charWidth(text.charAt(i)), Math.min(13, 6 + h / 20));
                        int contentW = renderSubString(g2, text, pr.openChar + 1, pr.closeChar, 0, 0,
                                font, false, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel, -1, -1);
                        int totalW = parenW + contentW + parenW;
                        pr.cachedWidth = totalW;
                        curX += totalW;
                    }
                    i = pr.closeChar + 1;
                    if (i == targetOffset) {
                        return new int[] { curX, 0, fm.getAscent(), fm.getDescent() };
                    }
                    continue;
                }
            }

            if (i == targetOffset) {
                return new int[] { curX, 0, fm.getAscent(), fm.getDescent() };
            }

            // Normal character
            char ch = text.charAt(i);
            if (ch == ')' || ch == ']') {
                boolean hasMatchingOpen = false;
                if (parens != null) {
                    for (ParensSuperscript.ParenRegion item : parens) {
                        if (item != null && item.closeChar == i) {
                            hasMatchingOpen = true;
                            break;
                        }
                    }
                }
                if (!hasMatchingOpen) {
                    i++;
                    continue;
                }
            }

            curX += getCharWidth(g2, font, fm, text.charAt(i));
            i++;
        }
        return new int[] { curX, 0, fm.getAscent(), fm.getDescent() };
    }

    public static MathLayout computeLayout(Graphics2D g2, String text, Font font) {
        if (text == null) text = "";
        clearCache();
        FractionsSuperscript.FractionRegion[] fractions = (text.indexOf('/') != -1) ? FractionsSuperscript.findRegions(text) : EMPTY_FRACTIONS;
        RootsSuperscript.RootRegion[] roots = (text.indexOf('√') != -1) ? RootsSuperscript.findRegions(text) : EMPTY_ROOTS;
        ExponentsSuperscript.PowerRegion[] powers = (text.indexOf('^') != -1) ? ExponentsSuperscript.findRegions(text) : EMPTY_POWERS;
        IntegralsSuperscript.IntegralRegion[] integrals = (text.indexOf('∫') != -1) ? IntegralsSuperscript.findRegions(text) : EMPTY_INTEGRALS;
        SummatoriesSuperScript.SumRegion[] summations = hasSumChar(text) ? SummatoriesSuperScript.findRegions(text) : EMPTY_SUMS;
        SubscriptsSuperscript.SubscriptRegion[] subscripts = (text.indexOf('_') != -1)
                ? SubscriptsSuperscript.findRegions(text, integrals, summations, powers) : EMPTY_SUBSCRIPTS;
        LogsSuperScript.LogRegion[] logs = (text.contains("log") || text.contains("ln")) ? LogsSuperScript.findRegions(text) : EMPTY_LOGS;
        DerivativesSuperScript.DerivativeRegion[] derivatives = (text.contains("d") || text.contains("∂")) ? DerivativesSuperScript.findRegions(text) : EMPTY_DERIVS;
        FloorAndCeilingSuperScript.FloorCeilRegion[] floorCeils = (text.indexOf('⌊') != -1 || text.indexOf('⌈') != -1 || text.contains("floor") || text.contains("ceil"))
                ? FloorAndCeilingSuperScript.findRegions(text, fractions, roots, powers, integrals, summations, logs, derivatives) : EMPTY_FLOORS;
        AbsoluteValueSuperScript.AbsoluteValueRegion[] absoluteValues = (text.indexOf('|') != -1)
                ? AbsoluteValueSuperScript.findRegions(text, fractions, roots, powers, integrals, summations, logs, derivatives) : EMPTY_ABS;
        ParensSuperscript.ParenRegion[] parens = (text.indexOf('(') != -1 || text.indexOf('[') != -1)
                ? ParensSuperscript.findRegions(text, fractions, roots, powers, integrals, summations, logs, derivatives) : EMPTY_PARENS;

        RegionContext rc = new RegionContext();
        rc.text = text;
        rc.fractions = fractions;
        rc.roots = roots;
        rc.powers = powers;
        rc.integrals = integrals;
        rc.summations = summations;
        rc.subscripts = subscripts;
        rc.logs = logs;
        rc.derivatives = derivatives;
        rc.floorCeils = floorCeils;
        rc.absoluteValues = absoluteValues;
        rc.parens = parens;
        CURRENT_REGIONS.set(rc);

        Graphics2D targetG2 = (g2 != null) ? g2 : FALLBACK_G2;
        Font targetFont = (font != null) ? font : new Font(Font.MONOSPACED, Font.PLAIN, 14);
        targetG2.setFont(targetFont);
        applyRenderingHints(targetG2);

        int totalW = renderSubString(targetG2, text, 0, text.length(), 0, 0, targetFont, false,
                fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, 0, -1, -1);
        int maxAscent = getRegionAscent(targetG2, text, 0, text.length(), targetFont,
                fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, 0);
        int maxDescent = getRegionDescent(targetG2, text, 0, text.length(), targetFont,
                fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, 0);

        return new MathLayout(text, targetFont, fractions, roots, powers, subscripts, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, totalW, maxAscent, maxDescent);
    }

    public static int renderMathString(Graphics2D g2, String text, int x, int baselineY, Font font, boolean draw) {
        if (text == null || text.isEmpty()) return 0;
        MathLayout layout = computeLayout(g2, text, font);
        return renderSubString(g2, text, 0, text.length(), x, baselineY, font, draw,
                layout.fractions, layout.roots, layout.powers, layout.integrals, layout.summations, layout.logs, layout.derivatives, layout.floorCeils, layout.absoluteValues, layout.parens, 0, -1, -1);
    }

    public static int getMainBaseline(int height, Insets insets, MathLayout layout) {
        if (insets == null) insets = new Insets(0, 0, 0, 0);
        int viewHeight = height - insets.top - insets.bottom;
        int mathHeight = layout != null ? (layout.maxAscent + layout.maxDescent) : 20;
        int ascent = layout != null ? layout.maxAscent : 15;
        return insets.top + 8 + Math.max(0, (viewHeight - (mathHeight + 16)) / 2) + ascent;
    }

    public static int findClosestCaretOffset(Graphics2D g2, MathLayout layout, int targetX, int targetY, int mainBase) {
        if (layout == null || layout.text.isEmpty()) return 0;
        FontMetrics fm = g2 != null ? g2.getFontMetrics(layout.font) : null;
        int defaultAscent = fm != null ? fm.getAscent() : 14;

        java.util.List<Integer> allowed = new java.util.ArrayList<>();
        for (int i = 0; i <= layout.text.length(); i++) {
            if (!MathNavigation.isForbiddenCaret(i, layout.fractions, layout.roots, layout.powers, layout.subscripts, layout.integrals, layout.summations, layout.logs, layout.derivatives)) {
                allowed.add(i);
            }
        }
        if (allowed.isEmpty()) return 0;

        // 1. Direct character hit-testing: if click is physically on any character cell
        int directHit = -1;
        int directWidth = Integer.MAX_VALUE;
        for (int a = 0; a < allowed.size() - 1; a++) {
            int p1 = allowed.get(a);
            int p2 = allowed.get(a + 1);
            int[] ca = layout.getCaretCoord(g2, p1);
            int[] cb = layout.getCaretCoord(g2, p2);
            if (ca == null || cb == null) continue;
            if (Math.abs(ca[1] - cb[1]) > 2) continue; // must be on same baseline
            int xL = Math.min(ca[0], cb[0]);
            int xR = Math.max(ca[0], cb[0]);
            int w = xR - xL;
            if (w <= 0 || w > 45) continue; // must be a realistic character cell
            int yT = mainBase + ca[1] - Math.max(ca[2], cb[2]) - 4;
            int yB = mainBase + ca[1] + Math.max(ca[3], cb[3]) + 4;

            if (targetX >= xL && targetX <= xR && targetY >= yT && targetY <= yB) {
                if (w < directWidth) {
                    directWidth = w;
                    directHit = (targetX < (xL + xR) / 2) ? p1 : p2;
                }
            }
        }
        if (directHit != -1) {
            return directHit;
        }

        // 2. Vertical segment distance hit-testing across all allowed offsets
        int bestOffset = allowed.get(0);
        double bestDist = Double.MAX_VALUE;
        for (int idx = 0; idx < allowed.size(); idx++) {
            int i = allowed.get(idx);
            int[] ci = layout.getCaretCoord(g2, i);
            if (ci != null) {
                int cx = ci[0];
                int cy = mainBase + ci[1];
                int yTop = cy - ci[2];
                int yBottom = cy + ci[3];

                double dy;
                if (targetY >= yTop && targetY <= yBottom) {
                    dy = 0;
                } else if (targetY < yTop) {
                    dy = (yTop - targetY) * 3.5;
                } else {
                    dy = (targetY - yBottom) * 3.5;
                }

                double dx = targetX - cx;
                double dist = dx * dx + dy * dy;
                if (dist < bestDist) {
                    bestDist = dist;
                    bestOffset = i;
                }
            }
        }
        return bestOffset;
    }

    /**
     * Single source of truth for the math rendering hints.
     * <p>
     * Text is rendered with grayscale antialiasing (never LCD subpixel): the math
     * content is drawn into transparent ARGB layers whose background is not known
     * at render time, so subpixel filtering would produce coloured fringes along
     * the glyph edges (the "aliasing / rainbow text" artifact). Grayscale AA is
     * also noticeably cheaper to composite on every repaint.
     * <p>
     * Geometry (lines, fraction bars, radical hooks, braces) uses pure antialiasing
     * with fractional metrics so that 1px strokes stay crisp and glyph advances
     * stay stable between layout and paint.
     */
    public static void applyRenderingHints(Graphics2D g2) {
        if (g2 == null) {
            return;
        }
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
    }

    /**
     * Hints used when blitting the cached field image onto the screen. Keeping the
     * bitmap pass-through (nearest neighbour, no AA) avoids re-filtering the whole
     * cached glyph raster on every repaint.
     */
    public static void applyBlitHints(Graphics2D g2) {
        if (g2 == null) {
            return;
        }
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        // QUALITY, non SPEED: la cache del testo e' un TYPE_INT_ARGB trasparente, e
        // copiarla con alpha "speed" arrotonda brutalmente il canale alpha di ogni
        // glifo => il testo sfuma nel fondo (era il "testo slavato" dei campi).
        // NEAREST_NEIGHBOR va bene: la copia e' 1:1, nessun ridimensionamento.
        g2.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION,
                RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
    }

    public static void paintField(Graphics2D g2, JTextField field, MathLayout layout,
                                  boolean fakeCaretVisible, Color caretColor) {
        if (field == null || layout == null) return;

        int w = field.getWidth();
        int h = field.getHeight();

        if (field.isOpaque()) {
            Color bg = field.getBackground();
            if (bg != null && !Color.WHITE.equals(bg)) {
                g2.setColor(bg);
                g2.fillRect(0, 0, w, h);
            }
        }

        Insets insets = field.getInsets();
        int startX = insets.left + 2;
        int mainBase = getMainBaseline(h, insets, layout);

        int selStart = field.getSelectionStart();
        int selEnd = field.getSelectionEnd();
        boolean hasSel = (selStart >= 0 && selEnd > selStart);
        Color fg = field.getForeground();

        applyRenderingHints(g2);
        // DOPO applyRenderingHints, che lo sovrascriverebbe: la scala di grigi su
        // fondo scuro lascia i bordi dei glifi a meta' alpha => testo slavato.
        // Il subpixel LCD li rende netti. Qui e' l'unico punto che conta davvero,
        // perche' paintField e' quello che disegna il testo in tutti i campi.
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g2.setFont(layout.font);
        g2.setColor(fg);

        renderSubString(g2, layout.text, 0, layout.text.length(), startX, mainBase,
                layout.font, true, layout.fractions, layout.roots, layout.powers, layout.integrals, layout.summations, layout.logs,
                layout.derivatives, layout.floorCeils, layout.absoluteValues, layout.parens,
                0, hasSel ? selStart : -1, hasSel ? selEnd : -1);

        if (field.hasFocus() && fakeCaretVisible) {
            int caret = field.getCaretPosition();
            int[] ci = layout.getCaretCoord(g2, caret);
            if (ci != null) {
                int cx = startX + ci[0];
                int cy = mainBase + ci[1];
                g2.setColor(caretColor != null ? caretColor : Color.WHITE);
                g2.drawLine(cx, cy - ci[2], cx, cy + ci[3]);
            }
        }
    }

    public static void paintFieldWithoutCaret(Graphics2D g2, JTextField field, MathLayout layout) {
        paintField(g2, field, layout, false, null);
    }

    public static void paintCaretOnly(Graphics2D g2, JTextField field, MathLayout layout, Color caretColor) {
        if (field == null || layout == null || !field.hasFocus()) return;
        Insets insets = field.getInsets();
        int startX = insets.left + 2;
        int mainBase = getMainBaseline(field.getHeight(), insets, layout);
        int caret = field.getCaretPosition();
        int[] ci = layout.getCaretCoord(g2, caret);
        if (ci != null) {
            int cx = startX + ci[0];
            int cy = mainBase + ci[1];
            g2.setColor(caretColor != null ? caretColor : Color.WHITE);
            g2.drawLine(cx, cy - ci[2], cx, cy + ci[3]);
        }
    }
}
