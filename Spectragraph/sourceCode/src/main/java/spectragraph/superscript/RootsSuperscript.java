   package com.spectragraph.superscript;

    import java.awt.BasicStroke;
    import java.awt.Font;
    import java.awt.FontMetrics;
    import java.awt.Graphics2D;

    /**
     * Handles detection, measurement, caret positioning, and rendering for
     * roots/radicals.
     */
    public class RootsSuperscript {

        public static class RootRegion {
            public int bracketOpen = -1, bracketClose = -1;
            public int sqrtChar;
            public int parenOpen = -1, parenClose = -1;
            public int cachedWidth = -1;
            public int cachedContentW = -1;
            public int cachedContentAscent = -1;
            public int cachedContentDescent = -1;
            public int cachedIndexW = 0;
            public int cachedRawIndexW = 0;
            public int cachedIndexAscent = 0;
            public int cachedIndexDescent = 0;

            public RootRegion() {
            }

            public RootRegion(RootRegion other) {
                if (other != null) {
                    this.bracketOpen = other.bracketOpen;
                    this.bracketClose = other.bracketClose;
                    this.sqrtChar = other.sqrtChar;
                    this.parenOpen = other.parenOpen;
                    this.parenClose = other.parenClose;
                    this.cachedWidth = other.cachedWidth;
                    this.cachedContentW = other.cachedContentW;
                    this.cachedContentAscent = other.cachedContentAscent;
                    this.cachedContentDescent = other.cachedContentDescent;
                    this.cachedIndexW = other.cachedIndexW;
                    this.cachedRawIndexW = other.cachedRawIndexW;
                    this.cachedIndexAscent = other.cachedIndexAscent;
                    this.cachedIndexDescent = other.cachedIndexDescent;
                }
            }
        }

        public static RootRegion[] findRegions(String text) {
            if (text == null || text.isEmpty()) {
                return new RootRegion[0];
            }
            int count = 0;
            int idx = 0;
            while ((idx = text.indexOf('√', idx)) != -1) {
                count++;
                idx++;
            }

            RootRegion[] res = new RootRegion[count];
            int i = 0;
            int arrayIdx = 0;
            while ((i = text.indexOf('√', i)) != -1) {
                RootRegion r = new RootRegion();
                r.sqrtChar = i;

                // Check if index bracket is immediately after √: √[index](content) or √[content]
                if (i + 1 < text.length() && text.charAt(i + 1) == '[') {
                    int bOpen = i + 1;
                    int depth = 1;
                    int bClose = -1;
                    for (int j = i + 2; j < text.length(); j++) {
                        if (text.charAt(j) == '[')
                            depth++;
                        else if (text.charAt(j) == ']')
                            depth--;
                        if (depth == 0) {
                            bClose = j;
                            break;
                        }
                    }
                    if (bClose != -1 && bClose + 1 < text.length()
                            && (text.charAt(bClose + 1) == '(' || text.charAt(bClose + 1) == '[')) {
                        r.bracketOpen = bOpen;
                        r.bracketClose = bClose;
                        int open = bClose + 1;
                        char openDelim = text.charAt(open);
                        char closeDelim = (openDelim == '(') ? ')' : ']';
                        r.parenOpen = open;
                        depth = 1;
                        for (int j = r.parenOpen + 1; j < text.length(); j++) {
                            if (text.charAt(j) == openDelim)
                                depth++;
                            else if (text.charAt(j) == closeDelim)
                                depth--;
                            if (depth == 0) {
                                r.parenClose = j;
                                break;
                            }
                        }
                    } else if (bClose != -1) {
                        // No following paren or bracket: the [content] IS the radicand!
                        r.parenOpen = bOpen;
                        r.parenClose = bClose;
                        r.bracketOpen = -1;
                        r.bracketClose = -1;
                    }
                } else if (i > 0 && text.charAt(i - 1) == ']') {
                    // Check if index bracket is immediately before √: [index]√(content) or [index]√[content]
                    r.bracketClose = i - 1;
                    int depth = 1;
                    for (int j = i - 2; j >= 0; j--) {
                        if (text.charAt(j) == ']')
                            depth++;
                        else if (text.charAt(j) == '[')
                            depth--;
                    if (depth == 0) {
                        r.bracketOpen = j;
                        break;
                    }
                }
                if (i + 1 < text.length() && (text.charAt(i + 1) == '(' || text.charAt(i + 1) == '[')) {
                    int open = i + 1;
                    char openDelim = text.charAt(open);
                    char closeDelim = (openDelim == '(') ? ')' : ']';
                    r.parenOpen = open;
                    depth = 1;
                    for (int j = r.parenOpen + 1; j < text.length(); j++) {
                        if (text.charAt(j) == openDelim)
                            depth++;
                        else if (text.charAt(j) == closeDelim)
                            depth--;
                        if (depth == 0) {
                            r.parenClose = j;
                            break;
                        }
                    }
                }
            } else if (i + 1 < text.length() && (text.charAt(i + 1) == '(' || text.charAt(i + 1) == '[')) {
                // Standard √(content) or √[content] without index bracket
                int open = i + 1;
                char openDelim = text.charAt(open);
                char closeDelim = (openDelim == '(') ? ')' : ']';
                r.parenOpen = open;
                int depth = 1;
                for (int j = r.parenOpen + 1; j < text.length(); j++) {
                    if (text.charAt(j) == openDelim)
                        depth++;
                    else if (text.charAt(j) == closeDelim)
                        depth--;
                    if (depth == 0) {
                        r.parenClose = j;
                        break;
                    }
                }
            }

            if (r.parenClose == -1)
                r.parenClose = text.length();

            res[arrayIdx++] = r;
            i++;
        }
        return res;
    }

    public static RootRegion getAt(RootRegion[] roots, int i) {
        if (roots == null)
            return null;
        for (RootRegion r : roots) {
            if (r == null)
                continue;
            int rootStartChar = (r.bracketOpen != -1 && r.bracketOpen < r.sqrtChar) ? r.bracketOpen : r.sqrtChar;
            if (rootStartChar == i) {
                return r;
            }
        }
        return null;
    }

    public static Font getIndexFont(Font font, int nestLevel) {
        if (font == null) return null;
        if (nestLevel > 0) {
            return font;
        }
        float newSize = Math.max(font.getSize2D() * 0.72f, 9f);
        return font.deriveFont(newSize);
    }

    public static int getIndexColumns(String text, int start, int end) {
        if (text == null || start >= end)
            return 0;
        int cols = 0;
        int i = start;
        while (i < end) {
            char c = text.charAt(i);
            if (c == ' ') {
                i++;
                continue;
            }
            if (c == '^') {
                i++;
                if (i < end && text.charAt(i) == '(') {
                    int match = MathRenderEngine.findMatchingChar(text, i, '(', ')');
                    if (match != -1 && match <= end) {
                        cols += getIndexColumns(text, i + 1, match);
                        i = match + 1;
                        continue;
                    }
                }
                int tokEnd = ExponentsSuperscript.consumeToken(text, i);
                cols += Math.max(1, tokEnd - i);
                i = tokEnd;
                continue;
            }
            if (c == '(') {
                int closeParen = MathRenderEngine.findMatchingChar(text, i, '(', ')');
                if (closeParen != -1 && closeParen + 1 < end && text.charAt(closeParen + 1) == '/'
                        && closeParen + 2 < end && text.charAt(closeParen + 2) == '(') {
                    int denClose = MathRenderEngine.findMatchingChar(text, closeParen + 2, '(', ')');
                    if (denClose != -1 && denClose <= end) {
                        int numCols = getIndexColumns(text, i + 1, closeParen);
                        int denCols = getIndexColumns(text, closeParen + 3, denClose);
                        cols += Math.max(numCols, denCols);
                        i = denClose + 1;
                        continue;
                    }
                }
            }
            if (c == '∫' || c == '\u222B') {
                cols += 10;
                i++;
                continue;
            }
            if (c == '√' || c == '\u221A') {
                cols += 6;
                i++;
                continue;
            }
            if (Character.isLetter(c)) {
                int nameStart = i;
                while (i < end && Character.isLetterOrDigit(text.charAt(i)))
                    i++;
                String name = text.substring(nameStart, i);
                if (i < end && text.charAt(i) == '(') {
                    int close = MathRenderEngine.findMatchingChar(text, i, '(', ')');
                    if (close != -1 && close <= end) {
                        int argCols = getIndexColumns(text, i + 1, close);
                        cols += name.length() + 1 + argCols + 1;
                        i = close + 1;
                        continue;
                    }
                }
                cols += name.length();
                continue;
            }
            if (Character.isDigit(c)) {
                int numStart = i;
                while (i < end && (Character.isDigit(text.charAt(i)) || text.charAt(i) == '.'))
                    i++;
                cols += (i - numStart);
                continue;
            }
            cols++;
            i++;
        }
        return cols;
    }

    public static boolean isMultiCellIndex(String text, int start, int end) {
        if (text == null || start >= end)
            return false;
        if (alreadyHasOuterBrackets(text, start, end))
            return false;
        if (isSingleFunctionCall(text, start, end))
            return false;
        if (hasFractionOver3VerticalCells(text, start, end)) {
            return true;
        }
        return getIndexColumns(text, start, end) > 3;
    }

    public static boolean alreadyHasOuterBrackets(String text, int start, int end) {
        if (text == null || start >= end)
            return false;
        while (start < end && text.charAt(start) == ' ')
            start++;
        while (end > start && text.charAt(end - 1) == ' ')
            end--;
        if (start < end && (text.charAt(start) == '[' || text.charAt(start) == '(')) {
            char open = text.charAt(start);
            char close = open == '[' ? ']' : ')';
            int match = MathRenderEngine.findMatchingChar(text, start, open, close);
            return match == end - 1;
        }
        return false;
    }

    public static boolean isSingleFunctionCall(String text, int start, int end) {
        if (text == null || start >= end)
            return false;
        while (start < end && text.charAt(start) == ' ')
            start++;
        while (end > start && text.charAt(end - 1) == ' ')
            end--;
        int i = start;
        if (i < end && (text.charAt(i) == '-' || text.charAt(i) == '+'))
            i++;
        int nameStart = i;
        while (i < end && Character.isLetter(text.charAt(i)))
            i++;
        if (i > nameStart && i < end && text.charAt(i) == '(') {
            int close = MathRenderEngine.findMatchingChar(text, i, '(', ')');
            return close == end - 1;
        }
        return false;
    }

    public static boolean hasFractionOver3VerticalCells(String text, int start, int end) {
        if (text == null || start >= end)
            return false;
        for (int i = start; i < end; i++) {
            if (text.charAt(i) == '(') {
                int numClose = MathRenderEngine.findMatchingChar(text, i, '(', ')');
                if (numClose != -1 && numClose + 1 < end && text.charAt(numClose + 1) == '/'
                        && numClose + 2 < end && text.charAt(numClose + 2) == '(') {
                    int denClose = MathRenderEngine.findMatchingChar(text, numClose + 2, '(', ')');
                    if (denClose != -1 && denClose <= end) {
                        if (takesMoreThanOneVerticalCell(text, i + 1, numClose)
                                || takesMoreThanOneVerticalCell(text, numClose + 3, denClose)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public static boolean takesMoreThanOneVerticalCell(String text, int start, int end) {
        if (text == null || start >= end)
            return false;
        for (int i = start; i < end; i++) {
            char c = text.charAt(i);
            if (c == '^' || c == '\u222B' || c == '\u221A' || c == '√' || c == '∫')
                return true;
            if (c == '/' && i > start && i + 1 < end && text.charAt(i - 1) == ')' && text.charAt(i + 1) == '(') {
                return true;
            }
        }
        return false;
    }

    public static boolean isMultiCellOrTall(String text, int start, int end) {
        if (text == null || start >= end)
            return false;
        if (getHorizontalColumns(text, start, end) > 3) {
            return true;
        }
        if (hasFractionOver3VerticalCells(text, start, end)) {
            return true;
        }
        for (int i = start; i < end; i++) {
            char c = text.charAt(i);
            if (c == '\u222B' || c == '∫' || c == '\u221A' || c == '√')
                return true;
        }
        return false;
    }

    public static boolean alreadyHasOuterParens(String text, int start, int end) {
        if (text == null || start >= end)
            return false;
        while (start < end && text.charAt(start) == ' ')
            start++;
        while (end > start && text.charAt(end - 1) == ' ')
            end--;
        if (start < end && text.charAt(start) == '(') {
            int match = MathRenderEngine.findMatchingChar(text, start, '(', ')');
            return match == end - 1;
        }
        return false;
    }

    public static int getHorizontalColumns(String text, int start, int end) {
        if (text == null || start >= end)
            return 0;
        int cols = 0;
        int i = start;
        while (i < end) {
            char c = text.charAt(i);
            if (c == ' ') {
                i++;
                continue;
            }

            // Check if fraction: (num)/(den)
            if (c == '(') {
                int closeParen = MathRenderEngine.findMatchingChar(text, i, '(', ')');
                if (closeParen != -1 && closeParen + 1 < end && text.charAt(closeParen + 1) == '/'
                        && closeParen + 2 < end && text.charAt(closeParen + 2) == '(') {
                    int denClose = MathRenderEngine.findMatchingChar(text, closeParen + 2, '(', ')');
                    if (denClose != -1 && denClose <= end) {
                        int numCols = getHorizontalColumns(text, i + 1, closeParen);
                        int denCols = getHorizontalColumns(text, closeParen + 3, denClose);
                        cols += Math.max(numCols, denCols);
                        i = denClose + 1;
                        continue;
                    }
                }
            }

            // Check if integral: ∫
            if (c == '∫' || c == '\u222B') {
                return 10; // definitely > 3 cols
            }

            // Check if function call like sin(, rect(, sinc(, tri(, saw(, etc.
            if (Character.isLetter(c)) {
                int nameStart = i;
                while (i < end && Character.isLetterOrDigit(text.charAt(i))) {
                    i++;
                }
                String name = text.substring(nameStart, i);
                if (i < end && text.charAt(i) == '(') {
                    int close = MathRenderEngine.findMatchingChar(text, i, '(', ')');
                    if (close != -1 && close <= end) {
                        int argCols = getHorizontalColumns(text, i + 1, close);
                        cols += name.length() + 1 + argCols + 1;
                        i = close + 1;
                        continue;
                    }
                }
                int idLen = name.length();
                cols += idLen;
                if (i < end && text.charAt(i) == '_') {
                    i++;
                    if (i < end && (text.charAt(i) == '(' || text.charAt(i) == '[' || text.charAt(i) == '{')) {
                        char openChar = text.charAt(i);
                        char closeChar = (openChar == '(') ? ')' : (openChar == '[') ? ']' : '}';
                        int match = MathRenderEngine.findMatchingChar(text, i, openChar, closeChar);
                        i = (match != -1) ? match + 1 : end;
                    } else {
                        while (i < end && (Character.isLetterOrDigit(text.charAt(i)) || text.charAt(i) == '.')) {
                            i++;
                        }
                    }
                }
                if (i < end && text.charAt(i) == '^') {
                    i = skipExp(text, i, end);
                }
                continue;
            }

            // Check digits like "23"
            if (Character.isDigit(c)) {
                int numStart = i;
                while (i < end && (Character.isDigit(text.charAt(i)) || text.charAt(i) == '.')) {
                    i++;
                }
                int numLen = i - numStart;
                cols += numLen;
                if (i < end && text.charAt(i) == '_') {
                    i++;
                    if (i < end && (text.charAt(i) == '(' || text.charAt(i) == '[' || text.charAt(i) == '{')) {
                        char openChar = text.charAt(i);
                        char closeChar = (openChar == '(') ? ')' : (openChar == '[') ? ']' : '}';
                        int match = MathRenderEngine.findMatchingChar(text, i, openChar, closeChar);
                        i = (match != -1) ? match + 1 : end;
                    } else {
                        while (i < end && (Character.isLetterOrDigit(text.charAt(i)) || text.charAt(i) == '.')) {
                            i++;
                        }
                    }
                }
                if (i < end && text.charAt(i) == '^') {
                    i = skipExp(text, i, end);
                }
                continue;
            }

            if (c == '^') {
                i = skipExp(text, i, end);
                continue;
            }

            // Root: √
            if (c == '√' || c == '\u221A') {
                return 6; // root takes > 3 cols
            }

            cols++;
            i++;
        }
        return cols;
    }

    private static int parseExpCols(String text, int caretIdx, int end) {
        int i = caretIdx + 1;
        if (i >= end)
            return 0;
        if (text.charAt(i) == '(') {
            int close = MathRenderEngine.findMatchingChar(text, i, '(', ')');
            if (close != -1 && close <= end) {
                if (close + 1 < end && text.charAt(close + 1) == '/' && close + 2 < end
                        && text.charAt(close + 2) == '(') {
                    int denClose = MathRenderEngine.findMatchingChar(text, close + 2, '(', ')');
                    if (denClose != -1 && denClose <= end) {
                        int numCols = getHorizontalColumns(text, i + 1, close);
                        int denCols = getHorizontalColumns(text, close + 3, denClose);
                        return Math.max(numCols, denCols);
                    }
                }
                String inner = text.substring(i + 1, close);
                int slash = inner.indexOf('/');
                if (slash != -1) {
                    int numCols = getHorizontalColumns(inner.substring(0, slash), 0, slash);
                    int denCols = getHorizontalColumns(inner.substring(slash + 1), 0, inner.length() - slash - 1);
                    return Math.max(numCols, denCols);
                }
                return getHorizontalColumns(text, i + 1, close);
            }
        }
        int start = i;
        while (i < end && (Character.isLetterOrDigit(text.charAt(i)) || text.charAt(i) == '.')) {
            i++;
        }
        if (i < end && text.charAt(i) == '/' && i + 1 < end) {
            int numCols = i - start;
            int denStart = i + 1;
            int j = denStart;
            while (j < end && (Character.isLetterOrDigit(text.charAt(j)) || text.charAt(j) == '.')) {
                j++;
            }
            int denCols = j - denStart;
            return Math.max(numCols, denCols);
        }
        return Math.max(1, i - start);
    }

    private static int skipExp(String text, int caretIdx, int end) {
        int i = caretIdx + 1;
        if (i >= end)
            return end;
        if (text.charAt(i) == '(') {
            int close = MathRenderEngine.findMatchingChar(text, i, '(', ')');
            if (close != -1 && close <= end) {
                if (close + 1 < end && text.charAt(close + 1) == '/' && close + 2 < end
                        && text.charAt(close + 2) == '(') {
                    int denClose = MathRenderEngine.findMatchingChar(text, close + 2, '(', ')');
                    if (denClose != -1 && denClose <= end)
                        return denClose + 1;
                }
                return close + 1;
            }
        }
        return ExponentsSuperscript.consumeToken(text, i);
    }

    public static int getAscent(Graphics2D g2, String text, RootRegion r, Font font,
            FractionsSuperscript.FractionRegion[] fractions, RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
            ParensSuperscript.ParenRegion[] parens, int nestLevel, int end) {
        return getAscent(g2, text, r, font, fractions, roots, powers, integrals, null, null, null, null, null, parens, nestLevel, end);
    }

    public static int getAscent(Graphics2D g2, String text, RootRegion r, Font font,
            FractionsSuperscript.FractionRegion[] fractions, RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations, LogsSuperScript.LogRegion[] logs,
            DerivativesSuperScript.DerivativeRegion[] derivatives,
            FloorAndCeilingSuperScript.FloorCeilRegion[] floorCeils,
            AbsoluteValueSuperScript.AbsoluteValueRegion[] absoluteValues,
            ParensSuperscript.ParenRegion[] parens, int nestLevel, int end) {
        FontMetrics fm = g2.getFontMetrics(font);
        int close = r.parenClose != -1 && r.parenClose < end ? r.parenClose : end;
        int rootStart = r.parenOpen != -1 ? r.parenOpen + 1 : (r.sqrtChar + 1);
        int subAscent = MathRenderEngine.getRegionAscent(g2, text, rootStart, close, font, fractions, roots, powers,
                integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
        boolean nested = hasNestedRoot(roots, rootStart, close);
        int clearance = nested ? 9 : 3;
        int rootAscent = subAscent + clearance;
        if (r.bracketOpen != -1 && r.bracketClose > r.bracketOpen + 1) {
            Font indexFont = getIndexFont(font, nestLevel);
            FontMetrics indexFm = g2.getFontMetrics(indexFont);
            int indexAscent = MathRenderEngine.getRegionAscent(g2, text, r.bracketOpen + 1, r.bracketClose, indexFont,
                    fractions, roots, powers,
                    integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel + 1);
            int indexDescent = MathRenderEngine.getRegionDescent(g2, text, r.bracketOpen + 1, r.bracketClose, indexFont,
                    fractions, roots, powers,
                    integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel + 1);
            boolean isMultiCell = isMultiCellIndex(text, r.bracketOpen + 1, r.bracketClose);
            boolean hasOuterBrackets = isMultiCell && alreadyHasOuterBrackets(text, r.bracketOpen + 1, r.bracketClose);
            boolean needsAutoBrackets = false;

            int notchYRel = -Math.max((int) (fm.getAscent() * 0.55f),
                    Math.min(subAscent / 4, (int) (fm.getAscent() * 0.75f)));
            int indexBaseYRel = notchYRel - 1;
            rootAscent = Math.max(rootAscent, -(indexBaseYRel - indexAscent));
        }
        return rootAscent;
    }

    public static boolean hasNestedRoot(RootRegion[] roots, int start, int end) {
        if (roots == null)
            return false;
        for (RootRegion r : roots) {
            if (r != null && r.sqrtChar >= start && r.sqrtChar < end) {
                return true;
            }
        }
        return false;
    }

    public static int getDescent(Graphics2D g2, String text, RootRegion r, Font font,
            FractionsSuperscript.FractionRegion[] fractions, RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
            ParensSuperscript.ParenRegion[] parens, int nestLevel, int end) {
        return getDescent(g2, text, r, font, fractions, roots, powers, integrals, null, null, null, null, null, parens, nestLevel, end);
    }

    public static int getDescent(Graphics2D g2, String text, RootRegion r, Font font,
            FractionsSuperscript.FractionRegion[] fractions, RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations, LogsSuperScript.LogRegion[] logs,
            DerivativesSuperScript.DerivativeRegion[] derivatives,
            FloorAndCeilingSuperScript.FloorCeilRegion[] floorCeils,
            AbsoluteValueSuperScript.AbsoluteValueRegion[] absoluteValues,
            ParensSuperscript.ParenRegion[] parens, int nestLevel, int end) {
        FontMetrics fm = g2.getFontMetrics(font);
        int close = r.parenClose != -1 && r.parenClose < end ? r.parenClose : end;
        int rootStart = r.parenOpen != -1 ? r.parenOpen + 1 : (r.sqrtChar + 1);
        int subDescent = MathRenderEngine.getRegionDescent(g2, text, rootStart, close, font, fractions, roots, powers,
                integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
        int rootDescent = subDescent + 2;
        if (r.bracketOpen != -1 && r.bracketClose > r.bracketOpen + 1) {
            Font indexFont = getIndexFont(font, nestLevel);
            FontMetrics indexFm = g2.getFontMetrics(indexFont);
            int indexAscent = MathRenderEngine.getRegionAscent(g2, text, r.bracketOpen + 1, r.bracketClose, indexFont,
                    fractions, roots, powers,
                    integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel + 1);
            int indexDescent = MathRenderEngine.getRegionDescent(g2, text, r.bracketOpen + 1, r.bracketClose, indexFont,
                    fractions, roots, powers,
                    integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel + 1);
            int subAscent = MathRenderEngine.getRegionAscent(g2, text, rootStart, close, font, fractions, roots, powers,
                    integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
            int notchYRel = -Math.max((int) (fm.getAscent() * 0.55f),
                    Math.min(subAscent / 4, (int) (fm.getAscent() * 0.75f)));
            int indexBaseYRel = notchYRel - 1;
            rootDescent = Math.max(rootDescent, indexBaseYRel + indexDescent);
        }
        return rootDescent;
    }

    public static int render(Graphics2D g2, String text, RootRegion r, int curX, int baselineY,
            Font font, boolean draw, FractionsSuperscript.FractionRegion[] fractions, RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
            ParensSuperscript.ParenRegion[] parens, int nestLevel, int selStart, int selEnd, int end) {
        return render(g2, text, r, curX, baselineY, font, draw, fractions, roots, powers, integrals, null, null, null, null, null, parens, nestLevel, selStart, selEnd, end);
    }

    public static int render(Graphics2D g2, String text, RootRegion r, int curX, int baselineY,
            Font font, boolean draw, FractionsSuperscript.FractionRegion[] fractions, RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations, LogsSuperScript.LogRegion[] logs,
            DerivativesSuperScript.DerivativeRegion[] derivatives,
            FloorAndCeilingSuperScript.FloorCeilRegion[] floorCeils,
            AbsoluteValueSuperScript.AbsoluteValueRegion[] absoluteValues,
            ParensSuperscript.ParenRegion[] parens, int nestLevel, int selStart, int selEnd, int end) {
        FontMetrics fm = g2.getFontMetrics(font);
        int close = r.parenClose != -1 && r.parenClose < end ? r.parenClose : end;
        int rootStart = r.parenOpen != -1 ? r.parenOpen + 1 : (r.sqrtChar + 1);

        boolean hasIndex = (r.bracketOpen != -1 && r.bracketClose != -1 && r.bracketClose > r.bracketOpen);
        boolean indexHasText = (hasIndex && r.bracketClose > r.bracketOpen + 1);
        Font indexFont = getIndexFont(font, nestLevel);
        FontMetrics indexFm = g2.getFontMetrics(indexFont);

        int rawIndexW = 0;
        int indexAscent = 0;
        int indexDescent = 0;
        if (indexHasText) {
            rawIndexW = MathRenderEngine.renderSubString(g2, text, r.bracketOpen + 1, r.bracketClose, 0, 0,
                    indexFont, false, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel + 1, -1, -1);
            indexAscent = MathRenderEngine.getRegionAscent(g2, text, r.bracketOpen + 1, r.bracketClose, indexFont,
                    fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel + 1);
            indexDescent = MathRenderEngine.getRegionDescent(g2, text, r.bracketOpen + 1, r.bracketClose, indexFont,
                    fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel + 1);
        } else if (hasIndex && (selStart > r.bracketOpen && selStart <= r.bracketClose)) {
            rawIndexW = 8;
            indexAscent = indexFm.getAscent();
            indexDescent = indexFm.getDescent();
        }

        boolean isLargeIndex = indexHasText && isMultiCellIndex(text, r.bracketOpen + 1, r.bracketClose);
        int idxBracketW = isLargeIndex ? Math.max(3, (int) (indexFm.charWidth('[') * 0.75f)) : 0;
        int idxBracketGap = isLargeIndex ? 1 : 0;
        int indexW = rawIndexW + 2 * idxBracketW + 2 * idxBracketGap;

        int contentW = MathRenderEngine.renderSubString(g2, text, rootStart, close, 0, 0,
                font, false, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel, -1, -1);
        if (contentW == 0 && (selStart > r.parenOpen && selStart <= close)) {
            contentW = 8;
        }

        int contentAscent = MathRenderEngine.getRegionAscent(g2, text, rootStart, close, font, fractions, roots, powers,
                integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
        int contentDescent = MathRenderEngine.getRegionDescent(g2, text, rootStart, close, font, fractions, roots,
                powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);

        boolean nested = hasNestedRoot(roots, rootStart, close);
        int clearance = nested ? 9 : 3;

        int topYRel = -(contentAscent + clearance);
        int bottomYRel = contentDescent + 2;
        int radH = (contentAscent + clearance) + (contentDescent + 2);
        int tickH = Math.min(Math.max(5, (int) (fm.getAscent() * 0.45f)), Math.max(5, radH / 4));
        int tickYRel = bottomYRel - tickH;

        int indexBaseYRel;
        if (hasIndex) {
            int notchYRel = -Math.max((int) (fm.getAscent() * 0.55f),
                    Math.min(contentAscent / 4, (int) (fm.getAscent() * 0.75f)));
            indexBaseYRel = notchYRel - 1;
        } else {
            indexBaseYRel = -Math.min(contentAscent / 2, (int) (fm.getAscent() * 0.6f)) - 1;
        }
        int diagW = Math.max(8, Math.min(18, 8 + Math.max(0, radH - 25) / 10));
        int tickW = Math.max(4, Math.min(7, diagW / 2));
        int tickX = curX + (indexW > 0 ? indexW + 2 : 2);
        int dipX = tickX + tickW;
        int contentX = dipX + diagW;
        int contentPad = 2;
        int hookPad = 2;
        int hookX = contentX + contentPad + contentW + hookPad;
        int rootTrailingGap = Math.max(8, (int) (fm.charWidth(' ') * 0.75f));
        int totalW = (hookX - curX) + rootTrailingGap;

        if (draw) {
            int topY = baselineY + topYRel;
            int bottomY = baselineY + contentDescent + 2;
            int indexBaseY = baselineY + indexBaseYRel;

            if (indexHasText) {
                int idxDrawX = curX;
                if (isLargeIndex) {
                    int bTop = indexBaseY - indexAscent;
                    int bBottom = indexBaseY + indexDescent;
                    ParensSuperscript.drawStretchyBracket(g2, idxDrawX, bTop, bBottom, idxBracketW, true);
                    idxDrawX += idxBracketW + idxBracketGap;
                }
                MathRenderEngine.renderSubString(g2, text, r.bracketOpen + 1, r.bracketClose, idxDrawX, indexBaseY,
                        indexFont, true, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel + 1, selStart,
                        selEnd);
                if (isLargeIndex) {
                    int bTop = indexBaseY - indexAscent;
                    int bBottom = indexBaseY + indexDescent;
                    ParensSuperscript.drawStretchyBracket(g2, idxDrawX + idxBracketGap + rawIndexW, bTop, bBottom, idxBracketW, false);
                }
            }

            g2.setStroke(new BasicStroke(1.5f));
            int tickY = bottomY - tickH;
            g2.drawLine(tickX, tickY, dipX, bottomY);
            g2.drawLine(dipX, bottomY, contentX, topY);
            g2.drawLine(contentX, topY, hookX, topY);
            int hookDown = Math.min(8, Math.max(4, radH / 12));
            g2.drawLine(hookX, topY, hookX, topY + hookDown);

            MathRenderEngine.renderSubString(g2, text, rootStart, close, contentX + contentPad, baselineY,
                    font, true, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel, selStart, selEnd);
        }

        if (r != null) {
            r.cachedWidth = totalW;
            r.cachedContentW = contentW;
            r.cachedContentAscent = contentAscent;
            r.cachedContentDescent = contentDescent;
            r.cachedIndexW = indexW;
            r.cachedRawIndexW = rawIndexW;
            r.cachedIndexAscent = indexAscent;
            r.cachedIndexDescent = indexDescent;
        }
        return curX + totalW;
    }

    public static int[] getCaretInfo(Graphics2D g2, String text, RootRegion r, int curX,
            int targetOffset, Font font, FractionsSuperscript.FractionRegion[] fractions, RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
            ParensSuperscript.ParenRegion[] parens, int nestLevel, int end) {
        return getCaretInfo(g2, text, r, curX, targetOffset, font, fractions, roots, powers, integrals,
                null, null, null, null, null, parens, nestLevel, end);
    }

    public static int[] getCaretInfo(Graphics2D g2, String text, RootRegion r, int curX,
            int targetOffset, Font font, FractionsSuperscript.FractionRegion[] fractions, RootRegion[] roots,
            ExponentsSuperscript.PowerRegion[] powers, IntegralsSuperscript.IntegralRegion[] integrals,
            SummatoriesSuperScript.SumRegion[] summations, LogsSuperScript.LogRegion[] logs,
            DerivativesSuperScript.DerivativeRegion[] derivatives,
            FloorAndCeilingSuperScript.FloorCeilRegion[] floorCeils,
            AbsoluteValueSuperScript.AbsoluteValueRegion[] absoluteValues,
            ParensSuperscript.ParenRegion[] parens, int nestLevel, int end) {
        if (r == null) return null;
        int close = r.parenClose != -1 && r.parenClose < end ? r.parenClose : end;
        if (targetOffset < r.sqrtChar || targetOffset > close) {
            return null;
        }
        FontMetrics fm = g2.getFontMetrics(font);
        int rootStart = r.parenOpen != -1 ? r.parenOpen + 1 : (r.sqrtChar + 1);

        boolean hasIndex = (r.bracketOpen != -1 && r.bracketClose != -1 && r.bracketClose > r.bracketOpen);
        boolean indexHasText = (hasIndex && r.bracketClose > r.bracketOpen + 1);
        Font indexFont = getIndexFont(font, nestLevel);
        FontMetrics indexFm = g2.getFontMetrics(indexFont);

        boolean isLargeIndex = indexHasText && isMultiCellIndex(text, r.bracketOpen + 1, r.bracketClose);
        int idxBracketW = isLargeIndex ? Math.max(3, (int) (indexFm.charWidth('[') * 0.75f)) : 0;
        int idxBracketGap = isLargeIndex ? 1 : 0;

        int rawIndexW;
        int indexAscent;
        int indexDescent;
        int indexW;
        int contentW;
        int contentAscent;
        int contentDescent;

        if (r.cachedContentW != -1) {
            rawIndexW = r.cachedRawIndexW;
            indexAscent = r.cachedIndexAscent;
            indexDescent = r.cachedIndexDescent;
            indexW = r.cachedIndexW;
            contentW = r.cachedContentW;
            contentAscent = r.cachedContentAscent;
            contentDescent = r.cachedContentDescent;
        } else {
            rawIndexW = 0;
            indexAscent = 0;
            indexDescent = 0;
            if (indexHasText) {
                rawIndexW = MathRenderEngine.renderSubString(g2, text, r.bracketOpen + 1, r.bracketClose, 0, 0,
                        indexFont, false, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel + 1, -1, -1);
                indexAscent = MathRenderEngine.getRegionAscent(g2, text, r.bracketOpen + 1, r.bracketClose, indexFont,
                        fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel + 1);
                indexDescent = MathRenderEngine.getRegionDescent(g2, text, r.bracketOpen + 1, r.bracketClose, indexFont,
                        fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel + 1);
            } else if (hasIndex && (targetOffset > r.bracketOpen && targetOffset <= r.bracketClose)) {
                rawIndexW = 8;
                indexAscent = indexFm.getAscent();
                indexDescent = indexFm.getDescent();
            }

            indexW = rawIndexW + 2 * idxBracketW + 2 * idxBracketGap;

            contentW = MathRenderEngine.renderSubString(g2, text, rootStart, close, 0, 0,
                    font, false, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel, -1, -1);
            if (contentW == 0 && (targetOffset > r.parenOpen && targetOffset <= close)) {
                contentW = 8;
            }
            contentAscent = MathRenderEngine.getRegionAscent(g2, text, rootStart, close, font, fractions, roots, powers,
                    integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
            contentDescent = MathRenderEngine.getRegionDescent(g2, text, rootStart, close, font, fractions, roots,
                    powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
        }

        boolean nested = hasNestedRoot(roots, rootStart, close);
        int clearance = nested ? 9 : 3;

        int topYRel = -(contentAscent + clearance);
        int bottomYRel = contentDescent + 2;
        int radH = (contentAscent + clearance) + (contentDescent + 2);
        int tickH = Math.min(Math.max(5, (int) (fm.getAscent() * 0.45f)), Math.max(5, radH / 4));
        int tickYRel = bottomYRel - tickH;

        int indexBaseYRel;
        if (hasIndex) {
            int notchYRel = -Math.max((int) (fm.getAscent() * 0.55f),
                    Math.min(contentAscent / 4, (int) (fm.getAscent() * 0.75f)));
            indexBaseYRel = notchYRel - 1;
        } else {
            int defaultNotchYRel = -Math.min(contentAscent / 2, (int) (fm.getAscent() * 0.6f));
            indexBaseYRel = defaultNotchYRel - 1;
        }
        int diagW = Math.max(8, Math.min(18, 8 + Math.max(0, radH - 25) / 10));
        int tickW = Math.max(4, Math.min(7, diagW / 2));
        int tickX = curX + (indexW > 0 ? indexW + 2 : 2);
        int dipX = tickX + tickW;
        int contentX = dipX + diagW;

        int contentPad = 2;

        // Caret inside index
        if (hasIndex && (targetOffset > r.bracketOpen && targetOffset <= r.bracketClose)) {
            int indexContentX = curX + (isLargeIndex ? idxBracketW + idxBracketGap : 0);
            int[] sub = MathRenderEngine.getCaretInfoSubString(g2, text, r.bracketOpen + 1, r.bracketClose,
                    targetOffset, indexFont, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel + 1);
            if (sub != null) {
                return new int[] { indexContentX + sub[0], indexBaseYRel + sub[1], sub[2], sub[3] };
            }
            return new int[] { indexContentX, indexBaseYRel, indexFm.getAscent(), indexFm.getDescent() };
        }

        // Caret before root index when [index]√
        if (r.bracketOpen != -1 && r.bracketOpen < r.sqrtChar && targetOffset == r.bracketOpen) {
            return new int[] { curX, 0, fm.getAscent(), fm.getDescent() };
        }

        // Caret before content
        if (targetOffset == r.parenOpen || (targetOffset == r.sqrtChar && r.sqrtChar > r.bracketClose)) {
            return new int[] { contentX + contentPad, 0, fm.getAscent(), fm.getDescent() };
        }

        // Caret inside content
        if (targetOffset >= rootStart && targetOffset <= close) {
            int[] sub = MathRenderEngine.getCaretInfoSubString(g2, text, rootStart, close,
                    targetOffset, font, fractions, roots, powers, integrals, summations, logs, derivatives, floorCeils, absoluteValues, parens, nestLevel);
            if (sub != null)
                return new int[] { contentX + contentPad + sub[0], sub[1], sub[2], sub[3] };
            return new int[] { contentX + contentPad, 0, fm.getAscent(), fm.getDescent() };
        }

        return null;
    }

    public static boolean isForbiddenCaret(int c, RootRegion[] roots) {
        if (roots == null)
            return false;
        for (RootRegion r : roots) {
            if (r == null)
                continue;
            if (c == r.parenOpen)
                return true; // between ] and ( or √ and (
            if (r.bracketOpen != -1) {
                if (c == r.bracketOpen && r.bracketOpen > r.sqrtChar)
                    return true; // between √ and [
                if (c == r.sqrtChar && r.bracketClose < r.sqrtChar)
                    return true; // between ] and √
            }
        }
        return false;
    }
}
