package com.spectragraph.superscript;

/**
 * Handles directional caret navigation (Left, Right, Up, Down) and forbidden caret
 * boundary skipping across non-linear mathematical structures.
 */
public class MathNavigation {

    public static boolean isForbiddenCaret(int c,
            FractionsSuperscript.FractionRegion[] fractions,
            RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers,
            IntegralsSuperscript.IntegralRegion[] integrals) {
        return isForbiddenCaret(c, fractions, roots, powers, integrals, null, null);
    }

    public static boolean isForbiddenCaret(int c,
            FractionsSuperscript.FractionRegion[] fractions,
            RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers,
            IntegralsSuperscript.IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations) {
        return isForbiddenCaret(c, fractions, roots, powers, integrals, summations, null);
    }

    public static boolean isForbiddenCaret(int c,
            FractionsSuperscript.FractionRegion[] fractions,
            RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers,
            IntegralsSuperscript.IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations,
            LogsSuperScript.LogRegion[] logs) {
        return isForbiddenCaret(c, fractions, roots, powers, integrals, summations, logs, null);
    }

    public static boolean isForbiddenCaret(int c,
            FractionsSuperscript.FractionRegion[] fractions,
            RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers,
            IntegralsSuperscript.IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations,
            LogsSuperScript.LogRegion[] logs,
            DerivativesSuperScript.DerivativeRegion[] derivatives) {
        return isForbiddenCaret(c, fractions, roots, powers, null, integrals, summations, logs, derivatives);
    }

    public static boolean isForbiddenCaret(int c,
            FractionsSuperscript.FractionRegion[] fractions,
            RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers,
            SubscriptsSuperscript.SubscriptRegion[] subscripts,
            IntegralsSuperscript.IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations,
            LogsSuperScript.LogRegion[] logs,
            DerivativesSuperScript.DerivativeRegion[] derivatives) {
        return FractionsSuperscript.isForbiddenCaret(c, fractions)
                || RootsSuperscript.isForbiddenCaret(c, roots)
                || ExponentsSuperscript.isForbiddenCaret(c, powers)
                || SubscriptsSuperscript.isForbiddenCaret(c, subscripts)
                || IntegralsSuperscript.isForbiddenCaret(c, integrals)
                || SummatoriesSuperScript.isForbiddenCaret(c, summations)
                || LogsSuperScript.isForbiddenCaret(c, logs)
                || DerivativesSuperScript.isForbiddenCaret(c, derivatives);
    }

    public static int getNextAllowedCaret(int position, String text,
            FractionsSuperscript.FractionRegion[] fractions,
            RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers,
            IntegralsSuperscript.IntegralRegion[] integrals) {
        return getNextAllowedCaret(position, text, fractions, roots, powers, integrals, null, null, null);
    }

    public static int getNextAllowedCaret(int position, String text,
            FractionsSuperscript.FractionRegion[] fractions,
            RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers,
            IntegralsSuperscript.IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations) {
        return getNextAllowedCaret(position, text, fractions, roots, powers, integrals, summations, null, null);
    }

    public static int getNextAllowedCaret(int position, String text,
            FractionsSuperscript.FractionRegion[] fractions,
            RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers,
            IntegralsSuperscript.IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations,
            LogsSuperScript.LogRegion[] logs) {
        return getNextAllowedCaret(position, text, fractions, roots, powers, integrals, summations, logs, null);
    }

    public static int getNextAllowedCaret(int position, String text,
            FractionsSuperscript.FractionRegion[] fractions,
            RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers,
            IntegralsSuperscript.IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations,
            LogsSuperScript.LogRegion[] logs,
            DerivativesSuperScript.DerivativeRegion[] derivatives) {
        return getNextAllowedCaret(position, text, fractions, roots, powers, null, integrals, summations, logs, derivatives);
    }

    public static int getNextAllowedCaret(int position, String text,
            FractionsSuperscript.FractionRegion[] fractions,
            RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers,
            SubscriptsSuperscript.SubscriptRegion[] subscripts,
            IntegralsSuperscript.IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations,
            LogsSuperScript.LogRegion[] logs,
            DerivativesSuperScript.DerivativeRegion[] derivatives) {
        if (text == null || text.isEmpty()) return position;
        int p = position;
        while (p < text.length() && isForbiddenCaret(p, fractions, roots, powers, subscripts, integrals, summations, logs, derivatives)) {
            p++;
        }
        return p;
    }

    public static int navigateLeft(String text, int caret,
            FractionsSuperscript.FractionRegion[] fractions,
            RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers,
            IntegralsSuperscript.IntegralRegion[] integrals) {
        return navigateLeft(text, caret, fractions, roots, powers, null, integrals, null, null, null);
    }

