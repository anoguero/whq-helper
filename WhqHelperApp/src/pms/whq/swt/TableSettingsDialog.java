package pms.whq.swt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.ScrollBar;

import com.whq.app.i18n.I18n;

import pms.whq.data.Table;

public class TableSettingsDialog {

  private final Shell parent;
  private final List<Table> tables;

  private boolean changesMade;

  public TableSettingsDialog(Shell parent, Map<String, Table> tableMap) {
    this.parent = parent;
    this.tables = new ArrayList<>(tableMap.values());
    Collections.sort(this.tables, Comparator
        .comparing((Table table) -> themeOrder(themeOf(table)))
        .thenComparing(Table::getName, String.CASE_INSENSITIVE_ORDER));
  }

  public boolean open() {
    Shell dialog = new Shell(parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.RESIZE);
    dialog.setText(I18n.t("dialog.tableSettings.title"));
    dialog.setLayout(new GridLayout(1, false));

    ScrolledComposite scroll = new ScrolledComposite(dialog, SWT.V_SCROLL | SWT.BORDER);
    scroll.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    scroll.setExpandHorizontal(true);
    scroll.setExpandVertical(true);

    Composite content = new Composite(scroll, SWT.NONE);
    GridLayout contentLayout = new GridLayout(1, false);
    contentLayout.marginWidth = 8;
    contentLayout.marginHeight = 8;
    contentLayout.verticalSpacing = 10;
    content.setLayout(contentLayout);

    Map<Table, Button> checkboxByTable = new java.util.LinkedHashMap<>();
    Map<TableTheme, List<Table>> grouped = new java.util.LinkedHashMap<>();
    for (TableTheme theme : TableTheme.values()) {
      grouped.put(theme, new ArrayList<>());
    }
    for (Table table : tables) {
      grouped.get(themeOf(table)).add(table);
    }

    for (TableTheme theme : TableTheme.values()) {
      List<Table> themedTables = grouped.get(theme);
      if (themedTables == null || themedTables.isEmpty()) {
        continue;
      }

      Group group = new Group(content, SWT.NONE);
      group.setText(I18n.t(theme.labelKey));
      group.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
      GridLayout groupLayout = new GridLayout(1, false);
      groupLayout.marginWidth = 10;
      groupLayout.marginHeight = 10;
      groupLayout.verticalSpacing = 6;
      group.setLayout(groupLayout);

      for (Table table : themedTables) {
        Button checkbox = new Button(group, SWT.CHECK);
        checkbox.setText(table.getName());
        checkbox.setSelection(table.isActive());
        checkbox.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        checkboxByTable.put(table, checkbox);
      }
    }

    scroll.setContent(content);
    content.setSize(content.computeSize(SWT.DEFAULT, SWT.DEFAULT));
    scroll.addListener(SWT.Resize, event -> {
      int width = scroll.getClientArea().width;
      ScrollBar verticalBar = scroll.getVerticalBar();
      if (verticalBar != null) {
        width = Math.max(0, width - verticalBar.getSize().x);
      }
      content.setSize(content.computeSize(Math.max(0, width), SWT.DEFAULT));
    });

    Composite buttonRow = new Composite(dialog, SWT.NONE);
    buttonRow.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false));
    buttonRow.setLayout(new GridLayout(2, true));

    Button cancel = new Button(buttonRow, SWT.PUSH);
    cancel.setText(I18n.t("button.cancel"));
    cancel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    cancel.addListener(SWT.Selection, event -> dialog.close());

    Button ok = new Button(buttonRow, SWT.PUSH);
    ok.setText(I18n.t("button.ok"));
    ok.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    ok.addListener(SWT.Selection, event -> {
      changesMade = false;
      for (Map.Entry<Table, Button> entry : checkboxByTable.entrySet()) {
        Table table = entry.getKey();
        boolean previous = table.isActive();
        boolean next = entry.getValue().getSelection();
        if (previous != next) {
          changesMade = true;
        }
        table.setActive(next);
      }
      dialog.close();
    });

    dialog.setDefaultButton(ok);
    dialog.setSize(760, 520);
    dialog.setLocation(centerOverParent(parent, dialog));
    dialog.open();

    Display display = parent.getDisplay();
    while (!dialog.isDisposed()) {
      if (!display.readAndDispatch()) {
        display.sleep();
      }
    }

    return changesMade;
  }

  private static org.eclipse.swt.graphics.Point centerOverParent(Shell parent, Shell dialog) {
    org.eclipse.swt.graphics.Rectangle parentBounds = parent.getBounds();
    org.eclipse.swt.graphics.Point size = dialog.getSize();
    int x = parentBounds.x + ((parentBounds.width - size.x) / 2);
    int y = parentBounds.y + ((parentBounds.height - size.y) / 2);
    return new org.eclipse.swt.graphics.Point(Math.max(0, x), Math.max(0, y));
  }

  private static TableTheme themeOf(Table table) {
    String lowerName = table.getName().toLowerCase();
    if ("settlement".equalsIgnoreCase(table.getKind())) {
      return TableTheme.SETTLEMENT;
    }
    if ("travel".equalsIgnoreCase(table.getKind())) {
      return TableTheme.TRAVEL;
    }
    if ("treasure".equalsIgnoreCase(table.getKind())) {
      return lowerName.contains("objective") || lowerName.contains("objetive")
          ? TableTheme.OBJECTIVE_TREASURE
          : TableTheme.TREASURE;
    }
    if (!table.getMonsterEntries().isEmpty()) {
      return TableTheme.MONSTERS;
    }
    return TableTheme.EVENTS;
  }

  private static int themeOrder(TableTheme theme) {
    return switch (theme) {
      case EVENTS -> 0;
      case MONSTERS -> 1;
      case SETTLEMENT -> 2;
      case TRAVEL -> 3;
      case TREASURE -> 4;
      case OBJECTIVE_TREASURE -> 5;
    };
  }

  private enum TableTheme {
    EVENTS("dialog.tableSettings.group.events"),
    MONSTERS("dialog.tableSettings.group.monsters"),
    SETTLEMENT("dialog.tableSettings.group.settlement"),
    TRAVEL("dialog.tableSettings.group.travel"),
    TREASURE("dialog.tableSettings.group.treasure"),
    OBJECTIVE_TREASURE("dialog.tableSettings.group.objectiveTreasure");

    private final String labelKey;

    TableTheme(String labelKey) {
      this.labelKey = labelKey;
    }
  }
}
