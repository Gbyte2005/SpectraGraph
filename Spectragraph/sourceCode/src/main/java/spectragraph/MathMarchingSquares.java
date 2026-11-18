package com.spectragraph;

import java.awt.geom.Path2D;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;

/**
 * High-performance 2D Marching Squares contour generator for implicit curves F(x, y) = 0.
 */
public final class MathMarchingSquares {

    // Thread-local direct off-heap buffer to store grid evaluation samples without heap allocations
    private static final ThreadLocal<DoubleBuffer> DIRECT_GRID_BUFFER = new ThreadLocal<>();

    private MathMarchingSquares() {
        // Utility class
    }

    /**
     * Obtains a thread-local direct off-heap buffer with at least the requested capacity.
     * Reuses the existing direct buffer if sufficiently large, or expands it cleanly.
     *
     * @param requiredDoubles number of doubles required for the grid
     * @return a direct off-heap {@link DoubleBuffer} ready for writing
     */
    private static DoubleBuffer getDirectGridBuffer(int requiredDoubles) {
        DoubleBuffer buffer = DIRECT_GRID_BUFFER.get();
        if (buffer == null || buffer.capacity() < requiredDoubles) {
            int newCapacity = Math.max(requiredDoubles, buffer == null ? 4096 : buffer.capacity() * 2);
            buffer = ByteBuffer.allocateDirect(newCapacity * Double.BYTES)
                               .order(ByteOrder.nativeOrder())
                               .asDoubleBuffer();
            DIRECT_GRID_BUFFER.set(buffer);
        }
        buffer.clear();
        return buffer;
    }

