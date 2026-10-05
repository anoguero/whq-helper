package pms.whq.data;

/**
 * Cualquier cosa que se pueda sortear de una {@link Table} o un {@link Deck}: un monstruo, un
 * evento, un grupo de monstruos ya resuelto, o una referencia a otra tabla pendiente de resolver.
 *
 * <p>Marcador puro (sin metodos): el motor de sorteo (TableDrawService, DeckBuilderService, ...)
 * distingue el tipo concreto con {@code switch}/{@code instanceof} sobre patrones, aprovechando
 * que al ser {@code sealed} el compilador exige cubrir los cuatro casos.
 */
public sealed interface DrawableEntry permits MonsterEntry, EventEntry, MonsterGroup, TableReferenceEntry {
}
