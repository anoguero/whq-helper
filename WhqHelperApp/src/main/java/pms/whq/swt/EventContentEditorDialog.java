package pms.whq.swt;

import static pms.whq.swt.editor.EditorSupport.*;

import java.nio.file.Path;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;

import com.whq.app.adventure.XmlObjectiveRoomAdventureRepository;
import com.whq.app.i18n.I18n;
import com.whq.app.storage.XmlDungeonCardStore;
import com.whq.app.ui.AppIcon;

import pms.whq.swt.editor.DungeonCardsTab;
import pms.whq.swt.editor.EditorContext;
import pms.whq.swt.editor.EventLikeTabs;
import pms.whq.swt.editor.LocationsTab;
import pms.whq.swt.editor.MonstersTab;
import pms.whq.swt.editor.ObjectiveRoomAdventuresTab;
import pms.whq.swt.editor.RulesTab;
import pms.whq.swt.editor.TablesTab;
import pms.whq.swt.editor.WarriorsTab;
import pms.whq.xml.XmlContentService;

public final class EventContentEditorDialog {
  private final Shell parent;
  private final Path projectRoot;
  private final XmlContentService service;
  private final XmlDungeonCardStore dungeonCardStore;
  private final XmlObjectiveRoomAdventureRepository objectiveRoomAdventureRepository;
  private final Runnable onContentSaved;

  public EventContentEditorDialog(Shell parent, Path projectRoot, Runnable onContentSaved) {
    this.parent = parent;
    this.projectRoot = projectRoot.toAbsolutePath().normalize();
    this.service = new XmlContentService(this.projectRoot);
    this.dungeonCardStore = new XmlDungeonCardStore(this.projectRoot);
    this.objectiveRoomAdventureRepository = new XmlObjectiveRoomAdventureRepository(this.projectRoot);
    this.onContentSaved = onContentSaved;
  }

  public void open() {
    Shell dialog =
        new Shell(parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.RESIZE | SWT.MAX);
    AppIcon.inherit(dialog, parent);
    dialog.setText(I18n.t("dialog.contentEditor.title"));
    dialog.setLayout(new GridLayout(1, false));
    dialog.setSize(1440, 920);
    dialog.setMaximized(true);

    SashForm layout = new SashForm(dialog, SWT.HORIZONTAL);
    layout.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

    Tree navigationTree = new Tree(layout, SWT.BORDER | SWT.SINGLE);
    TabFolder tabs = new TabFolder(layout, SWT.NONE);
    layout.setWeights(new int[] {24, 76});
    EditorContext context =
        new EditorContext(
            parent,
            projectRoot,
            service,
            dungeonCardStore,
            objectiveRoomAdventureRepository,
            onContentSaved);

    new DungeonCardsTab(context).create(tabs, dialog);
    new RulesTab(context).create(tabs, dialog);
    new EventLikeTabs(context).create(tabs, dialog);
    new TablesTab(context).create(tabs, dialog);
    new MonstersTab(context).create(tabs, dialog);
    new WarriorsTab(context).create(tabs, dialog);
    new LocationsTab(context).create(tabs, dialog);
    new ObjectiveRoomAdventuresTab(context).create(tabs, dialog);

    for (TabItem item : tabs.getItems()) {
      TreeItem treeItem = new TreeItem(navigationTree, SWT.NONE);
      treeItem.setText(item.getText());
      treeItem.setData("tabItem", item);
    }
    if (navigationTree.getItemCount() > 0) {
      navigationTree.setSelection(navigationTree.getItem(0));
      tabs.setSelection(0);
    }
    navigationTree.addListener(
        SWT.Selection,
        event -> {
          if (!(event.item instanceof TreeItem treeItem)) {
            return;
          }
          Object tabData = treeItem.getData("tabItem");
          if (tabData instanceof TabItem tabItem) {
            tabs.setSelection(tabItem);
          }
        });
    tabs.addListener(
        SWT.Selection,
        event -> {
          int index = tabs.getSelectionIndex();
          if (index >= 0 && index < navigationTree.getItemCount()) {
            navigationTree.setSelection(navigationTree.getItem(index));
          }
        });

    Composite actions = createActionRow(dialog, 1);

    Button closeButton = createActionButton(actions, "button.close");
    closeButton.addListener(SWT.Selection, event -> dialog.close());

    dialog.open();
    Display display = parent.getDisplay();
    while (!dialog.isDisposed()) {
      if (!display.readAndDispatch()) {
        display.sleep();
      }
    }
  }

}
