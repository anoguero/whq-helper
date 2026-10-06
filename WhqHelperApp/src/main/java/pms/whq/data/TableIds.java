package pms.whq.data;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Id estable de una tabla, con el que se guarda su configuracion (table.&lt;id&gt;.active).
 *
 * <p>Las tablas base lo traen en el atributo id. Una tabla sin id (ficheros antiguos del usuario)
 * usa el slug de su nombre. Mismo algoritmo que la SPA (tableIds.ts), el validador
 * (validate_shared_data.py) y el que genero los ids base: sin acentos, en minusculas, cada tramo de
 * caracteres fuera de [a-z0-9] pasa a '-', sin '-' en los extremos y "table" si no queda nada.
 */
public final class TableIds {

  private TableIds() {
  }

  public static String fromName(String name) {
    String withoutAccents =
        Normalizer.normalize(name == null ? "" : name, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
    String slug =
        withoutAccents.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
    return slug.isEmpty() ? "table" : slug;
  }
}
