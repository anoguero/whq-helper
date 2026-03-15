package pms.whq.swt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

import com.whq.app.i18n.I18n;

import pms.whq.data.Table;

public class TableSettingsDialog {

  private final Shell parent;
  private final List<Table> tables;

  private boolean changesMade;

  public TableSettingsDialog(Shell parent, Map<String, Table> tableMap) {
    this.parent = parent;
    this.tables = new ArrayList<>(tableMap.values());
    Collections.sort(this.tables, Comparator.comparing(Table::getName));
  }

  public boolean open() {
    Shell dialog = new Shell(parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.RESIZE);
    dialog.setText(I18n.t("dialog.tableSettings.title"));
    dialog.setLayout(new GridLayout(1, false));

    org.eclipse.swt.widgets.Table tableWidget =
        new org.eclipse.swt.widgets.Table(dialog, SWT.BORDER | SWT.CHECK | SWT.FULL_SELECTION | SWT.V_SCROLL);
    tableWidget.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    tableWidget.setHeaderVisible(true);
    tableWidget.setLinesVisible(true);

    TableColumn nameColumn = new TableColumn(tableWidget, SWT.LEFT);
    nameColumn.setText(I18n.t("dialog.tableSettings.column"));
    nameColumn.setWidth(600);

    for (Table table : tables) {
      TableItem item = new TableItem(tableWidget, SWT.NONE);
      item.setText(0, table.getName());
      item.setChecked(table.isActive());
      item.setData(table);
    }

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
      for (TableItem item : tableWidget.getItems()) {
        Table table = (Table) item.getData();
        boolean previous = table.isActive();
        boolean next = item.getChecked();
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
}