    public static int navigateLeft(String text, int caret,
            FractionsSuperscript.FractionRegion[] fractions,
            RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers,
            IntegralsSuperscript.IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations) {
        return navigateLeft(text, caret, fractions, roots, powers, null, integrals, summations, null, null);
    }

    public static int navigateLeft(String text, int caret,
            FractionsSuperscript.FractionRegion[] fractions,
            RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers,
            IntegralsSuperscript.IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations,
            LogsSuperScript.LogRegion[] logs) {
        return navigateLeft(text, caret, fractions, roots, powers, null, integrals, summations, logs, null);
    }

    public static int navigateLeft(String text, int caret,
            FractionsSuperscript.FractionRegion[] fractions,
            RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers,
            IntegralsSuperscript.IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations,
            LogsSuperScript.LogRegion[] logs,
            DerivativesSuperScript.DerivativeRegion[] derivatives) {
        return navigateLeft(text, caret, fractions, roots, powers, null, integrals, summations, logs, derivatives);
    }

    public static int navigateLeft(String text, int caret,
            FractionsSuperscript.FractionRegion[] fractions,
            RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers,
            SubscriptsSuperscript.SubscriptRegion[] subscripts,
            IntegralsSuperscript.IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations,
            LogsSuperScript.LogRegion[] logs,
            DerivativesSuperScript.DerivativeRegion[] derivatives) {
        if (text == null) return -1;

        if (integrals != null) {
            for (IntegralsSuperscript.IntegralRegion it : integrals) {
                if (it == null) continue;
                java.util.List<IntegralsSuperscript.IntegralSign> signList = (it.signs != null && !it.signs.isEmpty())
                        ? it.signs
                        : java.util.Collections.singletonList(new IntegralsSuperscript.IntegralSign(it.intChar, it.upperOpen, it.upperClose, it.lowerOpen, it.lowerClose));
                IntegralsSuperscript.IntegralSign lastSign = signList.get(signList.size() - 1);
                boolean emptyLimits = (lastSign.upperClose == lastSign.upperOpen + 1 && lastSign.lowerClose == lastSign.lowerOpen + 1);
                int gapStart = (lastSign.lowerClose != -1 ? lastSign.lowerClose + 1 : (lastSign.upperClose != -1 ? lastSign.upperClose + 1 : lastSign.intChar + 1));
                if (emptyLimits && caret == gapStart) {
                    return it.intChar;
                }
                if (it.parenOpen != -1 && (caret == it.parenOpen || caret == it.parenOpen + 1)) {
                    if (lastSign.lowerClose != -1 && lastSign.lowerClose > lastSign.lowerOpen + 1) {
                        return lastSign.lowerClose;
                    } else if (lastSign.upperClose != -1 && lastSign.upperClose > lastSign.upperOpen + 1) {
                        return lastSign.upperClose;
                    }
                }
            }
        }

        int c = caret - 1;
        while (c >= 0) {
            if (!isForbiddenCaret(c, fractions, roots, powers, subscripts, integrals, summations, logs, derivatives)) {
                return c;
            }
            c--;
        }
        return -1;
    }

    public static int navigateRight(String text, int caret,
            FractionsSuperscript.FractionRegion[] fractions,
            RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers,
            IntegralsSuperscript.IntegralRegion[] integrals) {
        return navigateRight(text, caret, fractions, roots, powers, null, integrals, null, null, null);
    }

    public static int navigateRight(String text, int caret,
            FractionsSuperscript.FractionRegion[] fractions,
            RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers,
            IntegralsSuperscript.IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations) {
        return navigateRight(text, caret, fractions, roots, powers, null, integrals, summations, null, null);
    }

    public static int navigateRight(String text, int caret,
            FractionsSuperscript.FractionRegion[] fractions,
            RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers,
            IntegralsSuperscript.IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations,
            LogsSuperScript.LogRegion[] logs) {
        return navigateRight(text, caret, fractions, roots, powers, null, integrals, summations, logs, null);
    }

    public static int navigateRight(String text, int caret,
            FractionsSuperscript.FractionRegion[] fractions,
            RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers,
            IntegralsSuperscript.IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations,
            LogsSuperScript.LogRegion[] logs,
            DerivativesSuperScript.DerivativeRegion[] derivatives) {
        return navigateRight(text, caret, fractions, roots, powers, null, integrals, summations, logs, derivatives);
    }

