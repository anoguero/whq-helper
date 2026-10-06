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
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.Text;

import com.whq.app.i18n.EditableContentTranslations;
import com.whq.app.i18n.I18n;

import pms.whq.xml.XmlContentService.RuleEntry;

/** Pestana de reglas especiales y magia. */
public final class RulesTab extends EditorTab {

  public RulesTab(EditorContext context) {
    super(context);
  }

  @Override
  public void create(TabFolder tabs, Shell dialog) {
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
}
