# Image to ASCII Art Converter

A desktop application that converts raster images into ASCII art using a luminance-based character mapping algorithm. Built entirely in Java with a custom Swing GUI.

## Features

- **ASCII conversion** — maps each pixel's brightness to a character from the Paul Bourke 70-character gradient (`$@B%8&WM#...` → ` `) using Rec. 601 luma coefficients for accurate grayscale perception
- **Pre-rendered buffer** — renders the full ASCII output to an off-screen `BufferedImage` once, making scroll and zoom O(1) regardless of image size
- **Zoom** — smooth zoom via `+`/`−` buttons, `Ctrl`+scroll, or `Ctrl`+`=`/`-` keyboard shortcuts; nearest-neighbor interpolation when zoomed in for crisp text, bilinear when zoomed out
- **Pan** — click-and-drag or arrow keys; two-finger trackpad scroll handled natively by the scroll pane
- **Fit to window** — auto-fits on every transform; `Fit` button and `Ctrl`+`0` to reset
- **Scale control** — downscale the image before conversion (0.1–1.0) to trade detail for density
- **Grayscale export** — writes the intermediate grayscale image alongside the source file automatically
- **Non-blocking UI** — image processing runs on a `SwingWorker` thread; controls disable during processing and re-enable with a status bar update on completion
- **Error handling** — validation on empty path, readable error dialogs, and `IOException` propagation instead of `System.exit`

## Technical Highlights

| Area | Detail |
|---|---|
| Language | Java 17+ |
| GUI | Java Swing — custom `JPanel` subclass with `paintComponent` override |
| Image processing | `BufferedImage` pixel manipulation, `ImageIO`, `Graphics2D` scaling |
| Concurrency | `SwingWorker` + EDT compliance |
| Rendering | Off-screen buffer pre-render; interpolation mode switches at zoom = 1× |
| Keyboard | `InputMap` / `ActionMap` with `WHEN_IN_FOCUSED_WINDOW` scope |
| Character mapping | Static lookup table built once at class load; Paul Bourke ASCII gradient |
| Luminance | Rec. 601: `0.299R + 0.587G + 0.114B` |

## Usage

```
java -jar ImageToText.jar
```

Requires Java 17 or later.

1. Enter or browse to an image path (JPG, PNG, GIF)
2. Set a scale factor (default 1.0)
3. Click **Transform**
4. Pan with drag or arrow keys, zoom with `Ctrl`+scroll or the `+`/`−` buttons
