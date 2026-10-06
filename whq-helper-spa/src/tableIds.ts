import type { AppSettings, ContentRepository, TableModel } from './types';

/**
 * Id estable de una tabla a partir de su nombre, para las tablas que no traen atributo id
 * (ficheros antiguos del usuario). Mismo algoritmo que Java (TableIds), el validador y el que
 * genero los ids base: sin acentos, en minusculas, cada tramo fuera de [a-z0-9] pasa a '-', sin
 * '-' en los extremos y "table" si no queda nada.
 */
export function tableIdFromName(name: string): string {
  const slug = (name ?? '')
    .normalize('NFD')
    .replace(/\p{M}+/gu, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
  return slug || 'table';
}

/**
 * Tabla a la que apunta una referencia (atributo name de tableRef): primero por id estable, para
 * que renombrar la tabla no rompa la referencia, y si no, por nombre visible (referencias
 * antiguas). Igual que Java (Table.findIn).
 */
export function findReferencedTable(tables: Map<string, TableModel>, reference: string): TableModel | undefined {
  const trimmed = (reference ?? '').trim();
  if (!trimmed) {
    return undefined;
  }
  for (const table of tables.values()) {
    if (table.id === trimmed) {
      return table;
    }
  }
  return tables.get(reference);
}

/**
 * Otra tabla que ya usa el id: dos tablas con el mismo id comparten estado activo y las referencias
 * por id irian a una sola. {@code ownName} es el nombre guardado de la tabla que se edita (no
 * cuenta como choque); se omite para una tabla nueva.
 */
export function findTableIdConflict(
  tables: Map<string, TableModel>,
  id: string,
  ownName?: string
): TableModel | undefined {
  for (const table of tables.values()) {
    if (table.id === id && table.name !== ownName) {
      return table;
    }
  }
  return undefined;
}

/** Ids que comparten varias tablas, con sus nombres (XML editados a mano: los editores ya no lo permiten). */
export function findDuplicateTableIds(tables: Map<string, TableModel>): Map<string, string[]> {
  const namesById = new Map<string, string[]>();
  for (const table of tables.values()) {
    namesById.set(table.id, [...(namesById.get(table.id) ?? []), table.name]);
  }
  return new Map([...namesById].filter(([, names]) => names.length > 1));
}

const warnedOrphans = new Set<string>();

/**
 * Pasa el estado activo guardado por nombre visible (formato antiguo) a tableActiveById. Si ya
 * hay valor por id, gana ese (es el mas reciente) y el antiguo se descarta. Las entradas que no
 * casan con ninguna tabla se conservan intactas y se avisan una vez por consola.
 *
 * @returns los nombres antiguos que no casan con ninguna tabla
 */
export function migrateLegacyTableActive(repository: ContentRepository, settings: AppSettings): string[] {
  const orphans: string[] = [];
  for (const [name, active] of Object.entries(settings.legacyTableActive)) {
    const table = repository.tables.get(name);
    if (!table) {
      orphans.push(name);
      continue;
    }
    if (settings.tableActiveById[table.id] === undefined) {
      settings.tableActiveById[table.id] = active;
    }
    delete settings.legacyTableActive[name];
  }

  const newOrphans = orphans.filter((name) => !warnedOrphans.has(name));
  if (newOrphans.length > 0) {
    newOrphans.forEach((name) => warnedOrphans.add(name));
    console.warn(`Estado activo guardado para tablas que no existen (se conserva): ${newOrphans.join(', ')}`);
  }
  return orphans;
}
