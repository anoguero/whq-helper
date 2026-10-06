package pms.whq.swt.editor;

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
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.Text;

import com.whq.app.i18n.EditableContentTranslations;
import com.whq.app.i18n.I18n;

import pms.whq.swt.CardFactory;
import pms.whq.xml.XmlContentService.EventEntry;
import pms.whq.data.Event;

/**
 * Las cinco pestanas de eventos (eventos, tesoro de mazmorra, tesoro de sala objetivo, viaje y
 * asentamiento). Comparten createEventLikeTab y se crean juntas, en este orden.
 */
public final class EventLikeTabs extends EditorTab {

  public EventLikeTabs(EditorContext context) {
    super(context);
  }

  @Override
  public void create(TabFolder tabs, Shell dialog) {
    createEventsTab(tabs, dialog);
    createDungeonTreasureTab(tabs, dialog);
    createObjectiveTreasureTab(tabs, dialog);
    createTravelEventsTab(tabs, dialog);
    createSettlementEventsTab(tabs, dialog);
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

  @FunctionalInterface
  private interface CheckedConsumer<T> {
    void accept(T value) throws Exception;
  }
}
