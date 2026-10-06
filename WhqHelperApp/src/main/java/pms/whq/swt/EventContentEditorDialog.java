package pms.whq.swt;

import static pms.whq.swt.editor.EditorSupport.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;

import com.whq.app.AppPaths;
import com.whq.app.adventure.ObjectiveRoomAdventure;
import com.whq.app.adventure.ObjectiveRoomAdventureRepositoryException;
import com.whq.app.adventure.XmlObjectiveRoomAdventureRepository;
import com.whq.app.i18n.EditableContentTranslations;
import com.whq.app.i18n.I18n;
import com.whq.app.model.CardType;
import com.whq.app.model.DungeonCard;
import com.whq.app.model.WhiteDwarfRoomReferences;
import com.whq.app.render.CardRenderer;
import com.whq.app.storage.DungeonCardStorageException;
import com.whq.app.storage.XmlDungeonCardStore;
import com.whq.app.ui.AppIcon;

import pms.whq.swt.editor.EditorContext;
import pms.whq.swt.editor.LocationsTab;
import pms.whq.swt.editor.MonstersTab;
import pms.whq.swt.editor.TablesTab;
import pms.whq.xml.XmlContentService;
import pms.whq.xml.XmlContentService.EventEntry;
import pms.whq.xml.XmlContentService.RuleEntry;
import pms.whq.xml.XmlContentService.WarriorEntry;
import pms.whq.data.Event;

public final class EventContentEditorDialog {
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
    Shell dialog =
        new Shell(parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.RESIZE | SWT.MAX);
    AppIcon.inherit(dialog, parent);
    dialog.setText(I18n.t("dialog.contentEditor.title"));
    dialog.setLayout(new GridLayout(1, false));
    dialog.setSize(1440, 920);
    dialog.setMaximized(true);

    SashForm layout = new SashForm(dialog, SWT.HORIZONTAL);
    layout.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

    Tree navigationTree = new Tree(layout, SWT.BORDER | SWT.SINGLE);
    TabFolder tabs = new TabFolder(layout, SWT.NONE);
    layout.setWeights(new int[] {24, 76});
    EditorContext context =
        new EditorContext(
            parent,
            projectRoot,
            service,
            dungeonCardStore,
            objectiveRoomAdventureRepository,
            onContentSaved);

    createDungeonCardsTab(tabs, dialog);
    createRulesTab(tabs, dialog);
    createEventsTab(tabs, dialog);
    createDungeonTreasureTab(tabs, dialog);
    createObjectiveTreasureTab(tabs, dialog);
    createTravelEventsTab(tabs, dialog);
    createSettlementEventsTab(tabs, dialog);
    new TablesTab(context).create(tabs, dialog);
    new MonstersTab(context).create(tabs, dialog);
    createWarriorsTab(tabs, dialog);
    new LocationsTab(context).create(tabs, dialog);
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

    SashForm contentSash = new SashForm(sash, SWT.HORIZONTAL);
    Composite details = new Composite(contentSash, SWT.NONE);
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

    Group previewGroup = new Group(contentSash, SWT.NONE);
    previewGroup.setText(I18n.t("dashboard.preview.title"));
    previewGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    previewGroup.setLayout(new GridLayout(1, false));

    Button whiteDwarfReferenceButton = new Button(previewGroup, SWT.PUSH);
    whiteDwarfReferenceButton.setText(I18n.t("dialog.whiteDwarfReference.button"));
    whiteDwarfReferenceButton.setLayoutData(new GridData(SWT.BEGINNING, SWT.TOP, false, false));
    whiteDwarfReferenceButton.setEnabled(false);

    Canvas previewCanvas = new Canvas(previewGroup, SWT.DOUBLE_BUFFERED | SWT.BORDER);
    previewCanvas.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    contentSash.setWeights(new int[] {65, 35});