    public static int navigateRight(String text, int caret,
            FractionsSuperscript.FractionRegion[] fractions,
            RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers,
            SubscriptsSuperscript.SubscriptRegion[] subscripts,
            IntegralsSuperscript.IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations,
            LogsSuperScript.LogRegion[] logs,
            DerivativesSuperScript.DerivativeRegion[] derivatives) {
        if (text == null) return -1;

        if (fractions != null) {
            for (int k = fractions.length - 1; k >= 0; k--) {
                FractionsSuperscript.FractionRegion f = fractions[k];
                if (f == null) continue;
                if (caret == f.numClose) {
                    int next = f.denClose + 1;
                    return getNextAllowedCaret(next, text, fractions, roots, powers, subscripts, integrals, summations, logs, derivatives);
                }
            }
        }

        if (integrals != null) {
            for (IntegralsSuperscript.IntegralRegion it : integrals) {
                if (it == null) continue;
                java.util.List<IntegralsSuperscript.IntegralSign> signList = (it.signs != null && !it.signs.isEmpty())
                        ? it.signs
                        : java.util.Collections.singletonList(new IntegralsSuperscript.IntegralSign(it.intChar, it.upperOpen, it.upperClose, it.lowerOpen, it.lowerClose));
                IntegralsSuperscript.IntegralSign lastSign = signList.get(signList.size() - 1);
                boolean emptyLimits = (lastSign.upperClose == lastSign.upperOpen + 1 && lastSign.lowerClose == lastSign.lowerOpen + 1);
                int gapStart = (lastSign.lowerClose != -1 ? lastSign.lowerClose + 1 : (lastSign.upperClose != -1 ? lastSign.upperClose + 1 : lastSign.intChar + 1));
                if (emptyLimits && caret == it.intChar) {
                    return getNextAllowedCaret(gapStart, text, fractions, roots, powers, subscripts, integrals, summations, logs, derivatives);
                }
                if (it.parenOpen != -1 && (caret == lastSign.lowerClose || (lastSign.lowerClose == -1 && caret == lastSign.upperClose))) {
                    return getNextAllowedCaret(it.parenOpen + 1, text, fractions, roots, powers, subscripts, integrals, summations, logs, derivatives);
                }
            }
        }

        int c = caret + 1;
        while (c <= text.length()) {
            if (!isForbiddenCaret(c, fractions, roots, powers, subscripts, integrals, summations, logs, derivatives)) {
                return c;
            }
            c++;
        }
        return -1;
    }

    public static int navigateDown(String text, int caret,
            FractionsSuperscript.FractionRegion[] fractions,
            RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers,
            IntegralsSuperscript.IntegralRegion[] integrals) {
        return navigateDown(text, caret, fractions, roots, powers, integrals, null, null);
    }

    public static int navigateDown(String text, int caret,
            FractionsSuperscript.FractionRegion[] fractions,
            RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers,
            IntegralsSuperscript.IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations) {
        return navigateDown(text, caret, fractions, roots, powers, integrals, summations, null);
    }

    public static int navigateDown(String text, int caret,
            FractionsSuperscript.FractionRegion[] fractions,
            RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers,
            IntegralsSuperscript.IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations,
            LogsSuperScript.LogRegion[] logs) {
        return navigateDown(text, caret, fractions, roots, powers, integrals, summations, logs, null);
    }

    public static int navigateDown(String text, int caret,
            FractionsSuperscript.FractionRegion[] fractions,
            RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers,
            IntegralsSuperscript.IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations,
            LogsSuperScript.LogRegion[] logs,
            DerivativesSuperScript.DerivativeRegion[] derivatives) {
        return navigateDown(text, caret, fractions, roots, powers, null, integrals, summations, logs, derivatives);
    }

