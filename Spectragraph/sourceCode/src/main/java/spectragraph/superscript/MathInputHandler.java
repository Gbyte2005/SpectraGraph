package com.spectragraph.superscript;

import javax.swing.text.DocumentFilter.FilterBypass;
import javax.swing.text.BadLocationException;
import javax.swing.SwingUtilities;
import java.util.function.Consumer;

/**
 * Handles structural editing, smart unwrap/deletion, and operator insertion
 * (e.g. '/', '^', 'int', 'sqrt') for mathematical expressions.
 */
public class MathInputHandler {

    public static boolean handleRemove(FilterBypass fb, int offset, int length, String text,
                                       Consumer<Integer> setCaretAction, Runnable triggerResize)
            throws BadLocationException {
        if (length != 1 || offset < 0 || offset >= text.length()) {
            return false;
        }

        char ch = text.charAt(offset);

        FractionsSuperscript.FractionRegion[] fractions = FractionsSuperscript.findRegions(text);
        RootsSuperscript.RootRegion[] roots = RootsSuperscript.findRegions(text);
        ExponentsSuperscript.PowerRegion[] powers = ExponentsSuperscript.findRegions(text);
        IntegralsSuperscript.IntegralRegion[] integrals = IntegralsSuperscript.findRegions(text);
        SummatoriesSuperScript.SumRegion[] summations = SummatoriesSuperScript.findRegions(text);
        LogsSuperScript.LogRegion[] logs = LogsSuperScript.findRegions(text);
        DerivativesSuperScript.DerivativeRegion[] derivatives = DerivativesSuperScript.findRegions(text);
        SubscriptsSuperscript.SubscriptRegion[] subscripts = SubscriptsSuperscript.findRegions(text, integrals, summations);

        // 1. Fractions deletion
        if (fractions != null) {
            for (FractionsSuperscript.FractionRegion f : fractions) {
                if (offset == f.denClose) {
                    int numLen = f.numClose - f.numOpen - 1;
                    int denLen = f.denClose - f.denOpen - 1;
                    if ((numLen > 0 && denLen > 0) || (numLen == 0 && denLen == 0)) {
                        int finalCaret = f.numOpen;
                        fb.remove(f.numOpen, f.denClose - f.numOpen + 1);
                        triggerResize.run();
                        SwingUtilities.invokeLater(() -> setCaretAction.accept(finalCaret));
                        return true;
                    } else {
                        int finalCaret = (numLen > 0) ? (f.numOpen + numLen) : (f.numOpen + denLen);
                        fb.remove(f.denClose, 1);
                        fb.remove(f.slashChar, f.denOpen - f.slashChar + 1);
                        fb.remove(f.numClose, 1);
                        fb.remove(f.numOpen, 1);
                        triggerResize.run();
                        SwingUtilities.invokeLater(() -> setCaretAction.accept(finalCaret));
                        return true;
                    }
                } else if (offset == f.denOpen) {
                    int numLen = f.numClose - f.numOpen - 1;
                    int denLen = f.denClose - f.denOpen - 1;
                    if (denLen == 0) {
                        if (numLen > 0) {
                            int finalCaret = f.numOpen + numLen;
                            fb.remove(f.denClose, 1);
                            fb.remove(f.slashChar, f.denOpen - f.slashChar + 1);
                            fb.remove(f.numClose, 1);
                            fb.remove(f.numOpen, 1);
                            triggerResize.run();
                            SwingUtilities.invokeLater(() -> setCaretAction.accept(finalCaret));
                            return true;
                        } else {
                            int finalCaret = f.numOpen;
                            fb.remove(f.numOpen, f.denClose - f.numOpen + 1);
                            triggerResize.run();
                            SwingUtilities.invokeLater(() -> setCaretAction.accept(finalCaret));
                            return true;
                        }
                    } else {
                        final int pos = f.numClose;
                        SwingUtilities.invokeLater(() -> setCaretAction.accept(pos));
                        return true;
                    }
                } else if (offset == f.numOpen) {
                    int numLen = f.numClose - f.numOpen - 1;
                    int denLen = f.denClose - f.denOpen - 1;
                    if (numLen == 0) {
                        if (denLen > 0) {
                            int finalCaret = f.numOpen;
                            fb.remove(f.denClose, 1);
                            fb.remove(f.slashChar, f.denOpen - f.slashChar + 1);
                            fb.remove(f.numClose, 1);
                            fb.remove(f.numOpen, 1);
                            triggerResize.run();
                            SwingUtilities.invokeLater(() -> setCaretAction.accept(finalCaret));
                            return true;
                        } else {
                            int finalCaret = f.numOpen;
                            fb.remove(f.numOpen, f.denClose - f.numOpen + 1);
                            triggerResize.run();
                            SwingUtilities.invokeLater(() -> setCaretAction.accept(finalCaret));
                            return true;
                        }
                    } else {
                        final int pos = f.numOpen;
                        SwingUtilities.invokeLater(() -> setCaretAction.accept(pos));
                        return true;
                    }
                } else if (offset == f.numClose) {
                    final int pos = f.numClose;
                    SwingUtilities.invokeLater(() -> setCaretAction.accept(pos));
                    return true;
                } else if (offset == f.slashChar) {
                    int denClose = f.denClose;
                    int denOpen = f.denOpen;
                    int slash = f.slashChar;
                    int numClose = f.numClose;
                    int numOpen = f.numOpen;
                    int numLen = numClose - numOpen - 1;
                    int finalCaret = numOpen + numLen;
                    fb.remove(denClose, 1);
                    fb.remove(slash, denOpen - slash + 1);
                    fb.remove(numClose, 1);
                    fb.remove(numOpen, 1);
                    triggerResize.run();
                    SwingUtilities.invokeLater(() -> setCaretAction.accept(finalCaret));
                    return true;
                }
            }
        }

        // 2. Roots deletion
        if (ch != '/' && roots != null) {
            for (RootsSuperscript.RootRegion r : roots) {
                int removeStart = (r.bracketOpen != -1 && r.bracketOpen < r.sqrtChar) ? r.bracketOpen : r.sqrtChar;
                if (offset == r.parenOpen) {
                    int prefixLen = r.parenOpen - removeStart + 1;
                    int finalCaret = removeStart;
                    fb.remove(r.parenClose, 1);
                    fb.remove(removeStart, prefixLen);
                    triggerResize.run();
                    SwingUtilities.invokeLater(() -> setCaretAction.accept(finalCaret));
                    return true;
                } else if (offset == r.parenClose) {
                    if (r.parenClose == r.parenOpen + 1) {
                        fb.remove(removeStart, r.parenClose - removeStart + 1);
                        triggerResize.run();
                        SwingUtilities.invokeLater(() -> setCaretAction.accept(removeStart));
                        return true;
                    } else {
                        final int pos = offset;
                        SwingUtilities.invokeLater(() -> setCaretAction.accept(pos));
                        return true;
                    }
                } else if (offset == r.bracketOpen || offset == r.bracketClose) {
                    int targetCaret = offset;
                    if (offset == r.bracketOpen)
                        targetCaret = offset + 1;
                    final int pos = targetCaret;
                    SwingUtilities.invokeLater(() -> setCaretAction.accept(pos));
                    return true;
                } else if (offset == r.sqrtChar) {
                    int prefixLen = r.parenOpen - removeStart + 1;
                    int finalCaret = removeStart;
                    fb.remove(r.parenClose, 1);
                    fb.remove(removeStart, prefixLen);
                    triggerResize.run();
                    SwingUtilities.invokeLater(() -> setCaretAction.accept(finalCaret));
                    return true;
                }
            }
        }

        // 3. Powers deletion
        if (ch != '/' && ch != '√' && powers != null) {
            for (ExponentsSuperscript.PowerRegion p : powers) {
                if (offset == p.openParen) {
                    int finalCaret = p.caretChar;
                    fb.remove(p.closeParen, 1);
                    fb.remove(p.caretChar, 2);
                    triggerResize.run();
                    SwingUtilities.invokeLater(() -> setCaretAction.accept(finalCaret));
                    return true;
                } else if (offset == p.closeParen) {
                    if (p.closeParen == p.openParen + 1) {
                        fb.remove(p.caretChar, p.closeParen - p.caretChar + 1);
                        triggerResize.run();
                        SwingUtilities.invokeLater(() -> setCaretAction.accept(p.caretChar));
                        return true;
                    } else {
                        final int pos = offset;
                        SwingUtilities.invokeLater(() -> setCaretAction.accept(pos));
                        return true;
                    }
                } else if (offset == p.caretChar) {
                    int finalCaret = offset;
                    fb.remove(p.closeParen, 1);
                    fb.remove(p.caretChar, 2);
                    triggerResize.run();
                    SwingUtilities.invokeLater(() -> setCaretAction.accept(finalCaret));
                    return true;
                }
            }
        }

        // 3b. Subscripts deletion
        if (ch != '/' && ch != '√' && subscripts != null) {
            for (SubscriptsSuperscript.SubscriptRegion s : subscripts) {
                if (offset == s.openParen) {
                    int finalCaret = s.subChar;
                    fb.remove(s.closeParen, 1);
                    fb.remove(s.subChar, 2);
                    triggerResize.run();
                    SwingUtilities.invokeLater(() -> setCaretAction.accept(finalCaret));
                    return true;
                } else if (offset == s.closeParen) {
                    if (s.closeParen == s.openParen + 1) {
                        fb.remove(s.subChar, s.closeParen - s.subChar + 1);
                        triggerResize.run();
                        SwingUtilities.invokeLater(() -> setCaretAction.accept(s.subChar));
                        return true;
                    } else {
                        final int pos = offset;
                        SwingUtilities.invokeLater(() -> setCaretAction.accept(pos));
                        return true;
                    }
                } else if (offset == s.subChar) {
                    int finalCaret = offset;
                    fb.remove(s.closeParen, 1);
                    fb.remove(s.subChar, 2);
                    triggerResize.run();
                    SwingUtilities.invokeLater(() -> setCaretAction.accept(finalCaret));
                    return true;
                }
            }
        }

        // 4. Integrals deletion
        if (integrals != null) {
            for (IntegralsSuperscript.IntegralRegion it : integrals) {
                int upperLen = (it.upperOpen != -1 && it.upperClose != -1) ? (it.upperClose - it.upperOpen - 1) : 0;
                int lowerLen = (it.lowerOpen != -1 && it.lowerClose != -1) ? (it.lowerClose - it.lowerOpen - 1) : 0;
                int intLen = (it.parenOpen != -1 && it.parenClose != -1) ? (it.parenClose - it.parenOpen - 1) : 0;

                if (offset == it.upperOpen) {
                    if (upperLen == 0 && lowerLen == 0 && intLen == 0) {
                        int end = it.dxEnd != -1 ? it.dxEnd : (it.parenClose != -1 ? it.parenClose + 1 : it.intChar + 1);
                        fb.remove(it.intChar, end - it.intChar);
                        triggerResize.run();
                        SwingUtilities.invokeLater(() -> setCaretAction.accept(it.intChar));
                        return true;
                    } else {
                        SwingUtilities.invokeLater(() -> setCaretAction.accept(it.intChar));
                        return true;
                    }
                } else if (offset == it.lowerOpen) {
                    final int target = (it.upperOpen != -1) ? it.upperClose : it.intChar;
                    SwingUtilities.invokeLater(() -> setCaretAction.accept(target));
                    return true;
                } else if (offset == it.parenOpen) {
                    final int target = (it.lowerOpen != -1) ? it.lowerClose
                            : ((it.upperOpen != -1) ? it.upperClose : it.intChar);
                    SwingUtilities.invokeLater(() -> setCaretAction.accept(target));
                    return true;
                } else if (offset == it.intChar) {
                    if (intLen <= 0 && upperLen <= 0 && lowerLen <= 0) {
                        int end = it.dxEnd != -1 ? it.dxEnd : (it.parenClose != -1 ? it.parenClose + 1 : it.intChar + 1);
                        fb.remove(it.intChar, end - it.intChar);
                        triggerResize.run();
                        SwingUtilities.invokeLater(() -> setCaretAction.accept(it.intChar));
                        return true;
                    } else {
                        int finalCaret = it.intChar;
                        if (it.dxStart != -1 && it.dxEnd > it.dxStart) {
                            fb.remove(it.dxStart, it.dxEnd - it.dxStart);
                        }
                        fb.remove(it.parenClose, 1);
                        fb.remove(it.intChar, it.parenOpen - it.intChar + 1);
                        triggerResize.run();
                        SwingUtilities.invokeLater(() -> setCaretAction.accept(finalCaret));
                        return true;
                    }
                } else if (offset == it.parenClose) {
                    if (it.parenClose == it.parenOpen + 1 && upperLen == 0 && lowerLen == 0) {
                        int end = it.dxEnd != -1 ? it.dxEnd : (it.parenClose + 1);
                        fb.remove(it.intChar, end - it.intChar);
                        triggerResize.run();
                        SwingUtilities.invokeLater(() -> setCaretAction.accept(it.intChar));
                        return true;
                    } else {
                        final int pos = offset;
                        SwingUtilities.invokeLater(() -> setCaretAction.accept(pos));
                        return true;
                    }
                } else if (it.dxStart != -1 && it.dxEnd > it.dxStart && offset >= it.dxStart && offset < it.dxEnd) {
                    java.util.List<IntegralsSuperscript.DiffVarRegion> diffs = IntegralsSuperscript.getDiffVarRegions(text, it);
                    for (IntegralsSuperscript.DiffVarRegion dvr : diffs) {
                        // Deleting variable of integration (e.g. Backspace on 'x' in 'dx')
                        if (dvr.varEnd > dvr.varStart && offset >= dvr.varStart && offset < dvr.varEnd) {
                            fb.remove(offset, length);
                            triggerResize.run();
                            final int target = offset;
                            setCaretAction.accept(target);
                            SwingUtilities.invokeLater(() -> setCaretAction.accept(target));
                            return true;
                        }
                        // Deleting 'd' of differential
                        if (offset == dvr.dPos) {
                            if (dvr.varEnd == dvr.varStart) {
                                fb.remove(dvr.dPos, 1);
                            } else {
                                fb.remove(dvr.dPos, dvr.varEnd - dvr.dPos);
                            }
                            triggerResize.run();
                            final int target = it.parenClose;
                            setCaretAction.accept(target);
                            SwingUtilities.invokeLater(() -> setCaretAction.accept(target));
                            return true;
                        }
                    }
                } else if (offset == it.upperClose && upperLen == 0) {
                    final int pos = it.intChar;
                    SwingUtilities.invokeLater(() -> setCaretAction.accept(pos));
                    return true;
                } else if (offset == it.lowerClose && lowerLen == 0) {
                    final int pos = (it.upperOpen != -1) ? it.upperClose : it.intChar;
                    SwingUtilities.invokeLater(() -> setCaretAction.accept(pos));
                    return true;
                }
            }
        }

        // 4b. Summations deletion
        if (summations != null) {
            for (SummatoriesSuperScript.SumRegion s : summations) {
                if (s == null) continue;
                int lowerLen = (s.lowerOpen != -1 && s.lowerClose != -1) ? (s.lowerClose - s.lowerOpen - 1) : 0;
                int upperLen = (s.upperOpen != -1 && s.upperClose != -1) ? (s.upperClose - s.upperOpen - 1) : 0;
                int argLen = (s.parenOpen != -1 && s.parenClose != -1) ? (s.parenClose - s.parenOpen - 1) : 0;

                int sumEnd = (s.parenClose != -1 ? s.parenClose + 1
                        : (s.lowerClose != -1 ? s.lowerClose + 1
                        : (s.upperClose != -1 ? s.upperClose + 1 : s.sumEnd)));

                if (offset == s.upperOpen) {
                    if (lowerLen == 0 && upperLen == 0 && argLen == 0) {
                        fb.remove(s.sumStart, sumEnd - s.sumStart);
                        triggerResize.run();
                        SwingUtilities.invokeLater(() -> setCaretAction.accept(s.sumStart));
                        return true;
                    } else {
                        SwingUtilities.invokeLater(() -> setCaretAction.accept(s.sumStart));
                        return true;
                    }
                } else if (offset == s.upperClose) {
                    if (upperLen == 0) {
                        SwingUtilities.invokeLater(() -> setCaretAction.accept(s.sumStart));
                        return true;
                    } else {
                        final int pos = offset;
                        SwingUtilities.invokeLater(() -> setCaretAction.accept(pos));
                        return true;
                    }
                } else if (offset == s.lowerOpen) {
                    final int target = (s.upperOpen != -1) ? s.upperClose : s.sumStart;
                    SwingUtilities.invokeLater(() -> setCaretAction.accept(target));
                    return true;
                } else if (offset == s.lowerClose) {
                    if (lowerLen == 0) {
                        final int target = (s.upperOpen != -1) ? s.upperClose : s.sumStart;
                        SwingUtilities.invokeLater(() -> setCaretAction.accept(target));
                        return true;
                    } else {
                        final int pos = offset;
                        SwingUtilities.invokeLater(() -> setCaretAction.accept(pos));
                        return true;
                    }
                } else if (offset == s.parenOpen) {
                    final int target = (s.lowerOpen != -1) ? s.lowerClose : ((s.upperOpen != -1) ? s.upperClose : s.sumStart);
                    SwingUtilities.invokeLater(() -> setCaretAction.accept(target));
                    return true;
                } else if (offset == s.parenClose) {
                    if (lowerLen == 0 && upperLen == 0 && argLen == 0) {
                        fb.remove(s.sumStart, sumEnd - s.sumStart);
                        triggerResize.run();
                        SwingUtilities.invokeLater(() -> setCaretAction.accept(s.sumStart));
                        return true;
                    } else if (argLen == 0) {
                        final int target = (s.lowerOpen != -1) ? s.lowerClose : ((s.upperOpen != -1) ? s.upperClose : s.sumStart);
                        SwingUtilities.invokeLater(() -> setCaretAction.accept(target));
                        return true;
                    } else {
                        final int pos = offset;
                        SwingUtilities.invokeLater(() -> setCaretAction.accept(pos));
                        return true;
                    }
                } else if (offset >= s.sumStart && offset < s.sumEnd) {
                    if (lowerLen <= 0 && upperLen <= 0 && argLen <= 0) {
                        fb.remove(s.sumStart, sumEnd - s.sumStart);
                        triggerResize.run();
                        SwingUtilities.invokeLater(() -> setCaretAction.accept(s.sumStart));
                        return true;
                    } else {
                        if (argLen > 0 && s.parenOpen != -1 && s.parenClose != -1) {
                            fb.remove(s.parenClose, 1);
                            fb.remove(s.sumStart, s.parenOpen - s.sumStart + 1);
                            triggerResize.run();
                            SwingUtilities.invokeLater(() -> setCaretAction.accept(s.sumStart));
                            return true;
                        } else {
                            fb.remove(s.sumStart, sumEnd - s.sumStart);
                            triggerResize.run();
                            SwingUtilities.invokeLater(() -> setCaretAction.accept(s.sumStart));
                            return true;
                        }
                    }
                }
            }
        }

        // 4c. Logs deletion
        if (logs != null) {
            for (LogsSuperScript.LogRegion lg : logs) {
                if (lg == null) continue;
                int baseLen = (lg.bracketOpen != -1 && lg.bracketClose != -1) ? (lg.bracketClose - lg.bracketOpen - 1) : 0;
                int argLen = (lg.parenOpen != -1 && lg.parenClose != -1) ? (lg.parenClose - lg.parenOpen - 1) : 0;
                int logTotalEnd = lg.getEnd(text.length());

                if (offset == lg.bracketOpen) {
                    if (baseLen == 0 && argLen == 0) {
                        fb.remove(lg.logStart, logTotalEnd - lg.logStart);
                        triggerResize.run();
                        SwingUtilities.invokeLater(() -> setCaretAction.accept(lg.logStart));
                        return true;
                    } else {
                        SwingUtilities.invokeLater(() -> setCaretAction.accept(lg.logStart));
                        return true;
                    }
                } else if (offset == lg.bracketClose) {
                    if (baseLen == 0) {
                        SwingUtilities.invokeLater(() -> setCaretAction.accept(lg.logStart));
                        return true;
                    } else {
                        final int pos = offset;
                        SwingUtilities.invokeLater(() -> setCaretAction.accept(pos));
                        return true;
                    }
                } else if (offset == lg.parenOpen) {
                    final int target = (lg.bracketClose != -1) ? lg.bracketClose : lg.logStart;
                    SwingUtilities.invokeLater(() -> setCaretAction.accept(target));
                    return true;
                } else if (offset == lg.parenClose) {
                    if (baseLen == 0 && argLen == 0) {
                        fb.remove(lg.logStart, logTotalEnd - lg.logStart);
                        triggerResize.run();
                        SwingUtilities.invokeLater(() -> setCaretAction.accept(lg.logStart));
                        return true;
                    } else if (argLen == 0) {
                        final int target = (lg.bracketClose != -1) ? lg.bracketClose : lg.logStart;
                        SwingUtilities.invokeLater(() -> setCaretAction.accept(target));
                        return true;
                    } else {
                        final int pos = offset;
                        SwingUtilities.invokeLater(() -> setCaretAction.accept(pos));
                        return true;
                    }
                } else if (offset >= lg.logStart && offset < lg.logEnd) {
                    if (baseLen == 0 && argLen == 0) {
                        fb.remove(lg.logStart, logTotalEnd - lg.logStart);
                        triggerResize.run();
                        SwingUtilities.invokeLater(() -> setCaretAction.accept(lg.logStart));
                        return true;
                    } else if (argLen > 0 && lg.parenOpen != -1 && lg.parenClose != -1) {
                        fb.remove(lg.parenClose, 1);
                        fb.remove(lg.logStart, lg.parenOpen - lg.logStart + 1);
                        triggerResize.run();
                        SwingUtilities.invokeLater(() -> setCaretAction.accept(lg.logStart));
                        return true;
                    } else {
                        fb.remove(lg.logStart, logTotalEnd - lg.logStart);
                        triggerResize.run();
                        SwingUtilities.invokeLater(() -> setCaretAction.accept(lg.logStart));
                        return true;
                    }
                }
            }
        }

        // 4d. Derivatives deletion
        if (derivatives != null) {
            for (DerivativesSuperScript.DerivativeRegion dr : derivatives) {
                if (dr == null) continue;
                int argLen = (dr.parenOpen != -1 && dr.parenClose != -1) ? (dr.parenClose - dr.parenOpen - 1) : 0;
                int derivTotalEnd = dr.getEnd(text.length());

                if (offset == dr.parenOpen) {
                    if (argLen == 0) {
                        int removeStart = dr.varChar != -1 ? dr.varChar : dr.parenOpen;
                        fb.remove(removeStart, derivTotalEnd - removeStart);
                        triggerResize.run();
                        final int target = dr.denDChar != -1 ? dr.denDChar + 1 : dr.derivStart;
                        setCaretAction.accept(target);
                        SwingUtilities.invokeLater(() -> setCaretAction.accept(target));
                        return true;
                    } else {
                        final int target = dr.derivStart;
                        setCaretAction.accept(target);
                        SwingUtilities.invokeLater(() -> setCaretAction.accept(target));
                        return true;
                    }
                } else if (offset == dr.parenClose) {
                    if (argLen == 0) {
                        fb.remove(dr.derivStart, derivTotalEnd - dr.derivStart);
                        triggerResize.run();
                        setCaretAction.accept(dr.derivStart);
                        SwingUtilities.invokeLater(() -> setCaretAction.accept(dr.derivStart));
                        return true;
                    } else {
                        final int pos = offset;
                        setCaretAction.accept(pos);
                        SwingUtilities.invokeLater(() -> setCaretAction.accept(pos));
                        return true;
                    }
                } else if (dr.varChar != -1 && (offset == dr.varChar || offset == dr.denClose || (offset <= dr.varChar && offset + length > dr.varChar))) {
                    if (dr.parenOpen != -1) {
                        if (dr.parenClose != -1 && argLen == 0) {
                            // Argument is empty ()! Remove () and varChar so it reverts to clean (d)/(d) without stray parentheses
                            fb.remove(dr.parenOpen, dr.parenClose - dr.parenOpen + 1);
                        } else if (dr.parenClose == -1) {
                            fb.remove(dr.parenOpen, 1);
                        }
                    }
                    fb.remove(dr.varChar, 1);
                    triggerResize.run();
                    final int target = dr.denDChar != -1 ? dr.denDChar + 1 : dr.varChar;
                    setCaretAction.accept(target);
                    SwingUtilities.invokeLater(() -> setCaretAction.accept(target));
                    return true;
                } else if (dr.varChar == -1 && offset == dr.denDChar) {
                    fb.remove(dr.derivStart, derivTotalEnd - dr.derivStart);
                    triggerResize.run();
                    final int target = dr.derivStart;
                    setCaretAction.accept(target);
                    SwingUtilities.invokeLater(() -> setCaretAction.accept(target));
                    return true;
                } else if (offset >= dr.derivStart && offset < (dr.parenOpen != -1 ? dr.parenOpen : derivTotalEnd)) {
                    if (argLen == 0) {
                        fb.remove(dr.derivStart, derivTotalEnd - dr.derivStart);
                        triggerResize.run();
                        SwingUtilities.invokeLater(() -> setCaretAction.accept(dr.derivStart));
                        return true;
                    } else if (dr.parenOpen != -1 && dr.parenClose != -1) {
                        fb.remove(dr.parenClose, 1);
                        fb.remove(dr.derivStart, dr.parenOpen - dr.derivStart + 1);
                        triggerResize.run();
                        SwingUtilities.invokeLater(() -> setCaretAction.accept(dr.derivStart));
                        return true;
                    } else {
                        fb.remove(dr.derivStart, derivTotalEnd - dr.derivStart);
                        triggerResize.run();
                        SwingUtilities.invokeLater(() -> setCaretAction.accept(dr.derivStart));
                        return true;
                    }
                }
            }
        }

        // 5. Empty delimiters deletion
        if (ch == '(' && offset + 1 < text.length() && text.charAt(offset + 1) == ')') {
            fb.remove(offset, 2);
            triggerResize.run();
            return true;
        }
        if (ch == '[' && offset + 1 < text.length() && text.charAt(offset + 1) == ']') {
            fb.remove(offset, 2);
            triggerResize.run();
            return true;
        }
        if ((ch == '\u230A' || ch == '⌊') && offset + 1 < text.length() && (text.charAt(offset + 1) == '\u230B' || text.charAt(offset + 1) == '⌋')) {
            fb.remove(offset, 2);
            triggerResize.run();
            return true;
        }
        if ((ch == '\u2308' || ch == '⌈') && offset + 1 < text.length() && (text.charAt(offset + 1) == '\u2309' || text.charAt(offset + 1) == '⌉')) {
            fb.remove(offset, 2);
            triggerResize.run();
            return true;
        }
        if (ch == '|' && offset + 1 < text.length() && text.charAt(offset + 1) == '|') {
            fb.remove(offset, 2);
            triggerResize.run();
            return true;
        }

        // 6. Delete paired delimiters (special brackets and bars only; plain parentheses delete individually)
        if (ch == '\u230A' || ch == '⌊') {
            int depth = 1;
            int closeIdx = -1;
            for (int j = offset + 1; j < text.length(); j++) {
                char cj = text.charAt(j);
                if (cj == '\u230A' || cj == '⌊') depth++;
                else if (cj == '\u230B' || cj == '⌋') depth--;
                if (depth == 0) {
                    closeIdx = j;
                    break;
                }
            }
            if (closeIdx != -1) {
                fb.remove(closeIdx, 1);
                fb.remove(offset, 1);
                triggerResize.run();
                final int finalPos = offset;
                SwingUtilities.invokeLater(() -> setCaretAction.accept(finalPos));
                return true;
            }
        }
        if (ch == '\u2308' || ch == '⌈') {
            int depth = 1;
            int closeIdx = -1;
            for (int j = offset + 1; j < text.length(); j++) {
                char cj = text.charAt(j);
                if (cj == '\u2308' || cj == '⌈') depth++;
                else if (cj == '\u2309' || cj == '⌉') depth--;
                if (depth == 0) {
                    closeIdx = j;
                    break;
                }
            }
            if (closeIdx != -1) {
                fb.remove(closeIdx, 1);
                fb.remove(offset, 1);
                triggerResize.run();
                final int finalPos = offset;
                SwingUtilities.invokeLater(() -> setCaretAction.accept(finalPos));
                return true;
            }
        }
        if (ch == '|') {
            AbsoluteValueSuperScript.AbsoluteValueRegion[] avrs = AbsoluteValueSuperScript.findRegions(text, fractions, roots, powers, integrals, summations, logs, derivatives);
            if (avrs != null) {
                for (AbsoluteValueSuperScript.AbsoluteValueRegion avr : avrs) {
                    if (avr != null && avr.openChar == offset) {
                        fb.remove(avr.closeChar, 1);
                        fb.remove(offset, 1);
                        triggerResize.run();
                        final int finalPos = offset;
                        SwingUtilities.invokeLater(() -> setCaretAction.accept(finalPos));
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public static void handleSlashInsertion(javax.swing.text.Document doc, int caret, String text,
                                            Consumer<Integer> setCaretAction, Runnable triggerResize) {
        if (DerivativesSuperScript.isInsideDerivativeOperator(caret, text)) return;
        if (caret > 0) {
            // If caret is explicitly inside subscript parentheses x_(), insert fraction inside subscript
            SubscriptsSuperscript.SubscriptRegion[] subscripts = SubscriptsSuperscript.findRegions(text, null, null);
            if (subscripts != null) {
                for (SubscriptsSuperscript.SubscriptRegion s : subscripts) {
                    if (s == null) continue;
                    if (s.openParen != -1 && caret > s.openParen && caret < s.closeParen) {
                        if (s.closeParen == s.openParen + 1 && caret == s.openParen + 1) {
                            try {
                                doc.insertString(caret, "()/()", null);
                                setCaretAction.accept(caret + 1);
                                triggerResize.run();
                                return;
                            } catch (Exception ignored) {}
                        }
                    }
                }
            }

            boolean hasSpaceBefore = Character.isWhitespace(text.charAt(caret - 1));


            int endToken = caret;
            int start;

            if (hasSpaceBefore) {
                start = caret - 1;
                while (start >= 0 && Character.isWhitespace(text.charAt(start))) {
                    start--;
                }
                if (start >= 0) {
                    char lastChar = text.charAt(start);
                    if (lastChar == '+' || lastChar == '-' || lastChar == '*' || lastChar == '/' || lastChar == '='
                            || lastChar == '(' || lastChar == '[') {
                        start = caret;
                        endToken = caret;
                    } else {
                        endToken = start + 1;
                        int depth = 0;
                        while (start >= 0) {
                            char ch = text.charAt(start);
                            if (ch == ')' || ch == ']') depth++;
                            else if (ch == '(' || ch == '[') depth--;
                            if (depth < 0) break;
                            if (depth == 0 && (ch == '=' || ch == ',' || ch == ';')) break;
                            start--;
                        }
                        start++;
                        while (start < endToken && Character.isWhitespace(text.charAt(start))) {
                            start++;
                        }
                    }
                } else {
                    start = 0;
                    endToken = caret;
                }
            } else {
                start = caret - 1;
                char ch = text.charAt(start);
                if (ch == ')' || ch == ']') {
                    char closeCh = ch;
                    char openCh = (closeCh == ')') ? '(' : '[';
                    int depth = 1;
                    start--;
                    while (start >= 0) {
                        char c = text.charAt(start);
                        if (c == closeCh) depth++;
                        else if (c == openCh) depth--;
                        if (depth == 0) break;
                        start--;
                    }
                    if (start < 0) start = 0;

                    if (start > 0 && (text.charAt(start - 1) == '^' || text.charAt(start - 1) == '_')) {
                        start--;
                        if (start > 0 && (text.charAt(start - 1) == ')' || text.charAt(start - 1) == ']')) {
                            char bClose = text.charAt(start - 1);
                            char bOpen = (bClose == ')') ? '(' : '[';
                            int bDepth = 1;
                            start -= 2;
                            while (start >= 0) {
                                char c = text.charAt(start);
                                if (c == bClose) bDepth++;
                                else if (c == bOpen) bDepth--;
                                if (bDepth == 0) break;
                                start--;
                            }
                            if (start < 0) start = 0;
                        } else {
                            int baseScan = start - 1;
                            while (baseScan >= 0 && (Character.isLetterOrDigit(text.charAt(baseScan))
                                    || text.charAt(baseScan) == '.' || text.charAt(baseScan) == '√')) {
                                baseScan--;
                            }
                            start = baseScan + 1;
                        }
                    } else if (start > 1 && text.charAt(start - 1) == '/' && text.charAt(start - 2) == ')') {
                        start -= 2;
                        char nClose = ')';
                        char nOpen = '(';
                        int nDepth = 1;
                        start--;
                        while (start >= 0) {
                            char c = text.charAt(start);
                            if (c == nClose) nDepth++;
                            else if (c == nOpen) nDepth--;
                            if (nDepth == 0) break;
                            start--;
                        }
                        if (start < 0) start = 0;

                        boolean keepScanning = true;
                        while (keepScanning && start > 0) {
                            keepScanning = false;
                            if (start > 0 && (text.charAt(start - 1) == '^' || text.charAt(start - 1) == '_')) {
                                start--;
                                if (start > 0 && (text.charAt(start - 1) == ')' || text.charAt(start - 1) == ']')) {
                                    char bClose2 = text.charAt(start - 1);
                                    char bOpen2 = (bClose2 == ')') ? '(' : '[';
                                    int bDepth2 = 1;
                                    start -= 2;
                                    while (start >= 0) {
                                        char c = text.charAt(start);
                                        if (c == bClose2) bDepth2++;
                                        else if (c == bOpen2) bDepth2--;
                                        if (bDepth2 == 0) break;
                                        start--;
                                    }
                                    if (start < 0) start = 0;
                                } else {
                                    int baseScan2 = start - 1;
                                    while (baseScan2 >= 0 && (Character.isLetterOrDigit(text.charAt(baseScan2))
                                            || text.charAt(baseScan2) == '.' || text.charAt(baseScan2) == '\u221A')) {
                                        baseScan2--;
                                    }
                                    start = baseScan2 + 1;
                                }
                                keepScanning = true;
                            } else if (start > 1 && text.charAt(start - 1) == '/' && text.charAt(start - 2) == ')') {
                                start -= 2;
                                int nDepth2 = 1;
                                start--;
                                while (start >= 0) {
                                    char c = text.charAt(start);
                                    if (c == ')') nDepth2++;
                                    else if (c == '(') nDepth2--;
                                    if (nDepth2 == 0) break;
                                    start--;
                                }
                                if (start < 0) start = 0;
                                keepScanning = true;
                            } else {
                                int funcScan2 = start - 1;
                                while (funcScan2 >= 0 && (Character.isLetter(text.charAt(funcScan2))
                                        || text.charAt(funcScan2) == '\u221A' || text.charAt(funcScan2) == '\u222B' || text.charAt(funcScan2) == '\u2211' || text.charAt(funcScan2) == '∑' || text.charAt(funcScan2) == 'Σ' || text.charAt(funcScan2) == '\u03A3')) {
                                    funcScan2--;
                                }
                                if (funcScan2 + 1 < start) {
                                    start = funcScan2 + 1;
                                }
                            }
                        }
                    } else {
                        int funcScan = start - 1;
                        while (funcScan >= 0 && (Character.isLetter(text.charAt(funcScan))
                                || text.charAt(funcScan) == '√' || text.charAt(funcScan) == '\u221A' || text.charAt(funcScan) == '∫' || text.charAt(funcScan) == '∑' || text.charAt(funcScan) == '\u2211' || text.charAt(funcScan) == 'Σ' || text.charAt(funcScan) == '\u03A3')) {
                            funcScan--;
                        }
                        start = funcScan + 1;
                        if (start > 0 && text.charAt(start - 1) == ']' && start < text.length() && (text.charAt(start) == '√' || text.charAt(start) == '\u221A')) {
                            int bDepth = 1;
                            int bScan = start - 2;
                            while (bScan >= 0) {
                                if (text.charAt(bScan) == ']') bDepth++;
                                else if (text.charAt(bScan) == '[') bDepth--;
                                if (bDepth == 0) {
                                    start = bScan;
                                    break;
                                }
                                bScan--;
                            }
                        }
                    }
                } else if (Character.isLetterOrDigit(ch) || ch == '.') {
                    int tokenScan = start;
                    while (tokenScan >= 0 && (Character.isLetterOrDigit(text.charAt(tokenScan)) || text.charAt(tokenScan) == '.')) {
                        tokenScan--;
                    }
                    start = tokenScan + 1;
                    if (start > 1 && text.charAt(start - 1) == '_') {
                        int bScan = start - 2;
                        while (bScan >= 0 && (Character.isLetterOrDigit(text.charAt(bScan)) || text.charAt(bScan) == '.')) {
                            bScan--;
                        }
                        start = bScan + 1;
                    }
                    if (start > 1 && text.charAt(start - 1) == '^') {
                        int bScan = start - 2;
                        while (bScan >= 0 && (Character.isLetterOrDigit(text.charAt(bScan)) || text.charAt(bScan) == '.')) {
                            bScan--;
                        }
                        start = bScan + 1;
                    }
                    if (start > 0 && (text.charAt(start - 1) == '√' || text.charAt(start - 1) == '\u221A')) {
                        start--;
                        if (start > 0 && text.charAt(start - 1) == ']') {
                            int bDepth = 1;
                            int bScan = start - 2;
                            while (bScan >= 0) {
                                if (text.charAt(bScan) == ']') bDepth++;
                                else if (text.charAt(bScan) == '[') bDepth--;
                                if (bDepth == 0) {
                                    start = bScan;
                                    break;
                                }
                                bScan--;
                            }
                        }
                    } else if (start > 0 && (text.charAt(start - 1) == '∫' || text.charAt(start - 1) == '∑' || text.charAt(start - 1) == '\u2211' || text.charAt(start - 1) == 'Σ' || text.charAt(start - 1) == '\u03A3')) {
                        start--;
                    }
                } else {
                    start = caret;
                }
                endToken = caret;
            }

            int replaceEnd = endToken;
            String prevToken = (start < endToken) ? text.substring(start, endToken).trim() : "";
            if (prevToken.isEmpty() || prevToken.equals("(") || prevToken.equals("[")) {
                int end = caret;
                int depthF = 0;
                while (end < text.length()) {
                    char ch = text.charAt(end);
                    if (ch == '(' || ch == '[') depthF++;
                    else if (ch == ')' || ch == ']') depthF--;
                    if (depthF < 0) break;
                    if (depthF == 0 && (ch == '=' || ch == ',' || ch == ';')) break;
                    end++;
                }
                String nextToken = text.substring(caret, end).trim();
                if (!nextToken.isEmpty()) {
                    boolean nextWrapped = isBalancedParens(nextToken);
                    try {
                        String replacement = nextWrapped ? ("()/" + nextToken) : ("()/(" + nextToken + ")");
                        doc.remove(caret, end - caret);
                        doc.insertString(caret, replacement, null);
                        setCaretAction.accept(caret + 1);
                    } catch (Exception ignored) {
                    }
                } else {
                    try {
                        doc.insertString(caret, "()/()", null);
                        setCaretAction.accept(caret + 1);
                    } catch (Exception ignored) {
                    }
                }
                triggerResize.run();
                return;
            }

            boolean alreadyWrapped = isBalancedParens(prevToken);
            try {
                String replacement = alreadyWrapped ? (prevToken + "/()") : ("(" + prevToken + ")/()");
                doc.remove(start, replaceEnd - start);
                doc.insertString(start, replacement, null);
                setCaretAction.accept(start + replacement.length() - 1);
            } catch (Exception ignored) {
            }
        } else {
            // caret == 0
            int end = 0;
            int depthF = 0;
            while (end < text.length()) {
                char ch = text.charAt(end);
                if (ch == '(' || ch == '[') depthF++;
                else if (ch == ')' || ch == ']') depthF--;
                if (depthF < 0) break;
                if (depthF == 0 && (ch == '=' || ch == ',' || ch == ';')) break;
                end++;
            }
            String nextToken = text.substring(0, end).trim();
            if (!nextToken.isEmpty()) {
                boolean nextWrapped = isBalancedParens(nextToken);
                try {
                    String replacement = nextWrapped ? ("()/" + nextToken) : ("()/(" + nextToken + ")");
                    doc.remove(0, end);
                    doc.insertString(0, replacement, null);
                    setCaretAction.accept(1);
                } catch (Exception ignored) {
                }
            } else {
                try {
                    doc.insertString(0, "()/()", null);
                    setCaretAction.accept(1);
                } catch (Exception ignored) {
                }
            }
        }
        triggerResize.run();
    }

    private static final java.util.Set<String> BUILT_IN_FUNCTIONS = new java.util.HashSet<>(java.util.Arrays.asList(
        "rect", "sinc", "saw", "tri", "triangle", "sign", "diff", "sqrt",
        "sin", "cos", "tan", "acos", "asin", "atan", "atan2", "sinh", "cosh", "tanh",
        "exp", "log", "log10", "abs", "floor", "ceil", "round", "arccos", "arcsin", "arctan", "ln", "int",
        "re", "im", "arg", "conj",
        "pulse", "trapz", "step", "heaviside", "drcd", "drcc", "\u03B4", "\u0428", "\u0448", "delta", "comb", "shah", "u", "h", "dirac",
        "rand", "randn", "noise", "rep", "per", "sqw", "square",
        "sum", "∑", "\u2211", "Σ", "\u03A3", "Δ", "\u0394", "θ", "\u03B8", "Θ", "\u0398"
    ));

    public static boolean isBuiltInFunction(String name) {
        return name != null && BUILT_IN_FUNCTIONS.contains(name.toLowerCase());
    }

    public static boolean isInsideSubscript(int caret, SubscriptsSuperscript.SubscriptRegion[] subscripts) {
        if (subscripts == null) return false;
        for (SubscriptsSuperscript.SubscriptRegion s : subscripts) {
            if (s != null && caret > s.subChar && caret <= s.closeParen) {
                return true;
            }
        }
        return false;
    }

    public static boolean isInsidePower(int caret, ExponentsSuperscript.PowerRegion[] powers) {
        if (powers == null) return false;
        for (ExponentsSuperscript.PowerRegion p : powers) {
            if (p != null && caret > p.caretChar && caret <= p.closeParen) {
                return true;
            }
        }
        return false;
    }

    public static void handlePowerInsertion(javax.swing.text.Document doc, int caret, String text,
                                            Consumer<Integer> setCaretAction, Runnable triggerResize) {
        if (caret == 0 || text == null) return;
        if (DerivativesSuperScript.isInsideDerivativeOperator(caret, text)) return;

        // Cannot insert power if inside a subscript or directly after a subscript!
        SubscriptsSuperscript.SubscriptRegion[] subs = SubscriptsSuperscript.findRegions(text, null, null);
        if (isInsideSubscript(caret, subs)) return;
        if (subs != null) {
            for (SubscriptsSuperscript.SubscriptRegion s : subs) {
                if (s != null && (caret == s.closeParen || caret == s.closeParen + 1)) {
                    return;
                }
            }
        }

        char prev = text.charAt(caret - 1);
        if (!Character.isLetterOrDigit(prev) && prev != ')' && prev != ']') return;

        if (prev == ')') {
            int open = MathRenderEngine.findMatchingCharBackward(text, caret - 1, ')', '(');
            if (open != -1 && text.substring(open + 1, caret - 1).trim().isEmpty()) {
                return; // Empty () cannot have an exponent!
            }
        } else if (prev == ']') {
            int open = MathRenderEngine.findMatchingCharBackward(text, caret - 1, ']', '[');
            if (open != -1) {
                if (text.substring(open + 1, caret - 1).trim().isEmpty()) {
                    return; // Empty [] cannot have an exponent!
                }
                String before = text.substring(0, open).trim();
                if (before.endsWith("√") || before.toLowerCase().endsWith("log") ||
                    before.endsWith("∫") || before.endsWith("∑") ||
                    before.contains("d/d")) {
                    return; // Cannot exponentiate parameter brackets!
                }
            }
        }

        if (caret < text.length() && text.charAt(caret) == '^') {
            if (caret + 1 < text.length() && text.charAt(caret + 1) == '(') {
                setCaretAction.accept(caret + 2);
            } else {
                setCaretAction.accept(caret + 1);
            }
            return;
        }

        try {
            doc.insertString(caret, "^()", null);
            setCaretAction.accept(caret + 2);
        } catch (Exception ignored) {
        }
        triggerResize.run();
    }

    public static boolean canAddSubscriptAt(String text, int caret, MathLayout layout) {
        if (text == null || caret <= 0 || caret > text.length()) return false;
        if (DerivativesSuperScript.isInsideDerivativeOperator(caret, text)) return false;

        // Cannot insert subscript if inside an existing subscript or exponent
        SubscriptsSuperscript.SubscriptRegion[] subs = (layout != null && layout.subscripts != null)
                ? layout.subscripts : SubscriptsSuperscript.findRegions(text, null, null);
        if (isInsideSubscript(caret, subs)) return false;
        if (subs != null) {
            for (SubscriptsSuperscript.SubscriptRegion s : subs) {
                if (s != null && (caret == s.closeParen || caret == s.closeParen + 1)) return false;
            }
        }

        ExponentsSuperscript.PowerRegion[] powers = (layout != null && layout.powers != null)
                ? layout.powers : ExponentsSuperscript.findRegions(text);
        if (isInsidePower(caret, powers)) return false;
        if (powers != null) {
            for (ExponentsSuperscript.PowerRegion p : powers) {
                if (p != null && (caret == p.closeParen || caret == p.closeParen + 1)) return false;
            }
        }

        // Cannot add subscript if next character is already '_'
        if (caret < text.length() && text.charAt(caret) == '_') return false;

        // Preceding character must be a valid subscript base (letters, not numbers/symbols/parens/etc.)
        char prev = text.charAt(caret - 1);
        if (!SubscriptsSuperscript.isValidSubscriptBase(prev)) return false;

        // Preceding word cannot be a multi-letter built-in function (e.g. sin, cos, sinc, rect)
        int wordStart = caret - 1;
        while (wordStart >= 0 && Character.isLetter(text.charAt(wordStart))) {
            wordStart--;
        }
        wordStart++;
        String word = text.substring(wordStart, caret).toLowerCase();
        if (word.length() > 1 && isBuiltInFunction(word)) return false;

        return true;
    }

    public static void handleSubscriptInsertion(javax.swing.text.Document doc, int caret, String text,
                                                Consumer<Integer> setCaretAction, Runnable triggerResize) {
        if (!canAddSubscriptAt(text, caret, null)) {
            if (text != null && caret < text.length() && text.charAt(caret) == '_') {
                if (caret + 1 < text.length() && text.charAt(caret + 1) == '(') {
                    setCaretAction.accept(caret + 2);
                } else {
                    setCaretAction.accept(caret + 1);
                }
            }
            return;
        }

        try {
            doc.insertString(caret, "_()", null);
            setCaretAction.accept(caret + 2);
        } catch (Exception ignored) {
        }
        triggerResize.run();
    }

    private static boolean isBalancedParens(String s) {
        if (s == null || !s.startsWith("(") || !s.endsWith(")")) return false;
        int d = 0;
        for (int k = 0; k < s.length() - 1; k++) {
            if (s.charAt(k) == '(') d++;
            else if (s.charAt(k) == ')') d--;
            if (d == 0) return false;
        }
        return d == 1;
    }
}
