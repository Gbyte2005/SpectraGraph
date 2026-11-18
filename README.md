# SpectraGraph

**SpectraGraph** is a lightweight, zero-dependency mathematical graphing and signal analysis desktop application written in pure **Java**. Designed for instant startup and real-time responsiveness, it provides interactive multi-plotting, symbolic/numerical calculus, and one-click Fourier analysis without heavy external frameworks.

---

## Quick Start (Ready to Run)

No setup, compilation, or external runtime installation is required:

1. Download or clone this repository (`[https://github.com/Gbyte2005/SpectraGraph](https://github.com/Gbyte2005/SpectraGraph)`) or click **`Code` → `Download ZIP`** and extract the archive.
2. Double-click `SpectraGraph.exe` in the root folder to launch **SpectraGraph** (the bundled runtime is already included).

> **Note:** Full source files are included in the `sourceCode/` directory for inspection, learning, or custom builds.

---

## Key Features

### 1. Ergonomic Formula Input (`MathTextField`)
* **Natural Fraction & Derivative Entry:** Typing `d/` automatically formats a differential fraction in the denominator and places the cursor in the natural position once the variable of differentiation is entered.
* **Multi-Order Integrals:** Intuitive shorthand commands (`int`, `iint`, `iiint`, and higher orders) render structured integral expressions on the fly.
* **Clean Mathematical Rendering:** Real-time formatting of fractions, exponents, and nested expressions with fluid cursor navigation.

### 2. Signal Processing & Analysis Toolkit
* **Built-in Signal Primitives:** Native support for standard continuous and impulsive signal functions without manual piecewise definitions:
  * `rect(t)` — Rectangular pulse function
  * `tri(t)` — Triangular function
  * `sinc(t)` — Sinc function
  * `step(t)` — Heaviside unit step function
  * `trapz(t)` — Trapezoidal signal/integration primitive
  * `conj(z)` — Complex conjugate
* **One-Click Fourier Analysis:** Instant computation and visualization of frequency-domain spectra directly from plotted time-domain signals.
* **Built-in Scientific & Signal Calculator:** Dedicated calculator panel tailored for quick signal-theory evaluations alongside the main viewport.

### 3. Interactive Multi-Plotting & Domain Control
* **Simultaneous Multi-Function Plotting:** Overlay, toggle, and compare multiple expressions in real time.
* **Granular Domain Control:** Restrict or modify the domain of definition ($x$-range / support) for a **single function**, a **selected group**, or **all plotted functions** simultaneously—ideal for demonstrating windowing, truncation, and time-frequency duality.

---

## Architecture & Repository Structure

* **Self-Contained & Zero External Dependencies:** Built directly on standard SE libraries with an embedded runtime, ensuring instant startup, low memory footprint, and smooth 60+ fps interactive rendering.
* **Custom Rendering & Layout Engine:** Custom mathematical text fields and plotting canvas engineered from scratch to eliminate input lag during live demonstrations.

```text
SpectraGraph/
├── SpectraGraph.exe      # Pre-compiled ready-to-run executable
├── app/                  # Application binaries and resources
├── runtime/              # Bundled runtime environment (no external install needed)
├── sourceCode/           # Complete source files
└── README.md
```

---

## Quick Syntax Reference

| Input Shorthand | Rendered / Evaluated Operation |
| :--- | :--- |
| `d/` + `<var>` | Differential operator $\frac{d}{d\text{var}}$ with automatic cursor placement |
| `int` / `iint` / `iiint` | Single, double, and triple integrals |
| `rect(x)`, `tri(x)`, `sinc(x)` | Standard window and transform pair primitives |
| `step(x)`, `trapz(x)`, `conj(x)` | Unit step, trapezoidal primitive, and complex conjugate |
