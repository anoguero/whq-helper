package pms.whq.swt;

import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import com.whq.app.i18n.I18n;

public final class SwtDialogs {

  private SwtDialogs() {
  }

  public static boolean confirmYesNo(Shell parent, String title, String message) {
    MessageBox box = new MessageBox(parent, SWT.ICON_QUESTION | SWT.YES | SWT.NO);
    box.setText(title);
    box.setMessage(message);
    return box.open() == SWT.YES;
  }

  public static void showWarning(Shell parent, String title, String message) {
    MessageBox box = new MessageBox(parent, SWT.ICON_WARNING | SWT.OK);
    box.setText(title);
    box.setMessage(message);
    box.open();
  }

  public static String promptForText(Shell parent, String title, String message, String initialValue) {
    Shell dialog = new Shell(parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
    dialog.setText(title);
    dialog.setLayout(new GridLayout(1, false));

    Label label = new Label(dialog, SWT.WRAP);
    label.setText(message);
    label.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

    Text text = new Text(dialog, SWT.BORDER);
    text.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    if (initialValue != null) {
      text.setText(initialValue);
      text.selectAll();
    }

    Composite buttonRow = new Composite(dialog, SWT.NONE);
    buttonRow.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false));
    buttonRow.setLayout(new GridLayout(2, true));

    AtomicReference<String> result = new AtomicReference<>(null);

    Button ok = new Button(buttonRow, SWT.PUSH);
    ok.setText(I18n.t("button.ok"));
    ok.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    ok.addListener(SWT.Selection, event -> {
      result.set(text.getText());
      dialog.close();
    });

    Button cancel = new Button(buttonRow, SWT.PUSH);
    cancel.setText(I18n.t("button.cancel"));
    cancel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    cancel.addListener(SWT.Selection, event -> dialog.close());

    dialog.setDefaultButton(ok);
    dialog.pack();
    dialog.setSize(Math.max(420, dialog.getSize().x), dialog.getSize().y);
    dialog.setLocation(centerOverParent(parent, dialog));
    dialog.open();

    Display display = parent.getDisplay();
    while (!dialog.isDisposed()) {
      if (!display.readAndDispatch()) {
        display.sleep();
      }
    }

    return result.get();
  }

  private static org.eclipse.swt.graphics.Point centerOverParent(Shell parent, Shell dialog) {
    org.eclipse.swt.graphics.Rectangle parentBounds = parent.getBounds();
    org.eclipse.swt.graphics.Point size = dialog.getSize();
    int x = parentBounds.x + ((parentBounds.width - size.x) / 2);
    int y = parentBounds.y + ((parentBounds.height - size.y) / 2);
    return new org.eclipse.swt.graphics.Point(Math.max(0, x), Math.max(0, y));
  }
}
