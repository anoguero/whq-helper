package pms.whq.content;

import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

import pms.whq.data.EventEntry;
import pms.whq.data.MonsterEntry;
import pms.whq.data.Table;
import pms.whq.data.TableKind;
import pms.whq.data.TableReferenceEntry;

public class RuntimeContentValidator {

  public void pruneInvalidTableEntries(ContentRepository repository, Consumer<ContentIssue> issueConsumer) {
    for (Table table : repository.tables().values()) {
      pruneInvalidEntries(table.getMonsterEntries(), table, repository, issueConsumer);
      pruneInvalidEntries(table.getEventEntries(), table, repository, issueConsumer);
    }
  }

  @SuppressWarnings("unchecked")
  private void pruneInvalidEntries(
      List<Object> entries,
      Table table,
      ContentRepository repository,
      Consumer<ContentIssue> issueConsumer) {
    Iterator<Object> iterator = entries.iterator();
    String tableName = table.getName();

    while (iterator.hasNext()) {
      Object entry = iterator.next();

      if (entry instanceof List<?>) {
        pruneInvalidEntries((List<Object>) entry, table, repository, issueConsumer);
      } else if (entry instanceof MonsterEntry monsterEntry) {
        if (!repository.monsters().containsKey(monsterEntry.id)) {
          issueConsumer.accept(
              new ContentIssue(
                  "Monster Not Found",
                  "While loading table ["
                      + tableName
                      + "], the monster with id ["
                      + monsterEntry.id
                      + "] could not be found. This entry will be removed from the table."));
          iterator.remove();
        }
      } else if (entry instanceof TableReferenceEntry tableReferenceEntry) {
        if (!repository.tables().containsKey(tableReferenceEntry.tableName)) {
          issueConsumer.accept(
              new ContentIssue(
                  "Table Reference Not Found",
                  "While loading table ["
                      + tableName
                      + "], the referenced table ["
                      + tableReferenceEntry.tableName
                      + "] could not be found. This entry will be removed from the table."));
          iterator.remove();
        }
      } else if (entry instanceof EventEntry eventEntry) {
        boolean missing;
        if (table.getTableKind() == TableKind.TRAVEL) {
          missing = !repository.travelEvents().containsKey(eventEntry.id);
        } else if (table.getTableKind() == TableKind.SETTLEMENT) {
          missing = !repository.settlementEvents().containsKey(eventEntry.id);
        } else {
          missing = !repository.events().containsKey(eventEntry.id);
        }

        if (missing) {
          issueConsumer.accept(
              new ContentIssue(
                  "Event Not Found",
                  "While loading table ["
                      + tableName
                      + "], the event with id ["
                      + eventEntry.id
                      + "] could not be found. This entry will be removed from the table."));
          iterator.remove();
        }
      } else {
        String type = entry == null ? "null" : entry.getClass().getName();
        issueConsumer.accept(
            new ContentIssue(
                "Unknown Entry Type",
                "While loading table ["
                    + tableName
                    + "], an unknown entry type ("
                    + type
                    + ") was found. This entry will be removed from the table."));
        iterator.remove();
      }
    }
  }
}
