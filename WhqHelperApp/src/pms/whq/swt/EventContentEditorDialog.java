package pms.whq.swt;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;

import com.whq.app.adventure.ObjectiveRoomAdventure;
import com.whq.app.adventure.ObjectiveRoomAdventureRepositoryException;
import com.whq.app.adventure.XmlObjectiveRoomAdventureRepository;
import com.whq.app.i18n.I18n;
import com.whq.app.model.CardType;
import com.whq.app.model.DungeonCard;
import com.whq.app.render.CardRenderer;
import com.whq.app.storage.DungeonCardStorageException;
import com.whq.app.storage.XmlDungeonCardStore;

import pms.whq.xml.XmlContentService;
import pms.whq.xml.XmlContentService.EventEntry;
import pms.whq.xml.XmlContentService.MonsterEntry;
import pms.whq.xml.XmlContentService.RuleEntry;
import pms.whq.xml.XmlContentService.TableDefinition;
import pms.whq.xml.XmlContentService.TableEntry;
import pms.whq.xml.XmlContentService.TableFileModel;
import pms.whq.xml.XmlContentService.TableGroupMember;

public final class EventContentEditorDialog {
  private static final int HEADER_BUTTON_COLUMNS = 8;

  private final Shell parent;
  private final Path projectRoot;
  private final XmlContentService service;
  private final XmlDungeonCardStore dungeonCardStore;
  private final XmlObjectiveRoomAdventureRepository objectiveRoomAdventureRepository;
  private final Runnable onContentSaved;

  public EventContentEditorDialog(Shell parent, Path projectRoot, Runnable onContentSaved) {
    this.parent = parent;
    this.projectRoot = projectRoot.toAbsolutePath().normalize();
    this.service = new XmlContentService(this.projectRoot);
    this.dungeonCardStore = new XmlDungeonCardStore(this.projectRoot);
    this.objectiveRoomAdventureRepository = new XmlObjectiveRoomAdventureRepository(this.projectRoot);
    this.onContentSaved = onContentSaved;
  }

  public void open() {
    Shell dialog = new Shell(parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.RESIZE);
    dialog.setText(I18n.t("dialog.contentEditor.title"));
    dialog.setLayout(new GridLayout(1, false));
    dialog.setSize(1440, 920);

    SashForm layout = new SashForm(dialog, SWT.HORIZONTAL);
    layout.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

    Tree navigationTree = new Tree(layout, SWT.BORDER | SWT.SINGLE);
    TabFolder tabs = new TabFolder(layout, SWT.NONE);
    layout.setWeights(new int[] {24, 76});

    createDungeonCardsTab(tabs, dialog);
    createRulesTab(tabs, dialog);
    createEventsTab(tabs, dialog);
    createDungeonTreasureTab(tabs, dialog);
    createObjectiveTreasureTab(tabs, dialog);
    createTravelEventsTab(tabs, dialog);
    createSettlementEventsTab(tabs, dialog);
    createTablesTab(tabs, dialog);
    createMonstersTab(tabs, dialog);
    createObjectiveRoomAdventuresTab(tabs, dialog);

    for (TabItem item : tabs.getItems()) {
      TreeItem treeItem = new TreeItem(navigationTree, SWT.NONE);
      treeItem.setText(item.getText());
      treeItem.setData("tabItem", item);
    }
    if (navigationTree.getItemCount() > 0) {
      navigationTree.setSelection(navigationTree.getItem(0));
      tabs.setSelection(0);
    }
    navigationTree.addListener(
        SWT.Selection,
        event -> {
          if (!(event.item instanceof TreeItem treeItem)) {
            return;
          }
          Object tabData = treeItem.getData("tabItem");
          if (tabData instanceof TabItem tabItem) {
            tabs.setSelection(tabItem);
          }
        });
    tabs.addListener(
        SWT.Selection,
        event -> {
          int index = tabs.getSelectionIndex();
          if (index >= 0 && index < navigationTree.getItemCount()) {
            navigationTree.setSelection(navigationTree.getItem(index));
          }
        });

    Composite actions = createActionRow(dialog, 1);

    Button closeButton = createActionButton(actions, "button.close");
    closeButton.addListener(SWT.Selection, event -> dialog.close());

    dialog.open();
    Display display = parent.getDisplay();
    while (!dialog.isDisposed()) {
      if (!display.readAndDispatch()) {
        display.sleep();
      }
    }
  }

  private void createDungeonCardsTab(TabFolder tabs, Shell dialog) {
    TabItem tab = new TabItem(tabs, SWT.NONE);
    tab.setText(I18n.t("dialog.contentEditor.tab.dungeonCards"));

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
    details.setLayout(new GridLayout(1, false));
    sash.setWeights(new int[] {28, 72});

    Composite form = new Composite(details, SWT.NONE);
    form.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
    GridLayout formLayout = new GridLayout(2, false);
    formLayout.marginWidth = 0;
    form.setLayout(formLayout);

    Text nameText = createLabeledText(form, "Nombre/Name:");
    new Label(form, SWT.NONE).setText("Tipo/Type:");
    Combo typeCombo = new Combo(form, SWT.DROP_DOWN | SWT.READ_ONLY);
    typeCombo.setItems(new String[] {"DUNGEON_ROOM", "OBJECTIVE_ROOM", "CORRIDOR", "SPECIAL"});
    typeCombo.select(0);
    typeCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    Text environmentText = createLabeledText(form, "Entorno/Environment:");
    Text tilePathText = createLabeledText(form, "Tile:");
    new Label(form, SWT.NONE).setText("Copies:");
    org.eclipse.swt.widgets.Spinner copySpinner = new org.eclipse.swt.widgets.Spinner(form, SWT.BORDER);
    copySpinner.setMinimum(0);
    copySpinner.setMaximum(999);
    copySpinner.setSelection(1);
    copySpinner.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    new Label(form, SWT.NONE).setText("Enabled:");
    Button enabledCheck = new Button(form, SWT.CHECK);
    enabledCheck.setSelection(true);

    new Label(form, SWT.NONE).setText("Description:");
    StyledText descriptionText = new StyledText(form, SWT.BORDER | SWT.WRAP | SWT.V_SCROLL);
    GridData descriptionData = new GridData(SWT.FILL, SWT.FILL, true, true);
    descriptionData.heightHint = 90;
    descriptionText.setLayoutData(descriptionData);

    new Label(form, SWT.NONE).setText("Rules:");
    StyledText rulesText = new StyledText(form, SWT.BORDER | SWT.WRAP | SWT.V_SCROLL);
    GridData rulesData = new GridData(SWT.FILL, SWT.FILL, true, true);
    rulesData.heightHint = 120;
    rulesText.setLayoutData(rulesData);

    Group previewGroup = new Group(details, SWT.NONE);
    previewGroup.setText(I18n.t("dashboard.preview.title"));
    previewGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    previewGroup.setLayout(new GridLayout(1, false));

    Canvas previewCanvas = new Canvas(previewGroup, SWT.DOUBLE_BUFFERED | SWT.BORDER);
    previewCanvas.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

    CardRenderer renderer = new CardRenderer(dialog.getDisplay(), projectRoot);
    dialog.addDisposeListener(event -> renderer.dispose());

    List<DungeonCard> cards = new ArrayList<>();
    final DungeonCard[] draftCard = new DungeonCard[1];
    final long[] selectedCardId = new long[] {-1L};

    Runnable buildDraftCard =
        () -> {
          long id = selectedCardId[0] > 0 ? selectedCardId[0] : 1L;
          draftCard[0] =
              new DungeonCard(
                  id,
                  nameText.getText().trim(),
                  CardType.valueOf(typeCombo.getText()),
                  environmentText.getText().trim(),
                  copySpinner.getSelection(),
                  enabledCheck.getSelection(),
                  descriptionText.getText().trim(),
                  rulesText.getText().trim(),
                  tilePathText.getText().trim());
          previewCanvas.redraw();
        };

    previewCanvas.addPaintListener(
        event -> {
          if (draftCard[0] == null) {
            return;
          }
          org.eclipse.swt.graphics.Rectangle area = previewCanvas.getClientArea();
          org.eclipse.swt.graphics.Point scaled = renderer.scaleToFit(area);
          int x = area.x + (area.width - scaled.x) / 2;
          int y = area.y + (area.height - scaled.y) / 2;
          renderer.drawCard(event.gc, new org.eclipse.swt.graphics.Rectangle(x, y, scaled.x, scaled.y), draftCard[0]);
        });

    Runnable clearForm =
        () -> {
          selectedCardId[0] = -1L;
          itemList.deselectAll();
          nameText.setText("");
          typeCombo.select(0);
          environmentText.setText("");
          tilePathText.setText("");
          copySpinner.setSelection(1);
          enabledCheck.setSelection(true);
          descriptionText.setText("");
          rulesText.setText("");
          buildDraftCard.run();
        };

    Runnable refreshList =
        () -> {
          itemList.removeAll();
          for (DungeonCard card : cards) {
            itemList.add(card.getName() + " (#" + card.getId() + ")");
          }
        };

    Runnable loadSelectedCard =
        () -> {
          int index = itemList.getSelectionIndex();
          if (index < 0 || index >= cards.size()) {
            return;
          }
          DungeonCard card = cards.get(index);
          selectedCardId[0] = card.getId();
          nameText.setText(safe(card.getName()));
          typeCombo.setText(card.getType().name());
          environmentText.setText(safe(card.getEnvironment()));
          tilePathText.setText(safe(card.getTileImagePath()));
          copySpinner.setSelection(card.getCopyCount());
          enabledCheck.setSelection(card.isEnabled());
          descriptionText.setText(safe(card.getDescriptionText()));
          rulesText.setText(safe(card.getRulesText()));
          buildDraftCard.run();
        };

    Runnable reloadCards =
        () -> {
          try {
            cards.clear();
            cards.addAll(dungeonCardStore.loadCards());
            refreshList.run();
            clearForm.run();
            if (!cards.isEmpty()) {
              itemList.setSelection(0);
              loadSelectedCard.run();
            }
          } catch (DungeonCardStorageException ex) {
            showError(dialog, ex.getMessage());
          }
        };

    itemList.addListener(SWT.Selection, event -> loadSelectedCard.run());
    newButton.addListener(SWT.Selection, event -> clearForm.run());
    reloadButton.addListener(SWT.Selection, event -> reloadCards.run());

    deleteButton.addListener(
        SWT.Selection,
        event -> {
          if (selectedCardId[0] <= 0) {
            showWarning(dialog, I18n.t("editor.message.selectEntry"));
            return;
          }
          try {
            dungeonCardStore.deleteCard(selectedCardId[0]);
            notifySaved();
            reloadCards.run();
          } catch (DungeonCardStorageException ex) {
            showError(dialog, ex.getMessage());
          }
        });

    saveButton.addListener(
        SWT.Selection,
        event -> {
          try {
            DungeonCard card =
                new DungeonCard(
                    selectedCardId[0] > 0 ? selectedCardId[0] : 0L,
                    nameText.getText().trim(),
                    CardType.valueOf(typeCombo.getText()),
                    environmentText.getText().trim(),
                    copySpinner.getSelection(),
                    enabledCheck.getSelection(),
                    descriptionText.getText().trim(),
                    rulesText.getText().trim(),
                    tilePathText.getText().trim());
            if (selectedCardId[0] > 0) {
              dungeonCardStore.updateCard(card);
            } else {
              dungeonCardStore.insertCards(List.of(card));
            }
            notifySaved();
            reloadCards.run();
          } catch (Exception ex) {
            showError(dialog, ex.getMessage());
          }
        });

    nameText.addModifyListener(event -> buildDraftCard.run());
    typeCombo.addListener(SWT.Selection, event -> buildDraftCard.run());
    environmentText.addModifyListener(event -> buildDraftCard.run());
    tilePathText.addModifyListener(event -> buildDraftCard.run());
    copySpinner.addModifyListener(event -> buildDraftCard.run());
    enabledCheck.addListener(SWT.Selection, event -> buildDraftCard.run());
    descriptionText.addModifyListener(event -> buildDraftCard.run());
    rulesText.addModifyListener(event -> buildDraftCard.run());

    reloadCards.run();
  }

