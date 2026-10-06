package pms.whq.content;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import pms.whq.Settings;
import pms.whq.data.Table;

/**
 * Estado activo de las tablas en settings.cfg, guardado por id estable: table.&lt;id&gt;.active.
 *
 * <p>Antes se guardaba por nombre visible (&lt;nombre&gt;.active) y renombrar una tabla perdia su
 * configuracion. {@link #migrateLegacyKeys} convierte ese formato una sola vez.
 */
public final class TableActiveSettings {

  private static final String SUFFIX = ".active";
  private static final Pattern ID_KEY = Pattern.compile("table\\.[a-z0-9]+(-[a-z0-9]+)*\\.active");

  private TableActiveSettings() {
  }

  public static String settingKey(Table table) {
    return "table." + table.getId() + SUFFIX;
  }

  /**
   * Convierte las claves &lt;nombre&gt;.active de las tablas cargadas a table.&lt;id&gt;.active, conservando
   * el valor, y guarda settings.cfg si ha cambiado algo. Si ya existe la clave por id, gana esa (es
   * la mas reciente) y la antigua se descarta. Una clave antigua que no casa con ninguna tabla se
   * conserva intacta y se reporta.
   *
   * @return true si se ha migrado alguna clave
   */
  public static boolean migrateLegacyKeys(Map<String, Table> tablesByName, Consumer<ContentIssue> issueConsumer) {
    boolean migrated = false;
    List<String> orphans = new ArrayList<>();

    for (String key : Settings.settingNames()) {
      if (!key.endsWith(SUFFIX) || ID_KEY.matcher(key).matches()) {
        continue;
      }
      Table table = tablesByName.get(key.substring(0, key.length() - SUFFIX.length()));
      if (table == null) {
        orphans.add(key);
        continue;
      }
      String idKey = settingKey(table);
      if (Settings.getSetting(idKey) == null) {
        Settings.setSetting(idKey, Settings.getSetting(key));
      }
      Settings.removeSetting(key);
      migrated = true;
    }

    if (migrated) {
      Settings.save();
    }
    if (!orphans.isEmpty()) {
      issueConsumer.accept(
          new ContentIssue(
              "Table Settings Without Table",
              "These settings use the old per-name format and match no loaded table, so they were"
                  + " kept unchanged: "
                  + String.join(", ", orphans)
                  + "."));
    }
    return migrated;
  }

  public static boolean isActive(Table table) {
    return Settings.getSettingAsBool(settingKey(table), true);
  }

  public static void store(Table table) {
    Settings.setSetting(settingKey(table), Boolean.toString(table.isActive()));
  }
}
