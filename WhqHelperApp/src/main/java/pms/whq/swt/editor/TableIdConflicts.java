package pms.whq.swt.editor;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import pms.whq.data.TableIds;
import pms.whq.xml.XmlContentService.TableDefinition;
import pms.whq.xml.XmlContentService.TableFileModel;

/**
 * Comprueba que el id de una tabla que se va a guardar no lo use ya otra tabla (del mismo fichero o
 * de cualquier otro, base o del usuario): el id indexa el estado activo y las referencias tableRef.
 */
final class TableIdConflicts {

  /** Tabla que ya usa el id y fichero en el que esta. */
  record Conflict(String id, String tableName, Path file) {
  }

  private TableIdConflicts() {
  }

  /** Id con el que se guardara la tabla: el que trae o, si no trae, el slug de su nombre. */
  static String effectiveId(TableDefinition table) {
    String id = table == null || table.id == null ? "" : table.id.trim();
    return id.isEmpty() ? TableIds.fromName(table == null ? "" : table.name) : id;
  }

  /**
   * Primera tabla, distinta de la que se guarda, que ya usa su id.
   *
   * @param table la tabla que se va a guardar
   * @param ownIndex su posicion en {@code targetModel}, o -1 si es nueva
   * @param targetFile el fichero en el que se guarda
   * @param targetModel el contenido de ese fichero en el editor
   * @param otherFiles el resto de ficheros de tablas, ya cargados
   * @return el conflicto, o null si el id esta libre
   */
  static Conflict find(
      TableDefinition table,
      int ownIndex,
      Path targetFile,
      TableFileModel targetModel,
      Map<Path, TableFileModel> otherFiles) {
    String id = effectiveId(table);
    List<TableDefinition> sameFile = targetModel == null ? List.of() : targetModel.tables;
    for (int i = 0; i < sameFile.size(); i++) {
      if (i != ownIndex && id.equals(effectiveId(sameFile.get(i)))) {
        return new Conflict(id, sameFile.get(i).name, targetFile);
      }
    }
    Path normalizedTarget = targetFile == null ? null : targetFile.toAbsolutePath().normalize();
    for (Map.Entry<Path, TableFileModel> other : otherFiles.entrySet()) {
      if (other.getKey().toAbsolutePath().normalize().equals(normalizedTarget) || other.getValue() == null) {
        continue;
      }
      for (TableDefinition candidate : other.getValue().tables) {
        if (id.equals(effectiveId(candidate))) {
          return new Conflict(id, candidate.name, other.getKey());
        }
      }
    }
    return null;
  }
}
