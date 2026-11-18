package com.spectragraph;

import java.util.Map;
import com.spectragraph.superscript.AbsoluteValueSuperScript;
import org.apache.commons.numbers.complex.Complex;

/**
 * Key Capabilities:
 *   - Real and Complex Arithmetic: Native evaluation of real scalars and complex numbers (via Apache Commons Numbers {@link Complex}).
 *   - Calculus and Differential Operators: Dynamic numerical and symbolic evaluation of derivatives (d/dx, d/dy, partials ∂/∂x), multidimensional definite integrals (∫), and sums (∑).
 *   - Signal Processing Primitives: Standard signals including rect, tri, sinc, step, saw, trapz, for the trapezoidal wave, and Dirac delta impulses (δ).
 *   - Safe Numerical Guards: Protects against division by zero, fractional powers of negative bases, domain overflow, and recursive evaluation stack depth limits.
 */
public class MathParser {

    @FunctionalInterface
    public interface Expression {
        double eval(double x, double y);
        default double eval(double x) {
            return eval(x, 0.0);
        }
        default Complex evalComplex(Complex x, Complex y) {
            return Complex.ofCartesian(eval(x.real(), y.real()), 0.0);
        }
        default Complex evalComplex(Complex x) {
            return evalComplex(x, Complex.ZERO);
        }
    }

    @FunctionalInterface
    public interface ComplexExpression {
        Complex eval(Complex x, Complex y);
        default Complex eval(Complex x) {
            return eval(x, Complex.ZERO);
        }
    }

    public static class IncompleteExpressionException extends RuntimeException {
        public IncompleteExpressionException(String message) {
            super(message);
        }
    }

    private static final ThreadLocal<Integer> integralDepth = ThreadLocal.withInitial(() -> 0);
    private static final int MAX_INTEGRAL_DEPTH = 6;
    private static final ThreadLocal<Integer> sumDepth = ThreadLocal.withInitial(() -> 0);
    private static final int MAX_SUM_DEPTH = 4;
    private static final ThreadLocal<Map<String, Double>> sumContext = ThreadLocal.withInitial(java.util.HashMap::new);
    public static final ThreadLocal<Double> samplingStep = ThreadLocal.withInitial(() -> 0.0);
    public static final ThreadLocal<Boolean> impulseTriggered = ThreadLocal.withInitial(() -> false);

    public static boolean containsImpulse(String str) {
        if (str == null) return false;
        String s = str.toLowerCase();
        return str.contains("δ") || str.contains("Δ") || str.contains("Ш")
                || s.contains("delta") || s.contains("dirac") || s.contains("drcd")
                || s.contains("drcc") || s.contains("sha") || s.contains("comb");
    }

    public static double hashRand(double n, int salt) {
        long k = Double.doubleToLongBits(Math.round(n)) ^ (salt * 0x9E3779B97F4A7C15L);
        k ^= (k >>> 30);
        k *= 0xBF58476D1CE4E5B9L;
        k ^= (k >>> 27);
        k *= 0x94D049BB133111EBL;
        k ^= (k >>> 31);
        double u = (k & 0xFFFFFFFFFFFFFL) / (double) 0x10000000000000L;
        return 2.0 * u - 1.0;
    }

