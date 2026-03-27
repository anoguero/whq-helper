package pms.whq.swt;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

import com.whq.app.i18n.I18n;
import com.whq.app.ui.AppIcon;

public final class AboutDialog {

  private AboutDialog() {
  }

  public static void open(Shell parent, Image image) {
    Shell dialog = new Shell(parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
    AppIcon.inherit(dialog, parent);
    dialog.setText(I18n.t("dialog.about.title"));
    dialog.setLayout(new GridLayout(1, false));

    if (image != null) {
      Label imageLabel = new Label(dialog, SWT.CENTER);
      imageLabel.setImage(image);
      imageLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false));
    }

    Label title = new Label(dialog, SWT.CENTER);
    title.setText(I18n.t("dialog.about.version"));
    title.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false));

    Label subtitle = new Label(dialog, SWT.CENTER);
    subtitle.setText("\u00A9 2005, Paul Siegel");
    subtitle.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false));

    Label disclaimer = new Label(dialog, SWT.CENTER | SWT.WRAP);
    disclaimer.setText(
        "Warhammer Quest is a trademark of Games Workshop Limited.\n"
            + "Text and Images displayed by this application are the\n"
            + "property of Games Workshop Limited unless stated otherwise.");
    disclaimer.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false));

    Button ok = new Button(dialog, SWT.PUSH);
    ok.setText(I18n.t("button.ok"));
    ok.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false));
    ok.addListener(SWT.Selection, event -> dialog.close());

    dialog.setDefaultButton(ok);
    dialog.pack();
    dialog.setLocation(centerOverParent(parent, dialog));
    dialog.open();

    Display display = parent.getDisplay();
    while (!dialog.isDisposed()) {
      if (!display.readAndDispatch()) {
        display.sleep();
      }
    }
  }

  private static org.eclipse.swt.graphics.Point centerOverParent(Shell parent, Shell dialog) {
    org.eclipse.swt.graphics.Rectangle parentBounds = parent.getBounds();
    org.eclipse.swt.graphics.Point size = dialog.getSize();
    int x = parentBounds.x + ((parentBounds.width - size.x) / 2);
    int y = parentBounds.y + ((parentBounds.height - size.y) / 2);
    return new org.eclipse.swt.graphics.Point(Math.max(0, x), Math.max(0, y));
  }
}
