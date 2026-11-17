package com.spectragraph;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.Objects;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import com.spectragraph.superscript.SubscriptsSuperscript;
import com.spectragraph.superscript.ExponentsSuperscript;
import com.spectragraph.superscript.MathInputHandler;
import com.spectragraph.superscript.MathRenderEngine;

public class SpectraGraphApp extends JFrame {

    public static class PanelDragHandler extends MouseAdapter {
        private final JComponent target;
        private Point grabOffset;

        public PanelDragHandler(JComponent target) {
            this.target = target;
        }

        @Override
        public void mousePressed(MouseEvent e) {
            if (SwingUtilities.isLeftMouseButton(e)) {
                grabOffset = e.getPoint();
            }
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            grabOffset = null;
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if (grabOffset == null || !SwingUtilities.isLeftMouseButton(e)) {
                return;
            }
            Point oldPos = target.getLocation();
            int nx = oldPos.x + e.getX() - grabOffset.x;
            int ny = oldPos.y + e.getY() - grabOffset.y;

            Container parent = target.getParent();
            if (parent != null) {
                int maxX = Math.max(0, parent.getWidth() - target.getWidth());
                int maxY = Math.max(0, parent.getHeight() - target.getHeight());
                nx = Math.max(0, Math.min(maxX, nx));
                ny = Math.max(0, Math.min(maxY, ny));
            }
            if (nx == oldPos.x && ny == oldPos.y) {
                return;
            }

            target.setLocation(nx, ny);
            if (parent instanceof JComponent) {
                ((JComponent) parent).paintImmediately(oldPos.x, oldPos.y,
                        target.getWidth(), target.getHeight());
            } else if (parent != null) {
                parent.repaint(oldPos.x, oldPos.y,
                        target.getWidth(), target.getHeight());
            }
        }
    }

    public enum SignalType {
        CONTINUOUS_APERIODIC("Cont", "Continuous Aperiodic Signal (f(t) / f(x))"),
        CONTINUOUS_PERIODIC("Per", "Continuous Periodic Signal (repeats with period 2L)"),
        DISCRETE_APERIODIC("Disc", "Discrete Aperiodic Signal (x[n] stems)"),
        DISCRETE_PERIODIC("Disc-Per", "Discrete Periodic Signal (x[n] periodic stems)"),
        DISCRETE_RANDOM("Rand", "Discrete Stochastic Signal (w[n] random stems)");

        public final String shortLabel;
        public final String fullName;

        SignalType(String shortLabel, String fullName) {
            this.shortLabel = shortLabel;
            this.fullName = fullName;
        }
    }

    public class FunctionEntry {
        enum DisplayMode {
            NORMAL, SPECTRUM_AMP_PHASE, SPECTRUM_POWER
        }

        MathTextField textField;
        SignalEngine engine;
        Timer debounceTimer;
        Future<?> evalFuture;
        Future<?> panRefineFuture;
        long panRefineVersion = 0;
        String currentStr = "";
        long evalVersion = 0;
        boolean isValid = false;
        boolean isDiscretized = false;
        boolean isImplicit = false;
        MathParser.Expression expr;
        DisplayMode displayMode = DisplayMode.NORMAL;
        JPanel rowPanel;
        JScrollPane scrollPane;
        JLabel lblFx;
        SignalType signalType = SignalType.CONTINUOUS_APERIODIC;
        boolean userExplicitType = false;
        public java.util.List<Double> featureX = new java.util.ArrayList<>();

        java.awt.geom.Path2D cachedSignalPath = null;
        java.awt.geom.Path2D cachedDisplayPath = null;
        java.awt.geom.Path2D cachedBandPath = null;
        java.awt.geom.Path2D cachedAccPath = null;
        int cachedAccHarmonic = -1;
        double cachedAccViewMinX = Double.NaN;
        double cachedAccViewMaxX = Double.NaN;
        double cachedAccViewMinY = Double.NaN;
        double cachedAccViewMaxY = Double.NaN;
        int cachedAccW = -1;
        int cachedAccH = -1;
        double cachedViewMinX = Double.NaN;
        double cachedViewMaxX = Double.NaN;
        double cachedViewMinY = Double.NaN;
        double cachedViewMaxY = Double.NaN;
        double cachedOriginMinX = Double.NaN;
        double cachedOriginMaxX = Double.NaN;
        double cachedOriginMinY = Double.NaN;
        double cachedOriginMaxY = Double.NaN;
        double cachedEvalMinX = Double.NaN;
        double cachedEvalMaxX = Double.NaN;
        int cachedW = -1;
        int cachedH = -1;
        int cachedPrecisionDepth = -1;
        MathParser.Expression cachedExpr = null;
    }

    public final List<FunctionEntry> functionEntries = new ArrayList<>();
    private final Set<FunctionEntry> visibleFunctions = new HashSet<>();
    private FunctionEntry activeFunction = null;

    private final GraphPanel graphPanel;
    private final JPanel bottomBar;
    private final JPanel functionsListPanel;
    private final JPanel rightSidebarPanel;
    private final JScrollPane rightScrollPane;
    private final JPanel sidebar;
    private final JLayeredPane centerLayeredPane;
    private final JPanel leftResizer;
    private final JPanel rightResizer;
    private int sidebarWidth = 320;
    private int rightSidebarWidth = 250;

    private final Color COLOR_BG = new Color(20, 22, 26);
    private final Color COLOR_SIDEBAR = new Color(33, 37, 43);
    private final Color COLOR_FG = new Color(200, 204, 212);
    private final Color COLOR_GRID = new Color(50, 56, 64);
    private final Color COLOR_SIGNAL = new Color(86, 182, 194);
    private final Color COLOR_ACCUMULATED = new Color(152, 195, 121);
    private final Color COLOR_SELECTED_FIELD = new Color(229, 192, 123);

    private final Color[] COLORS_HARMONIC = {
            new Color(224, 108, 117),
            new Color(209, 154, 102),
            new Color(229, 192, 123),
            new Color(198, 120, 221),
            new Color(97, 175, 239)
    };