    CardRenderer renderer = new CardRenderer(dialog.getDisplay(), projectRoot);
    dialog.addDisposeListener(event -> renderer.dispose());
    RuleDialog whiteDwarfReferenceDialog = new RuleDialog(dialog);
    dialog.addDisposeListener(event -> whiteDwarfReferenceDialog.dispose());

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
          whiteDwarfReferenceButton.setEnabled(WhiteDwarfRoomReferences.find(draftCard[0]).isPresent());
          previewCanvas.redraw();
        };

    whiteDwarfReferenceButton.addListener(
        SWT.Selection,
        event -> {
          if (draftCard[0] == null) {
            return;
          }
          WhiteDwarfRoomReferences.find(draftCard[0]).ifPresent(reference -> whiteDwarfReferenceDialog.showContent(
              reference.title(com.whq.app.i18n.I18n.getLanguage()),
              reference.source() + "\n\n" + reference.text(com.whq.app.i18n.I18n.getLanguage())));
        });

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
          Path sharedHome = AppPaths.sharedHome(projectRoot);
          String storedPath;
          if (normalizedSelection.startsWith(sharedHome)) {
            storedPath = sharedHome.relativize(normalizedSelection).toString();
          } else if (normalizedSelection.startsWith(projectRoot)) {
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
            EditableContentTranslations translations = loadEditableTranslations();
            entries.clear();
            entries.addAll(applyRuleTranslations(service.loadRules(files.get(fileIndex)), translations));
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
            RuleEntry removedEntry = updatedEntries.remove(index);
            service.saveRules(selectedFile(fileCombo, files), updatedEntries);
            EditableContentTranslations translations = loadEditableTranslations();
            removeRuleTranslations(translations, removedEntry.id);
            translations.save();
            entries.clear();
            entries.addAll(applyRuleTranslations(updatedEntries, translations));
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
            EditableContentTranslations translations = loadEditableTranslations();
            putRuleTranslations(translations, entry);
            translations.save();
            entries.clear();
            entries.addAll(applyRuleTranslations(updatedEntries, translations));
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
    SashForm contentArea = new SashForm(sash, SWT.HORIZONTAL);
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

    Label specialLabel = new Label(details, SWT.NONE);
    specialLabel.setText(I18n.t("editor.events.label.special") + ":");
    StyledText specialText = new StyledText(details, SWT.BORDER | SWT.WRAP | SWT.V_SCROLL);
    GridData specialData = new GridData(SWT.FILL, SWT.FILL, true, true);
    specialData.heightHint = 120;
    specialLabel.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false));
    specialText.setLayoutData(specialData);
    specialLabel.setVisible(treasureCheckVisible);
    specialText.setVisible(treasureCheckVisible);
    ((GridData) specialLabel.getLayoutData()).exclude = !treasureCheckVisible;
    ((GridData) specialText.getLayoutData()).exclude = !treasureCheckVisible;

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

    Group previewGroup = new Group(contentArea, SWT.NONE);
    previewGroup.setText(I18n.t("dashboard.preview.title"));
    previewGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    previewGroup.setLayout(new FillLayout());
    Composite cardPreviewViewport = new Composite(previewGroup, SWT.NONE);
    cardPreviewViewport.setLayout(null);
    Composite cardPreviewHost = new Composite(cardPreviewViewport, SWT.NONE);
    cardPreviewHost.setLayout(new FillLayout());
    contentArea.setWeights(new int[] {65, 35});

    java.util.List<Path> files = new ArrayList<>();
    java.util.List<EventEntry> entries = new ArrayList<>();
    final int[] selectedIndex = new int[] {-1};
    final String[] selectedEntryId = new String[] {""};
    final Composite previewViewport = cardPreviewViewport;
    final Composite previewHost = cardPreviewHost;

    Runnable refreshId =
        () ->
            idText.setText(
                selectedEntryId[0].isBlank()
                    ? buildEventLikeId(nameText.getText(), treasureFieldsVisible, objectiveTreasurePreview)
                    : selectedEntryId[0]);

    Runnable refreshCardPreview =
        () -> {
          if (previewHost == null || previewHost.isDisposed()) {
            return;
          }
          for (org.eclipse.swt.widgets.Control child : previewHost.getChildren()) {
            child.dispose();
          }
          if (treasureFieldsVisible) {
            CardFactory.createTreasureCardPreview(
                previewHost,
                toPreviewTreasureEvent(
                    nameText.getText(),
                    flavorText.getText(),
                    rulesText.getText(),
                    goldValueText.getText(),
                    buildTreasureUsers(barbarianCheck, dwarfCheck, elfCheck, wizardCheck),
                    objectiveTreasurePreview));
          } else {
            Event previewEvent =
                toPreviewEventLike(
                    nameText.getText(),
                    flavorText.getText(),
                    rulesText.getText(),
                    specialText.getText(),
                    treasureCheckVisible && treasureCheck.getSelection(),
                    fileDirectory);
            CardFactory.createEventCardPreview(
                previewHost,
                previewEvent,
                previewEventBadge(fileDirectory),
                isTravelOrSettlementDirectory(fileDirectory));
          }
          layoutTreasurePreview(previewViewport, previewHost);
          previewHost.layout(true, true);
        };

    previewViewport.addControlListener(
        new ControlAdapter() {
          @Override
          public void controlResized(ControlEvent event) {
            layoutTreasurePreview(previewViewport, previewHost);
          }
        });

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
          specialText.setText("");
          goldValueText.setText("");
          barbarianCheck.setSelection(false);
          dwarfCheck.setSelection(false);
          elfCheck.setSelection(false);
          wizardCheck.setSelection(false);
          treasureCheck.setSelection(false);
          refreshId.run();
          refreshCardPreview.run();
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
          specialText.setText(safe(entry.special));
          goldValueText.setText(safe(entry.goldValue));
          applyTreasureUsers(entry.users, barbarianCheck, dwarfCheck, elfCheck, wizardCheck);
          treasureCheck.setSelection(entry.treasure);
          refreshId.run();
          refreshCardPreview.run();
        };

    Runnable loadFile =
        () -> {
          int fileIndex = fileCombo.getSelectionIndex();
          if (fileIndex < 0 || fileIndex >= files.size()) {
            return;
          }
          try {
            EditableContentTranslations translations = loadEditableTranslations();
            entries.clear();
            List<EventEntry> loadedEntries = service.loadEvents(files.get(fileIndex));
            entries.addAll(
                filterVisibleEvents(
                    files.get(fileIndex),
                    applyEventTranslations(loadedEntries, translations),
                    entryFilter,
                    treasureFieldsVisible,
                    objectiveTreasurePreview));
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
    nameText.addModifyListener(event -> refreshCardPreview.run());
    flavorText.addModifyListener(event -> refreshCardPreview.run());
    rulesText.addModifyListener(event -> refreshCardPreview.run());
    specialText.addModifyListener(event -> refreshCardPreview.run());
    goldValueText.addModifyListener(event -> refreshCardPreview.run());
    barbarianCheck.addListener(SWT.Selection, event -> refreshCardPreview.run());
    dwarfCheck.addListener(SWT.Selection, event -> refreshCardPreview.run());
    elfCheck.addListener(SWT.Selection, event -> refreshCardPreview.run());
    wizardCheck.addListener(SWT.Selection, event -> refreshCardPreview.run());
    treasureCheck.addListener(SWT.Selection, event -> refreshCardPreview.run());

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
            EventEntry removedEntry = updatedEntries.remove(index);
            List<EventEntry> entriesToSave = mergeVisibleEvents(file, updatedEntries, entryFilter);
            if (entriesToSave.isEmpty()) {
              showWarning(dialog, I18n.t("editor.message.lastEntry"));
              return;
            }
            service.saveEvents(file, entriesToSave);
            EditableContentTranslations translations = loadEditableTranslations();
            removeEventTranslations(translations, removedEntry.id);
            translations.save();
            entries.clear();
            entries.addAll(applyEventTranslations(updatedEntries, translations));
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
          entry.special = treasureCheckVisible ? specialText.getText().trim() : "";
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
            EditableContentTranslations translations = loadEditableTranslations();
            putEventTranslations(translations, entry);
            translations.save();
            entries.clear();
            entries.addAll(applyEventTranslations(updatedEntries, translations));
            refreshList.run();
            selectById(itemList, entries, entry.id);
            refreshCardPreview.run();
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

    refreshCardPreview.run();
  }

  private void createWarriorsTab(TabFolder tabs, Shell dialog) {
    TabItem tab = new TabItem(tabs, SWT.NONE);
    tab.setText(I18n.t("dialog.contentEditor.tab.warriors"));

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
    attributes.setText(I18n.t("editor.warriors.group.attributes"));
    attributes.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
    attributes.setLayout(new GridLayout(2, false));

    Text idText = createLabeledText(attributes, I18n.t("editor.warriors.label.id") + ":");
    idText.setEditable(false);
    Text nameText = createLabeledText(attributes, I18n.t("editor.warriors.label.name") + ":");
    Text raceText = createLabeledText(attributes, I18n.t("editor.warriors.label.race") + ":");
    Text counterText = createLabeledText(attributes, I18n.t("editor.warriors.label.counter") + ":");

    Group contentGroup = new Group(details, SWT.NONE);
    contentGroup.setText(I18n.t("editor.warriors.group.rules"));
    contentGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    contentGroup.setLayout(new GridLayout(2, false));

    new Label(contentGroup, SWT.NONE).setText(I18n.t("editor.warriors.label.rules") + ":");
    StyledText rulesText = new StyledText(contentGroup, SWT.BORDER | SWT.WRAP | SWT.V_SCROLL);
    GridData rulesData = new GridData(SWT.FILL, SWT.FILL, true, true);
    rulesData.heightHint = 180;
    rulesText.setLayoutData(rulesData);

    java.util.List<Path> files = new ArrayList<>();
    java.util.List<WarriorEntry> entries = new ArrayList<>();
    final int[] selectedIndex = new int[] {-1};
    final String[] selectedId = new String[] {""};

    Runnable refreshList = () -> {
      itemList.removeAll();
      for (WarriorEntry entry : entries) {
        itemList.add(safe(entry.id) + " - " + safe(entry.name));
      }
    };

    Runnable refreshId =
        () -> idText.setText(selectedId[0].isBlank() ? "warrior-" + slugify(nameText.getText()) : selectedId[0]);

    Runnable clearForm =
        () -> {
          selectedIndex[0] = -1;
          selectedId[0] = "";
          itemList.deselectAll();
          nameText.setText("");
          raceText.setText("");
          counterText.setText("");
          rulesText.setText("");
          refreshId.run();
        };

    Runnable loadSelectedToForm =
        () -> {
          int index = itemList.getSelectionIndex();
          if (index < 0 || index >= entries.size()) {
            return;
          }
          selectedIndex[0] = index;
          WarriorEntry entry = entries.get(index);
          selectedId[0] = safe(entry.id);
          nameText.setText(safe(entry.name));
          raceText.setText(safe(entry.race));
          counterText.setText(safe(entry.counter));
          rulesText.setText(safe(entry.rules));
          refreshId.run();
        };

    Runnable loadFile =
        () -> {
          int fileIndex = fileCombo.getSelectionIndex();
          if (fileIndex < 0 || fileIndex >= files.size()) {
            return;
          }
          try {
            EditableContentTranslations translations = loadEditableTranslations();
            entries.clear();
            entries.addAll(applyWarriorTranslations(service.loadWarriors(files.get(fileIndex)), translations));
            refreshList.run();
            clearForm.run();
          } catch (Exception ex) {
            showError(dialog, ex);
          }
        };

    try {
      files.addAll(service.listWarriorFiles());
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
                service.getWarriorsDirectory(),
                "userdefined-warriors.xml",
                service::createEmptyWarriorsFile,
                service::listWarriorFiles,
                loadFile));
    itemList.addListener(SWT.Selection, event -> loadSelectedToForm.run());
    newButton.addListener(SWT.Selection, event -> clearForm.run());
    nameText.addModifyListener(event -> refreshId.run());

    saveButton.addListener(
        SWT.Selection,
        event -> {
          try {
            if (!hasSelectedFile(fileCombo, files, dialog)) {
              return;
            }
            WarriorEntry entry = new WarriorEntry();
            entry.id = selectedId[0].isBlank() ? "warrior-" + slugify(nameText.getText()) : selectedId[0];
            entry.name = nameText.getText().trim();
            entry.race = raceText.getText().trim();
            entry.counter = counterText.getText().trim();
            entry.rules = rulesText.getText().trim();

            ensureUniqueId(entries, selectedIndex[0], entry.id);
            EditableContentTranslations translations = loadEditableTranslations();
            if (selectedIndex[0] >= 0) {
              entries.set(selectedIndex[0], entry);
            } else {
              entries.add(entry);
            }
            putWarriorTranslations(translations, entry);
            service.saveWarriors(selectedFile(fileCombo, files), entries);
            translations.save();
            refreshList.run();
            selectById(itemList, entries, entry.id);
            loadSelectedToForm.run();
            notifySaved();
            showInfo(dialog, I18n.t("editor.message.saved"));
          } catch (Exception ex) {
            showError(dialog, ex);
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
            EditableContentTranslations translations = loadEditableTranslations();
            removeWarriorTranslations(translations, entries.get(index).id);
            entries.remove(index);
            service.saveWarriors(selectedFile(fileCombo, files), entries);
            translations.save();
            refreshList.run();
            clearForm.run();
            notifySaved();
          } catch (Exception ex) {
            showError(dialog, ex);
          }
        });

    reloadButton.addListener(SWT.Selection, event -> loadFile.run());
    validateButton.addListener(
        SWT.Selection,
        event -> {
          try {
            if (!hasSelectedFile(fileCombo, files, dialog)) {
              return;
            }
            service.validateWarriorsFile(selectedFile(fileCombo, files));
            showInfo(dialog, I18n.t("editor.message.validated"));
          } catch (Exception ex) {
            showError(dialog, ex);
          }
        });

    refreshId.run();
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

  private boolean isDungeonTreasureEntry(EventEntry entry) {
    String normalizedId = safe(entry == null ? "" : entry.id).trim().toLowerCase();
    return entry != null && entry.treasure && !normalizedId.contains("-objective-");
  }

  private boolean isObjectiveTreasureEntry(EventEntry entry) {
    return isTreasureEntry(entry, "objective");
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

  private Event toPreviewEventLike(
      String name, String flavor, String rules, String special, boolean treasure, Path fileDirectory) {
    Event event = new Event();
    event.id = "userdefined-event-preview";
    event.name = safe(name).trim();
    event.flavor = isTravelOrSettlementDirectory(fileDirectory) ? null : safe(flavor).trim();
    event.rules = safe(rules).trim();
    event.special = isTravelOrSettlementDirectory(fileDirectory) ? null : safe(special).trim();
    event.goldValue = "";
    event.users = "";
    event.treasure = treasure;
    return event;
  }

  private boolean isTravelOrSettlementDirectory(Path fileDirectory) {
    Path normalizedDirectory = fileDirectory == null ? null : fileDirectory.toAbsolutePath().normalize();
    return normalizedDirectory != null
        && (normalizedDirectory.equals(service.getTravelDirectory().toAbsolutePath().normalize())
            || normalizedDirectory.equals(service.getSettlementDirectory().toAbsolutePath().normalize()));
  }

  private String previewEventBadge(Path fileDirectory) {
    Path normalizedDirectory = fileDirectory == null ? null : fileDirectory.toAbsolutePath().normalize();
    if (normalizedDirectory != null
        && normalizedDirectory.equals(service.getSettlementDirectory().toAbsolutePath().normalize())) {
      return "SE";
    }
    if (normalizedDirectory != null
        && normalizedDirectory.equals(service.getTravelDirectory().toAbsolutePath().normalize())) {
      return "TR";
    }
    return "EV";
  }

  private EditableContentTranslations loadEditableTranslations() {
    return EditableContentTranslations.load(projectRoot, I18n.getLanguage());
  }

  private static List<RuleEntry> applyRuleTranslations(
      List<RuleEntry> entries, EditableContentTranslations translations) {
    List<RuleEntry> localized = new ArrayList<>();
    for (RuleEntry entry : entries) {
      localized.add(applyRuleTranslation(entry, translations));
    }
    return localized;
  }

  private static void putRuleTranslations(EditableContentTranslations translations, RuleEntry entry) {
    translations.put(ruleTranslationKey(entry.id, RULE_NAME_SUFFIX), entry.name);
    translations.put(ruleTranslationKey(entry.id, RULE_TEXT_SUFFIX), entry.text);
    translations.put(ruleTranslationKey(entry.id, RULE_PARAMETER_NAME_SUFFIX), entry.parameterName);
    translations.put(ruleTranslationKey(entry.id, RULE_PARAMETER_NAMES_SUFFIX), entry.parameterNames);
    translations.put(ruleTranslationKey(entry.id, RULE_PARAMETER_FORMAT_SUFFIX), entry.parameterFormat);
  }

  private static void removeRuleTranslations(EditableContentTranslations translations, String ruleId) {
    translations.remove(ruleTranslationKey(ruleId, RULE_NAME_SUFFIX));
    translations.remove(ruleTranslationKey(ruleId, RULE_TEXT_SUFFIX));
    translations.remove(ruleTranslationKey(ruleId, RULE_PARAMETER_NAME_SUFFIX));
    translations.remove(ruleTranslationKey(ruleId, RULE_PARAMETER_NAMES_SUFFIX));
    translations.remove(ruleTranslationKey(ruleId, RULE_PARAMETER_FORMAT_SUFFIX));
  }

  private static void putEventTranslations(EditableContentTranslations translations, EventEntry entry) {
    translations.put(eventTranslationKey(entry.id, EVENT_NAME_SUFFIX), entry.name);
    translations.put(eventTranslationKey(entry.id, EVENT_FLAVOR_SUFFIX), entry.flavor);
    translations.put(eventTranslationKey(entry.id, EVENT_RULES_SUFFIX), entry.rules);
    translations.put(eventTranslationKey(entry.id, EVENT_SPECIAL_SUFFIX), entry.special);
  }

  private static void removeEventTranslations(EditableContentTranslations translations, String eventId) {
    translations.remove(eventTranslationKey(eventId, EVENT_NAME_SUFFIX));
    translations.remove(eventTranslationKey(eventId, EVENT_FLAVOR_SUFFIX));
    translations.remove(eventTranslationKey(eventId, EVENT_RULES_SUFFIX));
    translations.remove(eventTranslationKey(eventId, EVENT_SPECIAL_SUFFIX));
  }

  private static void putWarriorTranslations(EditableContentTranslations translations, WarriorEntry entry) {
    translations.put(warriorTranslationKey(entry.id, WARRIOR_NAME_SUFFIX), entry.name);
    translations.put(warriorTranslationKey(entry.id, WARRIOR_RACE_SUFFIX), entry.race);
    translations.put(warriorTranslationKey(entry.id, WARRIOR_RULES_SUFFIX), entry.rules);
  }

  private static void removeWarriorTranslations(EditableContentTranslations translations, String warriorId) {
    translations.remove(warriorTranslationKey(warriorId, WARRIOR_NAME_SUFFIX));
    translations.remove(warriorTranslationKey(warriorId, WARRIOR_RACE_SUFFIX));
    translations.remove(warriorTranslationKey(warriorId, WARRIOR_RULES_SUFFIX));
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

  private void notifySaved() {
    if (onContentSaved != null) {
      onContentSaved.run();
    }
  }

  private Composite createActionRow(Composite parent, int columns) {
    Composite actions = new Composite(parent, SWT.NONE);
    actions.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false));
    GridLayout layout = new GridLayout(columns, false);
    layout.marginWidth = 0;
    actions.setLayout(layout);
    return actions;
  }

  @FunctionalInterface
  private interface CheckedConsumer<T> {
    void accept(T value) throws Exception;
  }

}
