package pms.whq.swt;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

import com.whq.app.i18n.I18n;

import pms.whq.Settings;
import pms.whq.data.Rule;

public class RuleDialog {

  private final Shell parent;
  private Shell shell;
  private Label title;
  private StyledText text;

  public RuleDialog(Shell parent) {
    this.parent = parent;
  }

  public void showRule(Rule rule) {
    if (rule == null) {
      return;
    }

    if (shell == null || shell.isDisposed()) {
      createShell();
    }

    String titleText = rule.name;
    if ("magic".equals(rule.type)) {
      titleText += " Magic";
    }

    shell.setText(titleText);
    title.setText(titleText);
    text.setText(rule.text == null ? "" : rule.text);
    text.setTopIndex(0);

    int w = Settings.getSettingAsInt(Settings.CARD_HEIGHT);
    int h = Settings.getSettingAsInt(Settings.CARD_WIDTH);
    shell.setSize(Math.max(320, w), Math.max(240, h));
    shell.open();
    shell.forceActive();
  }

  public void dispose() {
    if (shell != null && !shell.isDisposed()) {
      shell.dispose();
    }
  }

  private void createShell() {
    shell = new Shell(parent, SWT.DIALOG_TRIM | SWT.RESIZE);
    shell.setLayout(new GridLayout(1, false));

    title = new Label(shell, SWT.WRAP);
    title.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

    text = new StyledText(shell, SWT.BORDER | SWT.WRAP | SWT.V_SCROLL | SWT.READ_ONLY);
    text.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

    Button ok = new Button(shell, SWT.PUSH);
    ok.setText(I18n.t("dialog.rule.ok"));
    ok.setLayoutData(new GridData(SWT.END, SWT.CENTER, false, false));
    ok.addListener(SWT.Selection, event -> shell.setVisible(false));
    shell.setDefaultButton(ok);

    shell.setSize(420, 340);
    shell.setLocation(centerOverParent(parent, shell));
  }

  private static org.eclipse.swt.graphics.Point centerOverParent(Shell parent, Shell dialog) {
    org.eclipse.swt.graphics.Rectangle parentBounds = parent.getBounds();
    org.eclipse.swt.graphics.Point size = dialog.getSize();
    int x = parentBounds.x + ((parentBounds.width - size.x) / 2);
    int y = parentBounds.y + ((parentBounds.height - size.y) / 2);
    return new org.eclipse.swt.graphics.Point(Math.max(0, x), Math.max(0, y));
  }
}