    public static int navigateDown(String text, int caret,
            FractionsSuperscript.FractionRegion[] fractions,
            RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers,
            SubscriptsSuperscript.SubscriptRegion[] subscripts,
            IntegralsSuperscript.IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations,
            LogsSuperScript.LogRegion[] logs,
            DerivativesSuperScript.DerivativeRegion[] derivatives) {
        if (text == null) return -1;
        int bestLen = Integer.MAX_VALUE;
        int targetCaret = -1;

        // Subscripts: navigate into them
        if (subscripts != null) {
            for (SubscriptsSuperscript.SubscriptRegion s : subscripts) {
                if (s == null) continue;
                if (caret == s.subChar) {
                    targetCaret = (s.openParen != -1 ? s.openParen + 1 : s.subChar + 1);
                }
            }
        }

        // Derivatives
        if (derivatives != null) {
            for (DerivativesSuperScript.DerivativeRegion dr : derivatives) {
                if (dr == null) continue;
                if (caret == dr.derivStart) {
                    if (dr.varChar != -1) {
                        targetCaret = dr.varChar + 1;
                    } else if (dr.denDChar != -1) {
                        targetCaret = dr.denDChar + 1;
                    }
                }
            }
        }

        // Exponents: from inside exponent → go to base level (right after the base variable/number)
        if (powers != null) {
            for (ExponentsSuperscript.PowerRegion p : powers) {
                int start = p.openParen != -1 ? p.openParen : p.caretChar;
                if (caret > start && caret <= p.closeParen) {
                    int len = p.closeParen - start;
                    if (len < bestLen) {
                        bestLen = len;
                        // Go to the base of this exponent (right after the base number/variable)
                        targetCaret = p.caretChar;
                    }
                }
            }
        }

        // Fractions: numerator → fraction line → denominator
        if (fractions != null) {
            for (FractionsSuperscript.FractionRegion f : fractions) {
                if (f == null) continue;
                // If in numerator and not inside an exponent with a closer target:
                if (caret > f.numOpen && caret <= f.numClose) {
                    boolean insideExp = false;
                    if (powers != null) {
                        for (ExponentsSuperscript.PowerRegion p : powers) {
                            if (p != null && p.caretChar > f.numOpen && p.closeParen <= f.numClose) {
                                int start = p.openParen != -1 ? p.openParen : p.caretChar;
                                if (caret > start && caret <= p.closeParen) {
                                    insideExp = true;
                                    break;
                                }
                            }
                        }
                    }
                    if (!insideExp) {
                        int len = f.numClose - f.numOpen;
                        if (len < bestLen) {
                            bestLen = len;
                            // Go to the fraction line (baseline position after fraction)
                            targetCaret = f.denClose + 1;
                        }
                    }
                } else if (caret == f.denClose + 1) {
                    // At fraction line -> descend into denominator!
                    int len = f.denClose - f.denOpen;
                    if (len < bestLen) {
                        bestLen = len;
                        targetCaret = (f.denClose > f.denOpen + 1) ? f.denClose : f.denOpen + 1;
                    }
                } else if (caret > f.denOpen && caret <= f.denClose) {
                    // Inside denominator:
                    // If the character before caret can have a subscript, do not jump out;
                    // let MathTextField.navigateDown add _() on the first press!
                    char prev = (caret > 0 && caret <= text.length()) ? text.charAt(caret - 1) : '\0';
                    if (!SubscriptsSuperscript.isValidSubscriptBase(prev)) {
                        int len = f.denClose - f.denOpen;
                        if (len < bestLen) {
                            bestLen = len;
                            RootsSuperscript.RootRegion insideRoot = null;
                            if (roots != null) {
                                for (RootsSuperscript.RootRegion r : roots) {
                                    if (r.bracketOpen != -1 && f.denClose < r.bracketClose && f.numOpen > r.bracketOpen) {
                                        insideRoot = r;
                                        break;
                                    }
                                }
                            }
                            if (insideRoot != null) {
                                targetCaret = insideRoot.parenOpen + 1;
                            } else {
                                targetCaret = f.denClose + 1;
                            }
                        }
                    }
                }
            }
        }

        // Roots: from index → radicand
        if (roots != null) {
            for (RootsSuperscript.RootRegion r : roots) {
                if (r.bracketOpen != -1 && caret > r.bracketOpen && caret <= r.bracketClose) {
                    int len = r.bracketClose - r.bracketOpen;
                    if (len < bestLen) {
                        bestLen = len;
                        targetCaret = r.parenOpen + 1;
                    }
                }
            }
        }

        // Integrals: upper → lower → argument
        if (integrals != null) {
            for (IntegralsSuperscript.IntegralRegion it : integrals) {
                if (it == null) continue;
                java.util.List<IntegralsSuperscript.IntegralSign> signList = (it.signs != null && !it.signs.isEmpty())
                        ? it.signs
                        : java.util.Collections.singletonList(new IntegralsSuperscript.IntegralSign(it.intChar, it.upperOpen, it.upperClose, it.lowerOpen, it.lowerClose));
                for (IntegralsSuperscript.IntegralSign s : signList) {
                    // Upper limit → lower limit → argument
                    if (s.upperOpen != -1 && caret > s.upperOpen && caret <= s.upperClose) {
                        int len = s.upperClose - s.upperOpen;
                        if (len < bestLen) {
                            bestLen = len;
                            targetCaret = (s.lowerOpen != -1) ? (s.lowerOpen + 1) : (it.parenOpen != -1 ? it.parenOpen + 1 : s.upperClose + 1);
                        }
                    } else if (s.lowerOpen != -1 && caret > s.lowerOpen && caret <= s.lowerClose) {
                        int len = s.lowerClose - s.lowerOpen;
                        if (len < bestLen) {
                            bestLen = len;
                            targetCaret = (it.parenOpen != -1) ? (it.parenOpen + 1) : s.lowerClose + 1;
                        }
                    }
                }
                // Argument of integral → exit integral (go after dxEnd or parenClose)
                if (it.parenOpen != -1 && caret > it.parenOpen && caret <= it.parenClose) {
                    // Check if we're truly at the argument level, not inside a nested structure
                    boolean insideNested = false;
                    if (fractions != null) {
                        for (FractionsSuperscript.FractionRegion f : fractions) {
                            if (f.numOpen > it.parenOpen && f.denClose < it.parenClose
                                    && caret > f.numOpen && caret <= f.denClose) {
                                insideNested = true;
                                break;
                            }
                        }
                    }
                    if (!insideNested) {
                        int len = it.parenClose - it.parenOpen;
                        if (len < bestLen) {
                            bestLen = len;
                            targetCaret = (it.dxEnd != -1) ? it.dxEnd : (it.parenClose != -1 ? it.parenClose + 1 : text.length());
                        }
                    }
                } else if (caret >= it.intChar && (it.parenOpen == -1 || caret <= it.parenOpen)) {
                    int len = (it.parenClose != -1 ? it.parenClose : text.length()) - it.intChar;
                    if (len < bestLen) {
                        bestLen = len;
                        IntegralsSuperscript.IntegralSign targetSign = findClosestIntegralSign(signList, caret);
                        if (targetSign != null && targetSign.lowerOpen != -1) {
                            targetCaret = targetSign.lowerOpen + 1;
                        }
                    }
                }
            }
        }

        // Summations: ∑ symbol → upper → lower → argument
        if (summations != null) {
            for (SummatoriesSuperScript.SumRegion s : summations) {
                if (s == null) continue;
                // Upper → lower
                if (s.upperOpen != -1 && caret > s.upperOpen && caret <= s.upperClose) {
                    int len = s.upperClose - s.upperOpen;
                    if (len < bestLen) {
                        bestLen = len;
                        targetCaret = (s.lowerOpen != -1) ? (s.lowerOpen + 1) : (s.parenOpen != -1 ? s.parenOpen + 1 : s.sumEnd);
                    }
                // Lower → argument
                } else if (s.lowerOpen != -1 && caret > s.lowerOpen && caret <= s.lowerClose) {
                    int len = s.lowerClose - s.lowerOpen;
                    if (len < bestLen) {
                        bestLen = len;
                        targetCaret = (s.parenOpen != -1) ? (s.parenOpen + 1) : s.sumEnd;
                    }
                // Argument → exit summation
                } else if (s.parenOpen != -1 && caret > s.parenOpen && caret <= s.parenClose) {
                    boolean insideNested = false;
                    if (fractions != null) {
                        for (FractionsSuperscript.FractionRegion f : fractions) {
                            if (f.numOpen > s.parenOpen && f.denClose < s.parenClose
                                    && caret > f.numOpen && caret <= f.denClose) {
                                insideNested = true;
                                break;
                            }
                        }
                    }
                    if (!insideNested) {
                        int len = s.parenClose - s.parenOpen;
                        if (len < bestLen) {
                            bestLen = len;
                            targetCaret = (s.parenClose != -1 ? s.parenClose + 1 : text.length());
                        }
                    }
                // On ∑ symbol → upper (or lower if no upper, or argument)
                } else if (caret >= s.sumStart && caret <= s.sumEnd) {
                    int len = s.sumEnd - s.sumStart;
                    if (len < bestLen) {
                        bestLen = len;
                        targetCaret = (s.upperOpen != -1) ? (s.upperOpen + 1) : (s.lowerOpen != -1 ? s.lowerOpen + 1 : s.sumEnd);
                    }
                }
            }
        }

        // Logarithms: log keyword → base → argument → exit
        if (logs != null) {
            for (LogsSuperScript.LogRegion lg : logs) {
                if (lg == null) continue;
                // From log keyword → base
                if (lg.bracketOpen != -1 && caret >= lg.logStart && caret <= lg.logStart + 3
                        && (lg.parenOpen == -1 || caret < lg.parenOpen)) {
                    int len = (lg.getEnd(text.length())) - lg.logStart;
                    if (len < bestLen) {
                        bestLen = len;
                        targetCaret = lg.bracketOpen + 1;
                    }
                // From base → argument
                } else if (lg.bracketOpen != -1 && caret > lg.bracketOpen && caret <= lg.bracketClose) {
                    int len = lg.bracketClose - lg.bracketOpen;
                    if (len < bestLen) {
                        bestLen = len;
                        targetCaret = (lg.parenOpen != -1) ? (lg.parenOpen + 1) : lg.getEnd(text.length());
                    }
                // From argument → exit logarithm
                } else if (lg.parenOpen != -1 && caret > lg.parenOpen && caret <= lg.parenClose) {
                    int len = lg.parenClose - lg.parenOpen;
                    if (len < bestLen) {
                        bestLen = len;
                        targetCaret = lg.getEnd(text.length());
                    }
                }
            }
        }

        // Generic functions with parens: func(arg)
        if (targetCaret == -1 && text != null) {
            int pOpen = text.indexOf('(', caret);
            if (pOpen != -1) {
                int startLetters = caret;
                while (startLetters > 0 && Character.isLetter(text.charAt(startLetters - 1))) {
                    startLetters--;
                }
                boolean isFunc = true;
                for (int k = startLetters; k < pOpen; k++) {
                    if (!Character.isLetter(text.charAt(k))) { isFunc = false; break; }
                }
                if (isFunc && pOpen > startLetters) {
                    targetCaret = pOpen + 1;
                }
            }
            if (targetCaret == -1) {
                int openIdx = text.lastIndexOf('(', caret > 0 ? caret - 1 : 0);
                if (openIdx > 0 && Character.isLetter(text.charAt(openIdx - 1))) {
                    int closeIdx = MathRenderEngine.findMatchingChar(text, openIdx, '(', ')');
                    if (closeIdx != -1 && caret <= closeIdx) {
                        targetCaret = closeIdx + 1;
                    }
                }
            }
        }

        return targetCaret;
    }

