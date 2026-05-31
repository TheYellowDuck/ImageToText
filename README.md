# Image to ASCII Art Converter

A desktop Java application that converts raster images into ASCII art in real time. Designed and built from scratch with a fully custom Swing GUI, off-screen rendering pipeline, and smooth zoom/pan interaction.

---

## Overview

The app takes any JPG, PNG, or GIF image, converts each pixel to a grayscale luminance value using the Rec. 601 standard, and maps it to one of 70 ASCII characters ordered by visual density (Paul Bourke gradient). The result is rendered into a scrollable, zoomable panel and the intermediate grayscale image is saved to disk automatically.

---

## Skills Demonstrated

### Java & Software Engineering

- Swing GUI built from scratch — custom layouts, components, and event handling
- `SwingWorker` for background processing, keeping the UI fully responsive during conversion
- Off-screen `BufferedImage` pre-render so scroll and zoom are O(1) regardless of image size
- `InputMap` / `ActionMap` keyboard shortcut system with `WHEN_IN_FOCUSED_WINDOW` scope
- Static lookup table built once at class load time for the brightness → character mapping
- `StringBuilder` over string concatenation in hot pixel loops for O(n) performance
- `IOException` propagation and user-facing error dialogs — no silent failures

### Image Processing

- Pixel-level RGB manipulation via `BufferedImage.getRGB` / `setRGB`
- Grayscale conversion using Rec. 601 luma coefficients (`0.299R + 0.587G + 0.114B`)
- Smooth image downscaling with `SCALE_SMOOTH` before conversion
- Nearest-neighbor interpolation when zoomed in (crisp), bilinear when zoomed out (smooth)

### UI/UX

- Drag-to-pan with `MouseAdapter` using screen coordinates for stable delta tracking
- Ctrl+scroll zoom with `getPreciseWheelRotation` for smooth trackpad input
- Fit-to-window on every transform, with manual zoom (+/−/Fit) and keyboard shortcuts
- Two-row control panel using nested `BorderLayout` — zoom controls always visible at any window width
- Status bar with live feedback: processing state, image dimensions, character grid size

---

## Features

| Feature | Details |
|---|---|
| ASCII conversion | 70-character Paul Bourke gradient, Rec. 601 luma |
| Zoom | Buttons, Ctrl+scroll, Ctrl+=/- keyboard shortcuts |
| Pan | Click+drag, arrow keys, two-finger trackpad scroll |
| Fit to window | Auto on load, Fit button, Ctrl+0 |
| Scale | 0.1–1.0 downscale before conversion |
| Grayscale export | Saved alongside source image automatically |
| Performance | O(1) scroll/zoom via pre-rendered buffer |
| Concurrency | SwingWorker — UI stays responsive during processing |

---

## Usage

```sh
java -jar ImageToText.jar
```

Requires Java 17 or later.

1. Browse to or paste an image path (JPG, PNG, GIF)
2. Set a scale factor (default 1.0 — lower for denser output)
3. Click **Transform**
4. Zoom with `Ctrl`+scroll or `+`/`−`, pan by dragging or using arrow keys
