package pms.whq.swt.editor;

import static pms.whq.swt.editor.EditorSupport.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.StyledText;
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

import pms.whq.xml.XmlContentService.LocationEntry;
import pms.whq.xml.XmlContentService.WarriorEntry;

/** Pestana de localizaciones del asentamiento. */
public final class LocationsTab extends EditorTab {

  public LocationsTab(EditorContext context) {
    super(context);
  }

  private static final String LOCATION_NAME_SUFFIX = ".name";

  private static final String LOCATION_DESCRIPTION_SUFFIX = ".description";

  private static final String LOCATION_RULES_SUFFIX = ".rules";

  @Override
  public void create(TabFolder tabs, Shell dialog) {
    TabItem tab = new TabItem(tabs, SWT.NONE);
    tab.setText(I18n.t("dialog.contentEditor.tab.locations"));

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
    attributes.setText(I18n.t("editor.locations.group.attributes"));
    attributes.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
    attributes.setLayout(new GridLayout(2, false));

    Text idText = createLabeledText(attributes, I18n.t("editor.locations.label.id") + ":");
    idText.setEditable(false);
    Text nameText = createLabeledText(attributes, I18n.t("editor.locations.label.name") + ":");

    new Label(attributes, SWT.NONE).setText(I18n.t("editor.locations.label.available") + ":");
    Composite availableEditor = new Composite(attributes, SWT.NONE);
    availableEditor.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
    GridLayout availableEditorLayout = new GridLayout(1, false);
    availableEditorLayout.marginWidth = 0;
    availableEditorLayout.marginHeight = 0;
    availableEditor.setLayout(availableEditorLayout);

    Composite availablePickerRow = new Composite(availableEditor, SWT.NONE);
    availablePickerRow.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    GridLayout availablePickerLayout = new GridLayout(3, false);
    availablePickerLayout.marginWidth = 0;
    availablePickerLayout.marginHeight = 0;
    availablePickerRow.setLayout(availablePickerLayout);
    Combo availableTypeCombo = new Combo(availablePickerRow, SWT.DROP_DOWN | SWT.READ_ONLY);
    availableTypeCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    Button addAvailableButton = new Button(availablePickerRow, SWT.PUSH);
    addAvailableButton.setText("+");
    Button removeAvailableButton = new Button(availablePickerRow, SWT.PUSH);
    removeAvailableButton.setText("-");
    org.eclipse.swt.widgets.List availableList =
        new org.eclipse.swt.widgets.List(availableEditor, SWT.BORDER | SWT.SINGLE | SWT.V_SCROLL);
    GridData availableListData = new GridData(SWT.FILL, SWT.FILL, true, false);
    availableListData.heightHint = 72;
    availableList.setLayoutData(availableListData);

    new Label(attributes, SWT.NONE).setText(I18n.t("editor.locations.label.visitors") + ":");
    Composite visitorsEditor = new Composite(attributes, SWT.NONE);
    visitorsEditor.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
    GridLayout visitorsEditorLayout = new GridLayout(1, false);
    visitorsEditorLayout.marginWidth = 0;
    visitorsEditorLayout.marginHeight = 0;
    visitorsEditor.setLayout(visitorsEditorLayout);

    Composite visitorsPickerRow = new Composite(visitorsEditor, SWT.NONE);
    visitorsPickerRow.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    GridLayout visitorsPickerLayout = new GridLayout(3, false);
    visitorsPickerLayout.marginWidth = 0;
    visitorsPickerLayout.marginHeight = 0;
    visitorsPickerRow.setLayout(visitorsPickerLayout);
    Combo visitorsCombo = new Combo(visitorsPickerRow, SWT.DROP_DOWN | SWT.READ_ONLY);
    visitorsCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    Button addVisitorButton = new Button(visitorsPickerRow, SWT.PUSH);
    addVisitorButton.setText("+");
    Button removeVisitorButton = new Button(visitorsPickerRow, SWT.PUSH);
    removeVisitorButton.setText("-");
    org.eclipse.swt.widgets.List visitorsList =
        new org.eclipse.swt.widgets.List(visitorsEditor, SWT.BORDER | SWT.SINGLE | SWT.V_SCROLL);
    GridData visitorsListData = new GridData(SWT.FILL, SWT.FILL, true, false);
    visitorsListData.heightHint = 96;
    visitorsList.setLayoutData(visitorsListData);

    Group textGroup = new Group(details, SWT.NONE);
    textGroup.setText(I18n.t("editor.locations.group.text"));
    textGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    textGroup.setLayout(new GridLayout(2, false));

    new Label(textGroup, SWT.NONE).setText(I18n.t("editor.locations.label.description") + ":");
    StyledText descriptionText = new StyledText(textGroup, SWT.BORDER | SWT.WRAP | SWT.V_SCROLL);
    GridData descriptionData = new GridData(SWT.FILL, SWT.FILL, true, true);
    descriptionData.heightHint = 120;
    descriptionText.setLayoutData(descriptionData);

    new Label(textGroup, SWT.NONE).setText(I18n.t("editor.locations.label.rules") + ":");
    StyledText rulesText = new StyledText(textGroup, SWT.BORDER | SWT.WRAP | SWT.V_SCROLL);
    GridData rulesData = new GridData(SWT.FILL, SWT.FILL, true, true);
    rulesData.heightHint = 180;
    rulesText.setLayoutData(rulesData);

    java.util.List<Path> files = new ArrayList<>();
    java.util.List<LocationEntry> entries = new ArrayList<>();
    final int[] selectedIndex = new int[] {-1};
    final String[] selectedId = new String[] {""};
    final LinkedHashSet<String> selectedAvailableTypes = new LinkedHashSet<>();
    final LinkedHashSet<String> selectedVisitors = new LinkedHashSet<>();
    final java.util.List<String> visibleSelectedAvailableTypes = new ArrayList<>();
    final java.util.List<String> visibleSelectedVisitors = new ArrayList<>();
    final Map<String, String> availableTypeLabels = locationAvailableTypeLabels();
    final Map<String, String> visitorLabels = new LinkedHashMap<>();

    for (String type : availableTypeLabels.keySet()) {
      availableTypeCombo.add(availableTypeLabels.get(type));
    }
    if (availableTypeCombo.getItemCount() > 0) {
      availableTypeCombo.select(0);
    }

    Runnable refreshList = () -> {
      itemList.removeAll();
      for (LocationEntry entry : entries) {
        itemList.add(safe(entry.id) + " - " + safe(entry.name));
      }
    };

    Runnable refreshAvailableSelectionList =
        () -> {
          availableList.removeAll();
          visibleSelectedAvailableTypes.clear();
          for (String type : selectedAvailableTypes) {
            String label = availableTypeLabels.getOrDefault(type, type);
            availableList.add(label);
            visibleSelectedAvailableTypes.add(type);
          }
        };

    Runnable refreshVisitorsSelectionList =
        () -> {
          visitorsList.removeAll();
          visibleSelectedVisitors.clear();
          for (String visitor : selectedVisitors) {
            String label = visitorLabels.getOrDefault(visitor, visitor);
            visitorsList.add(label);
            visibleSelectedVisitors.add(visitor);
          }
        };

    Runnable refreshId =
        () -> idText.setText(selectedId[0].isBlank() ? "location-" + slugify(nameText.getText()) : selectedId[0]);

    Runnable clearForm =
        () -> {
          selectedIndex[0] = -1;
          selectedId[0] = "";
          itemList.deselectAll();
          nameText.setText("");
          selectedAvailableTypes.clear();
          selectedVisitors.clear();
          refreshAvailableSelectionList.run();
          refreshVisitorsSelectionList.run();
          descriptionText.setText("");
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
          LocationEntry entry = entries.get(index);
          selectedId[0] = safe(entry.id);
          nameText.setText(safe(entry.name));
          selectedAvailableTypes.clear();
          selectedAvailableTypes.addAll(splitCsv(entry.available));
          selectedVisitors.clear();
          selectedVisitors.addAll(splitCsv(entry.visitors));
          refreshAvailableSelectionList.run();
          refreshVisitorsSelectionList.run();
          descriptionText.setText(safe(entry.description));
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
            entries.addAll(applyLocationTranslations(service.loadLocations(files.get(fileIndex)), translations));
            refreshList.run();
            clearForm.run();
          } catch (Exception ex) {
            showError(dialog, ex);
          }
        };