  private void createObjectiveRoomAdventuresTab(TabFolder tabs, Shell dialog) {
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

    Combo objectiveRoomCombo = new Combo(details, SWT.DROP_DOWN | SWT.READ_ONLY);
    objectiveRoomCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    new Label(details, SWT.NONE).setText("");
    new Label(details, SWT.NONE).setText("Objective room:");
    Text nameText = createLabeledText(details, "Nombre/Name:");
    Text idText = createLabeledText(details, "ID:");
    idText.setEditable(false);
    new Label(details, SWT.NONE).setText("Generica/Generic:");
    Button genericCheck = new Button(details, SWT.CHECK);
    new Label(details, SWT.NONE).setText("Flavor:");
    StyledText flavorText = new StyledText(details, SWT.BORDER | SWT.WRAP | SWT.V_SCROLL);
    GridData flavorData = new GridData(SWT.FILL, SWT.FILL, true, true);
    flavorData.heightHint = 110;
    flavorText.setLayoutData(flavorData);
    new Label(details, SWT.NONE).setText("Rules:");
    StyledText rulesText = new StyledText(details, SWT.BORDER | SWT.WRAP | SWT.V_SCROLL);
    GridData rulesData = new GridData(SWT.FILL, SWT.FILL, true, true);
    rulesData.heightHint = 150;
    rulesText.setLayoutData(rulesData);

    List<ObjectiveRoomAdventure> adventures = new ArrayList<>();
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
            itemList.add(adventure.objectiveRoomName() + " - " + adventure.name() + " (" + adventure.id() + ")");
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
          objectiveRoomCombo.setText(adventure.objectiveRoomName());
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
            refreshList.run();

            List<DungeonCard> cards = dungeonCardStore.loadCards();
            objectiveRoomCombo.removeAll();
            for (DungeonCard card : cards) {
              if (card.getType() == CardType.OBJECTIVE_ROOM && objectiveRoomCombo.indexOf(card.getName()) < 0) {
                objectiveRoomCombo.add(card.getName());
              }
            }
            clearForm.run();
          } catch (Exception ex) {
            showError(dialog, ex.getMessage());
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
            objectiveRoomAdventureRepository.deleteUserAdventure(objectiveRoomCombo.getText(), idText.getText());
            notifySaved();
            reloadAdventures.run();
          } catch (ObjectiveRoomAdventureRepositoryException ex) {
            showError(dialog, ex.getMessage());
          }
        });

    saveButton.addListener(
        SWT.Selection,
        event -> {
          try {
            String objectiveRoomName = objectiveRoomCombo.getText().trim();
            String adventureId = selectedKey[0].isBlank() ? slugify(nameText.getText()) : selectedKey[0];
            objectiveRoomAdventureRepository.saveUserAdventure(
                new ObjectiveRoomAdventure(
                    objectiveRoomName,
                    adventureId,
                    nameText.getText().trim(),
                    flavorText.getText().trim(),
                    rulesText.getText().trim(),
                    genericCheck.getSelection()));
            notifySaved();
            reloadAdventures.run();
          } catch (Exception ex) {
            showError(dialog, ex.getMessage());
          }
        });

    reloadAdventures.run();
  }

  private void createRulesTab(TabFolder tabs, Shell dialog) {
    TabItem tab = new TabItem(tabs, SWT.NONE);
    tab.setText(I18n.t("dialog.contentEditor.tab.rules"));

    Composite root = new Composite(tabs, SWT.NONE);
    root.setLayout(new GridLayout(1, false));
    tab.setControl(root);

    EditorHeader header = createEditorHeader(root);
    Combo fileCombo = header.fileCombo();
    Button newButton = header.newButton();
    Button deleteButton = header.deleteButton();
    Button saveButton = header.saveButton();
    Button reloadButton = header.reloadButton();
    Button validateButton = header.validateButton();

    SashForm sash = new SashForm(root, SWT.HORIZONTAL);
    sash.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

    org.eclipse.swt.widgets.List itemList = new org.eclipse.swt.widgets.List(sash, SWT.BORDER | SWT.V_SCROLL);

    Composite details = new Composite(sash, SWT.NONE);
    details.setLayout(new GridLayout(2, false));
    sash.setWeights(new int[] {34, 66});

    new Label(details, SWT.NONE).setText("Tipo/Type:");
    Combo typeCombo = new Combo(details, SWT.DROP_DOWN | SWT.READ_ONLY);
    typeCombo.setItems(new String[] {"rule", "magic"});
    typeCombo.select(0);
    typeCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

    new Label(details, SWT.NONE).setText("ID:");
    Text idText = new Text(details, SWT.BORDER);
    idText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

    new Label(details, SWT.NONE).setText("Nombre/Name:");
    Text nameText = new Text(details, SWT.BORDER);
    nameText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

    new Label(details, SWT.NONE).setText("Texto/Text:");
    StyledText contentText = new StyledText(details, SWT.BORDER | SWT.WRAP | SWT.V_SCROLL);
    GridData contentData = new GridData(SWT.FILL, SWT.FILL, true, true);
    contentData.heightHint = 300;
    contentText.setLayoutData(contentData);

    java.util.List<Path> files = new ArrayList<>();
    java.util.List<RuleEntry> entries = new ArrayList<>();
    final int[] selectedIndex = new int[] {-1};

    Runnable refreshList =
        () -> {
          itemList.removeAll();
          for (RuleEntry entry : entries) {
            String label = safe(entry.id) + " - " + safe(entry.name) + " [" + safe(entry.type) + "]";
            itemList.add(label);
          }
        };

    Runnable clearForm =
        () -> {
          selectedIndex[0] = -1;
          itemList.deselectAll();
          typeCombo.select(0);
          idText.setText("");
          nameText.setText("");
          contentText.setText("");
        };

    Runnable loadSelectedToForm =
        () -> {
          int index = itemList.getSelectionIndex();
          if (index < 0 || index >= entries.size()) {
            return;
          }
          selectedIndex[0] = index;
          RuleEntry entry = entries.get(index);
          typeCombo.setText("magic".equals(entry.type) ? "magic" : "rule");
          idText.setText(safe(entry.id));
          nameText.setText(safe(entry.name));
          contentText.setText(safe(entry.text));
        };

    Runnable loadFile =
        () -> {
          int fileIndex = fileCombo.getSelectionIndex();
          if (fileIndex < 0 || fileIndex >= files.size()) {
            return;
          }
          try {
            entries.clear();
            entries.addAll(service.loadRules(files.get(fileIndex)));
            refreshList.run();
            clearForm.run();
          } catch (Exception ex) {
            showError(dialog, ex.getMessage());
          }
        };

    try {
      files.addAll(service.listRuleFiles());
      for (Path file : files) {
        fileCombo.add(file.getFileName().toString());
      }
      if (!files.isEmpty()) {
        fileCombo.select(0);
        loadFile.run();
      }
    } catch (Exception ex) {
      showError(dialog, ex.getMessage());
    }

    fileCombo.addListener(SWT.Selection, event -> loadFile.run());
    itemList.addListener(SWT.Selection, event -> loadSelectedToForm.run());
    newButton.addListener(SWT.Selection, event -> clearForm.run());

    deleteButton.addListener(
        SWT.Selection,
        event -> {
          int index = itemList.getSelectionIndex();
          if (index < 0 || index >= entries.size()) {
            showWarning(dialog, I18n.t("editor.message.selectEntry"));
            return;
          }
          if (entries.size() <= 1) {
            showWarning(dialog, I18n.t("editor.message.lastEntry"));
            return;
          }
          if (!confirm(dialog, I18n.t("editor.message.deleteConfirm"))) {
            return;
          }
          entries.remove(index);
          try {
            service.saveRules(selectedFile(fileCombo, files), entries);
            refreshList.run();
            clearForm.run();
            notifySaved();
            showInfo(dialog, I18n.t("editor.message.saved"));
          } catch (Exception ex) {
            showError(dialog, ex.getMessage());
          }
        });

    saveButton.addListener(
        SWT.Selection,
        event -> {
          if (!hasSelectedFile(fileCombo, files, dialog)) {
            return;
          }
          RuleEntry entry = new RuleEntry();
          entry.type = typeCombo.getText();
          entry.id = idText.getText().trim();
          entry.name = nameText.getText().trim();
          entry.text = contentText.getText().trim();

          try {
            ensureUniqueId(entries, selectedIndex[0], entry.id);
            if (selectedIndex[0] >= 0 && selectedIndex[0] < entries.size()) {
              entries.set(selectedIndex[0], entry);
            } else {
              entries.add(entry);
            }
            service.saveRules(selectedFile(fileCombo, files), entries);
            refreshList.run();
            selectById(itemList, entries, entry.id);
            notifySaved();
            showInfo(dialog, I18n.t("editor.message.saved"));
          } catch (Exception ex) {
            showError(dialog, ex.getMessage());
          }
        });

    reloadButton.addListener(SWT.Selection, event -> loadFile.run());
    validateButton.addListener(
        SWT.Selection,
        event -> {
          if (!hasSelectedFile(fileCombo, files, dialog)) {
            return;
          }
          try {
            service.validateRulesFile(selectedFile(fileCombo, files));
            showInfo(dialog, I18n.t("editor.message.validated"));
          } catch (Exception ex) {
            showError(dialog, ex.getMessage());
          }
        });
  }

  private void createEventsTab(TabFolder tabs, Shell dialog) {
    createEventLikeTab(
        tabs,
        dialog,
        I18n.t("dialog.contentEditor.tab.events"),
        this::listNonTreasureEventFiles,
        path -> service.validateEventsFile(path));
  }

  private void createDungeonTreasureTab(TabFolder tabs, Shell dialog) {
    createEventLikeTab(
        tabs,
        dialog,
        I18n.t("dialog.contentEditor.tab.treasureDungeon"),
        this::listTreasureEventFiles,
        path -> service.validateEventsFile(path),
        this::isDungeonTreasureEntry);
  }

  private void createObjectiveTreasureTab(TabFolder tabs, Shell dialog) {
    createEventLikeTab(
        tabs,
        dialog,
        I18n.t("dialog.contentEditor.tab.treasureObjective"),
        this::listTreasureEventFiles,
        path -> service.validateEventsFile(path),
        this::isObjectiveTreasureEntry);
  }

  private void createTravelEventsTab(TabFolder tabs, Shell dialog) {
    createEventLikeTab(
        tabs,
        dialog,
        I18n.t("dialog.contentEditor.tab.travel"),
        () -> service.listTravelFiles(),
        path -> service.validateTravelFile(path));
  }

  private void createSettlementEventsTab(TabFolder tabs, Shell dialog) {
    createEventLikeTab(
        tabs,
        dialog,
        I18n.t("dialog.contentEditor.tab.settlement"),
        () -> service.listSettlementFiles(),
        path -> service.validateSettlementFile(path));
  }

  private void createEventLikeTab(
      TabFolder tabs,
      Shell dialog,
      String tabTitle,
      CheckedSupplier<java.util.List<Path>> fileSupplier,
      CheckedConsumer<Path> validator) {
    createEventLikeTab(tabs, dialog, tabTitle, fileSupplier, validator, null);
  }

  private void createEventLikeTab(
      TabFolder tabs,
      Shell dialog,
      String tabTitle,
      CheckedSupplier<java.util.List<Path>> fileSupplier,
      CheckedConsumer<Path> validator,
      Predicate<EventEntry> entryFilter) {
    TabItem tab = new TabItem(tabs, SWT.NONE);
    tab.setText(tabTitle);

    Composite root = new Composite(tabs, SWT.NONE);
    root.setLayout(new GridLayout(1, false));
    tab.setControl(root);

    EditorHeader header = createEditorHeader(root);
    Combo fileCombo = header.fileCombo();
    Button newButton = header.newButton();
    Button deleteButton = header.deleteButton();
    Button saveButton = header.saveButton();
    Button reloadButton = header.reloadButton();
    Button validateButton = header.validateButton();

    SashForm sash = new SashForm(root, SWT.HORIZONTAL);
    sash.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

    org.eclipse.swt.widgets.List itemList = new org.eclipse.swt.widgets.List(sash, SWT.BORDER | SWT.V_SCROLL);
    Composite details = new Composite(sash, SWT.NONE);
    details.setLayout(new GridLayout(2, false));
    sash.setWeights(new int[] {32, 68});

    new Label(details, SWT.NONE).setText("ID:");
    Text idText = new Text(details, SWT.BORDER);
    idText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

    new Label(details, SWT.NONE).setText("Nombre/Name:");
    Text nameText = new Text(details, SWT.BORDER);
    nameText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

    new Label(details, SWT.NONE).setText("Flavor:");
    StyledText flavorText = new StyledText(details, SWT.BORDER | SWT.WRAP | SWT.V_SCROLL);
    GridData flavorData = new GridData(SWT.FILL, SWT.FILL, true, true);
    flavorData.heightHint = 140;
    flavorText.setLayoutData(flavorData);

    new Label(details, SWT.NONE).setText("Rules:");
    StyledText rulesText = new StyledText(details, SWT.BORDER | SWT.WRAP | SWT.V_SCROLL);
    GridData rulesData = new GridData(SWT.FILL, SWT.FILL, true, true);
    rulesData.heightHint = 210;
    rulesText.setLayoutData(rulesData);

    new Label(details, SWT.NONE).setText("Special:");
    StyledText specialText = new StyledText(details, SWT.BORDER | SWT.WRAP | SWT.V_SCROLL);
    GridData specialData = new GridData(SWT.FILL, SWT.FILL, true, true);
    specialData.heightHint = 120;
    specialText.setLayoutData(specialData);

    new Label(details, SWT.NONE).setText("Gold Value:");
    Text goldValueText = new Text(details, SWT.BORDER);
    goldValueText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

    new Label(details, SWT.NONE).setText("Users:");
    Text usersText = new Text(details, SWT.BORDER);
    usersText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

    new Label(details, SWT.NONE).setText("Treasure:");
    Button treasureCheck = new Button(details, SWT.CHECK);

    java.util.List<Path> files = new ArrayList<>();
    java.util.List<EventEntry> entries = new ArrayList<>();
    final int[] selectedIndex = new int[] {-1};

    Runnable refreshList =
        () -> {
          itemList.removeAll();
          for (EventEntry entry : entries) {
            itemList.add(safe(entry.id) + " - " + safe(entry.name));
          }
        };

    Runnable clearForm =
        () -> {
          selectedIndex[0] = -1;
          itemList.deselectAll();
          idText.setText("");
          nameText.setText("");
          flavorText.setText("");
          rulesText.setText("");
          specialText.setText("");
          goldValueText.setText("");
          usersText.setText("");
          treasureCheck.setSelection(false);
        };

    Runnable loadSelectedToForm =
        () -> {
          int index = itemList.getSelectionIndex();
          if (index < 0 || index >= entries.size()) {
            return;
          }
          selectedIndex[0] = index;
          EventEntry entry = entries.get(index);
          idText.setText(safe(entry.id));
          nameText.setText(safe(entry.name));
          flavorText.setText(safe(entry.flavor));
          rulesText.setText(safe(entry.rules));
          specialText.setText(safe(entry.special));
          goldValueText.setText(safe(entry.goldValue));
          usersText.setText(safe(entry.users));
          treasureCheck.setSelection(entry.treasure);
        };

    Runnable loadFile =
        () -> {
          int fileIndex = fileCombo.getSelectionIndex();
          if (fileIndex < 0 || fileIndex >= files.size()) {
            return;
          }
          try {
            entries.clear();
            List<EventEntry> loadedEntries = service.loadEvents(files.get(fileIndex));
            entries.addAll(filterEvents(loadedEntries, entryFilter));
            refreshList.run();
            clearForm.run();
          } catch (Exception ex) {
            showError(dialog, ex.getMessage());
          }
        };

    try {
      files.addAll(fileSupplier.get());
      for (Path file : files) {
        fileCombo.add(file.getFileName().toString());
      }
      if (!files.isEmpty()) {
        fileCombo.select(0);
        loadFile.run();
      }
    } catch (Exception ex) {
      showError(dialog, ex.getMessage());
    }

    fileCombo.addListener(SWT.Selection, event -> loadFile.run());
    itemList.addListener(SWT.Selection, event -> loadSelectedToForm.run());
    newButton.addListener(SWT.Selection, event -> clearForm.run());

    deleteButton.addListener(
        SWT.Selection,
        event -> {
          int index = itemList.getSelectionIndex();
          if (index < 0 || index >= entries.size()) {
            showWarning(dialog, I18n.t("editor.message.selectEntry"));
            return;
          }
          if (!confirm(dialog, I18n.t("editor.message.deleteConfirm"))) {
            return;
          }
          try {
            Path file = selectedFile(fileCombo, files);
            List<EventEntry> updatedEntries = new ArrayList<>(entries);
            updatedEntries.remove(index);
            List<EventEntry> entriesToSave = mergeVisibleEvents(file, updatedEntries, entryFilter);
            if (entriesToSave.isEmpty()) {
              showWarning(dialog, I18n.t("editor.message.lastEntry"));
              return;
            }
            entries.clear();
            entries.addAll(updatedEntries);
            service.saveEvents(file, entriesToSave);
            refreshList.run();
            clearForm.run();
            notifySaved();
            showInfo(dialog, I18n.t("editor.message.saved"));
          } catch (Exception ex) {
            showError(dialog, ex.getMessage());
          }
        });

    saveButton.addListener(
        SWT.Selection,
        event -> {
          if (!hasSelectedFile(fileCombo, files, dialog)) {
            return;
          }

          EventEntry entry = new EventEntry();
          entry.id = idText.getText().trim();
          entry.name = nameText.getText().trim();
          entry.flavor = flavorText.getText().trim();
          entry.rules = rulesText.getText().trim();
          entry.special = specialText.getText().trim();
          entry.goldValue = goldValueText.getText().trim();
          entry.users = usersText.getText().trim();
          entry.treasure = treasureCheck.getSelection();

          try {
            ensureUniqueId(entries, selectedIndex[0], entry.id);
            Path file = selectedFile(fileCombo, files);
            if (selectedIndex[0] >= 0 && selectedIndex[0] < entries.size()) {
              entries.set(selectedIndex[0], entry);
            } else {
              entries.add(entry);
            }
            service.saveEvents(file, mergeVisibleEvents(file, entries, entryFilter));
            refreshList.run();
            selectById(itemList, entries, entry.id);
            notifySaved();
            showInfo(dialog, I18n.t("editor.message.saved"));
          } catch (Exception ex) {
            showError(dialog, ex.getMessage());
          }
        });

    reloadButton.addListener(SWT.Selection, event -> loadFile.run());
    validateButton.addListener(
        SWT.Selection,
        event -> {
          if (!hasSelectedFile(fileCombo, files, dialog)) {
            return;
          }
          try {
            validator.accept(selectedFile(fileCombo, files));
            showInfo(dialog, I18n.t("editor.message.validated"));
          } catch (Exception ex) {
            showError(dialog, ex.getMessage());
          }
        });
  }

  private void createTablesTab(TabFolder tabs, Shell dialog) {
    TabItem tab = new TabItem(tabs, SWT.NONE);
    tab.setText(I18n.t("dialog.contentEditor.tab.tables"));

    Composite root = new Composite(tabs, SWT.NONE);
    root.setLayout(new GridLayout(1, false));
    tab.setControl(root);

    EditorHeader header = createEditorHeader(root);
    Combo fileCombo = header.fileCombo();
    Button newButton = header.newButton();
    Button deleteButton = header.deleteButton();
    Button saveButton = header.saveButton();
    Button reloadButton = header.reloadButton();
    Button validateButton = header.validateButton();

    SashForm sash = new SashForm(root, SWT.HORIZONTAL);
    sash.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

    org.eclipse.swt.widgets.List tableList = new org.eclipse.swt.widgets.List(sash, SWT.BORDER | SWT.V_SCROLL);
    Composite details = new Composite(sash, SWT.NONE);
    details.setLayout(new GridLayout(1, false));
    sash.setWeights(new int[] {30, 70});

    Group tableGroup = new Group(details, SWT.NONE);
    tableGroup.setText(I18n.t("editor.tables.group.table"));
    tableGroup.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
    tableGroup.setLayout(new GridLayout(3, false));

    new Label(tableGroup, SWT.NONE).setText(I18n.t("editor.tables.label.name"));
    Text tableNameText = new Text(tableGroup, SWT.BORDER);
    tableNameText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

    Button applyTableButton = createActionButton(tableGroup, "editor.button.apply");

    new Label(tableGroup, SWT.NONE).setText(I18n.t("editor.tables.label.kind"));
    Text tableKindText = new Text(tableGroup, SWT.BORDER);
    tableKindText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

    new Label(tableGroup, SWT.NONE);

    Group entriesGroup = new Group(details, SWT.NONE);
    entriesGroup.setText(I18n.t("editor.tables.group.entries"));
    entriesGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    entriesGroup.setLayout(new GridLayout(1, false));

    Composite entryActions = createActionRow(entriesGroup, 3);
    Button newEntryButton = createActionButton(entryActions, "editor.button.new");
    Button deleteEntryButton = createActionButton(entryActions, "editor.button.delete");
    Button applyEntryButton = createActionButton(entryActions, "editor.button.apply");

    SashForm entriesSash = new SashForm(entriesGroup, SWT.HORIZONTAL);
    entriesSash.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

    org.eclipse.swt.widgets.List entryList = new org.eclipse.swt.widgets.List(entriesSash, SWT.BORDER | SWT.V_SCROLL);
    Composite entryDetails = new Composite(entriesSash, SWT.NONE);
    entryDetails.setLayout(new GridLayout(2, false));
    entriesSash.setWeights(new int[] {35, 65});

    new Label(entryDetails, SWT.NONE).setText(I18n.t("editor.tables.label.entryType"));
    Combo typeCombo = new Combo(entryDetails, SWT.DROP_DOWN | SWT.READ_ONLY);
    typeCombo.setItems(new String[] {"monster", "event", "group"});
    typeCombo.select(0);
    typeCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

    new Label(entryDetails, SWT.NONE).setText("ID:");
    Text idText = new Text(entryDetails, SWT.BORDER);
    idText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

    new Label(entryDetails, SWT.NONE).setText(I18n.t("editor.tables.label.number"));
    Text numberText = new Text(entryDetails, SWT.BORDER);
    numberText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

    new Label(entryDetails, SWT.NONE).setText(I18n.t("editor.tables.label.special"));
    StyledText specialText = new StyledText(entryDetails, SWT.BORDER | SWT.WRAP | SWT.V_SCROLL);
    GridData specialData = new GridData(SWT.FILL, SWT.FILL, true, true);
    specialData.heightHint = 90;
    specialText.setLayoutData(specialData);

    new Label(entryDetails, SWT.NONE).setText(I18n.t("editor.tables.label.groupMembers"));
    StyledText groupMembersText = new StyledText(entryDetails, SWT.BORDER | SWT.WRAP | SWT.V_SCROLL);
    GridData membersData = new GridData(SWT.FILL, SWT.FILL, true, true);
    membersData.heightHint = 140;
    groupMembersText.setLayoutData(membersData);

    Label membersHint = new Label(entryDetails, SWT.WRAP);
    membersHint.setText(I18n.t("editor.tables.groupMembersHint"));
    GridData hintData = new GridData(SWT.FILL, SWT.TOP, true, false);
    hintData.horizontalSpan = 2;
    membersHint.setLayoutData(hintData);

    java.util.List<Path> files = new ArrayList<>();
    final TableFileModel[] model = new TableFileModel[] {new TableFileModel()};
    final int[] selectedTableIndex = new int[] {-1};
    final int[] selectedEntryIndex = new int[] {-1};
    final boolean[] syncingForm = new boolean[] {false};

    Runnable updateEntryFieldsEnabled =
        () -> {
          String type = typeCombo.getText();
          boolean group = "group".equals(type);
          boolean monster = "monster".equals(type);
          idText.setEnabled(!group);
          numberText.setEnabled(monster);
          specialText.setEnabled(monster);
          groupMembersText.setEnabled(group);
        };

    Runnable refreshTableList =
        () -> {
          tableList.removeAll();
          for (TableDefinition table : model[0].tables) {
            tableList.add(tableLabel(table));
          }
        };

    Runnable refreshEntryList =
        () -> {
          entryList.removeAll();
          int tableIndex = selectedTableIndex[0];
          if (tableIndex < 0 || tableIndex >= model[0].tables.size()) {
            return;
          }
          TableDefinition table = model[0].tables.get(tableIndex);
          for (TableEntry entry : table.entries) {
            entryList.add(tableEntryLabel(entry));
          }
        };

    Runnable clearEntryForm =
        () -> {
          syncingForm[0] = true;
          try {
            selectedEntryIndex[0] = -1;
            entryList.deselectAll();
            typeCombo.select(0);
            idText.setText("");
            numberText.setText("");
            specialText.setText("");
            groupMembersText.setText("");
            updateEntryFieldsEnabled.run();
          } finally {
            syncingForm[0] = false;
          }
        };

    Runnable clearTableForm =
        () -> {
          syncingForm[0] = true;
          try {
            selectedTableIndex[0] = -1;
            tableList.deselectAll();
            tableNameText.setText("");
            tableKindText.setText("");
            entryList.removeAll();
            clearEntryForm.run();
          } finally {
            syncingForm[0] = false;
          }
        };

    Runnable loadSelectedEntryToForm =
        () -> {
          int tableIndex = selectedTableIndex[0];
          if (tableIndex < 0 || tableIndex >= model[0].tables.size()) {
            return;
          }
          TableDefinition table = model[0].tables.get(tableIndex);
          int entryIndex = entryList.getSelectionIndex();
          if (entryIndex < 0 || entryIndex >= table.entries.size()) {
            return;
          }

          TableEntry entry = table.entries.get(entryIndex);
          selectedEntryIndex[0] = entryIndex;

          syncingForm[0] = true;
          try {
            String type = safe(entry.type);
            if ("group".equals(type)) {
              typeCombo.setText("group");
              idText.setText("");
              numberText.setText("");
              specialText.setText("");
              groupMembersText.setText(groupMembersToRaw(entry.groupMembers));
            } else if ("event".equals(type)) {
              typeCombo.setText("event");
              idText.setText(safe(entry.id));
              numberText.setText("");
              specialText.setText("");
              groupMembersText.setText("");
            } else {
              typeCombo.setText("monster");
              idText.setText(safe(entry.id));
              numberText.setText(safe(entry.number));
              specialText.setText(safe(entry.specialRaw));
              groupMembersText.setText("");
            }
            updateEntryFieldsEnabled.run();
          } finally {
            syncingForm[0] = false;
          }
        };

    Runnable loadSelectedTableToForm =
        () -> {
          int tableIndex = tableList.getSelectionIndex();
          if (tableIndex < 0 || tableIndex >= model[0].tables.size()) {
            return;
          }

          selectedTableIndex[0] = tableIndex;
          TableDefinition table = model[0].tables.get(tableIndex);

          syncingForm[0] = true;
          try {
            tableNameText.setText(safe(table.name));
            tableKindText.setText(safe(table.kind));
            refreshEntryList.run();
            clearEntryForm.run();
          } finally {
            syncingForm[0] = false;
          }
        };

    Runnable loadFile =
        () -> {
          int fileIndex = fileCombo.getSelectionIndex();
          if (fileIndex < 0 || fileIndex >= files.size()) {
            return;
          }
          try {
            model[0] = service.loadTables(files.get(fileIndex));
            refreshTableList.run();
            clearTableForm.run();
            if (!model[0].tables.isEmpty()) {
              tableList.setSelection(0);
              loadSelectedTableToForm.run();
            }
          } catch (Exception ex) {
            showError(dialog, ex.getMessage());
          }
        };

    try {
      files.addAll(service.listTableFiles());
      for (Path file : files) {
        fileCombo.add(file.getFileName().toString());
      }
      if (!files.isEmpty()) {
        fileCombo.select(0);
        loadFile.run();
      }
    } catch (Exception ex) {
      showError(dialog, ex.getMessage());
    }

    fileCombo.addListener(SWT.Selection, event -> loadFile.run());
    tableList.addListener(SWT.Selection, event -> loadSelectedTableToForm.run());
    entryList.addListener(SWT.Selection, event -> loadSelectedEntryToForm.run());
    typeCombo.addListener(
        SWT.Selection,
        event -> {
          if (!syncingForm[0]) {
            updateEntryFieldsEnabled.run();
          }
        });

    tableNameText.addModifyListener(
        event -> {
          if (syncingForm[0]) {
            return;
          }
          int tableIndex = selectedTableIndex[0];
          if (tableIndex < 0 || tableIndex >= model[0].tables.size()) {
            return;
          }
          model[0].tables.get(tableIndex).name = tableNameText.getText().trim();
          refreshTableList.run();
          tableList.setSelection(tableIndex);
        });

    tableKindText.addModifyListener(
        event -> {
          if (syncingForm[0]) {
            return;
          }
          int tableIndex = selectedTableIndex[0];
          if (tableIndex < 0 || tableIndex >= model[0].tables.size()) {
            return;
          }
          model[0].tables.get(tableIndex).kind = tableKindText.getText().trim();
          refreshTableList.run();
          tableList.setSelection(tableIndex);
        });

    newButton.addListener(SWT.Selection, event -> clearTableForm.run());

    applyTableButton.addListener(
        SWT.Selection,
        event -> {
          String name = tableNameText.getText().trim();
          if (name.isEmpty()) {
            showWarning(dialog, I18n.t("editor.tables.message.requiredTableName"));
            return;
          }
          int tableIndex = selectedTableIndex[0];
          if (tableIndex >= 0 && tableIndex < model[0].tables.size()) {
            TableDefinition table = model[0].tables.get(tableIndex);
            table.name = name;
            table.kind = tableKindText.getText().trim();
            refreshTableList.run();
            tableList.setSelection(tableIndex);
          } else {
            TableDefinition table = new TableDefinition();
            table.name = name;
            table.kind = tableKindText.getText().trim();
            model[0].tables.add(table);
            refreshTableList.run();
            tableList.setSelection(model[0].tables.size() - 1);
            loadSelectedTableToForm.run();
          }
        });

    deleteButton.addListener(
        SWT.Selection,
        event -> {
          int tableIndex = tableList.getSelectionIndex();
          if (tableIndex < 0 || tableIndex >= model[0].tables.size()) {
            showWarning(dialog, I18n.t("editor.tables.message.selectTable"));
            return;
          }
          if (model[0].tables.size() <= 1) {
            showWarning(dialog, I18n.t("editor.tables.message.lastTable"));
            return;
          }
          if (!confirm(dialog, I18n.t("editor.message.deleteConfirm"))) {
            return;
          }
          model[0].tables.remove(tableIndex);
          refreshTableList.run();
          clearTableForm.run();
          if (!model[0].tables.isEmpty()) {
            tableList.setSelection(Math.max(0, tableIndex - 1));
            loadSelectedTableToForm.run();
          }
        });

    newEntryButton.addListener(SWT.Selection, event -> clearEntryForm.run());

    applyEntryButton.addListener(
        SWT.Selection,
        event -> {
          int tableIndex = selectedTableIndex[0];
          if (tableIndex < 0 || tableIndex >= model[0].tables.size()) {
            showWarning(dialog, I18n.t("editor.tables.message.selectTable"));
            return;
          }

          String type = typeCombo.getText();
          if (type == null || type.isBlank()) {
            type = "monster";
          }

          TableEntry entry = new TableEntry();
          entry.type = type.trim();

          try {
            if ("group".equals(entry.type)) {
              entry.groupMembers = parseGroupMembersRaw(groupMembersText.getText());
              if (entry.groupMembers.isEmpty()) {
                throw new IllegalArgumentException(I18n.t("editor.tables.message.requiredGroupMembers"));
              }
            } else if ("event".equals(entry.type)) {
              entry.id = idText.getText().trim();
              validateRequiredField(entry.id, I18n.t("editor.message.requiredId"));
            } else {
              entry.type = "monster";
              entry.id = idText.getText().trim();
              entry.number = numberText.getText().trim();
              entry.specialRaw = specialText.getText().trim();
              validateRequiredField(entry.id, I18n.t("editor.message.requiredId"));
              validateRequiredField(entry.number, I18n.t("editor.tables.message.requiredNumber"));
            }

            TableDefinition table = model[0].tables.get(tableIndex);
            int entryIndex = selectedEntryIndex[0];
            if (entryIndex >= 0 && entryIndex < table.entries.size()) {
              table.entries.set(entryIndex, entry);
            } else {
              table.entries.add(entry);
              entryIndex = table.entries.size() - 1;
            }

            refreshEntryList.run();
            entryList.setSelection(entryIndex);
            loadSelectedEntryToForm.run();
          } catch (Exception ex) {
            showError(dialog, ex.getMessage());
          }
        });

    deleteEntryButton.addListener(
        SWT.Selection,
        event -> {
          int tableIndex = selectedTableIndex[0];
          if (tableIndex < 0 || tableIndex >= model[0].tables.size()) {
            showWarning(dialog, I18n.t("editor.tables.message.selectTable"));
            return;
          }
          TableDefinition table = model[0].tables.get(tableIndex);
          int entryIndex = entryList.getSelectionIndex();
          if (entryIndex < 0 || entryIndex >= table.entries.size()) {
            showWarning(dialog, I18n.t("editor.message.selectEntry"));
            return;
          }
          if (table.entries.size() <= 1) {
            showWarning(dialog, I18n.t("editor.message.lastEntry"));
            return;
          }
          if (!confirm(dialog, I18n.t("editor.message.deleteConfirm"))) {
            return;
          }
          table.entries.remove(entryIndex);
          refreshEntryList.run();
          clearEntryForm.run();
        });

    saveButton.addListener(
        SWT.Selection,
        event -> {
          if (!hasSelectedFile(fileCombo, files, dialog)) {
            return;
          }
          try {
            service.saveTables(selectedFile(fileCombo, files), model[0]);
            notifySaved();
            showInfo(dialog, I18n.t("editor.message.saved"));
          } catch (Exception ex) {
            showError(dialog, ex.getMessage());
          }
        });

    reloadButton.addListener(SWT.Selection, event -> loadFile.run());
    validateButton.addListener(
        SWT.Selection,
        event -> {
          if (!hasSelectedFile(fileCombo, files, dialog)) {
            return;
          }
          try {
            service.validateTablesFile(selectedFile(fileCombo, files));
            showInfo(dialog, I18n.t("editor.message.validated"));
          } catch (Exception ex) {
            showError(dialog, ex.getMessage());
          }
        });
  }

  private void createMonstersTab(TabFolder tabs, Shell dialog) {
    TabItem tab = new TabItem(tabs, SWT.NONE);
    tab.setText(I18n.t("dialog.contentEditor.tab.monsters"));

    Composite root = new Composite(tabs, SWT.NONE);
    root.setLayout(new GridLayout(1, false));
    tab.setControl(root);

    EditorHeader header = createEditorHeader(root);
    Combo fileCombo = header.fileCombo();
    Button newButton = header.newButton();
    Button deleteButton = header.deleteButton();
    Button saveButton = header.saveButton();
    Button reloadButton = header.reloadButton();
    Button validateButton = header.validateButton();

    SashForm sash = new SashForm(root, SWT.HORIZONTAL);
    sash.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

    org.eclipse.swt.widgets.List itemList = new org.eclipse.swt.widgets.List(sash, SWT.BORDER | SWT.V_SCROLL);
    Composite details = new Composite(sash, SWT.NONE);
    details.setLayout(new GridLayout(1, false));
    sash.setWeights(new int[] {30, 70});

    Group attributes = new Group(details, SWT.NONE);
    attributes.setText("Atributos/Attributes");
    attributes.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
    GridLayout attrsLayout = new GridLayout(4, false);
    attrsLayout.marginWidth = 8;
    attributes.setLayout(attrsLayout);

    Text idText = createLabeledText(attributes, "ID:");
    Text nameText = createLabeledText(attributes, "Nombre/Name:");
    Text pluralText = createLabeledText(attributes, "Plural:");
    Text factionsText = createLabeledText(attributes, "Factions:");
    Text woundsText = createLabeledText(attributes, "Wounds:");
    Text moveText = createLabeledText(attributes, "Move:");
    Text wsText = createLabeledText(attributes, "WS:");
    Text bsText = createLabeledText(attributes, "BS:");
    Text strengthText = createLabeledText(attributes, "Strength:");
    Text toughnessText = createLabeledText(attributes, "Toughness:");
    Text initiativeText = createLabeledText(attributes, "Initiative:");
    Text attacksText = createLabeledText(attributes, "Attacks:");
    Text goldText = createLabeledText(attributes, "Gold:");
    Text armorText = createLabeledText(attributes, "Armor:");
    Text damageText = createLabeledText(attributes, "Damage:");

    Group specialGroup = new Group(details, SWT.NONE);
    specialGroup.setText("Special (líneas: text|texto, rule|id|texto, magic|id|nivel)");
    specialGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    specialGroup.setLayout(new GridLayout(1, false));

    StyledText specialText = new StyledText(specialGroup, SWT.BORDER | SWT.WRAP | SWT.V_SCROLL);
    specialText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

    java.util.List<Path> files = new ArrayList<>();
    java.util.List<MonsterEntry> entries = new ArrayList<>();
    final int[] selectedIndex = new int[] {-1};

    Runnable refreshList =
        () -> {
          itemList.removeAll();
          for (MonsterEntry entry : entries) {
            itemList.add(safe(entry.id) + " - " + safe(entry.name));
          }
        };

    Runnable clearForm =
        () -> {
          selectedIndex[0] = -1;
          itemList.deselectAll();
          idText.setText("");
          nameText.setText("");
          pluralText.setText("");
          factionsText.setText("");
          woundsText.setText("");
          moveText.setText("");
          wsText.setText("");
          bsText.setText("");
          strengthText.setText("");
          toughnessText.setText("");
          initiativeText.setText("");
          attacksText.setText("");
          goldText.setText("");
          armorText.setText("");
          damageText.setText("");
          specialText.setText("");
        };

    Runnable loadSelectedToForm =
        () -> {
          int index = itemList.getSelectionIndex();
          if (index < 0 || index >= entries.size()) {
            return;
          }
          selectedIndex[0] = index;
          MonsterEntry entry = entries.get(index);
          idText.setText(safe(entry.id));
          nameText.setText(safe(entry.name));
          pluralText.setText(safe(entry.plural));
          factionsText.setText(safe(entry.factions));
          woundsText.setText(safe(entry.wounds));
          moveText.setText(safe(entry.move));
          wsText.setText(safe(entry.weaponSkill));
          bsText.setText(safe(entry.ballisticSkill));
          strengthText.setText(safe(entry.strength));
          toughnessText.setText(safe(entry.toughness));
          initiativeText.setText(safe(entry.initiative));
          attacksText.setText(safe(entry.attacks));
          goldText.setText(safe(entry.gold));
          armorText.setText(safe(entry.armor));
          damageText.setText(safe(entry.damage));
          specialText.setText(safe(entry.specialEntriesRaw));
        };

    Runnable loadFile =
        () -> {
          int fileIndex = fileCombo.getSelectionIndex();
          if (fileIndex < 0 || fileIndex >= files.size()) {
            return;
          }
          try {
            entries.clear();
            entries.addAll(service.loadMonsters(files.get(fileIndex)));
            refreshList.run();
            clearForm.run();
          } catch (Exception ex) {
            showError(dialog, ex.getMessage());
          }
        };

    try {
      files.addAll(service.listMonsterFiles());
      for (Path file : files) {
        fileCombo.add(file.getFileName().toString());
      }
      if (!files.isEmpty()) {
        fileCombo.select(0);
        loadFile.run();
      }
    } catch (Exception ex) {
      showError(dialog, ex.getMessage());
    }

    fileCombo.addListener(SWT.Selection, event -> loadFile.run());
    itemList.addListener(SWT.Selection, event -> loadSelectedToForm.run());
    newButton.addListener(SWT.Selection, event -> clearForm.run());

    deleteButton.addListener(
        SWT.Selection,
        event -> {
          int index = itemList.getSelectionIndex();
          if (index < 0 || index >= entries.size()) {
            showWarning(dialog, I18n.t("editor.message.selectEntry"));
            return;
          }
          if (entries.size() <= 1) {
            showWarning(dialog, I18n.t("editor.message.lastEntry"));
            return;
          }
          if (!confirm(dialog, I18n.t("editor.message.deleteConfirm"))) {
            return;
          }
          entries.remove(index);
          try {
            service.saveMonsters(selectedFile(fileCombo, files), entries);
            refreshList.run();
            clearForm.run();
            notifySaved();
            showInfo(dialog, I18n.t("editor.message.saved"));
          } catch (Exception ex) {
            showError(dialog, ex.getMessage());
          }
        });

    saveButton.addListener(
        SWT.Selection,
        event -> {
          if (!hasSelectedFile(fileCombo, files, dialog)) {
            return;
          }

          MonsterEntry entry = new MonsterEntry();
          entry.id = idText.getText().trim();
          entry.name = nameText.getText().trim();
          entry.plural = pluralText.getText().trim();
          entry.factions = factionsText.getText().trim();
          entry.wounds = woundsText.getText().trim();
          entry.move = moveText.getText().trim();
          entry.weaponSkill = wsText.getText().trim();
          entry.ballisticSkill = bsText.getText().trim();
          entry.strength = strengthText.getText().trim();
          entry.toughness = toughnessText.getText().trim();
          entry.initiative = initiativeText.getText().trim();
          entry.attacks = attacksText.getText().trim();
          entry.gold = goldText.getText().trim();
          entry.armor = armorText.getText().trim();
          entry.damage = damageText.getText().trim();
          entry.specialEntriesRaw = specialText.getText().trim();

          try {
            ensureUniqueId(entries, selectedIndex[0], entry.id);
            if (selectedIndex[0] >= 0 && selectedIndex[0] < entries.size()) {
              entries.set(selectedIndex[0], entry);
            } else {
              entries.add(entry);
            }
            service.saveMonsters(selectedFile(fileCombo, files), entries);
            refreshList.run();
            selectById(itemList, entries, entry.id);
            notifySaved();
            showInfo(dialog, I18n.t("editor.message.saved"));
          } catch (Exception ex) {
            showError(dialog, ex.getMessage());
          }
        });

    reloadButton.addListener(SWT.Selection, event -> loadFile.run());
    validateButton.addListener(
        SWT.Selection,
        event -> {
          if (!hasSelectedFile(fileCombo, files, dialog)) {
            return;
          }
          try {
            service.validateMonstersFile(selectedFile(fileCombo, files));
            showInfo(dialog, I18n.t("editor.message.validated"));
          } catch (Exception ex) {
            showError(dialog, ex.getMessage());
          }
        });
  }

  private static Text createLabeledText(Composite parent, String label) {
    Label l = new Label(parent, SWT.NONE);
    l.setText(label);
    l.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
    Text text = new Text(parent, SWT.BORDER);
    text.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    return text;
  }

  private <T> void ensureUniqueId(java.util.List<T> entries, int selectedIndex, String id) {
    String normalized = id == null ? "" : id.trim();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException(I18n.t("editor.message.requiredId"));
    }
    for (int i = 0; i < entries.size(); i++) {
      if (i == selectedIndex) {
        continue;
      }
      String candidateId = extractId(entries.get(i));
      if (normalized.equals(candidateId)) {
        throw new IllegalArgumentException(I18n.t("editor.message.duplicateId") + " " + normalized);
      }
    }
  }

  private static String tableLabel(TableDefinition table) {
    String name = safe(table == null ? "" : table.name);
    String kind = safe(table == null ? "" : table.kind);
    if (kind.isEmpty()) {
      return name;
    }
    return name + " [" + kind + "]";
  }

  private static String tableEntryLabel(TableEntry entry) {
    if (entry == null) {
      return "";
    }
    String type = safe(entry.type);
    if ("group".equals(type)) {
      int members = entry.groupMembers == null ? 0 : entry.groupMembers.size();
      return "group (" + members + ")";
    }
    if ("event".equals(type)) {
      return "event | " + safe(entry.id);
    }
    return "monster | " + safe(entry.id) + " | " + safe(entry.number);
  }

  private static List<TableGroupMember> parseGroupMembersRaw(String raw) {
    List<TableGroupMember> members = new ArrayList<>();
    if (raw == null || raw.isBlank()) {
      return members;
    }

    String[] lines = raw.split("\\R");
    for (int i = 0; i < lines.length; i++) {
      String line = lines[i] == null ? "" : lines[i].trim();
      if (line.isEmpty()) {
        continue;
      }
      String[] parts = line.split("\\|", 3);
      if (parts.length < 2) {
        throw new IllegalArgumentException("Invalid group member at line " + (i + 1) + ": " + line);
      }
      TableGroupMember member = new TableGroupMember();
      member.id = parts[0].trim();
      member.number = parts[1].trim();
      member.specialRaw = parts.length > 2 ? decodeInline(parts[2]) : "";
      if (member.id.isEmpty() || member.number.isEmpty()) {
        throw new IllegalArgumentException("Invalid group member at line " + (i + 1) + ": " + line);
      }
      members.add(member);
    }
    return members;
  }

  private static String groupMembersToRaw(List<TableGroupMember> members) {
    if (members == null || members.isEmpty()) {
      return "";
    }
    List<String> lines = new ArrayList<>();
    for (TableGroupMember member : members) {
      if (member == null) {
        continue;
      }
      String base = safe(member.id) + "|" + safe(member.number);
      String special = safe(member.specialRaw);
      if (!special.isEmpty()) {
        base = base + "|" + encodeInline(special);
      }
      lines.add(base);
    }
    return String.join(System.lineSeparator(), lines);
  }

  private static String encodeInline(String value) {
    if (value == null || value.isEmpty()) {
      return "";
    }
    return value.replace("\\", "\\\\").replace("\n", "\\n");
  }

  private static String decodeInline(String value) {
    if (value == null || value.isEmpty()) {
      return "";
    }
    return value.replace("\\n", "\n").replace("\\\\", "\\");
  }

  private static void validateRequiredField(String value, String message) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(message == null || message.isBlank() ? "Required field." : message);
    }
  }

  private List<Path> listNonTreasureEventFiles() throws Exception {
    List<Path> files = new ArrayList<>();
    for (Path file : service.listEventFiles()) {
      if (!isTreasureFile(file)) {
        files.add(file);
      }
    }
    return files;
  }

  private List<Path> listTreasureEventFiles() throws Exception {
    List<Path> files = new ArrayList<>();
    for (Path file : service.listEventFiles()) {
      if (isTreasureFile(file)) {
        files.add(file);
      }
    }
    return files;
  }

  private static boolean isTreasureFile(Path file) {
    String name = file == null ? "" : safe(file.getFileName().toString()).toLowerCase();
    return name.contains("treasure");
  }

  private boolean isDungeonTreasureEntry(EventEntry entry) {
    String normalizedId = safe(entry == null ? "" : entry.id).trim().toLowerCase();
    return entry != null && entry.treasure && !normalizedId.contains("-objective-");
  }

  private boolean isObjectiveTreasureEntry(EventEntry entry) {
    return isTreasureEntry(entry, "objective");
  }

  private static boolean isTreasureEntry(EventEntry entry, String section) {
    String normalizedId = safe(entry == null ? "" : entry.id).trim().toLowerCase();
    return entry != null
        && entry.treasure
        && normalizedId.contains("-treasure-")
        && normalizedId.contains("-" + safe(section).toLowerCase() + "-");
  }

  private List<EventEntry> mergeVisibleEvents(
      Path file, List<EventEntry> visibleEntries, Predicate<EventEntry> entryFilter) throws Exception {
    if (entryFilter == null) {
      return new ArrayList<>(visibleEntries);
    }

    List<EventEntry> originalEntries = service.loadEvents(file);
    List<EventEntry> mergedEntries = new ArrayList<>();
    int visibleIndex = 0;

    for (EventEntry originalEntry : originalEntries) {
      if (entryFilter.test(originalEntry)) {
        if (visibleIndex < visibleEntries.size()) {
          mergedEntries.add(visibleEntries.get(visibleIndex++));
        }
      } else {
        mergedEntries.add(originalEntry);
      }
    }

    while (visibleIndex < visibleEntries.size()) {
      mergedEntries.add(visibleEntries.get(visibleIndex++));
    }

    return mergedEntries;
  }

  private static List<EventEntry> filterEvents(List<EventEntry> entries, Predicate<EventEntry> entryFilter) {
    if (entryFilter == null) {
      return new ArrayList<>(entries);
    }

    List<EventEntry> filteredEntries = new ArrayList<>();
    for (EventEntry entry : entries) {
      if (entryFilter.test(entry)) {
        filteredEntries.add(entry);
      }
    }
    return filteredEntries;
  }

  private static String extractId(Object entry) {
    if (entry instanceof RuleEntry e) {
      return safe(e.id);
    }
    if (entry instanceof EventEntry e) {
      return safe(e.id);
    }
    if (entry instanceof MonsterEntry e) {
      return safe(e.id);
    }
    return "";
  }

  private <T> void selectById(
      org.eclipse.swt.widgets.List listWidget, java.util.List<T> entries, String id) {
    String normalized = safe(id);
    for (int i = 0; i < entries.size(); i++) {
      if (normalized.equals(extractId(entries.get(i)))) {
        listWidget.setSelection(i);
        break;
      }
    }
  }

  private static boolean hasSelectedFile(Combo fileCombo, java.util.List<Path> files, Shell shell) {
    int fileIndex = fileCombo.getSelectionIndex();
    if (fileIndex < 0 || fileIndex >= files.size()) {
      MessageBox box = new MessageBox(shell, SWT.ICON_WARNING | SWT.OK);
      box.setText("Warning");
      box.setMessage(I18n.t("editor.message.selectFile"));
      box.open();
      return false;
    }
    return true;
  }

  private static Path selectedFile(Combo fileCombo, java.util.List<Path> files) {
    return files.get(fileCombo.getSelectionIndex());
  }

  private void notifySaved() {
    if (onContentSaved != null) {
      onContentSaved.run();
    }
  }

  private static void showError(Shell parent, String message) {
    MessageBox box = new MessageBox(parent, SWT.ICON_ERROR | SWT.OK);
    box.setText("Error");
    box.setMessage(message == null ? "" : message);
    box.open();
  }

  private static void showWarning(Shell parent, String message) {
    MessageBox box = new MessageBox(parent, SWT.ICON_WARNING | SWT.OK);
    box.setText("Warning");
    box.setMessage(message == null ? "" : message);
    box.open();
  }

  private static void showInfo(Shell parent, String message) {
    MessageBox box = new MessageBox(parent, SWT.ICON_INFORMATION | SWT.OK);
    box.setText("Info");
    box.setMessage(message == null ? "" : message);
    box.open();
  }

  private static boolean confirm(Shell parent, String message) {
    MessageBox box = new MessageBox(parent, SWT.ICON_QUESTION | SWT.YES | SWT.NO);
    box.setText("Confirm");
    box.setMessage(message == null ? "" : message);
    return box.open() == SWT.YES;
  }

  private static String safe(String value) {
    return value == null ? "" : value;
  }

  private static String slugify(String value) {
    String normalized = safe(value).trim().toLowerCase(Locale.ROOT).replace("&", "and");
    normalized = normalized.replaceAll("[^a-z0-9]+", "-");
    normalized = normalized.replaceAll("^-+|-+$", "");
    return normalized.isBlank() ? "userdefined-entry" : normalized;
  }

  private Composite createActionRow(Composite parent, int columns) {
    Composite actions = new Composite(parent, SWT.NONE);
    actions.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false));
    GridLayout layout = new GridLayout(columns, false);
    layout.marginWidth = 0;
    actions.setLayout(layout);
    return actions;
  }

  private Button createActionButton(Composite parent, String textKey) {
    Button button = new Button(parent, SWT.PUSH);
    button.setText(I18n.t(textKey));
    button.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    return button;
  }

  private EditorHeader createEditorHeader(Composite parent) {
    Composite header = new Composite(parent, SWT.NONE);
    header.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    GridLayout headerLayout = new GridLayout(HEADER_BUTTON_COLUMNS, false);
    headerLayout.marginWidth = 0;
    header.setLayout(headerLayout);

    Label fileLabel = new Label(header, SWT.NONE);
    fileLabel.setText(I18n.t("editor.label.file"));

    Combo fileCombo = new Combo(header, SWT.DROP_DOWN | SWT.READ_ONLY);
    fileCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

    Button newButton = createActionButton(header, "editor.button.new");
    Button deleteButton = createActionButton(header, "editor.button.delete");
    Button saveButton = createActionButton(header, "editor.button.save");
    Button reloadButton = createActionButton(header, "editor.button.reload");
    Button validateButton = createActionButton(header, "editor.button.validate");

    return new EditorHeader(fileCombo, newButton, deleteButton, saveButton, reloadButton, validateButton);
  }

  private record EditorHeader(
      Combo fileCombo,
      Button newButton,
      Button deleteButton,
      Button saveButton,
      Button reloadButton,
      Button validateButton) {}

  @FunctionalInterface
  private interface CheckedSupplier<T> {
    T get() throws Exception;
  }

  @FunctionalInterface
  private interface CheckedConsumer<T> {
    void accept(T value) throws Exception;
  }
}
