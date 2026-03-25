package com.whq.app.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.eclipse.swt.graphics.Device;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.widgets.Display;

public final class FontResources {

  private FontResources() {
  }

  public static void loadBundledFonts(Display display, Path projectRoot) {
    if (display == null || projectRoot == null) {
      return;
    }

    Path fontDir = projectRoot.toAbsolutePath().normalize().resolve("data/fonts");
    if (!Files.isDirectory(fontDir)) {
      return;
    }

    try (var stream = Files.list(fontDir)) {
      stream
          .filter(Files::isRegularFile)
          .filter(FontResources::isSupportedFontFile)
          .forEach(path -> loadFont(display, path));
    } catch (IOException ignored) {
      // Keep SWT/system fallbacks if bundled fonts cannot be read.
    }
  }

  public static String pickAvailableFont(Device device, String fallback, String... candidates) {
    if (device == null) {
      return fallback;
    }

    FontData[] available = device.getFontList(null, true);
    List<String> expanded = new ArrayList<>();
    if (candidates != null) {
      for (String candidate : candidates) {
        addCandidate(expanded, candidate);
      }
    }

    for (String candidate : expanded) {
      for (FontData data : available) {
        if (candidate.equalsIgnoreCase(data.getName())) {
          return data.getName();
        }
      }
    }

    return fallback;
  }

  private static boolean isSupportedFontFile(Path path) {
    String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
    return name.endsWith(".ttf") || name.endsWith(".otf");
  }

  private static void loadFont(Display display, Path path) {
    try {
      display.loadFont(path.toString());
    } catch (RuntimeException ignored) {
      // Ignore unsupported or malformed font files.
    }
  }

  private static void addCandidate(List<String> candidates, String candidate) {
    if (candidate == null || candidate.isBlank()) {
      return;
    }

    candidates.add(candidate);
    switch (candidate.toLowerCase(Locale.ROOT)) {
      case "casablanca antique" -> candidates.add("Caslon Antique");
      case "newtext bk bt" -> candidates.add("Book Antiqua");
      case "copperplate" -> candidates.add("Copperplate Gothic Bold");
      default -> {
      }
    }
  }
}
