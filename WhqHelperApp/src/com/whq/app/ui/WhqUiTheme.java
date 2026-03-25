package com.whq.app.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Device;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Pattern;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Display;

public final class WhqUiTheme {

  public final Color shellBackground;
  public final Color panelBackground;
  public final Color panelBackgroundAlt;
  public final Color parchment;
  public final Color parchmentDeep;
  public final Color ink;
  public final Color mutedInk;
  public final Color brass;
  public final Color brassDark;
  public final Color ember;
  public final Color mist;

  public final Font heroTitleFont;
  public final Font heroSubtitleFont;
  public final Font sectionTitleFont;
  public final Font bodyFont;
  public final Font statFont;

  private final Display display;
  private final List<Font> fonts = new ArrayList<>();
  private final List<Color> colors = new ArrayList<>();
  private final Map<CacheKey, Image> surfaceCache = new HashMap<>();

  private final Image heroCastle;
  private final Image parchmentMap;
  private final Image manuscriptBorder;

  public WhqUiTheme(Display display, Path projectRoot) {
    this.display = display;

    shellBackground = color(20, 15, 12);
    panelBackground = color(36, 28, 22);
    panelBackgroundAlt = color(55, 43, 34);
    parchment = color(226, 214, 188);
    parchmentDeep = color(190, 168, 126);
    ink = color(39, 28, 18);
    mutedInk = color(111, 90, 69);
    brass = color(194, 155, 82);
    brassDark = color(106, 76, 37);
    ember = color(136, 55, 34);
    mist = color(222, 217, 205);

    heroTitleFont =
        font(
            new String[] {"Cinzel Decorative", "Cinzel", "Book Antiqua", "Times New Roman", "Serif"},
            26,
            SWT.BOLD);
    heroSubtitleFont =
        font(
            new String[] {"Book Antiqua", "Georgia", "Times New Roman", "Serif"},
            11,
            SWT.NORMAL);
    sectionTitleFont =
        font(
            new String[] {"Cinzel", "Book Antiqua", "Georgia", "Serif"},
            14,
            SWT.BOLD);
    bodyFont =
        font(
            new String[] {"Book Antiqua", "Georgia", "Times New Roman", "Serif"},
            10,
            SWT.NORMAL);
    statFont =
        font(
            new String[] {"Cinzel", "Book Antiqua", "Georgia", "Serif"},
            10,
            SWT.BOLD);

    heroCastle = loadImage(projectRoot.resolve("resources/ui/hero-castle.jpg"));
    parchmentMap = loadImage(projectRoot.resolve("resources/ui/parchment-map.jpg"));
    manuscriptBorder = loadImage(projectRoot.resolve("resources/ui/manuscript-border.jpg"));
  }

  public Image getHeroCastle() {
    return heroCastle;
  }

  public Image getParchmentMap() {
    return parchmentMap;
  }

  public Image getManuscriptBorder() {
    return manuscriptBorder;
  }

  public void paintHeroBanner(GC gc, Rectangle area) {
    paintCachedSurface(gc, area, SurfaceKind.HERO);
  }

  public void paintParchmentPanel(GC gc, Rectangle area) {
    paintCachedSurface(gc, area, SurfaceKind.PARCHMENT);
  }

  public void paintDarkPanel(GC gc, Rectangle area) {
    paintCachedSurface(gc, area, SurfaceKind.DARK);
  }

  private void paintCachedSurface(GC gc, Rectangle area, SurfaceKind kind) {
    if (area.width <= 0 || area.height <= 0) {
      return;
    }

    Image surface = getCachedSurface(kind, area.width, area.height);
    if (surface != null && !surface.isDisposed()) {
      gc.drawImage(surface, area.x, area.y);
    }
  }

  private Image getCachedSurface(SurfaceKind kind, int width, int height) {
    CacheKey key = new CacheKey(kind, width, height);
    Image cached = surfaceCache.get(key);
    if (cached != null && !cached.isDisposed()) {
      return cached;
    }

    Image rendered = new Image(display, width, height);
    GC gc = new GC(rendered);
    try {
      switch (kind) {
        case HERO:
          renderHeroBanner(gc, new Rectangle(0, 0, width, height));
          break;
        case PARCHMENT:
          renderParchmentPanel(gc, new Rectangle(0, 0, width, height));
          break;
        case DARK:
          renderDarkPanel(gc, new Rectangle(0, 0, width, height));
          break;
        default:
          break;
      }
    } finally {
      gc.dispose();
    }

    surfaceCache.put(key, rendered);
    return rendered;
  }

  private void renderHeroBanner(GC gc, Rectangle area) {
    gc.setAdvanced(true);
    gc.setAntialias(SWT.ON);
    gc.setInterpolation(SWT.HIGH);

    gc.setBackground(shellBackground);
    gc.fillRectangle(area);

    drawCover(gc, parchmentMap, area, 90);

    Rectangle artArea =
        new Rectangle(area.x + (area.width / 3), area.y, (area.width * 2) / 3, area.height);
    drawCover(gc, heroCastle, artArea, 235);

    gc.setAlpha(170);
    Pattern leftFade =
        new Pattern(
            display,
            area.x,
            area.y,
            area.x + area.width,
            area.y,
            shellBackground,
            255,
            panelBackground,
            25);
    gc.setBackgroundPattern(leftFade);
    gc.fillRectangle(area);
    gc.setBackgroundPattern(null);
    leftFade.dispose();

    gc.setAlpha(120);
    gc.setBackground(brassDark);
    gc.fillRectangle(area.x + area.width - 40, area.y, 40, area.height);
    gc.setAlpha(255);

    if (manuscriptBorder != null) {
      Rectangle borderBounds = manuscriptBorder.getBounds();
      int borderWidth = Math.min(54, area.width / 8);
      gc.setAlpha(185);
      gc.drawImage(
          manuscriptBorder,
          0,
          0,
          borderBounds.width,
          borderBounds.height,
          area.x + area.width - borderWidth - 8,
          area.y + 6,
          borderWidth,
          Math.max(12, area.height - 12));
      gc.setAlpha(255);
    }

    drawFrame(gc, area, brass, brassDark, 3);
  }