    public static int navigateUp(String text, int caret,
            FractionsSuperscript.FractionRegion[] fractions,
            RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers,
            IntegralsSuperscript.IntegralRegion[] integrals) {
        return navigateUp(text, caret, fractions, roots, powers, integrals, null, null);
    }

    public static int navigateUp(String text, int caret,
            FractionsSuperscript.FractionRegion[] fractions,
            RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers,
            IntegralsSuperscript.IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations) {
        return navigateUp(text, caret, fractions, roots, powers, integrals, summations, null);
    }

    public static int navigateUp(String text, int caret,
            FractionsSuperscript.FractionRegion[] fractions,
            RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers,
            IntegralsSuperscript.IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations,
            LogsSuperScript.LogRegion[] logs) {
        return navigateUp(text, caret, fractions, roots, powers, integrals, summations, logs, null);
    }

    public static int navigateUp(String text, int caret,
            FractionsSuperscript.FractionRegion[] fractions,
            RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers,
            IntegralsSuperscript.IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations,
            LogsSuperScript.LogRegion[] logs,
            DerivativesSuperScript.DerivativeRegion[] derivatives) {
        return navigateUp(text, caret, fractions, roots, powers, null, integrals, summations, logs, derivatives);
    }

    public static int navigateUp(String text, int caret,
            FractionsSuperscript.FractionRegion[] fractions,
            RootsSuperscript.RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers,
            SubscriptsSuperscript.SubscriptRegion[] subscripts,
            IntegralsSuperscript.IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations,
            LogsSuperScript.LogRegion[] logs,
            DerivativesSuperScript.DerivativeRegion[] derivatives) {
        if (text == null) return -1;
        int bestLen = Integer.MAX_VALUE;
        int targetCaret = -1;

        if (subscripts != null) {
            for (SubscriptsSuperscript.SubscriptRegion s : subscripts) {
                if (s == null) continue;
                int start = (s.openParen != -1 ? s.openParen : s.subChar);
                if (caret > start && caret <= s.closeParen) {
                    int len = s.closeParen - start;
                    if (len < bestLen) {
                        bestLen = len;
                        targetCaret = (s.closeParen != -1 && s.closeParen < text.length() ? s.closeParen + 1 : s.subChar);
                    }
                }
            }
        }

        if (derivatives != null) {
            for (DerivativesSuperScript.DerivativeRegion dr : derivatives) {
                if (dr == null) continue;
                if (caret == dr.varChar || caret == dr.varChar + 1 || (dr.varChar == -1 && caret == dr.denDChar + 1)) {
                    targetCaret = dr.derivStart;
                }
            }
        }

        if (powers != null) {
            for (ExponentsSuperscript.PowerRegion p : powers) {
                int pEnd = p.closeParen != -1 ? p.closeParen + 1 : p.closeParen;
                if (caret == pEnd) {
                    int len = 1;
                    if (len < bestLen) {
                        bestLen = len;
                        targetCaret = (p.closeParen != -1 ? p.closeParen : p.caretChar + 1);
                    }
                } else if (caret == p.caretChar || (p.caretChar > 0 && caret == p.caretChar - 1)) {
                    int len = (caret == p.caretChar) ? 1 : 2;
                    if (len < bestLen) {
                        bestLen = len;
                        // If the exponent has an inner power, go to the first inner power's base
                        ExponentsSuperscript.PowerRegion firstInner = null;
                        if (powers != null) {
                            for (ExponentsSuperscript.PowerRegion inner : powers) {
                                if (inner != null && inner != p && inner.caretChar > p.caretChar && inner.closeParen <= p.closeParen) {
                                    if (firstInner == null || inner.caretChar < firstInner.caretChar) {
                                        firstInner = inner;
                                    }
                                }
                            }
                        }
                        if (firstInner != null) {
                            targetCaret = firstInner.caretChar;
                        } else {
                            targetCaret = (p.closeParen != -1 ? p.closeParen : p.caretChar + 1);
                        }
                    }
                }
            }
        }

        if (fractions != null) {
            for (FractionsSuperscript.FractionRegion f : fractions) {
                if (f == null) continue;
                // In denominator -> go UP to fraction line
                if (caret > f.denOpen && caret <= f.denClose) {
                    int len = f.denClose - f.denOpen;
                    if (len < bestLen) {
                        bestLen = len;
                        targetCaret = f.denClose + 1;
                    }
                } else if (caret == f.denClose + 1) {
                    // At fraction line -> go UP to numerator
                    int len = f.numClose - f.numOpen;
                    if (len < bestLen) {
                        bestLen = len;
                        // If numerator has an exponent, go to the base of that exponent
                        ExponentsSuperscript.PowerRegion firstPow = null;
                        if (powers != null) {
                            for (ExponentsSuperscript.PowerRegion p : powers) {
                                if (p != null && p.caretChar > f.numOpen && p.closeParen <= f.numClose) {
                                    if (firstPow == null || p.caretChar < firstPow.caretChar) {
                                        firstPow = p;
                                    }
                                }
                            }
                        }
                        if (firstPow != null) {
                            targetCaret = firstPow.caretChar;
                        } else {
                            targetCaret = f.numClose;
                        }
                    }
                } else if (caret > f.numOpen && caret <= f.numClose) {
                    // In numerator -> check if there is an exponent in numerator
                    if (powers != null) {
                        for (ExponentsSuperscript.PowerRegion p : powers) {
                            if (p != null && p.caretChar > f.numOpen && p.closeParen <= f.numClose) {
                                int len = p.closeParen - p.caretChar;
                                if (len < bestLen) {
                                    bestLen = len;
                                    targetCaret = (p.closeParen != -1 ? p.closeParen : p.caretChar + 1);
                                }
                            }
                        }
                    }
                }
            }
        }

        if (roots != null) {
            for (RootsSuperscript.RootRegion r : roots) {
                if (r.bracketOpen != -1) {
                    boolean nearRoot = (caret == r.sqrtChar || caret == r.parenOpen
                            || (caret > r.parenOpen && caret <= r.parenOpen + 2)
                            || (caret > r.parenOpen && caret <= r.parenClose && (r.parenClose - r.parenOpen <= 3)));
                    if (nearRoot) {
                        int len = r.parenClose - r.parenOpen;
                        if (len < bestLen) {
                            bestLen = len;
                            targetCaret = r.bracketOpen + 1;
                        }
                    }
                }
            }
        }

        if (integrals != null) {
            for (IntegralsSuperscript.IntegralRegion it : integrals) {
                if (it == null) continue;
                java.util.List<IntegralsSuperscript.IntegralSign> signList = (it.signs != null && !it.signs.isEmpty())
                        ? it.signs
                        : java.util.Collections.singletonList(new IntegralsSuperscript.IntegralSign(it.intChar, it.upperOpen, it.upperClose, it.lowerOpen, it.lowerClose));
                for (IntegralsSuperscript.IntegralSign s : signList) {
                    if (s.lowerOpen != -1 && caret > s.lowerOpen && caret <= s.lowerClose) {
                        int len = s.lowerClose - s.lowerOpen;
                        if (len < bestLen) {
                            bestLen = len;
                            targetCaret = (s.upperOpen != -1) ? (s.upperOpen + 1) : s.lowerOpen;
                        }
                    }
                }
                if (caret > it.parenOpen && caret <= it.parenClose) {
                    int len = it.parenClose - it.parenOpen;
                    if (len < bestLen) {
                        bestLen = len;
                        IntegralsSuperscript.IntegralSign targetSign = findClosestIntegralSign(signList, caret);
                        if (targetSign != null && targetSign.lowerOpen != -1) {
                            targetCaret = targetSign.lowerOpen + 1;
                        } else if (targetSign != null && targetSign.upperOpen != -1) {
                            targetCaret = targetSign.upperOpen + 1;
                        }
                    }
                } else if (caret >= it.intChar && (it.parenOpen == -1 || caret <= it.parenOpen)) {
                    int len = (it.parenClose != -1 ? it.parenClose : text.length()) - it.intChar;
                    if (len < bestLen) {
                        bestLen = len;
                        IntegralsSuperscript.IntegralSign targetSign = findClosestIntegralSign(signList, caret);
                        if (targetSign != null && targetSign.lowerOpen != -1) {
                            targetCaret = targetSign.lowerOpen + 1;
                        } else if (targetSign != null && targetSign.upperOpen != -1) {
                            targetCaret = targetSign.upperOpen + 1;
                        }
                    }
                }
            }
        }

        if (summations != null) {
            for (SummatoriesSuperScript.SumRegion s : summations) {
                if (s == null) continue;
                if (s.lowerOpen != -1 && caret > s.lowerOpen && caret <= s.lowerClose) {
                    int len = s.lowerClose - s.lowerOpen;
                    if (len < bestLen) {
                        bestLen = len;
                        targetCaret = (s.upperOpen != -1) ? (s.upperOpen + 1) : s.sumStart;
                    }
                } else if (s.upperOpen != -1 && caret > s.upperOpen && caret <= s.upperClose) {
                    int len = s.upperClose - s.upperOpen;
                    if (len < bestLen) {
                        bestLen = len;
                        targetCaret = s.sumStart;
                    }
                } else if (caret >= s.sumStart && caret <= s.sumEnd) {
                    int len = s.sumEnd - s.sumStart;
                    if (len < bestLen) {
                        bestLen = len;
                        targetCaret = (s.upperOpen != -1) ? (s.upperOpen + 1) : s.sumEnd;
                    }
                } else if (caret > s.parenOpen && caret <= s.parenClose) {
                    boolean nearSum = (caret <= s.parenOpen + 2 || (s.parenClose - s.parenOpen <= 3));
                    if (nearSum) {
                        int len = s.parenClose - s.parenOpen;
                        if (len < bestLen) {
                            bestLen = len;
                            targetCaret = (s.lowerOpen != -1) ? (s.lowerOpen + 1) : ((s.upperOpen != -1) ? (s.upperOpen + 1) : s.sumStart);
                        }
                    }
                }
            }
        }

        if (logs != null) {
            for (LogsSuperScript.LogRegion lg : logs) {
                if (lg == null) continue;
                // From argument → base
                if (lg.parenOpen != -1 && caret > lg.parenOpen && caret <= lg.parenClose) {
                    int len = lg.parenClose - lg.parenOpen;
                    if (len < bestLen) {
                        bestLen = len;
                        targetCaret = (lg.bracketOpen != -1) ? (lg.bracketClose) : lg.logStart;
                    }
                // From base → log keyword
                } else if (lg.bracketOpen != -1 && caret > lg.bracketOpen && caret <= lg.bracketClose) {
                    int len = lg.bracketClose - lg.bracketOpen;
                    if (len < bestLen) {
                        bestLen = len;
                        targetCaret = lg.logStart;
                    }
                }
            }
        }

        // Generic functions with parens: func(arg)
        if (targetCaret == -1 && text != null) {
            int openIdx = text.lastIndexOf('(', caret > 0 ? caret - 1 : 0);
            if (openIdx > 0 && Character.isLetter(text.charAt(openIdx - 1))) {
                int closeIdx = MathRenderEngine.findMatchingChar(text, openIdx, '(', ')');
                if (closeIdx != -1 && caret <= closeIdx + 1) {
                    int startLetters = openIdx - 1;
                    while (startLetters > 0 && Character.isLetter(text.charAt(startLetters - 1))) {
                        startLetters--;
                    }
                    targetCaret = startLetters;
                }
            }
        }

        return targetCaret;
    }

    public static IntegralsSuperscript.IntegralSign findClosestIntegralSign(java.util.List<IntegralsSuperscript.IntegralSign> signList, int caret) {
        if (signList == null || signList.isEmpty()) return null;
        IntegralsSuperscript.IntegralSign best = null;
        for (int i = signList.size() - 1; i >= 0; i--) {
            IntegralsSuperscript.IntegralSign s = signList.get(i);
            if (caret >= s.intChar) {
                best = s;
                break;
            }
        }
        if (best == null) best = signList.get(0);
        if (best.upperOpen == -1 && best.lowerOpen == -1) {
            for (int i = signList.size() - 1; i >= 0; i--) {
                IntegralsSuperscript.IntegralSign s = signList.get(i);
                if (s.upperOpen != -1 || s.lowerOpen != -1) {
                    return s;
                }
            }
        }
        return best;
    }
}