    /**
     * Evaluates the implicit function F(x, y) across a regular 2D screen grid and constructs
     * the vector contour path representing the zero level-set F(x, y) = 0.
     *
     * @param function the parsed mathematical bivariate expression
     * @param minX     minimum mathematical x coordinate in view
     * @param maxX     maximum mathematical x coordinate in view
     * @param minY     minimum mathematical y coordinate in view
     * @param maxY     maximum mathematical y coordinate in view
     * @param width    pixel width of the viewport
     * @param height   pixel height of the viewport
     * @param cellSize sampling cell resolution in pixels (smaller = higher detail)
     * @return a {@link Path2D} containing the contour segments, or null if computation was cancelled
     */
    public static Path2D render(MathParser.Expression function, double minX, double maxX,
                                double minY, double maxY, int width, int height, int cellSize) {
        if (function == null || width <= 0 || height <= 0
                || !Double.isFinite(minX) || !Double.isFinite(maxX)
                || !Double.isFinite(minY) || !Double.isFinite(maxY)) {
            return new Path2D.Double();
        }

        int step = Math.max(2, cellSize);
        int columnCount = (width + step - 1) / step;
        int rowCount = (height + step - 1) / step;

        int gridWidth = columnCount + 1;
        int gridHeight = rowCount + 1;
        int totalGridCells = gridWidth * gridHeight;

        // Obtain zero-allocation direct off-heap buffer for evaluation grid
        DoubleBuffer grid = getDirectGridBuffer(totalGridCells);

        double deltaX = (maxX - minX) / width;
        double deltaY = (maxY - minY) / height;

        // Phase 1: Sample function values at all grid intersection vertices
        for (int col = 0; col <= columnCount; col++) {
            // Check for worker thread cancellation periodically
            if ((col & 7) == 0 && Thread.currentThread().isInterrupted()) {
                return null;
            }
            double pixelX = col * step;
            double mathX = minX + pixelX * deltaX;

            for (int row = 0; row <= rowCount; row++) {
                double pixelY = row * step;
                double mathY = maxY - pixelY * deltaY;
                double value;
                try {
                    value = function.eval(mathX, mathY);
                } catch (Exception evaluationException) {
                    value = Double.NaN;
                }
                // Store in 1D flattened off-heap native buffer: index = col * gridHeight + row
                grid.put(col * gridHeight + row, value);
            }
        }

        Path2D contourPath = new Path2D.Double();
        double[] lastPt = new double[] { Double.NaN, Double.NaN };

        // Phase 2: March through all cells and interpolate zero-crossing segments
        for (int col = 0; col < columnCount; col++) {
            double cellX0 = col * step;
            double cellX1 = Math.min(width, (col + 1) * step);

            for (int row = 0; row < rowCount; row++) {
                double cellY0 = row * step;
                double cellY1 = Math.min(height, (row + 1) * step);

                // Fetch values of the four cell corners:
                // v0 = top-left, v1 = top-right, v2 = bottom-right, v3 = bottom-left
                double v0 = grid.get(col * gridHeight + row);
                double v1 = grid.get((col + 1) * gridHeight + row);
                double v2 = grid.get((col + 1) * gridHeight + (row + 1));
                double v3 = grid.get(col * gridHeight + (row + 1));

                // If any corner is undefined or singular (NaN / Infinite), skip cell
                if (!Double.isFinite(v0) || !Double.isFinite(v1) || !Double.isFinite(v2) || !Double.isFinite(v3)) {
                    continue;
                }

                // Determine 4-bit configuration index (1 = positive, 0 = non-positive)
                int config = 0;
                if (v0 > 0) config |= 8;
                if (v1 > 0) config |= 4;
                if (v2 > 0) config |= 2;
                if (v3 > 0) config |= 1;

                // Trivial configurations: all corners positive (15) or all negative (0)
                if (config == 0 || config == 15) {
                    continue;
                }

                // Calculate edge crossing coordinates via linear interpolation
                double pointTopX = cellX0 + (cellX1 - cellX0) * interpolateCrossing(v0, v1);
                double pointTopY = cellY0;

                double pointRightX = cellX1;
                double pointRightY = cellY0 + (cellY1 - cellY0) * interpolateCrossing(v1, v2);

                double pointBottomX = cellX0 + (cellX1 - cellX0) * interpolateCrossing(v3, v2);
                double pointBottomY = cellY1;

                double pointLeftX = cellX0;
                double pointLeftY = cellY0 + (cellY1 - cellY0) * interpolateCrossing(v0, v3);

                switch (config) {
                    case 1:
                    case 14:
                        appendSegment(contourPath, pointLeftX, pointLeftY, pointBottomX, pointBottomY, lastPt);
                        break;
                    case 2:
                    case 13:
                        appendSegment(contourPath, pointBottomX, pointBottomY, pointRightX, pointRightY, lastPt);
                        break;
                    case 3:
                    case 12:
                        appendSegment(contourPath, pointLeftX, pointLeftY, pointRightX, pointRightY, lastPt);
                        break;
                    case 4:
                    case 11:
                        appendSegment(contourPath, pointTopX, pointTopY, pointRightX, pointRightY, lastPt);
                        break;
                    case 5: {
                        // Saddle point ambiguity: Top-Right (v1) and Bottom-Left (v3) are positive.
                        // Disambiguate topology by sampling the center of the cell.
                        double midPixelX = (cellX0 + cellX1) * 0.5;
                        double midPixelY = (cellY0 + cellY1) * 0.5;
                        double midMathX = minX + midPixelX * deltaX;
                        double midMathY = maxY - midPixelY * deltaY;
                        double centerValue = (v0 + v1 + v2 + v3) * 0.25;
                        try {
                            double evaluatedCenter = function.eval(midMathX, midMathY);
                            if (Double.isFinite(evaluatedCenter)) centerValue = evaluatedCenter;
                        } catch (Exception ignored) {}

                        if (centerValue > 0) {
                            // Positive corners connect across center
                            appendSegment(contourPath, pointTopX, pointTopY, pointLeftX, pointLeftY, lastPt);
                            appendSegment(contourPath, pointBottomX, pointBottomY, pointRightX, pointRightY, lastPt);
                        } else {
                            // Negative corners connect across center
                            appendSegment(contourPath, pointTopX, pointTopY, pointRightX, pointRightY, lastPt);
                            appendSegment(contourPath, pointLeftX, pointLeftY, pointBottomX, pointBottomY, lastPt);
                        }
                        break;
                    }
                    case 6:
                    case 9:
                        appendSegment(contourPath, pointTopX, pointTopY, pointBottomX, pointBottomY, lastPt);
                        break;
                    case 7:
                    case 8:
                        appendSegment(contourPath, pointLeftX, pointLeftY, pointTopX, pointTopY, lastPt);
                        break;
                    case 10: {
                        // Saddle point ambiguity: Top-Left (v0) and Bottom-Right (v2) are positive.
                        double midPixelX = (cellX0 + cellX1) * 0.5;
                        double midPixelY = (cellY0 + cellY1) * 0.5;
                        double midMathX = minX + midPixelX * deltaX;
                        double midMathY = maxY - midPixelY * deltaY;
                        double centerValue = (v0 + v1 + v2 + v3) * 0.25;
                        try {
                            double evaluatedCenter = function.eval(midMathX, midMathY);
                            if (Double.isFinite(evaluatedCenter)) centerValue = evaluatedCenter;
                        } catch (Exception ignored) {}

                        if (centerValue > 0) {
                            // Positive corners connect across center
                            appendSegment(contourPath, pointTopX, pointTopY, pointRightX, pointRightY, lastPt);
                            appendSegment(contourPath, pointLeftX, pointLeftY, pointBottomX, pointBottomY, lastPt);
                        } else {
                            // Negative corners connect across center
                            appendSegment(contourPath, pointTopX, pointTopY, pointLeftX, pointLeftY, lastPt);
                            appendSegment(contourPath, pointBottomX, pointBottomY, pointRightX, pointRightY, lastPt);
                        }
                        break;
                    }
                }
            }
        }

        return contourPath;
    }

    /**
     * Linearly interpolates the exact parameter t in [0, 1] where the function crosses zero
     * between valueA and valueB.
     */
    private static double interpolateCrossing(double valueA, double valueB) {
        double delta = valueB - valueA;
        if (Math.abs(delta) < 1e-12) return 0.5;
        double t = -valueA / delta;
        return Math.max(0.0, Math.min(1.0, t));
    }

    /**
     * Appends a line segment to the contour vector path, chaining contiguous segments into polylines.
     */
    private static void appendSegment(Path2D path, double startX, double startY, double endX, double endY, double[] lastPt) {
        if (!Double.isNaN(lastPt[0]) && Math.abs(lastPt[0] - startX) < 1e-4 && Math.abs(lastPt[1] - startY) < 1e-4) {
            path.lineTo(endX, endY);
        } else {
            path.moveTo(startX, startY);
            path.lineTo(endX, endY);
        }
        lastPt[0] = endX;
        lastPt[1] = endY;
    }
}
