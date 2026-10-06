package pms.whq.swt.editor;

import static pms.whq.swt.editor.EditorSupport.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TabFolder;

import com.whq.app.adventure.XmlObjectiveRoomAdventureRepository;
import com.whq.app.i18n.EditableContentTranslations;
import com.whq.app.i18n.I18n;
import com.whq.app.storage.XmlDungeonCardStore;

import pms.whq.xml.XmlContentService;
import pms.whq.xml.XmlContentService.EventEntry;

/**
 * Pestana del editor de contenido. Expone las dependencias del dialogo con los mismos nombres de
 * campo que tenian en EventContentEditorDialog, para que el codigo de cada pestana se mueva tal cual.
 */
public abstract class EditorTab {

  protected final Shell parent;
  protected final Path projectRoot;
  protected final XmlContentService service;
  protected final XmlDungeonCardStore dungeonCardStore;
  protected final XmlObjectiveRoomAdventureRepository objectiveRoomAdventureRepository;
  protected final Runnable onContentSaved;

  protected EditorTab(EditorContext context) {
    this.parent = context.parent();
    this.projectRoot = context.projectRoot();
    this.service = context.service();
    this.dungeonCardStore = context.dungeonCardStore();
    this.objectiveRoomAdventureRepository = context.objectiveRoomAdventureRepository();
    this.onContentSaved = context.onContentSaved();
  }

  public abstract void create(TabFolder tabs, Shell dialog);

  protected EditableContentTranslations loadEditableTranslations() {
    return EditableContentTranslations.load(projectRoot, I18n.getLanguage());
  }

  protected void notifySaved() {
    if (onContentSaved != null) {
      onContentSaved.run();
    }
  }

  protected List<Path> listNonTreasureEventFiles() throws Exception {
    List<Path> files = new ArrayList<>();
    for (Path file : service.listEventFiles()) {
      if (!isTreasureFile(file)) {
        files.add(file);
      }
    }
    return files;
  }

  protected boolean isDungeonTreasureEntry(EventEntry entry) {
    String normalizedId = safe(entry == null ? "" : entry.id).trim().toLowerCase();
    return entry != null && entry.treasure && !normalizedId.contains("-objective-");
  }

  protected boolean isObjectiveTreasureEntry(EventEntry entry) {
    return isTreasureEntry(entry, "objective");
  }
}
