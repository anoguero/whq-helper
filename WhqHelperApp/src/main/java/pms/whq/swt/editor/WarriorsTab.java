package pms.whq.swt.editor;

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

import pms.whq.xml.XmlContentService.WarriorEntry;

/** Pestana de guerreros. */
public final class WarriorsTab extends EditorTab {

  public WarriorsTab(EditorContext context) {
    super(context);
  }

  @Override
  public void create(TabFolder tabs, Shell dialog) {
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
}
