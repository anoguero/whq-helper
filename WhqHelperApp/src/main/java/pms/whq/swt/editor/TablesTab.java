package pms.whq.swt.editor;

import static pms.whq.swt.editor.EditorSupport.*;

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
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.Text;

import com.whq.app.i18n.EditableContentTranslations;
import com.whq.app.i18n.I18n;

import pms.whq.xml.XmlContentService.EventEntry;
import pms.whq.xml.XmlContentService.MonsterEntry;
import pms.whq.xml.XmlContentService.TableDefinition;
import pms.whq.xml.XmlContentService.TableEntry;
import pms.whq.xml.XmlContentService.TableFileModel;
import pms.whq.xml.XmlContentService.TableGroupMember;

/** Pestana de tablas de monstruos, eventos y tesoros. */
public final class TablesTab extends EditorTab {

  public TablesTab(EditorContext context) {
    super(context);
  }

  @Override
  public void create(TabFolder tabs, Shell dialog) {
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

    Group editorModeGroup = new Group(details, SWT.NONE);
    editorModeGroup.setText(I18n.t("editor.tables.group.editor"));
    editorModeGroup.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
    editorModeGroup.setLayout(new GridLayout(2, false));

    new Label(editorModeGroup, SWT.NONE).setText(I18n.t("editor.tables.label.editorType"));
    Combo editorModeCombo = new Combo(editorModeGroup, SWT.DROP_DOWN | SWT.READ_ONLY);
    editorModeCombo.setItems(
        new String[] {
          I18n.t("editor.tables.editor.monsters"),
          I18n.t("editor.tables.editor.events"),
          I18n.t("editor.tables.editor.treasures")
        });
    editorModeCombo.select(1);
    editorModeCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

    Composite editorStack = new Composite(details, SWT.NONE);
    editorStack.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    StackLayout editorStackLayout = new StackLayout();
    editorStackLayout.marginHeight = 0;
    editorStackLayout.marginWidth = 0;
    editorStack.setLayout(editorStackLayout);

    Composite monstersEditor = new Composite(editorStack, SWT.NONE);
    monstersEditor.setLayout(new GridLayout(1, false));

    Group monsterTableGroup = new Group(monstersEditor, SWT.NONE);
    monsterTableGroup.setText(I18n.t("editor.tables.group.table"));
    monsterTableGroup.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
    monsterTableGroup.setLayout(new GridLayout(2, false));

    Text monsterTableNameText =
        createLabeledText(monsterTableGroup, I18n.t("editor.tables.label.name") + ":");

    Group monsterEntryGroup = new Group(monstersEditor, SWT.NONE);
    monsterEntryGroup.setText(I18n.t("editor.tables.group.monsterEntry"));
    monsterEntryGroup.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
    monsterEntryGroup.setLayout(new GridLayout(2, false));

    new Label(monsterEntryGroup, SWT.NONE).setText(I18n.t("editor.tables.label.monster"));
    Combo availableMonsterCombo = new Combo(monsterEntryGroup, SWT.DROP_DOWN | SWT.READ_ONLY);
    availableMonsterCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

    Text minNumberText =
        createLabeledText(monsterEntryGroup, I18n.t("editor.tables.label.minNumber") + ":");
    Text maxNumberText =
        createLabeledText(monsterEntryGroup, I18n.t("editor.tables.label.maxNumber") + ":");

    Text monsterSpecialText =
        createLabeledText(monsterEntryGroup, I18n.t("editor.tables.label.specialText") + ":");
    GridData monsterSpecialTextData = new GridData(SWT.FILL, SWT.CENTER, true, false);
    monsterSpecialTextData.horizontalSpan = 2;
    monsterSpecialText.setLayoutData(monsterSpecialTextData);

    Composite monsterButtonsRow = new Composite(monsterEntryGroup, SWT.NONE);
    monsterButtonsRow.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 2, 1));
    GridLayout monsterButtonsLayout = new GridLayout(2, false);
    monsterButtonsLayout.marginWidth = 0;
    monsterButtonsLayout.marginHeight = 0;
    monsterButtonsRow.setLayout(monsterButtonsLayout);
    Button addMonsterButton = new Button(monsterButtonsRow, SWT.PUSH);
    addMonsterButton.setText(I18n.t("editor.tables.button.addMonster"));
    Button removeMonsterButton = new Button(monsterButtonsRow, SWT.PUSH);
    removeMonsterButton.setText(I18n.t("editor.tables.button.removeMonster"));

    Group encounterMonstersGroup = new Group(monstersEditor, SWT.NONE);
    encounterMonstersGroup.setText(I18n.t("editor.tables.group.encounterMonsters"));
    encounterMonstersGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    encounterMonstersGroup.setLayout(new GridLayout(1, false));

    org.eclipse.swt.widgets.List encounterMonsterList =
        new org.eclipse.swt.widgets.List(encounterMonstersGroup, SWT.BORDER | SWT.SINGLE | SWT.V_SCROLL);
    encounterMonsterList.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

    Composite encounterLevelRow = new Composite(monstersEditor, SWT.NONE);
    encounterLevelRow.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    GridLayout encounterLevelLayout = new GridLayout(2, false);
    encounterLevelLayout.marginWidth = 0;
    encounterLevelLayout.marginHeight = 0;
    encounterLevelRow.setLayout(encounterLevelLayout);
    new Label(encounterLevelRow, SWT.NONE).setText(I18n.t("editor.tables.label.encounterLevel"));
    Combo encounterLevelCombo = new Combo(encounterLevelRow, SWT.DROP_DOWN | SWT.READ_ONLY);
    encounterLevelCombo.setItems(new String[] {"1", "2", "3", "4", "5", "6", "7", "8", "9", "10"});
    encounterLevelCombo.select(0);

    Composite encounterButtonsRow = new Composite(monstersEditor, SWT.NONE);
    encounterButtonsRow.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
    GridLayout encounterButtonsLayout = new GridLayout(2, false);
    encounterButtonsLayout.marginWidth = 0;
    encounterButtonsLayout.marginHeight = 0;
    encounterButtonsRow.setLayout(encounterButtonsLayout);
    Button addEncounterButton = new Button(encounterButtonsRow, SWT.PUSH);
    addEncounterButton.setText(I18n.t("editor.tables.button.addEncounter"));
    Button removeEncounterButton = new Button(encounterButtonsRow, SWT.PUSH);
    removeEncounterButton.setText(I18n.t("editor.tables.button.removeEncounter"));

    Group encountersGroup = new Group(monstersEditor, SWT.NONE);
    encountersGroup.setText(I18n.t("editor.tables.group.encounters"));
    encountersGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    encountersGroup.setLayout(new GridLayout(1, false));

    org.eclipse.swt.widgets.List encounterList =
        new org.eclipse.swt.widgets.List(encountersGroup, SWT.BORDER | SWT.SINGLE | SWT.V_SCROLL);
    encounterList.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

    Composite eventsEditor = new Composite(editorStack, SWT.NONE);
    eventsEditor.setLayout(new GridLayout(1, false));

    Group eventTableGroup = new Group(eventsEditor, SWT.NONE);
    eventTableGroup.setText(I18n.t("editor.tables.group.table"));
    eventTableGroup.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
    eventTableGroup.setLayout(new GridLayout(2, false));

    Text eventTableNameText = createLabeledText(eventTableGroup, I18n.t("editor.tables.label.name") + ":");
    new Label(eventTableGroup, SWT.NONE).setText(I18n.t("editor.tables.label.subtype"));
    Combo eventSubtypeCombo = new Combo(eventTableGroup, SWT.DROP_DOWN | SWT.READ_ONLY);
    eventSubtypeCombo.setItems(
        new String[] {
          I18n.t("editor.tables.subtype.events.dungeon"),
          I18n.t("editor.tables.subtype.events.travel"),
          I18n.t("editor.tables.subtype.events.settlement")
        });
    eventSubtypeCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

    Group eventEntriesGroup = new Group(eventsEditor, SWT.NONE);
    eventEntriesGroup.setText(I18n.t("editor.tables.group.entries"));
    eventEntriesGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    eventEntriesGroup.setLayout(new GridLayout(1, false));

    Button eventOnlyUnassignedCheck = new Button(eventEntriesGroup, SWT.CHECK);
    eventOnlyUnassignedCheck.setText(I18n.t("editor.tables.label.onlyUnassigned"));

    Composite eventPickerRow = new Composite(eventEntriesGroup, SWT.NONE);
    eventPickerRow.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    GridLayout eventPickerLayout = new GridLayout(3, false);
    eventPickerLayout.marginWidth = 0;
    eventPickerLayout.marginHeight = 0;
    eventPickerRow.setLayout(eventPickerLayout);
    Combo availableEventCombo = new Combo(eventPickerRow, SWT.DROP_DOWN | SWT.READ_ONLY);
    availableEventCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    Button addEventButton = new Button(eventPickerRow, SWT.PUSH);
    addEventButton.setText("+");
    Button removeEventButton = new Button(eventPickerRow, SWT.PUSH);
    removeEventButton.setText("-");

    org.eclipse.swt.widgets.List eventEntryList =
        new org.eclipse.swt.widgets.List(eventEntriesGroup, SWT.BORDER | SWT.SINGLE | SWT.V_SCROLL);
    eventEntryList.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

    Composite treasuresEditor = new Composite(editorStack, SWT.NONE);
    treasuresEditor.setLayout(new GridLayout(1, false));

    Group treasureTableGroup = new Group(treasuresEditor, SWT.NONE);
    treasureTableGroup.setText(I18n.t("editor.tables.group.table"));
    treasureTableGroup.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
    treasureTableGroup.setLayout(new GridLayout(2, false));

    Text treasureTableNameText =
        createLabeledText(treasureTableGroup, I18n.t("editor.tables.label.name") + ":");
    new Label(treasureTableGroup, SWT.NONE).setText(I18n.t("editor.tables.label.subtype"));
    Combo treasureSubtypeCombo = new Combo(treasureTableGroup, SWT.DROP_DOWN | SWT.READ_ONLY);
    treasureSubtypeCombo.setItems(
        new String[] {
          I18n.t("editor.tables.subtype.treasures.dungeon"),
          I18n.t("editor.tables.subtype.treasures.objective")
        });
    treasureSubtypeCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

    Group treasureEntriesGroup = new Group(treasuresEditor, SWT.NONE);
    treasureEntriesGroup.setText(I18n.t("editor.tables.group.entries"));
    treasureEntriesGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    treasureEntriesGroup.setLayout(new GridLayout(1, false));

    Button treasureOnlyUnassignedCheck = new Button(treasureEntriesGroup, SWT.CHECK);
    treasureOnlyUnassignedCheck.setText(I18n.t("editor.tables.label.onlyUnassigned"));

    Composite treasurePickerRow = new Composite(treasureEntriesGroup, SWT.NONE);
    treasurePickerRow.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    GridLayout treasurePickerLayout = new GridLayout(3, false);
    treasurePickerLayout.marginWidth = 0;
    treasurePickerLayout.marginHeight = 0;
    treasurePickerRow.setLayout(treasurePickerLayout);
    Combo availableTreasureCombo = new Combo(treasurePickerRow, SWT.DROP_DOWN | SWT.READ_ONLY);
    availableTreasureCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    Button addTreasureButton = new Button(treasurePickerRow, SWT.PUSH);
    addTreasureButton.setText("+");
    Button removeTreasureButton = new Button(treasurePickerRow, SWT.PUSH);
    removeTreasureButton.setText("-");

    org.eclipse.swt.widgets.List treasureEntryList =
        new org.eclipse.swt.widgets.List(treasureEntriesGroup, SWT.BORDER | SWT.SINGLE | SWT.V_SCROLL);
    treasureEntryList.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

    java.util.List<Path> files = new ArrayList<>();
    final TableFileModel[] model = new TableFileModel[] {new TableFileModel()};
    final int[] selectedTableModelIndex = new int[] {-1};
    final TableDefinition[] draftTable = new TableDefinition[] {emptyTableDefinition()};
    final java.util.List<Integer> visibleTableIndices = new ArrayList<>();
    final java.util.List<String> visibleEventOptionIds = new ArrayList<>();
    final java.util.List<String> visibleTreasureOptionIds = new ArrayList<>();
    final java.util.List<String> visibleMonsterOptionIds = new ArrayList<>();
    final java.util.List<TableEntry> draftEncounterMembers = new ArrayList<>();
    final boolean[] syncing = new boolean[] {false};

    Runnable showCurrentEditor =
        () -> {
          String mode = currentTableEditorMode(editorModeCombo);
          if ("monsters".equals(mode)) {
            editorStackLayout.topControl = monstersEditor;
          } else if ("treasures".equals(mode)) {
            editorStackLayout.topControl = treasuresEditor;
          } else {
            editorStackLayout.topControl = eventsEditor;
          }
          editorStack.layout(true, true);
        };

    Runnable refreshTableList =
        () -> {
          String mode = currentTableEditorMode(editorModeCombo);
          tableList.removeAll();
          visibleTableIndices.clear();
          for (int i = 0; i < model[0].tables.size(); i++) {
            TableDefinition table = model[0].tables.get(i);
            if (tableMatchesEditorMode(table, mode)) {
              visibleTableIndices.add(i);
              tableList.add(tableLabel(table));
            }
          }
        };

    Runnable refreshAvailableMonsters =
        () -> {
          availableMonsterCombo.removeAll();
          visibleMonsterOptionIds.clear();
          Map<String, MonsterEntry> labels = availableTableMonsterEntries();
          for (Map.Entry<String, MonsterEntry> entry : labels.entrySet()) {
            visibleMonsterOptionIds.add(entry.getKey());
            availableMonsterCombo.add(monsterChoiceLabel(entry.getKey(), entry.getValue()));
          }
          if (availableMonsterCombo.getItemCount() > 0) {
            availableMonsterCombo.select(0);
          }
        };

    Runnable refreshEncounterMonsterList =
        () -> {
          encounterMonsterList.removeAll();
          Map<String, String> monsterLabels = availableTableMonsterLabels();
          for (TableEntry entry : draftEncounterMembers) {
            encounterMonsterList.add(tableMonsterMemberLabel(entry, monsterLabels));
          }
        };

    Runnable refreshEncounterList =
        () -> {
          encounterList.removeAll();
          Map<String, String> monsterLabels = availableTableMonsterLabels();
          for (TableEntry entry : tableMonsterEntries(draftTable[0])) {
            encounterList.add(tableMonsterEncounterLabel(entry, monsterLabels));
          }
        };

    Runnable refreshEventEntryList =
        () -> {
          eventEntryList.removeAll();
          Map<String, String> labels = availableTableEventLabels(eventSubtypeValue(eventSubtypeCombo), false);
          for (TableEntry entry : tableEventEntries(draftTable[0])) {
            eventEntryList.add(labels.getOrDefault(entry.id, entry.id));
          }
        };

    Runnable refreshTreasureEntryList =
        () -> {
          treasureEntryList.removeAll();
          Map<String, String> labels =
              availableTableEventLabels(treasureSubtypeValue(treasureSubtypeCombo), true);
          for (TableEntry entry : tableEventEntries(draftTable[0])) {
            treasureEntryList.add(labels.getOrDefault(entry.id, entry.id));
          }
        };

    Runnable refreshAvailableEvents =
        () -> {
          availableEventCombo.removeAll();
          visibleEventOptionIds.clear();
          String subtype = eventSubtypeValue(eventSubtypeCombo);
          if (subtype.isBlank()) {
            return;
          }
          boolean onlyUnassigned = eventOnlyUnassignedCheck.getSelection();
          Set<String> assignedIds =
              onlyUnassigned
                  ? assignedEventIdsForSubtype(
                      selectedFile(fileCombo, files), model[0], "events", subtype, selectedTableModelIndex[0])
                  : Set.of();
          Map<String, String> labels = availableTableEventLabels(subtype, false);
          for (Map.Entry<String, String> entry : labels.entrySet()) {
            if (onlyUnassigned && assignedIds.contains(entry.getKey())) {
              continue;
            }
            visibleEventOptionIds.add(entry.getKey());
            availableEventCombo.add(entry.getValue());
          }
          if (availableEventCombo.getItemCount() > 0) {
            availableEventCombo.select(0);
          }
        };

    Runnable refreshAvailableTreasures =
        () -> {
          availableTreasureCombo.removeAll();
          visibleTreasureOptionIds.clear();
          String subtype = treasureSubtypeValue(treasureSubtypeCombo);
          if (subtype.isBlank()) {
            return;
          }
          boolean onlyUnassigned = treasureOnlyUnassignedCheck.getSelection();
          Set<String> assignedIds =
              onlyUnassigned
                  ? assignedEventIdsForSubtype(
                      selectedFile(fileCombo, files), model[0], "treasures", subtype, selectedTableModelIndex[0])
                  : Set.of();
          Map<String, String> labels = availableTableEventLabels(subtype, true);
          for (Map.Entry<String, String> entry : labels.entrySet()) {
            if (onlyUnassigned && assignedIds.contains(entry.getKey())) {
              continue;
            }
            visibleTreasureOptionIds.add(entry.getKey());
            availableTreasureCombo.add(entry.getValue());
          }
          if (availableTreasureCombo.getItemCount() > 0) {
            availableTreasureCombo.select(0);
          }
        };

    Runnable clearDraft =
        () -> {
          syncing[0] = true;
          try {
            selectedTableModelIndex[0] = -1;
            draftTable[0] = emptyTableDefinition();
            draftEncounterMembers.clear();
            tableList.deselectAll();
            monsterTableNameText.setText("");
            availableMonsterCombo.removeAll();
            minNumberText.setText("");
            maxNumberText.setText("");
            monsterSpecialText.setText("");
            encounterLevelCombo.select(0);
            encounterMonsterList.removeAll();
            encounterList.removeAll();
            eventTableNameText.setText("");
            eventSubtypeCombo.deselectAll();
            eventOnlyUnassignedCheck.setSelection(false);
            eventEntryList.removeAll();
            availableEventCombo.removeAll();
            treasureTableNameText.setText("");
            treasureSubtypeCombo.deselectAll();
            treasureOnlyUnassignedCheck.setSelection(false);
            treasureEntryList.removeAll();
            availableTreasureCombo.removeAll();
          } finally {
            syncing[0] = false;
          }
          refreshAvailableMonsters.run();
          refreshEncounterMonsterList.run();
          refreshEncounterList.run();
          refreshAvailableEvents.run();
          refreshAvailableTreasures.run();
        };

    Runnable loadSelectedTable =
        () -> {
          int visibleIndex = tableList.getSelectionIndex();
          if (visibleIndex < 0 || visibleIndex >= visibleTableIndices.size()) {
            return;
          }
          int modelIndex = visibleTableIndices.get(visibleIndex);
          TableDefinition source = model[0].tables.get(modelIndex);
          selectedTableModelIndex[0] = modelIndex;
          draftTable[0] = copyTableDefinition(source);
          syncing[0] = true;
          try {
            String mode = currentTableEditorMode(editorModeCombo);
            if ("monsters".equals(mode)) {
              monsterTableNameText.setText(safe(source.name));
              draftEncounterMembers.clear();
              minNumberText.setText("");
              maxNumberText.setText("");
              monsterSpecialText.setText("");
              encounterLevelCombo.setText(safe(firstMonsterEncounterLevel(source)));
              refreshEncounterMonsterList.run();
              refreshEncounterList.run();
            } else if ("events".equals(mode)) {
              eventTableNameText.setText(safe(source.name));
              setTableSubtypeSelection(eventSubtypeCombo, eventSubtypeForTable(source, false));
              refreshEventEntryList.run();
            } else if ("treasures".equals(mode)) {
              treasureTableNameText.setText(safe(source.name));
              setTableSubtypeSelection(treasureSubtypeCombo, eventSubtypeForTable(source, true));
              refreshTreasureEntryList.run();
            }
          } finally {
            syncing[0] = false;
          }
          refreshAvailableMonsters.run();
          refreshEncounterMonsterList.run();
          refreshEncounterList.run();
          refreshAvailableEvents.run();
          refreshAvailableTreasures.run();
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
            clearDraft.run();
            if (tableList.getItemCount() > 0) {
              tableList.select(0);
              loadSelectedTable.run();
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
    editorModeCombo.addListener(
        SWT.Selection,
        event -> {
          showCurrentEditor.run();
          refreshTableList.run();
          clearDraft.run();
          if (tableList.getItemCount() > 0) {
            tableList.select(0);
            loadSelectedTable.run();
          }
        });
    tableList.addListener(SWT.Selection, event -> loadSelectedTable.run());
    newButton.addListener(SWT.Selection, event -> clearDraft.run());

    monsterTableNameText.addModifyListener(
        event -> {
          if (!syncing[0] && "monsters".equals(currentTableEditorMode(editorModeCombo))) {
            draftTable[0].name = monsterTableNameText.getText().trim();
          }
        });
    eventTableNameText.addModifyListener(
        event -> {
          if (!syncing[0] && "events".equals(currentTableEditorMode(editorModeCombo))) {
            draftTable[0].name = eventTableNameText.getText().trim();
          }
        });
    treasureTableNameText.addModifyListener(
        event -> {
          if (!syncing[0] && "treasures".equals(currentTableEditorMode(editorModeCombo))) {
            draftTable[0].name = treasureTableNameText.getText().trim();
          }
        });
    eventSubtypeCombo.addListener(
        SWT.Selection,
        event -> {
          if (!syncing[0]) {
            draftTable[0].kind = tableKindForEventSubtype(eventSubtypeValue(eventSubtypeCombo));
            draftTable[0].entries.removeIf(entry -> !"event".equals(entry.type));
            refreshEventEntryList.run();
            refreshAvailableEvents.run();
          }
        });
    treasureSubtypeCombo.addListener(
        SWT.Selection,
        event -> {
          if (!syncing[0]) {
            draftTable[0].kind = "treasure";
            draftTable[0].entries.removeIf(entry -> !"event".equals(entry.type));
            refreshTreasureEntryList.run();
            refreshAvailableTreasures.run();
          }
        });
    eventOnlyUnassignedCheck.addListener(SWT.Selection, event -> refreshAvailableEvents.run());
    treasureOnlyUnassignedCheck.addListener(SWT.Selection, event -> refreshAvailableTreasures.run());

    addMonsterButton.addListener(
        SWT.Selection,
        event -> {
          int comboIndex = availableMonsterCombo.getSelectionIndex();
          if (comboIndex < 0 || comboIndex >= visibleMonsterOptionIds.size()) {
            return;
          }
          try {
            int min = parsePositiveTableNumber(minNumberText.getText(), I18n.t("editor.tables.message.requiredMinNumber"));
            int max = parsePositiveTableNumber(maxNumberText.getText(), I18n.t("editor.tables.message.requiredMaxNumber"));
            if (min > max) {
              throw new IllegalArgumentException(I18n.t("editor.tables.message.invalidNumberRange"));
            }
            TableEntry entry = new TableEntry();
            entry.type = "monster";
            entry.id = visibleMonsterOptionIds.get(comboIndex);
            entry.number = formatMonsterNumberRange(min, max);
            entry.specialRaw = textToSpecialRaw(monsterSpecialText.getText());
            MonsterEntry monster = availableTableMonsterEntries().get(entry.id);
            entry.ambiences = monsterFactionsAsAmbiences(monster);
            draftEncounterMembers.add(entry);
            minNumberText.setText("");
            maxNumberText.setText("");
            monsterSpecialText.setText("");
            refreshEncounterMonsterList.run();
          } catch (Exception ex) {
            showError(dialog, ex);
          }
        });
    removeMonsterButton.addListener(
        SWT.Selection,
        event -> {
          int index = encounterMonsterList.getSelectionIndex();
          if (index < 0 || index >= draftEncounterMembers.size()) {
            return;
          }
          draftEncounterMembers.remove(index);
          refreshEncounterMonsterList.run();
        });
    addEncounterButton.addListener(
        SWT.Selection,
        event -> {
          try {
            validateRequiredField(monsterTableNameText.getText().trim(), I18n.t("editor.tables.message.requiredTableName"));
            if (draftEncounterMembers.isEmpty()) {
              throw new IllegalArgumentException(I18n.t("editor.tables.message.requiredEncounterMonsters"));
            }
            String level = safe(encounterLevelCombo.getText()).trim();
            validateRequiredField(level, I18n.t("editor.tables.message.requiredEncounterLevel"));
            TableEntry encounter =
                createMonsterEncounterEntry(draftEncounterMembers, level, availableTableMonsterEntries());
            draftTable[0].entries.add(encounter);
            draftEncounterMembers.clear();
            minNumberText.setText("");
            maxNumberText.setText("");
            monsterSpecialText.setText("");
            encounterLevelCombo.select(0);
            refreshEncounterMonsterList.run();
            refreshEncounterList.run();
          } catch (Exception ex) {
            showError(dialog, ex);
          }
        });
    removeEncounterButton.addListener(
        SWT.Selection,
        event -> {
          int index = encounterList.getSelectionIndex();
          List<TableEntry> entries = tableMonsterEntries(draftTable[0]);
          if (index < 0 || index >= entries.size()) {
            return;
          }
          TableEntry selectedEncounter = entries.get(index);
          draftTable[0].entries.remove(selectedEncounter);
          refreshEncounterList.run();
        });

    addEventButton.addListener(
        SWT.Selection,
        event -> {
          int comboIndex = availableEventCombo.getSelectionIndex();
          if (comboIndex < 0 || comboIndex >= visibleEventOptionIds.size()) {
            return;
          }
          String eventId = visibleEventOptionIds.get(comboIndex);
          if (containsTableEntryId(draftTable[0], eventId)) {
            return;
          }
          draftTable[0].entries.add(tableEventEntry(eventId));
          refreshEventEntryList.run();
          refreshAvailableEvents.run();
        });
    removeEventButton.addListener(
        SWT.Selection,
        event -> {
          int selectedIndex = eventEntryList.getSelectionIndex();
          if (selectedIndex < 0) {
            return;
          }
          List<TableEntry> entries = tableEventEntries(draftTable[0]);
          if (selectedIndex >= entries.size()) {
            return;
          }
          String id = safe(entries.get(selectedIndex).id);
          draftTable[0].entries.removeIf(entry -> "event".equals(entry.type) && id.equals(safe(entry.id)));
          refreshEventEntryList.run();
          refreshAvailableEvents.run();
        });
    addTreasureButton.addListener(
        SWT.Selection,
        event -> {
          int comboIndex = availableTreasureCombo.getSelectionIndex();
          if (comboIndex < 0 || comboIndex >= visibleTreasureOptionIds.size()) {
            return;
          }
          String eventId = visibleTreasureOptionIds.get(comboIndex);
          if (containsTableEntryId(draftTable[0], eventId)) {
            return;
          }
          draftTable[0].entries.add(tableEventEntry(eventId));
          refreshTreasureEntryList.run();
          refreshAvailableTreasures.run();
        });
    removeTreasureButton.addListener(
        SWT.Selection,
        event -> {
          int selectedIndex = treasureEntryList.getSelectionIndex();
          if (selectedIndex < 0) {
            return;
          }
          List<TableEntry> entries = tableEventEntries(draftTable[0]);
          if (selectedIndex >= entries.size()) {
            return;
          }
          String id = safe(entries.get(selectedIndex).id);
          draftTable[0].entries.removeIf(entry -> "event".equals(entry.type) && id.equals(safe(entry.id)));
          refreshTreasureEntryList.run();
          refreshAvailableTreasures.run();
        });

    deleteButton.addListener(
        SWT.Selection,
        event -> {
          if (!hasSelectedFile(fileCombo, files, dialog)) {
            return;
          }
          int modelIndex = selectedTableModelIndex[0];
          if (modelIndex < 0 || modelIndex >= model[0].tables.size()) {
            showWarning(dialog, I18n.t("editor.tables.message.selectTable"));
            return;
          }
          if (!confirm(dialog, I18n.t("editor.message.deleteConfirm"))) {
            return;
          }
          try {
            model[0].tables.remove(modelIndex);
            service.saveTables(selectedFile(fileCombo, files), model[0]);
            refreshTableList.run();
            clearDraft.run();
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
          String mode = currentTableEditorMode(editorModeCombo);
          try {
            TableDefinition prepared = copyTableDefinition(draftTable[0]);
            if ("monsters".equals(mode)) {
              prepared.name = monsterTableNameText.getText().trim();
              prepared.kind = "";
              prepared.entries = new ArrayList<>(tableMonsterEntries(draftTable[0]));
              validateRequiredField(prepared.name, I18n.t("editor.tables.message.requiredTableName"));
            } else if ("events".equals(mode)) {
              prepared.entries = new ArrayList<>(tableEventEntries(draftTable[0]));
              String subtype = eventSubtypeValue(eventSubtypeCombo);
              prepared.name = eventTableNameText.getText().trim();
              prepared.kind = tableKindForEventSubtype(subtype);
              validateRequiredField(prepared.name, I18n.t("editor.tables.message.requiredTableName"));
              validateRequiredField(subtype, I18n.t("editor.tables.message.requiredSubtype"));
            } else {
              prepared.entries = new ArrayList<>(tableEventEntries(draftTable[0]));
              String subtype = treasureSubtypeValue(treasureSubtypeCombo);
              prepared.name = treasureTableNameText.getText().trim();
              prepared.kind = "treasure";
              validateRequiredField(prepared.name, I18n.t("editor.tables.message.requiredTableName"));
              validateRequiredField(subtype, I18n.t("editor.tables.message.requiredSubtype"));
            }
            if (prepared.entries.isEmpty()) {
              throw new IllegalArgumentException(I18n.t("editor.tables.message.requiredEntries"));
            }

            if (selectedTableModelIndex[0] >= 0 && selectedTableModelIndex[0] < model[0].tables.size()) {
              model[0].tables.set(selectedTableModelIndex[0], prepared);
            } else {
              model[0].tables.add(prepared);
              selectedTableModelIndex[0] = model[0].tables.size() - 1;
            }

            service.saveTables(selectedFile(fileCombo, files), model[0]);
            refreshTableList.run();
            selectVisibleTableIndex(tableList, visibleTableIndices, selectedTableModelIndex[0]);
            loadSelectedTable.run();
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

    showCurrentEditor.run();
    clearDraft.run();
  }

  private static String tableLabel(TableDefinition table) {
    String name = safe(table == null ? "" : table.name);
    String kind = safe(table == null ? "" : table.kind);
    if (kind.isEmpty()) {
      return name;
    }
    return name + " [" + kind + "]";
  }

  private static TableDefinition emptyTableDefinition() {
    return new TableDefinition();
  }

  private static TableDefinition copyTableDefinition(TableDefinition source) {
    TableDefinition copy = new TableDefinition();
    copy.id = safe(source == null ? "" : source.id);
    copy.name = safe(source == null ? "" : source.name);
    copy.kind = safe(source == null ? "" : source.kind);
    if (source != null && source.entries != null) {
      for (TableEntry entry : source.entries) {
        copy.entries.add(copyTableEntry(entry));
      }
    }
    return copy;
  }

  private static TableEntry copyTableEntry(TableEntry source) {
    TableEntry copy = new TableEntry();
    copy.type = safe(source == null ? "" : source.type);
    copy.id = safe(source == null ? "" : source.id);
    copy.number = safe(source == null ? "" : source.number);
    copy.level = safe(source == null ? "" : source.level);
    copy.ambiences = safe(source == null ? "" : source.ambiences);
    copy.specialRaw = safe(source == null ? "" : source.specialRaw);
    copy.specialAppend = safe(source == null ? "" : source.specialAppend);
    copy.refName = safe(source == null ? "" : source.refName);
    copy.targetLevel = safe(source == null ? "" : source.targetLevel);
    copy.times = safe(source == null ? "" : source.times);
    if (source != null && source.groupMembers != null) {
      for (TableGroupMember member : source.groupMembers) {
        TableGroupMember memberCopy = new TableGroupMember();
        memberCopy.id = safe(member == null ? "" : member.id);
        memberCopy.number = safe(member == null ? "" : member.number);
        memberCopy.ambiences = safe(member == null ? "" : member.ambiences);
        memberCopy.specialRaw = safe(member == null ? "" : member.specialRaw);
        memberCopy.specialAppend = safe(member == null ? "" : member.specialAppend);
        copy.groupMembers.add(memberCopy);
      }
    }
    return copy;
  }

  private static String currentTableEditorMode(Combo combo) {
    int index = combo == null ? -1 : combo.getSelectionIndex();
    return switch (index) {
      case 0 -> "monsters";
      case 2 -> "treasures";
      default -> "events";
    };
  }

  private static boolean tableMatchesEditorMode(TableDefinition table, String mode) {
    String normalizedMode = safe(mode);
    String kind = safe(table == null ? "" : table.kind).trim().toLowerCase(Locale.ROOT);
    boolean treasure = "treasure".equals(kind);
    boolean monster =
        table != null
            && table.entries.stream().anyMatch(entry -> "monster".equals(safe(entry.type)) || "group".equals(safe(entry.type)));
    if ("treasures".equals(normalizedMode)) {
      return treasure;
    }
    if ("monsters".equals(normalizedMode)) {
      return monster && !treasure;
    }
    return !treasure && !monster;
  }

  private static String eventSubtypeValue(Combo combo) {
    int index = combo == null ? -1 : combo.getSelectionIndex();
    return switch (index) {
      case 0 -> "dungeon";
      case 1 -> "travel";
      case 2 -> "settlement";
      default -> "";
    };
  }

  private static String treasureSubtypeValue(Combo combo) {
    int index = combo == null ? -1 : combo.getSelectionIndex();
    return switch (index) {
      case 0 -> "dungeonTreasure";
      case 1 -> "objectiveTreasure";
      default -> "";
    };
  }

  private static void setTableSubtypeSelection(Combo combo, String subtype) {
    String normalized = safe(subtype);
    if (combo == null) {
      return;
    }
    switch (normalized) {
      case "dungeon", "dungeonTreasure" -> combo.select(0);
      case "travel" -> combo.select(1);
      case "settlement", "objectiveTreasure" -> combo.select(normalized.equals("settlement") ? 2 : 1);
      default -> combo.deselectAll();
    }
  }

  private static String eventSubtypeForTable(TableDefinition table, boolean treasureEditor) {
    if (treasureEditor) {
      String name = safe(table == null ? "" : table.name).toLowerCase(Locale.ROOT);
      return name.contains("objective") || name.contains("objetive")
          ? "objectiveTreasure"
          : "dungeonTreasure";
    }
    String kind = safe(table == null ? "" : table.kind).trim().toLowerCase(Locale.ROOT);
    if ("travel".equals(kind)) {
      return "travel";
    }
    if ("settlement".equals(kind)) {
      return "settlement";
    }
    return "dungeon";
  }

  private static String tableKindForEventSubtype(String subtype) {
    return switch (safe(subtype)) {
      case "travel" -> "travel";
      case "settlement" -> "settlement";
      default -> "";
    };
  }

  private static TableEntry tableEventEntry(String id) {
    TableEntry entry = new TableEntry();
    entry.type = "event";
    entry.id = safe(id).trim();
    return entry;
  }

  private static List<TableEntry> tableEventEntries(TableDefinition table) {
    List<TableEntry> entries = new ArrayList<>();
    if (table == null || table.entries == null) {
      return entries;
    }
    for (TableEntry entry : table.entries) {
      if ("event".equals(safe(entry.type))) {
        entries.add(entry);
      }
    }
    return entries;
  }

  private static List<TableEntry> tableMonsterEntries(TableDefinition table) {
    List<TableEntry> entries = new ArrayList<>();
    if (table == null || table.entries == null) {
      return entries;
    }
    for (TableEntry entry : table.entries) {
      String type = safe(entry.type);
      if ("monster".equals(type) || "group".equals(type)) {
        entries.add(entry);
      }
    }
    return entries;
  }

  private static boolean containsTableEntryId(TableDefinition table, String id) {
    String normalized = safe(id).trim();
    return tableEventEntries(table).stream().anyMatch(entry -> normalized.equals(safe(entry.id).trim()));
  }

  private static String tableMonsterMemberLabel(TableEntry entry, Map<String, String> monsterLabels) {
    String name = monsterLabels.getOrDefault(safe(entry.id), safe(entry.id));
    String number = safe(entry.number);
    String special = specialRawToText(entry.specialRaw);
    return special.isBlank() ? name + " x " + number : name + " x " + number + " [" + special + "]";
  }

  private static String tableMonsterEncounterLabel(TableEntry entry, Map<String, String> monsterLabels) {
    String level = safe(entry.level).isBlank() ? "1" : safe(entry.level);
    if ("group".equals(safe(entry.type))) {
      List<String> members = new ArrayList<>();
      for (TableGroupMember member : entry.groupMembers) {
        String name = monsterLabels.getOrDefault(safe(member.id), safe(member.id));
        members.add(name + " x " + safe(member.number));
      }
      return "L" + level + " | " + String.join(" + ", members);
    }
    return "L"
        + level
        + " | "
        + monsterLabels.getOrDefault(safe(entry.id), safe(entry.id))
        + " x "
        + safe(entry.number);
  }

  private static String firstMonsterEncounterLevel(TableDefinition table) {
    List<TableEntry> entries = tableMonsterEntries(table);
    if (entries.isEmpty()) {
      return "1";
    }
    String level = safe(entries.get(0).level).trim();
    return level.isBlank() ? "1" : level;
  }

  private static int parsePositiveTableNumber(String raw, String message) {
    validateRequiredField(raw, message);
    try {
      int value = Integer.parseInt(safe(raw).trim());
      if (value <= 0) {
        throw new IllegalArgumentException(message);
      }
      return value;
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException(message);
    }
  }

  private static String formatMonsterNumberRange(int min, int max) {
    return min == max ? Integer.toString(min) : min + "-" + max;
  }

  private static String textToSpecialRaw(String text) {
    String normalized = safe(text).trim();
    return normalized.isBlank() ? "" : "text|" + normalized;
  }

  private static String specialRawToText(String raw) {
    String normalized = safe(raw).trim();
    if (normalized.startsWith("text|")) {
      return normalized.substring("text|".length()).trim();
    }
    return normalized;
  }

  private Map<String, MonsterEntry> availableTableMonsterEntries() {
    Map<String, MonsterEntry> monsters = new LinkedHashMap<>();
    try {
      EditableContentTranslations translations = loadEditableTranslations();
      List<MonsterEntry> entries = new ArrayList<>();
      for (Path file : service.listMonsterFiles()) {
        entries.addAll(applyMonsterTranslations(service.loadMonsters(file), translations));
      }
      entries.sort(
          Comparator.comparing((MonsterEntry entry) -> safe(entry.name), String.CASE_INSENSITIVE_ORDER)
              .thenComparing(entry -> safe(entry.id), String.CASE_INSENSITIVE_ORDER));
      for (MonsterEntry entry : entries) {
        monsters.putIfAbsent(safe(entry.id), copyMonsterEntry(entry));
      }
      return monsters;
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  private Map<String, String> availableTableMonsterLabels() {
    Map<String, String> labels = new LinkedHashMap<>();
    for (Map.Entry<String, MonsterEntry> entry : availableTableMonsterEntries().entrySet()) {
      labels.put(entry.getKey(), safe(entry.getValue().name).isBlank() ? entry.getKey() : safe(entry.getValue().name));
    }
    return labels;
  }

  private static String monsterChoiceLabel(String id, MonsterEntry entry) {
    String name = safe(entry == null ? "" : entry.name).trim();
    return name.isBlank() ? safe(id) : name + " (" + safe(id) + ")";
  }

  private static String monsterFactionsAsAmbiences(MonsterEntry monster) {
    return joinAmbiences(splitCsv(monster == null ? "" : monster.factions));
  }

  private static String joinAmbiences(List<String> values) {
    LinkedHashSet<String> normalized = new LinkedHashSet<>();
    for (String value : values) {
      String trimmed = safe(value).trim().toLowerCase(Locale.ROOT);
      if (!trimmed.isBlank()) {
        normalized.add(trimmed);
      }
    }
    return String.join(" ", normalized);
  }

  private static String combinedEncounterAmbiences(
      List<TableEntry> members, Map<String, MonsterEntry> availableMonsters) {
    List<String> values = new ArrayList<>();
    for (TableEntry member : members) {
      MonsterEntry monster = availableMonsters.get(safe(member.id));
      if (monster != null) {
        values.addAll(splitCsv(monster.factions));
      }
    }
    return joinAmbiences(values);
  }

  private static TableEntry createMonsterEncounterEntry(
      List<TableEntry> members, String level, Map<String, MonsterEntry> availableMonsters) {
    String normalizedLevel = safe(level).trim();
    String combinedAmbiences = combinedEncounterAmbiences(members, availableMonsters);
    if (members.size() == 1) {
      TableEntry entry = copyTableEntry(members.get(0));
      entry.type = "monster";
      entry.level = normalizedLevel;
      entry.ambiences = combinedAmbiences;
      return entry;
    }

    TableEntry group = new TableEntry();
    group.type = "group";
    group.level = normalizedLevel;
    for (TableEntry memberEntry : members) {
      TableGroupMember member = new TableGroupMember();
      member.id = safe(memberEntry.id);
      member.number = safe(memberEntry.number);
      member.specialRaw = safe(memberEntry.specialRaw);
      member.ambiences = combinedAmbiences;
      group.groupMembers.add(member);
    }
    return group;
  }

  private static void selectVisibleTableIndex(
      org.eclipse.swt.widgets.List tableList, java.util.List<Integer> visibleTableIndices, int modelIndex) {
    for (int i = 0; i < visibleTableIndices.size(); i++) {
      if (visibleTableIndices.get(i) == modelIndex) {
        tableList.select(i);
        return;
      }
    }
    tableList.deselectAll();
  }

  private Set<String> assignedEventIdsForSubtype(
      Path currentFile,
      TableFileModel currentModel,
      String mode,
      String subtype,
      int excludedTableModelIndex) {
    Set<String> ids = new LinkedHashSet<>();
    java.util.List<Path> tableFiles;
    try {
      tableFiles = service.listTableFiles();
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
    for (Path file : tableFiles) {
      TableFileModel model;
      try {
        model =
            currentFile != null
                    && file.toAbsolutePath().normalize().equals(currentFile.toAbsolutePath().normalize())
                ? currentModel
                : service.loadTables(file);
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
      if (model == null || model.tables == null) {
        continue;
      }
      for (int i = 0; i < model.tables.size(); i++) {
        if (currentFile != null
            && file.toAbsolutePath().normalize().equals(currentFile.toAbsolutePath().normalize())
            && i == excludedTableModelIndex) {
          continue;
        }
        TableDefinition table = model.tables.get(i);
        if (!tableMatchesEditorMode(table, mode)) {
          continue;
        }
        if (!safe(subtype).equals(eventSubtypeForTable(table, "treasures".equals(mode)))) {
          continue;
        }
        for (TableEntry entry : tableEventEntries(table)) {
          ids.add(safe(entry.id));
        }
      }
    }
    return ids;
  }

  private Map<String, String> availableTableEventLabels(String subtype, boolean treasureMode) {
    Map<String, String> labels = new LinkedHashMap<>();
    String normalizedSubtype = safe(subtype);
    if (normalizedSubtype.isBlank()) {
      return labels;
    }
    try {
      EditableContentTranslations translations = loadEditableTranslations();
      List<EventEntry> loadedEntries = new ArrayList<>();
      if (treasureMode) {
        Predicate<EventEntry> filter =
            "objectiveTreasure".equals(normalizedSubtype)
                ? this::isObjectiveTreasureEntry
                : this::isDungeonTreasureEntry;
        for (Path file : service.listEventFiles()) {
          loadedEntries.addAll(filterVisibleEvents(file, service.loadEvents(file), filter, true, "objectiveTreasure".equals(normalizedSubtype)));
        }
      } else if ("travel".equals(normalizedSubtype)) {
        for (Path file : service.listTravelFiles()) {
          loadedEntries.addAll(service.loadEvents(file));
        }
      } else if ("settlement".equals(normalizedSubtype)) {
        for (Path file : service.listSettlementFiles()) {
          loadedEntries.addAll(service.loadEvents(file));
        }
      } else {
        for (Path file : listNonTreasureEventFiles()) {
          loadedEntries.addAll(service.loadEvents(file));
        }
      }
      List<EventEntry> translatedEntries = applyEventTranslations(loadedEntries, translations);
      translatedEntries.sort(
          Comparator.comparing((EventEntry entry) -> safe(entry.name), String.CASE_INSENSITIVE_ORDER)
              .thenComparing(entry -> safe(entry.id), String.CASE_INSENSITIVE_ORDER));
      for (EventEntry entry : translatedEntries) {
        labels.putIfAbsent(safe(entry.id), safe(entry.name).isBlank() ? safe(entry.id) : safe(entry.name) + " (" + safe(entry.id) + ")");
      }
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
    return labels;
  }

  private static void validateRequiredField(String value, String message) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(message == null || message.isBlank() ? "Required field." : message);
    }
  }
}
