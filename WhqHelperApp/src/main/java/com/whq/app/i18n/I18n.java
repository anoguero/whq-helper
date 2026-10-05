package com.whq.app.i18n;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import com.whq.app.AppPaths;

public final class I18n {

  private static final String UI_RELATIVE_DIR = "data/i18n";

  // Textos de interfaz en shared/data/i18n/ui-{es,en}.xml (mismo formato que content-*.xml).
  // Si init() no se ha llamado, se cargan del shared home resuelto desde el directorio de trabajo.
  private static volatile Map<String, String> es;
  private static volatile Map<String, String> en;

  private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();
  private static Language currentLanguage = Language.ES;

  private I18n() {
  }

  /** Carga los textos de interfaz del shared home de projectRoot. Llamar antes de construir la UI. */
  public static synchronized void init(Path projectRoot) {
    Path directory = AppPaths.sharedPath(projectRoot, UI_RELATIVE_DIR);
    es = ContentTranslations.parse(directory.resolve("ui-es.xml"));
    en = ContentTranslations.parse(directory.resolve("ui-en.xml"));
  }

  private static void ensureLoaded() {
    if (es == null || en == null) {
      init(Path.of(""));
    }
  }

  public static Language getLanguage() {
    return currentLanguage;
  }

  public static void setLanguage(Language language) {
    if (language == null || language == currentLanguage) {
      return;
    }
    currentLanguage = language;
    for (Runnable listener : LISTENERS) {
      listener.run();
    }
  }

  public static String t(String key) {
    if (key == null || key.isEmpty()) {
      return "";
    }

    ensureLoaded();
    String value = currentLanguage == Language.EN ? en.get(key) : es.get(key);
    if (value != null) {
      return value;
    }

    String fallback = currentLanguage == Language.EN ? es.get(key) : en.get(key);
    return fallback != null ? fallback : key;
  }

  /** Texto con placeholders con nombre: "{count} cartas" con params {count=3} da "3 cartas". */
  public static String t(String key, Map<String, ?> params) {
    String text = t(key);
    if (params == null) {
      return text;
    }
    for (Map.Entry<String, ?> param : params.entrySet()) {
      text = text.replace("{" + param.getKey() + "}", String.valueOf(param.getValue()));
    }
    return text;
  }

  public static void addListener(Runnable listener) {
    if (listener != null) {
      LISTENERS.add(listener);
    }
  }

  public static void removeListener(Runnable listener) {
    if (listener != null) {
      LISTENERS.remove(listener);
    }
  }
}