  private void renderParchmentPanel(GC gc, Rectangle area) {
    if (area.width <= 0 || area.height <= 0) {
      return;
    }

    gc.setAdvanced(true);
    gc.setAntialias(SWT.ON);
    gc.setInterpolation(SWT.HIGH);

    gc.setBackground(parchment);
    gc.fillRectangle(area);
    drawCover(gc, parchmentMap, area, 65);

    gc.setAlpha(40);
    gc.setBackground(mist);
    gc.fillRectangle(area.x, area.y, area.width, Math.max(12, area.height / 4));
    gc.setAlpha(255);

    drawFrame(gc, area, brassDark, mutedInk, 2);
  }

  private void renderDarkPanel(GC gc, Rectangle area) {
    if (area.width <= 0 || area.height <= 0) {
      return;
    }

    gc.setAdvanced(true);
    gc.setAntialias(SWT.ON);
    gc.setInterpolation(SWT.HIGH);

    gc.setBackground(panelBackground);
    gc.fillRectangle(area);
    drawCover(gc, heroCastle, area, 28);
    drawCover(gc, parchmentMap, area, 22);

    gc.setAlpha(155);
    gc.setBackground(shellBackground);
    gc.fillRectangle(area);
    gc.setAlpha(255);

    gc.setAlpha(38);
    gc.setBackground(mist);
    gc.fillRectangle(area.x, area.y, area.width, Math.max(12, area.height / 5));
    gc.setAlpha(255);

    drawFrame(gc, area, brass, brassDark, 2);
  }

  public void drawFrame(GC gc, Rectangle area, Color outer, Color inner, int lineWidth) {
    if (area.width <= 6 || area.height <= 6) {
      return;
    }

    gc.setForeground(outer);
    gc.setLineWidth(Math.max(1, lineWidth));
    gc.drawRectangle(area.x, area.y, area.width - 1, area.height - 1);

    gc.setForeground(inner);
    gc.setLineWidth(1);
    gc.drawRectangle(area.x + 4, area.y + 4, area.width - 9, area.height - 9);
  }

  public void drawCover(GC gc, Image image, Rectangle area, int alpha) {
    if (image == null || area.width <= 0 || area.height <= 0) {
      return;
    }

    Rectangle source = image.getBounds();
    double scale = Math.max((double) area.width / source.width, (double) area.height / source.height);
    int width = Math.max(1, (int) Math.round(source.width * scale));
    int height = Math.max(1, (int) Math.round(source.height * scale));
    int x = area.x + ((area.width - width) / 2);
    int y = area.y + ((area.height - height) / 2);

    int previousAlpha = gc.getAlpha();
    gc.setAlpha(Math.max(0, Math.min(255, alpha)));
    gc.drawImage(image, 0, 0, source.width, source.height, x, y, width, height);
    gc.setAlpha(previousAlpha);
  }

  public void dispose() {
    for (Font font : fonts) {
      if (font != null && !font.isDisposed()) {
        font.dispose();
      }
    }
    for (Color color : colors) {
      if (color != null && !color.isDisposed()) {
        color.dispose();
      }
    }
    for (Image image : surfaceCache.values()) {
      disposeImage(image);
    }
    surfaceCache.clear();
    disposeImage(heroCastle);
    disposeImage(parchmentMap);
    disposeImage(manuscriptBorder);
  }

  private enum SurfaceKind {
    HERO,
    PARCHMENT,
    DARK
  }

  private static final class CacheKey {
    private final SurfaceKind kind;
    private final int width;
    private final int height;

    private CacheKey(SurfaceKind kind, int width, int height) {
      this.kind = kind;
      this.width = width;
      this.height = height;
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      }
      if (!(other instanceof CacheKey)) {
        return false;
      }
      CacheKey key = (CacheKey) other;
      return kind == key.kind && width == key.width && height == key.height;
    }

    @Override
    public int hashCode() {
      int result = kind.hashCode();
      result = (31 * result) + width;
      result = (31 * result) + height;
      return result;
    }
  }

  private Font font(String[] candidates, int height, int style) {
    String name = pickFont(candidates);
    Font font = new Font(display, name, height, style);
    fonts.add(font);
    return font;
  }

  private String pickFont(String[] candidates) {
    FontData[] available = display.getFontList(null, true);
    for (String candidate : candidates) {
      for (FontData data : available) {
        if (candidate.equalsIgnoreCase(data.getName())) {
          return data.getName();
        }
      }
    }
    return display.getSystemFont().getFontData()[0].getName();
  }

  private Color color(int r, int g, int b) {
    Color color = new Color(display, r, g, b);
    colors.add(color);
    return color;
  }

  private Image loadImage(Path path) {
    if (path == null || !Files.exists(path)) {
      return null;
    }

    try {
      return new Image((Device) display, path.toString());
    } catch (RuntimeException ex) {
      return null;
    }
  }

  private static void disposeImage(Image image) {
    if (image != null && !image.isDisposed()) {
      image.dispose();
    }
  }
}
