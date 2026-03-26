package com.whq.app.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.eclipse.swt.graphics.Device;
import org.eclipse.swt.graphics.Font;
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

    List<String> expanded = new ArrayList<>();
    if (candidates != null) {
      for (String candidate : candidates) {
        addCandidate(expanded, candidate);
      }
    }

    String fromLists = findFontInDeviceLists(device, expanded);
    if (fromLists != null) {
      return fromLists;
    }

    String fromProbe = probeFontCreation(device, expanded);
    if (fromProbe != null) {
      return fromProbe;
    }

    String normalizedFallback = normalizeFontName(fallback);
    if (normalizedFallback != null) {
      String probedFallback = probeFontCreation(device, List.of(fallback));
      if (probedFallback != null && normalizedFallback.equals(normalizeFontName(probedFallback))) {
        return probedFallback;
      }
    }

    return fallback;
  }

  private static String findFontInDeviceLists(Device device, List<String> candidates) {
    String fromScalable = findFontInList(device.getFontList(null, true), candidates);
    if (fromScalable != null) {
      return fromScalable;
    }
    return findFontInList(device.getFontList(null, false), candidates);
  }

  private static String findFontInList(FontData[] available, List<String> candidates) {
    if (available == null || available.length == 0) {
      return null;
    }

    for (String candidate : candidates) {
      for (FontData data : available) {
        if (fontNamesMatch(candidate, data.getName())) {
          return data.getName();
        }
      }
    }

    return null;
  }

  private static String probeFontCreation(Device device, List<String> candidates) {
    for (String candidate : candidates) {
      Font probe = null;
      try {
        probe = new Font(device, candidate, 12, 0);
        FontData[] data = probe.getFontData();
        if (data != null && data.length > 0 && fontNamesMatch(candidate, data[0].getName())) {
          return data[0].getName();
        }
      } catch (RuntimeException ignored) {
        // Ignore probe failures and continue trying other candidates.
      } finally {
        if (probe != null && !probe.isDisposed()) {
          probe.dispose();
        }
      }
    }
    return null;
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
      case "casablanca antique" -> {
        candidates.add("CasablancaAntique");
        candidates.add("Caslon Antique");
      }
      case "newtext bk bt" -> candidates.add("Book Antiqua");
      case "copperplate" -> candidates.add("Copperplate Gothic Bold");
      default -> {
      }
    }
  }

  private static boolean fontNamesMatch(String left, String right) {
    String normalizedLeft = normalizeFontName(left);
    String normalizedRight = normalizeFontName(right);
    return normalizedLeft != null && normalizedLeft.equals(normalizedRight);
  }

  private static String normalizeFontName(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value
        .toLowerCase(Locale.ROOT)
        .replace(" ", "")
        .replace("-", "")
        .replace("_", "");
  }
}
