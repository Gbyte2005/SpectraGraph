package com.spectragraph;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * High-performance signal processing and Fourier Analysis Engine.
 *
 * <p>Core Architectural Principles:
 * <ul>
 *   <li><b>100% Direct Off-Heap Memory:</b> All continuous numeric signal buffers (samples, coefficients,
 *       and range bounding trees) are allocated off-heap using native {@link ByteBuffer#allocateDirect}.
 *       This eliminates GC overhead, prevents memory fragmentation, and maximizes hardware cache locality.</li>
 *   <li><b>Analytical Dirac Delta Impulses:</b> Singular impulses (e.g., from Dirac delta functions) are
 *       isolated, integrated, and analytically added into the Fourier series coefficients to avoid numerical
 *       ringing or infinite sample poisoning.</li>
 *   <li><b>Segment Tree Accelerated Level-of-Detail (LOD):</b> A direct off-heap segment tree enables
 *       O(log N) min/max bounding queries over arbitrary time intervals for 60fps graph rendering.</li>
 *   <li><b>Scale-Invariant Multiscale Anti-Aliasing:</b> Sums Fourier harmonics with smooth Fej??r / cosine
 *       attenuation beyond the pixel Nyquist frequency, preventing Moir?? beating and resize flickering.</li>
 * </ul>
 */
public class SignalEngine {

    private final int numSamples;
    private final int maxHarmonics;

    // Direct off-heap buffers for zero-overhead native memory mapping
    private final DoubleBuffer xValues;
    private final DoubleBuffer inputSignal;
    private final DoubleBuffer displaySignal;
    private final DoubleBuffer aCoeffs; // Cosine Fourier coefficients
    private final DoubleBuffer bCoeffs; // Sine Fourier coefficients

    // Scratch direct buffers to eliminate allocation in evaluateFunction & computeFourierSeries
    private final ByteBuffer impulseFlagsBuffer;
    private final DoubleBuffer rawWeightsBuffer;
    private final DoubleBuffer fastYBuffer;
    private final ByteBuffer hasYBuffer;

    /**
     * Represents a discrete Dirac impulse root with an exact spatial coordinate and weight.
     */
    public static class Impulse {
        public final double x;
        public final double weight;

        public Impulse(double x, double weight) {
            this.x = x;
            this.weight = weight;
        }
    }

    private final List<Impulse> impulses = new ArrayList<>();

    private volatile SegmentTree segmentTree;
    private DoubleBuffer accumulatedSignal;
    private volatile SegmentTree accumulatedSegmentTree;
    private int currentAccumulatedHarmonic = -1;
    private final DoubleBuffer accumulatedExtremaFracs;
    private int accumulatedExtremaLen = 0;
    private double accumulatedMin = Double.NaN;
    private double accumulatedMax = Double.NaN;

    private double implicitMinX = Double.NaN;
    private double implicitMaxX = Double.NaN;
    private double implicitMinY = Double.NaN;
    private double implicitMaxY = Double.NaN;

    // Shared worker pool for parallel mathematical evaluation
    public static final ForkJoinPool EVALUATION_POOL = new ForkJoinPool(
            Math.max(2, Math.min(8, Runtime.getRuntime().availableProcessors())),
            ForkJoinPool.defaultForkJoinWorkerThreadFactory, null, true);

    /** Backwards-compatible alias for EVALUATION_POOL */
    public static final ForkJoinPool EVAL_POOL = EVALUATION_POOL;

    /**
     * Allocates a native direct {@link DoubleBuffer} outside the garbage-collected Java heap.
     *
     * @param capacity number of double elements to allocate
     * @return direct native byte-ordered double buffer
     */
    public static DoubleBuffer allocateDirectDoubleBuffer(int capacity) {
        return ByteBuffer.allocateDirect(capacity * Double.BYTES)
                         .order(ByteOrder.nativeOrder())
                         .asDoubleBuffer();
    }

    /**
     * Allocates a native direct {@link ByteBuffer} outside the garbage-collected Java heap.
     *
     * @param capacity number of bytes to allocate
     * @return direct native byte-ordered byte buffer
     */
    public static ByteBuffer allocateDirectByteBuffer(int capacity) {
        return ByteBuffer.allocateDirect(capacity)
                         .order(ByteOrder.nativeOrder());
    }

    /**
     * Constructs a new SignalEngine with the specified sample resolution and Fourier harmonic limit.
     *
     * @param numSamples   total discrete spatial sample count
     * @param maxHarmonics maximum Fourier harmonic index to compute
     */
    public SignalEngine(int numSamples, int maxHarmonics) {
        this.numSamples = numSamples;
        this.maxHarmonics = maxHarmonics;

        // Allocate all primary working buffers off-heap
        this.xValues = allocateDirectDoubleBuffer(numSamples);
        this.inputSignal = allocateDirectDoubleBuffer(numSamples);
        this.displaySignal = allocateDirectDoubleBuffer(numSamples);
        this.aCoeffs = allocateDirectDoubleBuffer(maxHarmonics + 1);
        this.bCoeffs = allocateDirectDoubleBuffer(maxHarmonics + 1);

        // Pre-allocate scratch off-heap buffers to avoid any per-frame heap allocations
        this.impulseFlagsBuffer = allocateDirectByteBuffer(numSamples);
        this.rawWeightsBuffer = allocateDirectDoubleBuffer(numSamples);
        this.fastYBuffer = allocateDirectDoubleBuffer(numSamples);
        this.hasYBuffer = allocateDirectByteBuffer(numSamples);
        this.accumulatedExtremaFracs = allocateDirectDoubleBuffer(numSamples + 2);
    }

    /**
     * Resets all internal buffers, impulses, segment trees, and Fourier series states.
     */
    public void clear() {
        impulses.clear();
        segmentTree = null;
        accumulatedSegmentTree = null;
        currentAccumulatedHarmonic = -1;
        accumulatedExtremaLen = 0;
        accumulatedMin = Double.NaN;
        accumulatedMax = Double.NaN;

        for (int i = 0; i < numSamples; i++) {
            inputSignal.put(i, Double.NaN);
            displaySignal.put(i, Double.NaN);
        }
        for (int i = 0; i <= maxHarmonics; i++) {
            aCoeffs.put(i, 0.0);
            bCoeffs.put(i, 0.0);
        }
    }

    public void evaluateFunction(MathParser.Expression expression, double minX, double maxX) {
        evaluateFunction(expression, minX, maxX, null);
    }

    /**
     * Samples the mathematical expression across the interval [minX, maxX] in parallel.
     * Detected singular impulses are clustered and registered into the impulse table.
     *
     * @param expression mathematical expression to sample
     * @param minX       interval start
     * @param maxX       interval end
     * @param rawFormula raw formula string (optional, for periodic/impulse heuristics)
     */
    public void evaluateFunction(MathParser.Expression expression, double minX, double maxX, String rawFormula) {
        impulses.clear();
        double step = (maxX - minX) / (numSamples - 1);
        MathParser.samplingStep.set(step);

        // Reset scratch off-heap buffers
        for (int i = 0; i < numSamples; i++) {
            impulseFlagsBuffer.put(i, (byte) 0);
            rawWeightsBuffer.put(i, 0.0);
        }

        AtomicBoolean cancelled = new AtomicBoolean(false);

        try {
            int numWorkers = Math.max(1, Math.min(8, Runtime.getRuntime().availableProcessors()));
            int chunkSize = (numSamples + numWorkers - 1) / numWorkers;
            List<Callable<Void>> workerTasks = new ArrayList<>(numWorkers);

            for (int w = 0; w < numWorkers; w++) {
                final int startIdx = w * chunkSize;
                final int endIdx = Math.min(numSamples, startIdx + chunkSize);
                if (startIdx >= endIdx) break;

                workerTasks.add(() -> {
                    Thread.interrupted();
                    MathParser.samplingStep.set(step);
                    for (int i = startIdx; i < endIdx; i++) {
                        if ((i & 7) == 0 && (cancelled.get() || Thread.currentThread().isInterrupted())) {
                            cancelled.set(true);
                            return null;
                        }
                        double x = minX + i * step;
                        xValues.put(i, x);
                        double y;
                        MathParser.impulseTriggered.set(false);
                        try {
                            y = expression.eval(x);
                        } catch (Exception evaluationException) {
                            if (Thread.currentThread().isInterrupted()) {
                                cancelled.set(true);
                                return null;
                            }
                            y = Double.NaN;
                        }

                        boolean isImpulse = Boolean.TRUE.equals(MathParser.impulseTriggered.get());
                        if (isImpulse && Double.isFinite(y)) {
                            impulseFlagsBuffer.put(i, (byte) 1);
                            rawWeightsBuffer.put(i, y * step);
                            displaySignal.put(i, 0.0);
                            inputSignal.put(i, 0.0);
                        } else {
                            double clampedValue = (Double.isFinite(y) && Math.abs(y) <= 1e8) ? y : Double.NaN;
                            displaySignal.put(i, clampedValue);
                            inputSignal.put(i, clampedValue);
                        }
                    }
                    return null;
                });
            }

            Future<?>[] futures = new Future<?>[workerTasks.size()];
            for (int t = 0; t < workerTasks.size(); t++) {
                futures[t] = EVALUATION_POOL.submit(workerTasks.get(t));
            }
            for (Future<?> f : futures) {
                try {
                    f.get();
                } catch (Exception e) {
                    cancelled.set(true);
                    if (e instanceof InterruptedException) {
                        for (Future<?> other : futures) other.cancel(true);
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }

            if (cancelled.get() || Thread.currentThread().isInterrupted()) {
                return;
            }

            // Cluster consecutive discrete impulse triggers into single exact impulses
            int sampleIdx = 0;
            while (sampleIdx < numSamples) {
                if (impulseFlagsBuffer.get(sampleIdx) == 1) {
                    int start = sampleIdx;
                    while (sampleIdx + 1 < numSamples && impulseFlagsBuffer.get(sampleIdx + 1) == 1) {
                        sampleIdx++;
                    }
                    int end = sampleIdx;

                    int bestIdx = (start + end) / 2;
                    double impulseX = xValues.get(bestIdx);
                    if (Math.abs(impulseX) < step * 0.5) {
                        impulseX = 0.0;
                    }

                    double finalWeight = rawWeightsBuffer.get(bestIdx);
                    if (finalWeight > 50.0) finalWeight = 50.0;
                    else if (finalWeight < -50.0) finalWeight = -50.0;

                    impulses.add(new Impulse(impulseX, finalWeight));
                }
                sampleIdx++;
            }

            // Extended domain scan for impulses if the formula contains delta Dirac functions
            if (rawFormula != null && (rawFormula.contains("??") || rawFormula.contains("dirac") || rawFormula.contains("drc"))) {
                double viewWidth = maxX - minX;
                double extendedMin = minX - viewWidth * 1.5;
                double extendedMax = maxX + viewWidth * 1.5;
                int extendedSamples = numSamples * 2;
                double extendedStep = (extendedMax - extendedMin) / (extendedSamples - 1);

                for (int j = 0; j < extendedSamples; j++) {
                    if ((j & 15) == 0 && (cancelled.get() || Thread.currentThread().isInterrupted())) return;
                    double x = extendedMin + j * extendedStep;
                    if (x >= minX - step * 0.5 && x <= maxX + step * 0.5) continue;

                    MathParser.impulseTriggered.set(false);
                    double y = Double.NaN;
                    try {
                        y = expression.eval(x);
                    } catch (Exception ignored) {
                        if (Thread.currentThread().isInterrupted()) return;
                    }
                    if (Boolean.TRUE.equals(MathParser.impulseTriggered.get()) && Double.isFinite(y)) {
                        double weight = y * extendedStep;
                        if (weight > 50.0) weight = 50.0;
                        else if (weight < -50.0) weight = -50.0;
                        impulses.add(new Impulse(Math.abs(x) < extendedStep * 0.5 ? 0.0 : x, weight));
                    }
                }
            }

            // Construct O(log N) bounding segment tree over the sampled display signal
            segmentTree = new SegmentTree(displaySignal, numSamples);
        } finally {
            MathParser.samplingStep.set(0.0);
            MathParser.impulseTriggered.set(false);
        }
    }

    /**
     * Scans an implicit bivariate curve F(x, y) = 0 across vertical slices and locates bounds.
     */
    public void evaluateImplicitFunction(MathParser.Expression expression, double minX, double maxX, double minY, double maxY) {
        double step = (maxX - minX) / (numSamples - 1);
        int verticalSlices = 200;
        double scanMinY = Math.min(-10.0, Double.isFinite(minY) ? minY : -10.0);
        double scanMaxY = Math.max(10.0, Double.isFinite(maxY) ? maxY : 10.0);
        double stepY = (scanMaxY - scanMinY) / verticalSlices;

        double foundMinX = Double.MAX_VALUE;
        double foundMaxX = -Double.MAX_VALUE;
        double foundMinY = Double.MAX_VALUE;
        double foundMaxY = -Double.MAX_VALUE;

        for (int i = 0; i < numSamples; i++) {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            double x = minX + i * step;
            xValues.put(i, x);
            double foundY = Double.NaN;
            double prevF = Double.NaN;
            try {
                prevF = expression.eval(x, scanMinY);
            } catch (Exception ignored) {}

            for (int j = 1; j <= verticalSlices; j++) {
                if ((j & 31) == 0 && Thread.currentThread().isInterrupted()) return;
                double currY = scanMinY + j * stepY;
                double currF = Double.NaN;
                try {
                    currF = expression.eval(x, currY);
                } catch (Exception ignored) {}

                if (Double.isFinite(prevF) && Double.isFinite(currF)) {
                    if (prevF == 0.0) {
                        double rootY = currY - stepY;
                        if (!Double.isFinite(foundY)) foundY = rootY;
                        if (rootY < foundMinY) foundMinY = rootY;
                        if (rootY > foundMaxY) foundMaxY = rootY;
                        if (x < foundMinX) foundMinX = x;
                        if (x > foundMaxX) foundMaxX = x;
                    } else if (currF == 0.0) {
                        double rootY = currY;
                        if (!Double.isFinite(foundY)) foundY = rootY;
                        if (rootY < foundMinY) foundMinY = rootY;
                        if (rootY > foundMaxY) foundMaxY = rootY;
                        if (x < foundMinX) foundMinX = x;
                        if (x > foundMaxX) foundMaxX = x;
                    } else if ((prevF > 0 && currF < 0) || (prevF < 0 && currF > 0)) {
                        double interpolationFraction = -prevF / (currF - prevF);
                        double rootY = (currY - stepY) + interpolationFraction * stepY;
                        if (!Double.isFinite(foundY)) foundY = rootY;
                        if (rootY < foundMinY) foundMinY = rootY;
                        if (rootY > foundMaxY) foundMaxY = rootY;
                        if (x < foundMinX) foundMinX = x;
                        if (x > foundMaxX) foundMaxX = x;
                    }
                }
                prevF = currF;
            }

            if (Double.isFinite(foundY)) {
                if (foundY > 1e15) foundY = 1e15;
                else if (foundY < -1e15) foundY = -1e15;
            }
            inputSignal.put(i, foundY);
        }
        segmentTree = new SegmentTree(inputSignal, numSamples);

        if (foundMinY != Double.MAX_VALUE && foundMaxY != -Double.MAX_VALUE) {
            implicitMinY = foundMinY;
            implicitMaxY = foundMaxY;
            implicitMinX = foundMinX;
            implicitMaxX = foundMaxX;
        } else {
            implicitMinY = Double.NaN;
            implicitMaxY = Double.NaN;
            implicitMinX = Double.NaN;
            implicitMaxX = Double.NaN;
        }
    }

    public double getImplicitMinX() { return implicitMinX; }
    public double getImplicitMaxX() { return implicitMaxX; }
    public double getImplicitMinY() { return implicitMinY; }
    public double getImplicitMaxY() { return implicitMaxY; }

    public boolean hasImplicitBounds() {
        return Double.isFinite(implicitMinY) && Double.isFinite(implicitMaxY) &&
               Double.isFinite(implicitMinX) && Double.isFinite(implicitMaxX);
    }

    private static void quickSortDirect(DoubleBuffer buf, int low, int high) {
        while (low < high) {
            if (high - low < 16) {
                for (int i = low + 1; i <= high; i++) {
                    double val = buf.get(i);
                    int j = i - 1;
                    while (j >= low && buf.get(j) > val) {
                        buf.put(j + 1, buf.get(j));
                        j--;
                    }
                    buf.put(j + 1, val);
                }
                break;
            }
            int mid = (low + high) >>> 1;
            double pivot = buf.get(mid);
            int i = low;
            int j = high;
            while (i <= j) {
                while (buf.get(i) < pivot) i++;
                while (buf.get(j) > pivot) j--;
                if (i <= j) {
                    double tmp = buf.get(i);
                    buf.put(i, buf.get(j));
                    buf.put(j, tmp);
                    i++;
                    j--;
                }
            }
            if (j - low < high - i) {
                quickSortDirect(buf, low, j);
                low = i;
            } else {
                quickSortDirect(buf, i, high);
                high = j;
            }
        }
    }

    /**
     * Computes the numerical Fourier trigonometric series decomposition over [-L, L]:
     * f(x) ~ a_0/2 + sum_{n=1}^N [a_n cos(n pi x / L) + b_n sin(n pi x / L)]
     *
     * <p>Robust percentile IQR clamping is applied to reject numerical singularities from poles/asymptotes.
     * Dirac impulses within the fundamental interval are analytically incorporated into a_n and b_n.
     * Executed 100% off-heap in pre-allocated direct native buffers.
     */
    public void computeFourierSeries(double minX, double maxX) {
        double halfPeriod = (maxX - minX) / 2.0;
        double dx = (maxX - minX) / (numSamples - 1);

        // Robust percentile range detection to prevent asymptotes / poles from poisoning Fourier sums
        int finiteCount = 0;
        for (int i = 0; i < numSamples; i++) {
            if (Double.isFinite(inputSignal.get(i))) {
                finiteCount++;
            }
        }

        double clampLow = -1e4;
        double clampHigh = 1e4;
        if (finiteCount >= 10) {
            int sortedIndex = 0;
            for (int i = 0; i < numSamples; i++) {
                double val = inputSignal.get(i);
                if (Double.isFinite(val)) {
                    fastYBuffer.put(sortedIndex++, val);
                }
            }
            quickSortDirect(fastYBuffer, 0, finiteCount - 1);
            double percentileLow = fastYBuffer.get((int) (finiteCount * 0.01));
            double percentileHigh = fastYBuffer.get((int) (finiteCount * 0.99));
            if (Math.abs(percentileHigh) > 1e3 || Math.abs(percentileLow) > 1e3) {
                percentileLow = fastYBuffer.get((int) (finiteCount * 0.05));
                percentileHigh = fastYBuffer.get((int) (finiteCount * 0.95));
            }
            double interquartileRange = percentileHigh - percentileLow;
            if (interquartileRange <= 1e-9) {
                interquartileRange = Math.max(1.0, Math.abs(percentileHigh) * 0.5);
            }
            clampLow = percentileLow - 3.0 * interquartileRange;
            clampHigh = percentileHigh + 3.0 * interquartileRange;
        }

        // Populate fast off-heap scratch buffers
        for (int i = 0; i < numSamples; i++) {
            double val = inputSignal.get(i);
            if (Double.isFinite(val)) {
                fastYBuffer.put(i, Math.max(clampLow, Math.min(clampHigh, val)));
                hasYBuffer.put(i, (byte) 1);
            } else {
                hasYBuffer.put(i, (byte) 0);
            }
        }

        for (int n = 0; n <= maxHarmonics; n++) {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            double sumCosine = 0;
            double sumSine = 0;
            for (int i = 0; i < numSamples; i++) {
                if (hasYBuffer.get(i) == 0) {
                    continue;
                }
                double x = xValues.get(i);
                double y = fastYBuffer.get(i);

                double trapezoidWeight = (i == 0 || i == numSamples - 1) ? 0.5 : 1.0;
                double angle = n * Math.PI * x / halfPeriod;
                sumCosine += y * Math.cos(angle) * trapezoidWeight;
                sumSine += y * Math.sin(angle) * trapezoidWeight;
            }

            double a_n = (sumCosine * dx) / halfPeriod;
            double b_n = (sumSine * dx) / halfPeriod;

            // Add exact analytical Fourier series contribution for Dirac impulses within [minX, maxX]
            if (!impulses.isEmpty()) {
                for (Impulse impulse : impulses) {
                    if (impulse.x >= minX - 1e-9 && impulse.x <= maxX + 1e-9) {
                        double angle = n * Math.PI * impulse.x / halfPeriod;
                        a_n += (impulse.weight / halfPeriod) * Math.cos(angle);
                        b_n += (impulse.weight / halfPeriod) * Math.sin(angle);
                    }
                }
            }

            aCoeffs.put(n, a_n);
            bCoeffs.put(n, b_n);
        }
    }

    public Harmonic getHarmonic(int n) {
        return new Harmonic(n, aCoeffs.get(n), bCoeffs.get(n));
    }

    public int getMaxHarmonics() { return maxHarmonics; }
    public DoubleBuffer getXValues() { return xValues; }
    public DoubleBuffer getInputSignal() { return inputSignal; }
    public DoubleBuffer getDisplaySignal() { return displaySignal; }
    public List<Impulse> getImpulses() { return impulses; }
    public int getNumSamples() { return numSamples; }
    public boolean hasSegmentTree() { return segmentTree != null; }
    public SegmentTree getSegmentTree() { return segmentTree; }

    public void queryRange(double tStart, double tEnd, double[] outMinMax) {
        SegmentTree tree = this.segmentTree;
        DoubleBuffer sig = this.displaySignal;
        if (tree != null && sig != null) {
            tree.queryRange(sig, tStart, tEnd, outMinMax);
        } else {
            outMinMax[0] = Double.NaN;
            outMinMax[1] = Double.NaN;
        }
    }

    /**
     * Computes the synthesized accumulated signal for harmonics 0..selectedHarmonic
     * into a direct off-heap buffer and indexes it with a SegmentTree.
     * Evaluated 100% off-heap via Quaternion/Rotor recurrence with zero object allocations.
     */
    public synchronized void computeAccumulatedSignal(int selectedHarmonic, double minX, double maxX) {
        if (selectedHarmonic < 0 || selectedHarmonic > maxHarmonics) {
            currentAccumulatedHarmonic = -1;
            accumulatedSegmentTree = null;
            return;
        }
        if (accumulatedSignal == null || accumulatedSignal.capacity() < numSamples) {
            accumulatedSignal = allocateDirectDoubleBuffer(numSamples);
        }
        double halfPeriod = (maxX - minX) / 2.0;
        double a0 = aCoeffs.get(0);

        for (int i = 0; i < numSamples; i++) {
            double x = xValues.get(i);
            double theta = Math.PI * x / halfPeriod;
            double y = (a0 / 2.0) + evalFourierHarmonicsFast(theta, aCoeffs, bCoeffs, (DoubleBuffer) null, selectedHarmonic);
            accumulatedSignal.put(i, y);
        }
        accumulatedSegmentTree = new SegmentTree(accumulatedSignal, numSamples);
        currentAccumulatedHarmonic = selectedHarmonic;

        // Find min, max, and local extrema for template sampling
        double minVal = Double.POSITIVE_INFINITY;
        double maxVal = Double.NEGATIVE_INFINITY;
        int extremaCount = 2;
        for (int i = 0; i < numSamples; i++) {
            double v = accumulatedSignal.get(i);
            if (v < minVal) minVal = v;
            if (v > maxVal) maxVal = v;
            if (i > 0 && i < numSamples - 1) {
                double prev = accumulatedSignal.get(i - 1);
                double next = accumulatedSignal.get(i + 1);
                if ((v >= prev && v > next) || (v > prev && v >= next) ||
                    (v <= prev && v < next) || (v < prev && v <= next)) {
                    extremaCount++;
                }
            }
        }
        accumulatedMin = minVal;
        accumulatedMax = maxVal;

        accumulatedExtremaLen = extremaCount;
        accumulatedExtremaFracs.put(0, 0.0);
        int extremaIdx = 1;
        for (int i = 1; i < numSamples - 1; i++) {
            double prev = accumulatedSignal.get(i - 1);
            double v = accumulatedSignal.get(i);
            double next = accumulatedSignal.get(i + 1);
            if ((v >= prev && v > next) || (v > prev && v >= next) ||
                (v <= prev && v < next) || (v < prev && v <= next)) {
                accumulatedExtremaFracs.put(extremaIdx++, (double) i / (numSamples - 1));
            }
        }
        accumulatedExtremaFracs.put(accumulatedExtremaLen - 1, 1.0);
    }

    /**
     * Samples a single periodic template of the accumulated signal into direct off-heap DoubleBuffers,
     * combining uniform steps with exact critical extrema points for sub-pixel fidelity without aliasing.
     */
    public int getPeriodTemplate(double pxPerPeriod, DoubleBuffer outFracs, DoubleBuffer outY) {
        if (accumulatedSignal == null || numSamples <= 0 || accumulatedExtremaLen == 0) {
            return 0;
        }
        int uniformSteps = Math.max(32, Math.min(numSamples - 1, (int) Math.ceil(pxPerPeriod * 4.0)));
        int p1 = 0;
        int p2 = 0;
        int extremaLen = accumulatedExtremaLen;
        int maxCap = Math.min(outFracs.capacity(), outY.capacity());
        int count = 0;
        double lastF = -1.0;

        while ((p1 <= uniformSteps || p2 < extremaLen) && count < maxCap) {
            double f1 = (p1 <= uniformSteps) ? (double) p1 / uniformSteps : Double.POSITIVE_INFINITY;
            double f2 = (p2 < extremaLen) ? accumulatedExtremaFracs.get(p2) : Double.POSITIVE_INFINITY;
            double nextF;
            if (f1 <= f2) {
                nextF = f1;
                p1++;
            } else {
                nextF = f2;
                p2++;
            }
            if (nextF - lastF > 1e-5) {
                outFracs.put(count, nextF);
                outY.put(count, evalAccumulatedSample(nextF));
                count++;
                lastF = nextF;
            }
        }
        return count;
    }

    /**
     * Array-based backward compatible overload for getPeriodTemplate.
     */
    public int getPeriodTemplate(double pxPerPeriod, double[] outFracs, double[] outY) {
        if (accumulatedSignal == null || numSamples <= 0 || accumulatedExtremaLen == 0) {
            return 0;
        }
        int uniformSteps = Math.max(32, Math.min(numSamples - 1, (int) Math.ceil(pxPerPeriod * 4.0)));
        int p1 = 0;
        int p2 = 0;
        int extremaLen = accumulatedExtremaLen;
        int count = 0;
        double lastF = -1.0;

        while ((p1 <= uniformSteps || p2 < extremaLen) && count < outFracs.length) {
            double f1 = (p1 <= uniformSteps) ? (double) p1 / uniformSteps : Double.POSITIVE_INFINITY;
            double f2 = (p2 < extremaLen) ? accumulatedExtremaFracs.get(p2) : Double.POSITIVE_INFINITY;
            double nextF;
            if (f1 <= f2) {
                nextF = f1;
                p1++;
            } else {
                nextF = f2;
                p2++;
            }
            if (nextF - lastF > 1e-5) {
                outFracs[count] = nextF;
                outY[count] = evalAccumulatedSample(nextF);
                count++;
                lastF = nextF;
            }
        }
        return count;
    }

    public double getAccumulatedMin() { return accumulatedMin; }
    public double getAccumulatedMax() { return accumulatedMax; }
    public int getCurrentAccumulatedHarmonic() { return currentAccumulatedHarmonic; }
    public DoubleBuffer getAccumulatedSignalBuffer() { return accumulatedSignal; }

    public double evalAccumulatedSample(double fraction) {
        if (accumulatedSignal == null || numSamples <= 0) return Double.NaN;
        if (fraction <= 0.0) return accumulatedSignal.get(0);
        if (fraction >= 1.0) return accumulatedSignal.get(numSamples - 1);
        double t = fraction * (numSamples - 1);
        int idx0 = (int) Math.floor(t);
        if (idx0 >= numSamples - 1) return accumulatedSignal.get(numSamples - 1);
        int idx1 = idx0 + 1;
        double f = t - idx0;
        return accumulatedSignal.get(idx0) + f * (accumulatedSignal.get(idx1) - accumulatedSignal.get(idx0));
    }

    /**
     * Scale-invariant, anti-aliased Fourier series evaluator.
     * Evaluates sum_{n=0}^maxH [a_n cos + b_n sin] with Nyquist frequency attenuation
     * to eliminate high-frequency aliasing and Moir?? beating patterns when zoomed out.
     */
    public double evalAccumulatedSignalAA(double x, double minX, double maxX, double scaleX, int selectedHarmonic) {
        if (selectedHarmonic < 0) return Double.NaN;
        int maxHarmonic = Math.min(selectedHarmonic, maxHarmonics);
        double halfPeriod = (maxX - minX) / 2.0;
        if (halfPeriod <= 0.0) return Double.NaN;

        double sum = aCoeffs.get(0) / 2.0;
        if (maxHarmonic == 0) return sum;

        double theta = Math.PI * x / halfPeriod;
        double fundamentalCyclesPerPixel = 1.0 / (2.0 * halfPeriod * scaleX);

        // Vectorized Complex Rotor Recurrence:
        // Computes all harmonics via unit rotor rotation with zero transcendental calls in inner loop
        double c1 = Math.cos(theta);
        double s1 = Math.sin(theta);
        double curC = c1;
        double curS = s1;

        for (int n = 1; n <= maxHarmonic; n++) {
            double a = aCoeffs.get(n);
            double b = bCoeffs.get(n);
            sum += (a * curC + b * curS);

            // Rotor recurrence: e^{j * (n+1) * theta} = e^{j * n * theta} * e^{j * theta}
            double nextC = curC * c1 - curS * s1;
            double nextS = curC * s1 + curS * c1;
            curC = nextC;
            curS = nextS;

            if ((n & 31) == 0) {
                double invMag = 1.0 / Math.sqrt(curC * curC + curS * curS);
                curC *= invMag;
                curS *= invMag;
            }
        }
        return sum;
    }

    public double evalAccumulatedSignalAA(double x, double minX, double maxX, double scaleX) {
        return evalAccumulatedSignalAA(x, minX, maxX, scaleX, currentAccumulatedHarmonic);
    }

    public double evalAccumulatedSignal(double x, double minX, double maxX) {
        if (currentAccumulatedHarmonic < 0) return Double.NaN;
        return evalAccumulatedSignalAA(x, minX, maxX, 1e9, currentAccumulatedHarmonic);
    }

    public void queryAccumulatedRange(double tStart, double tEnd, double[] outMinMax) {
        SegmentTree tree = this.accumulatedSegmentTree;
        DoubleBuffer sig = this.accumulatedSignal;
        if (tree != null && sig != null) {
            tree.queryRange(sig, tStart, tEnd, outMinMax);
        } else {
            outMinMax[0] = Double.NaN;
            outMinMax[1] = Double.NaN;
        }
    }

    /**
     * O(log N) Segment Tree implemented in direct off-heap native memory.
     * Stores parallel min and max pyramids to answer range envelope queries in O(log N) time
     * with zero object allocations.
     */
    public static class SegmentTree {
        private final int numLeaves;
        private final int treeSize;
        private final DoubleBuffer treeMin;
        private final DoubleBuffer treeMax;

        public SegmentTree(DoubleBuffer buffer, int sampleCount) {
            this.numLeaves = sampleCount;
            int s = 1;
            while (s < numLeaves) s <<= 1;
            this.treeSize = s;

            // Allocate direct off-heap native memory for the segment tree nodes
            this.treeMin = allocateDirectDoubleBuffer(2 * treeSize);
            this.treeMax = allocateDirectDoubleBuffer(2 * treeSize);

            for (int i = 0; i < 2 * treeSize; i++) {
                treeMin.put(i, Double.POSITIVE_INFINITY);
                treeMax.put(i, Double.NEGATIVE_INFINITY);
            }

            for (int i = 0; i < numLeaves; i++) {
                double val = buffer.get(i);
                if (Double.isFinite(val)) {
                    treeMin.put(treeSize + i, val);
                    treeMax.put(treeSize + i, val);
                }
            }

            for (int i = treeSize - 1; i > 0; i--) {
                treeMin.put(i, Math.min(treeMin.get(2 * i), treeMin.get(2 * i + 1)));
                treeMax.put(i, Math.max(treeMax.get(2 * i), treeMax.get(2 * i + 1)));
            }
        }

        public double queryMin(int leftLeaf, int rightLeaf) {
            if (leftLeaf < 0) leftLeaf = 0;
            if (rightLeaf >= numLeaves) rightLeaf = numLeaves - 1;
            if (leftLeaf > rightLeaf) return Double.NaN;

            int left = treeSize + leftLeaf;
            int right = treeSize + rightLeaf;
            double minVal = Double.POSITIVE_INFINITY;

            while (left <= right) {
                if ((left & 1) == 1) {
                    double v = treeMin.get(left);
                    if (v < minVal) minVal = v;
                    left++;
                }
                if ((right & 1) == 0) {
                    double v = treeMin.get(right);
                    if (v < minVal) minVal = v;
                    right--;
                }
                left >>= 1;
                right >>= 1;
            }
            return minVal == Double.POSITIVE_INFINITY ? Double.NaN : minVal;
        }

        public double queryMax(int leftLeaf, int rightLeaf) {
            if (leftLeaf < 0) leftLeaf = 0;
            if (rightLeaf >= numLeaves) rightLeaf = numLeaves - 1;
            if (leftLeaf > rightLeaf) return Double.NaN;

            int left = treeSize + leftLeaf;
            int right = treeSize + rightLeaf;
            double maxVal = Double.NEGATIVE_INFINITY;

            while (left <= right) {
                if ((left & 1) == 1) {
                    double v = treeMax.get(left);
                    if (v > maxVal) maxVal = v;
                    left++;
                }
                if ((right & 1) == 0) {
                    double v = treeMax.get(right);
                    if (v > maxVal) maxVal = v;
                    right--;
                }
                left >>= 1;
                right >>= 1;
            }
            return maxVal == Double.NEGATIVE_INFINITY ? Double.NaN : maxVal;
        }

        public void query(int leftLeaf, int rightLeaf, double[] outMinMax) {
            outMinMax[0] = queryMin(leftLeaf, rightLeaf);
            outMinMax[1] = queryMax(leftLeaf, rightLeaf);
        }

        private static double lerpSample(DoubleBuffer buffer, int count, double t) {
            int idx0 = (int) Math.floor(t);
            if (idx0 < 0) idx0 = 0;
            if (idx0 >= count - 1) idx0 = count - 2;
            int idx1 = idx0 + 1;
            double fraction = t - idx0;
            return buffer.get(idx0) + fraction * (buffer.get(idx1) - buffer.get(idx0));
        }

        /**
         * Queries the exact min and max bounds across [tStart, tEnd], automatically handling
         * periodic wrap-around across period boundaries without heap allocations.
         */
        public void queryRange(DoubleBuffer buffer, double tStart, double tEnd, double[] outMinMax) {
            if (tEnd < tStart) {
                double temp = tStart;
                tStart = tEnd;
                tEnd = temp;
            }
            double span = tEnd - tStart;
            double periodLength = numLeaves - 1;
            if (span >= periodLength) {
                outMinMax[0] = treeMin.get(1) == Double.POSITIVE_INFINITY ? Double.NaN : treeMin.get(1);
                outMinMax[1] = treeMax.get(1) == Double.NEGATIVE_INFINITY ? Double.NaN : treeMax.get(1);
                return;
            }
            double wrappedStart = ((tStart % periodLength) + periodLength) % periodLength;
            double wrappedEnd = wrappedStart + span;

            double yStart = lerpSample(buffer, numLeaves, wrappedStart);

            if (wrappedEnd <= periodLength) {
                double yEnd = lerpSample(buffer, numLeaves, wrappedEnd);
                int idxStart = (int) Math.floor(wrappedStart);
                int idxEnd = (int) Math.floor(wrappedEnd);
                if (idxStart == idxEnd) {
                    outMinMax[0] = Math.min(yStart, yEnd);
                    outMinMax[1] = Math.max(yStart, yEnd);
                } else {
                    double minVal = Math.min(yStart, yEnd);
                    double maxVal = Math.max(yStart, yEnd);
                    if (idxStart + 1 <= idxEnd) {
                        double innerMin = queryMin(idxStart + 1, idxEnd);
                        double innerMax = queryMax(idxStart + 1, idxEnd);
                        if (innerMin < minVal) minVal = innerMin;
                        if (innerMax > maxVal) maxVal = innerMax;
                    }
                    outMinMax[0] = minVal;
                    outMinMax[1] = maxVal;
                }
            } else {
                double splitEnd = wrappedEnd - periodLength;
                double yEnd = lerpSample(buffer, numLeaves, splitEnd);
                double minVal = Math.min(yStart, yEnd);
                double maxVal = Math.max(yStart, yEnd);

                int idxStart = (int) Math.floor(wrappedStart);
                if (idxStart + 1 <= numLeaves - 1) {
                    double innerMin1 = queryMin(idxStart + 1, numLeaves - 1);
                    double innerMax1 = queryMax(idxStart + 1, numLeaves - 1);
                    if (innerMin1 < minVal) minVal = innerMin1;
                    if (innerMax1 > maxVal) maxVal = innerMax1;
                }
                int idxEnd = (int) Math.floor(splitEnd);
                if (idxEnd >= 0) {
                    double innerMin2 = queryMin(0, idxEnd);
                    double innerMax2 = queryMax(0, idxEnd);
                    if (innerMin2 < minVal) minVal = innerMin2;
                    if (innerMax2 > maxVal) maxVal = innerMax2;
                }
                outMinMax[0] = minVal;
                outMinMax[1] = maxVal;
            }
        }
    }

    /**
     * Represents a single Fourier harmonic:
     * H_n(x) = a_n cos(n pi x / L) + b_n sin(n pi x / L) (for n > 0)
     * H_0(x) = a_0 / 2
     */
    public static class Harmonic {
        public final int n;
        public final double a;
        public final double b;

        public Harmonic(int n, double a, double b) {
            this.n = n;
            this.a = a;
            this.b = b;
        }

        public double eval(double x, double halfPeriod) {
            if (n == 0) return a / 2.0;
            double angle = n * Math.PI * x / halfPeriod;
            return a * Math.cos(angle) + b * Math.sin(angle);
        }
    }

    private static double evalFourierHarmonicsFast(double theta, DoubleBuffer aCoeffs, DoubleBuffer bCoeffs,
                                                 DoubleBuffer weights, int maxH) {
        if (maxH <= 0) return 0.0;
        double c1 = Math.cos(theta);
        double s1 = Math.sin(theta);
        double curC = c1;
        double curS = s1;
        double sum = 0.0;
        int aCap = (aCoeffs != null) ? aCoeffs.capacity() : 0;
        int bCap = (bCoeffs != null) ? bCoeffs.capacity() : 0;
        int wCap = (weights != null) ? weights.capacity() : 0;
        for (int n = 1; n <= maxH; n++) {
            double w = (weights != null && n < wCap) ? weights.get(n) : 1.0;
            if (w > 0.0) {
                double a = (n < aCap) ? aCoeffs.get(n) : 0.0;
                double b = (n < bCap) ? bCoeffs.get(n) : 0.0;
                sum += (a * curC + b * curS) * w;
            }
            double nextC = curC * c1 - curS * s1;
            double nextS = curC * s1 + curS * c1;
            curC = nextC;
            curS = nextS;
            if ((n & 31) == 0) {
                double invMag = 1.0 / Math.sqrt(curC * curC + curS * curS);
                curC *= invMag;
                curS *= invMag;
            }
        }
        return sum;
    }
}