    private double minX = -Math.PI;
    private double maxX = Math.PI;
    private double viewMinX = -Math.PI;
    private double viewMaxX = Math.PI;
    private double viewMinY = -2.0;
    private double viewMaxY = 2.0;
    private boolean lockIsometric = true;
    private double graphScale = 100.0;
    private JPanel graphToolbar;
    private JButton btn1to1;
    private JButton btnFitView;
    private JButton btnResetView;
    private Integer selectedHarmonic = null;
    private MathTextField periodField;
    private JScrollPane periodScrollPane;
    private final ExecutorService executor = Executors
            .newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors()));
    private CalculatorPanel calculatorPanel;
    private ShortcutsPanel shortcutsPanel;

    public SpectraGraphApp() {
        super("SpectraGraph - Fourier Series Visualizer");

        setLayout(new BorderLayout());
        getContentPane().setBackground(COLOR_BG);

        centerLayeredPane = new JLayeredPane() {
            @Override
            public void doLayout() {
                super.doLayout();
                int w = getWidth();
                int h = getHeight();
                graphPanel.setBounds(0, 0, w, h);
                sidebar.setBounds(0, 0, sidebarWidth, h);
                leftResizer.setBounds(sidebarWidth - 4, 0, 8, h);
                if (rightScrollPane.isVisible()) {
                    rightScrollPane.setBounds(w - rightSidebarWidth, 0, rightSidebarWidth, h);
                    rightResizer.setBounds(w - rightSidebarWidth - 4, 0, 8, h);
                    rightResizer.setVisible(true);
                } else {
                    rightResizer.setVisible(false);
                }
                if (graphToolbar != null) {
                    int rightBound = rightScrollPane.isVisible() ? w - rightSidebarWidth : w;
                    Dimension pref = graphToolbar.getPreferredSize();
                    int tbW = Math.max(pref.width + 6, 230);
                    int tbH = 34;
                    graphToolbar.setBounds(rightBound - tbW - 16, 12, tbW, tbH);
                }
            }
        };

        graphPanel = new GraphPanel();
        centerLayeredPane.add(graphPanel, JLayeredPane.DEFAULT_LAYER);

        graphToolbar = createGraphToolbar();
        centerLayeredPane.add(graphToolbar, JLayeredPane.PALETTE_LAYER);

        sidebar = new JPanel();
        sidebar.setLayout(new BorderLayout());
        sidebar.setBackground(COLOR_SIDEBAR);
        sidebar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, COLOR_GRID),
                BorderFactory.createEmptyBorder(20, 20, 20, 20)));
        centerLayeredPane.add(sidebar, JLayeredPane.PALETTE_LAYER);

        leftResizer = new JPanel();
        leftResizer.setOpaque(false);
        leftResizer.setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
        MouseAdapter leftDrag = new MouseAdapter() {
            private int startX;
            private int startW;

            @Override
            public void mousePressed(MouseEvent e) {
                startX = e.getXOnScreen();
                startW = sidebarWidth;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                int delta = e.getXOnScreen() - startX;
                int maxW = centerLayeredPane.getWidth() - (rightScrollPane.isVisible() ? rightSidebarWidth + 100 : 200);
                sidebarWidth = Math.max(260, Math.min(startW + delta, maxW));
                centerLayeredPane.revalidate();
                centerLayeredPane.doLayout();
                centerLayeredPane.repaint();
                functionsListPanel.revalidate();
                functionsListPanel.repaint();
            }
        };
        leftResizer.addMouseListener(leftDrag);
        leftResizer.addMouseMotionListener(leftDrag);
        centerLayeredPane.add(leftResizer, JLayeredPane.DRAG_LAYER);

        rightResizer = new JPanel();
        rightResizer.setOpaque(false);
        rightResizer.setCursor(Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR));
        MouseAdapter rightDrag = new MouseAdapter() {
            private int startX;
            private int startW;

            @Override
            public void mousePressed(MouseEvent e) {
                startX = e.getXOnScreen();
                startW = rightSidebarWidth;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                int delta = startX - e.getXOnScreen();
                int maxW = centerLayeredPane.getWidth() - sidebarWidth - 100;
                rightSidebarWidth = Math.max(200, Math.min(startW + delta, maxW));
                centerLayeredPane.revalidate();
                centerLayeredPane.doLayout();
                centerLayeredPane.repaint();
                rightScrollPane.revalidate();
                rightScrollPane.doLayout();
                rightSidebarPanel.revalidate();
                rightSidebarPanel.repaint();
            }
        };
        rightResizer.addMouseListener(rightDrag);
        rightResizer.addMouseMotionListener(rightDrag);
        centerLayeredPane.add(rightResizer, JLayeredPane.DRAG_LAYER);

        JLabel lblTitle = new JLabel("Functions");
        lblTitle.setForeground(COLOR_FG);
        lblTitle.setFont(new Font("Consolas", Font.BOLD, 18));

        HamburgerMenuButton hamburgerBtn = new HamburgerMenuButton();

        JPanel titlePanel = new JPanel(new BorderLayout());
        titlePanel.setBackground(COLOR_SIDEBAR);
        titlePanel.add(lblTitle, BorderLayout.WEST);
        titlePanel.add(hamburgerBtn, BorderLayout.EAST);
        titlePanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 15, 0));
        sidebar.add(titlePanel, BorderLayout.NORTH);

        functionsListPanel = new JPanel() {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                if (getParent() != null && getParent().getParent() instanceof JScrollPane) {
                    JScrollPane sp = (JScrollPane) getParent().getParent();
                    d.width = Math.max(d.width, sp.getViewport().getWidth());
                }
                return d;
            }
        };
        functionsListPanel.setLayout(new BoxLayout(functionsListPanel, BoxLayout.Y_AXIS));
        functionsListPanel.setBackground(COLOR_SIDEBAR);

        JScrollPane sidebarScroll = new JScrollPane(functionsListPanel);
        sidebarScroll.setBorder(null);
        sidebarScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        sidebarScroll.getVerticalScrollBar().setUnitIncrement(16);
        sidebarScroll.setBackground(COLOR_SIDEBAR);
        sidebarScroll.getViewport().setBackground(COLOR_SIDEBAR);
        sidebarScroll.getVerticalScrollBar().setUI(new CustomScrollBarUI());
        sidebarScroll.getVerticalScrollBar().setPreferredSize(new Dimension(6, 0));

        sidebar.add(sidebarScroll, BorderLayout.CENTER);

        JPanel bottomSidebarPanel = new JPanel(new BorderLayout(0, 15));
        bottomSidebarPanel.setBackground(COLOR_SIDEBAR);
        bottomSidebarPanel.setBorder(BorderFactory.createEmptyBorder(15, 0, 0, 0));

        JPanel periodPanel = new JPanel(new BorderLayout(10, 0));
        periodPanel.setBackground(COLOR_SIDEBAR);

        JLabel periodLabel = new JLabel("Analysis Period (2L):");
        periodLabel.setForeground(COLOR_FG);
        periodLabel.setFont(new Font("Consolas", Font.PLAIN, 14));
        JPanel lblWrapper = new JPanel(new java.awt.GridBagLayout());
        lblWrapper.setOpaque(false);
        lblWrapper.add(periodLabel);
        periodPanel.add(lblWrapper, BorderLayout.WEST);

        periodField = new MathTextField("2*pi");
        periodField.setForeground(Color.WHITE);
        periodField.setCaretColor(Color.WHITE);
        periodField.setBackground(COLOR_SIDEBAR);
        periodField.setOpaque(false);
        periodField.setFont(new Font("Consolas", Font.PLAIN, 14));
        periodField.setBorder(BorderFactory.createEmptyBorder(3, 5, 3, 5));

        periodScrollPane = new JScrollPane(periodField) {
            @Override
            public Dimension getPreferredSize() {
                Dimension viewPref = getViewport().getView().getPreferredSize();
                boolean sbVis = getHorizontalScrollBar().isVisible();
                int sbHeight = sbVis ? getHorizontalScrollBar().getPreferredSize().height : 0;
                return new Dimension(Math.max(50, viewPref.width + 10), Math.max(34, viewPref.height + sbHeight + 4));
            }
        };
        periodField.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                periodScrollPane.revalidate();
                bottomSidebarPanel.revalidate();
            }
        });
        periodScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        periodScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        periodScrollPane.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(COLOR_GRID, 1, true),
                BorderFactory.createEmptyBorder(1, 1, 1, 1)));
        periodScrollPane.setOpaque(false);
        periodScrollPane.getViewport().setOpaque(false);
        periodScrollPane.getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
        periodScrollPane.getHorizontalScrollBar().setUnitIncrement(16);
        periodScrollPane.getHorizontalScrollBar().setUI(new CustomScrollBarUI());
        periodScrollPane.getHorizontalScrollBar().setPreferredSize(new Dimension(0, 6));

        periodField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                periodScrollPane.setBorder(BorderFactory.createLineBorder(COLOR_SELECTED_FIELD, 2, true));
            }

            @Override
            public void focusLost(FocusEvent e) {
                periodScrollPane.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(COLOR_GRID, 1, true),
                        BorderFactory.createEmptyBorder(1, 1, 1, 1)));

                String current = periodField.getText();
                if (current != null && !current.trim().isEmpty()) {
                    String normalized = MathParser.normalizeForField(current);
                    if (!current.equals(normalized)) {
                        periodField.setText(normalized);
                    }
                }
            }
        });

        periodPanel.add(periodScrollPane, BorderLayout.CENTER);

        Timer periodDebounceTimer = new Timer(150, e -> updatePeriod());
        periodDebounceTimer.setRepeats(false);

        periodField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                periodDebounceTimer.restart();
            }

            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                periodDebounceTimer.restart();
            }

            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                periodDebounceTimer.restart();
            }
        });
        bottomSidebarPanel.add(periodPanel, BorderLayout.NORTH);

        JButton btnNewFunc = new JButton("+ New Function");
        btnNewFunc.setBackground(new Color(97, 175, 239));
        btnNewFunc.setForeground(Color.WHITE);
        btnNewFunc.setFont(new Font("Consolas", Font.BOLD, 14));
        btnNewFunc.setFocusPainted(false);
        btnNewFunc.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnNewFunc.setPreferredSize(new Dimension(0, 40));
        btnNewFunc.addActionListener(e -> addNewFunction(""));
        bottomSidebarPanel.add(btnNewFunc, BorderLayout.SOUTH);

        sidebar.add(bottomSidebarPanel, BorderLayout.SOUTH);

        bottomBar = new JPanel();
        bottomBar.setLayout(new FlowLayout(FlowLayout.LEFT, 15, 15));
        bottomBar.setBackground(COLOR_SIDEBAR);

        JScrollPane scrollBottom = new JScrollPane(bottomBar);
        scrollBottom.setPreferredSize(new Dimension(0, 120));
        scrollBottom.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, COLOR_GRID));
        scrollBottom.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollBottom.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        scrollBottom.getHorizontalScrollBar().setUnitIncrement(16);
        scrollBottom.getHorizontalScrollBar().setUI(new CustomScrollBarUI());
        scrollBottom.getHorizontalScrollBar().setPreferredSize(new Dimension(0, 6));

        rightSidebarPanel = new ScrollableSidebarPanel();
        rightSidebarPanel.setLayout(new BoxLayout(rightSidebarPanel, BoxLayout.Y_AXIS));
        rightSidebarPanel.setBackground(COLOR_BG);
        rightSidebarPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        rightScrollPane = new JScrollPane(rightSidebarPanel);
        rightScrollPane.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, COLOR_GRID));
        rightScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        rightScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        rightScrollPane.getVerticalScrollBar().setUI(new CustomScrollBarUI());
        rightScrollPane.getVerticalScrollBar().setPreferredSize(new Dimension(6, 0));
        rightScrollPane.setBackground(COLOR_BG);
        rightScrollPane.getViewport().setBackground(COLOR_BG);
        rightScrollPane.setVisible(false);

        centerLayeredPane.add(rightScrollPane, JLayeredPane.PALETTE_LAYER);

        add(centerLayeredPane, BorderLayout.CENTER);
        add(scrollBottom, BorderLayout.SOUTH);

        calculatorPanel = new CalculatorPanel();
        calculatorPanel.setBounds(340, 50, 360, 380);
        calculatorPanel.setVisible(false);
        getLayeredPane().add(calculatorPanel, JLayeredPane.PALETTE_LAYER);

        shortcutsPanel = new ShortcutsPanel();
        shortcutsPanel.setVisible(false);
        getLayeredPane().add(shortcutsPanel, JLayeredPane.PALETTE_LAYER);

        Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (event.getID() == MouseEvent.MOUSE_PRESSED) {
                MouseEvent me = (MouseEvent) event;
                Component source = me.getComponent();
                if (calculatorPanel.isVisible() && source != null
                        && !SwingUtilities.isDescendingFrom(source, calculatorPanel) &&
                        (activeFunction == null || source != activeFunction.textField)) {
                    if (!(source instanceof JMenuItem) && !(source instanceof JPopupMenu)) {
                        calculatorPanel.setVisible(false);
                    }
                }
                if (shortcutsPanel.isVisible() && source != null
                        && !SwingUtilities.isDescendingFrom(source, shortcutsPanel)) {
                    if (!(source instanceof JMenuItem) && !(source instanceof JPopupMenu)) {
                        shortcutsPanel.setVisible(false);
                    }
                }
            }
        }, AWTEvent.MOUSE_EVENT_MASK);

        setTitle("SpectraGraph");
        try {
            java.io.InputStream iconStream = getClass().getResourceAsStream("/icon.png");
            if (iconStream != null) {
                setIconImage(javax.imageio.ImageIO.read(iconStream));
            }
        } catch (Exception ignored) {}

        setSize(1300, 850);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        addNewFunction("sign(sin(x))");
    }

    private void updatePeriod() {
        try {
            MathParser.Expression expr = MathParser.parse(periodField.getText(), java.util.Collections.emptyMap());
            double period = expr.eval(0);
            if (period > 0) {
                MathParser.setDefaultPeriod(period);
                minX = -period / 2.0;
                maxX = period / 2.0;
                for (FunctionEntry entry : visibleFunctions) {
                    plotFunction(entry);
                }
            }
        } catch (Exception ex) {
        }
    }

    public static SignalType detectSignalType(String text) {
        if (text == null || text.trim().isEmpty()) {
            return SignalType.CONTINUOUS_APERIODIC;
        }
        String s = text.toLowerCase();
        if (s.contains("rand(") || s.contains("randn(") || s.contains("noise(") ||
                s.contains("rand[") || s.contains("randn[") || s.contains("noise[")) {
            return SignalType.DISCRETE_RANDOM;
        }
        boolean isDiscrete = s.matches(".*\\b[nk]\\b.*") || s.contains("[n]") || s.contains("[k]") || s.contains("[m]")
                || s.contains("*[n]") || s.contains("*[k]") || s.contains("*[m]");

        if (s.contains("rep(") || s.contains("per(") || s.contains("drcc(") ||
                s.contains("rep[") || s.contains("per[") || s.contains("drcc[")) {
            return isDiscrete ? SignalType.DISCRETE_PERIODIC : SignalType.CONTINUOUS_PERIODIC;
        }

        if (isDiscrete) {
            if (s.contains("sqw(") || s.contains("saw(") || s.contains("sqw[") || s.contains("saw[")) {
                return SignalType.DISCRETE_PERIODIC;
            }
            return SignalType.DISCRETE_APERIODIC;
        }

        boolean hasAperiodicPulse = s.matches(".*\\b(rect|tri|sinc|drcd|sign|exp|sinh|cosh|step)\\b.*")
                || s.matches(".*\\b[xt]\\s*\\*\\s*(?:sin|cos).*")
                || s.matches(".*\\)\\s*\\*\\s*[xt]\\b.*");
        boolean hasPeriodic = s.contains("sin(") || s.contains("cos(") || s.contains("tan(")
                || s.contains("saw(") || s.contains("triangle(") || s.contains("sqw(") || s.contains("square(");

        if (hasPeriodic && !hasAperiodicPulse) {
            return SignalType.CONTINUOUS_PERIODIC;
        }
        return SignalType.CONTINUOUS_APERIODIC;
    }

    private void updateSignalTypeUI(FunctionEntry entry) {
        SignalType st = entry.signalType;
        if (entry.lblFx != null) {
            if (st == SignalType.DISCRETE_APERIODIC || st == SignalType.DISCRETE_PERIODIC
                    || st == SignalType.DISCRETE_RANDOM || entry.isDiscretized) {
                entry.lblFx.setText("x[n]");
            } else {
                String text = entry.textField != null ? entry.textField.getText().toLowerCase() : "";
                if (text.contains("t") && !text.contains("x")) {
                    entry.lblFx.setText("f(t)");
                } else {
                    entry.lblFx.setText("f(x)");
                }
            }
        }
    }

    private static class MenuCheckIcon implements Icon {
        private final boolean checked;

        public MenuCheckIcon(boolean checked) {
            this.checked = checked;
        }

        @Override
        public int getIconWidth() {
            return 16;
        }

        @Override
        public int getIconHeight() {
            return 16;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            if (!checked)
                return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.setColor(new Color(86, 182, 194));
            g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int[] px = { x + 3, x + 7, x + 13 };
            int[] py = { y + 8, y + 12, y + 4 };
            g2.drawPolyline(px, py, 3);
            g2.dispose();
        }
    }

    private void styleMenuItem(JMenuItem item) {
        item.setBackground(COLOR_SIDEBAR);
        item.setForeground(COLOR_FG);
        item.setFont(new Font("Consolas", Font.PLAIN, 14));
        item.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 12));
        item.setIconTextGap(8);
        item.setUI(new javax.swing.plaf.basic.BasicMenuItemUI() {
            @Override
            protected void paintBackground(Graphics g, JMenuItem menuItem, Color bgColor) {
                ButtonModel model = menuItem.getModel();
                g.setColor(model.isArmed() ? new Color(50, 56, 66) : COLOR_SIDEBAR);
                g.fillRect(0, 0, menuItem.getWidth(), menuItem.getHeight());
            }

            @Override
            protected void paintText(Graphics g, JMenuItem menuItem, Rectangle textRect, String text) {
                Graphics2D g2 = (Graphics2D) g;
                java.util.Map<?, ?> hints = (java.util.Map<?, ?>) Toolkit.getDefaultToolkit()
                        .getDesktopProperty("awt.font.desktophints");
                if (hints != null)
                    g2.addRenderingHints(hints);
                else
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setFont(menuItem.getFont());
                g2.setColor(menuItem.getForeground());
                FontMetrics fm = g2.getFontMetrics();
                int y = textRect.y + fm.getAscent();
                g2.drawString(text, textRect.x, y);
            }
        });
    }

    private void styleMenu(JMenu menu) {
        menu.setBackground(COLOR_SIDEBAR);
        menu.setForeground(COLOR_FG);
        menu.setFont(new Font("Consolas", Font.PLAIN, 14));
        menu.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        menu.setUI(new javax.swing.plaf.basic.BasicMenuUI() {
            @Override
            protected void paintBackground(Graphics g, JMenuItem menuItem, Color bgColor) {
                ButtonModel model = menuItem.getModel();
                g.setColor(model.isSelected() ? COLOR_GRID : COLOR_SIDEBAR);
                g.fillRect(0, 0, menuItem.getWidth(), menuItem.getHeight());
            }

            @Override
            protected void paintText(Graphics g, JMenuItem menuItem, Rectangle textRect, String text) {
                Graphics2D g2 = (Graphics2D) g;
                java.util.Map<?, ?> hints = (java.util.Map<?, ?>) Toolkit.getDefaultToolkit()
                        .getDesktopProperty("awt.font.desktophints");
                if (hints != null)
                    g2.addRenderingHints(hints);
                else
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setFont(menuItem.getFont());
                g2.setColor(COLOR_FG);
                FontMetrics fm = g2.getFontMetrics();
                int y = textRect.y + fm.getAscent();
                g2.drawString(text, textRect.x, y);
            }
        });
    }

    private void styleNavBtn(JButton b) {
        b.setFocusable(false);
        b.setBackground(new Color(45, 50, 60));
        b.setForeground(COLOR_FG);
        b.setFont(new Font("Consolas", Font.BOLD, 12));
        b.setFocusPainted(false);
        b.setMargin(new Insets(1, 6, 1, 6));
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
    }

    private JButton createCloseButton(Runnable action) {
        JButton btn = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

                boolean hover = getModel().isRollover();
                if (hover) {
                    g2.setColor(new Color(60, 42, 48));
                    g2.fillRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 4, 4);
                    g2.setColor(new Color(255, 120, 130));
                } else {
                    g2.setColor(new Color(224, 108, 117));
                }

                g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int pad = 6;
                g2.drawLine(pad, pad, getWidth() - pad, getHeight() - pad);
                g2.drawLine(getWidth() - pad, pad, pad, getHeight() - pad);
                g2.dispose();
            }
        };
        btn.setPreferredSize(new Dimension(24, 24));
        btn.setMinimumSize(new Dimension(24, 24));
        btn.setMaximumSize(new Dimension(24, 24));
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setToolTipText("Close");
        if (action != null)
            btn.addActionListener(e -> action.run());
        return btn;
    }

    private JButton createArrowButton(boolean isLeft, Runnable action) {
        JButton btn = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

                boolean hover = getModel().isRollover();
                if (hover) {
                    g2.setColor(new Color(50, 56, 68));
                    g2.fillRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 4, 4);
                    g2.setColor(Color.WHITE);
                } else {
                    g2.setColor(COLOR_FG);
                }

                int w = getWidth();
                int h = getHeight();
                int cx = w / 2;
                int cy = h / 2;

                Path2D path = new Path2D.Float();
                if (isLeft) {
                    path.moveTo(cx + 2, cy - 5);
                    path.lineTo(cx - 3, cy);
                    path.lineTo(cx + 2, cy + 5);
                } else {
                    path.moveTo(cx - 2, cy - 5);
                    path.lineTo(cx + 3, cy);
                    path.lineTo(cx - 2, cy + 5);
                }
                g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(path);
                g2.dispose();
            }
        };
        btn.setPreferredSize(new Dimension(24, 24));
        btn.setMinimumSize(new Dimension(24, 24));
        btn.setMaximumSize(new Dimension(24, 24));
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setToolTipText(isLeft ? "Previous section" : "Next section");
        if (action != null)
            btn.addActionListener(e -> action.run());
        return btn;
    }

    private void addNewFunction(String defaultText) {
        FunctionEntry entry = new FunctionEntry();
        entry.engine = new SignalEngine(1500, 30);
        entry.currentStr = defaultText;

        JPanel row = new JPanel(new BorderLayout(8, 0)) {
            @Override
            public Dimension getMaximumSize() {
                Dimension pref = getPreferredSize();
                return new Dimension(Integer.MAX_VALUE, pref.height);
            }
        };
        row.setBackground(COLOR_SIDEBAR);
        row.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 10));
        entry.rowPanel = row;

        JLabel lblFx = new JLabel("f(x)");
        lblFx.setFont(new Font("Serif", Font.ITALIC, 18));
        lblFx.setForeground(COLOR_FG);
        lblFx.setBorder(BorderFactory.createEmptyBorder(0, 5, 4, 2));
        lblFx.setCursor(new Cursor(Cursor.HAND_CURSOR));
        lblFx.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isControlDown()) {
                    if (visibleFunctions.contains(entry)) {
                        visibleFunctions.remove(entry);
                        if (activeFunction == entry) {
                            activeFunction = visibleFunctions.isEmpty() ? null : visibleFunctions.iterator().next();
                        }
                    } else {
                        visibleFunctions.add(entry);
                        activeFunction = entry;
                    }
                    updateSelectionUI();
                } else {
                    if (visibleFunctions.size() != 1 || !visibleFunctions.contains(entry) || activeFunction != entry) {
                        visibleFunctions.clear();
                        visibleFunctions.add(entry);
                        activeFunction = entry;
                        updateSelectionUI();
                    }
                }
            }
        });

        entry.textField = new MathTextField(defaultText);
        entry.textField.setForeground(Color.WHITE);
        entry.textField.setCaretColor(Color.WHITE);
        entry.textField.setBackground(COLOR_SIDEBAR);
        entry.textField.setOpaque(true);
        entry.textField.setFont(new Font("Consolas", Font.PLAIN, 14));
        entry.textField.setBorder(BorderFactory.createEmptyBorder(3, 5, 3, 5));

        entry.textField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                String current = entry.textField.getText();
                if (current != null && !current.trim().isEmpty()) {
                    String normalized = MathParser.normalizeForField(current);
                    if (!current.equals(normalized)) {
                        entry.textField.setText(normalized);
                    }
                }
            }
        });

        entry.textField.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    showCalculatorMenu(entry, e);
                    return;
                }
                if (e.isControlDown()) {
                    if (visibleFunctions.contains(entry)) {
                        visibleFunctions.remove(entry);
                        if (activeFunction == entry) {
                            activeFunction = visibleFunctions.isEmpty() ? null : visibleFunctions.iterator().next();
                        }
                    } else {
                        visibleFunctions.add(entry);
                        activeFunction = entry;
                    }
                    updateSelectionUI();
                } else {
                    if (visibleFunctions.size() != 1 || !visibleFunctions.contains(entry) || activeFunction != entry) {
                        visibleFunctions.clear();
                        visibleFunctions.add(entry);
                        activeFunction = entry;
                        updateSelectionUI();
                    }
                }
            }
        });

        entry.debounceTimer = new Timer(120, e -> plotFunction(entry));
        entry.debounceTimer.setRepeats(false);

        entry.textField.addActionListener(e -> {
            entry.debounceTimer.stop();
            plotFunction(entry);
        });

        entry.textField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                handleTyping(e.getLength() > 1);
            }

            public void removeUpdate(DocumentEvent e) {
                handleTyping(false);
            }

            public void changedUpdate(DocumentEvent e) {
                handleTyping(false);
            }

            private void handleTyping(boolean isPaste) {
                if (!visibleFunctions.contains(entry) || visibleFunctions.size() > 1) {
                    visibleFunctions.clear();
                    visibleFunctions.add(entry);
                    activeFunction = entry;
                    updateSelectionUI();
                }
                entry.debounceTimer.setInitialDelay(isPaste ? 30 : 120);
                entry.debounceTimer.restart();
            }
        });

        entry.lblFx = lblFx;
        entry.signalType = detectSignalType(defaultText);
        updateSignalTypeUI(entry);

        JPanel lblWrapper = new JPanel(new java.awt.GridBagLayout());
        lblWrapper.setOpaque(false);
        lblWrapper.add(lblFx);
        row.add(lblWrapper, BorderLayout.WEST);

        javax.swing.JScrollPane scrollPane = new javax.swing.JScrollPane(entry.textField) {
            @Override
            public java.awt.Dimension getPreferredSize() {
                java.awt.Dimension viewPref = getViewport().getView().getPreferredSize();
                boolean sbVis = getHorizontalScrollBar().isVisible();
                int sbHeight = sbVis ? getHorizontalScrollBar().getPreferredSize().height : 0;
                return new java.awt.Dimension(50, viewPref.height + sbHeight);
            }
        };
        scrollPane.setHorizontalScrollBarPolicy(javax.swing.JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setVerticalScrollBarPolicy(javax.swing.JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getViewport().setScrollMode(javax.swing.JViewport.SIMPLE_SCROLL_MODE);
        entry.scrollPane = scrollPane;
        scrollPane.getHorizontalScrollBar().setUnitIncrement(16);
        scrollPane.getHorizontalScrollBar().setUI(new CustomScrollBarUI());
        scrollPane.getHorizontalScrollBar().setPreferredSize(new java.awt.Dimension(0, 6));

        row.add(scrollPane, BorderLayout.CENTER);

        JButton btnRemove = new JButton("X");
        btnRemove.setPreferredSize(new Dimension(28, 28));
        btnRemove.setMinimumSize(new Dimension(28, 28));
        btnRemove.setMaximumSize(new Dimension(28, 28));
        btnRemove.setBackground(new Color(224, 108, 117));
        btnRemove.setForeground(Color.WHITE);
        btnRemove.setFont(new Font("Consolas", Font.BOLD, 12));
        btnRemove.setFocusPainted(false);
        btnRemove.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnRemove.setMargin(new Insets(2, 6, 2, 6));
        btnRemove.addActionListener(e -> {
            functionEntries.remove(entry);
            visibleFunctions.remove(entry);
            functionsListPanel.remove(row);
            if (activeFunction == entry) {
                activeFunction = visibleFunctions.isEmpty() ? null : visibleFunctions.iterator().next();
            }
            updateSelectionUI();
            functionsListPanel.revalidate();
            functionsListPanel.repaint();
        });
        JPanel removeWrapper = new JPanel(new java.awt.GridBagLayout());
        removeWrapper.setOpaque(false);
        removeWrapper.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
        removeWrapper.add(btnRemove);
        row.add(removeWrapper, BorderLayout.EAST);

        functionEntries.add(entry);
        functionsListPanel.add(row);
        functionsListPanel.revalidate();

        visibleFunctions.clear();
        visibleFunctions.add(entry);
        activeFunction = entry;
        updateSelectionUI();

        if (!defaultText.isEmpty()) {
            plotFunction(entry);
        }
    }

    private int findBestHarmonic(FunctionEntry entry) {
        if (entry == null || entry.engine == null)
            return 1;
        int maxH = entry.engine.getMaxHarmonics();
        int best = 1;
        double maxAmp = -1;
        for (int i = 1; i <= maxH; i++) {
            SignalEngine.Harmonic h = entry.engine.getHarmonic(i);
            double amp = Math.hypot(h.a, h.b);
            if (amp > maxAmp && amp > 1e-6) {
                maxAmp = amp;
                best = i;
            }
        }
        return best;
    }

    private void updateSelectionUI() {
        for (FunctionEntry f : functionEntries) {
            boolean sel = visibleFunctions.contains(f);
            f.textField.setSelected(sel);
            if (sel) {
                f.scrollPane.setBorder(BorderFactory.createLineBorder(COLOR_SELECTED_FIELD, 2, true));
            } else {
                f.scrollPane.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
            }
        }
        if (selectedHarmonic == null && activeFunction != null && activeFunction.engine != null) {
            selectedHarmonic = findBestHarmonic(activeFunction);
        }
        buildBottomBar();
        updateRightSidebar();
        graphPanel.repaint();
    }

    private boolean isAutogeneratedDDText(String text) {
        if (text == null)
            return false;
        String s = text.trim();
        int eqIdx = MathParser.findTopLevelEquals(s);
        if (eqIdx != -1) {
            s = s.substring(eqIdx + 1).trim();
        }
        return s.matches(
                ".*\\(d(?:\\^(?:\\(?\\d+\\)?|\\{\\d+\\}))?\\)/\\(d(?:\\^(?:\\(?\\d+\\)?|\\{\\d+\\}))?\\)(\\(\\))?$")
                || s.equals("(d)/(d)") || s.endsWith("(d)/(d)") || s.contains("(d)/(d)");
    }

    void plotFunction(FunctionEntry entry) {
        String funcStr = entry.textField.getText();
        if (funcStr.trim().isEmpty()) {
            if (entry.evalFuture != null && !entry.evalFuture.isDone()) {
                entry.evalFuture.cancel(true);
            }
            entry.isValid = false;
            entry.expr = null;
            entry.cachedSignalPath = null;
            entry.cachedDisplayPath = null;
            entry.cachedBandPath = null;
            entry.featureX = null;
            entry.currentStr = "";
            if (entry.engine == null)
                entry.engine = new SignalEngine(1500, 30);
            else
                entry.engine.clear();
            entry.textField.setForeground(Color.WHITE);
            if (activeFunction == entry) {
                buildBottomBar();
            }
            updateRightSidebar();
            graphPanel.repaint();
            return;
        }

        String clean = funcStr.trim();
        if (entry.textField.isAutogeneratedDD() && isAutogeneratedDDText(clean)) {
            if (entry.evalFuture != null && !entry.evalFuture.isDone()) {
                entry.evalFuture.cancel(true);
            }
            entry.isValid = false;
            entry.expr = null;
            entry.cachedSignalPath = null;
            entry.cachedDisplayPath = null;
            entry.cachedBandPath = null;
            entry.featureX = null;
            entry.currentStr = "";
            if (entry.engine == null)
                entry.engine = new SignalEngine(1500, 30);
            else
                entry.engine.clear();
            entry.textField.setForeground(Color.WHITE);
            if (activeFunction == entry) {
                buildBottomBar();
            }
            updateRightSidebar();
            graphPanel.repaint();
            return;
        }

        if (!entry.userExplicitType) {
            entry.signalType = detectSignalType(funcStr);
            updateSignalTypeUI(entry);
        }

        final String origFuncStr = funcStr;
        String normalized = MathParser.normalizeForField(funcStr);
        if (!normalized.equals(funcStr)) {
            final String normFinal = normalized;
            SwingUtilities.invokeLater(() -> {
                if (!entry.textField.hasFocus()) {
                    int pos = entry.textField.getCaretPosition();
                    int diff = normFinal.length() - origFuncStr.length();
                    entry.textField.setText(normFinal);
                    entry.textField.setCaretPosition(Math.max(0, Math.min(normFinal.length(), pos + diff)));
                }
            });
            funcStr = normalized;
        }

        final String finalFuncStr = funcStr;
        final long currentVersion = ++entry.evalVersion;
        if (entry.evalFuture != null && !entry.evalFuture.isDone()) {
            entry.evalFuture.cancel(true);
        }
        entry.evalFuture = executor.submit(() -> {
            Thread.interrupted();
            try {
                if (Thread.currentThread().isInterrupted())
                    return;
                java.util.Map<String, MathParser.Expression> env = new java.util.HashMap<>();
                for (FunctionEntry fe : functionEntries) {
                    if (Thread.currentThread().isInterrupted())
                        return;
                    if (fe == entry)
                        continue;
                    String text = fe.textField.getText().trim();
                    int eqIdx = MathParser.findTopLevelEquals(text);
                    if (eqIdx != -1) {
                        String lhs = text.substring(0, eqIdx).trim();
                        String funcName = lhs.replaceAll("\\(.*\\)", "").trim();
                        try {
                            env.put(funcName, MathParser.parse(text, env));
                        } catch (Exception ignore) {
                        }
                    }
                }
                if (Thread.currentThread().isInterrupted())
                    return;
                MathParser.Expression parsedExpr = MathParser.parse(finalFuncStr, env);
                if (Thread.currentThread().isInterrupted())
                    return;
                boolean implicit = MathParser.isImplicit(finalFuncStr);
                if (implicit) {
                    entry.engine.evaluateImplicitFunction(parsedExpr, minX, maxX, viewMinY, viewMaxY);
                } else {
                    entry.engine.evaluateFunction(parsedExpr, minX, maxX, finalFuncStr);
                }
                if (Thread.currentThread().isInterrupted())
                    return;

                List<Double> feats = new ArrayList<>();
                feats.add(0.0);
                feats.add(minX);
                feats.add(maxX);
                for (SignalEngine.Impulse imp : entry.engine.getImpulses()) {
                    feats.add(imp.x);
                }
                java.nio.DoubleBuffer sig = entry.engine.getDisplaySignal();
                java.nio.DoubleBuffer xv = entry.engine.getXValues();
                int numS = entry.engine.getNumSamples();
                if (sig != null && xv != null && numS > 2) {
                    for (int i = 1; i < numS - 1; i++) {
                        double y0 = sig.get(i - 1);
                        double y1 = sig.get(i);
                        double y2 = sig.get(i + 1);
                        if (Double.isFinite(y0) != Double.isFinite(y1) || Double.isFinite(y1) != Double.isFinite(y2)) {
                            feats.add(xv.get(i));
                        } else if (Double.isFinite(y0) && Double.isFinite(y1) && Double.isFinite(y2)) {
                            if ((y1 > y0 && y1 >= y2) || (y1 < y0 && y1 <= y2)) {
                                feats.add(xv.get(i));
                            }
                        }
                    }
                }
                java.util.Collections.sort(feats);
                List<Double> uniqueFeats = new ArrayList<>();
                for (double fx : feats) {
                    if (uniqueFeats.isEmpty() || Math.abs(fx - uniqueFeats.get(uniqueFeats.size() - 1)) > 1e-6) {
                        uniqueFeats.add(fx);
                    }
                }

                double globalMin = Double.MAX_VALUE;
                double globalMax = -Double.MAX_VALUE;
                double globalMinX = Double.MAX_VALUE;
                double globalMaxX = -Double.MAX_VALUE;
                for (FunctionEntry fe : visibleFunctions) {
                    SignalEngine eng = (fe == entry) ? entry.engine : fe.engine;
                    boolean isVal = (fe == entry) ? true : fe.isValid;
                    if (isVal && eng != null) {
                        if (fe.isImplicit && eng.hasImplicitBounds()) {
                            if (eng.getImplicitMinY() < globalMin)
                                globalMin = eng.getImplicitMinY();
                            if (eng.getImplicitMaxY() > globalMax)
                                globalMax = eng.getImplicitMaxY();
                            if (eng.getImplicitMinX() < globalMinX)
                                globalMinX = eng.getImplicitMinX();
                            if (eng.getImplicitMaxX() > globalMaxX)
                                globalMaxX = eng.getImplicitMaxX();
                        } else {
                            java.util.List<Double> validVals = new java.util.ArrayList<>();
                            for (int i = 0; i < eng.getNumSamples(); i++) {
                                double val = eng.getDisplaySignal().get(i);
                                if (Double.isFinite(val)) {
                                    validVals.add(val);
                                    double sx = eng.getXValues().get(i);
                                    if (sx < globalMinX)
                                        globalMinX = sx;
                                    if (sx > globalMaxX)
                                        globalMaxX = sx;
                                }
                            }
                            if (!validVals.isEmpty()) {
                                java.util.Collections.sort(validVals);
                                int n = validVals.size();
                                double lowP = validVals.get((int) (n * 0.01));
                                double highP = validVals.get((int) (n * 0.99));
                                if (Math.abs(highP) > 1e4 || Math.abs(lowP) > 1e4) {
                                    lowP = validVals.get((int) (n * 0.05));
                                    highP = validVals.get((int) (n * 0.95));
                                    if (Math.abs(highP) > 1e4 || Math.abs(lowP) > 1e4) {
                                        lowP = validVals.get((int) (n * 0.10));
                                        highP = validVals.get((int) (n * 0.90));
                                    }
                                }
                                lowP = Math.max(-1e4, Math.min(1e4, lowP));
                                highP = Math.max(-1e4, Math.min(1e4, highP));
                                if (lowP < globalMin)
                                    globalMin = lowP;
                                if (highP > globalMax)
                                    globalMax = highP;
                            }
                            if (!eng.getImpulses().isEmpty()) {
                                for (SignalEngine.Impulse imp : eng.getImpulses()) {
                                    if (imp.weight > 0 && imp.weight > globalMax)
                                        globalMax = imp.weight;
                                    if (imp.weight < 0 && imp.weight < globalMin)
                                        globalMin = imp.weight;
                                    if (0.0 < globalMin)
                                        globalMin = 0.0;
                                    if (0.0 > globalMax)
                                        globalMax = 0.0;
                                    if (imp.x < globalMinX)
                                        globalMinX = imp.x;
                                    if (imp.x > globalMaxX)
                                        globalMaxX = imp.x;
                                }
                            }
                        }
                    }
                }

                double targetViewMinY;
                double targetViewMaxY;
                if (globalMin != Double.MAX_VALUE && globalMax != -Double.MAX_VALUE
                        && (globalMax - globalMin) > 1e-6) {
                    double range = globalMax - globalMin;
                    if (range > 2e4) {
                        double mid = (globalMax + globalMin) / 2.0;
                        globalMin = mid - 1e4;
                        globalMax = mid + 1e4;
                        range = 2e4;
                    }
                    double margin = range * 0.2;
                    targetViewMinY = globalMin - margin;
                    targetViewMaxY = globalMax + margin;
                } else {
                    targetViewMinY = -2.0;
                    targetViewMaxY = 2.0;
                }

                double targetViewMinX = viewMinX;
                double targetViewMaxX = viewMaxX;
                int pw = Math.max(800, graphPanel.getWidth());
                int ph = Math.max(600, graphPanel.getHeight());

                if (lockIsometric) {
                    double spanY = targetViewMaxY - targetViewMinY;
                    if (spanY <= 0 || Double.isNaN(spanY))
                        spanY = 4.0;
                    double scale = ph / spanY;
                    if (scale < 1e-4)
                        scale = 1e-4;
                    if (scale > 1e7)
                        scale = 1e7;
                    graphScale = scale;
                    double cx = 0.0;
                    if (globalMinX != Double.MAX_VALUE && Math.abs(globalMinX) < 1e4
                            && Math.abs(globalMaxX) < 1e4) {
                        cx = (globalMinX + globalMaxX) / 2.0;
                    } else {
                        cx = (minX + maxX) / 2.0;
                    }
                    targetViewMinX = cx - (pw / 2.0) / scale;
                    targetViewMaxX = cx + (pw / 2.0) / scale;
                } else {
                    if (targetViewMinX < -500 || targetViewMaxX > 500) {
                        targetViewMinX = minX;
                        targetViewMaxX = maxX;
                    }
                }

                boolean isPeriodic = (entry.signalType == SignalType.CONTINUOUS_PERIODIC);
                double period = maxX - minX;
                GraphPanel.PathData pd = null;
                if (!implicit) {
                    pd = graphPanel.buildContinuousSignalPath(parsedExpr, isPeriodic, minX, period,
                            targetViewMinX, targetViewMaxX, targetViewMinY, targetViewMaxY, pw, ph, uniqueFeats,
                            () -> entry.evalVersion != currentVersion || Thread.currentThread().isInterrupted());
                }
                final GraphPanel.PathData finalPd = pd;
                final double fTargetMinX = targetViewMinX;
                final double fTargetMaxX = targetViewMaxX;
                final double fTargetMinY = targetViewMinY;
                final double fTargetMaxY = targetViewMaxY;

                if (Thread.currentThread().isInterrupted())
                    return;
                entry.engine.computeFourierSeries(minX, maxX);
                int bestH = findBestHarmonic(entry);
                int hToCompute = (selectedHarmonic != null) ? selectedHarmonic : bestH;
                entry.engine.computeAccumulatedSignal(hToCompute, minX, maxX);
                if (Thread.currentThread().isInterrupted())
                    return;

                SwingUtilities.invokeLater(() -> {
                    if (entry.evalVersion != currentVersion) {
                        return;
                    }
                    entry.featureX = uniqueFeats;
                    entry.expr = parsedExpr;
                    if (finalPd != null) {
                        entry.cachedSignalPath = finalPd.signalPath;
                        entry.cachedBandPath = finalPd.bandPath;
                        entry.cachedDisplayPath = finalPd.displayPath;
                        entry.cachedExpr = parsedExpr;
                        entry.cachedViewMinX = finalPd.evalMinX;
                        entry.cachedViewMaxX = finalPd.evalMaxX;
                        entry.cachedViewMinY = finalPd.evalMinY;
                        entry.cachedViewMaxY = finalPd.evalMaxY;
                        entry.cachedOriginMinX = finalPd.originMinX;
                        entry.cachedOriginMaxX = finalPd.originMaxX;
                        entry.cachedOriginMinY = finalPd.originMinY;
                        entry.cachedOriginMaxY = finalPd.originMaxY;
                        entry.cachedEvalMinX = finalPd.evalMinX;
                        entry.cachedEvalMaxX = finalPd.evalMaxX;
                        entry.cachedW = finalPd.originW;
                        entry.cachedH = finalPd.originH;
                    } else {
                        entry.cachedSignalPath = null;
                        entry.cachedDisplayPath = null;
                        entry.cachedBandPath = null;
                    }
                    entry.cachedAccPath = null;
                    entry.isImplicit = implicit;
                    entry.currentStr = finalFuncStr;
                    entry.isValid = true;
                    entry.textField.setForeground(Color.WHITE);

                    viewMinX = fTargetMinX;
                    viewMaxX = fTargetMaxX;
                    viewMinY = fTargetMinY;
                    viewMaxY = fTargetMaxY;
                    if (lockIsometric) {
                        graphPanel.enforceIsometric(graphPanel.getWidth(), graphPanel.getHeight());
                    }

                    if (selectedHarmonic == null) {
                        selectedHarmonic = bestH;
                    }
                    buildBottomBar();
                    updateRightSidebar();
                    graphPanel.repaint();
                });
            } catch (Exception ex) {
                if (Thread.currentThread().isInterrupted())
                    return;
                SwingUtilities.invokeLater(() -> {
                    if (entry.evalVersion != currentVersion) {
                        return;
                    }
                    entry.isValid = false;
                    boolean incomplete = isIncompleteExpression(finalFuncStr, ex);
                    entry.textField.setForeground(incomplete ? Color.WHITE : new Color(255, 100, 100));
                    if (activeFunction == entry) {
                        buildBottomBar();
                    }
                    updateRightSidebar();
                    graphPanel.repaint();
                });
            }
        });
    }

    private boolean isIncompleteExpression(String text, Throwable ex) {
        if (text == null || text.trim().isEmpty())
            return true;
        String t = text.trim();
        if (ex instanceof MathParser.IncompleteExpressionException
                || (ex != null && ex.getCause() instanceof MathParser.IncompleteExpressionException)) {
            return true;
        }
        if (ex != null && ex.getMessage() != null) {
            String msg = ex.getMessage().toLowerCase();
            if (msg.contains("incomplete") || msg.contains("cannot have an empty argument")
                    || msg.contains("empty fraction") || msg.contains("unexpected end of input")
                    || msg.contains("derivative operator must be followed")) {
                return true;
            }
        }
        if (t.endsWith("(") || t.endsWith("[") || t.endsWith("{") || t.endsWith("+") || t.endsWith("-")
                || t.endsWith("*") || t.endsWith("/") || t.endsWith("^") || t.endsWith(",") || t.endsWith("_")) {
            return true;
        }
        if (t.contains("()") || t.contains("[]") || t.contains("{}") || t.matches(".*(?i)[d\u2202]/[d\u2202][a-zA-Z]?$")
                || t.matches(".*(?i)\\([d\u2202]\\)/\\([d\u2202][a-zA-Z]?\\)$") || t.endsWith("dx")
                || t.endsWith("dt")) {
            return true;
        }
        int openP = 0, openB = 0, openC = 0;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '(')
                openP++;
            else if (c == ')')
                openP--;
            else if (c == '[')
                openB++;
            else if (c == ']')
                openB--;
            else if (c == '{')
                openC++;
            else if (c == '}')
                openC--;
        }
        if (openP > 0 || openB > 0 || openC > 0)
            return true;

        if (MathParser.isFunctionRequiringParens(t) || t.equalsIgnoreCase("log") || t.equalsIgnoreCase("ln")
                || t.equalsIgnoreCase("sum") || t.equals("∑") || t.equals("∫")) {
            return true;
        }
        return false;
    }

    private void updateRightSidebar() {
        rightSidebarPanel.removeAll();
        if (selectedHarmonic == null || visibleFunctions.isEmpty()) {
            rightScrollPane.setVisible(false);
            if (centerLayeredPane != null) {
                centerLayeredPane.revalidate();
                centerLayeredPane.repaint();
            }
            revalidate();
            repaint();
            return;
        }

        rightScrollPane.setVisible(true);
        for (FunctionEntry entry : visibleFunctions) {
            if (entry.isValid && entry.engine != null) {
                VignettePanel vp = new VignettePanel(entry, selectedHarmonic);
                vp.setAlignmentX(Component.CENTER_ALIGNMENT);
                rightSidebarPanel.add(vp);
                Component spacer = Box.createRigidArea(new Dimension(0, 10));
                ((JComponent) spacer).setAlignmentX(Component.CENTER_ALIGNMENT);
                rightSidebarPanel.add(spacer);
            }
        }
        rightSidebarPanel.revalidate();
        rightSidebarPanel.repaint();
        if (centerLayeredPane != null) {
            centerLayeredPane.revalidate();
            centerLayeredPane.doLayout();
            centerLayeredPane.repaint();
        }
        revalidate();
        repaint();
    }

    public static String formatHUDCoord(double v) {
        if (Double.isNaN(v))
            return "NaN";
        if (Double.isInfinite(v))
            return v > 0 ? "+Inf" : "-Inf";
        double abs = Math.abs(v);
        if (abs == 0.0)
            return "+0.0000";
        if (abs >= 1e4 || abs < 1e-3) {
            return String.format(java.util.Locale.US, "%+.3e", v);
        }
        return String.format(java.util.Locale.US, "%+.4f", v);
    }

    public static String formatAmp(double amp) {
        double abs = Math.abs(amp);
        if (abs == 0.0)
            return "0.00";
        if (abs >= 1e4)
            return String.format(java.util.Locale.US, "%.2e", amp);
        if (abs >= 100)
            return String.format(java.util.Locale.US, "%.1f", amp);
        if (abs >= 0.01)
            return String.format(java.util.Locale.US, "%.2f", amp);
        if (abs >= 0.001)
            return String.format(java.util.Locale.US, "%.3f", amp);
        if (abs >= 0.0001)
            return String.format(java.util.Locale.US, "%.4f", amp);
        return String.format(java.util.Locale.US, "%.2e", amp);
    }

    private void buildBottomBar() {
        bottomBar.removeAll();
        if (visibleFunctions.isEmpty()) {
            bottomBar.revalidate();
            bottomBar.repaint();
            return;
        }

        int maxHarmonics = 0;
        for (FunctionEntry entry : visibleFunctions) {
            if (entry.isValid && entry.engine != null) {
                maxHarmonics = Math.max(maxHarmonics, entry.engine.getMaxHarmonics());
            }
        }

        double globalPeakAmp = 0.0;
        for (FunctionEntry entry : visibleFunctions) {
            if (entry.isValid && entry.engine != null) {
                for (int i = 0; i <= maxHarmonics; i++) {
                    SignalEngine.Harmonic h = entry.engine.getHarmonic(i);
                    globalPeakAmp = Math.max(globalPeakAmp, Math.hypot(h.a, h.b));
                }
            }
        }

        double threshold = Math.max(1e-12, Math.min(0.005, globalPeakAmp * 0.005));

        for (int i = 0; i <= maxHarmonics; i++) {
            boolean hasSignificant = false;
            double maxAmp = 0;

            for (FunctionEntry entry : visibleFunctions) {
                if (entry.isValid && entry.engine != null) {
                    SignalEngine.Harmonic h = entry.engine.getHarmonic(i);
                    double amp = Math.hypot(h.a, h.b);
                    if (amp >= threshold) {
                        hasSignificant = true;
                        maxAmp = Math.max(maxAmp, amp);
                    }
                }
            }

            if (!hasSignificant)
                continue;

            Color componentColor = COLORS_HARMONIC[i % COLORS_HARMONIC.length];

            final String btnLabelText = String.format("Freq %d", i);
            final String btnSubText = String.format("%s: %s", visibleFunctions.size() > 1 ? "Max" : "Amp",
                    formatAmp(maxAmp));

            JButton btn = new JButton() {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Graphics2D g2 = (Graphics2D) g.create();
                    MathRenderEngine.applyRenderingHints(g2);
                    g2.setFont(getFont());
                    g2.setColor(getForeground());
                    FontMetrics fm = g2.getFontMetrics();
                    int w1 = fm.stringWidth(btnLabelText);
                    int w2 = MathTextField.renderMathString(g2, btnSubText, 0, 0, getFont(), false);
                    g2.drawString(btnLabelText, (getWidth() - w1) / 2, 22);
                    MathTextField.renderMathString(g2, btnSubText, (getWidth() - w2) / 2, 42, getFont(), true);
                    g2.dispose();
                }
            };
            boolean isSel = Objects.equals(selectedHarmonic, i);
            btn.setBackground(isSel ? new Color(55, 62, 75) : new Color(40, 44, 52));
            btn.setForeground(componentColor);
            btn.setFont(new Font("Consolas", Font.BOLD, 12));
            btn.setFocusPainted(false);
            btn.setPreferredSize(new Dimension(100, 60));
            btn.setBorder(
                    BorderFactory.createLineBorder(isSel ? componentColor : new Color(60, 64, 73), isSel ? 3 : 2));
            btn.setCursor(new Cursor(Cursor.HAND_CURSOR));

            final int harmonicIndex = i;
            btn.addActionListener(e -> {
                if (Objects.equals(selectedHarmonic, harmonicIndex)) {
                    selectedHarmonic = null;
                } else {
                    selectedHarmonic = harmonicIndex;
                }
                buildBottomBar();
                updateRightSidebar();
                graphPanel.repaint();
            });
            bottomBar.add(btn);
        }
        bottomBar.revalidate();
        bottomBar.repaint();
    }

    private static class VectorIcon implements Icon {
        enum Type {
            ZOOM_IN, FIT, RESET
        }

        private final Type type;
        private final int size;
        private final Color color;

        public VectorIcon(Type type, int size, Color color) {
            this.type = type;
            this.size = size;
            this.color = color;
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.setColor(color);

            switch (type) {
                case ZOOM_IN: {
                    g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.drawOval(x + 1, y + 1, 8, 8);
                    g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.drawLine(x + 3, y + 5, x + 7, y + 5);
                    g2.drawLine(x + 5, y + 3, x + 5, y + 7);
                    g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.drawLine(x + 8, y + 8, x + 12, y + 12);
                    break;
                }
                case FIT: {
                    g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    int d = size - 2;
                    g2.drawLine(x + 1, y + 4, x + 1, y + 1);
                    g2.drawLine(x + 1, y + 1, x + 4, y + 1);
                    g2.drawLine(x + d - 3, y + 1, x + d, y + 1);
                    g2.drawLine(x + d, y + 1, x + d, y + 4);
                    g2.drawLine(x + 1, y + d - 3, x + 1, y + d);
                    g2.drawLine(x + 1, y + d, x + 4, y + d);
                    g2.drawLine(x + d - 3, y + d, x + d, y + d);
                    g2.drawLine(x + d, y + d - 3, x + d, y + d);
                    g2.setStroke(new BasicStroke(1.2f));
                    g2.drawRect(x + 4, y + 4, 4, 4);
                    break;
                }
                case RESET: {
                    g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.drawArc(x + 2, y + 2, size - 4, size - 4, 45, 270);
                    int ax = x + size - 2;
                    int ay = y + 2;
                    int[] px = { ax - 4, ax + 1, ax - 1 };
                    int[] py = { ay - 1, ay, ay + 4 };
                    g2.fillPolygon(px, py, 3);
                    break;
                }
            }
            g2.dispose();
        }
    }

    private JPanel createGraphToolbar() {
        JPanel tb = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 2)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(24, 27, 34, 230));
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                g2.setColor(new Color(55, 62, 72));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        tb.setOpaque(false);

        btn1to1 = new JButton("1:1", new VectorIcon(VectorIcon.Type.ZOOM_IN, 14, new Color(152, 195, 121)));
        styleToolbarButton(btn1to1);
        btn1to1.setForeground(new Color(152, 195, 121));
        btn1to1.setToolTipText("Zoom in (1:1 isometric aspect ratio)");
        btn1to1.addActionListener(e -> zoomIn1to1());

        btnFitView = new JButton("Fit", new VectorIcon(VectorIcon.Type.FIT, 14, new Color(97, 175, 239)));
        styleToolbarButton(btnFitView);
        btnFitView.setForeground(new Color(97, 175, 239));
        btnFitView.setToolTipText("Fit view to curves / Zoom out (1:1 aspect ratio)");
        btnFitView.addActionListener(e -> fitToVisibleCurves());

        btnResetView = new JButton("Reset", new VectorIcon(VectorIcon.Type.RESET, 14, new Color(229, 192, 123)));
        styleToolbarButton(btnResetView);
        btnResetView.setForeground(new Color(229, 192, 123));
        btnResetView.setToolTipText("Reset all states and view to default");
        btnResetView.addActionListener(e -> resetAllStates());

        tb.add(btn1to1);
        tb.add(btnFitView);
        tb.add(btnResetView);
        return tb;
    }

    private void styleToolbarButton(JButton btn) {
        btn.setFont(new Font("Consolas", Font.BOLD, 12));
        btn.setBackground(new Color(36, 41, 50));
        btn.setFocusPainted(false);
        btn.setIconTextGap(6);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(65, 72, 84)),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                btn.setBackground(new Color(48, 55, 68));
            }

            public void mouseExited(MouseEvent e) {
                btn.setBackground(new Color(36, 41, 50));
            }
        });
    }

    public void zoomIn1to1() {
        lockIsometric = true;
        int w = graphPanel.getWidth();
        int h = graphPanel.getHeight();
        if (w <= 0 || h <= 0) {
            w = 1300;
            h = 730;
        }

        double screenCenterX = w / 2.0;
        double screenCenterY = h / 2.0;

        double currentScale = (graphScale > 0 && !Double.isNaN(graphScale)) ? graphScale : h / (viewMaxY - viewMinY);
        double zoomMultiplier = 1.35;
        double newScale = currentScale * zoomMultiplier;
        if (newScale > 1e7)
            newScale = 1e7;
        graphScale = newScale;

        double mathCenterX = viewMinX + screenCenterX / currentScale;
        double mathCenterY = viewMinY + (h - screenCenterY) / currentScale;

        viewMinX = mathCenterX - screenCenterX / newScale;
        viewMaxX = viewMinX + w / newScale;
        viewMinY = mathCenterY - (h - screenCenterY) / newScale;
        viewMaxY = viewMinY + h / newScale;

        graphPanel.repaint();
    }

    public void fitToVisibleCurves() {
        lockIsometric = true;

        double minFitX = Double.MAX_VALUE, maxFitX = -Double.MAX_VALUE;
        double minFitY = Double.MAX_VALUE, maxFitY = -Double.MAX_VALUE;

        for (FunctionEntry fe : visibleFunctions) {
            if (!fe.isValid)
                continue;
            if (fe.isImplicit && fe.engine != null && fe.engine.hasImplicitBounds()) {
                minFitY = Math.min(minFitY, fe.engine.getImplicitMinY());
                maxFitY = Math.max(maxFitY, fe.engine.getImplicitMaxY());
                minFitX = Math.min(minFitX, fe.engine.getImplicitMinX());
                maxFitX = Math.max(maxFitX, fe.engine.getImplicitMaxX());
            } else if (fe.engine != null) {
                minFitX = Math.min(minFitX, minX);
                maxFitX = Math.max(maxFitX, maxX);
                java.util.List<Double> validVals = new java.util.ArrayList<>();
                for (int i = 0; i < fe.engine.getNumSamples(); i++) {
                    double val = fe.engine.getDisplaySignal().get(i);
                    if (Double.isFinite(val))
                        validVals.add(val);
                }
                if (!validVals.isEmpty()) {
                    java.util.Collections.sort(validVals);
                    int n = validVals.size();
                    double lowP = validVals.get((int) (n * 0.01));
                    double highP = validVals.get((int) (n * 0.99));
                    if (Math.abs(highP) > 1e4 || Math.abs(lowP) > 1e4) {
                        lowP = validVals.get((int) (n * 0.05));
                        highP = validVals.get((int) (n * 0.95));
                        if (Math.abs(highP) > 1e4 || Math.abs(lowP) > 1e4) {
                            lowP = validVals.get((int) (n * 0.10));
                            highP = validVals.get((int) (n * 0.90));
                        }
                    }
                    lowP = Math.max(-1e4, Math.min(1e4, lowP));
                    highP = Math.max(-1e4, Math.min(1e4, highP));
                    minFitY = Math.min(minFitY, lowP);
                    maxFitY = Math.max(maxFitY, highP);
                }
                if (!fe.engine.getImpulses().isEmpty()) {
                    for (SignalEngine.Impulse imp : fe.engine.getImpulses()) {
                        if (imp.weight > 0)
                            maxFitY = Math.max(maxFitY, imp.weight);
                        if (imp.weight < 0)
                            minFitY = Math.min(minFitY, imp.weight);
                        minFitX = Math.min(minFitX, imp.x);
                        maxFitX = Math.max(maxFitX, imp.x);
                    }
                }
            }
        }

        if (minFitY == Double.MAX_VALUE || maxFitY == -Double.MAX_VALUE || Math.abs(maxFitY - minFitY) < 1e-6) {
            minFitX = minX;
            maxFitX = maxX;
            minFitY = -2.0;
            maxFitY = 2.0;
        }
        minFitX = Math.max(-1e4, Math.min(1e4, minFitX));
        maxFitX = Math.max(-1e4, Math.min(1e4, maxFitX));
        minFitY = Math.max(-1e4, Math.min(1e4, minFitY));
        maxFitY = Math.max(-1e4, Math.min(1e4, maxFitY));

        int w = graphPanel.getWidth();
        int h = graphPanel.getHeight();
        if (w <= 0 || h <= 0) {
            w = 1300;
            h = 730;
        }

        double cx = (minFitX + maxFitX) / 2.0;
        double cy = (minFitY + maxFitY) / 2.0;
        if (!Double.isFinite(cx) || Math.abs(cx) > 1e4)
            cx = (minX + maxX) / 2.0;
        if (!Double.isFinite(cy) || Math.abs(cy) > 1e4)
            cy = 0.0;

        double spanX = Math.max(1.0, (maxFitX - minFitX) * 1.35);
        double spanY = Math.max(1.0, (maxFitY - minFitY) * 1.35);

        double targetScale = Math.min(w / spanX, h / spanY);
        if (targetScale < 1e-4)
            targetScale = 1e-4;
        if (targetScale > 1e7)
            targetScale = 1e7;
        double currentScale = (graphScale > 0 && !Double.isNaN(graphScale)) ? graphScale : h / (viewMaxY - viewMinY);
        if (currentScale < 1e-4)
            currentScale = 1e-4;
        if (currentScale > 1e7)
            currentScale = 1e7;

        double chosenScale;
        if (currentScale > targetScale * 1.05) {
            chosenScale = targetScale;
        } else {
            chosenScale = currentScale / 1.35;
            if (chosenScale < 1e-4)
                chosenScale = 1e-4;
        }
        graphScale = chosenScale;

        double centerOffsetScreenX = w / 2.0;
        viewMinX = cx - centerOffsetScreenX / chosenScale;
        viewMaxX = viewMinX + w / chosenScale;
        viewMinY = cy - (h / 2.0) / chosenScale;
        viewMaxY = cy + (h / 2.0) / chosenScale;

        graphPanel.repaint();
    }

    public void resetAllStates() {
        lockIsometric = true;
        selectedHarmonic = null;

        for (FunctionEntry fe : functionEntries) {
            fe.isDiscretized = false;
            fe.displayMode = FunctionEntry.DisplayMode.NORMAL;
        }

        if (rightScrollPane != null)
            rightScrollPane.setVisible(false);
        if (calculatorPanel != null)
            calculatorPanel.setVisible(false);
        if (shortcutsPanel != null)
            shortcutsPanel.setVisible(false);

        double globalMin = Double.MAX_VALUE;
        double globalMax = -Double.MAX_VALUE;

        FunctionEntry targetEntry = (activeFunction != null && activeFunction.isValid) ? activeFunction
                : (!visibleFunctions.isEmpty() ? visibleFunctions.iterator().next() : null);

        if (targetEntry != null && targetEntry.engine != null) {
            if (targetEntry.isImplicit && targetEntry.engine.hasImplicitBounds()) {
                globalMin = targetEntry.engine.getImplicitMinY();
                globalMax = targetEntry.engine.getImplicitMaxY();
            } else if (targetEntry.engine.getDisplaySignal() != null && targetEntry.engine.getNumSamples() > 0) {
                for (int i = 0; i < targetEntry.engine.getNumSamples(); i++) {
                    double val = targetEntry.engine.getDisplaySignal().get(i);
                    if (Double.isFinite(val)) {
                        if (val < globalMin)
                            globalMin = val;
                        if (val > globalMax)
                            globalMax = val;
                    }
                }
                if (!targetEntry.engine.getImpulses().isEmpty()) {
                    for (SignalEngine.Impulse imp : targetEntry.engine.getImpulses()) {
                        if (imp.weight > 0 && imp.weight > globalMax)
                            globalMax = imp.weight;
                        if (imp.weight < 0 && imp.weight < globalMin)
                            globalMin = imp.weight;
                        if (0.0 < globalMin)
                            globalMin = 0.0;
                        if (0.0 > globalMax)
                            globalMax = 0.0;
                    }
                }
            }
        }

        if (globalMin != Double.MAX_VALUE && globalMax != -Double.MAX_VALUE) {
            globalMin = Math.max(-1e4, Math.min(1e4, globalMin));
            globalMax = Math.max(-1e4, Math.min(1e4, globalMax));
            double range = globalMax - globalMin;
            double margin = range == 0 ? 2.0 : range * 0.2;
            viewMinY = globalMin - margin;
            viewMaxY = globalMax + margin;
        } else {
            viewMinY = -2.0;
            viewMaxY = 2.0;
        }

        int w = graphPanel.getWidth();
        int h = graphPanel.getHeight();
        if (w <= 0 || h <= 0) {
            w = 1300;
            h = 730;
        }

        double spanY = viewMaxY - viewMinY;
        if (spanY <= 0 || Double.isNaN(spanY))
            spanY = 4.0;
        double scale = h / spanY;
        if (scale < 1e-4)
            scale = 1e-4;
        if (scale > 1e7)
            scale = 1e7;
        graphScale = scale;
        double cx = 0.0;
        viewMinX = cx - (w / 2.0) / scale;
        viewMaxX = cx + (w / 2.0) / scale;

        updateRightSidebar();
        buildBottomBar();

        centerLayeredPane.revalidate();
        centerLayeredPane.doLayout();
        centerLayeredPane.repaint();
        graphPanel.repaint();
        sidebar.repaint();
        bottomBar.repaint();
    }

    public void resetView() {
        resetAllStates();
    }

    private static String formatAxisLabel(double val, double step) {
        if (Math.abs(val) < 1e-11)
            return "0";
        if (Math.abs(val) >= 1e5 || (Math.abs(val) > 0 && Math.abs(val) < 1e-4)) {
            return String.format(java.util.Locale.US, "%.2e", val);
        }
        if (Math.abs(val - Math.round(val)) < 1e-9 && Math.abs(val) < 1e12) {
            return Long.toString(Math.round(val));
        }
        int decimals = (int) Math.max(0, Math.min(6, Math.ceil(-Math.log10(step))));
        String s = String.format(java.util.Locale.US, "%." + decimals + "f", val);
        if (s.contains(".")) {
            s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return s;
    }

    class GraphPanel extends JPanel {
        private Point lastMousePt;
        private Point hoverPt;
        private Point hoverDrawnAt;
        private boolean isZooming = false;
        private volatile boolean gestureActive = false;
        private volatile boolean mouseDown = false;
        private final Timer gestureEndTimer = new Timer(140, ev -> {
            if (mouseDown) {
                return;
            }
            gestureActive = false;
            invalidate();
            repaint(0, 0, getWidth(), getHeight());
        });
        private final Timer zoomDebounceTimer = new Timer(75, ev -> {
            isZooming = false;
            repaint();
        });
        private final Timer panRefineTimer = new Timer(120, ev -> triggerAsyncPanRefine());
        private volatile boolean pendingGestureRepaint = false;
        private final Timer gesturePaintTimer = new Timer(7, ev -> {
            if (gestureActive && pendingGestureRepaint) {
                pendingGestureRepaint = false;
                repaint();
            }
        });

        private volatile BufferedImage displayedCanvas;
        private volatile double displayedViewMinX = Double.NaN, displayedViewMaxX = Double.NaN;
        private volatile double displayedViewMinY = Double.NaN, displayedViewMaxY = Double.NaN;
        private volatile int displayedW = -1, displayedH = -1;
        private volatile long displayedDataHash = 0;
        private volatile int displayedSidebarW = -1, displayedRightSidebarW = -1;
        private volatile boolean displayedRightVis = false, displayedLockIso = false;
        private static final boolean DISEGNA_CURVA_SU_CANVAS = false;

        private transient int canvasMargin = -1;
        private transient int canvasMarginW = -1;
        private transient int canvasMarginH = -1;

        private int canvasMargin(int w, int h) {
            if (w == canvasMarginW && h == canvasMarginH && canvasMargin > 0) {
                return canvasMargin;
            }
            canvasMargin = Math.max(w, h);
            canvasMarginW = w;
            canvasMarginH = h;
            return canvasMargin;
        }

        private final AtomicLong renderGeneration = new AtomicLong(0);
        private volatile boolean renderInProgress = false;
        private final ExecutorService renderExecutor;

        public static class PathData {
            public Path2D.Double signalPath;
            public Path2D.Double bandPath;
            public Path2D.Double displayPath;
            public double evalMinX;
            public double evalMaxX;
            public double evalMinY;
            public double evalMaxY;
            public double originMinX;
            public double originMaxX;
            public double originMinY;
            public double originMaxY;
            public int originW;
            public int originH;
            public int evalW;
            public int evalH;
        }

        private static boolean viewApproxEqual(double a, double b) {
            return Math.abs(a - b) < 1e-6;
        }

        public GraphPanel() {
            setBackground(COLOR_BG);
            setOpaque(true);
            setDoubleBuffered(false);

            renderExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "GraphPanel-Render");
                t.setDaemon(true);
                t.setPriority(Thread.NORM_PRIORITY - 1);
                return t;
            });

            zoomDebounceTimer.setRepeats(false);
            gestureEndTimer.setRepeats(false);

            addMouseWheelListener(e -> {
                int w = getWidth();
                int h = getHeight();
                if (w <= 0 || h <= 0)
                    return;

                isZooming = true;
                zoomDebounceTimer.restart();
                gestureActive = true;
                gestureEndTimer.restart();

                double zoomFactor = e.getWheelRotation() < 0 ? (1.0 / 1.1) : 1.1;

                if (lockIsometric) {
                    double currentScale = (graphScale > 0 && !Double.isNaN(graphScale)) ? graphScale
                            : h / (viewMaxY - viewMinY);
                    if (currentScale < 1e-4)
                        currentScale = 1e-4;
                    if (currentScale > 1e7)
                        currentScale = 1e7;
                    double newScale = currentScale / zoomFactor;
                    if (newScale < 1e-4)
                        newScale = 1e-4;
                    if (newScale > 1e7)
                        newScale = 1e7;
                    graphScale = newScale;

                    double mx = e.getX();
                    double my = e.getY();
                    double x = viewMinX + mx / currentScale;
                    double y = viewMaxY - my / currentScale;

                    viewMinX = x - mx / newScale;
                    viewMaxX = viewMinX + w / newScale;
                    viewMinY = y - (h - my) / newScale;
                    viewMaxY = viewMinY + h / newScale;
                } else {
                    double x = viewMinX + (e.getX() / (double) w) * (viewMaxX - viewMinX);
                    double y = viewMaxY - (e.getY() / (double) h) * (viewMaxY - viewMinY);

                    double newRangeX = (viewMaxX - viewMinX) * zoomFactor;
                    double newRangeY = (viewMaxY - viewMinY) * zoomFactor;

                    double minSpan = 1e-4;
                    double maxSpan = 1e6;
                    newRangeX = Math.max(minSpan, Math.min(maxSpan, newRangeX));
                    newRangeY = Math.max(minSpan, Math.min(maxSpan, newRangeY));

                    double ratioX = (x - viewMinX) / (viewMaxX - viewMinX);
                    double ratioY = (y - viewMinY) / (viewMaxY - viewMinY);

                    viewMinX = x - newRangeX * ratioX;
                    viewMaxX = x + newRangeX * (1 - ratioX);
                    viewMinY = y - newRangeY * ratioY;
                    viewMaxY = y + newRangeY * (1 - ratioY);
                }

                double maxCoord = 1e6;
                if (viewMinX < -maxCoord) {
                    double s = -maxCoord - viewMinX;
                    viewMinX += s;
                    viewMaxX += s;
                }
                if (viewMaxX > maxCoord) {
                    double s = maxCoord - viewMaxX;
                    viewMinX += s;
                    viewMaxX += s;
                }
                if (viewMinY < -maxCoord) {
                    double s = -maxCoord - viewMinY;
                    viewMinY += s;
                    viewMaxY += s;
                }
                if (viewMaxY > maxCoord) {
                    double s = maxCoord - viewMaxY;
                    viewMinY += s;
                    viewMaxY += s;
                }
                repaint();
                panRefineTimer.restart();
            });

            addMouseListener(new MouseAdapter() {
                public void mousePressed(MouseEvent e) {
                    lastMousePt = e.getPoint();
                    gestureActive = true;
                    mouseDown = true;
                    gestureEndTimer.stop();
                }

                public void mouseReleased(MouseEvent e) {
                    lastMousePt = null;
                    mouseDown = false;
                    repaint();
                    triggerAsyncPanRefine();
                    gestureEndTimer.restart();
                }

                public void mouseExited(MouseEvent e) {
                    hoverPt = null;
                    repaintHoverStrips();
                }
            });

            addMouseMotionListener(new MouseMotionAdapter() {
                public void mouseMoved(MouseEvent e) {
                    hoverPt = e.getPoint();
                    repaintHoverStrips();
                }

                public void mouseDragged(MouseEvent e) {
                    hoverPt = e.getPoint();
                    if (lastMousePt == null) {
                        lastMousePt = e.getPoint();
                        return;
                    }
                    gestureActive = true;
                    gestureEndTimer.restart();
                    int dx = e.getX() - lastMousePt.x;
                    int dy = e.getY() - lastMousePt.y;
                    int w = getWidth();
                    int h = getHeight();
                    if (w <= 0 || h <= 0)
                        return;

                    if (lockIsometric) {
                        double scale = (graphScale > 0 && !Double.isNaN(graphScale)) ? graphScale
                                : h / (viewMaxY - viewMinY);
                        double deltaX = dx / scale;
                        double deltaY = dy / scale;
                        viewMinX -= deltaX;
                        viewMaxX -= deltaX;
                        viewMinY += deltaY;
                        viewMaxY += deltaY;
                    } else {
                        double deltaX = dx * (viewMaxX - viewMinX) / w;
                        double deltaY = dy * (viewMaxY - viewMinY) / h;
                        viewMinX -= deltaX;
                        viewMaxX -= deltaX;
                        viewMinY += deltaY;
                        viewMaxY += deltaY;
                    }

                    double maxCoord = 1e6;
                    if (viewMinX < -maxCoord) {
                        double s = -maxCoord - viewMinX;
                        viewMinX += s;
                        viewMaxX += s;
                    }
                    if (viewMaxX > maxCoord) {
                        double s = maxCoord - viewMaxX;
                        viewMinX += s;
                        viewMaxX += s;
                    }
                    if (viewMinY < -maxCoord) {
                        double s = -maxCoord - viewMinY;
                        viewMinY += s;
                        viewMaxY += s;
                    }
                    if (viewMaxY > maxCoord) {
                        double s = maxCoord - viewMaxY;
                        viewMinY += s;
                        viewMaxY += s;
                    }

                    lastMousePt = e.getPoint();
                    pendingGestureRepaint = true;
                    if (!gesturePaintTimer.isRunning()) {
                        gesturePaintTimer.start();
                    }
                    panRefineTimer.restart();
                }
            });

            addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    int w = getWidth();
                    int h = getHeight();
                    if (w <= 0 || h <= 0)
                        return;
                    if (lockIsometric)
                        enforceIsometric(w, h);
                    repaint();
                    panRefineTimer.restart();
                }
            });
        }

        private long computeStaticDataHash(java.util.Collection<FunctionEntry> functions,
                Integer selHarmonic, FunctionEntry activeFunc) {
            long h = 17;
            for (FunctionEntry fe : functions) {
                h = h * 31 + System.identityHashCode(fe);
                h = h * 31 + fe.displayMode.ordinal();
                h = h * 31 + (fe.isValid ? 1 : 0);
                h = h * 31 + fe.cachedAccHarmonic;
            }
            h = h * 31 + (selHarmonic == null ? -1 : selHarmonic);
            h = h * 31 + (activeFunc == null ? 0 : System.identityHashCode(activeFunc));
            return h;
        }

        @Override
        protected void paintComponent(Graphics g) {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0)
                return;

            final int cm = canvasMargin(w, h);
            long currentHash = computeStaticDataHash(visibleFunctions, selectedHarmonic, activeFunction);

            boolean exact = !gestureActive && displayedCanvas != null
                    && displayedW == w && displayedH == h
                    && displayedSidebarW == sidebarWidth
                    && displayedRightSidebarW == rightSidebarWidth
                    && displayedRightVis == rightScrollPane.isVisible()
                    && displayedLockIso == lockIsometric
                    && viewApproxEqual(displayedViewMinX, viewMinX)
                    && viewApproxEqual(displayedViewMaxX, viewMaxX)
                    && viewApproxEqual(displayedViewMinY, viewMinY)
                    && viewApproxEqual(displayedViewMaxY, viewMaxY)
                    && displayedDataHash == currentHash;

            boolean canTransform = displayedCanvas != null
                    && displayedW == w && displayedH == h
                    && displayedSidebarW == sidebarWidth
                    && displayedRightSidebarW == rightSidebarWidth
                    && displayedRightVis == rightScrollPane.isVisible()
                    && displayedLockIso == lockIsometric;

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

            if (exact) {
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.drawImage(displayedCanvas,
                        0, 0, w, h,
                        cm, cm, cm + w, cm + h,
                        null);
            } else if (canTransform) {
                double dispW = displayedViewMaxX - displayedViewMinX;
                double dispH = displayedViewMaxY - displayedViewMinY;
                double curW = viewMaxX - viewMinX;
                double curH = viewMaxY - viewMinY;
                if (dispW > 1e-12 && dispH > 1e-12 && curW > 1e-12 && curH > 1e-12) {
                    double sx = curW / dispW;
                    double sy = curH / dispH;
                    double tx = (displayedViewMinX - viewMinX) * w / curW;
                    double ty = (viewMaxY - displayedViewMaxY) * h / curH;
                    boolean soloPan = Math.abs(sx - 1.0) < 1e-3 && Math.abs(sy - 1.0) < 1e-3;
                    double halfMargin = cm - 8.0;
                    if (Math.abs(tx) > halfMargin || Math.abs(ty) > halfMargin) {
                        tx = Math.max(-halfMargin, Math.min(halfMargin, tx));
                        ty = Math.max(-halfMargin, Math.min(halfMargin, ty));
                    }

                    g2.setColor(getBackground());
                    g2.fillRect(0, 0, w, h);
                    if (soloPan) {
                        tx = Math.rint(tx);
                        ty = Math.rint(ty);
                        int sx0 = cm - (int) tx;
                        int sy0 = cm - (int) ty;
                        sx0 = Math.max(0, Math.min(displayedCanvas.getWidth() - w, sx0));
                        sy0 = Math.max(0, Math.min(displayedCanvas.getHeight() - h, sy0));
                        g2.drawImage(displayedCanvas, 0, 0, w, h, sx0, sy0, sx0 + w, sy0 + h, null);
                    } else {
                        // FIX BUG SPESSORE: Durante lo zoom, disegniamo sempre i vettori
                        // invece di scalare l'immagine di sfondo. Questo mantiene lo spessore costante.
                        paintStaticContent(g2, w, h,
                                new java.util.ArrayList<>(visibleFunctions), selectedHarmonic, activeFunction,
                                viewMinX, viewMaxX, viewMinY, viewMaxY,
                                sidebarWidth, rightSidebarWidth, rightScrollPane.isVisible(), lockIsometric);
                    }
                } else {
                    g2.setColor(getBackground());
                    g2.fillRect(0, 0, w, h);
                    paintStaticContent(g2, w, h,
                            new java.util.ArrayList<>(visibleFunctions), selectedHarmonic, activeFunction,
                            viewMinX, viewMaxX, viewMinY, viewMaxY,
                            sidebarWidth, rightSidebarWidth, rightScrollPane.isVisible(), lockIsometric);
                }
            } else {
                // FIX BUG SPESSORE: Se il canvas non è pronto o la finestra è stata
                // ridimensionata,
                // ridisegniamo i vettori da zero invece di scalare l'immagine.
                g2.setColor(getBackground());
                g2.fillRect(0, 0, w, h);
                paintStaticContent(g2, w, h,
                        new java.util.ArrayList<>(visibleFunctions), selectedHarmonic, activeFunction,
                        viewMinX, viewMaxX, viewMinY, viewMaxY,
                        sidebarWidth, rightSidebarWidth, rightScrollPane.isVisible(), lockIsometric);
            }

            paintHoverOverlay(g2, w, h);
            g2.dispose();

            hoverDrawnAt = hoverOverlayVisible(w, h) ? new Point(hoverPt.x, hoverPt.y) : null;

            if (!exact && !gestureActive) {
                triggerBackgroundRender(w, h);
            }
        }

        private void triggerBackgroundRender(final int w, final int h) {
            if (renderInProgress)
                return;
            renderInProgress = true;

            final long gen = renderGeneration.incrementAndGet();
            final double snapViewMinX = viewMinX;
            final double snapViewMaxX = viewMaxX;
            final double snapViewMinY = viewMinY;
            final double snapViewMaxY = viewMaxY;
            final int snapSidebarW = sidebarWidth;
            final int snapRightSidebarW = rightSidebarWidth;
            final boolean snapRightVis = rightScrollPane.isVisible();
            final boolean snapLockIso = lockIsometric;
            final Integer snapSelectedHarmonic = selectedHarmonic;
            final FunctionEntry snapActiveFunction = activeFunction;
            final List<FunctionEntry> snapFunctions = new ArrayList<>(visibleFunctions);
            final long snapHash = computeStaticDataHash(snapFunctions, snapSelectedHarmonic, snapActiveFunction);

            renderExecutor.submit(() -> {
                BufferedImage newCanvas = null;
                try {
                    if (renderGeneration.get() != gen)
                        return;

                    final int cmR = canvasMargin(w, h);
                    final int cw = w + 2 * cmR;
                    final int ch = h + 2 * cmR;
                    GraphicsConfiguration gc = getGraphicsConfiguration();
                    if (gc != null) {
                        newCanvas = gc.createCompatibleImage(cw, ch, Transparency.OPAQUE);
                    }
                    if (newCanvas == null) {
                        newCanvas = new BufferedImage(cw, ch, BufferedImage.TYPE_INT_RGB);
                    }

                    Graphics2D cg = newCanvas.createGraphics();
                    cg.setColor(getBackground());
                    cg.fillRect(0, 0, cw, ch);
                    cg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    cg.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    cg.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                    cg.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                            RenderingHints.VALUE_FRACTIONALMETRICS_ON);
                    cg.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

                    double unitX = (snapViewMaxX - snapViewMinX) / w;
                    double unitY = (snapViewMaxY - snapViewMinY) / h;
                    double margX = cmR * unitX;
                    double margY = cmR * unitY;
                    paintStaticContent(cg, cw, ch,
                            snapFunctions, snapSelectedHarmonic, snapActiveFunction,
                            snapViewMinX - margX, snapViewMaxX + margX,
                            snapViewMinY - margY, snapViewMaxY + margY,
                            snapSidebarW, snapRightSidebarW, snapRightVis, snapLockIso);

                    cg.dispose();

                    if (renderGeneration.get() != gen)
                        return;

                    final BufferedImage swap = newCanvas;
                    SwingUtilities.invokeLater(() -> {
                        if (renderGeneration.get() != gen)
                            return;
                        boolean vistaCambiata = !viewApproxEqual(snapViewMinX, viewMinX)
                                || !viewApproxEqual(snapViewMaxX, viewMaxX)
                                || !viewApproxEqual(snapViewMinY, viewMinY)
                                || !viewApproxEqual(snapViewMaxY, viewMaxY);
                        if (vistaCambiata || gestureActive || mouseDown) {
                            renderInProgress = false;
                            repaint();
                            return;
                        }
                        displayedCanvas = swap;
                        displayedViewMinX = snapViewMinX;
                        displayedViewMaxX = snapViewMaxX;
                        displayedViewMinY = snapViewMinY;
                        displayedViewMaxY = snapViewMaxY;
                        displayedW = w;
                        displayedH = h;
                        displayedSidebarW = snapSidebarW;
                        displayedRightSidebarW = snapRightSidebarW;
                        displayedRightVis = snapRightVis;
                        displayedLockIso = snapLockIso;
                        displayedDataHash = snapHash;
                        repaint();
                    });
                } catch (Exception ex) {
                    // ignora
                } finally {
                    SwingUtilities.invokeLater(() -> {
                        renderInProgress = false;
                        if (!viewApproxEqual(snapViewMinX, viewMinX)
                                || !viewApproxEqual(snapViewMaxX, viewMaxX)
                                || !viewApproxEqual(snapViewMinY, viewMinY)
                                || !viewApproxEqual(snapViewMaxY, viewMaxY)) {
                            repaint();
                        }
                    });
                }
            });
        }

        private void paintStaticContent(Graphics2D g2, int w, int h,
                List<FunctionEntry> functions,
                Integer selHarmonic,
                FunctionEntry activeFunc,
                double vMinX, double vMaxX, double vMinY, double vMaxY,
                int sw, int rsw, boolean rvis, boolean lockIso) {
            double xRange = vMaxX - vMinX;
            double yRange = vMaxY - vMinY;
            if (xRange <= 0 || yRange <= 0)
                return;
            double scaleX = w / xRange;
            double scaleY = h / yRange;

            double gridStepX;
            double gridStepY;
            if (lockIso) {
                double rawStep = 90.0 / scaleY;
                double mag = Math.pow(10, Math.floor(Math.log10(rawStep)));
                double norm = rawStep / mag;
                double step;
                if (norm < 1.5)
                    step = 1 * mag;
                else if (norm < 3.5)
                    step = 2 * mag;
                else if (norm < 7.5)
                    step = 5 * mag;
                else
                    step = 10 * mag;
                gridStepX = step;
                gridStepY = step;
            } else {
                double rawGridStepX = xRange / 10.0;
                double magX = Math.pow(10, Math.floor(Math.log10(rawGridStepX)));
                double normX = rawGridStepX / magX;
                if (normX < 1.5)
                    gridStepX = 1 * magX;
                else if (normX < 3.5)
                    gridStepX = 2 * magX;
                else if (normX < 7.5)
                    gridStepX = 5 * magX;
                else
                    gridStepX = 10 * magX;

                double rawGridStepY = yRange / 10.0;
                double magY = Math.pow(10, Math.floor(Math.log10(rawGridStepY)));
                double normY = rawGridStepY / magY;
                if (normY < 1.5)
                    gridStepY = 1 * magY;
                else if (normY < 3.5)
                    gridStepY = 2 * magY;
                else if (normY < 7.5)
                    gridStepY = 5 * magY;
                else
                    gridStepY = 10 * magY;
            }

            double cellW = gridStepX * scaleX;
            double cellH = gridStepY * scaleY;
            if (cellW >= 60.0 && cellH >= 60.0) {
                g2.setColor(new Color(36, 41, 48));
                g2.setStroke(new BasicStroke(1.0f));
                double subStepX = gridStepX / 5.0;
                double startSubX = Math.ceil(vMinX / subStepX) * subStepX;
                for (double x = startSubX; x <= vMaxX; x += subStepX) {
                    int px = (int) Math.round((x - vMinX) * scaleX);
                    g2.drawLine(px, 0, px, h);
                }
                double subStepY = gridStepY / 5.0;
                double startSubY = Math.ceil(vMinY / subStepY) * subStepY;
                for (double y = startSubY; y <= vMaxY; y += subStepY) {
                    int py = (int) Math.round(h - (y - vMinY) * scaleY);
                    g2.drawLine(0, py, w, py);
                }
            }

            g2.setColor(COLOR_GRID);
            g2.setStroke(new BasicStroke(1.0f));

            double startXGrid = Math.ceil(vMinX / gridStepX) * gridStepX;
            for (double x = startXGrid; x <= vMaxX; x += gridStepX) {
                int px = (int) Math.round((x - vMinX) * scaleX);
                g2.drawLine(px, 0, px, h);
            }

            double startYGrid = Math.ceil(vMinY / gridStepY) * gridStepY;
            for (double y = startYGrid; y <= vMaxY; y += gridStepY) {
                int py = (int) Math.round(h - (y - vMinY) * scaleY);
                g2.drawLine(0, py, w, py);
            }

            g2.setColor(new Color(100, 110, 125));
            g2.setStroke(new BasicStroke(2.0f));
            int zeroY = (int) Math.round(h - (0 - vMinY) * scaleY);
            if (zeroY >= 0 && zeroY <= h)
                g2.drawLine(0, zeroY, w, zeroY);

            int zeroX = (int) Math.round((0 - vMinX) * scaleX);
            if (zeroX >= 0 && zeroX <= w)
                g2.drawLine(zeroX, 0, zeroX, h);

            g2.setStroke(new BasicStroke(1.5f));
            g2.setColor(new Color(130, 140, 155));
            if (zeroY >= 0 && zeroY <= h) {
                for (double x = startXGrid; x <= vMaxX; x += gridStepX) {
                    int px = (int) Math.round((x - vMinX) * scaleX);
                    g2.drawLine(px, zeroY - 4, px, zeroY + 4);
                }
            }
            if (zeroX >= 0 && zeroX <= w) {
                for (double y = startYGrid; y <= vMaxY; y += gridStepY) {
                    int py = (int) Math.round(h - (y - vMinY) * scaleY);
                    g2.drawLine(zeroX - 4, py, zeroX + 4, py);
                }
            }

            double[] templateFracs = null;
            double[] templateY = null;

            for (FunctionEntry entry : functions) {
                if (!entry.isValid)
                    continue;

                if (entry.displayMode != FunctionEntry.DisplayMode.NORMAL) {
                    int maxH = entry.engine.getMaxHarmonics();
                    double stepX = w / (double) (maxH + 1);
                    for (int n = 1; n <= maxH; n++) {
                        SignalEngine.Harmonic harm = entry.engine.getHarmonic(n);
                        double hAmp = Math.hypot(harm.a, harm.b);
                        double hPhase = Math.atan2(-harm.b, harm.a);

                        double px = n * stepX;

                        if (entry.displayMode == FunctionEntry.DisplayMode.SPECTRUM_AMP_PHASE) {
                            double pyAmp = zeroY - hAmp * scaleY;
                            g2.setColor(COLOR_SIGNAL);
                            g2.setStroke(new BasicStroke(2.0f));
                            g2.drawLine((int) px, (int) zeroY, (int) px, (int) pyAmp);
                            g2.fillOval((int) px - 4, (int) pyAmp - 4, 8, 8);

                            double phaseScaleY = (h / 2.0) / (Math.PI * 1.2);
                            double pyPhase = zeroY - hPhase * phaseScaleY;
                            g2.setColor(new Color(229, 192, 123));
                            g2.fillOval((int) px - 3, (int) pyPhase - 3, 6, 6);
                        } else if (entry.displayMode == FunctionEntry.DisplayMode.SPECTRUM_POWER) {
                            double power = hAmp * hAmp;
                            double pyPow = zeroY - power * scaleY;
                            g2.setColor(new Color(198, 120, 221));
                            g2.setStroke(new BasicStroke(2.0f));
                            g2.drawLine((int) px, (int) zeroY, (int) px, (int) pyPow);
                            g2.fillOval((int) px - 4, (int) pyPow - 4, 8, 8);
                        }
                    }
                } else {
                    if (entry.isImplicit && entry.expr != null) {
                        Path2D implicitPath;
                        if (entry.cachedSignalPath != null && Double.isFinite(entry.cachedViewMinX)
                                && entry.cachedW > 0) {
                            double oldScaleX = (double) entry.cachedW / (entry.cachedViewMaxX - entry.cachedViewMinX);
                            double oldScaleY = (double) h / (entry.cachedViewMaxY - entry.cachedViewMinY);
                            double scaleRatioX = scaleX / oldScaleX;
                            double scaleRatioY = scaleY / oldScaleY;
                            double transX = (entry.cachedViewMinX - vMinX) * scaleX;
                            double transY = h * (1.0 - scaleRatioY) - (entry.cachedViewMinY - vMinY) * scaleY;
                            AffineTransform atFast = new AffineTransform(scaleRatioX, 0.0, 0.0, scaleRatioY, transX,
                                    transY);
                            implicitPath = new Path2D.Double(entry.cachedSignalPath, atFast);
                        } else {
                            implicitPath = null;
                            SwingUtilities.invokeLater(() -> triggerAsyncPanRefine());
                        }
                        if (implicitPath != null) {
                            g2.setColor(COLOR_SIGNAL);
                            g2.setStroke(new BasicStroke(entry == activeFunc ? 2.5f : 1.5f));
                            g2.draw(implicitPath);
                        }
                    } else if (entry.signalType == SignalType.DISCRETE_APERIODIC
                            || entry.signalType == SignalType.DISCRETE_PERIODIC
                            || entry.signalType == SignalType.DISCRETE_RANDOM
                            || entry.isDiscretized) {
                        g2.setColor(COLOR_SIGNAL);
                        g2.setStroke(new BasicStroke(entry == activeFunc ? 2.0f : 1.5f));

                        long nStart = (long) Math.floor(vMinX);
                        long nEnd = (long) Math.ceil(vMaxX);
                        long stepN = 1L;
                        if (nEnd - nStart > 3000L) {
                            stepN = Math.max(1L, (nEnd - nStart) / 3000L);
                        }

                        double period = maxX - minX;
                        boolean isPeriodic = (entry.signalType == SignalType.DISCRETE_PERIODIC
                                || entry.signalType == SignalType.CONTINUOUS_PERIODIC
                                || (entry.isDiscretized && selHarmonic != null));
                        int dotR = 4;

                        for (long n = nStart; n <= nEnd; n += stepN) {
                            double evalN = n;
                            if (isPeriodic && period > 0) {
                                evalN = ((n - minX) % period + period) % period + minX;
                            }
                            double y;
                            if (entry.expr != null) {
                                try {
                                    y = entry.expr.eval(evalN);
                                } catch (Exception e) {
                                    y = Double.NaN;
                                }
                            } else if (entry.signalType == SignalType.DISCRETE_RANDOM) {
                                y = MathParser.hashRandn(evalN);
                            } else {
                                y = Double.NaN;
                            }

                            if (!Double.isFinite(y))
                                continue;

                            int px = (int) Math.round((n - vMinX) * scaleX);
                            if (px < -20 || px > w + 20)
                                continue;

                            int clampedZeroY = Math.max(0, Math.min(h, zeroY));
                            double pyExact = h - (y - vMinY) * scaleY;

                            if (pyExact < 0) {
                                g2.drawLine(px, clampedZeroY, px, 0);
                                Path2D.Double arrow = new Path2D.Double();
                                arrow.moveTo(px, 0);
                                arrow.lineTo(px - 4, 6);
                                arrow.lineTo(px + 4, 6);
                                arrow.closePath();
                                g2.fill(arrow);
                            } else if (pyExact > h) {
                                g2.drawLine(px, clampedZeroY, px, h);
                                Path2D.Double arrow = new Path2D.Double();
                                arrow.moveTo(px, h);
                                arrow.lineTo(px - 4, h - 6);
                                arrow.lineTo(px + 4, h - 6);
                                arrow.closePath();
                                g2.fill(arrow);
                            } else {
                                int py = (int) Math.round(pyExact);
                                g2.drawLine(px, zeroY, px, py);
                                g2.fillOval(px - dotR, py - dotR, 2 * dotR, 2 * dotR);
                            }
                        }
                    } else if (entry.engine != null) {
                        double period = maxX - minX;
                        boolean isPeriodic = (entry.signalType == SignalType.CONTINUOUS_PERIODIC);

                        boolean canUseCache = entry.cachedDisplayPath != null
                                && entry.cachedExpr == entry.expr
                                && entry.cachedOriginMinX == vMinX && entry.cachedOriginMaxX == vMaxX
                                && entry.cachedOriginMinY == vMinY && entry.cachedOriginMaxY == vMaxY
                                && entry.cachedW == w && entry.cachedH == h;

                        if (canUseCache && (DISEGNA_CURVA_SU_CANVAS || displayedCanvas == null)) {
                            g2.setColor(COLOR_SIGNAL);
                            g2.setStroke(new BasicStroke(entry == activeFunc ? 2.5f : 1.5f,
                                    BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                            g2.draw(entry.cachedDisplayPath);

                            if (entry.cachedBandPath != null) {
                                g2.setColor(new Color(COLOR_SIGNAL.getRed(), COLOR_SIGNAL.getGreen(),
                                        COLOR_SIGNAL.getBlue(), 180));
                                g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
                                g2.draw(entry.cachedBandPath);
                            }
                        } else if (entry.cachedSignalPath != null && entry.cachedExpr == entry.expr
                                && Double.isFinite(entry.cachedOriginMinX) && entry.cachedW > 0) {
                            double oldScaleX = (double) entry.cachedW
                                    / (entry.cachedOriginMaxX - entry.cachedOriginMinX);
                            double oldScaleY = (double) entry.cachedH
                                    / (entry.cachedOriginMaxY - entry.cachedOriginMinY);

                            double scaleRatioX = scaleX / oldScaleX;
                            double scaleRatioY = scaleY / oldScaleY;
                            double transX = (entry.cachedOriginMinX - vMinX) * scaleX;
                            double transY = h - entry.cachedH * scaleRatioY - (entry.cachedOriginMinY - vMinY) * scaleY;
                            if (Math.abs(scaleRatioX - 1.0) < 1e-3 && Math.abs(scaleRatioY - 1.0) < 1e-3) {
                                transX = Math.rint(transX);
                                transY = Math.rint(transY);
                            }

                            AffineTransform atFast = new AffineTransform(scaleRatioX, 0.0, 0.0, scaleRatioY, transX,
                                    transY);

                            Graphics2D g2Curve = (Graphics2D) g2.create();
                            g2Curve.clipRect(0, 0, w, h);
                            g2Curve.setColor(COLOR_SIGNAL);
                            g2Curve.setStroke(new BasicStroke(entry == activeFunction ? 2.5f : 1.5f,
                                    BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

                            // FIX BUG SPESSORE: Trasformiamo la forma, non il Graphics2D.
                            // Così lo spessore del BasicStroke rimane costante a 2.5f indipendentemente
                            // dallo zoom.
                            java.awt.Shape transformedSignal = atFast.createTransformedShape(entry.cachedSignalPath);
                            g2Curve.draw(transformedSignal);

                            if (entry.cachedBandPath != null && scaleRatioX <= 1.25) {
                                g2Curve.setColor(new Color(COLOR_SIGNAL.getRed(), COLOR_SIGNAL.getGreen(),
                                        COLOR_SIGNAL.getBlue(), 180));
                                g2Curve.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
                                java.awt.Shape transformedBand = atFast.createTransformedShape(entry.cachedBandPath);
                                g2Curve.draw(transformedBand);
                            }
                            g2Curve.dispose();
                        }

                        if (!entry.engine.getImpulses().isEmpty()) {
                            for (SignalEngine.Impulse imp : entry.engine.getImpulses()) {
                                long kMin = isPeriodic && period > 0 ? (long) Math.floor((vMinX - imp.x) / period) : 0;
                                long kMax = isPeriodic && period > 0 ? (long) Math.ceil((vMaxX - imp.x) / period) : 0;
                                for (long k = kMin; k <= kMax; k++) {
                                    double x = imp.x + (isPeriodic ? k * period : 0.0);
                                    if (x < vMinX - 10 || x > vMaxX + 10)
                                        continue;
                                    double yBase = 0.0;
                                    if (entry.expr != null) {
                                        try {
                                            double yLeft = entry.expr.eval(x - 1e-5);
                                            double yRight = entry.expr.eval(x + 1e-5);
                                            if (Double.isFinite(yLeft) && Double.isFinite(yRight)) {
                                                yBase = (yLeft + yRight) / 2.0;
                                            } else if (Double.isFinite(yLeft)) {
                                                yBase = yLeft;
                                            } else if (Double.isFinite(yRight)) {
                                                yBase = yRight;
                                            } else {
                                                yBase = entry.expr.eval(x);
                                            }
                                            if (!Double.isFinite(yBase))
                                                yBase = 0.0;
                                        } catch (Exception ignore) {
                                        }
                                    }
                                    if (Math.abs(imp.weight) < 1e-12)
                                        continue;

                                    int px = (int) Math.round((x - vMinX) * scaleX);
                                    double pyBase = h - (yBase - vMinY) * scaleY;

                                    int dir = imp.weight >= 0 ? -1 : 1;
                                    double rawStemLen = Math.abs(imp.weight * scaleY);
                                    double stemLen = Math.max(6.0, rawStemLen);
                                    double pyTip = pyBase + dir * stemLen;

                                    double arrowHeight = Math.min(11.0, stemLen * 0.60);
                                    double arrowWidth = Math.min(5.5, Math.max(1.8, arrowHeight * 0.55));

                                    float strokeWidth = entry == activeFunc ? 2.2f : 1.6f;
                                    g2.setColor(COLOR_SIGNAL);
                                    g2.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND,
                                            BasicStroke.JOIN_ROUND));

                                    double stemEnd = pyTip - dir * (arrowHeight * 0.4);
                                    g2.drawLine(px, (int) Math.round(pyBase), px, (int) Math.round(stemEnd));

                                    double arrowBaseY = pyTip - dir * arrowHeight;
                                    Path2D.Double arrow = new Path2D.Double();
                                    arrow.moveTo(px, pyTip);
                                    arrow.lineTo(px - arrowWidth, arrowBaseY);
                                    arrow.lineTo(px + arrowWidth, arrowBaseY);
                                    arrow.closePath();
                                    g2.fill(arrow);
                                }
                            }
                        }
                    }
                }

                if (entry.displayMode == FunctionEntry.DisplayMode.NORMAL && selHarmonic != null
                        && entry.engine != null) {
                    if (entry.engine.getCurrentAccumulatedHarmonic() != selHarmonic) {
                        entry.engine.computeAccumulatedSignal(selHarmonic, minX, maxX);
                    }

                    double period = maxX - minX;
                    double pxPerPeriod = period * scaleX;

                    double curveAlphaRatio = Math.max(0.0, Math.min(1.0, (pxPerPeriod - 2.0) / 1.5));
                    double envAlphaRatio = 1.0 - curveAlphaRatio;

                    if (envAlphaRatio > 0.0) {
                        double yMin = entry.engine.getAccumulatedMin();
                        double yMax = entry.engine.getAccumulatedMax();
                        if (Double.isFinite(yMin) && Double.isFinite(yMax)) {
                            double pyTop = h - (yMax - vMinY) * scaleY;
                            double pyBot = h - (yMin - vMinY) * scaleY;
                            if (pyTop > pyBot) {
                                double tmp = pyTop;
                                pyTop = pyBot;
                                pyBot = tmp;
                            }

                            int fillAlpha = (int) Math.round(50 * envAlphaRatio);
                            g2.setColor(new Color(COLOR_ACCUMULATED.getRed(), COLOR_ACCUMULATED.getGreen(),
                                    COLOR_ACCUMULATED.getBlue(), fillAlpha));
                            g2.fillRect(0, (int) Math.round(pyTop), w, (int) Math.round(pyBot - pyTop));

                            int lineAlpha = (int) Math.round(150 * envAlphaRatio);
                            g2.setColor(new Color(COLOR_ACCUMULATED.getRed(), COLOR_ACCUMULATED.getGreen(),
                                    COLOR_ACCUMULATED.getBlue(), lineAlpha));
                            g2.setStroke(new BasicStroke(1.5f));
                            g2.drawLine(0, (int) Math.round(pyTop), w, (int) Math.round(pyTop));
                            g2.drawLine(0, (int) Math.round(pyBot), w, (int) Math.round(pyBot));
                        }
                    }

                    if (curveAlphaRatio > 0.0) {
                        int strokeAlpha = (int) Math.round(120 * curveAlphaRatio);

                        if (templateFracs == null) {
                            templateFracs = new double[2048];
                            templateY = new double[2048];
                        }

                        Path2D accPath = new Path2D.Double();
                        boolean firstAcc = true;

                        if (pxPerPeriod >= 60.0) {
                            double stepX = (vMaxX - vMinX) / w;
                            for (int i = 0; i <= w; i++) {
                                double x = vMinX + i * stepX;
                                double y = entry.engine.evalAccumulatedSignalAA(x, minX, maxX, scaleX, selHarmonic);
                                double px = i;
                                double py = h - (y - vMinY) * scaleY;
                                if (Double.isFinite(py) && Math.abs(y) <= 1e8) {
                                    boolean pIn = (py >= -h && py <= 2 * h);
                                    if (pIn) {
                                        if (firstAcc) {
                                            accPath.moveTo(px, Math.max(-h, Math.min(2 * h, py)));
                                            firstAcc = false;
                                        } else
                                            accPath.lineTo(px, Math.max(-h, Math.min(2 * h, py)));
                                    } else
                                        firstAcc = true;
                                } else
                                    firstAcc = true;
                            }
                        } else {
                            int templateLen = entry.engine.getPeriodTemplate(pxPerPeriod, templateFracs, templateY);
                            long kStart = (long) Math.floor((vMinX - minX) / period) - 1;
                            long kEnd = (long) Math.ceil((vMaxX - minX) / period) + 1;

                            for (long k = kStart; k <= kEnd; k++) {
                                double periodBaseX = minX + k * period;
                                for (int s = 0; s < templateLen; s++) {
                                    double x = periodBaseX + templateFracs[s] * period;
                                    double px = (x - vMinX) * scaleX;
                                    if (px < -20 || px > w + 20)
                                        continue;

                                    double py = h - (templateY[s] - vMinY) * scaleY;
                                    if (Double.isFinite(py) && Math.abs(templateY[s]) <= 1e8) {
                                        boolean pIn = (py >= -h && py <= 2 * h);
                                        if (pIn) {
                                            if (firstAcc) {
                                                accPath.moveTo(px, Math.max(-h, Math.min(2 * h, py)));
                                                firstAcc = false;
                                            } else
                                                accPath.lineTo(px, Math.max(-h, Math.min(2 * h, py)));
                                        } else
                                            firstAcc = true;
                                    } else
                                        firstAcc = true;
                                }
                            }
                        }

                        g2.setColor(new Color(COLOR_ACCUMULATED.getRed(), COLOR_ACCUMULATED.getGreen(),
                                COLOR_ACCUMULATED.getBlue(), strokeAlpha));
                        g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND,
                                BasicStroke.JOIN_ROUND));
                        g2.draw(accPath);
                    }
                }
            }

            Font tickFont = new Font("Consolas", Font.BOLD, 12);
            g2.setFont(tickFont);
            FontMetrics fm = g2.getFontMetrics();
            Color tickTextColor = new Color(180, 188, 202);
            Color pillBgColor = new Color(24, 27, 34);

            int labelY;
            if (zeroY >= 20 && zeroY <= h - 25)
                labelY = zeroY + 16;
            else if (zeroY > h - 25)
                labelY = h - 8;
            else
                labelY = 20;

            for (double x = startXGrid; x <= vMaxX; x += gridStepX) {
                if (Math.abs(x) < gridStepX * 1e-4)
                    continue;
                int px = (int) Math.round((x - vMinX) * scaleX);
                if (px < sw + 10 || px > w - 10)
                    continue;

                String text = formatAxisLabel(x, gridStepX);
                int textW = fm.stringWidth(text);
                int textX = px - textW / 2;
                if (textX < sw + 5)
                    textX = sw + 5;

                g2.setColor(pillBgColor);
                g2.fillRoundRect(textX - 3, labelY - fm.getAscent() - 1, textW + 6, fm.getHeight() + 2, 4, 4);
                g2.setColor(tickTextColor);
                g2.drawString(text, textX, labelY);
            }

            int minVisX = sw + 15;
            int maxVisX = (rvis ? w - rsw : w) - 15;

            for (double y = startYGrid; y <= vMaxY; y += gridStepY) {
                if (Math.abs(y) < gridStepY * 1e-4)
                    continue;
                int py = (int) Math.round(h - (y - vMinY) * scaleY);
                if (py < 20 || py > h - 20)
                    continue;

                String text = formatAxisLabel(y, gridStepY);
                int textW = fm.stringWidth(text);
                int textY = py + fm.getAscent() / 2 - 1;

                int textX;
                if (zeroX >= minVisX + textW + 8 && zeroX <= maxVisX)
                    textX = zeroX - textW - 6;
                else if (zeroX < minVisX + textW + 8)
                    textX = minVisX + 4;
                else
                    textX = maxVisX - textW - 4;

                g2.setColor(pillBgColor);
                g2.fillRoundRect(textX - 3, textY - fm.getAscent() - 1, textW + 6, fm.getHeight() + 2, 4, 4);
                g2.setColor(tickTextColor);
                g2.drawString(text, textX, textY);
            }

            if (zeroX >= sw + 15 && zeroX <= w - 20 && zeroY >= 20 && zeroY <= h - 20) {
                g2.setColor(pillBgColor);
                g2.fillRoundRect(zeroX - 14, zeroY + 4, 13, fm.getHeight(), 4, 4);
                g2.setColor(tickTextColor);
                g2.drawString("0", zeroX - 11, zeroY + fm.getAscent() + 4);
            }
        }

        private void repaintHoverStrips() {
            if (!isCanvasExact()) {
                repaint();
                return;
            }
            if (hoverDrawnAt != null) {
                for (Rectangle r : hoverStrips(hoverDrawnAt)) {
                    repaint(r.x, r.y, r.width, r.height);
                }
            }
            if (hoverOverlayVisible(getWidth(), getHeight())) {
                for (Rectangle r : hoverStrips(hoverPt)) {
                    repaint(r.x, r.y, r.width, r.height);
                }
            }
        }

        private boolean hoverOverlayVisible(int w, int h) {
            if (hoverPt == null || w <= 0 || h <= 0) {
                return false;
            }
            if (hoverPt.x < sidebarWidth) {
                return false;
            }
            int rightLimit = rightScrollPane.isVisible() ? w - rightSidebarWidth : w;
            return hoverPt.x <= rightLimit;
        }

        private Rectangle[] hoverStrips(Point p) {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0 || p == null) {
                return new Rectangle[] { new Rectangle(0, 0, Math.max(1, w), Math.max(1, h)) };
            }
            int margin = 11;
            int top = Math.max(0, p.y - margin);
            int bottom = Math.min(h, p.y + margin + 1);
            int left = Math.max(0, p.x - margin);
            int right = Math.min(w, p.x + margin + 1);
            return new Rectangle[] {
                    new Rectangle(0, top, w, Math.max(1, bottom - top)),
                    new Rectangle(left, 0, Math.max(1, right - left), h) };
        }

        private boolean isCanvasExact() {
            int w = getWidth();
            int h = getHeight();
            if (displayedCanvas == null || w <= 0 || h <= 0) {
                return false;
            }
            long currentHash = computeStaticDataHash(visibleFunctions, selectedHarmonic, activeFunction);
            return displayedW == w && displayedH == h
                    && displayedSidebarW == sidebarWidth
                    && displayedRightSidebarW == rightSidebarWidth
                    && displayedRightVis == rightScrollPane.isVisible()
                    && displayedLockIso == lockIsometric
                    && viewApproxEqual(displayedViewMinX, viewMinX)
                    && viewApproxEqual(displayedViewMaxX, viewMaxX)
                    && viewApproxEqual(displayedViewMinY, viewMinY)
                    && viewApproxEqual(displayedViewMaxY, viewMaxY)
                    && displayedDataHash == currentHash;
        }

        private void paintHoverOverlay(Graphics2D g2, int w, int h) {
            if (hoverPt == null)
                return;
            if (hoverPt.x < sidebarWidth)
                return;
            int rightLimit = rightScrollPane.isVisible() ? w - rightSidebarWidth : w;
            if (hoverPt.x > rightLimit)
                return;

            int cx = hoverPt.x;
            int cy = hoverPt.y;

            double xRange = viewMaxX - viewMinX;
            double yRange = viewMaxY - viewMinY;
            if (xRange <= 0 || yRange <= 0)
                return;
            double scaleX = w / xRange;
            double scaleY = h / yRange;

            double mathX = viewMinX + cx / scaleX;
            double mathY = viewMinY + (h - cy) / scaleY;

            Object aaPrec = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g2.setColor(new Color(97, 175, 239, 120));
            Stroke dashed = new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                    10.0f, new float[] { 4.0f, 4.0f }, 0.0f);
            g2.setStroke(dashed);
            g2.drawLine(sidebarWidth, cy, rightLimit, cy);
            g2.drawLine(cx, 0, cx, h);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    aaPrec == null ? RenderingHints.VALUE_ANTIALIAS_ON : aaPrec);

            g2.setStroke(new BasicStroke(1.5f));
            g2.setColor(new Color(97, 175, 239));
            g2.fillOval(cx - 3, cy - 3, 6, 6);
            g2.drawOval(cx - 7, cy - 7, 14, 14);

            int visRight = rightLimit;
            g2.setFont(new Font("Consolas", Font.BOLD, 12));
            FontMetrics fmHud = g2.getFontMetrics();

            String strX = formatHUDCoord(mathX);
            String strY = formatHUDCoord(mathY);
            int labelXW = fmHud.stringWidth("X: ");
            int valXW = fmHud.stringWidth(strX);
            int labelYW = fmHud.stringWidth("Y: ");
            int valYW = fmHud.stringWidth(strY);

            int pad = 10;
            int colGap = 16;
            int hudW = Math.max(210, pad + labelXW + valXW + colGap + labelYW + valYW + pad);
            int hudH = 30;
            int hudX = visRight - hudW - 16;
            int hudY = 52;

            g2.setColor(new Color(24, 27, 34));
            g2.fillRoundRect(hudX, hudY, hudW, hudH, 8, 8);
            g2.setColor(new Color(60, 68, 80));
            g2.setStroke(new BasicStroke(1.0f));
            g2.drawRoundRect(hudX, hudY, hudW, hudH, 8, 8);

            int textBaseY = hudY + 19;

            int curHudDrawX = hudX + pad;
            g2.setColor(new Color(150, 155, 165));
            g2.drawString("X:", curHudDrawX, textBaseY);
            curHudDrawX += labelXW;
            g2.setColor(new Color(86, 182, 194));
            g2.drawString(strX, curHudDrawX, textBaseY);
            curHudDrawX += valXW + colGap;

            g2.setColor(new Color(150, 155, 165));
            g2.drawString("Y:", curHudDrawX, textBaseY);
            curHudDrawX += labelYW;
            g2.setColor(new Color(229, 192, 123));
            g2.drawString(strY, curHudDrawX, textBaseY);
        }

        public PathData buildContinuousSignalPath(MathParser.Expression expr, boolean isPeriodic,
                double minX, double period, double viewMinX, double viewMaxX,
                double viewMinY, double viewMaxY, int w, int h, List<Double> featureX) {
            return buildContinuousSignalPath(expr, isPeriodic, minX, period, viewMinX, viewMaxX,
                    viewMinY, viewMaxY, w, h, featureX, null);
        }

        public PathData buildContinuousSignalPath(MathParser.Expression expr, boolean isPeriodic,
                double minX, double period, double viewMinX, double viewMaxX,
                double viewMinY, double viewMaxY, int w, int h, List<Double> featureX,
                java.util.function.BooleanSupplier isCancelled) {
            if (isCancelled != null && isCancelled.getAsBoolean())
                return null;
            PathData pd = new PathData();
            pd.signalPath = new Path2D.Double();
            pd.bandPath = new Path2D.Double();

            int marginPx = Math.max(300, w / 2);
            int evalW = w + 2 * marginPx;
            double stepX = (viewMaxX - viewMinX) / w;
            double evalMinX = viewMinX - marginPx * stepX;
            double evalMaxX = viewMaxX + marginPx * stepX;
            double scaleX = (double) w / (viewMaxX - viewMinX);
            double scaleY = (double) h / (viewMaxY - viewMinY);
            int numBasePts = evalW + 1;

            List<Double> candFeats = new ArrayList<>();
            if (featureX != null && !featureX.isEmpty()) {
                if (isPeriodic && period > 0) {
                    long kStart = (long) Math.floor((evalMinX - maxX) / period);
                    long kEnd = (long) Math.ceil((evalMaxX - minX) / period);
                    for (long k = kStart; k <= kEnd; k++) {
                        double pBase = k * period;
                        for (double fx : featureX) {
                            double cx = pBase + fx;
                            if (cx >= evalMinX && cx <= evalMaxX)
                                candFeats.add(cx);
                        }
                    }
                } else {
                    for (double fx : featureX) {
                        if (fx >= evalMinX && fx <= evalMaxX)
                            candFeats.add(fx);
                    }
                }
            }

            int candCount = candFeats.size();
            double[] candArr = new double[candCount];
            for (int k = 0; k < candCount; k++)
                candArr[k] = candFeats.get(k);
            if (candCount > 1)
                java.util.Arrays.sort(candArr);

            int totalPts = numBasePts + candCount;
            double[] cleanX = new double[totalPts];
            double[] cleanPx = new double[totalPts];
            int cleanCount = 0;
            int idxBase = 0;
            int idxCand = 0;
            while (idxBase < numBasePts || idxCand < candCount) {
                double nextX;
                if (idxBase < numBasePts
                        && (idxCand >= candCount || (evalMinX + idxBase * stepX) <= candArr[idxCand])) {
                    nextX = evalMinX + idxBase * stepX;
                    idxBase++;
                } else {
                    nextX = candArr[idxCand++];
                }
                double px = (nextX - viewMinX) * scaleX;
                if (cleanCount == 0 || (px - cleanPx[cleanCount - 1]) >= 0.5) {
                    cleanX[cleanCount] = nextX;
                    cleanPx[cleanCount] = px;
                    cleanCount++;
                }
            }

            double[] cleanY = new double[cleanCount];
            double[] cleanPy = new double[cleanCount];
            final int finalCleanCount = cleanCount;

            java.util.concurrent.atomic.AtomicBoolean cancelFlag = new java.util.concurrent.atomic.AtomicBoolean(false);
            int numWorkers = Math.max(1, Math.min(8, Runtime.getRuntime().availableProcessors()));
            int chunkSize = (finalCleanCount + numWorkers - 1) / numWorkers;
            List<java.util.concurrent.Callable<Void>> tasks = new ArrayList<>(numWorkers);

            for (int wIdx = 0; wIdx < numWorkers; wIdx++) {
                final int startIdx = wIdx * chunkSize;
                final int endIdx = Math.min(finalCleanCount, startIdx + chunkSize);
                if (startIdx >= endIdx)
                    break;
                tasks.add(() -> {
                    Thread.interrupted();
                    for (int k = startIdx; k < endIdx; k++) {
                        if ((k & 15) == 0) {
                            if (cancelFlag.get() || Thread.currentThread().isInterrupted()
                                    || (isCancelled != null && isCancelled.getAsBoolean())) {
                                cancelFlag.set(true);
                                return null;
                            }
                        }
                        double y = safeEvalGraph(expr, cleanX[k], isPeriodic, minX, period);
                        cleanY[k] = y;
                        cleanPy[k] = (Double.isFinite(y)) ? (h - (y - viewMinY) * scaleY) : Double.NaN;
                    }
                    return null;
                });
            }

            try {
                List<java.util.concurrent.Future<Void>> futures = new ArrayList<>();
                for (java.util.concurrent.Callable<Void> task : tasks) {
                    futures.add(SignalEngine.EVAL_POOL.submit(task));
                }
                for (java.util.concurrent.Future<Void> fut : futures) {
                    try {
                        fut.get();
                    } catch (Exception ignore) {
                    }
                }
            } catch (Exception ignore) {
            }

            if (cancelFlag.get() || (isCancelled != null && isCancelled.getAsBoolean())
                    || Thread.currentThread().isInterrupted()) {
                return null;
            }

            double[] lastPt = new double[] { Double.NaN, Double.NaN };
            boolean[] firstFlag = new boolean[] { true };

            if (finalCleanCount > 0) {
                addPointToPath(pd.signalPath, cleanPx[0], cleanPy[0], lastPt, firstFlag, h);
            }

            double boundTop = -Math.max(500.0, h);
            double boundBot = h + Math.max(500.0, h);

            for (int k = 0; k < finalCleanCount - 1; k++) {
                if ((k & 63) == 0 && (isCancelled != null && isCancelled.getAsBoolean()))
                    return null;
                double p0x = cleanX[k];
                double p0y = cleanY[k];
                double p0px = cleanPx[k];
                double p0py = cleanPy[k];

                double p1x = cleanX[k + 1];
                double p1y = cleanY[k + 1];
                double p1px = cleanPx[k + 1];
                double p1py = cleanPy[k + 1];

                boolean p0Finite = Double.isFinite(p0py);
                boolean p1Finite = Double.isFinite(p1py);

                boolean checkSubdiv = false;
                if (p0Finite != p1Finite) {
                    checkSubdiv = true;
                } else if (p0Finite && p1Finite) {
                    if ((p0py < boundTop && p1py < boundTop) || (p0py > boundBot && p1py > boundBot)) {
                        checkSubdiv = false;
                    } else if (Math.abs(p1py - p0py) > 2.0) {
                        checkSubdiv = true;
                    }
                }

                if (checkSubdiv) {
                    adaptiveSubdivide(pd.signalPath, expr, isPeriodic, minX, period,
                            viewMinX, viewMinY, scaleX, scaleY, h,
                            p0x, p0y, p0px, p0py, p1x, p1y, p1px, p1py, lastPt, firstFlag, 0);
                } else {
                    addPointToPath(pd.signalPath, p1px, p1py, lastPt, firstFlag, h);
                }
            }

            pd.evalMinX = evalMinX;
            pd.evalMaxX = evalMaxX;
            pd.evalMinY = viewMinY;
            pd.evalMaxY = viewMaxY;
            pd.originMinX = viewMinX;
            pd.originMaxX = viewMaxX;
            pd.originMinY = viewMinY;
            pd.originMaxY = viewMaxY;
            pd.originW = w;
            pd.originH = h;
            pd.evalW = evalW;
            pd.evalH = h;
            pd.displayPath = pd.signalPath;

            return pd;
        }

        public void triggerAsyncPanRefine() {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0)
                return;

            for (FunctionEntry entry : visibleFunctions) {
                if (!entry.isValid || entry.expr == null || entry.engine == null)
                    continue;
                if (entry.signalType == SignalType.DISCRETE_APERIODIC
                        || entry.signalType == SignalType.DISCRETE_PERIODIC
                        || entry.signalType == SignalType.DISCRETE_RANDOM
                        || entry.isDiscretized) {
                    continue;
                }

                if (entry.isImplicit) {
                    if (entry.cachedSignalPath != null
                            && entry.cachedExpr == entry.expr
                            && Math.abs(entry.cachedOriginMinX - viewMinX) < 1e-4
                            && Math.abs(entry.cachedOriginMaxX - viewMaxX) < 1e-4
                            && Math.abs(entry.cachedViewMinY - viewMinY) < 1e-4
                            && Math.abs(entry.cachedViewMaxY - viewMaxY) < 1e-4
                            && entry.cachedW == w && entry.cachedH == h) {
                        continue;
                    }
                    if (entry.panRefineFuture != null && !entry.panRefineFuture.isDone()) {
                        entry.panRefineFuture.cancel(true);
                    }
                    final long currentPanVersion = ++entry.panRefineVersion;
                    final MathParser.Expression expr = entry.expr;
                    final double curMinX = viewMinX;
                    final double curMaxX = viewMaxX;
                    final double curMinY = viewMinY;
                    final double curMaxY = viewMaxY;
                    final int curW = w;
                    final int curH = h;
                    entry.panRefineFuture = executor.submit(() -> {
                        try {
                            if (Thread.currentThread().isInterrupted() || entry.panRefineVersion != currentPanVersion)
                                return;
                            Path2D path = MathMarchingSquares.render(expr, curMinX, curMaxX, curMinY, curMaxY, curW,
                                    curH, 3);
                            if (path == null || Thread.currentThread().isInterrupted()
                                    || entry.panRefineVersion != currentPanVersion)
                                return;
                            SwingUtilities.invokeLater(() -> {
                                if (entry.panRefineVersion != currentPanVersion || entry.expr != expr)
                                    return;
                                entry.cachedSignalPath = path;
                                entry.cachedViewMinX = curMinX;
                                entry.cachedViewMaxX = curMaxX;
                                entry.cachedViewMinY = curMinY;
                                entry.cachedViewMaxY = curMaxY;
                                entry.cachedOriginMinX = curMinX;
                                entry.cachedOriginMaxX = curMaxX;
                                entry.cachedW = curW;
                                entry.cachedH = curH;
                                if (!viewApproxEqual(displayedViewMinX, viewMinX)
                                        || !viewApproxEqual(displayedViewMaxX, viewMaxX)
                                        || !viewApproxEqual(displayedViewMinY, viewMinY)
                                        || !viewApproxEqual(displayedViewMaxY, viewMaxY)) {
                                    repaint();
                                }
                            });
                        } catch (Exception ignore) {
                        }
                    });
                    continue;
                }

                if (entry.cachedDisplayPath != null
                        && entry.cachedExpr == entry.expr
                        && Math.abs(entry.cachedOriginMinX - viewMinX) < 1e-4
                        && Math.abs(entry.cachedOriginMaxX - viewMaxX) < 1e-4
                        && Math.abs(entry.cachedViewMinY - viewMinY) < 1e-4
                        && Math.abs(entry.cachedViewMaxY - viewMaxY) < 1e-4
                        && entry.cachedH == h) {
                    continue;
                }

                if (entry.panRefineFuture != null && !entry.panRefineFuture.isDone()) {
                    entry.panRefineFuture.cancel(true);
                }

                final long currentPanVersion = ++entry.panRefineVersion;
                final MathParser.Expression expr = entry.expr;
                final boolean isPeriodic = (entry.signalType == SignalType.CONTINUOUS_PERIODIC);
                final double minXVal = minX;
                final double periodVal = maxX - minX;
                final double curViewMinX = viewMinX;
                final double curViewMaxX = viewMaxX;
                final double curViewMinY = viewMinY;
                final double curViewMaxY = viewMaxY;
                final int curW = w;
                final int curH = h;
                final List<Double> feats = entry.featureX;

                entry.panRefineFuture = executor.submit(() -> {
                    Thread.interrupted();
                    try {
                        if (Thread.currentThread().isInterrupted() || entry.panRefineVersion != currentPanVersion)
                            return;
                        PathData pd = buildContinuousSignalPath(expr, isPeriodic, minXVal, periodVal,
                                curViewMinX, curViewMaxX, curViewMinY, curViewMaxY, curW, curH, feats,
                                () -> entry.panRefineVersion != currentPanVersion
                                        || Thread.currentThread().isInterrupted());
                        if (pd == null || Thread.currentThread().isInterrupted()
                                || entry.panRefineVersion != currentPanVersion)
                            return;
                        SwingUtilities.invokeLater(() -> {
                            if (entry.panRefineVersion != currentPanVersion || entry.expr != expr)
                                return;
                            entry.cachedSignalPath = pd.signalPath;
                            entry.cachedBandPath = pd.bandPath;
                            entry.cachedDisplayPath = pd.displayPath;
                            entry.cachedExpr = expr;
                            entry.cachedViewMinX = pd.evalMinX;
                            entry.cachedViewMaxX = pd.evalMaxX;
                            entry.cachedViewMinY = pd.evalMinY;
                            entry.cachedViewMaxY = pd.evalMaxY;
                            entry.cachedOriginMinX = pd.originMinX;
                            entry.cachedOriginMaxX = pd.originMaxX;
                            entry.cachedOriginMinY = pd.originMinY;
                            entry.cachedOriginMaxY = pd.originMaxY;
                            entry.cachedEvalMinX = pd.evalMinX;
                            entry.cachedEvalMaxX = pd.evalMaxX;
                            entry.cachedW = pd.originW;
                            entry.cachedH = pd.originH;
                            repaint();
                        });
                    } catch (Exception ignore) {
                    }
                });
            }
        }

        public void enforceIsometric(int w, int h) {
            if (!lockIsometric || w <= 0 || h <= 0)
                return;
            if (graphScale <= 0 || Double.isNaN(graphScale)) {
                graphScale = h / (viewMaxY - viewMinY);
                if (graphScale <= 0 || Double.isNaN(graphScale))
                    graphScale = 100.0;
            }
            double cx = (viewMinX + viewMaxX) / 2.0;
            double cy = (viewMinY + viewMaxY) / 2.0;
            double nMinX = cx - (w / 2.0) / graphScale;
            double nMaxX = cx + (w / 2.0) / graphScale;
            double nMinY = cy - (h / 2.0) / graphScale;
            double nMaxY = cy + (h / 2.0) / graphScale;
            if (viewApproxEqual(viewMinX, nMinX) && viewApproxEqual(viewMaxX, nMaxX)
                    && viewApproxEqual(viewMinY, nMinY) && viewApproxEqual(viewMaxY, nMaxY)) {
                return;
            }
            viewMinX = nMinX;
            viewMaxX = nMaxX;
            viewMinY = nMinY;
            viewMaxY = nMaxY;
        }

        private double safeEvalGraph(MathParser.Expression expr, double rawX, boolean isPeriodic, double minX,
                double period) {
            if (expr == null)
                return Double.NaN;
            double evalX = rawX;
            if (isPeriodic && period > 0) {
                evalX = ((rawX - minX) % period + period) % period + minX;
            }
            try {
                double y = expr.eval(evalX);
                if (!Double.isFinite(y))
                    return Double.NaN;
                return y;
            } catch (Exception e) {
                return Double.NaN;
            }
        }

        private static double clampCoord(double val, double min, double max) {
            return Math.max(min, Math.min(max, val));
        }

        private void connectSegment(Path2D signalPath, double p0x, double p0y, double p1x, double p1y, int h) {
            double boundTop = -Math.max(500.0, h);
            double boundBot = h + Math.max(500.0, h);
            boolean p0In = (p0y >= boundTop && p0y <= boundBot);
            boolean p1In = (p1y >= boundTop && p1y <= boundBot);

            if (p0In && p1In) {
                signalPath.lineTo(p1x, p1y);
            } else if (p0In && !p1In) {
                double borderPy = (p1y < boundTop) ? boundTop : boundBot;
                double t = (borderPy - p0y) / (p1y - p0y);
                double exitPx = p0x + t * (p1x - p0x);
                signalPath.lineTo(exitPx, borderPy);
                signalPath.moveTo(p1x, clampCoord(p1y, boundTop, boundBot));
            } else if (!p0In && p1In) {
                double borderPy = (p0y < boundTop) ? boundTop : boundBot;
                double t = (borderPy - p0y) / (p1y - p0y);
                double enterPx = p0x + t * (p1x - p0x);
                signalPath.moveTo(enterPx, borderPy);
                signalPath.lineTo(p1x, p1y);
            } else {
                if ((p0y < boundTop && p1y > boundBot) || (p0y > boundBot && p1y < boundTop)) {
                    double tTop = (boundTop - p0y) / (p1y - p0y);
                    double tBot = (boundBot - p0y) / (p1y - p0y);
                    double xTop = p0x + tTop * (p1x - p0x);
                    double xBot = p0x + tBot * (p1x - p0x);
                    if (p0y < p1y) {
                        signalPath.moveTo(xTop, boundTop);
                        signalPath.lineTo(xBot, boundBot);
                    } else {
                        signalPath.moveTo(xBot, boundBot);
                        signalPath.lineTo(xTop, boundTop);
                    }
                } else {
                    signalPath.moveTo(p1x, clampCoord(p1y, boundTop, boundBot));
                }
            }
        }

        private void adaptiveSubdivide(Path2D signalPath, MathParser.Expression expr,
                boolean isPeriodic, double minX, double period,
                double viewMinX, double viewMinY, double scaleX, double scaleY, int h,
                double x0, double y0, double px0, double py0,
                double x1, double y1, double px1, double py1,
                double[] lastPt, boolean[] firstFlag, int depth) {
            double boundTop = -Math.max(500.0, h);
            double boundBot = h + Math.max(500.0, h);
            if (Double.isFinite(py0) && Double.isFinite(py1)) {
                if ((py0 < boundTop && py1 < boundTop) || (py0 > boundBot && py1 > boundBot)) {
                    addPointToPath(signalPath, px1, py1, lastPt, firstFlag, h);
                    return;
                }
            }

            if (depth >= 3 || Math.abs(px1 - px0) < 0.1) {
                addPointToPath(signalPath, px1, py1, lastPt, firstFlag, h);
                return;
            }

            double xm = 0.5 * (x0 + x1);
            double ym = safeEvalGraph(expr, xm, isPeriodic, minX, period);
            double pxm = 0.5 * (px0 + px1);
            double pym = Double.isFinite(ym) ? (h - (ym - viewMinY) * scaleY) : Double.NaN;

            boolean mFinite = Double.isFinite(pym);
            boolean p0Finite = Double.isFinite(py0);
            boolean p1Finite = Double.isFinite(py1);

            boolean needSubdiv = false;
            if (mFinite != p0Finite || mFinite != p1Finite) {
                needSubdiv = true;
            } else if (mFinite && p0Finite && p1Finite) {
                double pyLin = 0.5 * (py0 + py1);
                double diff = Math.abs(pym - pyLin);
                if (diff > 1.0)
                    needSubdiv = true;
            }

            if (needSubdiv) {
                adaptiveSubdivide(signalPath, expr, isPeriodic, minX, period, viewMinX, viewMinY, scaleX, scaleY, h,
                        x0, y0, px0, py0, xm, ym, pxm, pym, lastPt, firstFlag, depth + 1);
                adaptiveSubdivide(signalPath, expr, isPeriodic, minX, period, viewMinX, viewMinY, scaleX, scaleY, h,
                        xm, ym, pxm, pym, x1, y1, px1, py1, lastPt, firstFlag, depth + 1);
            } else {
                addPointToPath(signalPath, px1, py1, lastPt, firstFlag, h);
            }
        }

        private void addPointToPath(Path2D signalPath, double px, double py, double[] lastPt, boolean[] firstFlag,
                int h) {
            double boundTop = -Math.max(500.0, h);
            double boundBot = h + Math.max(500.0, h);
            if (Double.isFinite(py)) {
                if (firstFlag[0]) {
                    signalPath.moveTo(px, clampCoord(py, boundTop, boundBot));
                    firstFlag[0] = false;
                } else {
                    connectSegment(signalPath, lastPt[0], lastPt[1], px, py, h);
                }
                lastPt[0] = px;
                lastPt[1] = py;
            } else {
                firstFlag[0] = true;
                lastPt[0] = px;
                lastPt[1] = Double.NaN;
            }
        }

        private static Path2D createClippedTransformedPath(Path2D src, AffineTransform at, int w, int h) {
            if (src == null)
                return null;
            Path2D.Double dest = new Path2D.Double();
            double xmin = -w;
            double xmax = 2.0 * w;
            double ymin = -h;
            double ymax = 2.0 * h;

            java.awt.geom.PathIterator it = src.getPathIterator(at);
            double[] coords = new double[6];
            double lastX = Double.NaN;
            double lastY = Double.NaN;
            double currentDestX = Double.NaN;
            double currentDestY = Double.NaN;

            while (!it.isDone()) {
                int type = it.currentSegment(coords);
                if (type == java.awt.geom.PathIterator.SEG_MOVETO) {
                    lastX = coords[0];
                    lastY = coords[1];
                } else if (type == java.awt.geom.PathIterator.SEG_LINETO) {
                    double x0 = lastX;
                    double y0 = lastY;
                    double x1 = coords[0];
                    double y1 = coords[1];
                    lastX = x1;
                    lastY = y1;

                    if (!Double.isFinite(x0) || !Double.isFinite(y0) || !Double.isFinite(x1) || !Double.isFinite(y1)) {
                        continue;
                    }

                    double dx = x1 - x0;
                    double dy = y1 - y0;
                    double[] p = { -dx, dx, -dy, dy };
                    double[] q = { x0 - xmin, xmax - x0, y0 - ymin, ymax - y0 };

                    double u1 = 0.0;
                    double u2 = 1.0;
                    boolean accept = true;

                    for (int i = 0; i < 4; i++) {
                        if (p[i] == 0) {
                            if (q[i] < 0) {
                                accept = false;
                                break;
                            }
                        } else {
                            double t = q[i] / p[i];
                            if (p[i] < 0) {
                                if (t > u2) {
                                    accept = false;
                                    break;
                                }
                                if (t > u1)
                                    u1 = t;
                            } else {
                                if (t < u1) {
                                    accept = false;
                                    break;
                                }
                                if (t < u2)
                                    u2 = t;
                            }
                        }
                    }

                    if (accept && u1 <= u2) {
                        double cx0 = x0 + u1 * dx;
                        double cy0 = y0 + u1 * dy;
                        double cx1 = x0 + u2 * dx;
                        double cy1 = y0 + u2 * dy;

                        if (Double.isNaN(currentDestX) || Math.hypot(cx0 - currentDestX, cy0 - currentDestY) > 0.5) {
                            dest.moveTo(cx0, cy0);
                        }
                        dest.lineTo(cx1, cy1);
                        currentDestX = cx1;
                        currentDestY = cy1;
                    }
                }
                it.next();
            }
            return dest;
        }
    }

    private JMenuItem createStyledMenuItem(String text, java.awt.event.ActionListener action) {
        JMenuItem item = new JMenuItem(text);
        item.setBackground(COLOR_SIDEBAR);
        item.setForeground(COLOR_FG);
        item.setFont(new Font("Consolas", Font.PLAIN, 14));
        item.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        item.setUI(new javax.swing.plaf.basic.BasicMenuItemUI() {
            @Override
            protected void paintBackground(Graphics g, JMenuItem menuItem, Color bgColor) {
                ButtonModel model = menuItem.getModel();
                g.setColor(model.isArmed() ? COLOR_GRID : COLOR_SIDEBAR);
                g.fillRect(0, 0, menuItem.getWidth(), menuItem.getHeight());
            }

            @Override
            protected void paintText(Graphics g, JMenuItem menuItem, Rectangle textRect, String str) {
                Graphics2D g2 = (Graphics2D) g;
                java.util.Map<?, ?> hints = (java.util.Map<?, ?>) Toolkit.getDefaultToolkit()
                        .getDesktopProperty("awt.font.desktophints");
                if (hints != null)
                    g2.addRenderingHints(hints);
                else
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setFont(menuItem.getFont());
                g2.setColor(COLOR_FG);
                FontMetrics fm = g2.getFontMetrics();
                int y = textRect.y + fm.getAscent();
                g2.drawString(str, textRect.x, y);
            }
        });
        if (action != null)
            item.addActionListener(action);
        return item;
    }

    private void showCalculatorMenu(FunctionEntry entry, MouseEvent e) {
        JPopupMenu popup = new JPopupMenu();
        popup.setBackground(COLOR_SIDEBAR);
        popup.setBorder(BorderFactory.createLineBorder(COLOR_GRID));

        popup.add(createStyledMenuItem("Cut", ev -> entry.textField.cut()));
        popup.add(createStyledMenuItem("Copy", ev -> entry.textField.copy()));
        popup.add(createStyledMenuItem("Paste", ev -> entry.textField.paste()));
        popup.add(createStyledMenuItem("Select All", ev -> entry.textField.selectAll()));
        popup.addSeparator();
        popup.add(createStyledMenuItem("Zoom In (Ctrl +)", ev -> entry.textField.zoomIn()));
        popup.add(createStyledMenuItem("Zoom Out (Ctrl -)", ev -> entry.textField.zoomOut()));
        popup.add(createStyledMenuItem("Reset Zoom (Ctrl 0)", ev -> entry.textField.resetZoom()));
        popup.addSeparator();

        JMenuItem itemCalc = createStyledMenuItem("Calculator", ev -> {
            activeFunction = entry;
            visibleFunctions.clear();
            visibleFunctions.add(entry);
            updateSelectionUI();
            entry.textField.requestFocusInWindow();
            calculatorPanel.setVisible(true);
        });
        popup.add(itemCalc);

        popup.show(e.getComponent(), e.getX(), e.getY());
    }

    public CalculatorPanel getCalculatorPanel() {
        return calculatorPanel;
    }

    public class CalculatorPanel extends JPanel {
        public int getCurrentSection() {
            return currentSection;
        }

        public String getSectionTitle() {
            return lblTitle != null ? lblTitle.getText() : "";
        }

        public JButton getBtnPrev() {
            return btnPrev;
        }

        public JButton getBtnNext() {
            return btnNext;
        }

        public JButton[] getGridButtons() {
            return gridButtons;
        }

        private int currentSection = 1;
        private boolean capsLatin = false;
        private boolean capsGreek = false;
        private boolean capsRussian = false;

        private final JLabel lblTitle;
        private final JButton btnPrev;
        private final JButton btnNext;
        private final JButton[] gridButtons = new JButton[36];

        private final String[] SECTION_NAMES = {
                "1/8: Basic",
                "2/8: Functions (fx)",
                "3/8: Latin Letters",
                "4/8: Greek Letters",
                "5/8: Russian Letters",
                "6/8: Hebrew Alphabet",
                "7/8: Physics & Thermo",
                "8/8: Signal Theory"
        };

        private static class BtnSpec {
            final String label;
            final String cmd;

            BtnSpec(String label, String cmd) {
                this.label = label;
                this.cmd = cmd;
            }
        }

        public CalculatorPanel() {
            setLayout(new BorderLayout());
            setBackground(COLOR_SIDEBAR);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(COLOR_GRID, 2),
                    BorderFactory.createEmptyBorder(3, 3, 3, 3)));

            JPanel header = new JPanel(new BorderLayout(5, 0));
            header.setBackground(new Color(36, 40, 50));
            header.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

            PanelDragHandler dragHandler = new PanelDragHandler(this);
            header.addMouseListener(dragHandler);
            header.addMouseMotionListener(dragHandler);

            btnPrev = createArrowButton(true, () -> {
                int prev = (currentSection == 1) ? 8 : currentSection - 1;
                setSection(prev);
            });

            btnNext = createArrowButton(false, () -> {
                int next = (currentSection == 8) ? 1 : currentSection + 1;
                setSection(next);
            });

            JButton btnClose = createCloseButton(() -> setVisible(false));

            lblTitle = new JLabel(SECTION_NAMES[0], SwingConstants.CENTER);
            lblTitle.setForeground(COLOR_SIGNAL);
            lblTitle.setFont(new Font("Consolas", Font.BOLD, 13));

            JPanel leftControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
            leftControls.setOpaque(false);
            leftControls.add(btnPrev);

            JPanel rightControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
            rightControls.setOpaque(false);
            rightControls.add(btnNext);
            rightControls.add(btnClose);

            header.add(leftControls, BorderLayout.WEST);
            header.add(lblTitle, BorderLayout.CENTER);
            header.add(rightControls, BorderLayout.EAST);
            add(header, BorderLayout.NORTH);

            JPanel grid = new JPanel(new GridLayout(6, 6, 4, 4));
            grid.setBackground(COLOR_SIDEBAR);
            grid.setBorder(BorderFactory.createEmptyBorder(4, 2, 2, 2));

            for (int i = 0; i < 36; i++) {
                JButton btn = new JButton("");
                btn.setFocusable(false);
                btn.setBackground(new Color(40, 44, 52));
                btn.setForeground(COLOR_FG);
                btn.setFont(new Font("SansSerif", Font.BOLD, 13));
                btn.setFocusPainted(false);
                btn.setMargin(new Insets(2, 2, 2, 2));
                btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
                gridButtons[i] = btn;
                grid.add(btn);
            }
            add(grid, BorderLayout.CENTER);

            setFocusable(true);
            getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("LEFT"), "prevSection");
            getActionMap().put("prevSection", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    int prev = (currentSection == 1) ? 8 : currentSection - 1;
                    setSection(prev);
                }
            });
            getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("RIGHT"), "nextSection");
            getActionMap().put("nextSection", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    int next = (currentSection == 8) ? 1 : currentSection + 1;
                    setSection(next);
                }
            });

            setSection(1);
        }

        public void setSection(int sec) {
            if (sec < 1)
                sec = 8;
            if (sec > 8)
                sec = 1;
            this.currentSection = sec;

            lblTitle.setText(SECTION_NAMES[sec - 1]);
            btnPrev.setEnabled(true);
            btnNext.setEnabled(true);

            populateSection(sec);
            revalidate();
            repaint();
        }

        private void populateSection(int sec) {
            BtnSpec[] specs = new BtnSpec[36];
            switch (sec) {
                case 1:
                    specs = getBaseSpecs();
                    break;
                case 2:
                    specs = getFunctionSpecs();
                    break;
                case 3:
                    specs = getLatinSpecs();
                    break;
                case 4:
                    specs = getGreekSpecs();
                    break;
                case 5:
                    specs = getRussianSpecs();
                    break;
                case 6:
                    specs = getHebrewSpecs();
                    break;
                case 7:
                    specs = getPhysicsSpecs();
                    break;
                case 8:
                    specs = getSignalSpecs();
                    break;
            }

            for (int i = 0; i < 36; i++) {
                JButton btn = gridButtons[i];
                for (ActionListener al : btn.getActionListeners()) {
                    btn.removeActionListener(al);
                }

                btn.setFocusable(false);
                if (i < specs.length && specs[i] != null) {
                    BtnSpec s = specs[i];
                    btn.setText(s.label);
                    btn.setVisible(true);
                    btn.setEnabled(true);

                    if (i == 30) {
                        btn.setBackground(new Color(45, 62, 80));
                        btn.setForeground(COLOR_SIGNAL);
                    } else if (s.cmd.startsWith("CAPS_")) {
                        btn.setBackground(new Color(50, 56, 68));
                        btn.setForeground(new Color(229, 192, 123));
                    } else {
                        btn.setBackground(new Color(40, 44, 52));
                        btn.setForeground(COLOR_FG);
                    }

                    btn.addActionListener(ev -> handleCommand(s.cmd));
                } else {
                    btn.setText("");
                    btn.setVisible(false);
                }
            }
        }

        private BtnSpec[] getBaseSpecs() {
            return new BtnSpec[] {
                    new BtnSpec("C", "CLEAR"), new BtnSpec("+/-", "NEGATE"), new BtnSpec("⌫", "BACKSPACE"),
                    new BtnSpec("(", "PAREN"), new BtnSpec(")", ")"),
                    new BtnSpec("<html>x<sup>y</sup></html>", "POWER"),
                    new BtnSpec("7", "7"), new BtnSpec("8", "8"), new BtnSpec("9", "9"),
                    new BtnSpec("÷", "FRACTION"), new BtnSpec("√", "SQRT"),
                    new BtnSpec("<html><sup>n</sup>√</html>", "NTH_ROOT"),
                    new BtnSpec("4", "4"), new BtnSpec("5", "5"), new BtnSpec("6", "6"),
                    new BtnSpec("·", "*"), new BtnSpec("x", "x"), new BtnSpec("t", "t"),
                    new BtnSpec("1", "1"), new BtnSpec("2", "2"), new BtnSpec("3", "3"),
                    new BtnSpec("-", "-"), new BtnSpec("π", "π"), new BtnSpec("e", "e"),
                    new BtnSpec("0", "0"), new BtnSpec(".", "."), new BtnSpec("_", "SUB"),
                    new BtnSpec("+", "+"), new BtnSpec("i", "i"), new BtnSpec("=", "="),
                    new BtnSpec("fx", "SWITCH_2"), new BtnSpec("|x|", "ABS"), new BtnSpec("1/x", "RECIP"),
                    new BtnSpec("!", "!"), new BtnSpec("sin", "FUNC_sin"), new BtnSpec("cos", "FUNC_cos")
            };
        }

        private BtnSpec[] getFunctionSpecs() {
            return new BtnSpec[] {
                    new BtnSpec("∑", "SUM"), new BtnSpec("∫", "INT"), new BtnSpec("d/dx", "DIFF_x"),
                    new BtnSpec("∂/∂x", "PARTIAL_x"), new BtnSpec("Δ", "Δ"), new BtnSpec("⌫", "BACKSPACE"),
                    new BtnSpec("sin", "FUNC_sin"), new BtnSpec("cos", "FUNC_cos"), new BtnSpec("tan", "FUNC_tan"),
                    new BtnSpec("asin", "FUNC_asin"), new BtnSpec("acos", "FUNC_acos"),
                    new BtnSpec("atan", "FUNC_atan"),
                    new BtnSpec("sinh", "FUNC_sinh"), new BtnSpec("cosh", "FUNC_cosh"),
                    new BtnSpec("tanh", "FUNC_tanh"),
                    new BtnSpec("atan2", "FUNC_atan2"), new BtnSpec("ln", "LN"), new BtnSpec("log", "LOG"),
                    new BtnSpec("asinh", "FUNC_asinh"), new BtnSpec("acosh", "FUNC_acosh"),
                    new BtnSpec("atanh", "FUNC_atanh"),
                    new BtnSpec("log2", "FUNC_log2"), new BtnSpec("conj", "FUNC_conj"), new BtnSpec("cot", "FUNC_cot"),
                    new BtnSpec("⌊x⌋", "FLOOR"), new BtnSpec("⌈x⌉", "CEIL"), new BtnSpec("|x|", "ABS"),
                    new BtnSpec("√", "SQRT"), new BtnSpec("exp", "FUNC_exp"), new BtnSpec("∞", "∞"),
                    new BtnSpec("a b", "SWITCH_3"), new BtnSpec("ℝ", "ℝ"), new BtnSpec("ℂ", "ℂ"),
                    new BtnSpec("ℤ", "ℤ"), new BtnSpec("ℕ", "ℕ"), new BtnSpec("ℚ", "ℚ")
            };
        }

        private BtnSpec[] getLatinSpecs() {
            String[] raw = {
                    "a", "b", "c", "d", "e", "BACKSPACE",
                    "f", "g", "h", "i", "j", "k",
                    "l", "m", "n", "o", "p", "q",
                    "r", "s", "t", "u", "v", "w",
                    "x", "y", "z", "_", "^", "CAPS_LATIN",
                    "SWITCH_4", "(", ")", ",", "=", " "
            };
            BtnSpec[] specs = new BtnSpec[36];
            for (int i = 0; i < 36; i++) {
                String s = raw[i];
                if (s.equals("BACKSPACE"))
                    specs[i] = new BtnSpec("⌫", "BACKSPACE");
                else if (s.equals("CAPS_LATIN"))
                    specs[i] = new BtnSpec(capsLatin ? "A → a" : "a → A", "CAPS_LATIN");
                else if (s.equals("SWITCH_4"))
                    specs[i] = new BtnSpec("α β", "SWITCH_4");
                else if (s.equals(" "))
                    specs[i] = new BtnSpec("Space", " ");
                else if (s.length() == 1 && Character.isLetter(s.charAt(0))) {
                    String letter = capsLatin ? s.toUpperCase() : s.toLowerCase();
                    specs[i] = new BtnSpec(letter, letter);
                } else
                    specs[i] = new BtnSpec(s, s);
            }
            return specs;
        }

        private BtnSpec[] getGreekSpecs() {
            String[] lower = {
                    "α", "β", "γ", "δ", "ε", "BACKSPACE",
                    "ζ", "η", "θ", "ι", "κ", "λ",
                    "μ", "ν", "ξ", "π", "ρ", "σ",
                    "τ", "υ", "φ", "χ", "ψ", "ω",
                    "ς", "ϑ", "ϕ", "_", "^", "CAPS_GREEK",
                    "SWITCH_5", "ϵ", "ϖ", "(", ")", ","
            };
            String[] upper = {
                    "Α", "Β", "Γ", "Δ", "Ε", "BACKSPACE",
                    "Ζ", "Η", "Θ", "Ι", "Κ", "Λ",
                    "Μ", "Ν", "Ξ", "Π", "Ρ", "Σ",
                    "Τ", "Υ", "Φ", "Χ", "Ψ", "Ω",
                    "ς", "ϑ", "ϕ", "_", "^", "CAPS_GREEK",
                    "SWITCH_5", "ϵ", "ϖ", "(", ")", ","
            };
            String[] src = capsGreek ? upper : lower;
            BtnSpec[] specs = new BtnSpec[36];
            for (int i = 0; i < 36; i++) {
                String s = src[i];
                if (s.equals("BACKSPACE"))
                    specs[i] = new BtnSpec("⌫", "BACKSPACE");
                else if (s.equals("CAPS_GREEK"))
                    specs[i] = new BtnSpec(capsGreek ? "Α → α" : "α → Α", "CAPS_GREEK");
                else if (s.equals("SWITCH_5"))
                    specs[i] = new BtnSpec("а б", "SWITCH_5");
                else
                    specs[i] = new BtnSpec(s, s);
            }
            return specs;
        }

        private BtnSpec[] getRussianSpecs() {
            String[] lower = {
                    "а", "б", "в", "г", "д", "BACKSPACE",
                    "е", "ё", "ж", "з", "и", "й",
                    "к", "л", "м", "н", "о", "п",
                    "р", "с", "т", "у", "ф", "х",
                    "ц", "ч", "ш", "щ", "ъ", "CAPS_RUSSIAN",
                    "SWITCH_6", "ы", "ь", "э", "ю", "я"
            };
            String[] upper = {
                    "А", "Б", "В", "Г", "Д", "BACKSPACE",
                    "Е", "Ё", "Ж", "З", "И", "Й",
                    "К", "Л", "М", "Н", "О", "П",
                    "Р", "С", "Т", "У", "Ф", "Х",
                    "Ц", "Ч", "Ш", "Щ", "Ъ", "CAPS_RUSSIAN",
                    "SWITCH_6", "Ы", "Ь", "Э", "Ю", "Я"
            };
            String[] src = capsRussian ? upper : lower;
            BtnSpec[] specs = new BtnSpec[36];
            for (int i = 0; i < 36; i++) {
                String s = src[i];
                if (s.equals("BACKSPACE"))
                    specs[i] = new BtnSpec("⌫", "BACKSPACE");
                else if (s.equals("CAPS_RUSSIAN"))
                    specs[i] = new BtnSpec(capsRussian ? "А → а" : "а → А", "CAPS_RUSSIAN");
                else if (s.equals("SWITCH_6"))
                    specs[i] = new BtnSpec("א ב", "SWITCH_6");
                else
                    specs[i] = new BtnSpec(s, s);
            }
            return specs;
        }

        private BtnSpec[] getHebrewSpecs() {
            return new BtnSpec[] {
                    new BtnSpec("א", "א"), new BtnSpec("ב", "ב"), new BtnSpec("ג", "ג"),
                    new BtnSpec("ד", "ד"), new BtnSpec("ה", "ה"), new BtnSpec("⌫", "BACKSPACE"),
                    new BtnSpec("ו", "ו"), new BtnSpec("ז", "ז"), new BtnSpec("ח", "ח"),
                    new BtnSpec("ט", "ט"), new BtnSpec("י", "י"), new BtnSpec("כ", "כ"),
                    new BtnSpec("ל", "ל"), new BtnSpec("מ", "מ"), new BtnSpec("נ", "נ"),
                    new BtnSpec("ס", "ס"), new BtnSpec("ע", "ע"), new BtnSpec("פ", "פ"),
                    new BtnSpec("צ", "צ"), new BtnSpec("ק", "ק"), new BtnSpec("ר", "ר"),
                    new BtnSpec("ש", "ש"), new BtnSpec("ת", "ת"), new BtnSpec("_", "SUB"),
                    new BtnSpec("<html>ℵ<sub>0</sub></html>", "ℵ_0"), new BtnSpec("<html>ℵ<sub>1</sub></html>", "ℵ_1"),
                    new BtnSpec("<html>ℶ<sub>0</sub></html>", "ℶ_0"), new BtnSpec("<html>ℶ<sub>1</sub></html>", "ℶ_1"),
                    new BtnSpec("(", "("), new BtnSpec(")", ")"),
                    new BtnSpec("Phys", "SWITCH_7"), new BtnSpec("^", "POWER"), new BtnSpec(",", ","),
                    new BtnSpec("=", "="), new BtnSpec("+", "+"), new BtnSpec("-", "-")
            };
        }

        private BtnSpec[] getPhysicsSpecs() {
            return new BtnSpec[] {
                    new BtnSpec("Δ", "Δ"), new BtnSpec("d/dt", "DIFF_t"), new BtnSpec("∂/∂t", "PARTIAL_t"),
                    new BtnSpec("∂/∂x", "PARTIAL_x"), new BtnSpec("∇", "∇"), new BtnSpec("⌫", "BACKSPACE"),
                    new BtnSpec("T", "T"), new BtnSpec("P", "P"), new BtnSpec("V", "V"),
                    new BtnSpec("S", "S"), new BtnSpec("U", "U"), new BtnSpec("H", "H"),
                    new BtnSpec("G", "G"), new BtnSpec("A", "A"), new BtnSpec("Q", "Q"),
                    new BtnSpec("W", "W"), new BtnSpec("<html>C<sub>p</sub></html>", "C_p"),
                    new BtnSpec("<html>C<sub>v</sub></html>", "C_v"),
                    new BtnSpec("<html>k<sub>B</sub></html>", "k_B"), new BtnSpec("<html>N<sub>A</sub></html>", "N_A"),
                    new BtnSpec("R", "R"), new BtnSpec("ħ", "ħ"), new BtnSpec("h", "h"), new BtnSpec("c", "c"),
                    new BtnSpec("<html>ε<sub>0</sub></html>", "ε_0"), new BtnSpec("<html>μ<sub>0</sub></html>", "μ_0"),
                    new BtnSpec("<html>k<sub>e</sub></html>", "k_e"),
                    new BtnSpec("e", "e"), new BtnSpec("dt", "dt"), new BtnSpec("∇²", "∇^2"),
                    new BtnSpec("Sig", "SWITCH_8"), new BtnSpec("(", "("), new BtnSpec(")", ")"),
                    new BtnSpec("_", "SUB"), new BtnSpec("^", "POWER"), new BtnSpec("=", "=")
            };
        }

        private BtnSpec[] getSignalSpecs() {
            return new BtnSpec[] {
                    new BtnSpec("<html>⊗<sub>c</sub></html>", "⊗_c"),
                    new BtnSpec("<html>⊗<sub>k</sub></html>", "⊗_k"),
                    new BtnSpec("δ(t)", "δ(t)"), new BtnSpec("Ш(t)", "Ш(t)"),
                    new BtnSpec("δ[n]", "δ[n]"), new BtnSpec("⌫", "BACKSPACE"),
                    new BtnSpec("rect", "FUNC_rect"), new BtnSpec("tri", "FUNC_tri"),
                    new BtnSpec("trapz", "FUNC_trapz"),
                    new BtnSpec("sinc", "FUNC_sinc"), new BtnSpec("step", "FUNC_step"),
                    new BtnSpec("sign", "FUNC_sign"),
                    new BtnSpec("saw", "FUNC_saw"), new BtnSpec("sqw", "FUNC_sqw"), new BtnSpec("x[n]", "x[n]"),
                    new BtnSpec("y[n]", "y[n]"), new BtnSpec("h[n]", "h[n]"), new BtnSpec("w[n]", "w[n]"),
                    new BtnSpec("<html>ω<sub>0</sub></html>", "ω_0"), new BtnSpec("<html>f<sub>0</sub></html>", "f_0"),
                    new BtnSpec("<html>T<sub>0</sub></html>", "T_0"),
                    new BtnSpec("<html>f<sub>s</sub></html>", "f_s"), new BtnSpec("F", "F"), new BtnSpec("L", "L"),
                    new BtnSpec("x*[n]", "x*[n]"), new BtnSpec("x*(t)", "x*(t)"), new BtnSpec("y*[n]", "y*[n]"),
                    new BtnSpec("t", "t"), new BtnSpec("n", "n"), new BtnSpec("k", "k"),
                    new BtnSpec("123", "SWITCH_1"), new BtnSpec("(", "("), new BtnSpec(")", ")"),
                    new BtnSpec("_", "SUB"), new BtnSpec("^", "POWER"), new BtnSpec("=", "=")
            };
        }

        void handleCommand(String cmd) {
            if (activeFunction == null || activeFunction.textField == null)
                return;
            MathTextField tf = activeFunction.textField;
            tf.requestFocusInWindow();

            if (cmd.equals("CLEAR")) {
                tf.setText("");
                return;
            } else if (cmd.equals("BACKSPACE")) {
                String sel = tf.getSelectedText();
                if (sel != null && !sel.isEmpty()) {
                    tf.replaceSelection("");
                } else {
                    int c = tf.getCaretPosition();
                    if (c > 0) {
                        try {
                            tf.getDocument().remove(c - 1, 1);
                        } catch (Exception ignored) {
                        }
                    }
                }
                return;
            } else if (cmd.equals("CAPS_LATIN")) {
                capsLatin = !capsLatin;
                populateSection(3);
                return;
            } else if (cmd.equals("CAPS_GREEK")) {
                capsGreek = !capsGreek;
                populateSection(4);
                return;
            } else if (cmd.equals("CAPS_RUSSIAN")) {
                capsRussian = !capsRussian;
                populateSection(5);
                return;
            } else if (cmd.startsWith("SWITCH_")) {
                int target = Integer.parseInt(cmd.substring(7));
                setSection(target);
                return;
            } else if (cmd.equals("FRACTION")) {
                tf.replaceSelection("/");
                return;
            } else if (cmd.equals("POWER")) {
                int c = tf.getCaretPosition();
                String t = tf.getText();
                MathInputHandler.handlePowerInsertion(tf.getDocument(), c, t, tf::setCaretPosition,
                        tf::triggerDynamicResize);
                return;
            } else if (cmd.equals("SQRT")) {
                int c = tf.getCaretPosition();
                tf.replaceSelection("√()");
                tf.setCaretPosition(c + 2);
                return;
            } else if (cmd.equals("NTH_ROOT")) {
                tf.insertNthRoot();
                return;
            } else if (cmd.equals("FLOOR")) {
                tf.insertFloor();
                return;
            } else if (cmd.equals("CEIL")) {
                tf.insertCeil();
                return;
            } else if (cmd.equals("ABS")) {
                int c = tf.getCaretPosition();
                tf.replaceSelection("||");
                tf.setCaretPosition(c + 1);
                return;
            } else if (cmd.equals("PAREN")) {
                int c = tf.getCaretPosition();
                tf.replaceSelection("()");
                tf.setCaretPosition(c + 1);
                return;
            } else if (cmd.equals("RECIP")) {
                int c = tf.getCaretPosition();
                tf.replaceSelection("(1)/()");
                tf.setCaretPosition(c + 5);
                return;
            } else if (cmd.equals("SUM")) {
                int c = tf.getCaretPosition();
                tf.replaceSelection("∑[][]()");
                tf.setCaretPosition(c + 2);
                return;
            } else if (cmd.equals("Σ")) {
                tf.replaceSelection("∑");
                return;
            } else if (cmd.equals("INT")) {
                int c = tf.getCaretPosition();
                tf.replaceSelection("∫[][]()dx");
                tf.setCaretPosition(c + 6);
                return;
            } else if (cmd.equals("NEGATE")) {
                int selStart = tf.getSelectionStart();
                int selEnd = tf.getSelectionEnd();
                String fullText = tf.getText();
                if (selStart != selEnd) {
                    String sel = tf.getSelectedText();
                    String trimmed = sel.trim();
                    if (trimmed.startsWith("(-") && trimmed.endsWith(")")) {
                        String inner = trimmed.substring(2, trimmed.length() - 1).trim();
                        tf.replaceSelection(inner);
                        tf.select(selStart, selStart + inner.length());
                        return;
                    }
                    if (trimmed.startsWith("-(") && trimmed.endsWith(")")) {
                        String inner = trimmed.substring(2, trimmed.length() - 1).trim();
                        tf.replaceSelection(inner);
                        tf.select(selStart, selStart + inner.length());
                        return;
                    }
                    if (trimmed.startsWith("-")) {
                        String inner = trimmed.substring(1).trim();
                        tf.replaceSelection(inner);
                        tf.select(selStart, selStart + inner.length());
                        return;
                    }
                    if (trimmed.startsWith("+"))
                        trimmed = trimmed.substring(1).trim();

                    int beforeIdx = selStart - 1;
                    while (beforeIdx >= 0 && Character.isWhitespace(fullText.charAt(beforeIdx)))
                        beforeIdx--;
                    boolean precededByOp = (beforeIdx >= 0 && "+-*/·^(".indexOf(fullText.charAt(beforeIdx)) != -1);
                    boolean isCompound = trimmed.contains("+") || trimmed.contains("-") || precededByOp;

                    String replacement = isCompound ? "(-" + trimmed + ")" : "-" + trimmed;
                    tf.replaceSelection(replacement);
                    tf.select(selStart, selStart + replacement.length());
                    return;
                }

                int c = tf.getCaretPosition();
                if (c > 0 && fullText.charAt(c - 1) == '-') {
                    if (c > 1 && fullText.charAt(c - 2) == '(' && c < fullText.length() && fullText.charAt(c) == ')') {
                        try {
                            tf.getDocument().remove(c - 2, 3);
                            tf.setCaretPosition(c - 2);
                        } catch (Exception ignored) {
                        }
                        return;
                    }
                    try {
                        tf.getDocument().remove(c - 1, 1);
                        tf.setCaretPosition(c - 1);
                    } catch (Exception ignored) {
                    }
                    return;
                }

                int termEnd = c;
                int termStart = c;
                if (c > 0 && fullText.charAt(c - 1) == ')') {
                    int depth = 1;
                    int p = c - 2;
                    while (p >= 0 && depth > 0) {
                        if (fullText.charAt(p) == ')')
                            depth++;
                        else if (fullText.charAt(p) == '(')
                            depth--;
                        p--;
                    }
                    if (depth == 0) {
                        termStart = p + 1;
                        while (termStart > 0 && (Character.isLetterOrDigit(fullText.charAt(termStart - 1))
                                || fullText.charAt(termStart - 1) == '_')) {
                            termStart--;
                        }
                    }
                } else {
                    while (termStart > 0 && (Character.isLetterOrDigit(fullText.charAt(termStart - 1))
                            || fullText.charAt(termStart - 1) == '.' || fullText.charAt(termStart - 1) == '_')) {
                        termStart--;
                    }
                    while (termEnd < fullText.length() && (Character.isLetterOrDigit(fullText.charAt(termEnd))
                            || fullText.charAt(termEnd) == '.' || fullText.charAt(termEnd) == '_')) {
                        termEnd++;
                    }
                }

                if (termEnd > termStart) {
                    String termStr = fullText.substring(termStart, termEnd);
                    if (termStr.startsWith("(-") && termStr.endsWith(")") && termStr.length() > 3) {
                        String inner = termStr.substring(2, termStr.length() - 1);
                        try {
                            tf.getDocument().remove(termStart, termStr.length());
                            tf.getDocument().insertString(termStart, inner, null);
                            tf.setCaretPosition(termStart + inner.length());
                        } catch (Exception ignored) {
                        }
                        return;
                    }
                    if (termStr.startsWith("-(") && termStr.endsWith(")") && termStr.length() > 3) {
                        String inner = termStr.substring(2, termStr.length() - 1);
                        try {
                            tf.getDocument().remove(termStart, termStr.length());
                            tf.getDocument().insertString(termStart, inner, null);
                            tf.setCaretPosition(termStart + inner.length());
                        } catch (Exception ignored) {
                        }
                        return;
                    }

                    if (termStart >= 2 && fullText.substring(termStart - 2, termStart).equals("(-")
                            && termEnd < fullText.length() && fullText.charAt(termEnd) == ')') {
                        try {
                            tf.getDocument().remove(termEnd, 1);
                            tf.getDocument().remove(termStart - 2, 2);
                            tf.setCaretPosition(c - 2);
                        } catch (Exception ignored) {
                        }
                        return;
                    }
                    if (termStart > 0 && fullText.charAt(termStart - 1) == '-') {
                        int opIdx = termStart - 2;
                        while (opIdx >= 0 && Character.isWhitespace(fullText.charAt(opIdx)))
                            opIdx--;
                        if (opIdx >= 0 && "+-*/·^(".indexOf(fullText.charAt(opIdx)) != -1) {
                            String term = fullText.substring(termStart, termEnd);
                            int remLen = termEnd - (termStart - 1);
                            try {
                                tf.getDocument().remove(termStart - 1, remLen);
                                String ins = "(-" + term + ")";
                                tf.getDocument().insertString(termStart - 1, ins, null);
                                tf.setCaretPosition(termStart - 1 + ins.length());
                            } catch (Exception ignored) {
                            }
                            return;
                        } else {
                            try {
                                tf.getDocument().remove(termStart - 1, 1);
                                tf.setCaretPosition(c - 1);
                            } catch (Exception ignored) {
                            }
                            return;
                        }
                    }

                    int beforeIdx = termStart - 1;
                    while (beforeIdx >= 0 && Character.isWhitespace(fullText.charAt(beforeIdx)))
                        beforeIdx--;
                    boolean precededByOp = (beforeIdx >= 0 && "+-*/·^(".indexOf(fullText.charAt(beforeIdx)) != -1);

                    String term = fullText.substring(termStart, termEnd);
                    int remLen = termEnd - termStart;
                    try {
                        tf.getDocument().remove(termStart, remLen);
                        String ins = precededByOp ? "(-" + term + ")" : "-" + term;
                        tf.getDocument().insertString(termStart, ins, null);
                        tf.setCaretPosition(termStart + ins.length());
                    } catch (Exception ignored) {
                    }
                    return;
                }

                int beforeIdx = c - 1;
                while (beforeIdx >= 0 && Character.isWhitespace(fullText.charAt(beforeIdx)))
                    beforeIdx--;
                boolean precededByOp = (beforeIdx >= 0 && "+-*/·^(".indexOf(fullText.charAt(beforeIdx)) != -1);
                if (precededByOp) {
                    tf.replaceSelection("(-)");
                    tf.setCaretPosition(c + 2);
                } else {
                    tf.replaceSelection("-");
                    tf.setCaretPosition(c + 1);
                }
                return;
            } else if (cmd.equals("DIFF_x")) {
                int c = tf.getCaretPosition();
                tf.replaceSelection("(d)/(dx)");
                tf.setCaretPosition(c + 8);
                return;
            } else if (cmd.equals("DIFF_t")) {
                int c = tf.getCaretPosition();
                tf.replaceSelection("(d)/(dt)");
                tf.setCaretPosition(c + 8);
                return;
            } else if (cmd.equals("PARTIAL_x")) {
                int c = tf.getCaretPosition();
                tf.replaceSelection("(∂)/(∂x)");
                tf.setCaretPosition(c + 8);
                return;
            } else if (cmd.equals("PARTIAL_t")) {
                int c = tf.getCaretPosition();
                tf.replaceSelection("(∂)/(∂t)");
                tf.setCaretPosition(c + 8);
                return;
            } else if (cmd.equals("LN")) {
                int c = tf.getCaretPosition();
                tf.replaceSelection("log[e]()");
                tf.setCaretPosition(c + 7);
                return;
            } else if (cmd.equals("LOG")) {
                int c = tf.getCaretPosition();
                tf.replaceSelection("log[]()");
                tf.setCaretPosition(c + 4);
                return;
            } else if (cmd.equals("SUB")) {
                int c = tf.getCaretPosition();
                String t = tf.getText();
                if (!MathInputHandler.canAddSubscriptAt(t, c, null))
                    return;
                tf.replaceSelection("_()");
                tf.setCaretPosition(c + 2);
                return;
            } else if (cmd.equals("⊗_c")) {
                int c = tf.getCaretPosition();
                tf.replaceSelection("\u2297_c ");
                SwingUtilities.invokeLater(() -> tf.setCaretPosition(Math.min(tf.getText().length(), c + 4)));
                return;
            } else if (cmd.equals("⊗_k")) {
                int c = tf.getCaretPosition();
                tf.replaceSelection("\u2297_k ");
                SwingUtilities.invokeLater(() -> tf.setCaretPosition(Math.min(tf.getText().length(), c + 4)));
                return;
            } else if (cmd.equals("*_c")) {
                int c = tf.getCaretPosition();
                tf.replaceSelection("*_c ");
                SwingUtilities.invokeLater(() -> tf.setCaretPosition(Math.min(tf.getText().length(), c + 4)));
                return;
            } else if (cmd.equals("ℝ") || cmd.equals("ℂ") || cmd.equals("ℤ") || cmd.equals("ℕ") || cmd.equals("ℚ")) {
                int c = tf.getCaretPosition();
                String t = tf.getText();
                SubscriptsSuperscript.SubscriptRegion[] subs = SubscriptsSuperscript.findRegions(t, null, null);
                if (MathInputHandler.isInsideSubscript(c, subs))
                    return;
                tf.replaceSelection(cmd);
                return;
            } else if (cmd.startsWith("FUNC_")) {
                String fName = cmd.substring(5);
                int c = tf.getCaretPosition();
                if (fName.equals("atan2")) {
                    tf.replaceSelection("atan2(,)");
                    tf.setCaretPosition(c + 6);
                } else {
                    tf.replaceSelection(fName + "()");
                    tf.setCaretPosition(c + fName.length() + 1);
                }
                return;
            } else if (cmd.equals("δ(t)") || cmd.equals("Ш(t)") || cmd.equals("x*(t)")
                    || cmd.equals("δ[n]") || cmd.equals("x[n]") || cmd.equals("y[n]")
                    || cmd.equals("h[n]") || cmd.equals("w[n]")
                    || cmd.equals("x*[n]") || cmd.equals("y*[n]")) {
                int c = tf.getCaretPosition();
                tf.replaceSelection(cmd);
                tf.setCaretPosition(c + cmd.length());
                return;
            } else if (cmd.contains("_") && cmd.length() >= 3 && !cmd.startsWith("FUNC_")
                    && !cmd.startsWith("SWITCH_") && !cmd.startsWith("CAPS_")) {
                int c = tf.getCaretPosition();
                tf.replaceSelection(cmd);
                SwingUtilities
                        .invokeLater(() -> tf.setCaretPosition(Math.min(tf.getText().length(), c + cmd.length())));
                return;
            } else {
                tf.replaceSelection(cmd);
            }
        }
    }

    private static class ScrollableSidebarPanel extends JPanel implements Scrollable {
        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 64;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    class VignettePanel extends JPanel {
        private static final int FIXED_HEIGHT = 130;
        private final FunctionEntry entry;
        private final int harmonicIndex;

        VignettePanel(FunctionEntry entry, int harmonicIndex) {
            this.entry = entry;
            this.harmonicIndex = harmonicIndex;
            setBackground(new Color(24, 27, 32));
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(COLOR_GRID, 1),
                    BorderFactory.createEmptyBorder(8, 8, 8, 8)));
            setAlignmentX(Component.CENTER_ALIGNMENT);
        }

        @Override
        public Dimension getPreferredSize() {
            int w = 180;
            java.awt.Container p = getParent();
            if (p != null) {
                w = Math.max(180, p.getWidth() - 28);
            }
            return new Dimension(w, FIXED_HEIGHT);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, FIXED_HEIGHT);
        }

        @Override
        public Dimension getMinimumSize() {
            return new Dimension(100, FIXED_HEIGHT);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            MathRenderEngine.applyRenderingHints(g2);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            int w = getWidth();
            int h = getHeight();

            if (entry.engine == null)
                return;
            SignalEngine.Harmonic harm = entry.engine.getHarmonic(harmonicIndex);
            if (harm == null)
                return;

            double amp = Math.hypot(harm.a, harm.b);
            Color harmColor = COLORS_HARMONIC[harmonicIndex % COLORS_HARMONIC.length];

            int padX = 10;

            g2.setFont(new Font("Consolas", Font.BOLD, 12));
            g2.setColor(harmColor);
            int funcIdx = functionEntries.indexOf(entry) + 1;
            String headerText = (functionEntries.size() > 1 ? "f" + funcIdx + "(x) - " : "") + "Harmonic "
                    + harmonicIndex;
            g2.drawString(headerText, padX, 20);

            g2.setFont(new Font("Consolas", Font.PLAIN, 11));
            g2.setColor(COLOR_FG);
            g2.drawString("Amp: " + formatAmp(amp), padX, 36);
            g2.drawString("freq(k) = " + harmonicIndex, padX, 50);

            java.awt.Insets vi = getInsets();
            int maxY = h - vi.bottom - 4;
            int waveW = Math.max(10, w - 2 * padX - vi.left - vi.right);
            int waveTopY = 56;
            int waveH = Math.max(20, maxY - waveTopY);
            double zeroY = waveTopY + waveH / 2.0;
            java.awt.Shape oldClip = g2.getClip();
            g2.clipRect(padX, waveTopY, waveW, waveH);

            g2.setColor(new Color(60, 66, 75));
            g2.setStroke(new BasicStroke(1.0f));
            g2.drawLine(padX, (int) zeroY, padX + waveW, (int) zeroY);

            double L = (maxX - minX) / 2.0;

            double wx0 = minX;
            double wx1 = maxX;
            double scaleX = (double) waveW / (wx1 - wx0);

            double harmAmp = amp;
            if (harmAmp < 1e-12)
                harmAmp = 1.0;
            double scaleY = (waveH / 2.0) / (harmAmp * 1.3);

            if (entry.displayMode != FunctionEntry.DisplayMode.NORMAL) {
                int maxH = entry.engine.getMaxHarmonics();
                double stepX = (double) waveW / (double) (maxH + 1);
                double px = padX + harmonicIndex * stepX;
                double hAmp = amp;
                double hPhase = Math.atan2(-harm.b, harm.a);

                if (entry.displayMode == FunctionEntry.DisplayMode.SPECTRUM_AMP_PHASE) {
                    double pyAmp = zeroY - hAmp * scaleY;
                    g2.setColor(COLOR_SIGNAL);
                    g2.setStroke(new BasicStroke(2.0f));
                    g2.drawLine((int) px, (int) zeroY, (int) px, (int) pyAmp);
                    g2.fillOval((int) px - 3, (int) pyAmp - 3, 6, 6);

                    double phaseScaleY = (waveH / 2.0) / (Math.PI * 1.2);
                    double pyPhase = zeroY - hPhase * phaseScaleY;
                    g2.setColor(new Color(229, 192, 123));
                    g2.fillOval((int) px - 2, (int) pyPhase - 2, 4, 4);
                } else if (entry.displayMode == FunctionEntry.DisplayMode.SPECTRUM_POWER) {
                    double power = hAmp * hAmp;
                    double pScaleY = (waveH / 2.0) / ((harmAmp * harmAmp) * 1.5);
                    double pyPow = zeroY - power * pScaleY;
                    g2.setColor(new Color(198, 120, 221));
                    g2.setStroke(new BasicStroke(2.0f));
                    g2.drawLine((int) px, (int) zeroY, (int) px, (int) pyPow);
                    g2.fillOval((int) px - 3, (int) pyPow - 3, 6, 6);
                }
            } else {
                if (entry.isDiscretized || entry.signalType == SignalType.DISCRETE_APERIODIC
                        || entry.signalType == SignalType.DISCRETE_PERIODIC
                        || entry.signalType == SignalType.DISCRETE_RANDOM) {
                    g2.setColor(harmColor);
                    g2.setStroke(new BasicStroke(1.5f));
                    double stepX = (maxX - minX) / 40.0;
                    for (double x = minX; x <= maxX + 1e-9; x += stepX) {
                        double y = harm.eval(x, L);
                        double px = padX + (x - minX) * scaleX;
                        if (px > padX + waveW)
                            break;
                        double py = zeroY - y * scaleY;
                        g2.drawLine((int) px, (int) zeroY, (int) px, (int) py);
                        g2.fillOval((int) px - 2, (int) py - 2, 4, 4);
                    }
                } else {
                    Path2D harmPath = new Path2D.Double();
                    boolean first = true;
                    int numSteps = Math.max(320, waveW * 4);
                    for (int i = 0; i <= numSteps; i++) {
                        double px = padX + (double) i / numSteps * waveW;
                        double x = wx0 + ((double) i / numSteps) * (wx1 - wx0);
                        double y = harm.eval(x, L);
                        double py = zeroY - y * scaleY;
                        if (first) {
                            harmPath.moveTo(px, py);
                            first = false;
                        } else
                            harmPath.lineTo(px, py);
                    }
                    g2.setColor(harmColor);
                    double cicliOnda = harmonicIndex * (wx1 - wx0) / Math.max(1e-12, 2 * Math.PI);
                    float spessore = cicliOnda <= 8.0 ? 2.0f : 1.3f;
                    g2.setStroke(new BasicStroke(spessore, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.draw(harmPath);
                }
            }
            g2.setClip(oldClip);
        }

    }

    public void showShortcutsPanel() {
        int w = Math.min(940, Math.max(760, getWidth() - 80));
        int h = Math.min(640, Math.max(500, getHeight() - 80));
        int x = (getWidth() - w) / 2;
        int y = (getHeight() - h) / 2;
        shortcutsPanel.setBounds(x, y, w, h);
        shortcutsPanel.setVisible(true);
        getLayeredPane().setComponentZOrder(shortcutsPanel, 0);
        shortcutsPanel.requestFocusInWindow();
    }

    public class ShortcutsPanel extends JPanel {
        private final CardLayout cardLayout = new CardLayout();
        private final JPanel cardPanel = new JPanel(cardLayout);

        public ShortcutsPanel() {
            setLayout(new BorderLayout());
            setBackground(COLOR_SIDEBAR);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(COLOR_GRID, 2),
                    BorderFactory.createEmptyBorder(3, 3, 3, 3)));

            JPanel header = new JPanel(new BorderLayout(8, 0));
            header.setBackground(new Color(36, 40, 50));
            header.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 8));

            PanelDragHandler dragHandler = new PanelDragHandler(this);
            header.addMouseListener(dragHandler);
            header.addMouseMotionListener(dragHandler);

            JPanel titleBlock = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            titleBlock.setOpaque(false);

            JLabel lblTitle = new JLabel("Key Bindings & Shortcuts");
            lblTitle.setFont(new Font("Consolas", Font.BOLD, 14));
            lblTitle.setForeground(COLOR_SIGNAL);

            JLabel lblSub = new JLabel("•  Quick Guide & Syntax");
            lblSub.setFont(new Font("Consolas", Font.PLAIN, 12));
            lblSub.setForeground(new Color(152, 160, 172));

            titleBlock.add(lblTitle);
            titleBlock.add(lblSub);
            header.add(titleBlock, BorderLayout.CENTER);

            JButton btnClose = createCloseButton(() -> setVisible(false));
            header.add(btnClose, BorderLayout.EAST);

            add(header, BorderLayout.NORTH);

            JPanel body = new JPanel(new BorderLayout());
            body.setBackground(COLOR_BG);

            JPanel catSidebar = new JPanel();
            catSidebar.setLayout(new BoxLayout(catSidebar, BoxLayout.Y_AXIS));
            catSidebar.setBackground(new Color(25, 28, 34));
            catSidebar.setPreferredSize(new Dimension(215, 0));
            catSidebar.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 0, 1, COLOR_GRID),
                    BorderFactory.createEmptyBorder(12, 8, 12, 8)));

            cardPanel.setBackground(COLOR_BG);

            String[] catKeys = { "mouse", "nav", "shortcuts", "functions", "calculator" };
            String[] catLabels = {
                    "Mouse & Canvas",
                    "Navigation & Cursor",
                    "Shortcuts & Symbols",
                    "Math Functions",
                    "On-Screen Keypad"
            };

            JButton[] catButtons = new JButton[catKeys.length];

            for (int i = 0; i < catKeys.length; i++) {
                final int idx = i;
                final String key = catKeys[i];
                JButton btn = new JButton(catLabels[i]);
                btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
                btn.setAlignmentX(Component.LEFT_ALIGNMENT);
                btn.setFont(new Font("Consolas", i == 0 ? Font.BOLD : Font.PLAIN, 12));
                btn.setForeground(i == 0 ? COLOR_SIGNAL : COLOR_FG);
                btn.setBackground(i == 0 ? new Color(38, 44, 54) : new Color(25, 28, 34));
                btn.setFocusPainted(false);
                btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
                btn.setHorizontalAlignment(SwingConstants.LEFT);
                btn.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, (i == 0 ? 3 : 0), 0, 0, COLOR_SIGNAL),
                        BorderFactory.createEmptyBorder(6, 10, 6, 10)));

                btn.addActionListener(e -> {
                    cardLayout.show(cardPanel, key);
                    for (int j = 0; j < catButtons.length; j++) {
                        boolean active = (j == idx);
                        catButtons[j].setBackground(active ? new Color(38, 44, 54) : new Color(25, 28, 34));
                        catButtons[j].setForeground(active ? COLOR_SIGNAL : COLOR_FG);
                        catButtons[j].setFont(new Font("Consolas", active ? Font.BOLD : Font.PLAIN, 12));
                        catButtons[j].setBorder(BorderFactory.createCompoundBorder(
                                BorderFactory.createMatteBorder(0, active ? 3 : 0, 0, 0, COLOR_SIGNAL),
                                BorderFactory.createEmptyBorder(6, 10, 6, 10)));
                    }
                });
                catButtons[i] = btn;
                catSidebar.add(btn);
                catSidebar.add(Box.createVerticalStrut(4));
            }

            cardPanel.add(createMouseControlsPanel(), "mouse");
            cardPanel.add(createNavigationPanel(), "nav");
            cardPanel.add(createShortcutsPanelContent(), "shortcuts");
            cardPanel.add(createFunctionsPanel(), "functions");
            cardPanel.add(createCalculatorHelpPanel(), "calculator");

            body.add(catSidebar, BorderLayout.WEST);
            body.add(cardPanel, BorderLayout.CENTER);
            add(body, BorderLayout.CENTER);

            JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 8));
            footer.setBackground(COLOR_SIDEBAR);
            footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, COLOR_GRID));

            JLabel lblEscHint = new JLabel("Press ESC to exit");
            lblEscHint.setFont(new Font("Consolas", Font.ITALIC, 12));
            lblEscHint.setForeground(new Color(130, 138, 150));
            footer.add(lblEscHint);
            add(footer, BorderLayout.SOUTH);

            registerKeyboardAction(e -> setVisible(false),
                    KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                    JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        }
    }

    private static class DetailArea extends JTextArea {
        public DetailArea(String text) {
            super(text);
            setFont(new Font("Consolas", Font.PLAIN, 12));
            setForeground(new Color(152, 160, 172));
            setLineWrap(true);
            setWrapStyleWord(true);
            setEditable(false);
            setFocusable(false);
            setOpaque(false);
            setBorder(null);
        }

        @Override
        public Dimension getPreferredSize() {
            int w = getWidth();
            if (w <= 0 && getParent() != null) {
                w = getParent().getWidth();
            }
            if (w > 0 && getUI() != null) {
                javax.swing.text.View view = getUI().getRootView(this);
                if (view != null) {
                    view.setSize(w, 0);
                    int h = (int) Math.ceil(view.getPreferredSpan(javax.swing.text.View.Y_AXIS));
                    return new Dimension(w, Math.max(h, getFontMetrics(getFont()).getHeight()));
                }
            }
            return super.getPreferredSize();
        }
    }

    private JScrollPane createScrollContentPane(ScrollableSidebarPanel content) {
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(COLOR_BG);
        content.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 20));

        JScrollPane sp = new JScrollPane(content);
        sp.setBorder(null);
        sp.setBackground(COLOR_BG);
        sp.getViewport().setBackground(COLOR_BG);
        sp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        sp.getVerticalScrollBar().setUI(new CustomScrollBarUI());
        sp.getVerticalScrollBar().setPreferredSize(new Dimension(6, 0));
        return sp;
    }

    private void addShortcutCard(JPanel parent, String badge, String title, String detail) {
        JPanel card = new JPanel(new BorderLayout(14, 6)) {
            @Override
            public Dimension getMaximumSize() {
                Dimension pref = getPreferredSize();
                return new Dimension(Integer.MAX_VALUE, pref.height);
            }
        };
        card.setBackground(new Color(28, 32, 38));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(COLOR_GRID, 1),
                BorderFactory.createEmptyBorder(10, 14, 10, 20)));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblBadge = new JLabel(badge);
        lblBadge.setFont(new Font("Consolas", Font.BOLD, 12));
        lblBadge.setForeground(COLOR_SELECTED_FIELD);
        lblBadge.setBackground(new Color(40, 46, 56));
        lblBadge.setOpaque(true);
        lblBadge.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(COLOR_GRID, 1),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));

        int badgePrefW = lblBadge.getPreferredSize().width;
        JPanel leftBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        leftBox.setOpaque(false);
        leftBox.setPreferredSize(new Dimension(Math.max(200, badgePrefW), 0));
        leftBox.add(lblBadge);
        card.add(leftBox, BorderLayout.WEST);

        JPanel centerPanel = new JPanel(new BorderLayout(0, 4));
        centerPanel.setOpaque(false);

        JLabel lblTitle = new JLabel(title);
        lblTitle.setFont(new Font("Consolas", Font.BOLD, 13));
        lblTitle.setForeground(COLOR_FG);
        centerPanel.add(lblTitle, BorderLayout.NORTH);

        if (detail != null && !detail.isEmpty()) {
            DetailArea lblDetail = new DetailArea(detail);
            centerPanel.add(lblDetail, BorderLayout.CENTER);
        }

        card.add(centerPanel, BorderLayout.CENTER);
        parent.add(card);
        parent.add(Box.createVerticalStrut(8));
    }

    private JScrollPane createMouseControlsPanel() {
        ScrollableSidebarPanel p = new ScrollableSidebarPanel();
        JScrollPane sp = createScrollContentPane(p);

        addShortcutCard(p, "Single Click on f(x) or Field", "Active Function Selection",
                "Selects and plots only the clicked function, isolating its curve and exposing Fourier harmonics in the bottom bar.");
        addShortcutCard(p, "Ctrl + Click on f(x) or Field", "Multi-Plot / Signal Overlay",
                "Hold Ctrl and click function labels or input fields to toggle and plot multiple signals simultaneously on the graph.");
        addShortcutCard(p, "Left Drag", "Graph Panning & Movement",
                "Click and drag anywhere on the graph area to pan and explore across the X and Y axes.");
        addShortcutCard(p, "Mouse Scroll Wheel", "Centered Continuous Zoom",
                "Scroll the wheel to smoothly zoom in or out, keeping the mouse cursor position anchored.");
        addShortcutCard(p, "Toolbar: 1:1", "Isometric Aspect Ratio",
                "Lock or unlock 1:1 aspect ratio between horizontal and vertical axes to prevent visual distortion.");
        addShortcutCard(p, "Toolbar: Fit / Reset", "Auto Framing & Reset View",
                "Fit frames and centers the active function; Reset restores the default view [-π, π] and [-2, 2].");
        addShortcutCard(p, "Harmonics Bar (Freq 0..N)", "Fourier Harmonic Inspection",
                "Click frequency buttons at the bottom to view individual harmonics or accumulated Fourier synthesis.");
        addShortcutCard(p, "Split Borders (Resizers)", "Resize Sidebars",
                "Drag vertical divider lines to widen or shrink the functions list on the left and spectrum on the right.");

        p.add(Box.createVerticalGlue());
        return sp;
    }

    private JScrollPane createNavigationPanel() {
        ScrollableSidebarPanel p = new ScrollableSidebarPanel();
        JScrollPane sp = createScrollContentPane(p);

        addShortcutCard(p, "Down Arrow", "Descend & Create Subscript",
                "In a fraction numerator moves down to denominator; in exponents returns to base; after a variable/number creates subscript _(); inside an empty subscript deletes it and returns to baseline.");
        addShortcutCard(p, "Up Arrow", "Ascend & Create Exponent",
                "In a fraction denominator moves up to numerator; after a variable or number inserts an exponent ^().");
        addShortcutCard(p, "Left / Right Arrows", "Structural Navigation",
                "Traverse formulas character-by-character, automatically entering and exiting fractions, roots, integrals, subscripts, and parentheses.");
        addShortcutCard(p, "Tab / Shift + Tab", "Jump Between Fields",
                "Quickly jump to the next or previous parameter (e.g. from numerator to denominator, or between integral limits).");
        addShortcutCard(p, "Backspace / Delete", "Clean Structural Deletion",
                "Deletes empty mathematical containers (exponents, roots, fractions) without leaving orphan parentheses or broken syntax.");
        addShortcutCard(p, "Ctrl + +  /  Ctrl + -  /  Ctrl + 0", "Formula Font Zoom",
                "Increases, decreases, or resets font size and mathematical symbols in the active input field.");
        addShortcutCard(p, "Right Click", "Context Menu & Calculator",
                "Opens quick menu with Cut, Copy, Paste, Select All, Font Zoom, and the Virtual Calculator.");

        p.add(Box.createVerticalGlue());
        return sp;
    }

    private JScrollPane createShortcutsPanelContent() {
        ScrollableSidebarPanel p = new ScrollableSidebarPanel();
        JScrollPane sp = createScrollContentPane(p);

        addShortcutCard(p, "int  or  \\int", "Definite Integral",
                "Type \"int\" to generate ∫[][]()dx with the cursor immediately positioned inside the integrand.");
        addShortcutCard(p, "iint  /  iiint", "Multiple Integrals",
                "Type \"iint\" for double integral (∫∫...dx dy) or \"iiint\" for triple integral (∫∫∫...dx dy dz).");
        addShortcutCard(p, "sum  or  \\sum", "Summation with Limits",
                "Type \"sum\" to create ∑[][]() with dedicated fields for lower limit, upper limit, and argument.");
        addShortcutCard(p, "sqrt  or  \\sqrt", "Square or N-th Root",
                "Type \"sqrt\" to insert √[](), with optional bracket index for roots of any order.");
        addShortcutCard(p, "/  (Slash)", "Two-Story Fraction",
                "Press \"/\" to wrap selection or preceding term into the fraction numerator.");
        addShortcutCard(p, "^  (Caret)", "Power & Exponent",
                "Inserts an exponent, placing the cursor directly in the superscript box x^y.");
        addShortcutCard(p, "_  (Underscore)", "Subscript Index",
                "Inserts a subscript index (e.g. x_0, k_i, ω_c) placing the cursor inside.");
        addShortcutCard(p, "diff  or  d/dx", "Derivative Operator",
                "Inserts derivative notation d/dx() (or d/dt() when independent variable is t).");
        addShortcutCard(p, "drcd  /  delta  /  dirac", "Dirac Impulse δ(t)",
                "Type \"drcd\" to insert Dirac impulse δ(), rendered on graph with standard vertical arrow stem.");
        addShortcutCard(p, "drcc  /  comb  /  shah", "Dirac Comb (Impulse Train) Ш(t)",
                "Type \"drcc\" or \"shah\" to generate ideal sampling comb Ш(t) with configurable period and amplitude.");
        addShortcutCard(p, "step  /  heaviside", "Unit Step Function u(t)",
                "Type \"step\" for Heaviside step function (0 for negative values, 1 for positive values).");
        addShortcutCard(p, "floor(x), ceil(x), |x|", "Rounding & Absolute Value",
                "Generates floor(x), ceil(x), or |x| (absolute value, also created with vertical bar |).");
        addShortcutCard(p, "pi  /  e  /  i  /  inf", "Fundamental Constants",
                "Automatically converted to π (3.14159...), Euler's number e (2.71828...), imaginary unit i, and infinity ∞.");

        p.add(Box.createVerticalGlue());
        return sp;
    }

    private JScrollPane createFunctionsPanel() {
        ScrollableSidebarPanel p = new ScrollableSidebarPanel();
        JScrollPane sp = createScrollContentPane(p);

        addShortcutCard(p, "rect(x)", "Rectangular Pulse",
                "Rectangular window function: 1 for |x| ≤ 0.5, 0.5 for |x| = 0.5, and 0 elsewhere.");
        addShortcutCard(p, "tri(x)", "Triangular Pulse",
                "Unit triangle pulse: max(0, 1 - |x|), amplitude 1 at x = 0, base between -1 and +1.");
        addShortcutCard(p, "saw(x)", "Sawtooth Wave",
                "Periodic sawtooth waveform between -1 and +1 with unit period.");
        addShortcutCard(p, "sqw(x)  /  square(x, [duty])", "Square Wave",
                "Two-level square wave (+1 and -1) with optional duty cycle (default 50%, e.g. sqw(x, 0.3)).");
        addShortcutCard(p, "sinc(x)", "Normalized Sinc Function",
                "Computes sin(π x) / (π x) with removable singularity sinc(0) = 1.0 (vital for Fourier analysis and filtering).");
        addShortcutCard(p, "rand(x)", "Stochastic Signal / Noise",
                "Deterministic stationary pseudo-random generator to simulate noise or jitter.");
        addShortcutCard(p, "sin, cos, tan, asin, acos, atan", "Trigonometric & Inverse",
                "Standard and inverse trigonometric functions with analytical extension across the complex plane.");
        addShortcutCard(p, "sinh, cosh, tanh, asinh, acosh", "Hyperbolic & Inverse",
                "Complete support for real and complex hyperbolic functions.");
        addShortcutCard(p, "exp(x), ln(x), log(x), log[b](x)", "Exponential & Logarithms",
                "Natural exponential e^x, natural logarithm ln(x), and arbitrary base logarithm log[b](x).");

        p.add(Box.createVerticalGlue());
        return sp;
    }

    private JScrollPane createCalculatorHelpPanel() {
        ScrollableSidebarPanel p = new ScrollableSidebarPanel();
        JScrollPane sp = createScrollContentPane(p);

        addShortcutCard(p, "Opening & Movement", "Draggable Floating Palette",
                "Open calculator by right-clicking any formula field and selecting \"Calculator\". Drag freely by the header bar.");
        addShortcutCard(p, "8 Specialized Sections", "Comprehensive Symbol Palette",
                "Includes 8 grids: Base, Advanced Functions, Latin Alphabet, Greek (ω, θ, π, λ...), Cyrillic (Ш), Hebrew, Physical Constants, and Signal Theory.");
        addShortcutCard(p, "Section Navigation", "Fast Page Switching",
                "Use < and > in header, the quick toggle \"fx\" / \"123\" in bottom left, or left/right keyboard arrows.");
        addShortcutCard(p, "Insert at Caret", "Focus Preservation & Insertion",
                "Buttons do not steal focus: every click inserts or wraps text at the exact cursor position or replaces selection.");

        p.add(Box.createVerticalGlue());
        return sp;
    }

    private class HamburgerMenuButton extends JComponent {
        private float progress = 0f;
        private Timer timer;
        private boolean isOpen = false;
        private long lastCloseTime = 0;
        private final JPopupMenu popup;

        public HamburgerMenuButton() {
            setPreferredSize(new Dimension(24, 24));
            setCursor(new Cursor(Cursor.HAND_CURSOR));

            popup = new JPopupMenu();
            popup.setBackground(COLOR_SIDEBAR);
            popup.setBorder(BorderFactory.createLineBorder(COLOR_GRID));

            JMenu menuSigType = new JMenu("Signal Classification");
            styleMenu(menuSigType);
            menuSigType.getPopupMenu().setBackground(COLOR_SIDEBAR);
            menuSigType.getPopupMenu().setBorder(BorderFactory.createLineBorder(COLOR_GRID));
            popup.add(menuSigType);

            JMenu menuSpectrum = new JMenu("Spectrum");
            styleMenu(menuSpectrum);
            menuSpectrum.getPopupMenu().setBackground(COLOR_SIDEBAR);
            menuSpectrum.getPopupMenu().setBorder(BorderFactory.createLineBorder(COLOR_GRID));

            JMenuItem itemPhaseAmp = new JMenuItem("Phase and Amplitude");
            styleMenuItem(itemPhaseAmp);
            itemPhaseAmp.addActionListener(e -> {
                boolean toggleOff = activeFunction != null
                        && activeFunction.displayMode == FunctionEntry.DisplayMode.SPECTRUM_AMP_PHASE;
                for (FunctionEntry fe : visibleFunctions) {
                    fe.displayMode = toggleOff ? FunctionEntry.DisplayMode.NORMAL
                            : FunctionEntry.DisplayMode.SPECTRUM_AMP_PHASE;
                    fe.isDiscretized = false;
                }
                graphPanel.repaint();
                rightSidebarPanel.repaint();
            });

            JMenuItem itemPower = new JMenuItem("Power");
            styleMenuItem(itemPower);
            itemPower.addActionListener(e -> {
                boolean toggleOff = activeFunction != null
                        && activeFunction.displayMode == FunctionEntry.DisplayMode.SPECTRUM_POWER;
                for (FunctionEntry fe : visibleFunctions) {
                    fe.displayMode = toggleOff ? FunctionEntry.DisplayMode.NORMAL
                            : FunctionEntry.DisplayMode.SPECTRUM_POWER;
                    fe.isDiscretized = false;
                }
                graphPanel.repaint();
                rightSidebarPanel.repaint();
            });

            menuSpectrum.add(itemPhaseAmp);
            menuSpectrum.add(itemPower);
            popup.add(menuSpectrum);

            JMenuItem itemReset = new JMenuItem("Reset");
            styleMenuItem(itemReset);
            itemReset.addActionListener(e -> resetAllStates());
            popup.add(itemReset);

            popup.addSeparator();

            JMenuItem itemShortcuts = new JMenuItem("Key Bindings & Shortcuts");
            styleMenuItem(itemShortcuts);
            itemShortcuts.addActionListener(e -> showShortcutsPanel());
            popup.add(itemShortcuts);

            popup.addPopupMenuListener(new PopupMenuListener() {
                public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                    menuSigType.removeAll();
                    boolean isAuto = (activeFunction != null && !activeFunction.userExplicitType);
                    JMenuItem itAuto = new JMenuItem("Auto-detect");
                    itAuto.setIcon(new MenuCheckIcon(isAuto));
                    styleMenuItem(itAuto);
                    if (isAuto) {
                        itAuto.setFont(new Font("Consolas", Font.BOLD, 14));
                        itAuto.setForeground(Color.WHITE);
                    }
                    itAuto.addActionListener(ev -> {
                        for (FunctionEntry fe : visibleFunctions) {
                            fe.userExplicitType = false;
                            fe.signalType = detectSignalType(fe.textField.getText());
                            fe.isDiscretized = (fe.signalType == SignalType.DISCRETE_APERIODIC
                                    || fe.signalType == SignalType.DISCRETE_PERIODIC
                                    || fe.signalType == SignalType.DISCRETE_RANDOM);
                            updateSignalTypeUI(fe);
                        }
                        graphPanel.repaint();
                        rightSidebarPanel.repaint();
                    });
                    menuSigType.add(itAuto);

                    JSeparator sep = new JSeparator();
                    sep.setBackground(COLOR_SIDEBAR);
                    sep.setForeground(COLOR_GRID);
                    menuSigType.add(sep);

                    for (SignalType st : SignalType.values()) {
                        boolean sel = (activeFunction != null && activeFunction.userExplicitType
                                && activeFunction.signalType == st);
                        JMenuItem it = new JMenuItem(st.shortLabel + " - " + st.fullName);
                        it.setIcon(new MenuCheckIcon(sel));
                        styleMenuItem(it);
                        if (sel) {
                            it.setFont(new Font("Consolas", Font.BOLD, 14));
                            it.setForeground(Color.WHITE);
                        }
                        it.addActionListener(ev -> {
                            for (FunctionEntry fe : visibleFunctions) {
                                fe.userExplicitType = true;
                                fe.signalType = st;
                                fe.isDiscretized = (st == SignalType.DISCRETE_APERIODIC
                                        || st == SignalType.DISCRETE_PERIODIC || st == SignalType.DISCRETE_RANDOM);
                                updateSignalTypeUI(fe);
                            }
                            graphPanel.repaint();
                            rightSidebarPanel.repaint();
                        });
                        menuSigType.add(it);
                    }

                    FunctionEntry.DisplayMode currentMode = activeFunction != null ? activeFunction.displayMode
                            : FunctionEntry.DisplayMode.NORMAL;
                    itemPhaseAmp
                            .setIcon(new MenuCheckIcon(currentMode == FunctionEntry.DisplayMode.SPECTRUM_AMP_PHASE));
                    itemPower.setIcon(new MenuCheckIcon(currentMode == FunctionEntry.DisplayMode.SPECTRUM_POWER));
                }

                public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                    if (isOpen) {
                        isOpen = false;
                        animate();
                        lastCloseTime = System.currentTimeMillis();
                    }
                }

                public void popupMenuCanceled(PopupMenuEvent e) {
                }
            });

            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (System.currentTimeMillis() - lastCloseTime < 150)
                        return;
                    isOpen = !isOpen;
                    if (isOpen) {
                        popup.show(HamburgerMenuButton.this, -popup.getPreferredSize().width + getWidth(), getHeight());
                    } else {
                        popup.setVisible(false);
                    }
                    animate();
                }
            });
        }

        private void animate() {
            if (timer != null)
                timer.stop();
            timer = new Timer(16, e -> {
                progress += isOpen ? 0.15f : -0.15f;
                if (progress >= 1f) {
                    progress = 1f;
                    timer.stop();
                }
                if (progress <= 0f) {
                    progress = 0f;
                    timer.stop();
                }
                repaint();
            });
            timer.start();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setColor(COLOR_FG);
            g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            int w = getWidth();
            int h = getHeight();
            int cx = w / 2;
            int cy = h / 2;

            int len = 16;
            int dy = 5;

            g2.translate(cx, cy - dy * (1 - progress));
            g2.rotate(Math.PI / 4 * progress);
            g2.drawLine(-len / 2, 0, len / 2, 0);
            g2.rotate(-Math.PI / 4 * progress);
            g2.translate(-cx, -(cy - dy * (1 - progress)));

            if (progress < 1f) {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f - progress));
                g2.drawLine(cx - len / 2, cy, cx + len / 2, cy);
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
            }

            g2.translate(cx, cy + dy * (1 - progress));
            g2.rotate(-Math.PI / 4 * progress);
            g2.drawLine(-len / 2, 0, len / 2, 0);
            g2.rotate(Math.PI / 4 * progress);
            g2.translate(-cx, -(cy + dy * (1 - progress)));
        }
    }

    private class CustomScrollBarUI extends BasicScrollBarUI {
        @Override
        protected void configureScrollBarColors() {
            this.thumbColor = new Color(80, 84, 92);
            this.trackColor = COLOR_SIDEBAR;
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
            g.setColor(trackColor);
            g.fillRect(trackBounds.x, trackBounds.y, trackBounds.width, trackBounds.height);
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
            if (thumbBounds.isEmpty() || !scrollbar.isEnabled())
                return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(thumbColor);
            g2.fillRoundRect(thumbBounds.x, thumbBounds.y, thumbBounds.width, thumbBounds.height, 6, 6);
            g2.dispose();
        }

        @Override
        protected JButton createDecreaseButton(int orientation) {
            return createZeroButton();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return createZeroButton();
        }

        private JButton createZeroButton() {
            JButton jbutton = new JButton();
            jbutton.setPreferredSize(new Dimension(0, 0));
            jbutton.setMinimumSize(new Dimension(0, 0));
            jbutton.setMaximumSize(new Dimension(0, 0));
            return jbutton;
        }
    }

    public static void main(String[] args) {
        java.awt.SplashScreen splash = java.awt.SplashScreen.getSplashScreen();
        if (splash != null) {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException ignored) {}
        }
        SwingUtilities.invokeLater(() -> {
            new SpectraGraphApp().setVisible(true);
        });
    }
}