    public static double hashRandn(double n) {
        double u1 = Math.max(1e-15, (hashRand(n, 101) + 1.0) / 2.0);
        double u2 = (hashRand(n, 202) + 1.0) / 2.0;
        return Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2.0 * Math.PI * u2);
    }

    public static double safePow(double base, double exp) {
        if (Double.isNaN(base) || Double.isNaN(exp)) return Double.NaN;
        if (base == 0.0) {
            if (exp > 0) return 0.0;
            if (exp == 0) return 1.0;
            return Double.NaN;
        }
        if (base > 0 || exp == Math.floor(exp)) {
            return Math.pow(base, exp);
        }
        double result = Math.pow(Math.abs(base), exp);
        return -result;
    }

    public static Complex normalizeZero(Complex z) {
        if (z == null) return Complex.ZERO;
        double re = z.real();
        double im = z.imag();
        boolean change = false;
        if (re == -0.0) { re = 0.0; change = true; }
        if (im == -0.0) { im = 0.0; change = true; }
        return change ? Complex.ofCartesian(re, im) : z;
    }

    public static Complex safePowComplex(Complex base, Complex exp) {
        if (base == null || exp == null) return Complex.ofCartesian(Double.NaN, Double.NaN);
        if (Double.isNaN(base.real()) || Double.isNaN(base.imag()) || Double.isNaN(exp.real()) || Double.isNaN(exp.imag())) {
            return Complex.ofCartesian(Double.NaN, Double.NaN);
        }
        base = normalizeZero(base);
        exp = normalizeZero(exp);
        if (base.equals(Complex.ZERO) || (Math.abs(base.real()) < 1e-15 && Math.abs(base.imag()) < 1e-15)) {
            if (exp.equals(Complex.ZERO) || (Math.abs(exp.real()) < 1e-15 && Math.abs(exp.imag()) < 1e-15)) return Complex.ofCartesian(1.0, 0.0);
            if (exp.real() > 0 && Math.abs(exp.imag()) < 1e-15) return Complex.ZERO;
            return Complex.ofCartesian(Double.NaN, Double.NaN);
        }
        if (exp.equals(Complex.ZERO) || (Math.abs(exp.real()) < 1e-15 && Math.abs(exp.imag()) < 1e-15)) return Complex.ofCartesian(1.0, 0.0);
        try {
            return normalizeZero(base.pow(exp));
        } catch (Exception e) {
            return Complex.ofCartesian(Double.NaN, Double.NaN);
        }
    }

    public static Complex safeSqrtComplex(Complex z) {
        if (z == null) return Complex.ZERO;
        z = normalizeZero(z);
        try {
            return normalizeZero(z.sqrt());
        } catch (Exception e) {
            return Complex.ofCartesian(Double.NaN, Double.NaN);
        }
    }

    public static Complex safeNthRootComplex(Complex z, Complex n) {
        if (z == null || n == null || n.equals(Complex.ZERO)) return Complex.ofCartesian(Double.NaN, Double.NaN);
        z = normalizeZero(z);
        n = normalizeZero(n);
        try {
            if (Math.abs(n.imag()) < 1e-12 && Math.abs(z.imag()) < 1e-12) {
                double nr = n.real();
                long nInt = Math.round(nr);
                if (Math.abs(nr - nInt) < 1e-9 && nInt % 2 != 0 && z.real() < 0) {
                    return Complex.ofCartesian(-Math.pow(-z.real(), 1.0 / nr), 0.0);
                }
            }
            Complex invN = Complex.ofCartesian(1.0, 0.0).divide(n);
            return normalizeZero(z.pow(invN));
        } catch (Exception e) {
            return Complex.ofCartesian(Double.NaN, Double.NaN);
        }
    }

    public static Complex safeDivideComplex(Complex num, Complex den) {
        if (num == null || den == null) return Complex.ZERO;
        if (den.equals(Complex.ZERO) || (Math.abs(den.real()) < 1e-15 && Math.abs(den.imag()) < 1e-15)) {
            if (num.equals(Complex.ZERO) || (Math.abs(num.real()) < 1e-15 && Math.abs(num.imag()) < 1e-15)) {
                return Complex.ofCartesian(1.0, 0.0);
            }
            return Complex.ofCartesian((num.real() >= 0 ? 1.0 : -1.0) * 1000.0, 0.0);
        }
        try {
            return num.divide(den);
        } catch (Exception e) {
            return Complex.ofCartesian(Double.NaN, Double.NaN);
        }
    }

    public static double safeSqrt(double val) {
        if (Double.isNaN(val)) return Double.NaN;
        if (val < 0.0) {
            return -Math.sqrt(-val);
        }
        return Math.sqrt(val);
    }

    public static double safeNthRoot(double val, double n) {
        if (Double.isNaN(val) || Double.isNaN(n)) return Double.NaN;
        if (Math.abs(n) < 1e-12) return Double.NaN;
        if (val == 0.0) return 0.0;
        if (Math.abs(n - 2.0) < 1e-12) return safeSqrt(val);
        long roundN = Math.round(n);
        boolean isOddInt = Math.abs(n - roundN) < 1e-9 && (roundN % 2 != 0);
        if (val < 0) {
            return -Math.pow(-val, 1.0 / n);
        }
        return Math.pow(val, 1.0 / n);
    }

    public static double safeAcos(double val) {
        if (Double.isNaN(val)) return 0.0;
        if (val <= -1.0) return Math.PI;
        if (val >= 1.0) return 0.0;
        return Math.acos(val);
    }

    public static double safeAsin(double val) {
        if (Double.isNaN(val)) return 0.0;
        if (val <= -1.0) return -Math.PI / 2.0;
        if (val >= 1.0) return Math.PI / 2.0;
        return Math.asin(val);
    }

    public static double safeLog(double val) {
        if (Double.isNaN(val)) return 0.0;
        if (val == 0.0) return -1000.0;
        return Math.log(Math.abs(val));
    }

    public static double safeLog10(double val) {
        if (Double.isNaN(val)) return 0.0;
        if (val == 0.0) return -1000.0;
        return Math.log10(Math.abs(val));
    }

    public static double safeLogBase(double val, double base) {
        if (Double.isNaN(val) || Double.isNaN(base)) return Double.NaN;
        if (Math.abs(base - 1.0) < 1e-12 || base <= 0.0) return Double.NaN;
        if (val == 0.0) return -1000.0;
        return Math.log(Math.abs(val)) / Math.log(base);
    }

    public static boolean containsVariableY(String s) {
        if (s == null) return false;
        int len = s.length();
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            if (c == 'y' || c == 'Y') {
                if (i >= 7 && s.substring(i - 7, i + 1).equalsIgnoreCase("infinity")) {
                    continue;
                }
                if (i > 0 && (s.charAt(i - 1) == 'd' || s.charAt(i - 1) == 'D')) {
                    if (i == 1 || !Character.isLetter(s.charAt(i - 2))) {
                        continue;
                    }
                }
                boolean prevLetter = (i > 0 && Character.isLetter(s.charAt(i - 1)));
                boolean nextLetter = (i + 1 < len && Character.isLetter(s.charAt(i + 1)));
                if (!prevLetter && !nextLetter) {
                    return true;
                }
            }
        }
        return false;
    }

    private static volatile double defaultPeriod = 2.0 * Math.PI;

    public static void setDefaultPeriod(double period) {
        defaultPeriod = (period > 0 && Double.isFinite(period)) ? period : 2.0 * Math.PI;
    }

    public static double getDefaultPeriod() {
        return defaultPeriod;
    }

    public static java.util.List<String> splitTopLevelColons(String str) {
        java.util.List<String> list = new java.util.ArrayList<>();
        if (str == null) return list;
        int dParen = 0, dBracket = 0, dBrace = 0;
        int start = 0;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '(') dParen++;
            else if (c == ')') { if (dParen > 0) dParen--; }
            else if (c == '[') dBracket++;
            else if (c == ']') { if (dBracket > 0) dBracket--; }
            else if (c == '{') dBrace++;
            else if (c == '}') { if (dBrace > 0) dBrace--; }
            else if (c == ':' && dParen == 0 && dBracket == 0 && dBrace == 0) {
                list.add(str.substring(start, i));
                start = i + 1;
            }
        }
        list.add(str.substring(start));
        return list;
    }

    public static java.util.List<String> splitTopLevelCommas(String str) {
        java.util.List<String> list = new java.util.ArrayList<>();
        if (str == null) return list;
        int dParen = 0, dBracket = 0, dBrace = 0;
        int start = 0;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '(') dParen++;
            else if (c == ')') { if (dParen > 0) dParen--; }
            else if (c == '[') dBracket++;
            else if (c == ']') { if (dBracket > 0) dBracket--; }
            else if (c == '{') dBrace++;
            else if (c == '}') { if (dBrace > 0) dBrace--; }
            else if (c == ',' && dParen == 0 && dBracket == 0 && dBrace == 0) {
                list.add(str.substring(start, i));
                start = i + 1;
            }
        }
        list.add(str.substring(start));
        return list;
    }

    public static int findTopLevelEquals(String str) {
        if (str == null) return -1;
        int dParen = 0, dBracket = 0, dBrace = 0;
        int lastColon = -1;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '(') dParen++;
            else if (c == ')') { if (dParen > 0) dParen--; }
            else if (c == '[') dBracket++;
            else if (c == ']') { if (dBracket > 0) dBracket--; }
            else if (c == '{') dBrace++;
            else if (c == '}') { if (dBrace > 0) dBrace--; }
            else if (c == ':' && dParen == 0 && dBracket == 0 && dBrace == 0) {
                lastColon = i;
            }
        }
        int searchStart = (lastColon != -1) ? lastColon + 1 : 0;
        dParen = 0; dBracket = 0; dBrace = 0;
        for (int i = searchStart; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '(') dParen++;
            else if (c == ')') { if (dParen > 0) dParen--; }
            else if (c == '[') dBracket++;
            else if (c == ']') { if (dBracket > 0) dBracket--; }
            else if (c == '{') dBrace++;
            else if (c == '}') { if (dBrace > 0) dBrace--; }
            else if (c == '=' && dParen == 0 && dBracket == 0 && dBrace == 0) {
                return i;
            }
        }
        return -1;
    }

    public static boolean isImplicit(String str) {
        if (str == null) return false;
        String s = str.trim();
        int eq = findTopLevelEquals(s);
        if (containsVariableY(s)) {
            if (eq != -1) {
                String lhs = s.substring(0, eq).trim();
                String rhs = s.substring(eq + 1).trim();
                if ((lhs.equalsIgnoreCase("y") || lhs.matches("(?i)^[a-zA-Z_][a-zA-Z0-9_]*\\s*([\\(\\[])\\s*[xtnk]\\s*([\\)\\]])$")) && !containsVariableY(rhs)) {
                    return false;
                }
            }
            return true;
        }
        if (eq != -1) {
            String lhs = s.substring(0, eq).trim();
            if (lhs.isEmpty() || lhs.equalsIgnoreCase("y") || lhs.matches("(?i)^[a-zA-Z_][a-zA-Z0-9_]*(\\s*([\\(\\[])\\s*[a-zA-Z0-9_,\\s]*([\\)\\]]))?$")) {
                return false;
            }
            return true;
        }
        return false;
    }

    public static String normalize(String str) {
        return normalize(str, false);
    }

    private static final java.util.regex.Pattern COMPLEX_NORM_PATTERN =
            java.util.regex.Pattern.compile("(?<![a-zA-Z0-9_\\.\\(\\]])(-?\\d+(?:\\.\\d+)?)\\s*([+\\-])\\s*((?:\\d+(?:\\.\\d+)?\\s*)?[ij](?:\\^(?:\\([^)]+\\)|\\{[^}]+\\}|[0-9a-zA-Z]+))?)(?![a-zA-Z0-9_])");
    
    private static final java.util.regex.Pattern SPACE_IJ_PATTERN =
            java.util.regex.Pattern.compile("(?<=\\d)\\s+([ij])\\b");
    
    private static final java.util.regex.Pattern SUB_PAREN_PATTERN =
            java.util.regex.Pattern.compile("([a-zA-Z]+)_\\(([a-zA-Z0-9]+)\\)");
    
    private static final java.util.regex.Pattern SUB_BRACE_PATTERN =
            java.util.regex.Pattern.compile("([a-zA-Z]+)_\\{([a-zA-Z0-9]+)\\}");
    
    private static final java.util.regex.Pattern SUB_BRACKET_PATTERN =
            java.util.regex.Pattern.compile("([a-zA-Z]+)_\\[([a-zA-Z0-9]+)\\]");
    
    // Complex conjugate notation: y*[n] → conj(y_n), h*[k] → conj(h_k), etc.
    // Matches letter(s) followed by * and then [subscript] — the * denotes conjugate, not multiplication.
    private static final java.util.regex.Pattern CONJ_BRACKET_PATTERN =
            java.util.regex.Pattern.compile("([a-zA-Z]+)\\*\\[([a-zA-Z0-9]+)\\]");
    
    // Discrete signal subscript without underscore: x[n] → x_n, h[k] → h_k, etc.
    // Only matches single-letter variable names to avoid collisions with functions like sin[...], log[...].
    // Negative lookbehind excludes ∑, ∫, √ and similar operators that use [] for limits/indices,
    // as well as preceding letters (to avoid matching e.g. the 'g' in 'log[e]').
    private static final java.util.regex.Pattern SIGNAL_BRACKET_PATTERN =
            java.util.regex.Pattern.compile("(?<![a-zA-Z∑∫√\\u221A\\u222B\\u2211\\u03A3_])([a-zA-Z])\\[([a-zA-Z][a-zA-Z0-9]*)\\]");
    
    private static final java.util.regex.Pattern FRAC_D_ORDER_PATTERN =
            java.util.regex.Pattern.compile("(?i)\\\\frac\\s*\\{\\s*d(?:\\^\\{?(\\d+)\\}?)?\\s*\\}\\s*\\{\\s*d(?:\\^\\{?(\\d+)\\}?\\s*([a-zA-Z])|([a-zA-Z])(?:\\^\\{?(\\d+)\\}?)?)\\s*\\}");
    
    private static final java.util.regex.Pattern FRAC_D_SIMPLE_PATTERN =
            java.util.regex.Pattern.compile("(?i)\\\\frac\\s*\\{\\s*d\\s*\\}\\s*\\{\\s*d([a-zA-Z])\\s*\\}");
    private static final java.util.regex.Pattern FRAC_PARTIAL_PATTERN =
            java.util.regex.Pattern.compile("(?i)\\\\frac\\s*\\{\\s*\\\\partial\\s*\\}\\s*\\{\\s*\\\\partial([a-zA-Z])\\s*\\}");
    
    private static final java.util.regex.Pattern PARTIAL_SLASH_PATTERN =
            java.util.regex.Pattern.compile("(?i)\\\\partial/\\\\partial\\s*([a-zA-Z])");
    
    private static final java.util.regex.Pattern PARTIAL_UNICODE_SLASH_PATTERN =
            java.util.regex.Pattern.compile("(?i)∂/∂\\s*([a-zA-Z])");
    
    private static final java.util.regex.Pattern PAREN_D_SLASH_PATTERN =
            java.util.regex.Pattern.compile("(?i)\\(\\s*d/d([a-zA-Z])\\s*\\)");
    
    private static final java.util.regex.Pattern M_FRAC_NORM_PATTERN =
            java.util.regex.Pattern.compile("(?i)\\(\\s*([d\u2202]|\\\\partial)(?:\\s*\\^\\s*(?:\\(?\\d+\\)?|\\{\\d+\\}))?\\s*\\)\\s*/\\s*\\(\\s*([d\u2202]|\\\\partial)(?:\\s*\\^\\s*(?:\\(?\\d+\\)?|\\{\\d+\\}))?\\s*([a-zA-Z])(?:\\s*\\^\\s*(?:\\(?\\d+\\)?|\\{\\d+\\}))?\\s*\\)");
    
    private static final java.util.regex.Pattern DIFF_VAR_PAREN_PATTERN =
            java.util.regex.Pattern.compile("(?i)\\bdiff([a-zA-Z])\\s*\\(");
    
    private static final java.util.regex.Pattern DIFF_N_VAR_PAREN_PATTERN =
            java.util.regex.Pattern.compile("(?i)\\bdiff(\\d+)([a-zA-Z])\\s*\\(");
    
    private static final java.util.regex.Pattern DIFF_PAREN_PATTERN =
            java.util.regex.Pattern.compile("(?i)\\bdiff\\s*\\(");
    
    private static final java.util.regex.Pattern DIFF_WORD_PATTERN =
            java.util.regex.Pattern.compile("(?i)\\bdiff\\b");
    
    
    private static final java.util.regex.Pattern D_SLASH_D_PATTERN =
            java.util.regex.Pattern.compile("(?i)\\bd/d([a-zA-Z])\\b");
    
    private static final java.util.regex.Pattern PAREN_D_SLASH_D_PATTERN =
            java.util.regex.Pattern.compile("(?i)\\(\\s*d\\s*\\)\\s*/\\s*\\(\\s*d([a-zA-Z])\\s*\\)");
    
    private static final java.util.regex.Pattern PATTERN_FUNC_FLOOR =
            java.util.regex.Pattern.compile("(?i)\\bfloor\\s*\\(");
    
    private static final java.util.regex.Pattern PATTERN_FUNC_CEIL =
            java.util.regex.Pattern.compile("(?i)\\bceil\\s*\\(");
    
    private static final java.util.regex.Pattern PATTERN_FUNC_ABS =
            java.util.regex.Pattern.compile("(?i)\\babs\\s*\\(");

    public static String encapsulateComplexExpressions(String str) {
        if (str == null) return "";
        if (!str.contains("i") && !str.contains("j") && !str.contains("I") && !str.contains("J")) {
            return str;
        }
        str = SPACE_IJ_PATTERN.matcher(str).replaceAll("$1");

        java.util.regex.Matcher m = COMPLEX_NORM_PATTERN.matcher(str);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            int start = m.start();
            int end = m.end();
            int kBefore = start - 1;
            while (kBefore >= 0 && Character.isWhitespace(str.charAt(kBefore))) kBefore--;
            int kAfter = end;
            while (kAfter < str.length() && Character.isWhitespace(str.charAt(kAfter))) kAfter++;
            if (kBefore >= 0 && str.charAt(kBefore) == '(' && kAfter < str.length() && str.charAt(kAfter) == ')') {
                m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(m.group(0)));
            } else {
                String realPart = m.group(1);
                String op = m.group(2);
                String imagPart = m.group(3).replaceAll("\\s+", "");
                m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement("(" + realPart + " " + op + " " + imagPart + ")"));
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public static String normalizeForField(String str) {
        if (str == null) return "";
        str = encapsulateComplexExpressions(str);
        return normalize(str, true);
    }

    public static String normalize(String str, boolean preserveLhs) {
        if (str == null) return "";
        str = str.replace("\\(", "").replace("\\)", "");
        str = str.replace("\\[", "").replace("\\]", "");

        // Complex conjugate notation: y*[n] → conj(y_n), h*[k] → conj(h_k)
        // The * after a variable name and before [subscript] denotes conjugate, not multiplication.
        // Must be applied before SUB_BRACKET_PATTERN to capture these patterns.
        if (!preserveLhs) {
            str = CONJ_BRACKET_PATTERN.matcher(str).replaceAll("conj($1_$2)");
        }

        // Discrete signal subscript without underscore: x[n] → x_n, h[k] → h_k
        // Only for single-letter vars with letter subscripts, to avoid breaking function calls.
        // Applied only for parsing (not display), and before SUB_BRACKET_PATTERN.
        if (!preserveLhs) {
            str = SIGNAL_BRACKET_PATTERN.matcher(str).replaceAll("$1_$2");
        }

        str = SUB_PAREN_PATTERN.matcher(str).replaceAll("$1_$2");
        str = SUB_BRACE_PATTERN.matcher(str).replaceAll("$1_$2");
        str = SUB_BRACKET_PATTERN.matcher(str).replaceAll("$1_$2");

        java.util.List<String> colSegs = splitTopLevelColons(str);
        if (colSegs.size() > 1) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < colSegs.size(); i++) {
                if (i > 0) sb.append(" : ");
                sb.append(normalize(colSegs.get(i).trim(), preserveLhs));
            }
            return sb.toString();
        }

        // Space normalization between number and i or j: 4 j -> 4j, 4 i -> 4i
        if (str.contains("i") || str.contains("j") || str.contains("I") || str.contains("J")) {
            str = SPACE_IJ_PATTERN.matcher(str).replaceAll("$1");
        }

        int eqIndex = findTopLevelEquals(str);
        if (eqIndex != -1) {
            if (preserveLhs || isImplicit(str)) {
                String left = str.substring(0, eqIndex);
                String right = str.substring(eqIndex + 1);
                return normalize(left, preserveLhs) + "=" + normalize(right, preserveLhs);
            } else {
                str = str.substring(eqIndex + 1);
            }
        }

        // Derivative normalization (d/dx, d/dt, d/dy, d/dz, and higher order d^n/dx^n, d^n/d^nt)
        if (str.contains("/") || str.contains("\\") || str.contains("∂")) {
            str = FRAC_D_ORDER_PATTERN.matcher(str).replaceAll("(d^$1)/(d^$1$3$4)");
            str = FRAC_D_SIMPLE_PATTERN.matcher(str).replaceAll("(d)/(d$1)");
            if (preserveLhs) {
                str = FRAC_PARTIAL_PATTERN.matcher(str).replaceAll("(∂)/(∂$1)");
                str = PARTIAL_SLASH_PATTERN.matcher(str).replaceAll("(∂)/(∂$1)");
                str = PARTIAL_UNICODE_SLASH_PATTERN.matcher(str).replaceAll("(∂)/(∂$1)");
            } else {
                str = FRAC_PARTIAL_PATTERN.matcher(str).replaceAll("(d)/(d$1)");
                str = PARTIAL_SLASH_PATTERN.matcher(str).replaceAll("(d)/(d$1)");
                str = PARTIAL_UNICODE_SLASH_PATTERN.matcher(str).replaceAll("(d)/(d$1)");
            }
            str = PAREN_D_SLASH_PATTERN.matcher(str).replaceAll("(d)/(d$1)");

            java.util.regex.Matcher mFracNorm = M_FRAC_NORM_PATTERN.matcher(str);
            StringBuffer sbNorm = new StringBuffer();
            while (mFracNorm.find()) {
                String fullMatch = mFracNorm.group(0);
                int slashIdx = fullMatch.indexOf('/');
                String numStr = fullMatch.substring(1, slashIdx - 1).trim();
                String denStr = fullMatch.substring(slashIdx + 2, fullMatch.length() - 1).trim();
                DiffNum dn = parseDiffNum(numStr);
                DiffDen dd = parseDiffDen(denStr);
                if (dn != null && dd != null && dn.order == dd.order && dd.var != '\0') {
                    if (preserveLhs) {
                        boolean isPartial = numStr.contains("∂") || numStr.contains("\\partial") || denStr.contains("∂") || denStr.contains("\\partial");
                        String dSymbol = isPartial ? "∂" : "d";
                        String numRep = (dn.order > 1) ? "(" + dSymbol + "^(" + dn.order + "))" : "(" + dSymbol + ")";
                        mFracNorm.appendReplacement(sbNorm, numRep + "/(" + denStr + ")");
                    } else {
                        String diffName = "diff" + (dn.order > 1 ? dn.order : "") + dd.var;
                        mFracNorm.appendReplacement(sbNorm, diffName);
                    }
                } else {
                    mFracNorm.appendReplacement(sbNorm, java.util.regex.Matcher.quoteReplacement(fullMatch));
                }
            }
            mFracNorm.appendTail(sbNorm);
            str = sbNorm.toString();

            if (preserveLhs) {
                str = DIFF_VAR_PAREN_PATTERN.matcher(str).replaceAll("(d)/(d$1)(");
                str = DIFF_N_VAR_PAREN_PATTERN.matcher(str).replaceAll("(d^($1))/(d^($1)$2)(");
                str = DIFF_PAREN_PATTERN.matcher(str).replaceAll("(d)/(dx)(");
                str = DIFF_WORD_PATTERN.matcher(str).replaceAll("(d)/(dx)");
                str = D_SLASH_D_PATTERN.matcher(str).replaceAll("(d)/(d$1)");
                str = PAREN_D_SLASH_D_PATTERN.matcher(str).replaceAll("(d)/(d$1)");
            } else {
                str = PAREN_D_SLASH_D_PATTERN.matcher(str).replaceAll("diff$1");
                str = str.replaceAll("(?i)(?<![a-zA-Z0-9])d\\^(\\d+)/d\\^\\1([xyzt])\\b", "diff$1$2");
                str = str.replaceAll("(?i)(?<![a-zA-Z0-9])d\\^(\\d+)/d([xyzt])\\^\\1\\b", "diff$1$2");
                str = D_SLASH_D_PATTERN.matcher(str).replaceAll("diff$1");
                str = str.replaceAll("(?i)d/dx", "diffx");
                str = str.replaceAll("(?i)d/dt", "difft");
                str = str.replaceAll("(?i)d/dy", "diffy");
                str = str.replaceAll("(?i)d/dz", "diffz");
                str = DIFF_WORD_PATTERN.matcher(str).replaceAll("diffx");
            }
        }

        if (str.contains("\\")) {
            str = str.replaceAll("(?i)\\\\iiint", "\u222B\u222B\u222B");
            str = str.replaceAll("(?i)\\\\iint", "\u222B\u222B");
            str = str.replaceAll("(?i)\\\\int", "\u222B");
            str = str.replaceAll("(?i)\\\\sum", "∑");
            str = str.replaceAll("(?i)\\\\delta", "δ");
            str = str.replaceAll("(?i)\\\\sha", "Ш");
            str = str.replaceAll("(?i)\\\\comb", "Ш");
            str = str.replaceAll("(?i)\\\\infty", "∞");
            str = str.replaceAll("(?i)\\\\pi\\b", "π");
        }
        if (str.toLowerCase().contains("pi")) {
            str = str.replaceAll("(?i)(?<=\\d)pi(?![a-zA-Z])", "*π");
            str = str.replaceAll("(?i)(?<![a-zA-Z])pi(?![a-zA-Z])", "π");
        }
        if (str.toLowerCase().contains("drcd")) {
            str = str.replaceAll("(?i)\\bdrcd\\b", "δ");
        }
        if (str.toLowerCase().contains("drcc")) {
            str = str.replaceAll("(?i)\\bdrcc\\b", "Ш");
        }
        if (str.toLowerCase().contains("infinity")) {
            str = str.replaceAll("(?i)\\binfinity\\b", "∞");
        }
        if (str.toLowerCase().contains("inf")) {
            str = str.replaceAll("(?i)\\binf\\b", "∞");
        }
        if (str.toLowerCase().contains("sum")) {
            str = str.replaceAll("(?i)\\bsum(?=\\s*[\\[\\(_])", "∑");
        }
        str = str.replace("\u03A3", "∑");
        str = str.replaceAll("[\u0394\u03B4](?=\\s*[\\[\\(])", "δ");
        str = str.replaceAll("(?i)\\bdelta(?=\\s*[\\[\\(])", "δ");

        while (str.toLowerCase().contains("sum_")) {
            int sIdx = str.toLowerCase().indexOf("sum_");
            int curr = sIdx + 4;
            String lowerPart = "";
            if (curr < str.length() && str.charAt(curr) == '{') {
                int cl = findClosingBrace(str, curr);
                if (cl != -1) {
                    lowerPart = "[" + str.substring(curr + 1, cl) + "]";
                    curr = cl + 1;
                }
            }
            String upperPart = "";
            while (curr < str.length() && str.charAt(curr) == ' ') curr++;
            if (curr < str.length() && str.charAt(curr) == '^') {
                curr++;
                while (curr < str.length() && str.charAt(curr) == ' ') curr++;
                if (curr < str.length() && str.charAt(curr) == '{') {
                    int cl = findClosingBrace(str, curr);
                    if (cl != -1) {
                        upperPart = "[" + str.substring(curr + 1, cl) + "]";
                        curr = cl + 1;
                    }
                }
            }
            if (!lowerPart.isEmpty()) {
                str = str.substring(0, sIdx) + "∑" + upperPart + lowerPart + str.substring(curr);
                continue;
            }
            break;
        }

        while (str.toLowerCase().contains("log_")) {
            int lIdx = str.toLowerCase().indexOf("log_");
            int curr = lIdx + 4;
            String basePart = "";
            if (curr < str.length() && str.charAt(curr) == '{') {
                int cl = findClosingBrace(str, curr);
                if (cl != -1) {
                    basePart = "[" + str.substring(curr + 1, cl) + "]";
                    curr = cl + 1;
                }
            } else if (curr < str.length() && str.charAt(curr) == '[') {
                int cl = findClosingDelimiter(str, curr, '[', ']');
                if (cl != -1) {
                    basePart = str.substring(curr, cl + 1);
                    curr = cl + 1;
                }
            } else if (curr < str.length() && str.charAt(curr) == '(') {
                int cl = findClosingDelimiter(str, curr, '(', ')');
                if (cl != -1) {
                    basePart = "[" + str.substring(curr + 1, cl) + "]";
                    curr = cl + 1;
                }
            } else if (curr < str.length() && Character.isLetterOrDigit(str.charAt(curr))) {
                basePart = "[" + str.charAt(curr) + "]";
                curr++;
            }
            if (!basePart.isEmpty()) {
                str = str.substring(0, lIdx) + "log" + basePart + str.substring(curr);
                continue;
            }
            break;
        }

        if (str.contains("log") || str.contains("Log") || str.contains("LOG")
                || str.contains("ln") || str.contains("Ln") || str.contains("LN")) {
            str = str.replaceAll("(?i)\\bln(?=\\s*\\()", "log[e]");
            str = str.replaceAll("(?i)\\blog10(?=\\s*\\()", "log[10]");
            str = str.replaceAll("(?i)\\blog2(?=\\s*\\()", "log[2]");
            str = str.replaceAll("(?i)\\blog(?=\\s*\\()", "log[10]");
        }

        String trimmed = str.trim();
        if (!str.contains("\u222B")) {
            if (trimmed.startsWith("[") && 
                (trimmed.toLowerCase().endsWith("dx") || trimmed.toLowerCase().endsWith("dt") || trimmed.matches("(?i).*\\)\\s*d[xt]\\s*$"))) {
                str = "\u222B" + trimmed;
            }
        }
        if (!str.contains("\u222B") && !trimmed.matches("(?i)^d[xyztwu]$") && !trimmed.matches("(?i)^d[xyztwu](\\s+d[xyztwu])*$")) {
            str = str.replaceAll("(?i)(?<![/\\(d])\\bdx\\b", "");
            str = str.replaceAll("(?i)(?<![/\\(d])\\bdt\\b", "");
            str = str.replaceAll("(?i)(?<![/\\(d])\\bdy\\b", "");
            str = str.replaceAll("(?i)(?<![/\\(d])\\bdz\\b", "");
        }

        while (str.toLowerCase().contains("\\sqrt")) {
            int sqrtIdx = str.toLowerCase().indexOf("\\sqrt");
            int curr = sqrtIdx + 5;
            String indexPart = "";
            if (curr < str.length() && str.charAt(curr) == '[') {
                int brEnd = str.indexOf(']', curr);
                if (brEnd != -1) {
                    indexPart = "[" + str.substring(curr + 1, brEnd) + "]";
                    curr = brEnd + 1;
                }
            }
            if (curr < str.length() && str.charAt(curr) == '{') {
                int contentEnd = findClosingBrace(str, curr);
                if (contentEnd != -1) {
                    String content = str.substring(curr + 1, contentEnd);
                    str = str.substring(0, sqrtIdx) + "\u221A" + indexPart + "(" + content + ")" + str.substring(contentEnd + 1);
                    continue;
                }
            }
            break;
        }

        if (str.contains("sqrt") || str.contains("Sqrt") || str.contains("SQRT") || str.contains("√") || str.contains("\u221A")) {
            str = str.replaceAll("\\[([^\\]]*)\\]\u221A", "\u221A[$1]");
            str = str.replaceAll("(?i)sqrt", "\u221A");
            str = str.replaceAll("(\u221A(\\[[^\\]]*\\])?)\\(\\)\\(", "$1(");
        }

        if (str.contains("\\")) {
            str = str.replace("\\cdot", "*");
            str = str.replace("\\times", "*");
            str = str.replace("\\left(", "(").replace("\\right)", ")");
            str = str.replace("\\left[", "(").replace("\\right]", ")");
        }

        while (str.toLowerCase().contains("\\frac")) {
            int fracIdx = str.toLowerCase().indexOf("\\frac");
            int firstBraceStart = str.indexOf('{', fracIdx);
            if (firstBraceStart == -1) break; 
            int firstBraceEnd = findClosingBrace(str, firstBraceStart);
            if (firstBraceEnd == -1) break;

            int secondBraceStart = str.indexOf('{', firstBraceEnd);
            if (secondBraceStart == -1) break;
            int secondBraceEnd = findClosingBrace(str, secondBraceStart);
            if (secondBraceEnd == -1) break;

            String num = str.substring(firstBraceStart + 1, firstBraceEnd);
            String den = str.substring(secondBraceStart + 1, secondBraceEnd);

            str = str.substring(0, fracIdx) + "((" + num + ")/(" + den + "))" + str.substring(secondBraceEnd + 1);
        }

        if (str.contains("\\")) {
            str = str.replaceAll("(?i)\\\\left\\\\lfloor", "\u230A").replaceAll("(?i)\\\\right\\\\rfloor", "\u230B");
            str = str.replaceAll("(?i)\\\\left\\\\lceil", "\u2308").replaceAll("(?i)\\\\right\\\\rceil", "\u2309");
            str = str.replaceAll("(?i)\\\\lfloor", "\u230A").replaceAll("(?i)\\\\rfloor", "\u230B");
            str = str.replaceAll("(?i)\\\\lceil", "\u2308").replaceAll("(?i)\\\\rceil", "\u2309");
            str = str.replaceAll("(?i)\\\\vert", "|");
            str = str.replaceAll("(?i)\\\\left\\|", "|").replaceAll("(?i)\\\\right\\|", "|");
        }

        if (preserveLhs) {
            if (str.contains("floor") || str.contains("Floor")) str = convertFuncToDelimiters(str, "floor", '\u230A', '\u230B');
            if (str.contains("ceil") || str.contains("Ceil")) str = convertFuncToDelimiters(str, "ceil", '\u2308', '\u2309');
            if (str.contains("abs") || str.contains("Abs")) str = convertFuncToDelimiters(str, "abs", '|', '|');
        } else {
            if (str.indexOf('\u230A') != -1 || str.indexOf('⌊') != -1) {
                str = convertDelimitersToFunc(str, '\u230A', '\u230B', "floor");
                str = convertDelimitersToFunc(str, '⌊', '⌋', "floor");
            }
            if (str.indexOf('\u2308') != -1 || str.indexOf('⌈') != -1) {
                str = convertDelimitersToFunc(str, '\u2308', '\u2309', "ceil");
                str = convertDelimitersToFunc(str, '⌈', '⌉', "ceil");
            }
            if (str.indexOf('|') != -1) {
                str = convertAbsBarsToFunc(str);
            }
        }

        if (str.contains("\\")) {
            str = str.replace("\\", "");
        }
        if (str.indexOf(')') != -1 || str.indexOf(']') != -1 || str.indexOf('}') != -1 || str.indexOf('⌋') != -1 || str.indexOf('⌉') != -1 || str.indexOf('\u230B') != -1 || str.indexOf('\u2309') != -1) {
            str = removeUnmatchedClosingDelimiters(str);
        }

        return str;
    }

    public static int findClosingDelimiter(String s, int openIdx, char openCh, char closeCh) {
        if (s == null || openIdx < 0 || openIdx >= s.length()) return -1;
        int depth = 1;
        for (int i = openIdx + 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == openCh) depth++;
            else if (c == closeCh) depth--;
            if (depth == 0) return i;
        }
        return -1;
    }

    public static String convertFuncToDelimiters(String str, String funcName, char openCh, char closeCh) {
        if (str == null || str.isEmpty()) return "";
        java.util.regex.Pattern p;
        if ("floor".equalsIgnoreCase(funcName)) p = PATTERN_FUNC_FLOOR;
        else if ("ceil".equalsIgnoreCase(funcName)) p = PATTERN_FUNC_CEIL;
        else if ("abs".equalsIgnoreCase(funcName)) p = PATTERN_FUNC_ABS;
        else p = java.util.regex.Pattern.compile("(?i)\\b" + funcName + "\\s*\\(");

        java.util.regex.Matcher m = p.matcher(str);
        if (!m.find()) return str;

        StringBuilder sb = new StringBuilder(str.length());
        int lastIdx = 0;
        int searchFrom = 0;
        while (searchFrom < str.length() && m.find(searchFrom)) {
            sb.append(str, lastIdx, m.start());
            int openParen = m.end() - 1;
            int closeParen = findClosingDelimiter(str, openParen, '(', ')');
            if (closeParen == -1) {
                sb.append(str, m.start(), m.end());
                lastIdx = m.end();
                searchFrom = m.end();
                continue;
            }
            String inside = str.substring(openParen + 1, closeParen);
            sb.append(openCh).append(inside).append(closeCh);
            lastIdx = closeParen + 1;
            searchFrom = closeParen + 1;
        }
        if (lastIdx < str.length()) {
            sb.append(str, lastIdx, str.length());
        }
        return sb.toString();
    }

    public static String convertDelimitersToFunc(String str, char openCh, char closeCh, String funcName) {
        if (str == null || !str.contains(String.valueOf(openCh))) return str;
        StringBuilder sb = new StringBuilder(str);
        boolean changed = true;
        while (changed) {
            changed = false;
            int openIdx = -1;
            for (int i = 0; i < sb.length(); i++) {
                char c = sb.charAt(i);
                if (c == openCh) {
                    openIdx = i;
                } else if (c == closeCh && openIdx != -1) {
                    String inside = sb.substring(openIdx + 1, i);
                    String replacement = funcName + "(" + inside + ")";
                    sb.replace(openIdx, i + 1, replacement);
                    changed = true;
                    break;
                }
            }
        }
        return sb.toString();
    }

    public static String convertAbsBarsToFunc(String str) {
        if (str == null || !str.contains("|")) return str;
        StringBuilder sb = new StringBuilder(str);
        boolean changed = true;
        int maxIters = 50;
        while (changed && maxIters-- > 0) {
            changed = false;
            AbsoluteValueSuperScript.AbsoluteValueRegion[] regions = AbsoluteValueSuperScript.findRegions(sb.toString());
            if (regions != null && regions.length > 0) {
                AbsoluteValueSuperScript.AbsoluteValueRegion best = null;
                for (AbsoluteValueSuperScript.AbsoluteValueRegion r : regions) {
                    if (r != null && r.openChar >= 0 && r.closeChar > r.openChar) {
                        if (best == null || (r.closeChar - r.openChar) < (best.closeChar - best.openChar)) {
                            best = r;
                        }
                    }
                }
                if (best != null) {
                    String inside = sb.substring(best.openChar + 1, best.closeChar);
                    sb.replace(best.openChar, best.closeChar + 1, "abs(" + inside + ")");
                    changed = true;
                }
            }
        }
        return sb.toString();
    }

    public static String removeUnmatchedClosingDelimiters(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        int[] stack = new int[s.length()];
        int top = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(' || c == '[' || c == '{') {
                stack[top++] = c;
                sb.append(c);
            } else if (c == ')') {
                int matchIdx = -1;
                for (int k = top - 1; k >= 0; k--) {
                    if (stack[k] == '(') {
                        matchIdx = k;
                        break;
                    }
                }
                if (matchIdx != -1) {
                    top = matchIdx;
                    sb.append(c);
                }
            } else if (c == ']') {
                int matchIdx = -1;
                for (int k = top - 1; k >= 0; k--) {
                    if (stack[k] == '[') {
                        matchIdx = k;
                        break;
                    }
                }
                if (matchIdx != -1) {
                    top = matchIdx;
                    sb.append(c);
                }
            } else if (c == '}') {
                int matchIdx = -1;
                for (int k = top - 1; k >= 0; k--) {
                    if (stack[k] == '{') {
                        matchIdx = k;
                        break;
                    }
                }
                if (matchIdx != -1) {
                    top = matchIdx;
                    sb.append(c);
                }
            } else if (c == '\u230A' || c == '⌊') {
                stack[top++] = '\u230A';
                sb.append(c);
            } else if (c == '\u230B' || c == '⌋') {
                int matchIdx = -1;
                for (int k = top - 1; k >= 0; k--) {
                    if (stack[k] == '\u230A') {
                        matchIdx = k;
                        break;
                    }
                }
                if (matchIdx != -1) {
                    top = matchIdx;
                    sb.append(c);
                }
            } else if (c == '\u2308' || c == '⌈') {
                stack[top++] = '\u2308';
                sb.append(c);
            } else if (c == '\u2309' || c == '⌉') {
                int matchIdx = -1;
                for (int k = top - 1; k >= 0; k--) {
                    if (stack[k] == '\u2308') {
                        matchIdx = k;
                        break;
                    }
                }
                if (matchIdx != -1) {
                    top = matchIdx;
                    sb.append(c);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static int findClosingBrace(String s, int openIdx) {
        int depth = 1;
        for (int i = openIdx + 1; i < s.length(); i++) {
            if (s.charAt(i) == '{') depth++;
            else if (s.charAt(i) == '}') depth--;
            if (depth == 0) return i;
        }
        return -1;
    }

    public static void validate(String str) {
        if (str == null || str.trim().isEmpty()) {
            return;
        }

        int eq = findTopLevelEquals(str);
        if (eq != -1) {
            String lhsStr = str.substring(0, eq).trim();
            String rhsStr = str.substring(eq + 1).trim();
            if (!isImplicit(str)) {
                validate(rhsStr);
                return;
            }
            validate(lhsStr);
            validate(rhsStr);
            return;
        }

        // 1. Balanced delimiters
        int parens = 0, brackets = 0, braces = 0, floors = 0, ceils = 0, bars = 0;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '(') parens++;
            else if (c == ')') {
                parens--;
                if (parens < 0) throw new IllegalArgumentException("Unmatched closing parenthesis");
            } else if (c == '[') brackets++;
            else if (c == ']') {
                brackets--;
                if (brackets < 0) throw new IllegalArgumentException("Unmatched closing bracket");
            } else if (c == '{') braces++;
            else if (c == '}') {
                braces--;
                if (braces < 0) throw new IllegalArgumentException("Unmatched closing brace");
            } else if (c == '\u230A' || c == '⌊') floors++;
            else if (c == '\u230B' || c == '⌋') {
                floors--;
                if (floors < 0) throw new IllegalArgumentException("Unmatched closing floor");
            } else if (c == '\u2308' || c == '⌈') ceils++;
            else if (c == '\u2309' || c == '⌉') {
                ceils--;
                if (ceils < 0) throw new IllegalArgumentException("Unmatched closing ceil");
            } else if (c == '|') {
                bars++;
            }
        }
        if (parens != 0 || brackets != 0 || braces != 0 || floors != 0 || ceils != 0 || (bars % 2 != 0)) {
            throw new IllegalArgumentException("Unbalanced delimiters in expression");
        }

        // 2. Incomplete derivative fraction or invalid denominator / numerator
        java.util.regex.Matcher mFrac = java.util.regex.Pattern.compile("(?i)\\(((?:[^()]|\\([^()]*\\))*)\\)\\s*/\\s*\\(((?:[^()]|\\([^()]*\\))*)\\)").matcher(str);
        while (mFrac.find()) {
            String num = mFrac.group(1).trim();
            String den = mFrac.group(2).trim();
            DiffNum dn = parseDiffNum(num);
            DiffDen dd = parseDiffDen(den);
            if (dn != null || dd != null) {
                if (dn == null) {
                    throw new IllegalArgumentException("Invalid derivative numerator: " + num);
                }
                if (dd == null) {
                    throw new IllegalArgumentException("Invalid derivative denominator: " + den);
                }
                if (dd.var == '\0') {
                    throw new IncompleteExpressionException("Incomplete derivative denominator: " + den);
                }
                if (dn.order != dd.order) {
                    throw new IllegalArgumentException("Mismatched derivative order: " + dn.order + " vs " + dd.order);
                }
                int end = mFrac.end();
                while (end < str.length() && Character.isWhitespace(str.charAt(end))) {
                    end++;
                }
                if (end >= str.length()) {
                    throw new IncompleteExpressionException("Incomplete derivative operator");
                }
                if (str.charAt(end) != '(') {
                    throw new IllegalArgumentException("Derivative operator must be followed by argument in parentheses");
                }
            }
        }

        String s = str.replaceAll("\\s+", "");
        if (s.contains("()/(") || s.contains(")/()")) {
            throw new IncompleteExpressionException("Empty fraction");
        }
        if (s.matches(".*(?<![a-zA-Z\\(])d(?:\\^\\(?\\d+\\)?)?/d(?![a-zA-Z]|\\^).*") || s.matches(".*(?<![a-zA-Z\\(])d/(?![\\(da-zA-Z\u2202]).*")) {
            throw new IncompleteExpressionException("Incomplete derivative operator");
        }

        // 3. Non-fraction derivative operator d/d[a-zA-Z] or d^n/d^n[a-zA-Z] must be followed by '('
        java.util.regex.Matcher m2 = java.util.regex.Pattern.compile("(?i)(?<![a-zA-Z])d(?:\\^(?:\\(?(\\d+)\\)?|\\{(\\d+)\\}))?/d(?:\\^(?:\\(?\\1\\)?|\\{\\2\\})([a-zA-Z])|([a-zA-Z])(?:\\^(?:\\(?\\1\\)?|\\{\\2\\}))?)").matcher(str);
        while (m2.find()) {
            int end = m2.end();
            while (end < str.length() && Character.isWhitespace(str.charAt(end))) {
                end++;
            }
            if (end >= str.length()) {
                throw new IncompleteExpressionException("Incomplete derivative operator");
            }
            if (str.charAt(end) != '(') {
                throw new IllegalArgumentException("Derivative operator must be followed by argument in parentheses");
            }
        }
    }

    public static ComplexExpression parseComplex(String str) {
        return parseComplex(str, null, null);
    }

    public static ComplexExpression parseComplex(String str, Map<String, Expression> customFuncs) {
        return parseComplex(str, customFuncs, null);
    }

    public static ComplexExpression parseComplex(String str, Map<String, Expression> customFuncs, Map<String, ComplexExpression> parentDeclaredVars) {
        if (str == null || str.trim().isEmpty()) {
            return (u, v) -> Complex.ZERO;
        }
        validate(str);

        java.util.List<String> colonSegments = splitTopLevelColons(str);
        if (colonSegments.size() > 1) {
            Map<String, Expression> localCustom = (customFuncs != null) ? new java.util.LinkedHashMap<>(customFuncs) : new java.util.LinkedHashMap<>();
            Map<String, ComplexExpression> declaredVars = (parentDeclaredVars != null) ? new java.util.LinkedHashMap<>(parentDeclaredVars) : new java.util.LinkedHashMap<>();

            for (int i = 0; i < colonSegments.size() - 1; i++) {
                String segment = colonSegments.get(i).trim();
                java.util.List<String> decls = splitTopLevelCommas(segment);
                for (String decl : decls) {
                    decl = decl.trim();
                    if (decl.isEmpty()) continue;
                    int eq = findTopLevelEquals(decl);
                    if (eq != -1) {
                        String lhs = decl.substring(0, eq).trim();
                        String rhs = decl.substring(eq + 1).trim();
                        ComplexExpression rhsExpr = parseComplex(rhs, localCustom, declaredVars);
                        registerVariable(lhs, rhsExpr, declaredVars, localCustom);
                    }
                }
            }
            String mainExpr = colonSegments.get(colonSegments.size() - 1).trim();
            return parseComplex(mainExpr, localCustom, declaredVars);
        }

        Map<String, ComplexExpression> declaredVars = (parentDeclaredVars != null) ? parentDeclaredVars : java.util.Collections.emptyMap();

        int eq = findTopLevelEquals(str);
        if (eq != -1) {
            String lhsStr = str.substring(0, eq).trim();
            String rhsStr = str.substring(eq + 1).trim();
            if (!isImplicit(str)) {
                return parseComplex(rhsStr, customFuncs, declaredVars);
            }
            ComplexExpression lhsExpr = parseComplex(lhsStr, customFuncs, declaredVars);
            ComplexExpression rhsExpr = parseComplex(rhsStr, customFuncs, declaredVars);
            return (u, v) -> lhsExpr.eval(u, v).subtract(rhsExpr.eval(u, v));
        }

        final String normalizedStr = normalize(str);

        int normEq = findTopLevelEquals(normalizedStr);
        if (normEq != -1) {
            String lhsStr = normalizedStr.substring(0, normEq).trim();
            String rhsStr = normalizedStr.substring(normEq + 1).trim();
            ComplexExpression lhsExpr = parseComplex(lhsStr, customFuncs, declaredVars);
            ComplexExpression rhsExpr = parseComplex(rhsStr, customFuncs, declaredVars);
            return (u, v) -> lhsExpr.eval(u, v).subtract(rhsExpr.eval(u, v));
        }

        return parseComplexInternal(normalizedStr, customFuncs, declaredVars);
    }

    private static void registerVariable(String lhs, ComplexExpression rhsExpr,
                                         Map<String, ComplexExpression> declaredVars,
                                         Map<String, Expression> localCustom) {
        String varName = lhs;
        if ((lhs.matches("^[a-zA-Z][a-zA-Z0-9]*\\s*\\([a-zA-Z][a-zA-Z0-9]*\\)$") ||
             lhs.matches("^[a-zA-Z][a-zA-Z0-9]*\\s*\\[[a-zA-Z][a-zA-Z0-9]*\\]$")) && !lhs.contains("_")) {
            int cut = lhs.indexOf('(');
            if (cut == -1) cut = lhs.indexOf('[');
            varName = lhs.substring(0, cut).trim();
        }
        varName = varName.replaceAll("([a-zA-Z]+)_\\(([a-zA-Z0-9]+)\\)", "$1_$2")
                         .replaceAll("([a-zA-Z]+)_\\{([a-zA-Z0-9]+)\\}", "$1_$2")
                         .replaceAll("([a-zA-Z]+)_\\[([a-zA-Z0-9]+)\\]", "$1_$2");
        declaredVars.put(varName, rhsExpr);
        if (varName.contains("_")) {
            declaredVars.put(varName.replace("_", ""), rhsExpr);
        }
        Expression exprAdapter = new Expression() {
            @Override
            public double eval(double x, double y) {
                try {
                    Complex c = rhsExpr.eval(Complex.ofCartesian(x, 0.0), Complex.ofCartesian(y, 0.0));
                    return c != null ? c.real() : Double.NaN;
                } catch (Exception e) { return Double.NaN; }
            }
            @Override
            public Complex evalComplex(Complex x, Complex y) {
                return rhsExpr.eval(x, y);
            }
        };
        localCustom.put(varName, exprAdapter);
        if (varName.contains("_")) {
            localCustom.put(varName.replace("_", ""), exprAdapter);
        }
    }

    public static Expression parse(String str) {
        return parse(str, null);
    }

    public static Expression parse(String str, Map<String, Expression> customFuncs) {
        if (str == null || str.trim().isEmpty()) {
            return (u, v) -> 0.0;
        }
        final ComplexExpression root = parseComplex(str, customFuncs);
        return new Expression() {
            @Override
            public double eval(double x, double y) {
                try {
                    Complex res = root.eval(Complex.ofCartesian(x, 0.0), Complex.ofCartesian(y, 0.0));
                    if (res == null) return Double.NaN;
                    double r = res.real();
                    double im = res.imag();
                    if (!Double.isFinite(r)) return Double.NaN;
                    // On the real Cartesian 2D plane (y = f(x)), values with non-negligible
                    // imaginary parts are outside the function's real domain and evaluate to NaN.
                    if (Double.isFinite(im) && Math.abs(im) > 1e-10 * Math.max(1.0, Math.abs(r))) {
                        return Double.NaN;
                    }
                    return r;
                } catch (Exception e) {
                    return Double.NaN;
                }
            }
            @Override
            public Complex evalComplex(Complex x, Complex y) {
                return root.eval(x, y);
            }
        };
    }

    private static ComplexExpression parseComplexInternal(final String normalizedStr, final Map<String, Expression> customFuncs, final Map<String, ComplexExpression> declaredVars) {
        return new Object() {
            int pos = -1, ch;
            void nextChar() {
                ch = (++pos < normalizedStr.length()) ? normalizedStr.charAt(pos) : -1;
            }
            boolean eat(int charToEat) {
                while (ch == ' ') nextChar();
                if (ch == charToEat) {
                    nextChar();
                    return true;
                }
                return false;
            }
            ComplexExpression parse() {
                try {
                    nextChar();
                    ComplexExpression x = parseExpression();
                    while (pos < normalizedStr.length() && ch != -1) {
                        while (ch == ' ' || ch == ')' || ch == ']' || ch == '}') nextChar();
                        if (pos >= normalizedStr.length() || ch == -1) break;
                        try {
                            ComplexExpression next = parseExpression();
                            ComplexExpression prev = x;
                            x = (u, v) -> {
                                Complex v1 = prev.eval(u, v);
                                Complex v2 = next.eval(u, v);
                                if (v1 != null && v2 != null) return v1.multiply(v2);
                                if (v1 != null) return v1;
                                if (v2 != null) return v2;
                                return Complex.ZERO;
                            };
                        } catch (Exception ignore) {
                            nextChar();
                        }
                    }
                    return x != null ? x : ((u, v) -> Complex.ZERO);
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            ComplexExpression parseExpression() {
                ComplexExpression x = parseTerm();
                for (;;) {
                    while (ch == ' ') nextChar();
                    if (eat('+')) {
                        ComplexExpression a = x;
                        while (ch == ' ') nextChar();
                        ComplexExpression b = (ch == -1 || ch == '+' || ch == '-' || ch == '*' || ch == '/' || ch == ')' || ch == ']' || ch == '}' || ch == ',')
                                ? ((u, v) -> Complex.ZERO)
                                : parseTerm();
                        x = (u, v) -> a.eval(u, v).add(b.eval(u, v));
                    }
                    else if (eat('-')) {
                        ComplexExpression a = x;
                        while (ch == ' ') nextChar();
                        ComplexExpression b = (ch == -1 || ch == '+' || ch == '-' || ch == '*' || ch == '/' || ch == ')' || ch == ']' || ch == '}' || ch == ',')
                                ? ((u, v) -> Complex.ZERO)
                                : parseTerm();
                        x = (u, v) -> a.eval(u, v).subtract(b.eval(u, v));
                    }
                    else return x;
                }
            }
            ComplexExpression parseTerm() {
                ComplexExpression x = parseFactor();
                for (;;) {
                    while (ch == ' ') nextChar();
                    if (eat('*')) {
                        ComplexExpression a = x;
                        while (ch == ' ') nextChar();
                        ComplexExpression b = (ch == -1 || ch == '+' || ch == '-' || ch == '*' || ch == '/' || ch == ')' || ch == ']' || ch == '}' || ch == ',')
                                ? ((u, v) -> Complex.ofCartesian(1.0, 0.0))
                                : parseFactor();
                        x = (u, v) -> a.eval(u, v).multiply(b.eval(u, v));
                    }
                    else if (eat('/')) {
                        ComplexExpression a = x;
                        while (ch == ' ') nextChar();
                        ComplexExpression b = (ch == -1 || ch == '+' || ch == '-' || ch == '*' || ch == '/' || ch == ')' || ch == ']' || ch == '}' || ch == ',')
                                ? ((u, v) -> Complex.ofCartesian(1.0, 0.0))
                                : parseFactor();
                        x = (u, v) -> safeDivideComplex(a.eval(u, v), b.eval(u, v));
                    }
                    else if (ch != '+' && ch != '-' && ch != ')' && ch != ']' && ch != '}' && ch != '^' && ch != ',' && ch != -1) {
                        ComplexExpression a = x, b = parseFactor(); 
                        x = (u, v) -> a.eval(u, v).multiply(b.eval(u, v));
                    }
                    else return x;
                }
            }
            ComplexExpression parseLimitFactor() {
                while (ch == ' ') nextChar();
                int close = -1;
                if (eat('{')) close = '}';
                else if (eat('(')) close = ')';
                else if (eat('[')) close = ']';

                if (close != -1) {
                    while (ch == ' ') nextChar();
                    if (eat(close)) return null;
                    ComplexExpression e = parseExpression();
                    eat(close);
                    return e;
                }
                return parseFactor();
            }

            ComplexExpression parseSummation() {
                String varName = "i";
                ComplexExpression expr1 = null;
                ComplexExpression expr2 = null;
                String var1 = null;
                String var2 = null;

                while (ch == ' ') nextChar();
                if (eat('[')) {
                    while (ch == ' ') nextChar();
                    int startIdent = pos;
                    while (Character.isLetter(ch) || ch == '_') nextChar();
                    int endIdent = pos;
                    while (ch == ' ') nextChar();
                    if (ch == '=') {
                        if (endIdent > startIdent) {
                            var1 = normalizedStr.substring(startIdent, endIdent).toLowerCase();
                        }
                        nextChar(); // eat '='
                    } else {
                        pos = startIdent - 1;
                        nextChar();
                    }
                    while (ch == ' ') nextChar();
                    if (!eat(']')) {
                        expr1 = parseExpression();
                        while (ch == ')' || ch == '}' || ch == ' ') nextChar();
                        eat(']');
                    }
                    while (ch == ' ') nextChar();
                    if (eat('[')) {
                        while (ch == ' ') nextChar();
                        int startIdent2 = pos;
                        while (Character.isLetter(ch) || ch == '_') nextChar();
                        int endIdent2 = pos;
                        while (ch == ' ') nextChar();
                        if (ch == '=') {
                            if (endIdent2 > startIdent2) {
                                var2 = normalizedStr.substring(startIdent2, endIdent2).toLowerCase();
                            }
                            nextChar(); // eat '='
                        } else {
                            pos = startIdent2 - 1;
                            nextChar();
                        }
                        while (ch == ' ') nextChar();
                        if (!eat(']')) {
                            expr2 = parseExpression();
                            while (ch == ')' || ch == '}' || ch == ' ') nextChar();
                            eat(']');
                        }
                    }
                }

                ComplexExpression lowerExpr;
                ComplexExpression upperExpr;
                if (var1 != null) {
                    varName = var1;
                    lowerExpr = expr1 != null ? expr1 : (u, v) -> Complex.ofCartesian(1.0, 0.0);
                    upperExpr = expr2 != null ? expr2 : (u, v) -> Complex.ofCartesian(10.0, 0.0);
                } else if (var2 != null) {
                    varName = var2;
                    lowerExpr = expr2 != null ? expr2 : (u, v) -> Complex.ofCartesian(1.0, 0.0);
                    upperExpr = expr1 != null ? expr1 : (u, v) -> Complex.ofCartesian(10.0, 0.0);
                } else {
                    upperExpr = expr1 != null ? expr1 : (u, v) -> Complex.ofCartesian(10.0, 0.0);
                    lowerExpr = expr2 != null ? expr2 : (u, v) -> Complex.ofCartesian(1.0, 0.0);
                }
                while (ch == ' ' || ch == ')' || ch == ']' || ch == '}') nextChar();
                ComplexExpression summand;
                if (eat('(')) {
                    while (ch == ' ') nextChar();
                    if (eat(')')) {
                        summand = (u, v) -> Complex.ofCartesian(1.0, 0.0);
                    } else {
                        summand = parseExpression();
                        while (ch == ']' || ch == '}' || ch == ' ') nextChar();
                        eat(')');
                    }
                } else if (eat('[')) {
                    while (ch == ' ') nextChar();
                    if (eat(']')) {
                        summand = (u, v) -> Complex.ofCartesian(1.0, 0.0);
                    } else {
                        summand = parseExpression();
                        while (ch == ')' || ch == '}' || ch == ' ') nextChar();
                        eat(']');
                    }
                } else {
                    summand = parseFactor();
                }

                final String finalVar = varName;
                final ComplexExpression finalLower = lowerExpr;
                final ComplexExpression finalUpper = upperExpr;
                final ComplexExpression finalSummand = summand;
                return (u, v) -> {
                    int depth = sumDepth.get();
                    int intDepth = integralDepth.get();
                    if (depth >= MAX_SUM_DEPTH || (depth + intDepth) >= 5) {
                        return Complex.ZERO;
                    }

                    double lVal = finalLower.eval(u, v).real();
                    double uVal = finalUpper.eval(u, v).real();
                    if (Double.isNaN(lVal) || Double.isInfinite(lVal)) lVal = 0.0;
                    if (Double.isNaN(uVal) || Double.isInfinite(uVal)) uVal = 0.0;
                    lVal = Math.max(-5000.0, Math.min(5000.0, lVal));
                    uVal = Math.max(-5000.0, Math.min(5000.0, uVal));

                    int start = (int) Math.round(lVal);
                    int end = (int) Math.round(uVal);
                    if (start > end) {
                        int tmp = start; start = end; end = tmp;
                    }

                    int maxTerms;
                    if (depth == 0 && intDepth == 0) {
                        maxTerms = 100;
                    } else if (depth <= 1 && intDepth <= 1) {
                        maxTerms = 20;
                    } else {
                        maxTerms = 5;
                    }
                    if (end - start > maxTerms) {
                        end = start + maxTerms;
                    }

                    sumDepth.set(depth + 1);
                    Map<String, Double> ctx = sumContext.get();
                    Double oldVal = ctx.get(finalVar);
                    Complex total = Complex.ZERO;
                    try {
                        for (int idx = start; idx <= end; idx++) {
                            if ((idx & 7) == 0 && Thread.currentThread().isInterrupted()) {
                                throw new RuntimeException("interrupted");
                            }
                            ctx.put(finalVar, (double) idx);
                            Complex term = finalSummand.eval(u, v);
                            if (term != null && Double.isFinite(term.real()) && Double.isFinite(term.imag())) {
                                total = total.add(term);
                            }
                        }
                    } finally {
                        sumDepth.set(depth);
                        if (oldVal != null) ctx.put(finalVar, oldVal);
                        else ctx.remove(finalVar);
                    }
                    return total;
                };
            }

            ComplexExpression parseFactor() {
                while (ch == ' ') nextChar();
                if (eat('+')) return parseFactor();
                if (eat('-')) { ComplexExpression a = parseFactor(); return (u, v) -> a.eval(u, v).negate(); }
                if (eat('^')) {
                    ComplexExpression exp = parseFactor();
                    return (u, v) -> safePowComplex(Complex.ofCartesian(1.0, 0.0), exp.eval(u, v));
                }
                ComplexExpression x;
                int startPos = this.pos;
                if (eat('(')) {
                    while (ch == ' ') nextChar();
                    if (eat(')')) {
                        x = (u, v) -> Complex.ofCartesian(1.0, 0.0);
                    } else {
                        x = parseExpression();
                        eat(')');
                    }
                } else if (eat('[')) {
                    while (ch == ' ') nextChar();
                    if (eat(']')) {
                        x = (u, v) -> Complex.ofCartesian(1.0, 0.0);
                    } else {
                        x = parseExpression();
                        eat(']');
                    }
                } else if (eat('{')) {
                    while (ch == ' ') nextChar();
                    if (eat('}')) {
                        x = (u, v) -> Complex.ofCartesian(1.0, 0.0);
                    } else {
                        x = parseExpression();
                        eat('}');
                    }
                } else if (eat('\u230A') || eat('⌊')) {
                    while (ch == ' ') nextChar();
                    if (eat('\u230B') || eat('⌋')) {
                        x = (u, v) -> Complex.ZERO;
                    } else {
                        ComplexExpression inner = parseExpression();
                        while (ch == ' ' || ch == ')' || ch == ']' || ch == '}') nextChar();
                        if (!eat('\u230B')) eat('⌋');
                        x = (u, v) -> Complex.ofCartesian(Math.floor(inner.eval(u, v).real()), 0.0);
                    }
                } else if (eat('\u2308') || eat('⌈')) {
                    while (ch == ' ') nextChar();
                    if (eat('\u2309') || eat('⌉')) {
                        x = (u, v) -> Complex.ZERO;
                    } else {
                        ComplexExpression inner = parseExpression();
                        while (ch == ' ' || ch == ')' || ch == ']' || ch == '}') nextChar();
                        if (!eat('\u2309')) eat('⌉');
                        x = (u, v) -> Complex.ofCartesian(Math.ceil(inner.eval(u, v).real()), 0.0);
                    }
                } else if (eat('|')) {
                    while (ch == ' ') nextChar();
                    if (eat('|')) {
                        x = (u, v) -> Complex.ZERO;
                    } else {
                        ComplexExpression inner = parseExpression();
                        while (ch == ' ' || ch == ')' || ch == ']' || ch == '}') nextChar();
                        eat('|');
                        x = (u, v) -> Complex.ofCartesian(inner.eval(u, v).abs(), 0.0);
                    }
                } else if (eat('\u221A')) {
                    ComplexExpression degreeExpr = null;
                    while (ch == ' ') nextChar();
                    if (eat('[')) {
                        while (ch == ' ') nextChar();
                        if (!eat(']')) {
                            degreeExpr = parseExpression();
                            while (ch == ')' || ch == '}' || ch == ' ') nextChar();
                            eat(']');
                        }
                    }
                    ComplexExpression a = parseFactor();
                    if (degreeExpr == null) {
                        x = (u, v) -> safeSqrtComplex(a.eval(u, v));
                    } else {
                        final ComplexExpression deg = degreeExpr;
                        x = (u, v) -> safeNthRootComplex(a.eval(u, v), deg.eval(u, v));
                    }
                } else if (eat('\u221E')) {
                    x = (u, v) -> Complex.ofCartesian(Double.POSITIVE_INFINITY, 0.0);
                } else if (eat('\u2211') || eat('\u03A3')) {
                    x = parseSummation();
                } else if (eat('\u222B')) {
                    java.util.List<ComplexExpression> upperList = new java.util.ArrayList<>();
                    java.util.List<ComplexExpression> lowerList = new java.util.ArrayList<>();

                    for (;;) {
                        ComplexExpression uL = null;
                        ComplexExpression lL = null;
                        while (ch == ' ') nextChar();
                        if (eat('[')) {
                            while (ch == ' ') nextChar();
                            if (!eat(']')) {
                                uL = parseExpression();
                                while (ch == ')' || ch == '}' || ch == ' ') nextChar();
                                eat(']');
                            }
                            while (ch == ' ') nextChar();
                            if (eat('[')) {
                                while (ch == ' ') nextChar();
                                if (!eat(']')) {
                                    lL = parseExpression();
                                    while (ch == ')' || ch == '}' || ch == ' ') nextChar();
                                    eat(']');
                                }
                            }
                        } else {
                            for (int k = 0; k < 2; k++) {
                                while (ch == ' ') nextChar();
                                if (eat('_')) {
                                    lL = parseLimitFactor();
                                } else if (eat('^')) {
                                    uL = parseLimitFactor();
                                }
                            }
                        }
                        upperList.add(uL);
                        lowerList.add(lL);

                        while (ch == ' ') nextChar();
                        if (eat('\u222B')) {
                            continue;
                        } else {
                            break;
                        }
                    }

                    while (ch == ' ' || ch == ')' || ch == ']' || ch == '}') nextChar();
                    ComplexExpression integrand;
                    boolean isEmptyIntegrand = false;
                    if (eat('(')) {
                        while (ch == ' ') nextChar();
                        if (eat(')')) {
                            integrand = (u, v) -> Complex.ofCartesian(1.0, 0.0);
                            isEmptyIntegrand = true;
                        } else {
                            integrand = parseExpression();
                            while (ch == ']' || ch == '}' || ch == ' ') nextChar();
                            eat(')');
                        }
                    } else if (eat('[')) {
                        while (ch == ' ') nextChar();
                        if (eat(']')) {
                            integrand = (u, v) -> Complex.ofCartesian(1.0, 0.0);
                            isEmptyIntegrand = true;
                        } else {
                            integrand = parseExpression();
                            while (ch == ')' || ch == '}' || ch == ' ') nextChar();
                            eat(']');
                        }
                    } else {
                        integrand = parseFactor();
                    }

                    boolean isConstIntegrand = isEmptyIntegrand;
                    Complex constVal = isEmptyIntegrand ? Complex.ofCartesian(1.0, 0.0) : null;

                    while (ch == ' ') nextChar();
                    String diffVar = "x";
                    while (eat('d') || eat('D')) {
                        while (ch == ' ') nextChar();
                        StringBuilder varSb = new StringBuilder();
                        while (Character.isLetterOrDigit(ch) || ch == '_') {
                            varSb.append((char) ch);
                            nextChar();
                        }
                        if (varSb.length() > 0) {
                            diffVar = varSb.toString().toLowerCase();
                        }
                        while (ch == ' ') nextChar();
                    }

                    final ComplexExpression finalIntegrand = integrand;
                    if (isConstIntegrand) {
                        final Complex cVal = (constVal != null) ? constVal : Complex.ofCartesian(1.0, 0.0);
                        final int numSigns = upperList.size();
                        final java.util.List<ComplexExpression> fUpper = upperList;
                        final java.util.List<ComplexExpression> fLower = lowerList;
                        x = (u, v) -> {
                            double prod = 1.0;
                            for (int k = 0; k < numSigns; k++) {
                                ComplexExpression up = fUpper.get(k);
                                ComplexExpression lo = fLower.get(k);
                                double a = (lo != null) ? lo.eval(u, v).real() : 0.0;
                                double b = (up != null) ? up.eval(u, v).real() : (k == 0 ? (Double.isFinite(u.real()) ? u.real() : 1.0) : 1.0);
                                if (Double.isNaN(a)) a = 0.0;
                                if (Double.isNaN(b)) b = 1.0;
                                if (Double.isInfinite(a)) a = Math.signum(a) * 100.0;
                                if (Double.isInfinite(b)) b = Math.signum(b) * 100.0;
                                a = Math.max(-1000.0, Math.min(1000.0, a));
                                b = Math.max(-1000.0, Math.min(1000.0, b));
                                prod *= (b - a);
                            }
                            return cVal.multiply(prod);
                        };
                    } else if (upperList.size() == 1) {
                        final String vName = diffVar;
                        final ComplexExpression finalUpper = upperList.get(0);
                        final ComplexExpression finalLower = lowerList.get(0);
                        x = (u, v) -> {
                            int depth = integralDepth.get();
                            int sDepth = sumDepth.get();
                            if (depth >= MAX_INTEGRAL_DEPTH || (depth + sDepth) >= 5) {
                                return Complex.ZERO;
                            }
                            integralDepth.set(depth + 1);
                            Map<String, Double> ctx = sumContext.get();
                            Double oldVar = ctx.get(vName);
                            try {
                                double rawA = (finalLower != null) ? finalLower.eval(u, v).real() : 0.0;
                                double rawB = (finalUpper != null) ? finalUpper.eval(u, v).real() : (vName.equals("y") ? (Double.isFinite(v.real()) ? v.real() : 1.0) : (Double.isFinite(u.real()) ? u.real() : 1.0));
                                if (Double.isNaN(rawA)) rawA = 0.0;
                                if (Double.isNaN(rawB)) rawB = 1.0;

                                boolean aInf = Double.isInfinite(rawA) || Math.abs(rawA) >= 1e50;
                                boolean bInf = Double.isInfinite(rawB) || Math.abs(rawB) >= 1e50;

                                double a, b;
                                if (aInf && bInf) {
                                    a = -1.56;
                                    b = 1.56;
                                } else if (bInf) {
                                    a = 0.0005;
                                    b = 1.565;
                                } else if (aInf) {
                                    a = 0.0005;
                                    b = 1.565;
                                } else {
                                    a = Math.max(-1e8, Math.min(1e8, rawA));
                                    b = Math.max(-1e8, Math.min(1e8, rawB));
                                }
                                if (a == b) return Complex.ZERO;

                                int n;
                                if (sDepth > 0) {
                                    n = 2;
                                } else {
                                    n = (depth >= 2) ? 2 : (depth == 1 ? 4 : 8);
                                }
                                double h = (b - a) / n;
                                Complex sum = Complex.ZERO;
                                int validCount = 0;

                                for (int i = 0; i <= n; i++) {
                                    if (Thread.currentThread().isInterrupted()) {
                                        throw new RuntimeException("interrupted");
                                    }
                                    double t = a + i * h;
                                    double sample;
                                    double jacobian = 1.0;
                                    if (aInf && bInf) {
                                        sample = Math.tan(t);
                                        jacobian = 1.0 + sample * sample;
                                    } else if (bInf) {
                                        double tanT = Math.tan(t);
                                        sample = rawA + tanT;
                                        jacobian = 1.0 + tanT * tanT;
                                    } else if (aInf) {
                                        double tanT = Math.tan(t);
                                        sample = rawB - tanT;
                                        jacobian = 1.0 + tanT * tanT;
                                    } else {
                                        if (i == 0) {
                                            sample = a + 1e-7 * (b - a);
                                        } else if (i == n) {
                                            sample = b - 1e-7 * (b - a);
                                        } else {
                                            sample = t;
                                        }
                                    }

                                    ctx.put(vName, sample);
                                    Complex curU = vName.equals("x") ? Complex.ofCartesian(sample, 0.0) : u;
                                    Complex curV = vName.equals("y") ? Complex.ofCartesian(sample, 0.0) : v;
                                    Complex fVal = finalIntegrand.eval(curU, curV);
                                    if (fVal == null || !Double.isFinite(fVal.real()) || !Double.isFinite(fVal.imag())) {
                                        double dir = (b >= a) ? 1.0 : -1.0;
                                        double nudge = (i == 0) ? dir * 1e-6 : ((i == n) ? -dir * 1e-6 : dir * 1e-6);
                                        double nudgedSample = sample + nudge;
                                        ctx.put(vName, nudgedSample);
                                        Complex curU2 = vName.equals("x") ? Complex.ofCartesian(nudgedSample, 0.0) : u;
                                        Complex curV2 = vName.equals("y") ? Complex.ofCartesian(nudgedSample, 0.0) : v;
                                        fVal = finalIntegrand.eval(curU2, curV2);
                                    }
                                    if (fVal == null || !Double.isFinite(fVal.real()) || !Double.isFinite(fVal.imag())) {
                                        return Complex.ofCartesian(Double.NaN, 0.0);
                                    }

                                    if (jacobian != 1.0) {
                                        fVal = fVal.multiply(jacobian);
                                    }

                                    double weight = (i == 0 || i == n) ? 1.0 : ((i % 2 == 1) ? 4.0 : 2.0);
                                    sum = sum.add(fVal.multiply(weight));
                                    validCount++;
                                }
                                if (validCount == 0) {
                                    return Complex.ZERO;
                                }
                                Complex result = sum.multiply(h / 3.0);
                                if (!Double.isFinite(result.real()) || !Double.isFinite(result.imag())) {
                                    return Complex.ofCartesian(Double.NaN, 0.0);
                                }
                                return result;
                            } finally {
                                if (oldVar != null) ctx.put(vName, oldVar);
                                else ctx.remove(vName);
                                integralDepth.set(depth);
                            }
                        };
                    } else if (upperList.size() == 2) {
                        final ComplexExpression outerUpper = (upperList.get(0) != null) ? upperList.get(0) : upperList.get(1);
                        final ComplexExpression outerLower = (lowerList.get(0) != null) ? lowerList.get(0) : lowerList.get(1);
                        final ComplexExpression innerUpper = (upperList.get(1) != null) ? upperList.get(1) : outerUpper;
                        final ComplexExpression innerLower = (lowerList.get(1) != null) ? lowerList.get(1) : outerLower;
                        x = (u, v) -> {
                            int depth = integralDepth.get();
                            int sDepth = sumDepth.get();
                            if (depth >= MAX_INTEGRAL_DEPTH || (depth + sDepth) >= 5) {
                                return Complex.ZERO;
                            }
                            integralDepth.set(depth + 2);
                            Map<String, Double> ctx = sumContext.get();
                            Double oldX = ctx.get("x");
                            Double oldY = ctx.get("y");
                            try {
                                double ay = (outerLower != null) ? outerLower.eval(u, v).real() : 0.0;
                                double by = (outerUpper != null) ? outerUpper.eval(u, v).real() : 1.0;
                                if (Double.isNaN(ay)) ay = 0.0;
                                if (Double.isNaN(by)) by = 1.0;
                                ay = Math.max(-500.0, Math.min(500.0, ay));
                                by = Math.max(-500.0, Math.min(500.0, by));
                                if (ay == by) return Complex.ZERO;

                                int ny = (depth > 0 || sDepth > 0) ? 4 : 8;
                                int nx = (depth > 0 || sDepth > 0) ? 4 : 8;
                                double hy = (by - ay) / ny;
                                Complex sumTotal = Complex.ZERO;
                                int validOuter = 0;

                                for (int j = 0; j <= ny; j++) {
                                    if (Thread.currentThread().isInterrupted()) {
                                        throw new RuntimeException("interrupted");
                                    }
                                    double sampleY = ay + j * hy;
                                    double wy = (j == 0 || j == ny) ? 1.0 : ((j % 2 == 1) ? 4.0 : 2.0);
                                    ctx.put("y", sampleY);
                                    Complex curY = Complex.ofCartesian(sampleY, 0.0);

                                    double ax = (innerLower != null) ? innerLower.eval(u, curY).real() : 0.0;
                                    double bx = (innerUpper != null) ? innerUpper.eval(u, curY).real() : 1.0;
                                    if (Double.isNaN(ax)) ax = 0.0;
                                    if (Double.isNaN(bx)) bx = 1.0;
                                    ax = Math.max(-500.0, Math.min(500.0, ax));
                                    bx = Math.max(-500.0, Math.min(500.0, bx));
                                    if (ax == bx) continue;

                                    double hx = (bx - ax) / nx;
                                    Complex innerSum = Complex.ZERO;
                                    int validInner = 0;

                                    for (int i = 0; i <= nx; i++) {
                                        double sampleX = ax + i * hx;
                                        double wx = (i == 0 || i == nx) ? 1.0 : ((i % 2 == 1) ? 4.0 : 2.0);
                                        ctx.put("x", sampleX);
                                        Complex fVal = finalIntegrand.eval(Complex.ofCartesian(sampleX, 0.0), curY);
                                        if (fVal == null || !Double.isFinite(fVal.real()) || !Double.isFinite(fVal.imag())) {
                                            fVal = finalIntegrand.eval(Complex.ofCartesian(sampleX + 1e-7, 0.0), Complex.ofCartesian(sampleY + 1e-7, 0.0));
                                        }
                                        if (fVal == null || !Double.isFinite(fVal.real()) || !Double.isFinite(fVal.imag())) continue;
                                        innerSum = innerSum.add(fVal.multiply(wx));
                                        validInner++;
                                    }
                                    if (validInner > 0) {
                                        sumTotal = sumTotal.add(innerSum.multiply(wy * hx / 3.0));
                                        validOuter++;
                                    }
                                }
                                if (validOuter == 0) return Complex.ZERO;
                                Complex result = sumTotal.multiply(hy / 3.0);
                                if (!Double.isFinite(result.real()) || !Double.isFinite(result.imag())) {
                                    return Complex.ofCartesian(Double.NaN, 0.0);
                                }
                                return result;
                            } finally {
                                if (oldX != null) ctx.put("x", oldX); else ctx.remove("x");
                                if (oldY != null) ctx.put("y", oldY); else ctx.remove("y");
                                integralDepth.set(depth);
                            }
                        };
                    } else {
                        ComplexExpression defaultUpper = null;
                        ComplexExpression defaultLower = null;
                        for (ComplexExpression e : upperList) { if (e != null) { defaultUpper = e; break; } }
                        for (ComplexExpression e : lowerList) { if (e != null) { defaultLower = e; break; } }
                        final ComplexExpression zUpper = (upperList.get(0) != null) ? upperList.get(0) : defaultUpper;
                        final ComplexExpression zLower = (lowerList.get(0) != null) ? lowerList.get(0) : defaultLower;
                        final ComplexExpression yUpper = (upperList.size() > 1 && upperList.get(1) != null) ? upperList.get(1) : defaultUpper;
                        final ComplexExpression yLower = (lowerList.size() > 1 && lowerList.get(1) != null) ? lowerList.get(1) : defaultLower;
                        final ComplexExpression xUpper = (upperList.size() > 2 && upperList.get(2) != null) ? upperList.get(2) : defaultUpper;
                        final ComplexExpression xLower = (lowerList.size() > 2 && lowerList.get(2) != null) ? lowerList.get(2) : defaultLower;
                        final int numSigns = upperList.size();
                        final java.util.List<ComplexExpression> fUpper = upperList;
                        final java.util.List<ComplexExpression> fLower = lowerList;
                        x = (u, v) -> {
                            int depth = integralDepth.get();
                            if (depth >= MAX_INTEGRAL_DEPTH) return Complex.ZERO;
                            integralDepth.set(depth + 3);
                            Map<String, Double> ctx = sumContext.get();
                            Double oldZ = ctx.get("z");
                            try {
                                double az = (zLower != null) ? zLower.eval(u, v).real() : 0.0;
                                double bz = (zUpper != null) ? zUpper.eval(u, v).real() : 1.0;
                                if (Double.isNaN(az)) az = 0.0;
                                if (Double.isNaN(bz)) bz = 1.0;
                                if (az == bz) return Complex.ZERO;
                                int nz = (depth > 0 || numSigns >= 4) ? 2 : 4;
                                int ny = (depth > 0 || numSigns >= 4) ? 2 : 4;
                                int nx = (depth > 0 || numSigns >= 4) ? 2 : 4;
                                double hz = (bz - az) / nz;
                                Complex totalZ = Complex.ZERO;

                                for (int k = 0; k <= nz; k++) {
                                    if (Thread.currentThread().isInterrupted()) {
                                        throw new RuntimeException("interrupted");
                                    }
                                    double sampleZ = az + k * hz;
                                    double wz = (k == 0 || k == nz) ? 1.0 : ((k % 2 == 1) ? 4.0 : 2.0);
                                    ctx.put("z", sampleZ);

                                    double ay = (yLower != null) ? yLower.eval(u, v).real() : 0.0;
                                    double by = (yUpper != null) ? yUpper.eval(u, v).real() : 1.0;
                                    if (ay == by) continue;
                                    double hy = (by - ay) / ny;
                                    Complex totalY = Complex.ZERO;

                                    for (int j = 0; j <= ny; j++) {
                                        if (Thread.currentThread().isInterrupted()) {
                                            throw new RuntimeException("interrupted");
                                        }
                                        double sampleY = ay + j * hy;
                                        double wy = (j == 0 || j == ny) ? 1.0 : ((j % 2 == 1) ? 4.0 : 2.0);
                                        Complex curY = Complex.ofCartesian(sampleY, 0.0);

                                        double ax = (xLower != null) ? xLower.eval(u, curY).real() : 0.0;
                                        double bx = (xUpper != null) ? xUpper.eval(u, curY).real() : 1.0;
                                        if (ax == bx) continue;
                                        double hx = (bx - ax) / nx;
                                        Complex totalX = Complex.ZERO;

                                        for (int i = 0; i <= nx; i++) {
                                            if (Thread.currentThread().isInterrupted()) {
                                                throw new RuntimeException("interrupted");
                                            }
                                            double sampleX = ax + i * hx;
                                            double wx = (i == 0 || i == nx) ? 1.0 : ((i % 2 == 1) ? 4.0 : 2.0);
                                            Complex fVal = finalIntegrand.eval(Complex.ofCartesian(sampleX, 0.0), curY);
                                            if (fVal != null && Double.isFinite(fVal.real()) && Double.isFinite(fVal.imag())) {
                                                totalX = totalX.add(fVal.multiply(wx));
                                            }
                                        }
                                        totalY = totalY.add(totalX.multiply(wy * hx / 3.0));
                                    }
                                    totalZ = totalZ.add(totalY.multiply(wz * hy / 3.0));
                                }
                                double outerProd = 1.0;
                                for (int m = 3; m < numSigns; m++) {
                                    ComplexExpression up = fUpper.get(m);
                                    ComplexExpression lo = fLower.get(m);
                                    double a = (lo != null) ? lo.eval(u, v).real() : 0.0;
                                    double b = (up != null) ? up.eval(u, v).real() : 1.0;
                                    if (Double.isNaN(a)) a = 0.0;
                                    if (Double.isNaN(b)) b = 1.0;
                                    outerProd *= (b - a);
                                }
                                return totalZ.multiply(hz / 3.0).multiply(outerProd);
                            } finally {
                                integralDepth.set(depth);
                                if (oldZ != null) ctx.put("z", oldZ);
                                else ctx.remove("z");
                            }
                        };
                    }
                } else if ((ch >= '0' && ch <= '9') || ch == '.') {
                    while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
                    if ((ch == 'e' || ch == 'E') && pos < normalizedStr.length()) {
                        char next = normalizedStr.charAt(pos);
                        if (Character.isDigit(next) || next == '+' || next == '-') {
                            nextChar();
                            if (ch == '+' || ch == '-') nextChar();
                            while (ch >= '0' && ch <= '9') nextChar();
                        }
                    }
                    double val = Double.parseDouble(normalizedStr.substring(startPos, this.pos));
                    Complex cVal = Complex.ofCartesian(val, 0.0);
                    x = (u, v) -> cVal;
                } else if (Character.isLetter(ch)) {
                    while (Character.isLetter(ch)) nextChar();
                    String prefix = normalizedStr.substring(startPos, this.pos);
                    if (ch == '_') {
                        nextChar();
                        if (ch == '(' || ch == '{' || ch == '[') {
                            char open = (char) ch;
                            char close = open == '(' ? ')' : (open == '{' ? '}' : ']');
                            int depth = 1;
                            nextChar();
                            while (ch != -1 && depth > 0) {
                                if (ch == open) depth++;
                                else if (ch == close) depth--;
                                if (depth == 0) {
                                    nextChar();
                                    break;
                                }
                                nextChar();
                            }
                        } else {
                            while (Character.isLetterOrDigit(ch)) nextChar();
                        }
                    } else if (Character.isDigit(ch)) {
                        if (prefix.equals("T") || prefix.equals("f") || prefix.equals("w") || prefix.equals("W")
                                || (declaredVars != null && (declaredVars.containsKey(prefix + (char)ch) || declaredVars.containsKey(normalizedStr.substring(startPos, Math.min(normalizedStr.length(), this.pos + 1)))))) {
                            while (Character.isDigit(ch)) nextChar();
                        }
                    }
                    if ((prefix.equalsIgnoreCase("atan") && ch == '2') ||
                        (prefix.equalsIgnoreCase("log") && (ch == '2' || (ch == '1' && pos + 1 < normalizedStr.length() && normalizedStr.charAt(pos + 1) == '0')))) {
                        if (prefix.equalsIgnoreCase("log") && ch == '1') {
                            nextChar();
                            nextChar();
                        } else {
                            nextChar();
                        }
                    } else if (prefix.equalsIgnoreCase("diff")) {
                        while (Character.isDigit(ch)) nextChar();
                        while (Character.isLetter(ch)) nextChar();
                    } else if (prefix.matches("(?i)^d[a-z]+$")) {
                        while (Character.isLetterOrDigit(ch) || ch == '_') nextChar();
                    }
                    String func = normalizedStr.substring(startPos, this.pos);
                    String normFunc = func.replaceAll("([a-zA-Z]+)_\\(([a-zA-Z0-9]+)\\)", "$1_$2")
                                          .replaceAll("([a-zA-Z]+)_\\{([a-zA-Z0-9]+)\\}", "$1_$2")
                                          .replaceAll("([a-zA-Z]+)_\\[([a-zA-Z0-9]+)\\]", "$1_$2");
                    String funcLower = func.toLowerCase();
                    String normFuncLower = normFunc.toLowerCase();
                    while (ch == ' ') nextChar();
                    if ((funcLower.equals("sum") || func.equals("∑") || func.equals("Σ") || func.equals("\u2211") || func.equals("\u03A3")) && (ch == '[' || ch == '(')) {
                        x = parseSummation();
                    } else if (funcLower.equals("log")) {
                        ComplexExpression funcExp = null;
                        if (eat('^')) {
                            while (ch == ' ') nextChar();
                            funcExp = (ch == -1 || ch == '+' || ch == '-' || ch == '*' || ch == '/' || ch == ')' || ch == ']' || ch == '}')
                                    ? ((u, v) -> Complex.ofCartesian(1.0, 0.0))
                                    : parseFactor();
                        }
                        ComplexExpression logBase = null;
                        while (ch == ' ') nextChar();
                        if (ch == '_') {
                            nextChar();
                            while (ch == ' ') nextChar();
                        }
                        if (eat('[')) {
                            while (ch == ' ') nextChar();
                            if (!eat(']')) {
                                logBase = parseExpression();
                                while (ch == ')' || ch == '}' || ch == ' ') nextChar();
                                eat(']');
                            }
                        }
                        while (ch == ' ' || ch == ')' || ch == ']' || ch == '}') nextChar();
                        ComplexExpression a;
                        if (eat('(')) {
                            while (ch == ' ') nextChar();
                            if (eat(')')) {
                                throw new IncompleteExpressionException("Function 'log' cannot have an empty argument");
                            } else {
                                a = parseExpression();
                                while (ch == ']' || ch == '}' || ch == ' ') nextChar();
                                eat(')');
                            }
                        } else if (eat('[')) {
                            while (ch == ' ') nextChar();
                            if (eat(']')) {
                                throw new IncompleteExpressionException("Function 'log' cannot have an empty argument");
                            } else {
                                a = parseExpression();
                                while (ch == ')' || ch == '}' || ch == ' ') nextChar();
                                eat(']');
                            }
                        } else {
                            if (ch == -1) {
                                throw new IncompleteExpressionException("Function 'log' is incomplete");
                            }
                            throw new IllegalArgumentException("Function 'log' requires parentheses with an argument, e.g. log(x) or log[b](x)");
                        }
                        final ComplexExpression finalBase = logBase;
                        final ComplexExpression finalA = a;
                        ComplexExpression baseFunc;
                        if (finalBase == null) {
                            baseFunc = (u, v) -> finalA.eval(u, v).log10();
                        } else {
                            baseFunc = (u, v) -> finalA.eval(u, v).log().divide(finalBase.eval(u, v).log());
                        }
                        if (funcExp != null) {
                            final ComplexExpression fBase = baseFunc;
                            final ComplexExpression fExp = funcExp;
                            x = (u, v) -> safePowComplex(fBase.eval(u, v), fExp.eval(u, v));
                        } else {
                            x = baseFunc;
                        }
                    } else if (declaredVars != null && (declaredVars.containsKey(func) || declaredVars.containsKey(normFunc) || declaredVars.containsKey(funcLower) || declaredVars.containsKey(normFuncLower))) {
                        final ComplexExpression vExpr = declaredVars.containsKey(func) ? declaredVars.get(func)
                                : declaredVars.containsKey(normFunc) ? declaredVars.get(normFunc)
                                : declaredVars.containsKey(funcLower) ? declaredVars.get(funcLower)
                                : declaredVars.get(normFuncLower);
                        while (ch == ' ') nextChar();
                        ComplexExpression funcExp = null;
                        if (eat('^')) {
                            while (ch == ' ') nextChar();
                            funcExp = (ch == -1 || ch == '+' || ch == '-' || ch == '*' || ch == '/' || ch == ')' || ch == ']' || ch == '}' || ch == ',')
                                    ? ((u, v) -> Complex.ofCartesian(1.0, 0.0))
                                    : parseFactor();
                        }
                        if (eat('(')) {
                            while (ch == ' ') nextChar();
                            final ComplexExpression arg = parseExpression();
                            while (ch == ' ') nextChar();
                            eat(')');
                            if (funcExp != null) {
                                final ComplexExpression fExp = funcExp;
                                x = (u, v) -> safePowComplex(vExpr.eval(arg.eval(u, v), v), fExp.eval(u, v));
                            } else {
                                x = (u, v) -> vExpr.eval(arg.eval(u, v), v);
                            }
                        } else {
                            if (funcExp != null) {
                                final ComplexExpression fExp = funcExp;
                                x = (u, v) -> safePowComplex(vExpr.eval(u, v), fExp.eval(u, v));
                            } else {
                                x = vExpr;
                            }
                        }
                    } else if (func.equals("t") || func.startsWith("t_") || normFunc.startsWith("t_")) {
                        x = (u, v) -> {
                            Double sv = sumContext.get().get(func);
                            if (sv == null) sv = sumContext.get().get("t");
                            return sv != null ? Complex.ofCartesian(sv, 0.0) : u;
                        };
                    } else if (func.equals("x") || func.startsWith("x_") || normFunc.startsWith("x_")) {
                        x = (u, v) -> {
                            Double sv = sumContext.get().get(func);
                            if (sv == null) sv = sumContext.get().get("x");
                            return sv != null ? Complex.ofCartesian(sv, 0.0) : u;
                        };
                    } else if (func.equals("y") || func.startsWith("y_") || normFunc.startsWith("y_")) {
                        x = (u, v) -> {
                            Double sv = sumContext.get().get(func);
                            if (sv == null) sv = sumContext.get().get("y");
                            return sv != null ? Complex.ofCartesian(sv, 0.0) : v;
                        };
                    } else if ((func.equals("u") || func.startsWith("u_") || normFunc.startsWith("u_")) && ch != '(' && ch != '[' && ch != '{') {
                        x = (u, v) -> u;
                    } else if (func.equals("v") || func.startsWith("v_") || normFunc.startsWith("v_")) {
                        x = (u, v) -> v;
                    } else if (func.equals("T") || func.equals("T0") || func.equals("T_0")) {
                        x = (u, v) -> Complex.ofCartesian(defaultPeriod, 0.0);
                    } else if (func.equals("f0") || func.equals("f_0")) {
                        x = (u, v) -> Complex.ofCartesian(1.0 / defaultPeriod, 0.0);
                    } else if (func.equals("w0") || func.equals("w_0") || func.equals("omega_0") || func.equals("omega") || func.equals("\u03C9") || func.equals("\u03C9_0")) {
                        x = (u, v) -> Complex.ofCartesian(2.0 * Math.PI / defaultPeriod, 0.0);
                    } else if (func.equalsIgnoreCase("fs") || func.equalsIgnoreCase("f_s")) {
                        x = (u, v) -> Complex.ofCartesian(1000.0, 0.0);
                    } else if (func.equals("Ts") || func.equals("T_s")) {
                        x = (u, v) -> Complex.ofCartesian(0.001, 0.0);
                    } else if ((func.equals("f") || func.startsWith("f_") || normFunc.startsWith("f_")) && ch != '(' && ch != '[' && ch != '{') {
                        x = (u, v) -> {
                            Double sv = sumContext.get().get(func);
                            if (sv == null) sv = sumContext.get().get("f");
                            return sv != null ? Complex.ofCartesian(sv, 0.0) : u;
                        };
                    } else if ((func.equals("tau") || func.equals("\u03C4") || func.startsWith("tau_") || func.startsWith("\u03C4_")) && ch != '(' && ch != '[' && ch != '{') {
                        x = (u, v) -> {
                            Double sv = sumContext.get().get(func);
                            if (sv == null) sv = sumContext.get().get("tau");
                            if (sv == null) sv = sumContext.get().get("\u03C4");
                            return sv != null ? Complex.ofCartesian(sv, 0.0) : u;
                        };
                    } else if (funcLower.equals("i") || funcLower.equals("j")) {
                        final String vName = funcLower;
                        x = (u, v) -> {
                            Double sv = sumContext.get().get(vName);
                            return sv != null ? Complex.ofCartesian(sv, 0.0) : Complex.I;
                        };
                    } else if (funcLower.equals("n") || funcLower.equals("k") || funcLower.equals("m")) {
                        final String vName = funcLower;
                        x = (u, v) -> {
                            Double sv = sumContext.get().get(vName);
                            return sv != null ? Complex.ofCartesian(sv, 0.0) : u;
                        };
                    } else if ((funcLower.equals("z") || funcLower.equals("w")) && ch != '(' && ch != '[' && ch != '{') {
                        final String vName = funcLower;
                        x = (u, v) -> {
                            Double sv = sumContext.get().get(vName);
                            return Complex.ofCartesian(sv != null ? sv : 0.0, 0.0);
                        };
                    } else if (funcLower.matches("^d[a-z][a-z0-9_]*$") && !funcLower.startsWith("diff") && !funcLower.equals("drcd") && !funcLower.equals("drcc")) x = (u, v) -> Complex.ofCartesian(1.0, 0.0);
                    else if (funcLower.equals("inf") || funcLower.equals("infinity") || func.equals("∞") || func.equals("\u221E")) x = (u, v) -> Complex.ofCartesian(Double.POSITIVE_INFINITY, 0.0);
                    else if (funcLower.equals("pi") || func.equals("\u03C0")) x = (u, v) -> Complex.ofCartesian(Math.PI, 0.0);
                    else if (funcLower.equals("e")) x = (u, v) -> Complex.ofCartesian(Math.E, 0.0);
                    else if (funcLower.length() == 1 && Character.isLetter(funcLower.charAt(0)) && ch != '(' && ch != '[' && ch != '{') {
                        final String vName = funcLower;
                        x = (u, v) -> {
                            Double sv = sumContext.get().get(vName);
                            return sv != null ? Complex.ofCartesian(sv, 0.0) : u;
                        };
                    }
                    else if (funcLower.equals("d")) throw new IllegalArgumentException("Invalid identifier: d");
                    else {
                        ComplexExpression funcExp = null;
                        if (eat('^')) {
                            while (ch == ' ') nextChar();
                            funcExp = (ch == -1 || ch == '+' || ch == '-' || ch == '*' || ch == '/' || ch == ')' || ch == ']' || ch == '}')
                                    ? ((u, v) -> Complex.ofCartesian(1.0, 0.0))
                                    : parseFactor();
                        }
                        ComplexExpression a;
                        ComplexExpression b = null;
                        ComplexExpression c = null;
                        ComplexExpression d = null;
                        ComplexExpression argE = null;
                        boolean reqParens = isFunctionRequiringParens(func);
                        while (ch == ' ') nextChar();
                        if (eat('(')) {
                            while (ch == ' ') nextChar();
                            if (eat(')')) {
                                if (reqParens) {
                                    throw new IncompleteExpressionException("Function '" + func + "' cannot have an empty argument");
                                }
                                a = (u, v) -> u;
                            } else {
                                a = parseExpression();
                                while (ch == ' ') nextChar();
                                if (eat(',')) {
                                    while (ch == ' ') nextChar();
                                    b = parseExpression();
                                    while (ch == ' ') nextChar();
                                    if (eat(',')) {
                                        while (ch == ' ') nextChar();
                                        c = parseExpression();
                                        while (ch == ' ') nextChar();
                                        if (eat(',')) {
                                            while (ch == ' ') nextChar();
                                            d = parseExpression();
                                            while (ch == ' ') nextChar();
                                            if (eat(',')) {
                                                while (ch == ' ') nextChar();
                                                argE = parseExpression();
                                            }
                                        }
                                    }
                                }
                                while (ch == ' ') nextChar();
                                eat(')');
                            }
                        } else if (eat('[')) {
                            while (ch == ' ') nextChar();
                            if (eat(']')) {
                                if (reqParens) {
                                    throw new IncompleteExpressionException("Function '" + func + "' cannot have an empty argument");
                                }
                                a = (u, v) -> u;
                            } else {
                                a = parseExpression();
                                while (ch == ' ') nextChar();
                                if (eat(',')) {
                                    while (ch == ' ') nextChar();
                                    b = parseExpression();
                                    while (ch == ' ') nextChar();
                                    if (eat(',')) {
                                        while (ch == ' ') nextChar();
                                        c = parseExpression();
                                        while (ch == ' ') nextChar();
                                        if (eat(',')) {
                                            while (ch == ' ') nextChar();
                                            d = parseExpression();
                                            while (ch == ' ') nextChar();
                                            if (eat(',')) {
                                                while (ch == ' ') nextChar();
                                                argE = parseExpression();
                                            }
                                        }
                                    }
                                }
                                while (ch == ' ') nextChar();
                                eat(']');
                            }
                        } else if (eat('{')) {
                            while (ch == ' ') nextChar();
                            if (eat('}')) {
                                if (reqParens) {
                                    throw new IncompleteExpressionException("Function '" + func + "' cannot have an empty argument");
                                }
                                a = (u, v) -> u;
                            } else {
                                a = parseExpression();
                                while (ch == ' ') nextChar();
                                if (eat(',')) {
                                    while (ch == ' ') nextChar();
                                    b = parseExpression();
                                    while (ch == ' ') nextChar();
                                    if (eat(',')) {
                                        while (ch == ' ') nextChar();
                                        c = parseExpression();
                                        while (ch == ' ') nextChar();
                                        if (eat(',')) {
                                            while (ch == ' ') nextChar();
                                            d = parseExpression();
                                            while (ch == ' ') nextChar();
                                            if (eat(',')) {
                                                while (ch == ' ') nextChar();
                                                argE = parseExpression();
                                            }
                                        }
                                    }
                                }
                                while (ch == ' ') nextChar();
                                eat('}');
                            }
                        } else {
                            if (reqParens) {
                                if (ch == -1) {
                                    throw new IncompleteExpressionException("Function '" + func + "' is incomplete");
                                }
                                throw new IllegalArgumentException("Function '" + func + "' requires parentheses with an argument, e.g. " + func + "(...)");
                            }
                            if (funcExp != null) {
                                a = (u, v) -> u;
                            } else if (funcLower.equals("u") || func.equals("h") || func.equals("H") || ch == -1 || ch == '+' || ch == '-' || ch == '*' || ch == '/' || ch == ')' || ch == ']' || ch == '}' || ch == ',') {
                                a = (u, v) -> u;
                            } else {
                                a = parseFactor();
                            }
                        }

                        final ComplexExpression finalA = a;
                        final ComplexExpression finalB = b;
                        final ComplexExpression finalC = c;
                        final ComplexExpression finalD = d;
                        final ComplexExpression finalE = argE;
                        ComplexExpression baseFunc;
                        if (funcLower.equals("pulse")) {
                            baseFunc = (u, v) -> {
                                Complex valA = finalA.eval(u, v);
                                double t = valA.real();
                                if (!Double.isFinite(t)) return Complex.ofCartesian(Double.NaN, 0.0);
                                double center = 0.0;
                                double width = 1.0;
                                if (finalC != null) {
                                    center = finalB.eval(u, v).real();
                                    width = Math.abs(finalC.eval(u, v).real());
                                } else if (finalB != null) {
                                    width = Math.abs(finalB.eval(u, v).real());
                                }
                                double halfW = width / 2.0;
                                double diff = Math.abs(t - center);
                                double res = (diff <= halfW) ? 1.0 : 0.0;
                                return Complex.ofCartesian(res, 0.0);
                            };
                        } else if (funcLower.equals("trapz")) {
                            baseFunc = (u, v) -> {
                                Complex valA = finalA.eval(u, v);
                                double t = valA.real();
                                if (!Double.isFinite(t)) return Complex.ofCartesian(Double.NaN, 0.0);
                                if (finalD != null) {
                                    double aVal = finalB.eval(u, v).real();
                                    double bVal = finalC.eval(u, v).real();
                                    double cVal = finalD.eval(u, v).real();
                                    double dVal = (finalE != null) ? finalE.eval(u, v).real() : cVal;
                                    if (t <= aVal || t >= dVal) return Complex.ZERO;
                                    if (t >= bVal && t <= cVal) return Complex.ofCartesian(1.0, 0.0);
                                    if (t > aVal && t < bVal) return Complex.ofCartesian((bVal > aVal) ? (t - aVal) / (bVal - aVal) : 1.0, 0.0);
                                    if (t > cVal && t < dVal) return Complex.ofCartesian((dVal > cVal) ? (dVal - t) / (dVal - cVal) : 1.0, 0.0);
                                    return Complex.ZERO;
                                } else {
                                    double topW = 1.0;
                                    double baseW = 2.0;
                                    if (finalC != null) {
                                        topW = Math.abs(finalB.eval(u, v).real());
                                        baseW = Math.abs(finalC.eval(u, v).real());
                                    } else if (finalB != null) {
                                        topW = Math.abs(finalB.eval(u, v).real());
                                        baseW = 2.0 * topW;
                                    }
                                    if (baseW < topW) {
                                        double tmp = baseW; baseW = topW; topW = tmp;
                                    }
                                    double aVal = topW / 2.0;
                                    double bVal = baseW / 2.0;
                                    double absT = Math.abs(t);
                                    if (absT <= aVal) {
                                        return Complex.ofCartesian(1.0, 0.0);
                                    } else if (absT < bVal) {
                                        double res = (bVal > aVal) ? (bVal - absT) / (bVal - aVal) : 0.0;
                                        return Complex.ofCartesian(res, 0.0);
                                    } else {
                                        return Complex.ZERO;
                                    }
                                }
                            };
                        } else if (funcLower.equals("step") || funcLower.equals("heaviside") || funcLower.equals("u") || func.equals("θ") || func.equals("Θ") || func.equals("\u03B8") || func.equals("\u0398")) {
                            baseFunc = (u, v) -> {
                                double t = finalA.eval(u, v).real();
                                if (!Double.isFinite(t)) return Complex.ofCartesian(Double.NaN, 0.0);
                                double t0 = (finalB != null) ? finalB.eval(u, v).real() : 0.0;
                                double diff = t - t0;
                                double res;
                                if (Math.abs(diff) < 1e-15) {
                                    res = 0.5;
                                } else if (diff > 0) {
                                    res = 1.0;
                                } else {
                                    res = 0.0;
                                }
                                return Complex.ofCartesian(res, 0.0);
                            };
                        } else if (func.equals("h")) {
                            baseFunc = (u, v) -> {
                                Complex val = finalA.eval(u, v);
                                double t = val.real();
                                if (!Double.isFinite(t)) return Complex.ofCartesian(Double.NaN, 0.0);
                                if (t < -1e-15) return Complex.ZERO;
                                if (Math.abs(t) <= 1e-15) return Complex.ofCartesian(0.5, 0.0);
                                return Complex.ofCartesian(Math.exp(-t), 0.0);
                            };
                        } else if (func.equals("H")) {
                            baseFunc = (u, v) -> {
                                Complex val = finalA.eval(u, v);
                                Complex den = Complex.ofCartesian(1.0, 0.0).add(Complex.I.multiply(val));
                                return safeDivideComplex(Complex.ofCartesian(1.0, 0.0), den);
                            };
                        } else if (funcLower.equals("drcd") || funcLower.equals("\u03B4") || func.equals("δ") || func.equals("Δ") || func.equals("\u0394") || funcLower.equals("delta") || funcLower.equals("dirac")) {
                            baseFunc = (u, v) -> {
                                double t = finalA.eval(u, v).real();
                                if (!Double.isFinite(t)) return Complex.ofCartesian(Double.NaN, 0.0);
                                double height = (finalB != null) ? finalB.eval(u, v).real() : 1.0;
                                double dx = samplingStep.get();
                                if (dx > 0.0) {
                                    double dt = Math.abs(finalA.eval(Complex.ofCartesian(u.real() + dx / 2.0, u.imag()), v).real()
                                            - finalA.eval(Complex.ofCartesian(u.real() - dx / 2.0, u.imag()), v).real());
                                    // Guard against stationary/flat roots (e.g. g'(x)=0 as in x^2 or x^3)
                                    if (dt < dx * 0.05 || !Double.isFinite(dt)) dt = dx;
                                    double half = dt / 2.0;
                                    if (t > -half && t <= half) {
                                        impulseTriggered.set(true);
                                        return Complex.ofCartesian(height / dt, 0.0);
                                    }
                                    return Complex.ZERO;
                                } else {
                                    double eps = (finalC != null) ? Math.abs(finalC.eval(u, v).real()) : 1e-9;
                                    if (eps <= 0.0) eps = 1e-9;
                                    double res = (Math.abs(t) <= eps / 2.0 || Math.abs(t) < 1e-9) ? height : 0.0;
                                    return Complex.ofCartesian(res, 0.0);
                                }
                            };
                        } else if (funcLower.equals("drcc") || func.equals("\u0428") || func.equals("\u0448") || funcLower.equals("\u0448") || funcLower.equals("comb") || funcLower.equals("shah")) {
                            baseFunc = (u, v) -> {
                                double t = finalA.eval(u, v).real();
                                if (!Double.isFinite(t)) return Complex.ofCartesian(Double.NaN, 0.0);
                                double period = (finalB != null) ? finalB.eval(u, v).real() : 1.0;
                                if (Math.abs(period) < 1e-15) period = 1.0;
                                double height = (finalC != null) ? finalC.eval(u, v).real() : 1.0;
                                double tNorm = t / period;
                                double nearest = Math.round(tNorm);
                                double dist = Math.abs(tNorm - nearest) * Math.abs(period);
                                double dx = samplingStep.get();
                                if (dx > 0.0) {
                                    double dt = Math.abs(finalA.eval(Complex.ofCartesian(u.real() + dx / 2.0, u.imag()), v).real()
                                            - finalA.eval(Complex.ofCartesian(u.real() - dx / 2.0, u.imag()), v).real());
                                    // Guard against stationary/flat roots
                                    if (dt < dx * 0.05 || !Double.isFinite(dt)) dt = dx;
                                    double half = dt / 2.0;
                                    if (dist <= half) {
                                        impulseTriggered.set(true);
                                        return Complex.ofCartesian(height / dt, 0.0);
                                    }
                                    return Complex.ZERO;
                                } else {
                                    double res = (dist < 1e-9) ? height : 0.0;
                                    return Complex.ofCartesian(res, 0.0);
                                }
                            };
                        } else if (funcLower.equals("sinc")) {
                            baseFunc = (u, v) -> {
                                Complex val = finalA.eval(u, v);
                                if (val == null) return Complex.ofCartesian(Double.NaN, Double.NaN);
                                if (val.equals(Complex.ZERO) || (Math.abs(val.real()) < 1e-15 && Math.abs(val.imag()) < 1e-15)) return Complex.ofCartesian(1.0, 0.0);
                                Complex piVal = val.multiply(Math.PI);
                                return piVal.sin().divide(piVal);
                            };
                        } else if (funcLower.equals("rect")) {
                            baseFunc = (u, v) -> Complex.ofCartesian(finalA.eval(u, v).abs() <= 0.5 ? 1.0 : 0.0, 0.0);
                        } else if (funcLower.equals("tri")) {
                            baseFunc = (u, v) -> Complex.ofCartesian(Math.max(0.0, 1.0 - finalA.eval(u, v).abs()), 0.0);
                        } else if (funcLower.equals("triangle")) {
                            baseFunc = (u, v) -> {
                                double val = finalA.eval(u, v).real();
                                if (!Double.isFinite(val)) return Complex.ZERO;
                                double saw = 2.0 * (val - Math.floor(val + 0.5));
                                return Complex.ofCartesian(2.0 * Math.abs(saw) - 1.0, 0.0);
                            };
                        } else if (funcLower.equals("saw")) {
                            baseFunc = (u, v) -> {
                                double val = finalA.eval(u, v).real();
                                if (!Double.isFinite(val)) return Complex.ZERO;
                                return Complex.ofCartesian(2.0 * (val - Math.floor(val + 0.5)), 0.0);
                            };
                        } else if (funcLower.equals("sqw") || funcLower.equals("square")) {
                            baseFunc = (u, v) -> {
                                double val = finalA.eval(u, v).real();
                                if (!Double.isFinite(val)) return Complex.ZERO;
                                double duty = (finalB != null) ? finalB.eval(u, v).real() : 0.5;
                                double phase = val - Math.floor(val);
                                return Complex.ofCartesian(phase < duty ? 1.0 : -1.0, 0.0);
                            };
                        } else if (funcLower.equals("rand")) {
                            baseFunc = (u, v) -> {
                                double val = finalA != null ? finalA.eval(u, v).real() : u.real();
                                int salt = (finalB != null) ? (int) Math.round(finalB.eval(u, v).real()) : 0;
                                return Complex.ofCartesian(hashRand(val, salt), 0.0);
                            };
                        } else if (funcLower.equals("randn") || funcLower.equals("noise")) {
                            baseFunc = (u, v) -> {
                                double val = finalA != null ? finalA.eval(u, v).real() : u.real();
                                return Complex.ofCartesian(hashRandn(val), 0.0);
                            };
                        } else if (funcLower.equals("rep") || funcLower.equals("per")) {
                            baseFunc = (u, v) -> {
                                double t = u.real();
                                double T = (finalB != null) ? finalB.eval(u, v).real() : defaultPeriod;
                                if (T <= 0.0) T = defaultPeriod;
                                double tMod = ((t + T / 2.0) % T + T) % T - T / 2.0;
                                return finalA.eval(Complex.ofCartesian(tMod, u.imag()), v);
                            };
                        } else if (funcLower.equals("sign")) {
                            baseFunc = (u, v) -> {
                                Complex val = finalA.eval(u, v);
                                if (val.equals(Complex.ZERO)) return Complex.ZERO;
                                double aVal = val.abs();
                                return aVal == 0.0 ? Complex.ZERO : val.divide(Complex.ofCartesian(aVal, 0.0));
                            };
                        } else if (funcLower.startsWith("diff")) {
                            java.util.regex.Matcher mDiff = java.util.regex.Pattern.compile("^diff(?:(\\d+))?([a-zA-Z])?$").matcher(funcLower);
                            if (mDiff.matches()) {
                                int ord = 1;
                                if (mDiff.group(1) != null) {
                                    try { ord = Integer.parseInt(mDiff.group(1)); } catch (Exception ignore) {}
                                }
                                char diffVar = 'x';
                                if (mDiff.group(2) != null) {
                                    diffVar = mDiff.group(2).charAt(0);
                                }
                                final int finalOrder = ord;
                                final char finalVar = diffVar;
                                baseFunc = (u, v) -> evalDerivativeComplex(finalA, u, v, finalVar, finalOrder);
                            } else {
                                baseFunc = (u, v) -> evalDerivativeComplex(finalA, u, v, 'x', 1);
                            }
                        } else if (funcLower.equals("sqrt")) {
                            if (finalB != null) throw new IllegalArgumentException("Function '" + func + "' takes only 1 argument");
                            baseFunc = (u, v) -> safeSqrtComplex(finalA.eval(u, v));
                        } else if (funcLower.equals("acos") || funcLower.equals("arccos")) {
                            if (finalB != null) throw new IllegalArgumentException("Function '" + func + "' takes only 1 argument");
                            baseFunc = (u, v) -> finalA.eval(u, v).acos();
                        } else if (funcLower.equals("asin") || funcLower.equals("arcsin")) {
                            if (finalB != null) throw new IllegalArgumentException("Function '" + func + "' takes only 1 argument");
                            baseFunc = (u, v) -> finalA.eval(u, v).asin();
                        } else if (funcLower.equals("atan") || funcLower.equals("arctan")) {
                            if (finalB != null) throw new IllegalArgumentException("Function '" + func + "' takes 1 argument, but 2 were provided. Use atan2(y, x) for two arguments.");
                            baseFunc = (u, v) -> finalA.eval(u, v).atan();
                        } else if (funcLower.equals("atan2")) {
                            if (finalB != null) {
                                baseFunc = (u, v) -> Complex.ofCartesian(Math.atan2(finalA.eval(u, v).real(), finalB.eval(u, v).real()), 0.0);
                            } else {
                                baseFunc = (u, v) -> finalA.eval(u, v).atan();
                            }
                        } else if (funcLower.equals("ln")) {
                            if (finalB != null) throw new IllegalArgumentException("Function '" + func + "' takes only 1 argument");
                            baseFunc = (u, v) -> finalA.eval(u, v).log();
                        } else if (funcLower.equals("log") || funcLower.equals("log10")) {
                            baseFunc = (u, v) -> finalA.eval(u, v).log10();
                        } else if (funcLower.equals("log2")) {
                            if (finalB != null) throw new IllegalArgumentException("Function '" + func + "' takes only 1 argument");
                            baseFunc = (u, v) -> finalA.eval(u, v).log().divide(Complex.ofCartesian(Math.log(2.0), 0.0));
                        } else if (funcLower.equals("abs")) {
                            if (finalB != null) throw new IllegalArgumentException("Function '" + func + "' takes only 1 argument");
                            baseFunc = (u, v) -> Complex.ofCartesian(finalA.eval(u, v).abs(), 0.0);
                        } else if (funcLower.equals("re")) {
                            if (finalB != null) throw new IllegalArgumentException("Function '" + func + "' takes only 1 argument");
                            baseFunc = (u, v) -> Complex.ofCartesian(finalA.eval(u, v).real(), 0.0);
                        } else if (funcLower.equals("im")) {
                            if (finalB != null) throw new IllegalArgumentException("Function '" + func + "' takes only 1 argument");
                            baseFunc = (u, v) -> Complex.ofCartesian(finalA.eval(u, v).imag(), 0.0);
                        } else if (funcLower.equals("arg")) {
                            if (finalB != null) throw new IllegalArgumentException("Function '" + func + "' takes only 1 argument");
                            baseFunc = (u, v) -> Complex.ofCartesian(finalA.eval(u, v).arg(), 0.0);
                        } else if (funcLower.equals("conj")) {
                            if (finalB != null) throw new IllegalArgumentException("Function '" + func + "' takes only 1 argument");
                            baseFunc = (u, v) -> finalA.eval(u, v).conj();
                        } else if (funcLower.equals("sin")) {
                            if (finalB != null) throw new IllegalArgumentException("Function '" + func + "' takes only 1 argument");
                            baseFunc = (u, v) -> finalA.eval(u, v).sin();
                        } else if (funcLower.equals("cos")) {
                            if (finalB != null) throw new IllegalArgumentException("Function '" + func + "' takes only 1 argument");
                            baseFunc = (u, v) -> finalA.eval(u, v).cos();
                        } else if (funcLower.equals("tan")) {
                            if (finalB != null) throw new IllegalArgumentException("Function '" + func + "' takes only 1 argument");
                            baseFunc = (u, v) -> finalA.eval(u, v).tan();
                        } else if (funcLower.equals("sinh")) {
                            if (finalB != null) throw new IllegalArgumentException("Function '" + func + "' takes only 1 argument");
                            baseFunc = (u, v) -> finalA.eval(u, v).sinh();
                        } else if (funcLower.equals("cosh")) {
                            if (finalB != null) throw new IllegalArgumentException("Function '" + func + "' takes only 1 argument");
                            baseFunc = (u, v) -> finalA.eval(u, v).cosh();
                        } else if (funcLower.equals("tanh")) {
                            if (finalB != null) throw new IllegalArgumentException("Function '" + func + "' takes only 1 argument");
                            baseFunc = (u, v) -> finalA.eval(u, v).tanh();
                        } else if (funcLower.equals("asinh")) {
                            if (finalB != null) throw new IllegalArgumentException("Function '" + func + "' takes only 1 argument");
                            baseFunc = (u, v) -> finalA.eval(u, v).asinh();
                        } else if (funcLower.equals("acosh")) {
                            if (finalB != null) throw new IllegalArgumentException("Function '" + func + "' takes only 1 argument");
                            baseFunc = (u, v) -> finalA.eval(u, v).acosh();
                        } else if (funcLower.equals("atanh")) {
                            if (finalB != null) throw new IllegalArgumentException("Function '" + func + "' takes only 1 argument");
                            baseFunc = (u, v) -> finalA.eval(u, v).atanh();
                        } else if (funcLower.equals("exp")) {
                            if (finalB != null) throw new IllegalArgumentException("Function '" + func + "' takes only 1 argument");
                            baseFunc = (u, v) -> finalA.eval(u, v).exp();
                        } else if (funcLower.equals("floor")) {
                            if (finalB != null) throw new IllegalArgumentException("Function '" + func + "' takes only 1 argument");
                            baseFunc = (u, v) -> Complex.ofCartesian(Math.floor(finalA.eval(u, v).real()), 0.0);
                        } else if (funcLower.equals("ceil")) {
                            if (finalB != null) throw new IllegalArgumentException("Function '" + func + "' takes only 1 argument");
                            baseFunc = (u, v) -> Complex.ofCartesian(Math.ceil(finalA.eval(u, v).real()), 0.0);
                        } else if (funcLower.equals("round")) {
                            if (finalB != null) throw new IllegalArgumentException("Function '" + func + "' takes only 1 argument");
                            baseFunc = (u, v) -> Complex.ofCartesian(Math.round(finalA.eval(u, v).real()), 0.0);
                        } else if (funcLower.equals("int")) {
                            baseFunc = (u, v) -> {
                                Complex sum = Complex.ZERO;
                                int steps = 200;
                                double stepSize = u.real() / steps;
                                for (int i = 0; i < steps; i++) {
                                    sum = sum.add(finalA.eval(Complex.ofCartesian(i * stepSize, 0.0), v).multiply(stepSize));
                                }
                                return sum;
                            };
                        } else {
                            if (declaredVars != null && (declaredVars.containsKey(func) || declaredVars.containsKey(funcLower))) {
                                ComplexExpression vDef = declaredVars.containsKey(func) ? declaredVars.get(func) : declaredVars.get(funcLower);
                                baseFunc = (u, v) -> vDef.eval(finalA.eval(u, v), v);
                            } else if (customFuncs != null && (customFuncs.containsKey(func) || customFuncs.containsKey(funcLower))) {
                                Expression custom = customFuncs.containsKey(func) ? customFuncs.get(func) : customFuncs.get(funcLower);
                                baseFunc = (u, v) -> custom.evalComplex(finalA.eval(u, v), v);
                            } else {
                                java.lang.reflect.Method foundMethod = null;
                                for (java.lang.reflect.Method m : Math.class.getMethods()) {
                                    if (m.getName().equalsIgnoreCase(funcLower) && m.getParameterCount() == 1 && m.getParameterTypes()[0] == double.class) {
                                        foundMethod = m;
                                        break;
                                    }
                                }
                                if (foundMethod != null) {
                                    final java.lang.reflect.Method m = foundMethod;
                                    baseFunc = (u, v) -> {
                                        try {
                                            double r = (double) m.invoke(null, finalA.eval(u, v).real());
                                            return Complex.ofCartesian(r, 0.0);
                                        } catch (Exception e) {
                                            return Complex.ZERO;
                                        }
                                    };
                                } else {
                                    baseFunc = (u, v) -> finalA.eval(u, v);
                                }
                            }
                        }

                        if (funcExp != null) {
                            final ComplexExpression fBase = baseFunc;
                            final ComplexExpression fExp = funcExp;
                            x = (u, v) -> safePowComplex(fBase.eval(u, v), fExp.eval(u, v));
                        } else {
                            x = baseFunc;
                        }
                    }
                } else if (ch == ')' || ch == ']' || ch == '}') {
                    nextChar();
                    x = (u, v) -> Complex.ofCartesian(1.0, 0.0);
                } else if (ch == -1) {
                    x = (u, v) -> Complex.ZERO;
                } else {
                    nextChar();
                    x = (u, v) -> Complex.ZERO;
                }
                while (ch == ' ') nextChar();
                while (eat('^')) {
                    ComplexExpression a = x;
                    while (ch == ' ') nextChar();
                    ComplexExpression b = (ch == -1 || ch == '+' || ch == '-' || ch == '*' || ch == '/' || ch == ')' || ch == ']' || ch == '}')
                            ? ((u, v) -> Complex.ofCartesian(1.0, 0.0))
                            : parseFactor();
                    x = (u, v) -> safePowComplex(a.eval(u, v), b.eval(u, v));
                }
                while (ch == ' ') nextChar();
                while (eat('!')) {
                    ComplexExpression a = x;
                    x = (u, v) -> {
                        Complex val = a.eval(u, v);
                        if (Double.isNaN(val.real()) || val.real() < 0) return Complex.ofCartesian(Double.NaN, 0.0);
                        int n = (int) Math.round(val.real());
                        if (n < 0) return Complex.ofCartesian(Double.NaN, 0.0);
                        if (n > 170) return Complex.ofCartesian(Double.POSITIVE_INFINITY, 0.0);
                        double f = 1.0;
                        for (int k = 2; k <= n; k++) f *= k;
                        return Complex.ofCartesian(f, 0.0);
                    };
                }
                return x;
            }
        }.parse();
    }

    static class DiffNum {
        int order;
        DiffNum(int order) { this.order = order; }
    }

    static class DiffDen {
        int order;
        char var;
        DiffDen(int order, char var) {
            this.order = order;
            this.var = var;
        }
    }

    static DiffNum parseDiffNum(String s) {
        if (s == null) return null;
        String t = s.trim();
        while (t.startsWith("(") && t.endsWith(")") && t.length() > 2) {
            t = t.substring(1, t.length() - 1).trim();
        }
        if (t.matches("(?i)^[d\u2202]$") || t.equalsIgnoreCase("\\partial")) {
            return new DiffNum(1);
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i)^([d\u2202]|\\\\partial)\\s*\\^\\s*(?:\\(?(\\d+)\\)?|\\{(\\d+)\\})$").matcher(t);
        if (m.matches()) {
            String ordStr = m.group(2) != null ? m.group(2) : m.group(3);
            try {
                int ord = Integer.parseInt(ordStr);
                if (ord >= 1) return new DiffNum(ord);
            } catch (Exception ignore) {}
        }
        return null;
    }

    static DiffDen parseDiffDen(String s) {
        if (s == null) return null;
        String t = s.trim();
        while (t.startsWith("(") && t.endsWith(")") && t.length() > 2) {
            t = t.substring(1, t.length() - 1).trim();
        }
        // Case 1: single d/partial followed by var: "dx", "dt", "∂x", "\partial x"
        java.util.regex.Matcher m1 = java.util.regex.Pattern.compile("(?i)^([d\u2202]|\\\\partial)\\s*([a-zA-Z])$").matcher(t);
        if (m1.matches()) {
            return new DiffDen(1, Character.toLowerCase(m1.group(2).charAt(0)));
        }
        // Case 2: d^n var: "d^2x", "d^(2)t", "d^{2}y", "\partial^2 x"
        java.util.regex.Matcher m2 = java.util.regex.Pattern.compile("(?i)^([d\u2202]|\\\\partial)\\s*\\^\\s*(?:\\(?(\\d+)\\)?|\\{(\\d+)\\})\\s*([a-zA-Z])$").matcher(t);
        if (m2.matches()) {
            String ordStr = m2.group(2) != null ? m2.group(2) : m2.group(3);
            char v = Character.toLowerCase(m2.group(4).charAt(0));
            try {
                int ord = Integer.parseInt(ordStr);
                if (ord >= 1) return new DiffDen(ord, v);
            } catch (Exception ignore) {}
        }
        // Case 3: d var^n: "dx^2", "dt^(2)", "dx^{2}", "\partial x^2"
        java.util.regex.Matcher m3 = java.util.regex.Pattern.compile("(?i)^([d\u2202]|\\\\partial)\\s*([a-zA-Z])\\s*\\^\\s*(?:\\(?(\\d+)\\)?|\\{(\\d+)\\})$").matcher(t);
        if (m3.matches()) {
            char v = Character.toLowerCase(m3.group(2).charAt(0));
            String ordStr = m3.group(3) != null ? m3.group(3) : m3.group(4);
            try {
                int ord = Integer.parseInt(ordStr);
                if (ord >= 1) return new DiffDen(ord, v);
            } catch (Exception ignore) {}
        }
        // Case 4: incomplete/autogenerated without variable yet: "d", "d^2", "d^(2)", "d^{2}"
        if (t.matches("(?i)^[d\u2202]$") || t.equalsIgnoreCase("\\partial")) {
            return new DiffDen(1, '\0');
        }
        java.util.regex.Matcher m4 = java.util.regex.Pattern.compile("(?i)^([d\u2202]|\\\\partial)\\s*\\^\\s*(?:\\(?(\\d+)\\)?|\\{(\\d+)\\})$").matcher(t);
        if (m4.matches()) {
            String ordStr = m4.group(2) != null ? m4.group(2) : m4.group(3);
            try {
                int ord = Integer.parseInt(ordStr);
                if (ord >= 1) return new DiffDen(ord, '\0');
            } catch (Exception ignore) {}
        }
        return null;
    }

    static double evalDerivative(Expression expr, double u, double v, char var, int order) {
        if (order <= 0) return expr.eval(u, v);
        if (var == 'z') return 0.0;
        boolean isY = (var == 'y');
        if (order == 1) {
            double h = 1e-5;
            if (isY) {
                return (expr.eval(u, v + h) - expr.eval(u, v - h)) / (2 * h);
            } else {
                return (expr.eval(u + h, v) - expr.eval(u - h, v)) / (2 * h);
            }
        } else if (order == 2) {
            double h = 1e-4;
            if (isY) {
                return (expr.eval(u, v + h) - 2.0 * expr.eval(u, v) + expr.eval(u, v - h)) / (h * h);
            } else {
                return (expr.eval(u + h, v) - 2.0 * expr.eval(u, v) + expr.eval(u - h, v)) / (h * h);
            }
        } else if (order == 3) {
            double h = 1e-3;
            if (isY) {
                return (expr.eval(u, v + 2 * h) - 2.0 * expr.eval(u, v + h) + 2.0 * expr.eval(u, v - h) - expr.eval(u, v - 2 * h)) / (2 * h * h * h);
            } else {
                return (expr.eval(u + 2 * h, v) - 2.0 * expr.eval(u + h, v) + 2.0 * expr.eval(u - h, v) - expr.eval(u - 2 * h, v)) / (2 * h * h * h);
            }
        } else if (order == 4) {
            double h = 1e-2;
            if (isY) {
                return (expr.eval(u, v + 2 * h) - 4.0 * expr.eval(u, v + h) + 6.0 * expr.eval(u, v) - 4.0 * expr.eval(u, v - h) + expr.eval(u, v - 2 * h)) / (h * h * h * h);
            } else {
                return (expr.eval(u + 2 * h, v) - 4.0 * expr.eval(u + h, v) + 6.0 * expr.eval(u, v) - 4.0 * expr.eval(u - h, v) + expr.eval(u - 2 * h, v)) / (h * h * h * h);
            }
        } else {
            Expression lower = (xVal, yVal) -> evalDerivative(expr, xVal, yVal, var, order - 2);
            return evalDerivative(lower, u, v, var, 2);
        }
    }

    public static boolean isFunctionRequiringParens(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase();
        switch (lower) {
            case "rect":
            case "sinc":
            case "tri":
            case "triangle":
            case "trapz":
            case "saw":
            case "sqw":
            case "square":
            case "pulse":
            case "step":
            case "heaviside":
            case "drcd":
            case "delta":
            case "dirac":
            case "drcc":
            case "comb":
            case "shah":
            case "rep":
            case "per":
            case "sign":
            case "sqrt":
            case "sin":
            case "cos":
            case "tan":
            case "asin":
            case "arcsin":
            case "acos":
            case "arccos":
            case "atan":
            case "arctan":
            case "atan2":
            case "sinh":
            case "cosh":
            case "tanh":
            case "asinh":
            case "acosh":
            case "atanh":
            case "ln":
            case "log":
            case "log10":
            case "log2":
            case "abs":
            case "cbrt":
            case "cot":
            case "sec":
            case "csc":
            case "exp":
            case "floor":
            case "ceil":
            case "round":
            case "re":
            case "im":
            case "arg":
            case "conj":
                return true;
            default:
                if (lower.startsWith("diff")) return true;
                if (name.equals("δ") || name.equals("Δ") || name.equals("Ш") || name.equals("ш") || name.equals("θ") || name.equals("Θ")) return true;
                return false;
        }
    }

    static Complex evalDerivativeComplex(ComplexExpression expr, Complex u, Complex v, char var, int order) {
        if (order <= 0) return expr.eval(u, v);
        if (var == 'z') return Complex.ZERO;
        boolean isY = (var == 'y');
        if (order == 1) {
            double h = 1e-5;
            Complex hC = Complex.ofCartesian(h, 0.0);
            if (isY) {
                return expr.eval(u, v.add(hC)).subtract(expr.eval(u, v.subtract(hC))).divide(Complex.ofCartesian(2 * h, 0.0));
            } else {
                return expr.eval(u.add(hC), v).subtract(expr.eval(u.subtract(hC), v)).divide(Complex.ofCartesian(2 * h, 0.0));
            }
        } else if (order == 2) {
            double h = 1e-4;
            Complex hC = Complex.ofCartesian(h, 0.0);
            if (isY) {
                return expr.eval(u, v.add(hC)).subtract(expr.eval(u, v).multiply(2.0)).add(expr.eval(u, v.subtract(hC))).divide(Complex.ofCartesian(h * h, 0.0));
            } else {
                return expr.eval(u.add(hC), v).subtract(expr.eval(u, v).multiply(2.0)).add(expr.eval(u.subtract(hC), v)).divide(Complex.ofCartesian(h * h, 0.0));
            }
        } else if (order == 3) {
            double h = 1e-3;
            Complex hC = Complex.ofCartesian(h, 0.0);
            Complex twoHC = Complex.ofCartesian(2 * h, 0.0);
            if (isY) {
                return expr.eval(u, v.add(twoHC)).subtract(expr.eval(u, v.add(hC)).multiply(2.0)).add(expr.eval(u, v.subtract(hC)).multiply(2.0)).subtract(expr.eval(u, v.subtract(twoHC))).divide(Complex.ofCartesian(2 * h * h * h, 0.0));
            } else {
                return expr.eval(u.add(twoHC), v).subtract(expr.eval(u.add(hC), v).multiply(2.0)).add(expr.eval(u.subtract(hC), v).multiply(2.0)).subtract(expr.eval(u.subtract(twoHC), v)).divide(Complex.ofCartesian(2 * h * h * h, 0.0));
            }
        } else if (order == 4) {
            double h = 1e-2;
            Complex hC = Complex.ofCartesian(h, 0.0);
            Complex twoHC = Complex.ofCartesian(2 * h, 0.0);
            if (isY) {
                return expr.eval(u, v.add(twoHC)).subtract(expr.eval(u, v.add(hC)).multiply(4.0)).add(expr.eval(u, v).multiply(6.0)).subtract(expr.eval(u, v.subtract(hC)).multiply(4.0)).add(expr.eval(u, v.subtract(twoHC))).divide(Complex.ofCartesian(h * h * h * h, 0.0));
            } else {
                return expr.eval(u.add(twoHC), v).subtract(expr.eval(u.add(hC), v).multiply(4.0)).add(expr.eval(u, v).multiply(6.0)).subtract(expr.eval(u.subtract(hC), v).multiply(4.0)).add(expr.eval(u.subtract(twoHC), v)).divide(Complex.ofCartesian(h * h * h * h, 0.0));
            }
        } else {
            ComplexExpression lower = (xVal, yVal) -> evalDerivativeComplex(expr, xVal, yVal, var, order - 2);
            return evalDerivativeComplex(lower, u, v, var, 2);
        }
    }
}
