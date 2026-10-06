package pms.whq.swt.editor;

import java.nio.file.Path;

import org.eclipse.swt.widgets.Shell;

import com.whq.app.adventure.XmlObjectiveRoomAdventureRepository;
import com.whq.app.storage.XmlDungeonCardStore;

import pms.whq.xml.XmlContentService;

/** Dependencias comunes de las pestanas del editor de contenido. Las construye el dialogo. */
public record EditorContext(
    Shell parent,
    Path projectRoot,
    XmlContentService service,
    XmlDungeonCardStore dungeonCardStore,
    XmlObjectiveRoomAdventureRepository objectiveRoomAdventureRepository,
    Runnable onContentSaved) {
}
