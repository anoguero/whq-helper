package pms.whq.content;

import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

import pms.whq.data.DrawableEntry;
import pms.whq.data.EventEntry;
import pms.whq.data.MonsterEntry;
import pms.whq.data.MonsterGroup;
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

  private void pruneInvalidEntries(
      List<DrawableEntry> entries,
      Table table,
      ContentRepository repository,
      Consumer<ContentIssue> issueConsumer) {
    Iterator<DrawableEntry> iterator = entries.iterator();
    String tableName = table.getName();

    while (iterator.hasNext()) {
      DrawableEntry entry = iterator.next();

      switch (entry) {
        case MonsterGroup group -> pruneInvalidEntries(group, table, repository, issueConsumer);
        case MonsterEntry monsterEntry -> {
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
        }
        case TableReferenceEntry tableReferenceEntry -> {
          if (Table.findIn(repository.tables(), tableReferenceEntry.tableName) == null) {
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
        }
        case EventEntry eventEntry -> {
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
        }
        case null -> {
          issueConsumer.accept(
              new ContentIssue(
                  "Unknown Entry Type",
                  "While loading table ["
                      + tableName
                      + "], an unknown entry type (null) was found. This entry will be removed from the table."));
          iterator.remove();
        }
      }
    }
  }
}
