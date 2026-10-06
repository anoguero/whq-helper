package pms.whq.swt.editor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import com.whq.app.i18n.EditableContentTranslations;
import com.whq.app.i18n.I18n;

import pms.whq.xml.XmlContentService.EventEntry;
import pms.whq.xml.XmlContentService.LocationEntry;
import pms.whq.xml.XmlContentService.MonsterEntry;
import pms.whq.xml.XmlContentService.RuleEntry;
import pms.whq.xml.XmlContentService.WarriorEntry;

/** Utilidades estaticas compartidas por las pestanas del editor de contenido y por el dialogo. */
public final class EditorSupport {

  private EditorSupport() {
  }

  public static final int HEADER_BUTTON_COLUMNS = 9;

  public static final String WARRIOR_NAME_SUFFIX = ".name";

  public static final String WARRIOR_RACE_SUFFIX = ".race";

  public static final String WARRIOR_RULES_SUFFIX = ".rules";

  public static Text createLabeledText(Composite parent, String label) {
    Label l = new Label(parent, SWT.NONE);
    l.setText(label);
    l.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
    Text text = new Text(parent, SWT.BORDER);
    text.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    return text;
  }

  public static <T> void ensureUniqueId(java.util.List<T> entries, int selectedIndex, String id) {
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

  public static List<String> splitCsv(String raw) {
    List<String> values = new ArrayList<>();
    for (String part : safe(raw).split(",")) {
      String trimmed = part.trim();
      if (!trimmed.isEmpty()) {
        values.add(trimmed);
      }
    }
    return values;
  }

  public static String extractId(Object entry) {
    if (entry instanceof RuleEntry e) {
      return safe(e.id);
    }
    if (entry instanceof EventEntry e) {
      return safe(e.id);
    }
    if (entry instanceof MonsterEntry e) {
      return safe(e.id);
    }
    if (entry instanceof WarriorEntry e) {
      return safe(e.id);
    }
    if (entry instanceof LocationEntry e) {
      return safe(e.id);
    }
    return "";
  }

  public static <T> void selectById(
      org.eclipse.swt.widgets.List listWidget, java.util.List<T> entries, String id) {
    String normalized = safe(id);
    for (int i = 0; i < entries.size(); i++) {
      if (normalized.equals(extractId(entries.get(i)))) {
        listWidget.setSelection(i);
        break;
      }
    }
  }

  public static boolean hasSelectedFile(Combo fileCombo, java.util.List<Path> files, Shell shell) {
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

  public static Path selectedFile(Combo fileCombo, java.util.List<Path> files) {
    return files.get(fileCombo.getSelectionIndex());
  }

  public static List<WarriorEntry> applyWarriorTranslations(
      List<WarriorEntry> entries, EditableContentTranslations translations) {
    List<WarriorEntry> localized = new ArrayList<>();
    for (WarriorEntry entry : entries) {
      WarriorEntry copy = copyWarriorEntry(entry);
      copy.name = translations.t(warriorTranslationKey(copy.id, WARRIOR_NAME_SUFFIX), copy.name);
      copy.race = translations.t(warriorTranslationKey(copy.id, WARRIOR_RACE_SUFFIX), copy.race);
      copy.rules = translations.t(warriorTranslationKey(copy.id, WARRIOR_RULES_SUFFIX), copy.rules);
      localized.add(copy);
    }
    return localized;
  }

  public static WarriorEntry copyWarriorEntry(WarriorEntry source) {
    WarriorEntry copy = new WarriorEntry();
    copy.id = safe(source.id);
    copy.name = safe(source.name);
    copy.race = safe(source.race);
    copy.counter = safe(source.counter);
    copy.rules = safe(source.rules);
    return copy;
  }

  public static String warriorTranslationKey(String id, String suffix) {
    return "warrior." + safe(id).trim() + suffix;
  }

  public static void createAndSelectXmlFile(
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

  public static void refreshFileChoices(Combo fileCombo, List<Path> files, CheckedSupplier<List<Path>> fileSupplier)
      throws Exception {
    files.clear();
    files.addAll(fileSupplier.get());
    fileCombo.removeAll();
    for (Path file : files) {
      fileCombo.add(file.getFileName().toString());
    }
  }

  public static void selectFile(Combo fileCombo, List<Path> files, Path target) {
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

  public static void showError(Shell parent, String message) {
    MessageBox box = new MessageBox(parent, SWT.ICON_ERROR | SWT.OK);
    box.setText("Error");
    box.setMessage(message == null ? "" : message);
    box.open();
  }

  public static void showError(Shell parent, Throwable throwable) {
    if (throwable != null) {
      throwable.printStackTrace();
    }
    showError(parent, throwable == null ? "" : safe(throwable.getMessage()));
  }

  public static void showWarning(Shell parent, String message) {
    MessageBox box = new MessageBox(parent, SWT.ICON_WARNING | SWT.OK);
    box.setText("Warning");
    box.setMessage(message == null ? "" : message);
    box.open();
  }

  public static void showInfo(Shell parent, String message) {
    MessageBox box = new MessageBox(parent, SWT.ICON_INFORMATION | SWT.OK);
    box.setText("Info");
    box.setMessage(message == null ? "" : message);
    box.open();
  }

  public static boolean confirm(Shell parent, String message) {
    MessageBox box = new MessageBox(parent, SWT.ICON_QUESTION | SWT.YES | SWT.NO);
    box.setText("Confirm");
    box.setMessage(message == null ? "" : message);
    return box.open() == SWT.YES;
  }

  public static String safe(String value) {
    return value == null ? "" : value;
  }

  public static String slugify(String value) {
    String normalized = safe(value).trim().toLowerCase(Locale.ROOT).replace("&", "and");
    normalized = normalized.replaceAll("[^a-z0-9]+", "-");
    normalized = normalized.replaceAll("^-+|-+$", "");
    return normalized.isBlank() ? "userdefined-entry" : normalized;
  }

  public static Button createActionButton(Composite parent, String textKey) {
    Button button = new Button(parent, SWT.PUSH);
    button.setText(I18n.t(textKey));
    button.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    return button;
  }

  public static EditorHeader createEditorHeader(Composite parent) {
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

  public record EditorHeader(
      Combo fileCombo,
      Button newFileButton,
      Button newButton,
      Button deleteButton,
      Button saveButton,
      Button reloadButton,
      Button validateButton) {}

  @FunctionalInterface
  public interface CheckedSupplier<T> {
    T get() throws Exception;
  }

  @FunctionalInterface
  public interface CheckedPathFunction<T> {
    T apply(Path value) throws Exception;
  }

  public static final String EVENT_NAME_SUFFIX = ".name";

  public static final String EVENT_FLAVOR_SUFFIX = ".flavor";

  public static final String EVENT_RULES_SUFFIX = ".rules";

  public static final String EVENT_SPECIAL_SUFFIX = ".special";

  public static final String MONSTER_NAME_SUFFIX = ".name";

  public static final String MONSTER_PLURAL_SUFFIX = ".plural";

  public static boolean isTreasureFile(Path file) {
    String name = file == null ? "" : safe(file.getFileName().toString()).toLowerCase();
    return name.contains("treasure");
  }

  public static boolean isTreasureEntry(EventEntry entry, String section) {
    String normalizedId = safe(entry == null ? "" : entry.id).trim().toLowerCase();
    return entry != null
        && entry.treasure
        && normalizedId.contains("-treasure-")
        && normalizedId.contains("-" + safe(section).toLowerCase() + "-");
  }

  public static List<EventEntry> filterVisibleEvents(
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

  public static List<EventEntry> filterEvents(List<EventEntry> entries, Predicate<EventEntry> entryFilter) {
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

  public static List<EventEntry> applyEventTranslations(
      List<EventEntry> entries, EditableContentTranslations translations) {
    List<EventEntry> localized = new ArrayList<>();
    for (EventEntry entry : entries) {
      EventEntry copy = copyEventEntry(entry);
      copy.name = translations.t(eventTranslationKey(copy.id, EVENT_NAME_SUFFIX), copy.name);
      copy.flavor = translations.t(eventTranslationKey(copy.id, EVENT_FLAVOR_SUFFIX), copy.flavor);
      copy.rules = translations.t(eventTranslationKey(copy.id, EVENT_RULES_SUFFIX), copy.rules);
      copy.special = translations.t(eventTranslationKey(copy.id, EVENT_SPECIAL_SUFFIX), copy.special);
      localized.add(copy);
    }
    return localized;
  }

  public static List<MonsterEntry> applyMonsterTranslations(
      List<MonsterEntry> entries, EditableContentTranslations translations) {
    List<MonsterEntry> localized = new ArrayList<>();
    for (MonsterEntry entry : entries) {
      MonsterEntry copy = copyMonsterEntry(entry);
      copy.name = translations.t(monsterTranslationKey(copy.id, MONSTER_NAME_SUFFIX), copy.name);
      copy.plural = translations.t(monsterTranslationKey(copy.id, MONSTER_PLURAL_SUFFIX), copy.plural);
      localized.add(copy);
    }
    return localized;
  }

  public static EventEntry copyEventEntry(EventEntry source) {
    EventEntry copy = new EventEntry();
    copy.id = safe(source.id);
    copy.name = safe(source.name);
    copy.flavor = safe(source.flavor);
    copy.rules = safe(source.rules);
    copy.special = safe(source.special);
    copy.goldValue = safe(source.goldValue);
    copy.users = safe(source.users);
    copy.treasure = source.treasure;
    return copy;
  }

  public static MonsterEntry copyMonsterEntry(MonsterEntry source) {
    MonsterEntry copy = new MonsterEntry();
    copy.id = safe(source.id);
    copy.name = safe(source.name);
    copy.plural = safe(source.plural);
    copy.factions = safe(source.factions);
    copy.wounds = safe(source.wounds);
    copy.move = safe(source.move);
    copy.weaponSkill = safe(source.weaponSkill);
    copy.ballisticSkill = safe(source.ballisticSkill);
    copy.strength = safe(source.strength);
    copy.toughness = safe(source.toughness);
    copy.initiative = safe(source.initiative);
    copy.attacks = safe(source.attacks);
    copy.gold = safe(source.gold);
    copy.armor = safe(source.armor);
    copy.damage = safe(source.damage);
    copy.specialEntriesRaw = safe(source.specialEntriesRaw);
    return copy;
  }

  public static String eventTranslationKey(String id, String suffix) {
    return "event." + safe(id).trim() + suffix;
  }

  public static String monsterTranslationKey(String id, String suffix) {
    return "monster." + safe(id).trim() + suffix;
  }

  public static final double TREASURE_CARD_ASPECT_RATIO = 847d / 1264d;

  public static final String RULE_NAME_SUFFIX = ".name";

  public static final String RULE_TEXT_SUFFIX = ".text";

  public static final String RULE_PARAMETER_NAME_SUFFIX = ".parameterName";

  public static final String RULE_PARAMETER_NAMES_SUFFIX = ".parameterNames";

  public static final String RULE_PARAMETER_FORMAT_SUFFIX = ".parameterFormat";

  public static String selectedRuleId(Combo combo, List<RuleEntry> rules) {
    int index = combo.getSelectionIndex();
    if (index <= 0 || index - 1 >= rules.size()) {
      return "";
    }
    return safe(rules.get(index - 1).id);
  }

  public static RuleEntry applyRuleTranslation(RuleEntry entry, EditableContentTranslations translations) {
    RuleEntry localized = copyRuleEntry(entry);
    localized.name = translations.t(ruleTranslationKey(localized.id, RULE_NAME_SUFFIX), localized.name);
    localized.text = translations.t(ruleTranslationKey(localized.id, RULE_TEXT_SUFFIX), localized.text);
    localized.parameterName =
        translations.t(ruleTranslationKey(localized.id, RULE_PARAMETER_NAME_SUFFIX), localized.parameterName);
    localized.parameterNames =
        translations.t(ruleTranslationKey(localized.id, RULE_PARAMETER_NAMES_SUFFIX), localized.parameterNames);
    localized.parameterFormat =
        translations.t(ruleTranslationKey(localized.id, RULE_PARAMETER_FORMAT_SUFFIX), localized.parameterFormat);
    return localized;
  }

  public static RuleEntry copyRuleEntry(RuleEntry source) {
    RuleEntry copy = new RuleEntry();
    copy.type = safe(source.type);
    copy.id = safe(source.id);
    copy.name = safe(source.name);
    copy.text = safe(source.text);
    copy.parameterName = safe(source.parameterName);
    copy.parameterNames = safe(source.parameterNames);
    copy.parameterFormat = safe(source.parameterFormat);
    return copy;
  }

  public static String ruleTranslationKey(String id, String suffix) {
    return "rule." + safe(id).trim() + suffix;
  }

  public static void layoutTreasurePreview(Composite viewport, Composite previewHost) {
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
}