    try {
      files.addAll(service.listLocationFiles());
      visitorLabels.putAll(loadAvailableLocationVisitorLabels());
      for (String visitor : visitorLabels.keySet()) {
        visitorsCombo.add(visitorLabels.get(visitor));
      }
      if (visitorsCombo.getItemCount() > 0) {
        visitorsCombo.select(0);
      }
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
                service.getLocationsDirectory(),
                "userdefined-locations.xml",
                service::createEmptyLocationsFile,
                service::listLocationFiles,
                loadFile));
    itemList.addListener(SWT.Selection, event -> loadSelectedToForm.run());
    newButton.addListener(SWT.Selection, event -> clearForm.run());
    nameText.addModifyListener(event -> refreshId.run());
    addAvailableButton.addListener(
        SWT.Selection,
        event -> {
          int comboIndex = availableTypeCombo.getSelectionIndex();
          if (comboIndex < 0 || comboIndex >= availableTypeLabels.size()) {
            return;
          }
          String type = new ArrayList<>(availableTypeLabels.keySet()).get(comboIndex);
          selectedAvailableTypes.add(type);
          refreshAvailableSelectionList.run();
        });
    removeAvailableButton.addListener(
        SWT.Selection,
        event -> {
          int listIndex = availableList.getSelectionIndex();
          if (listIndex < 0 || listIndex >= visibleSelectedAvailableTypes.size()) {
            return;
          }
          selectedAvailableTypes.remove(visibleSelectedAvailableTypes.get(listIndex));
          refreshAvailableSelectionList.run();
        });
    addVisitorButton.addListener(
        SWT.Selection,
        event -> {
          int comboIndex = visitorsCombo.getSelectionIndex();
          if (comboIndex < 0 || comboIndex >= visitorLabels.size()) {
            return;
          }
          String visitor = new ArrayList<>(visitorLabels.keySet()).get(comboIndex);
          selectedVisitors.add(visitor);
          refreshVisitorsSelectionList.run();
        });
    removeVisitorButton.addListener(
        SWT.Selection,
        event -> {
          int listIndex = visitorsList.getSelectionIndex();
          if (listIndex < 0 || listIndex >= visibleSelectedVisitors.size()) {
            return;
          }
          selectedVisitors.remove(visibleSelectedVisitors.get(listIndex));
          refreshVisitorsSelectionList.run();
        });

    saveButton.addListener(
        SWT.Selection,
        event -> {
          try {
            if (!hasSelectedFile(fileCombo, files, dialog)) {
              return;
            }
            LocationEntry entry = new LocationEntry();
            entry.id = selectedId[0].isBlank() ? "location-" + slugify(nameText.getText()) : selectedId[0];
            entry.name = nameText.getText().trim();
            entry.available = String.join(",", selectedAvailableTypes);
            entry.visitors = String.join(",", selectedVisitors);
            entry.description = descriptionText.getText().trim();
            entry.rules = rulesText.getText().trim();

            ensureUniqueId(entries, selectedIndex[0], entry.id);
            EditableContentTranslations translations = loadEditableTranslations();
            if (selectedIndex[0] >= 0) {
              entries.set(selectedIndex[0], entry);
            } else {
              entries.add(entry);
            }
            putLocationTranslations(translations, entry);
            service.saveLocations(selectedFile(fileCombo, files), entries);
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
            removeLocationTranslations(translations, entries.get(index).id);
            entries.remove(index);
            service.saveLocations(selectedFile(fileCombo, files), entries);
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
            service.validateLocationsFile(selectedFile(fileCombo, files));
            showInfo(dialog, I18n.t("editor.message.validated"));
          } catch (Exception ex) {
            showError(dialog, ex);
          }
        });

    refreshId.run();
  }

  private Map<String, String> locationAvailableTypeLabels() {
    Map<String, String> labels = new LinkedHashMap<>();
    labels.put("city", I18n.t("dialog.newSettlement.type.city"));
    labels.put("town", I18n.t("dialog.newSettlement.type.town"));
    labels.put("village", I18n.t("dialog.newSettlement.type.village"));
    labels.put("outskirts", I18n.t("dialog.newSettlement.type.outskirts"));
    labels.put("special", I18n.t("dialog.newSettlement.type.special"));
    return labels;
  }

  private Map<String, String> loadAvailableLocationVisitorLabels() throws Exception {
    Map<String, String> labels = new LinkedHashMap<>();
    labels.put("all", "all");

    EditableContentTranslations translations = loadEditableTranslations();
    for (Path file : service.listWarriorFiles()) {
      for (WarriorEntry warrior : applyWarriorTranslations(service.loadWarriors(file), translations)) {
        String id = safe(warrior.id).trim();
        if (!id.isBlank()) {
          String label = safe(warrior.name).trim();
          labels.putIfAbsent(id, label.isBlank() ? id : label);
        }
      }
    }
    return labels;
  }

  private static List<LocationEntry> applyLocationTranslations(
      List<LocationEntry> entries, EditableContentTranslations translations) {
    List<LocationEntry> localized = new ArrayList<>();
    for (LocationEntry entry : entries) {
      LocationEntry copy = copyLocationEntry(entry);
      copy.name = translations.t(locationTranslationKey(copy.id, LOCATION_NAME_SUFFIX), copy.name);
      copy.description =
          translations.t(locationTranslationKey(copy.id, LOCATION_DESCRIPTION_SUFFIX), copy.description);
      copy.rules = translations.t(locationTranslationKey(copy.id, LOCATION_RULES_SUFFIX), copy.rules);
      localized.add(copy);
    }
    return localized;
  }

  private static void putLocationTranslations(EditableContentTranslations translations, LocationEntry entry) {
    translations.put(locationTranslationKey(entry.id, LOCATION_NAME_SUFFIX), entry.name);
    translations.put(locationTranslationKey(entry.id, LOCATION_DESCRIPTION_SUFFIX), entry.description);
    translations.put(locationTranslationKey(entry.id, LOCATION_RULES_SUFFIX), entry.rules);
  }

  private static void removeLocationTranslations(EditableContentTranslations translations, String locationId) {
    translations.remove(locationTranslationKey(locationId, LOCATION_NAME_SUFFIX));
    translations.remove(locationTranslationKey(locationId, LOCATION_DESCRIPTION_SUFFIX));
    translations.remove(locationTranslationKey(locationId, LOCATION_RULES_SUFFIX));
  }

  private static LocationEntry copyLocationEntry(LocationEntry source) {
    LocationEntry copy = new LocationEntry();
    copy.id = safe(source.id);
    copy.name = safe(source.name);
    copy.available = safe(source.available);
    copy.description = safe(source.description);
    copy.visitors = safe(source.visitors);
    copy.rules = safe(source.rules);
    return copy;
  }

  private static String locationTranslationKey(String id, String suffix) {
    return "location." + safe(id).trim() + suffix;
  }
}
