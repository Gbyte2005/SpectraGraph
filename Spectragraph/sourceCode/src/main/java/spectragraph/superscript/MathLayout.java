package com.spectragraph.superscript;

import java.awt.Font;
import java.awt.Graphics2D;

/**
 * Encapsulates the measured geometry, detected non-linear mathematical regions,
 * and lazy caret coordinates for a rendered mathematical formula.
 */
public class MathLayout {

    public final String text;
    public final Font font;
    public final FractionsSuperscript.FractionRegion[] fractions;
    public final RootsSuperscript.RootRegion[] roots;
    public final ExponentsSuperscript.PowerRegion[] powers;
    public final SubscriptsSuperscript.SubscriptRegion[] subscripts;
    public final IntegralsSuperscript.IntegralRegion[] integrals;
    public final SummatoriesSuperScript.SumRegion[] summations;
    public final LogsSuperScript.LogRegion[] logs;
    public final DerivativesSuperScript.DerivativeRegion[] derivatives;
    public final FloorAndCeilingSuperScript.FloorCeilRegion[] floorCeils;
    public final AbsoluteValueSuperScript.AbsoluteValueRegion[] absoluteValues;
    public final ParensSuperscript.ParenRegion[] parens;
    public final int totalWidth;
    public final int maxAscent;
    public final int maxDescent;
    private final int[][] cachedCaretCoords;

    public MathLayout(String text, Font font,
                      FractionsSuperscript.FractionRegion[] fractions,
                      RootsSuperscript.RootRegion[] roots,
                      ExponentsSuperscript.PowerRegion[] powers,
                      IntegralsSuperscript.IntegralRegion[] integrals,
                      ParensSuperscript.ParenRegion[] parens,
                      int totalWidth, int maxAscent, int maxDescent) {
        this(text, font, fractions, roots, powers, integrals, null, null, null, null, null, parens, totalWidth, maxAscent, maxDescent);
    }

    public MathLayout(String text, Font font,
                      FractionsSuperscript.FractionRegion[] fractions,
                      RootsSuperscript.RootRegion[] roots,
                      ExponentsSuperscript.PowerRegion[] powers,
                      IntegralsSuperscript.IntegralRegion[] integrals,
                      SummatoriesSuperScript.SumRegion[] summations,
                      ParensSuperscript.ParenRegion[] parens,
                      int totalWidth, int maxAscent, int maxDescent) {
        this(text, font, fractions, roots, powers, integrals, summations, null, null, null, null, parens, totalWidth, maxAscent, maxDescent);
    }

    public MathLayout(String text, Font font,
                      FractionsSuperscript.FractionRegion[] fractions,
                      RootsSuperscript.RootRegion[] roots,
                      ExponentsSuperscript.PowerRegion[] powers,
                      IntegralsSuperscript.IntegralRegion[] integrals,
                      SummatoriesSuperScript.SumRegion[] summations,
                      LogsSuperScript.LogRegion[] logs,
                      ParensSuperscript.ParenRegion[] parens,
                      int totalWidth, int maxAscent, int maxDescent) {
        this(text, font, fractions, roots, powers, integrals, summations, logs, null, null, null, parens, totalWidth, maxAscent, maxDescent);
    }

    public MathLayout(String text, Font font,
                      FractionsSuperscript.FractionRegion[] fractions,
                      RootsSuperscript.RootRegion[] roots,
                      ExponentsSuperscript.PowerRegion[] powers,
                      IntegralsSuperscript.IntegralRegion[] integrals,
                      SummatoriesSuperScript.SumRegion[] summations,
                      LogsSuperScript.LogRegion[] logs,
                      DerivativesSuperScript.DerivativeRegion[] derivatives,
                      ParensSuperscript.ParenRegion[] parens,
                      int totalWidth, int maxAscent, int maxDescent) {
        this(text, font, fractions, roots, powers, integrals, summations, logs, derivatives, null, null, parens, totalWidth, maxAscent, maxDescent);
    }

    public MathLayout(String text, Font font,
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
                      int totalWidth, int maxAscent, int maxDescent) {
        this(text, font, fractions, roots, powers, null, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, totalWidth, maxAscent, maxDescent);
    }

    public MathLayout(String text, Font font,
                      FractionsSuperscript.FractionRegion[] fractions,
                      RootsSuperscript.RootRegion[] roots,
                      ExponentsSuperscript.PowerRegion[] powers,
                      SubscriptsSuperscript.SubscriptRegion[] subscripts,
                      IntegralsSuperscript.IntegralRegion[] integrals,
                      SummatoriesSuperScript.SumRegion[] summations,
                      LogsSuperScript.LogRegion[] logs,
                      DerivativesSuperScript.DerivativeRegion[] derivatives,
                      FloorAndCeilingSuperScript.FloorCeilRegion[] floorCeils,
                      AbsoluteValueSuperScript.AbsoluteValueRegion[] absoluteValues,
                      ParensSuperscript.ParenRegion[] parens,
                      int totalWidth, int maxAscent, int maxDescent) {
        this.text = text != null ? text : "";
        this.font = font;
        this.fractions = fractions != null ? fractions : new FractionsSuperscript.FractionRegion[0];
        this.roots = roots != null ? roots : new RootsSuperscript.RootRegion[0];
        this.powers = powers != null ? powers : new ExponentsSuperscript.PowerRegion[0];
        this.subscripts = subscripts != null ? subscripts : new SubscriptsSuperscript.SubscriptRegion[0];
        this.integrals = integrals != null ? integrals : new IntegralsSuperscript.IntegralRegion[0];
        this.summations = summations != null ? summations : new SummatoriesSuperScript.SumRegion[0];
        this.logs = logs != null ? logs : new LogsSuperScript.LogRegion[0];
        this.derivatives = derivatives != null ? derivatives : new DerivativesSuperScript.DerivativeRegion[0];
        this.floorCeils = floorCeils != null ? floorCeils : new FloorAndCeilingSuperScript.FloorCeilRegion[0];
        this.absoluteValues = absoluteValues != null ? absoluteValues : new AbsoluteValueSuperScript.AbsoluteValueRegion[0];
        this.parens = parens != null ? parens : new ParensSuperscript.ParenRegion[0];
        this.totalWidth = totalWidth;
        this.maxAscent = maxAscent;
        this.maxDescent = maxDescent;
        this.cachedCaretCoords = new int[this.text.length() + 1][];
    }

    public boolean hasSpecialMath() {
        if (fractions.length > 0 || roots.length > 0 || powers.length > 0 || subscripts.length > 0
                || integrals.length > 0 || summations.length > 0 || logs.length > 0
                || derivatives.length > 0 || floorCeils.length > 0 || absoluteValues.length > 0) {
            return true;
        }
        // Also return true if font cannot display all characters (e.g. set symbols ℝℂℤℕℚ)
        // so MathRenderEngine.paintField is used with proper font fallback
        if (font != null && text != null) {
            for (int i = 0; i < text.length(); i++) {
                if (!font.canDisplay(text.charAt(i))) {
                    return true;
                }
            }
        }
        return false;
    }

    public int[] getCaretCoord(Graphics2D g2, int offset) {
        if (offset < 0 || offset >= cachedCaretCoords.length) {
            return null;
        }
        if (cachedCaretCoords[offset] != null) {
            return cachedCaretCoords[offset];
        }
        int[] ci = MathRenderEngine.getCaretInfoSubString(g2, text, 0, text.length(), offset, font,
                fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, 0);
        cachedCaretCoords[offset] = ci;
        return ci;
    }
}
