package pms.whq.swt;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Spinner;
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
import pms.whq.data.Event;

public final class EventContentEditorDialog {
  private static final int HEADER_BUTTON_COLUMNS = 9;
  private static final double TREASURE_CARD_ASPECT_RATIO = 847d / 1264d;

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

    Text nameText = createLabeledText(form, I18n.t("editor.dungeonCards.label.name") + ":");
    new Label(form, SWT.NONE).setText(I18n.t("editor.dungeonCards.label.type") + ":");
    Combo typeCombo = new Combo(form, SWT.DROP_DOWN | SWT.READ_ONLY);
    typeCombo.setItems(new String[] {"DUNGEON_ROOM", "OBJECTIVE_ROOM", "CORRIDOR", "SPECIAL"});
    typeCombo.select(0);
    typeCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    Text environmentText = createLabeledText(form, I18n.t("editor.dungeonCards.label.environment") + ":");
    new Label(form, SWT.NONE).setText(I18n.t("editor.dungeonCards.label.tile") + ":");
    Composite tileSelector = new Composite(form, SWT.NONE);
    tileSelector.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    GridLayout tileLayout = new GridLayout(2, false);
    tileLayout.marginWidth = 0;
    tileLayout.marginHeight = 0;
    tileSelector.setLayout(tileLayout);
    Text tilePathText = new Text(tileSelector, SWT.BORDER | SWT.READ_ONLY);
    tilePathText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    Button browseTileButton = new Button(tileSelector, SWT.PUSH);
    browseTileButton.setText(I18n.t("editor.button.browse"));
    new Label(form, SWT.NONE).setText(I18n.t("editor.dungeonCards.label.copies") + ":");
    org.eclipse.swt.widgets.Spinner copySpinner = new org.eclipse.swt.widgets.Spinner(form, SWT.BORDER);
    copySpinner.setMinimum(0);
    copySpinner.setMaximum(999);
    copySpinner.setSelection(1);
    copySpinner.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    new Label(form, SWT.NONE).setText(I18n.t("editor.dungeonCards.label.enabled") + ":");
    Button enabledCheck = new Button(form, SWT.CHECK);
    enabledCheck.setSelection(true);

    new Label(form, SWT.NONE).setText(I18n.t("editor.dungeonCards.label.description") + ":");
    StyledText descriptionText = new StyledText(form, SWT.BORDER | SWT.WRAP | SWT.V_SCROLL);
    GridData descriptionData = new GridData(SWT.FILL, SWT.FILL, true, true);
    descriptionData.heightHint = 90;
    descriptionText.setLayoutData(descriptionData);

    new Label(form, SWT.NONE).setText(I18n.t("editor.dungeonCards.label.rules") + ":");
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

    browseTileButton.addListener(
        SWT.Selection,
        event -> {
          FileDialog fileDialog = new FileDialog(dialog, SWT.OPEN);
          fileDialog.setText(I18n.t("editor.button.browse"));
          fileDialog.setFilterExtensions(new String[] {"*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.*"});
          String selectedPath = fileDialog.open();
          if (selectedPath == null || selectedPath.isBlank()) {
            return;
          }
          Path normalizedSelection = Path.of(selectedPath).toAbsolutePath().normalize();
          String storedPath;
          if (normalizedSelection.startsWith(projectRoot)) {
            storedPath = projectRoot.relativize(normalizedSelection).toString();
          } else {
            storedPath = normalizedSelection.toString();
          }
          tilePathText.setText(storedPath.replace('\\', '/'));
          buildDraftCard.run();
        });

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
            showError(dialog, ex);
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
            showError(dialog, ex);
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
            showError(dialog, ex);
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
            objectiveRoomAdventureRepository.deleteUserAdventure(objectiveRoomCombo.getText(), idText.getText());
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
            showError(dialog, ex);
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
    Button newFileButton = header.newFileButton();
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

    new Label(details, SWT.NONE).setText(I18n.t("editor.rules.label.type") + ":");
    Combo typeCombo = new Combo(details, SWT.DROP_DOWN | SWT.READ_ONLY);
    typeCombo.setItems(new String[] {"rule", "magic"});
    typeCombo.select(0);
    typeCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

    new Label(details, SWT.NONE).setText(I18n.t("editor.rules.label.id") + ":");
    Text idText = new Text(details, SWT.BORDER);
    idText.setEditable(false);
    idText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

    new Label(details, SWT.NONE).setText(I18n.t("editor.rules.label.name") + ":");
    Text nameText = new Text(details, SWT.BORDER);
    nameText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

    new Label(details, SWT.NONE).setText(I18n.t("editor.rules.label.parameterName") + ":");
    Text parameterNameText = new Text(details, SWT.BORDER);
    parameterNameText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

    new Label(details, SWT.NONE).setText(I18n.t("editor.rules.label.parameterNames") + ":");
    Text parameterNamesText = new Text(details, SWT.BORDER);
    parameterNamesText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

    new Label(details, SWT.NONE).setText(I18n.t("editor.rules.label.parameterFormat") + ":");
    Text parameterFormatText = new Text(details, SWT.BORDER);
    parameterFormatText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

    new Label(details, SWT.NONE).setText(I18n.t("editor.rules.label.text") + ":");
    StyledText contentText = new StyledText(details, SWT.BORDER | SWT.WRAP | SWT.V_SCROLL);
    GridData contentData = new GridData(SWT.FILL, SWT.FILL, true, true);
    contentData.heightHint = 300;
    contentText.setLayoutData(contentData);

    java.util.List<Path> files = new ArrayList<>();
    java.util.List<RuleEntry> entries = new ArrayList<>();
    final int[] selectedIndex = new int[] {-1};
    final String[] selectedRuleId = new String[] {""};

