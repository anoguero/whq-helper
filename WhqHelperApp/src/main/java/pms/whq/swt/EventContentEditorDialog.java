package pms.whq.swt;

import static pms.whq.swt.editor.EditorSupport.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
import pms.whq.swt.editor.EventLikeTabs;
import pms.whq.swt.editor.LocationsTab;
import pms.whq.swt.editor.MonstersTab;
import pms.whq.swt.editor.RulesTab;
import pms.whq.swt.editor.TablesTab;
import pms.whq.swt.editor.WarriorsTab;
import pms.whq.xml.XmlContentService;

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
    new RulesTab(context).create(tabs, dialog);
    new EventLikeTabs(context).create(tabs, dialog);
    new TablesTab(context).create(tabs, dialog);
    new MonstersTab(context).create(tabs, dialog);
    new WarriorsTab(context).create(tabs, dialog);
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

  private EditableContentTranslations loadEditableTranslations() {
    return EditableContentTranslations.load(projectRoot, I18n.getLanguage());
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

}
