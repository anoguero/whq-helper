package pms.whq.swt.editor;

import static pms.whq.swt.editor.EditorSupport.*;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.Text;

import com.whq.app.adventure.ObjectiveRoomAdventure;
import com.whq.app.adventure.ObjectiveRoomAdventureRepositoryException;
import com.whq.app.i18n.I18n;
import com.whq.app.model.CardType;
import com.whq.app.model.DungeonCard;

/** Pestana de misiones de sala objetivo. */
public final class ObjectiveRoomAdventuresTab extends EditorTab {

  public ObjectiveRoomAdventuresTab(EditorContext context) {
    super(context);
  }

  @Override
  public void create(TabFolder tabs, Shell dialog) {
    TabItem tab = new TabItem(tabs, SWT.NONE);
    tab.setText(I18n.t("dialog.contentEditor.tab.objectiveRoomAdventures"));

    Composite root = new Composite(tabs, SWT.NONE);
    root.setLayout(new GridLayout(1, false));
    tab.setControl(root);

    Composite header = createActionRow(root, 4);
    header.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    Button newButton = createActionButton(header, "editor.button.new");
    Button deleteButton = createActionButton(header, "editor.button.delete");
    Button saveButton = createActionButton(header, "editor.button.save");
    Button reloadButton = createActionButton(header, "editor.button.reload");

    SashForm sash = new SashForm(root, SWT.HORIZONTAL);
    sash.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

    org.eclipse.swt.widgets.List itemList = new org.eclipse.swt.widgets.List(sash, SWT.BORDER | SWT.V_SCROLL);

    Composite details = new Composite(sash, SWT.NONE);
    details.setLayout(new GridLayout(2, false));
    sash.setWeights(new int[] {30, 70});

    new Label(details, SWT.NONE).setText(I18n.t("editor.objectiveAdventures.label.objectiveRoom") + ":");
    Combo objectiveRoomCombo = new Combo(details, SWT.DROP_DOWN | SWT.READ_ONLY);
    objectiveRoomCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    Text nameText = createLabeledText(details, I18n.t("editor.objectiveAdventures.label.name") + ":");
    Text idText = createLabeledText(details, I18n.t("editor.objectiveAdventures.label.id") + ":");
    idText.setEditable(false);
    new Label(details, SWT.NONE).setText(I18n.t("editor.objectiveAdventures.label.generic") + ":");
    Button genericCheck = new Button(details, SWT.CHECK);
    Label flavorLabel = new Label(details, SWT.NONE);
    flavorLabel.setText(I18n.t("editor.objectiveAdventures.label.flavor") + ":");
    flavorLabel.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false));
    StyledText flavorText = new StyledText(details, SWT.BORDER | SWT.WRAP | SWT.V_SCROLL);
    GridData flavorData = new GridData(SWT.FILL, SWT.FILL, true, true);
    flavorData.heightHint = 110;
    flavorText.setLayoutData(flavorData);
    Label rulesLabel = new Label(details, SWT.NONE);
    rulesLabel.setText(I18n.t("editor.objectiveAdventures.label.rules") + ":");
    rulesLabel.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false));
    StyledText rulesText = new StyledText(details, SWT.BORDER | SWT.WRAP | SWT.V_SCROLL);
    GridData rulesData = new GridData(SWT.FILL, SWT.FILL, true, true);
    rulesData.heightHint = 150;
    rulesText.setLayoutData(rulesData);

    List<ObjectiveRoomAdventure> adventures = new ArrayList<>();
    // Salas objetivo del desplegable, en su mismo orden: se emparejan con las aventuras por id de carta.
    List<DungeonCard> objectiveRooms = new ArrayList<>();
    final String[] selectedKey = new String[] {""};

    Runnable refreshId = () -> idText.setText(selectedKey[0].isBlank() ? slugify(nameText.getText()) : selectedKey[0]);

    Runnable clearForm =
        () -> {
          selectedKey[0] = "";
          itemList.deselectAll();
          if (objectiveRoomCombo.getItemCount() > 0) {
            objectiveRoomCombo.select(0);
          }
          nameText.setText("");
          genericCheck.setSelection(false);
          flavorText.setText("");
          rulesText.setText("");
          refreshId.run();
        };

    Runnable refreshList =
        () -> {
          itemList.removeAll();
          for (ObjectiveRoomAdventure adventure : adventures) {
            itemList.add(
                objectiveRoomLabel(objectiveRooms, adventure) + " - " + adventure.name() + " (" + adventure.id() + ")");
          }
        };

    Runnable loadSelected =
        () -> {
          int index = itemList.getSelectionIndex();
          if (index < 0 || index >= adventures.size()) {
            return;
          }
          ObjectiveRoomAdventure adventure = adventures.get(index);
          selectedKey[0] = adventure.id();
          int roomIndex = objectiveRoomIndex(objectiveRooms, adventure);
          if (roomIndex >= 0) {
            objectiveRoomCombo.select(roomIndex);
          } else {
            objectiveRoomCombo.deselectAll();
          }
          nameText.setText(adventure.name());
          genericCheck.setSelection(adventure.generic());
          flavorText.setText(adventure.flavorText());
          rulesText.setText(adventure.rulesText());
          refreshId.run();
        };

    Runnable reloadAdventures =
        () -> {
          try {
            adventures.clear();
            adventures.addAll(objectiveRoomAdventureRepository.loadAllAdventures());

            List<DungeonCard> cards = dungeonCardStore.loadCards();
            objectiveRooms.clear();
            objectiveRoomCombo.removeAll();
            for (DungeonCard card : cards) {
              if (card.getType() == CardType.OBJECTIVE_ROOM
                  && objectiveRooms.stream().noneMatch(room -> room.getId() == card.getId())) {
                objectiveRooms.add(card);
                objectiveRoomCombo.add(card.getName());
              }
            }
            refreshList.run();
            clearForm.run();
          } catch (Exception ex) {
            showError(dialog, ex);
          }
        };

    itemList.addListener(SWT.Selection, event -> loadSelected.run());
    newButton.addListener(SWT.Selection, event -> clearForm.run());
    reloadButton.addListener(SWT.Selection, event -> reloadAdventures.run());
    nameText.addModifyListener(event -> refreshId.run());

    deleteButton.addListener(
        SWT.Selection,
        event -> {
          try {
            int roomIndex = objectiveRoomCombo.getSelectionIndex();
            if (roomIndex >= 0) {
              objectiveRoomAdventureRepository.deleteUserAdventure(objectiveRooms.get(roomIndex).getId(), idText.getText());
            } else {
              objectiveRoomAdventureRepository.deleteUserAdventure(objectiveRoomCombo.getText(), idText.getText());
            }
            notifySaved();
            reloadAdventures.run();
          } catch (ObjectiveRoomAdventureRepositoryException ex) {
            showError(dialog, ex);
          }
        });

    saveButton.addListener(
        SWT.Selection,
        event -> {
          try {
            int roomIndex = objectiveRoomCombo.getSelectionIndex();
            DungeonCard room = roomIndex >= 0 ? objectiveRooms.get(roomIndex) : null;
            String objectiveRoomName = room != null ? room.getName() : objectiveRoomCombo.getText().trim();
            String adventureId = selectedKey[0].isBlank() ? slugify(nameText.getText()) : selectedKey[0];
            objectiveRoomAdventureRepository.saveUserAdventure(
                new ObjectiveRoomAdventure(
                    objectiveRoomName,
                    adventureId,
                    nameText.getText().trim(),
                    flavorText.getText().trim(),
                    rulesText.getText().trim(),
                    genericCheck.getSelection(),
                    room != null ? room.getId() : 0L));
            notifySaved();
            reloadAdventures.run();
          } catch (Exception ex) {
            showError(dialog, ex);
          }
        });

    reloadAdventures.run();
  }

  // Sala de una aventura en el desplegable: por id de carta y, en ficheros antiguos sin cardId, por
  // nombre. Las aventuras traen el nombre ingles del XML y el desplegable los nombres traducidos.
  static int objectiveRoomIndex(List<DungeonCard> objectiveRooms, ObjectiveRoomAdventure adventure) {
    for (int i = 0; i < objectiveRooms.size(); i++) {
      if (adventure.objectiveRoomCardId() > 0 && objectiveRooms.get(i).getId() == adventure.objectiveRoomCardId()) {
        return i;
      }
    }
    for (int i = 0; i < objectiveRooms.size(); i++) {
      if (objectiveRooms.get(i).getName().equalsIgnoreCase(safe(adventure.objectiveRoomName()).trim())) {
        return i;
      }
    }
    return -1;
  }

  static String objectiveRoomLabel(List<DungeonCard> objectiveRooms, ObjectiveRoomAdventure adventure) {
    int index = objectiveRoomIndex(objectiveRooms, adventure);
    return index >= 0 ? objectiveRooms.get(index).getName() : adventure.objectiveRoomName();
  }
}