    Runnable refreshId =
        () -> idText.setText(selectedRuleId[0].isBlank() ? slugify(nameText.getText()) : selectedRuleId[0]);

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
          selectedRuleId[0] = "";
          itemList.deselectAll();
          typeCombo.select(0);
          nameText.setText("");
          parameterNameText.setText("");
          parameterNamesText.setText("");
          parameterFormatText.setText("");
          contentText.setText("");
          refreshId.run();
        };

    Runnable loadSelectedToForm =
        () -> {
          int index = itemList.getSelectionIndex();
          if (index < 0 || index >= entries.size()) {
            return;
          }
          selectedIndex[0] = index;
          RuleEntry entry = entries.get(index);
          selectedRuleId[0] = safe(entry.id);
          typeCombo.setText("magic".equals(entry.type) ? "magic" : "rule");
          nameText.setText(safe(entry.name));
          parameterNameText.setText(safe(entry.parameterName));
          parameterNamesText.setText(safe(entry.parameterNames));
          parameterFormatText.setText(safe(entry.parameterFormat));
          contentText.setText(safe(entry.text));
          refreshId.run();
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
            showError(dialog, ex);
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
      showError(dialog, ex);
    }

    fileCombo.addListener(SWT.Selection, event -> loadFile.run());
    newFileButton.addListener(
        SWT.Selection,
        event ->
            createAndSelectXmlFile(
                dialog,
                fileCombo,
                files,
                service.getRulesDirectory(),
                "userdefined-rules.xml",
                service::createEmptyRulesFile,
                service::listRuleFiles,
                loadFile));
    itemList.addListener(SWT.Selection, event -> loadSelectedToForm.run());
    newButton.addListener(SWT.Selection, event -> clearForm.run());
    nameText.addModifyListener(event -> refreshId.run());

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
          try {
            List<RuleEntry> updatedEntries = new ArrayList<>(entries);
            updatedEntries.remove(index);
            service.saveRules(selectedFile(fileCombo, files), updatedEntries);
            entries.clear();
            entries.addAll(updatedEntries);
            refreshList.run();
            clearForm.run();
            notifySaved();
            showInfo(dialog, I18n.t("editor.message.saved"));
          } catch (Exception ex) {
            showError(dialog, ex);
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
          entry.parameterName = parameterNameText.getText().trim();
          entry.parameterNames = parameterNamesText.getText().trim();
          entry.parameterFormat = parameterFormatText.getText().trim();
          entry.text = contentText.getText().trim();

          try {
            ensureUniqueId(entries, selectedIndex[0], entry.id);
            List<RuleEntry> updatedEntries = new ArrayList<>(entries);
            if (selectedIndex[0] >= 0 && selectedIndex[0] < entries.size()) {
              updatedEntries.set(selectedIndex[0], entry);
            } else {
              updatedEntries.add(entry);
            }
            service.saveRules(selectedFile(fileCombo, files), updatedEntries);
            entries.clear();
            entries.addAll(updatedEntries);
            refreshList.run();
            selectById(itemList, entries, entry.id);
            notifySaved();
            showInfo(dialog, I18n.t("editor.message.saved"));
          } catch (Exception ex) {
            showError(dialog, ex);
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
            showError(dialog, ex);
          }
        });
  }

  private void createEventsTab(TabFolder tabs, Shell dialog) {
    createEventLikeTab(
        tabs,
        dialog,
        I18n.t("dialog.contentEditor.tab.events"),
        service.getEventsDirectory(),
        service::createEmptyEventsFile,
        this::listNonTreasureEventFiles,
        path -> service.validateEventsFile(path),
        null,
        false,
        false,
        true);
  }

  private void createDungeonTreasureTab(TabFolder tabs, Shell dialog) {
    createEventLikeTab(
        tabs,
        dialog,
        I18n.t("dialog.contentEditor.tab.treasureDungeon"),
        service.getEventsDirectory(),
        service::createEmptyEventsFile,
        this::listDungeonTreasureEventFiles,
        path -> service.validateEventsFile(path),
        this::isDungeonTreasureEntry,
        false);
  }

  private void createObjectiveTreasureTab(TabFolder tabs, Shell dialog) {
    createEventLikeTab(
        tabs,
        dialog,
        I18n.t("dialog.contentEditor.tab.treasureObjective"),
        service.getEventsDirectory(),
        service::createEmptyEventsFile,
        this::listObjectiveTreasureEventFiles,
        path -> service.validateEventsFile(path),
        this::isObjectiveTreasureEntry,
        true);
  }

  private void createTravelEventsTab(TabFolder tabs, Shell dialog) {
    createEventLikeTab(
        tabs,
        dialog,
        I18n.t("dialog.contentEditor.tab.travel"),
        service.getTravelDirectory(),
        service::createEmptyTravelFile,
        () -> service.listTravelFiles(),
        path -> service.validateTravelFile(path),
        null,
        false,
        false,
        false);
  }

  private void createSettlementEventsTab(TabFolder tabs, Shell dialog) {
    createEventLikeTab(
        tabs,
        dialog,
        I18n.t("dialog.contentEditor.tab.settlement"),
        service.getSettlementDirectory(),
        service::createEmptySettlementFile,
        () -> service.listSettlementFiles(),
        path -> service.validateSettlementFile(path),
        null,
        false,
        false,
        false);
  }

  private void createEventLikeTab(
      TabFolder tabs,
      Shell dialog,
      String tabTitle,
      Path fileDirectory,
      CheckedPathFunction<Path> fileCreator,
      CheckedSupplier<java.util.List<Path>> fileSupplier,
      CheckedConsumer<Path> validator) {
    createEventLikeTab(
        tabs, dialog, tabTitle, fileDirectory, fileCreator, fileSupplier, validator, null, false, false, false);
  }

  private void createEventLikeTab(
      TabFolder tabs,
      Shell dialog,
      String tabTitle,
      Path fileDirectory,
      CheckedPathFunction<Path> fileCreator,
      CheckedSupplier<java.util.List<Path>> fileSupplier,
      CheckedConsumer<Path> validator,
      Predicate<EventEntry> entryFilter) {
    createEventLikeTab(
        tabs,
        dialog,
        tabTitle,
        fileDirectory,
        fileCreator,
        fileSupplier,
        validator,
        entryFilter,
        false,
        true,
        false);
  }

  private void createEventLikeTab(
      TabFolder tabs,
      Shell dialog,
      String tabTitle,
      Path fileDirectory,
      CheckedPathFunction<Path> fileCreator,
      CheckedSupplier<java.util.List<Path>> fileSupplier,
      CheckedConsumer<Path> validator,
      Predicate<EventEntry> entryFilter,
      boolean objectiveTreasurePreview) {
    createEventLikeTab(
        tabs,
        dialog,
        tabTitle,
        fileDirectory,
        fileCreator,
        fileSupplier,
        validator,
        entryFilter,
        objectiveTreasurePreview,
        true,
        false);
  }

  private void createEventLikeTab(
      TabFolder tabs,
      Shell dialog,
      String tabTitle,
      Path fileDirectory,
      CheckedPathFunction<Path> fileCreator,
      CheckedSupplier<java.util.List<Path>> fileSupplier,
      CheckedConsumer<Path> validator,
      Predicate<EventEntry> entryFilter,
      boolean objectiveTreasurePreview,
      boolean treasureFieldsVisible,
      boolean treasureCheckVisible) {
    TabItem tab = new TabItem(tabs, SWT.NONE);
    tab.setText(tabTitle);

    Composite root = new Composite(tabs, SWT.NONE);
    root.setLayout(new GridLayout(1, false));
    tab.setControl(root);

    EditorHeader header = createEditorHeader(root);
    Combo fileCombo = header.fileCombo();
    Button newFileButton = header.newFileButton();
    Button newButton = header.newButton();
    Button deleteButton = header.deleteButton();
    Button saveButton = header.saveButton();
    Button reloadButton = header.reloadButton();
    Button validateButton = header.validateButton();

    SashForm sash = new SashForm(root, SWT.HORIZONTAL);
    sash.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

    org.eclipse.swt.widgets.List itemList = new org.eclipse.swt.widgets.List(sash, SWT.BORDER | SWT.V_SCROLL);
    Composite contentArea = new Composite(sash, SWT.NONE);
    contentArea.setLayout(new GridLayout(treasureFieldsVisible ? 2 : 1, false));
    Composite details = new Composite(contentArea, SWT.NONE);
    details.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    details.setLayout(new GridLayout(2, false));
    sash.setWeights(new int[] {32, 68});

    new Label(details, SWT.NONE).setText(I18n.t("editor.events.label.id") + ":");
    Text idText = new Text(details, SWT.BORDER);
    idText.setEditable(false);
    idText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

    new Label(details, SWT.NONE).setText(I18n.t("editor.events.label.name") + ":");
    Text nameText = new Text(details, SWT.BORDER);
    nameText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

    new Label(details, SWT.NONE).setText(I18n.t("editor.events.label.flavor") + ":");
    StyledText flavorText = new StyledText(details, SWT.BORDER | SWT.WRAP | SWT.V_SCROLL);
    GridData flavorData = new GridData(SWT.FILL, SWT.FILL, true, true);
    flavorData.heightHint = 140;
    flavorText.setLayoutData(flavorData);

    new Label(details, SWT.NONE).setText(I18n.t("editor.events.label.rules") + ":");
    StyledText rulesText = new StyledText(details, SWT.BORDER | SWT.WRAP | SWT.V_SCROLL);
    GridData rulesData = new GridData(SWT.FILL, SWT.FILL, true, true);
    rulesData.heightHint = 210;
    rulesText.setLayoutData(rulesData);

    Label goldValueLabel = new Label(details, SWT.NONE);
    goldValueLabel.setText(I18n.t("editor.events.label.goldValue") + ":");
    Text goldValueText = new Text(details, SWT.BORDER);
    goldValueText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

    Label usersLabel = new Label(details, SWT.NONE);
    usersLabel.setText(I18n.t("editor.events.label.users") + ":");
    Composite usersChecks = new Composite(details, SWT.NONE);
    GridLayout usersChecksLayout = new GridLayout(2, true);
    usersChecksLayout.marginWidth = 0;
    usersChecksLayout.marginHeight = 0;
    usersChecks.setLayout(usersChecksLayout);
    usersChecks.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    Button barbarianCheck = new Button(usersChecks, SWT.CHECK);
    barbarianCheck.setText(I18n.t("card.treasure.user.barbarian"));
    Button dwarfCheck = new Button(usersChecks, SWT.CHECK);
    dwarfCheck.setText(I18n.t("card.treasure.user.dwarf"));
    Button elfCheck = new Button(usersChecks, SWT.CHECK);
    elfCheck.setText(I18n.t("card.treasure.user.elf"));
    Button wizardCheck = new Button(usersChecks, SWT.CHECK);
    wizardCheck.setText(I18n.t("card.treasure.user.wizard"));

    GridData goldLabelData = new GridData(SWT.LEFT, SWT.CENTER, false, false);
    goldLabelData.exclude = !treasureFieldsVisible;
    goldValueLabel.setLayoutData(goldLabelData);
    goldValueLabel.setVisible(treasureFieldsVisible);

    GridData goldTextData = new GridData(SWT.FILL, SWT.CENTER, true, false);
    goldTextData.exclude = !treasureFieldsVisible;
    goldValueText.setLayoutData(goldTextData);
    goldValueText.setVisible(treasureFieldsVisible);

    GridData usersLabelData = new GridData(SWT.LEFT, SWT.CENTER, false, false);
    usersLabelData.exclude = !treasureFieldsVisible;
    usersLabel.setLayoutData(usersLabelData);
    usersLabel.setVisible(treasureFieldsVisible);

    GridData usersTextData = new GridData(SWT.FILL, SWT.CENTER, true, false);
    usersTextData.exclude = !treasureFieldsVisible;
    usersChecks.setLayoutData(usersTextData);
    usersChecks.setVisible(treasureFieldsVisible);

    Label treasureLabel = new Label(details, SWT.NONE);
    treasureLabel.setText(I18n.t("editor.events.label.treasure") + ":");
    Button treasureCheck = new Button(details, SWT.CHECK);

    GridData treasureLabelData = new GridData(SWT.LEFT, SWT.CENTER, false, false);
    treasureLabelData.exclude = !treasureCheckVisible;
    treasureLabel.setLayoutData(treasureLabelData);
    treasureLabel.setVisible(treasureCheckVisible);

    GridData treasureCheckData = new GridData(SWT.LEFT, SWT.CENTER, false, false);
    treasureCheckData.exclude = !treasureCheckVisible;
    treasureCheck.setLayoutData(treasureCheckData);
    treasureCheck.setVisible(treasureCheckVisible);

    Composite treasurePreviewViewport = null;
    Composite treasurePreviewHost = null;
    if (treasureFieldsVisible) {
      Group previewGroup = new Group(contentArea, SWT.NONE);
      previewGroup.setText(I18n.t("dashboard.preview.title"));
      previewGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
      previewGroup.setLayout(new FillLayout());
      treasurePreviewViewport = new Composite(previewGroup, SWT.NONE);
      treasurePreviewViewport.setLayout(null);
      treasurePreviewHost = new Composite(treasurePreviewViewport, SWT.NONE);
      treasurePreviewHost.setLayout(new FillLayout());
    }

    java.util.List<Path> files = new ArrayList<>();
    java.util.List<EventEntry> entries = new ArrayList<>();
    final int[] selectedIndex = new int[] {-1};
    final String[] selectedEntryId = new String[] {""};
    final Composite previewViewport = treasurePreviewViewport;
    final Composite previewHost = treasurePreviewHost;

    Runnable refreshId =
        () ->
            idText.setText(
                selectedEntryId[0].isBlank()
                    ? buildEventLikeId(nameText.getText(), treasureFieldsVisible, objectiveTreasurePreview)
                    : selectedEntryId[0]);

    Runnable refreshTreasurePreview =
        () -> {
          if (!treasureFieldsVisible || previewHost == null || previewHost.isDisposed()) {
            return;
          }
          for (org.eclipse.swt.widgets.Control child : previewHost.getChildren()) {
            child.dispose();
          }
          CardFactory.createTreasureCardPreview(previewHost, toPreviewTreasureEvent(
              nameText.getText(),
              flavorText.getText(),
              rulesText.getText(),
              goldValueText.getText(),
              buildTreasureUsers(barbarianCheck, dwarfCheck, elfCheck, wizardCheck),
              objectiveTreasurePreview));
          layoutTreasurePreview(previewViewport, previewHost);
          previewHost.layout(true, true);
        };

    if (previewViewport != null) {
      previewViewport.addControlListener(
          new ControlAdapter() {
            @Override
            public void controlResized(ControlEvent event) {
              layoutTreasurePreview(previewViewport, previewHost);
            }
          });
    }

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
          selectedEntryId[0] = "";
          itemList.deselectAll();
          nameText.setText("");
          flavorText.setText("");
          rulesText.setText("");
          goldValueText.setText("");
          barbarianCheck.setSelection(false);
          dwarfCheck.setSelection(false);
          elfCheck.setSelection(false);
          wizardCheck.setSelection(false);
          treasureCheck.setSelection(false);
          refreshId.run();
          refreshTreasurePreview.run();
        };

    Runnable loadSelectedToForm =
        () -> {
          int index = itemList.getSelectionIndex();
          if (index < 0 || index >= entries.size()) {
            return;
          }
          selectedIndex[0] = index;
          EventEntry entry = entries.get(index);
          selectedEntryId[0] = safe(entry.id);
          nameText.setText(safe(entry.name));
          flavorText.setText(safe(entry.flavor));
          rulesText.setText(safe(entry.rules));
          goldValueText.setText(safe(entry.goldValue));
          applyTreasureUsers(entry.users, barbarianCheck, dwarfCheck, elfCheck, wizardCheck);
          treasureCheck.setSelection(entry.treasure);
          refreshId.run();
          refreshTreasurePreview.run();
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
            entries.addAll(filterVisibleEvents(files.get(fileIndex), loadedEntries, entryFilter, treasureFieldsVisible, objectiveTreasurePreview));
            refreshList.run();
            clearForm.run();
          } catch (Exception ex) {
            showError(dialog, ex);
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
      showError(dialog, ex);
    }

    fileCombo.addListener(SWT.Selection, event -> loadFile.run());
    newFileButton.addListener(
        SWT.Selection,
        event ->
            createAndSelectXmlFile(
                dialog,
                fileCombo,
                files,
                fileDirectory,
                suggestedEventFileName(fileDirectory, treasureFieldsVisible, objectiveTreasurePreview),
                fileCreator,
                fileSupplier,
                loadFile));
    itemList.addListener(SWT.Selection, event -> loadSelectedToForm.run());
    newButton.addListener(SWT.Selection, event -> clearForm.run());
    nameText.addModifyListener(event -> refreshId.run());
    nameText.addModifyListener(event -> refreshTreasurePreview.run());
    flavorText.addModifyListener(event -> refreshTreasurePreview.run());
    rulesText.addModifyListener(event -> refreshTreasurePreview.run());
    goldValueText.addModifyListener(event -> refreshTreasurePreview.run());
    barbarianCheck.addListener(SWT.Selection, event -> refreshTreasurePreview.run());
    dwarfCheck.addListener(SWT.Selection, event -> refreshTreasurePreview.run());
    elfCheck.addListener(SWT.Selection, event -> refreshTreasurePreview.run());
    wizardCheck.addListener(SWT.Selection, event -> refreshTreasurePreview.run());

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
            showError(dialog, ex);
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
          entry.special = "";
          entry.goldValue = treasureFieldsVisible ? goldValueText.getText().trim() : "";
          entry.users =
              treasureFieldsVisible
                  ? buildTreasureUsers(barbarianCheck, dwarfCheck, elfCheck, wizardCheck)
                  : "";
          entry.treasure = treasureFieldsVisible || (treasureCheckVisible && treasureCheck.getSelection());

          try {
            ensureUniqueId(entries, selectedIndex[0], entry.id);
            Path file = selectedFile(fileCombo, files);
            List<EventEntry> updatedEntries = new ArrayList<>(entries);
            if (selectedIndex[0] >= 0 && selectedIndex[0] < entries.size()) {
              updatedEntries.set(selectedIndex[0], entry);
            } else {
              updatedEntries.add(entry);
            }
            service.saveEvents(file, mergeVisibleEvents(file, updatedEntries, entryFilter));
            entries.clear();
            entries.addAll(updatedEntries);
            refreshList.run();
            selectById(itemList, entries, entry.id);
            notifySaved();
            showInfo(dialog, I18n.t("editor.message.saved"));
          } catch (Exception ex) {
            showError(dialog, ex);
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
            showError(dialog, ex);
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
    Button newFileButton = header.newFileButton();
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
            showError(dialog, ex);
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
      showError(dialog, ex);
    }

    fileCombo.addListener(SWT.Selection, event -> loadFile.run());
    newFileButton.addListener(
        SWT.Selection,
        event ->
            createAndSelectXmlFile(
                dialog,
                fileCombo,
                files,
                service.getTablesDirectory(),
                "userdefined-tables.xml",
                service::createEmptyTablesFile,
                service::listTableFiles,
                loadFile));
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
            showError(dialog, ex);
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
            showError(dialog, ex);
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
            showError(dialog, ex);
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
    Button newFileButton = header.newFileButton();
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
    attributes.setText(I18n.t("editor.monsters.group.attributes"));
    attributes.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
    GridLayout attrsLayout = new GridLayout(4, false);
    attrsLayout.marginWidth = 8;
    attributes.setLayout(attrsLayout);

    Text idText = createLabeledText(attributes, I18n.t("editor.monsters.label.id") + ":");
    idText.setEditable(false);
    Text nameText = createLabeledText(attributes, I18n.t("editor.monsters.label.name") + ":");
    Text pluralText = createLabeledText(attributes, I18n.t("editor.monsters.label.plural") + ":");
    Text factionsText = createLabeledText(attributes, I18n.t("editor.monsters.label.factions") + ":");
    factionsText.setEditable(false);
    new Label(attributes, SWT.NONE);
    Composite factionActions = new Composite(attributes, SWT.NONE);
    factionActions.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    GridLayout factionLayout = new GridLayout(3, false);
    factionLayout.marginWidth = 0;
    factionLayout.marginHeight = 0;
    factionActions.setLayout(factionLayout);
    Combo factionCombo = new Combo(factionActions, SWT.DROP_DOWN | SWT.READ_ONLY);
    factionCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    Button addFactionButton = new Button(factionActions, SWT.PUSH);
    addFactionButton.setText("+");
    Button removeFactionButton = new Button(factionActions, SWT.PUSH);
    removeFactionButton.setText("-");
    Text woundsText = createLabeledText(attributes, I18n.t("editor.monsters.label.wounds") + ":");
    Text moveText = createLabeledText(attributes, I18n.t("editor.monsters.label.move") + ":");
    Text wsText = createLabeledText(attributes, I18n.t("editor.monsters.label.weaponSkill") + ":");
    new Label(attributes, SWT.NONE).setText(I18n.t("editor.monsters.label.ballisticSkill") + ":");
    Combo bsCombo = new Combo(attributes, SWT.DROP_DOWN | SWT.READ_ONLY);
    bsCombo.setItems(monsterBallisticSkillOptions());
    bsCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    bsCombo.setText("-");
    Text strengthText = createLabeledText(attributes, I18n.t("editor.monsters.label.strength") + ":");
    Text toughnessText = createLabeledText(attributes, I18n.t("editor.monsters.label.toughness") + ":");
    Text initiativeText = createLabeledText(attributes, I18n.t("editor.monsters.label.initiative") + ":");
    Text attacksText = createLabeledText(attributes, I18n.t("editor.monsters.label.attacks") + ":");
    Text goldText = createLabeledText(attributes, I18n.t("editor.monsters.label.gold") + ":");
    Text armorText = createLabeledText(attributes, I18n.t("editor.monsters.label.armor") + ":");
    new Label(attributes, SWT.NONE).setText(I18n.t("editor.monsters.label.damage") + ":");
    Combo damageCombo = new Combo(attributes, SWT.DROP_DOWN | SWT.READ_ONLY);
    damageCombo.setItems(monsterDamageOptions());
    damageCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    damageCombo.setText("1D6");

    Group specialGroup = new Group(details, SWT.NONE);
    specialGroup.setText(I18n.t("editor.monsters.group.special"));
    specialGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    specialGroup.setLayout(new GridLayout(2, false));

    new Label(specialGroup, SWT.NONE).setText(I18n.t("editor.monsters.label.specialText") + ":");
    StyledText specialText = new StyledText(specialGroup, SWT.BORDER | SWT.WRAP | SWT.V_SCROLL);
    GridData specialTextData = new GridData(SWT.FILL, SWT.FILL, true, true);
    specialTextData.heightHint = 120;
    specialText.setLayoutData(specialTextData);

    new Label(specialGroup, SWT.NONE).setText(I18n.t("editor.monsters.label.specialRules") + ":");
    org.eclipse.swt.widgets.List specialRulesText =
        new org.eclipse.swt.widgets.List(specialGroup, SWT.BORDER | SWT.SINGLE | SWT.V_SCROLL);
    GridData specialRulesData = new GridData(SWT.FILL, SWT.FILL, true, true);
    specialRulesData.heightHint = 120;
    specialRulesText.setLayoutData(specialRulesData);

    new Label(specialGroup, SWT.NONE).setText(I18n.t("editor.monsters.label.magic") + ":");
    Composite magicActions = new Composite(specialGroup, SWT.NONE);
    magicActions.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    GridLayout magicActionsLayout = new GridLayout(4, false);
    magicActionsLayout.marginWidth = 0;
    magicActionsLayout.marginHeight = 0;
    magicActions.setLayout(magicActionsLayout);
    Combo magicRuleCombo = new Combo(magicActions, SWT.DROP_DOWN | SWT.READ_ONLY);
    magicRuleCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    Spinner magicLevelSpinner = new Spinner(magicActions, SWT.BORDER);
    magicLevelSpinner.setMinimum(0);
    magicLevelSpinner.setMaximum(10);
    magicLevelSpinner.setSelection(0);
    Label magicLevelLabel = new Label(magicActions, SWT.NONE);
    magicLevelLabel.setText(I18n.t("editor.monsters.label.magicLevel") + ":");
    Button clearMagicButton = new Button(magicActions, SWT.PUSH);
    clearMagicButton.setText("-");

    new Label(specialGroup, SWT.NONE);
    Composite specialActions = new Composite(specialGroup, SWT.NONE);
    specialActions.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    GridLayout specialActionsLayout = new GridLayout(4, false);
    specialActionsLayout.marginWidth = 0;
    specialActionsLayout.marginHeight = 0;
    specialActions.setLayout(specialActionsLayout);
    Combo specialRuleCombo = new Combo(specialActions, SWT.DROP_DOWN | SWT.READ_ONLY);
    specialRuleCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    Button addSpecialRuleButton = new Button(specialActions, SWT.PUSH);
    addSpecialRuleButton.setText("+");
    Button removeSpecialRuleButton = new Button(specialActions, SWT.PUSH);
    removeSpecialRuleButton.setText("-");
    new Label(specialGroup, SWT.NONE);
    Composite specialRuleParams = new Composite(specialGroup, SWT.NONE);
    specialRuleParams.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
    GridLayout specialRuleParamsLayout = new GridLayout(2, false);
    specialRuleParamsLayout.marginWidth = 0;
    specialRuleParamsLayout.marginHeight = 0;
    specialRuleParams.setLayout(specialRuleParamsLayout);

    java.util.List<Path> files = new ArrayList<>();
    java.util.List<MonsterEntry> entries = new ArrayList<>();
    final int[] selectedIndex = new int[] {-1};
    final String[] selectedMonsterId = new String[] {""};
    final List<String> selectedFactions = new ArrayList<>();
    final Map<String, MonsterSpecialRuleLink> selectedSpecialRules = new LinkedHashMap<>();
    final List<String> preservedSpecialLines = new ArrayList<>();
    final List<RuleEntry> availableRules = new ArrayList<>();
    final List<RuleEntry> availableMagicRules = new ArrayList<>();
    final List<String> availableFactions = new ArrayList<>();
    final List<Text> specialRuleParameterInputs = new ArrayList<>();
    final List<String> selectedRuleDraftParameters = new ArrayList<>();
    final Runnable[] updateSelectedSpecialRule = new Runnable[1];
    final String[] selectedLinkedRuleId = new String[] {""};
    final String[] selectedRuleDraftId = new String[] {""};

    Runnable refreshList =
        () -> {
          itemList.removeAll();
          for (MonsterEntry entry : entries) {
            itemList.add(safe(entry.id) + " - " + safe(entry.name));
          }
        };

    Runnable refreshMonsterId =
        () -> idText.setText(selectedMonsterId[0].isBlank() ? slugify(nameText.getText()) : selectedMonsterId[0]);

    Runnable refreshFactionText =
        () -> factionsText.setText(String.join(",", selectedFactions));

    Runnable refreshSpecialRuleText =
        () -> {
          specialRulesText.removeAll();
          int indexToSelect = -1;
          int index = 0;
          for (Map.Entry<String, MonsterSpecialRuleLink> entry : selectedSpecialRules.entrySet()) {
            specialRulesText.add(formatRuleLinks(Map.of(entry.getKey(), entry.getValue())));
            if (safe(selectedLinkedRuleId[0]).equals(entry.getKey())) {
              indexToSelect = index;
            }
            index++;
          }
          if (indexToSelect >= 0) {
            specialRulesText.select(indexToSelect);
          }
        };

    Runnable refreshSelectedRuleParameter =
        () -> {
          for (Control child : specialRuleParams.getChildren()) {
            child.dispose();
          }
          specialRuleParameterInputs.clear();
          int ruleIndex = specialRuleCombo.getSelectionIndex();
          if (ruleIndex < 0 || ruleIndex >= availableRules.size()) {
            selectedRuleDraftId[0] = "";
            selectedRuleDraftParameters.clear();
            specialRuleParams.layout(true, true);
            specialGroup.layout(true, true);
            details.layout(true, true);
            return;
          }
          RuleEntry rule = availableRules.get(ruleIndex);
          selectedRuleDraftId[0] = safe(rule.id);
          List<String> labels = ruleParameterLabels(rule);
          if (labels.isEmpty()) {
            selectedRuleDraftParameters.clear();
            specialRuleParams.layout(true, true);
            specialGroup.layout(true, true);
            details.layout(true, true);
            return;
          }
          MonsterSpecialRuleLink selectedRule = selectedSpecialRules.get(rule.id);
          List<String> parameters =
              selectedRule != null
                  ? (!selectedRule.parameters().isEmpty()
                      ? selectedRule.parameters()
                      : inferSpecialRuleParameters(rule.name, selectedRule.name(), rule.parameterFormat))
                  : List.of();
          selectedRuleDraftParameters.clear();
          for (int i = 0; i < labels.size(); i++) {
            selectedRuleDraftParameters.add(i < parameters.size() ? safe(parameters.get(i)) : "");
          }
          for (int i = 0; i < labels.size(); i++) {
            new Label(specialRuleParams, SWT.NONE).setText(labels.get(i) + ":");
            Text input = new Text(specialRuleParams, SWT.BORDER);
            input.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
            input.setMessage(labels.get(i));
            input.setText(i < selectedRuleDraftParameters.size() ? safe(selectedRuleDraftParameters.get(i)) : "");
            final int parameterIndex = i;
            input.addModifyListener(
                event -> {
                  while (selectedRuleDraftParameters.size() <= parameterIndex) {
                    selectedRuleDraftParameters.add("");
                  }
                  selectedRuleDraftParameters.set(parameterIndex, input.getText().trim());
                  updateSelectedSpecialRule[0].run();
                });
            specialRuleParameterInputs.add(input);
          }
          specialRuleParams.layout(true, true);
          specialGroup.layout(true, true);
          details.layout(true, true);
        };

    updateSelectedSpecialRule[0] =
        () -> {
          int ruleIndex = specialRuleCombo.getSelectionIndex();
          if (ruleIndex < 0 || ruleIndex >= availableRules.size()) {
            return;
          }
          RuleEntry rule = availableRules.get(ruleIndex);
          MonsterSpecialRuleLink selectedRule = selectedSpecialRules.get(rule.id);
          if (selectedRule == null) {
            return;
          }
          List<String> parameters = new ArrayList<>();
          if (safe(rule.id).equals(selectedRuleDraftId[0])) {
            parameters.addAll(selectedRuleDraftParameters);
          } else {
            for (Text input : specialRuleParameterInputs) {
              parameters.add(input.getText().trim());
            }
          }
          selectedSpecialRules.put(
              rule.id, new MonsterSpecialRuleLink(rule.name, parameters, safe(rule.parameterFormat)));
          refreshSpecialRuleText.run();
        };

    Runnable clearForm =
        () -> {
          selectedIndex[0] = -1;
          selectedMonsterId[0] = "";
          itemList.deselectAll();
          nameText.setText("");
          pluralText.setText("");
          selectedFactions.clear();
          preservedSpecialLines.clear();
          selectedSpecialRules.clear();
          selectedLinkedRuleId[0] = "";
          selectedRuleDraftId[0] = "";
          selectedRuleDraftParameters.clear();
          for (Control child : specialRuleParams.getChildren()) {
            child.dispose();
          }
          specialRuleParameterInputs.clear();
          specialRuleParams.layout(true, true);
          specialGroup.layout(true, true);
          details.layout(true, true);
          magicRuleCombo.deselectAll();
          if (magicRuleCombo.getItemCount() > 0) {
            magicRuleCombo.select(0);
          }
          magicLevelSpinner.setSelection(0);
          woundsText.setText("");
          moveText.setText("");
          wsText.setText("");
          bsCombo.setText("-");
          strengthText.setText("");
          toughnessText.setText("");
          initiativeText.setText("");
          attacksText.setText("");
          goldText.setText("");
          armorText.setText("");
          damageCombo.setText("1D6");
          specialText.setText("");
          refreshMonsterId.run();
          refreshFactionText.run();
          refreshSpecialRuleText.run();
          refreshSelectedRuleParameter.run();
        };

    Runnable loadSelectedToForm =
        () -> {
          int index = itemList.getSelectionIndex();
          if (index < 0 || index >= entries.size()) {
            return;
          }
          selectedIndex[0] = index;
          MonsterEntry entry = entries.get(index);
          selectedMonsterId[0] = safe(entry.id);
          nameText.setText(safe(entry.name));
          pluralText.setText(safe(entry.plural));
          selectedFactions.clear();
          for (String faction : splitCsv(entry.factions)) {
            if (!faction.isBlank() && !selectedFactions.contains(faction)) {
              selectedFactions.add(faction);
            }
          }
          woundsText.setText(safe(entry.wounds));
          moveText.setText(safe(entry.move));
          wsText.setText(safe(entry.weaponSkill));
          setComboValue(bsCombo, safe(entry.ballisticSkill), "-");
          strengthText.setText(safe(entry.strength));
          toughnessText.setText(safe(entry.toughness));
          initiativeText.setText(safe(entry.initiative));
          attacksText.setText(safe(entry.attacks));
          goldText.setText(safe(entry.gold));
          armorText.setText(safe(entry.armor));
          damageCombo.setText(safe(entry.damage).isBlank() ? "1D6" : safe(entry.damage));
          MonsterSpecialEditorState specialState = parseMonsterSpecialEditorState(entry.specialEntriesRaw);
          specialText.setText(specialState.plainText());
          selectedSpecialRules.clear();
          selectedSpecialRules.putAll(specialState.ruleLinks());
          selectedLinkedRuleId[0] = "";
          selectedRuleDraftId[0] = "";
          selectedRuleDraftParameters.clear();
          setRuleComboSelection(magicRuleCombo, availableMagicRules, specialState.magicRuleId());
          magicLevelSpinner.setSelection(specialState.magicLevel());
          preservedSpecialLines.clear();
          preservedSpecialLines.addAll(specialState.preservedLines());
          refreshMonsterId.run();
          refreshFactionText.run();
          refreshSpecialRuleText.run();
          refreshSelectedRuleParameter.run();
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
            showError(dialog, ex);
          }
        };

    try {
      files.addAll(service.listMonsterFiles());
      availableFactions.addAll(loadAvailableMonsterFactions());
      for (String faction : availableFactions) {
        factionCombo.add(faction);
      }
      if (factionCombo.getItemCount() > 0) {
        factionCombo.select(0);
      }
      availableRules.addAll(loadAvailableMonsterRules());
      for (RuleEntry rule : availableRules) {
        specialRuleCombo.add(ruleDisplayLabel(rule));
      }
      if (specialRuleCombo.getItemCount() > 0) {
        specialRuleCombo.select(0);
        selectedRuleDraftId[0] = safe(availableRules.get(0).id);
        refreshSelectedRuleParameter.run();
      }
      availableMagicRules.addAll(loadAvailableMonsterMagicRules());
      magicRuleCombo.add("");
      for (RuleEntry rule : availableMagicRules) {
        magicRuleCombo.add(ruleDisplayLabel(rule));
      }
      magicRuleCombo.select(0);
      for (Path file : files) {
        fileCombo.add(file.getFileName().toString());
      }
      if (!files.isEmpty()) {
        fileCombo.select(0);
        loadFile.run();
      }
    } catch (Exception ex) {
      showError(dialog, ex);
    }

    fileCombo.addListener(SWT.Selection, event -> loadFile.run());
    newFileButton.addListener(
        SWT.Selection,
        event ->
            createAndSelectXmlFile(
                dialog,
                fileCombo,
                files,
                service.getMonstersDirectory(),
                "userdefined-monsters.xml",
                service::createEmptyMonstersFile,
                service::listMonsterFiles,
                loadFile));
    itemList.addListener(SWT.Selection, event -> loadSelectedToForm.run());
    newButton.addListener(SWT.Selection, event -> clearForm.run());
    nameText.addModifyListener(event -> refreshMonsterId.run());
    addFactionButton.addListener(
        SWT.Selection,
        event -> {
          String faction = factionCombo.getText().trim();
          if (!faction.isEmpty() && !selectedFactions.contains(faction)) {
            selectedFactions.add(faction);
            refreshFactionText.run();
          }
        });
    removeFactionButton.addListener(
        SWT.Selection,
        event -> {
          String faction = factionCombo.getText().trim();
          selectedFactions.removeIf(existing -> existing.equalsIgnoreCase(faction));
          refreshFactionText.run();
        });
    addSpecialRuleButton.addListener(
        SWT.Selection,
        event -> {
          int ruleIndex = specialRuleCombo.getSelectionIndex();
          if (ruleIndex < 0 || ruleIndex >= availableRules.size()) {
            return;
          }
          RuleEntry rule = availableRules.get(ruleIndex);
          List<String> parameters = new ArrayList<>(selectedRuleDraftParameters);
          selectedSpecialRules.put(
              rule.id, new MonsterSpecialRuleLink(rule.name, parameters, safe(rule.parameterFormat)));
          selectedLinkedRuleId[0] = rule.id;
          refreshSpecialRuleText.run();
          refreshSelectedRuleParameter.run();
        });
    clearMagicButton.addListener(
        SWT.Selection,
        event -> {
          magicRuleCombo.select(0);
          magicLevelSpinner.setSelection(0);
        });
    removeSpecialRuleButton.addListener(
        SWT.Selection,
        event -> {
          int ruleIndex = specialRuleCombo.getSelectionIndex();
          if (ruleIndex < 0 || ruleIndex >= availableRules.size()) {
            return;
          }
          selectedSpecialRules.remove(availableRules.get(ruleIndex).id);
          selectedLinkedRuleId[0] = "";
          refreshSpecialRuleText.run();
          refreshSelectedRuleParameter.run();
        });
    specialRuleCombo.addListener(
        SWT.Selection,
        event -> {
          int ruleIndex = specialRuleCombo.getSelectionIndex();
          if (ruleIndex >= 0 && ruleIndex < availableRules.size()) {
            String id = safe(availableRules.get(ruleIndex).id);
            selectedRuleDraftId[0] = id;
            selectedLinkedRuleId[0] = selectedSpecialRules.containsKey(id) ? id : "";
            refreshSpecialRuleText.run();
          }
          refreshSelectedRuleParameter.run();
        });
    specialRulesText.addListener(
        SWT.Selection,
        event -> {
          int selected = specialRulesText.getSelectionIndex();
          if (selected < 0 || selected >= selectedSpecialRules.size()) {
            return;
          }
          String id = new ArrayList<>(selectedSpecialRules.keySet()).get(selected);
          selectedLinkedRuleId[0] = id;
          for (int i = 0; i < availableRules.size(); i++) {
            if (id.equals(safe(availableRules.get(i).id))) {
              specialRuleCombo.select(i);
              selectedRuleDraftId[0] = id;
              refreshSelectedRuleParameter.run();
              break;
            }
          }
        });

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
          try {
            List<MonsterEntry> updatedEntries = new ArrayList<>(entries);
            updatedEntries.remove(index);
            service.saveMonsters(selectedFile(fileCombo, files), updatedEntries);
            entries.clear();
            entries.addAll(updatedEntries);
            refreshList.run();
            clearForm.run();
            notifySaved();
            showInfo(dialog, I18n.t("editor.message.saved"));
          } catch (Exception ex) {
            showError(dialog, ex);
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
          entry.factions = String.join(",", selectedFactions);
          entry.wounds = woundsText.getText().trim();
          entry.move = moveText.getText().trim();
          entry.weaponSkill = wsText.getText().trim();
          entry.ballisticSkill = bsCombo.getText().trim();
          entry.strength = strengthText.getText().trim();
          entry.toughness = toughnessText.getText().trim();
          entry.initiative = initiativeText.getText().trim();
          entry.attacks = attacksText.getText().trim();
          entry.gold = goldText.getText().trim();
          entry.armor = armorText.getText().trim();
          entry.damage = damageCombo.getText().trim();
          entry.specialEntriesRaw =
              buildMonsterSpecialEntriesRaw(
                  specialText.getText(),
                  selectedSpecialRules,
                  selectedRuleId(magicRuleCombo, availableMagicRules),
                  magicLevelSpinner.getSelection(),
                  preservedSpecialLines);

          try {
            ensureUniqueId(entries, selectedIndex[0], entry.id);
            List<MonsterEntry> updatedEntries = new ArrayList<>(entries);
            if (selectedIndex[0] >= 0 && selectedIndex[0] < entries.size()) {
              updatedEntries.set(selectedIndex[0], entry);
            } else {
              updatedEntries.add(entry);
            }
            service.saveMonsters(selectedFile(fileCombo, files), updatedEntries);
            entries.clear();
            entries.addAll(updatedEntries);
            refreshList.run();
            selectById(itemList, entries, entry.id);
            notifySaved();
            showInfo(dialog, I18n.t("editor.message.saved"));
          } catch (Exception ex) {
            showError(dialog, ex);
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
            showError(dialog, ex);
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

  private List<Path> listDungeonTreasureEventFiles() throws Exception {
    return listTreasureEventFiles(this::isDungeonTreasureEntry, false);
  }

  private List<Path> listObjectiveTreasureEventFiles() throws Exception {
    return listTreasureEventFiles(this::isObjectiveTreasureEntry, true);
  }

  private List<Path> listTreasureEventFiles(Predicate<EventEntry> filter, boolean objectiveFiles) throws Exception {
    List<Path> files = new ArrayList<>();
    for (Path file : listTreasureEventFiles()) {
      String fileName = safe(file.getFileName() == null ? "" : file.getFileName().toString()).toLowerCase(Locale.ROOT);
      if (objectiveFiles && fileName.contains("objective")) {
        files.add(file);
        continue;
      }
      if (!objectiveFiles && fileName.contains("objective")) {
        continue;
      }
      List<EventEntry> entries = service.loadEvents(file);
      for (EventEntry entry : entries) {
        if (filter.test(entry)) {
          files.add(file);
          break;
        }
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

  private static List<EventEntry> filterVisibleEvents(
      Path file,
      List<EventEntry> entries,
      Predicate<EventEntry> entryFilter,
      boolean treasureFieldsVisible,
      boolean objectiveTreasurePreview) {
    if (!treasureFieldsVisible || entryFilter == null) {
      return filterEvents(entries, entryFilter);
    }

    String fileName =
        safe(file == null || file.getFileName() == null ? "" : file.getFileName().toString())
            .toLowerCase(Locale.ROOT);
    if (objectiveTreasurePreview && fileName.contains("objective")) {
      List<EventEntry> filteredEntries = new ArrayList<>();
      for (EventEntry entry : entries) {
        if (entry != null && entry.treasure) {
          filteredEntries.add(entry);
        }
      }
      return filteredEntries;
    }
    return filterEvents(entries, entryFilter);
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

  private List<String> loadAvailableMonsterFactions() throws Exception {
    Set<String> factions = new LinkedHashSet<>();
    for (Path file : service.listMonsterFiles()) {
      for (MonsterEntry entry : service.loadMonsters(file)) {
        for (String faction : splitCsv(entry.factions)) {
          if (!faction.isBlank()) {
            factions.add(faction);
          }
        }
      }
    }
    return new ArrayList<>(factions);
  }

  private List<RuleEntry> loadAvailableMonsterRules() throws Exception {
    List<RuleEntry> rules = new ArrayList<>();
    for (Path file : service.listRuleFiles()) {
      for (RuleEntry rule : service.loadRules(file)) {
        if (!"magic".equalsIgnoreCase(safe(rule.type))) {
          rules.add(rule);
        }
      }
    }
    rules.sort(
        Comparator.comparing((RuleEntry rule) -> safe(rule.name), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(rule -> safe(rule.id), String.CASE_INSENSITIVE_ORDER));
    return rules;
  }

  private List<RuleEntry> loadAvailableMonsterMagicRules() throws Exception {
    List<RuleEntry> rules = new ArrayList<>();
    for (Path file : service.listRuleFiles()) {
      for (RuleEntry rule : service.loadRules(file)) {
        if ("magic".equalsIgnoreCase(safe(rule.type))) {
          rules.add(rule);
        }
      }
    }
    rules.sort(
        Comparator.comparing((RuleEntry rule) -> safe(rule.name), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(rule -> safe(rule.id), String.CASE_INSENSITIVE_ORDER));
    return rules;
  }

  private static String[] monsterDamageOptions() {
    String[] values = new String[11];
    values[0] = "S";
    for (int i = 1; i <= 10; i++) {
      values[i] = i + "D6";
    }
    return values;
  }

  private static String[] monsterBallisticSkillOptions() {
    return new String[] {"-", "S", "A", "1+", "2+", "3+", "4+", "5+", "6+"};
  }

  private static void setComboValue(Combo combo, String value, String fallback) {
    String normalized = safe(value);
    for (String item : combo.getItems()) {
      if (item.equals(normalized)) {
        combo.setText(normalized);
        return;
      }
    }
    combo.setText(fallback);
  }

  private static List<String> splitCsv(String raw) {
    List<String> values = new ArrayList<>();
    for (String part : safe(raw).split(",")) {
      String trimmed = part.trim();
      if (!trimmed.isEmpty()) {
        values.add(trimmed);
      }
    }
    return values;
  }

  private static String formatRuleLinks(Map<String, MonsterSpecialRuleLink> ruleLinks) {
    List<String> lines = new ArrayList<>();
    for (Map.Entry<String, MonsterSpecialRuleLink> entry : ruleLinks.entrySet()) {
      List<String> parameters = entry.getValue().parameters();
      lines.add(
          buildSpecialRuleText(
                  safe(entry.getValue().name()), parameters, safe(entry.getValue().parameterFormat()))
              + " ("
              + entry.getKey()
              + ")"
              + (parameters.isEmpty() ? "" : " [" + String.join(" | ", parameters) + "]"));
    }
    return String.join(System.lineSeparator(), lines);
  }

  private static String buildSpecialRuleText(
      String name, List<String> parameters, String parameterFormat) {
    String normalizedName = safe(name).trim();
    String normalizedFormat = safe(parameterFormat).trim();
    if (parameters.stream().allMatch(value -> safe(value).trim().isBlank())) {
      return normalizedName;
    }
    if (!normalizedFormat.isBlank()) {
      String rendered = normalizedFormat.replace("{name}", normalizedName);
      rendered = rendered.replace("{param}", safe(parameters.isEmpty() ? "" : parameters.get(0)).trim());
      for (int i = 0; i < parameters.size(); i++) {
        rendered = rendered.replace("{" + i + "}", safe(parameters.get(i)).trim());
      }
      return rendered.trim();
    }
    return (normalizedName + " " + safe(parameters.isEmpty() ? "" : parameters.get(0)).trim()).trim();
  }

  private static List<String> inferSpecialRuleParameters(
      String ruleName, String storedText, String parameterFormat) {
    String normalizedRuleName = safe(ruleName).trim();
    String normalizedStoredText = safe(storedText).trim();
    String normalizedFormat = safe(parameterFormat).trim();
    if (normalizedRuleName.isBlank() || normalizedStoredText.isBlank()) {
      return List.of();
    }
    if (!normalizedFormat.isBlank()) {
      String source = normalizedFormat.replace("{name}", normalizedRuleName);
      java.util.regex.Pattern capturePattern =
          java.util.regex.Pattern.compile("(\\{(?:\\d+|param)\\})");
      String[] parts = capturePattern.split(source, -1);
      java.util.regex.Matcher matcher = capturePattern.matcher(source);
      List<String> placeholders = new ArrayList<>();
      while (matcher.find()) {
        placeholders.add(matcher.group(1));
      }
      if (!placeholders.isEmpty()) {
        StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < parts.length; i++) {
          regex.append(java.util.regex.Pattern.quote(parts[i]));
          if (i < placeholders.size()) {
            regex.append("(.*?)");
          }
        }
        regex.append("$");
        java.util.regex.Matcher valueMatcher =
            java.util.regex.Pattern.compile(regex.toString(), java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(normalizedStoredText);
        if (valueMatcher.matches()) {
          List<String> values = new ArrayList<>();
          for (int i = 0; i < placeholders.size(); i++) {
            int target =
                "{param}".equals(placeholders.get(i))
                    ? 0
                    : Integer.parseInt(placeholders.get(i).substring(1, placeholders.get(i).length() - 1));
            while (values.size() <= target) {
              values.add("");
            }
            values.set(target, safe(valueMatcher.group(i + 1)).trim());
          }
          return values;
        }
      }
    }
    if (!normalizedStoredText.regionMatches(true, 0, normalizedRuleName, 0, normalizedRuleName.length())) {
      return List.of();
    }
    return List.of(normalizedStoredText.substring(normalizedRuleName.length()).trim());
  }

  private static List<String> ruleParameterLabels(RuleEntry rule) {
    if (rule == null) {
      return List.of();
    }
    List<String> values = splitCsv(safe(rule.parameterNames));
    if (!values.isEmpty()) {
      return values;
    }
    if (!safe(rule.parameterName).isBlank()) {
      return List.of(safe(rule.parameterName));
    }
    return List.of();
  }

  private static String ruleDisplayLabel(RuleEntry rule) {
    return safe(rule.name) + " (" + safe(rule.id) + ")";
  }

  private static String selectedRuleId(Combo combo, List<RuleEntry> rules) {
    int index = combo.getSelectionIndex();
    if (index <= 0 || index - 1 >= rules.size()) {
      return "";
    }
    return safe(rules.get(index - 1).id);
  }

  private static void setRuleComboSelection(Combo combo, List<RuleEntry> rules, String id) {
    String normalized = safe(id);
    if (normalized.isBlank()) {
      combo.select(0);
      return;
    }
    for (int i = 0; i < rules.size(); i++) {
      if (normalized.equals(safe(rules.get(i).id))) {
        combo.select(i + 1);
        return;
      }
    }
    combo.select(0);
  }

  private static MonsterSpecialEditorState parseMonsterSpecialEditorState(String raw) {
    List<String> plainTextLines = new ArrayList<>();
    Map<String, MonsterSpecialRuleLink> ruleLinks = new LinkedHashMap<>();
    List<String> preservedLines = new ArrayList<>();
    String magicRuleId = "";
    int magicLevel = 0;

    for (String line : safe(raw).split("\\R")) {
      String trimmed = line.trim();
      if (trimmed.isEmpty()) {
        continue;
      }
      String[] parts = trimmed.split("\\|", -1);
      if (parts.length >= 2 && "text".equals(parts[0].trim())) {
        plainTextLines.add(parts[1].trim());
      } else if (parts.length >= 3 && "rule".equals(parts[0].trim())) {
        String parameter = parts.length >= 4 ? parts[3].trim() : "";
        ruleLinks.put(
            parts[1].trim(),
            new MonsterSpecialRuleLink(
                parts[2].trim(), parameter.isBlank() ? List.of() : List.of(parameter), ""));
      } else if (parts.length >= 3 && "magic".equals(parts[0].trim()) && magicRuleId.isBlank()) {
        magicRuleId = parts[1].trim();
        try {
          magicLevel = Integer.parseInt(parts[2].trim());
        } catch (NumberFormatException ignored) {
          magicLevel = 0;
        }
      } else {
        preservedLines.add(trimmed);
      }
    }

    return new MonsterSpecialEditorState(
        String.join(System.lineSeparator(), plainTextLines), ruleLinks, magicRuleId, magicLevel, preservedLines);
  }

  private static String buildMonsterSpecialEntriesRaw(
      String plainText,
      Map<String, MonsterSpecialRuleLink> ruleLinks,
      String magicRuleId,
      int magicLevel,
      List<String> preservedLines) {
    List<String> lines = new ArrayList<>();
    for (String line : safe(plainText).split("\\R")) {
      String trimmed = line.trim();
      if (!trimmed.isEmpty()) {
        lines.add("text|" + trimmed);
      }
    }
    for (Map.Entry<String, MonsterSpecialRuleLink> entry : ruleLinks.entrySet()) {
      List<String> parameters =
          entry.getValue().parameters().stream().map(EventContentEditorDialog::safe).map(String::trim).toList();
      String line =
          "rule|"
              + entry.getKey()
              + "|"
              + buildSpecialRuleText(
                  safe(entry.getValue().name()),
                  parameters,
                  safe(entry.getValue().parameterFormat()));
      if (parameters.size() == 1 && !safe(parameters.get(0)).isBlank()) {
        line += "|" + parameters.get(0);
      }
      lines.add(line);
    }
    if (!safe(magicRuleId).isBlank()) {
      lines.add("magic|" + magicRuleId.trim() + "|" + Math.max(0, magicLevel));
    }
    lines.addAll(preservedLines);
    return String.join(System.lineSeparator(), lines);
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

  private static String buildTreasureUsers(
      Button barbarianCheck, Button dwarfCheck, Button elfCheck, Button wizardCheck) {
    StringBuilder builder = new StringBuilder();
    if (barbarianCheck != null && barbarianCheck.getSelection()) {
      builder.append('B');
    }
    if (dwarfCheck != null && dwarfCheck.getSelection()) {
      builder.append('D');
    }
    if (elfCheck != null && elfCheck.getSelection()) {
      builder.append('E');
    }
    if (wizardCheck != null && wizardCheck.getSelection()) {
      builder.append('W');
    }
    return builder.toString();
  }

  private static void applyTreasureUsers(
      String users, Button barbarianCheck, Button dwarfCheck, Button elfCheck, Button wizardCheck) {
    String normalized = safe(users).replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    barbarianCheck.setSelection(normalized.indexOf('B') >= 0);
    dwarfCheck.setSelection(normalized.indexOf('D') >= 0);
    elfCheck.setSelection(normalized.indexOf('E') >= 0);
    wizardCheck.setSelection(normalized.indexOf('W') >= 0);
  }

  private static Event toPreviewTreasureEvent(
      String name, String flavor, String rules, String goldValue, String users, boolean objectiveTreasure) {
    Event event = new Event();
    event.id = objectiveTreasure ? "userdefined-treasure-objective-preview" : "userdefined-treasure-dungeon-preview";
    event.name = safe(name).trim();
    event.flavor = safe(flavor).trim();
    event.rules = safe(rules).trim();
    event.special = "";
    event.goldValue = safe(goldValue).trim();
    event.users = safe(users).trim();
    event.treasure = true;
    return event;
  }

  private static String buildEventLikeId(String name, boolean treasureFieldsVisible, boolean objectiveTreasurePreview) {
    String slug = slugify(name);
    if (!treasureFieldsVisible) {
      return slug;
    }
    return objectiveTreasurePreview
        ? "userdefined-treasure-objective-" + slug
        : "userdefined-treasure-dungeon-" + slug;
  }

  private String suggestedEventFileName(
      Path fileDirectory, boolean treasureFieldsVisible, boolean objectiveTreasurePreview) {
    Path normalizedDirectory = fileDirectory == null ? null : fileDirectory.toAbsolutePath().normalize();
    if (treasureFieldsVisible) {
      return objectiveTreasurePreview ? "userdefined-objective-treasure.xml" : "userdefined-treasure.xml";
    }
    if (normalizedDirectory != null
        && normalizedDirectory.equals(service.getTravelDirectory().toAbsolutePath().normalize())) {
      return "userdefined-travel.xml";
    }
    if (normalizedDirectory != null
        && normalizedDirectory.equals(service.getSettlementDirectory().toAbsolutePath().normalize())) {
      return "userdefined-settlement.xml";
    }
    return "userdefined-events.xml";
  }

  private static void layoutTreasurePreview(Composite viewport, Composite previewHost) {
    if (viewport == null || previewHost == null || viewport.isDisposed() || previewHost.isDisposed()) {
      return;
    }
    org.eclipse.swt.graphics.Rectangle area = viewport.getClientArea();
    if (area.width <= 0 || area.height <= 0) {
      return;
    }

    int width = area.width;
    int height = (int) Math.floor(width / TREASURE_CARD_ASPECT_RATIO);
    if (height > area.height) {
      height = area.height;
      width = (int) Math.floor(height * TREASURE_CARD_ASPECT_RATIO);
    }

    int x = area.x + Math.max(0, (area.width - width) / 2);
    int y = area.y + Math.max(0, (area.height - height) / 2);
    previewHost.setBounds(x, y, Math.max(1, width), Math.max(1, height));
  }

  private void createAndSelectXmlFile(
      Shell dialog,
      Combo fileCombo,
      List<Path> files,
      Path directory,
      String suggestedName,
      CheckedPathFunction<Path> fileCreator,
      CheckedSupplier<List<Path>> fileSupplier,
      Runnable loadFile) {
    try {
      FileDialog saveDialog = new FileDialog(dialog, SWT.SAVE);
      saveDialog.setText(I18n.t("editor.button.newFile"));
      saveDialog.setFilterExtensions(new String[] {"*.xml"});
      if (directory != null) {
        saveDialog.setFilterPath(directory.toString());
      }
      saveDialog.setFileName(suggestedName);

      String selected = saveDialog.open();
      if (selected == null || selected.isBlank()) {
        return;
      }

      Path createdFile = fileCreator.apply(Path.of(selected));
      refreshFileChoices(fileCombo, files, fileSupplier);
      selectFile(fileCombo, files, createdFile);
      loadFile.run();
      showInfo(dialog, I18n.t("editor.message.newFileCreated"));
    } catch (Exception ex) {
      showError(dialog, ex);
    }
  }

  private static void refreshFileChoices(Combo fileCombo, List<Path> files, CheckedSupplier<List<Path>> fileSupplier)
      throws Exception {
    files.clear();
    files.addAll(fileSupplier.get());
    fileCombo.removeAll();
    for (Path file : files) {
      fileCombo.add(file.getFileName().toString());
    }
  }

  private static void selectFile(Combo fileCombo, List<Path> files, Path target) {
    Path normalizedTarget = target == null ? null : target.toAbsolutePath().normalize();
    for (int i = 0; i < files.size(); i++) {
      Path candidate = files.get(i);
      if (candidate != null && candidate.toAbsolutePath().normalize().equals(normalizedTarget)) {
        fileCombo.select(i);
        return;
      }
    }
    if (!files.isEmpty()) {
      fileCombo.select(0);
    }
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

  private static void showError(Shell parent, Throwable throwable) {
    if (throwable != null) {
      throwable.printStackTrace();
    }
    showError(parent, throwable == null ? "" : safe(throwable.getMessage()));
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

    Button newFileButton = createActionButton(header, "editor.button.newFile");
    Button newButton = createActionButton(header, "editor.button.new");
    Button deleteButton = createActionButton(header, "editor.button.delete");
    Button saveButton = createActionButton(header, "editor.button.save");
    Button reloadButton = createActionButton(header, "editor.button.reload");
    Button validateButton = createActionButton(header, "editor.button.validate");

    return new EditorHeader(fileCombo, newFileButton, newButton, deleteButton, saveButton, reloadButton, validateButton);
  }

  private record EditorHeader(
      Combo fileCombo,
      Button newFileButton,
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

  @FunctionalInterface
  private interface CheckedPathFunction<T> {
    T apply(Path value) throws Exception;
  }

  private record MonsterSpecialEditorState(
      String plainText,
      Map<String, MonsterSpecialRuleLink> ruleLinks,
      String magicRuleId,
      int magicLevel,
      List<String> preservedLines) {}

  private record MonsterSpecialRuleLink(String name, List<String> parameters, String parameterFormat) {}
}
