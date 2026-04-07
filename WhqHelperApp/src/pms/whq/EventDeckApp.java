package pms.whq;

import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import java.nio.file.Files;

import javax.xml.parsers.DocumentBuilderFactory;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.whq.app.i18n.EditableContentTranslations;
import com.whq.app.i18n.I18n;
import com.whq.app.ui.AppIcon;
import com.whq.app.ui.WhqUiTheme;

import pms.whq.content.ContentRepository;
import pms.whq.data.EventList;
import pms.whq.data.Table;
import pms.whq.presenter.DeckType;
import pms.whq.presenter.DeckWindowController;
import pms.whq.state.AppState;
import pms.whq.state.AdventureAmbience;
import pms.whq.state.DeckMode;
import pms.whq.swt.AboutDialog;
import pms.whq.swt.CardWindowManager;
import pms.whq.swt.SwtDialogs;
import pms.whq.swt.TableSettingsDialog;

public class EventDeckApp {

  private static final int DECK_BUTTON_WIDTH = 232;
  private static final int DECK_BUTTON_HEIGHT = 318;
  private static final int DECK_IMAGE_PADDING = 8;

  private final Display display;
  private final WhqUiTheme theme;
  private final DeckWindowController controller;
  private final CardWindowManager cardWindowManager;
  private final Map<String, Image> previewImages;
  private final Path projectRoot;

  private Shell shell;
  private Composite buttonRow;
  private Canvas headerCanvas;
  private Composite eventDeckContainer;
  private Composite settlementDeckContainer;
  private Composite travelDeckContainer;
  private Composite treasureDeckContainer;
  private Composite objectiveTreasureDeckContainer;
  private Button eventButton;
  private Button settlementButton;
  private Button travelButton;
  private Button treasureButton;
  private Button objectiveTreasureButton;
  private Button showEventDeckToggle;
  private Button showSettlementDeckToggle;
  private Button showTravelDeckToggle;
  private Button showTreasureDeckToggle;
  private Button showObjectiveTreasureDeckToggle;
  private Button closeAllCardsButton;

  private Label eventDeckTitleLabel;
  private Label eventDeckSubtitleLabel;
  private Label settlementDeckTitleLabel;
  private Label settlementDeckSubtitleLabel;
  private Label travelDeckTitleLabel;
  private Label travelDeckSubtitleLabel;
  private Label treasureDeckTitleLabel;
  private Label treasureDeckSubtitleLabel;
  private Label objectiveTreasureDeckTitleLabel;
  private Label objectiveTreasureDeckSubtitleLabel;

  private EventList eventList;
  private EventList travelEventList;
  private EventList settlementEventList;
  private EventList treasureEventList;
  private EventList objectiveTreasureEventList;

  private record WarriorDefinition(
      String id,
      String name,
      String race,
      String counterPath,
      String rulesPath) {
    String displayName() {
      if (race == null || race.isBlank()) {
        return name;
      }
      return name + " (" + race + ")";
    }
  }

  /*public static void main(String[] args) {
    Display display = new Display();
    EventDeckApp app = new EventDeckApp(display, Path.of("").toAbsolutePath());
    app.open();

    while (!app.isDisposed()) {
      if (!display.readAndDispatch()) {
        display.sleep();
      }
    }

    display.dispose();
  }*/

  public EventDeckApp(Display display) {
    this(display, Path.of("").toAbsolutePath());
  }

  public EventDeckApp(Display display, Path projectRoot) {
    this.display = display;
    Path normalizedProjectRoot = projectRoot.toAbsolutePath().normalize();
    this.projectRoot = normalizedProjectRoot;
    this.theme = new WhqUiTheme(display, normalizedProjectRoot);
    this.cardWindowManager = new CardWindowManager(display);
    this.previewImages = new TreeMap<>();

    Settings.load(normalizedProjectRoot);
    AppState appState = AppState.loadFromSettings();
    I18n.setLanguage(appState.language());
    this.controller = new DeckWindowController(normalizedProjectRoot, appState.deckMode().isDeck());
    layoutMainWindow();

    reloadControllerContent();
    resetEventList();
  }

  public void open() {
    if (shell != null && !shell.isDisposed()) {
      shell.open();
      shell.forceActive();
    }
  }

  public void focus() {
    if (shell != null && !shell.isDisposed()) {
      shell.forceActive();
    }
  }

  public boolean isDisposed() {
    return shell == null || shell.isDisposed();
  }

  private void layoutMainWindow() {
    shell = new Shell(display);
    AppIcon.apply(shell, projectRoot);
    shell.setText(I18n.t("event.window.title"));
    shell.setBackground(theme.shellBackground);
    shell.setLayout(new GridLayout(1, false));

    createHeaderBanner();
    createDeckVisibilityControls();

    buttonRow = new Composite(shell, SWT.DOUBLE_BUFFERED);
    buttonRow.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    buttonRow.setBackground(theme.panelBackground);
    GridLayout rowLayout = new GridLayout(5, true);
    rowLayout.marginTop = 16;
    rowLayout.marginBottom = 16;
    rowLayout.marginLeft = 16;
    rowLayout.marginRight = 16;
    rowLayout.horizontalSpacing = 16;
    buttonRow.setLayout(rowLayout);
    buttonRow.addPaintListener(event -> theme.paintDarkPanel(event.gc, buttonRow.getClientArea()));

    String imgDir = Settings.getSetting(Settings.IMG_DIR);

    eventDeckContainer =
        createDeckContainer(
            buttonRow,
            I18n.t("deck.events.title"),
            I18n.t("deck.events.subtitle"));
    eventButton =
        createDeckButton(eventDeckContainer, imgDir + "eventback.png", I18n.t("button.clickHere"), this::generateNextEvent);

    settlementDeckContainer =
        createDeckContainer(
            buttonRow,
            I18n.t("deck.settlement.title"),
            I18n.t("deck.settlement.subtitle"));
    settlementButton =
        createDeckButton(
            settlementDeckContainer, imgDir + "settlement.png", I18n.t("button.clickHere"), this::generateSettlementEvent);

    travelDeckContainer =
        createDeckContainer(
            buttonRow,
            I18n.t("deck.travel.title"),
            I18n.t("deck.travel.subtitle"));
    travelButton =
        createDeckButton(travelDeckContainer, imgDir + "travel.png", I18n.t("button.clickHere"), this::generateTravelEvent);

    treasureDeckContainer =
        createDeckContainer(
            buttonRow,
            I18n.t("deck.treasure.title"),
            I18n.t("deck.treasure.subtitle"));
    treasureButton =
        createDeckButton(
            treasureDeckContainer,
            imgDir + "treasureback.png",
            I18n.t("button.clickHere"),
            this::generateTreasureEvent);

    objectiveTreasureDeckContainer =
        createDeckContainer(
            buttonRow,
            I18n.t("deck.objectiveTreasure.title"),
            I18n.t("deck.objectiveTreasure.subtitle"));
    objectiveTreasureButton =
        createDeckButton(
            objectiveTreasureDeckContainer,
            imgDir + "objective-treasureback.png",
            I18n.t("button.clickHere"),
            this::generateObjectiveTreasureEvent);

    applyDeckVisibility();

    shell.addListener(SWT.Close, event -> {
      persistOptions();
      cardWindowManager.closeAllCards();
      cardWindowManager.disposeImages();
      disposePreviewImages();
      theme.dispose();
    });

    shell.pack();
    shell.setSize(Math.max(shell.getSize().x, 1240), Math.max(shell.getSize().y, 420));
    centerOnPrimaryMonitor(shell);
    refreshTexts();
  }

  private void createDeckVisibilityControls() {
    Composite visibilityRow = new Composite(shell, SWT.DOUBLE_BUFFERED);
    visibilityRow.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    visibilityRow.setBackground(theme.panelBackground);

    GridLayout visibilityLayout = new GridLayout(2, false);
    visibilityLayout.marginTop = 0;
    visibilityLayout.marginBottom = 4;
    visibilityLayout.marginLeft = 18;
    visibilityLayout.marginRight = 18;
    visibilityLayout.horizontalSpacing = 18;
    visibilityRow.setLayout(visibilityLayout);
    visibilityRow.addPaintListener(event -> theme.paintDarkPanel(event.gc, visibilityRow.getClientArea()));

    Composite toggleGroup = new Composite(visibilityRow, SWT.NONE);
    toggleGroup.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    toggleGroup.setBackground(theme.panelBackground);
    GridLayout toggleLayout = new GridLayout(5, false);
    toggleLayout.marginWidth = 0;
    toggleLayout.marginHeight = 0;
    toggleLayout.horizontalSpacing = 14;
    toggleGroup.setLayout(toggleLayout);

    showEventDeckToggle = new Button(toggleGroup, SWT.CHECK);
    styleToggle(showEventDeckToggle);
    AppState appState = AppState.loadFromSettings();
    showEventDeckToggle.setSelection(appState.showEventDeck());

    showSettlementDeckToggle = new Button(toggleGroup, SWT.CHECK);
    styleToggle(showSettlementDeckToggle);
    showSettlementDeckToggle.setSelection(appState.showSettlementDeck());

    showTravelDeckToggle = new Button(toggleGroup, SWT.CHECK);
    styleToggle(showTravelDeckToggle);
    showTravelDeckToggle.setSelection(appState.showTravelDeck());

    showTreasureDeckToggle = new Button(toggleGroup, SWT.CHECK);
    styleToggle(showTreasureDeckToggle);
    showTreasureDeckToggle.setSelection(appState.showTreasureDeck());

    showObjectiveTreasureDeckToggle = new Button(toggleGroup, SWT.CHECK);
    styleToggle(showObjectiveTreasureDeckToggle);
    showObjectiveTreasureDeckToggle.setSelection(appState.showObjectiveTreasureDeck());

    closeAllCardsButton = new Button(visibilityRow, SWT.PUSH);
    closeAllCardsButton.setLayoutData(new GridData(SWT.END, SWT.CENTER, false, false));
    closeAllCardsButton.setBackground(theme.panelBackgroundAlt);
    closeAllCardsButton.setForeground(theme.mist);
    closeAllCardsButton.setFont(theme.bodyFont);
    closeAllCardsButton.addListener(SWT.Selection, event -> cardWindowManager.closeAllCards());

    showEventDeckToggle.addListener(SWT.Selection, event -> {
      applyDeckVisibility();
      persistDeckVisibility();
    });
    showSettlementDeckToggle.addListener(SWT.Selection, event -> {
      applyDeckVisibility();
      persistDeckVisibility();
    });
    showTravelDeckToggle.addListener(SWT.Selection, event -> {
      applyDeckVisibility();
      persistDeckVisibility();
    });
    showTreasureDeckToggle.addListener(SWT.Selection, event -> {
      applyDeckVisibility();
      persistDeckVisibility();
    });
    showObjectiveTreasureDeckToggle.addListener(SWT.Selection, event -> {
      applyDeckVisibility();
      persistDeckVisibility();
    });
  }

  private void createHeaderBanner() {
    headerCanvas = new Canvas(shell, SWT.DOUBLE_BUFFERED);
    GridData headerData = new GridData(SWT.FILL, SWT.TOP, true, false);
    headerData.heightHint = 170;
    headerCanvas.setLayoutData(headerData);
    headerCanvas.addPaintListener(
        event -> {
          Rectangle area = headerCanvas.getClientArea();
          theme.paintHeroBanner(event.gc, area);
          event.gc.setForeground(theme.mist);
          event.gc.setFont(theme.heroTitleFont);
          event.gc.drawText(I18n.t("event.window.title"), area.x + 28, area.y + 28, true);
          event.gc.setForeground(theme.parchment);
          event.gc.setFont(theme.bodyFont);
          event.gc.drawText(I18n.t("deck.window.subtitle"), area.x + 32, area.y + 76, true);
        });
  }

  private void styleToggle(Button toggle) {
    toggle.setBackground(theme.panelBackground);
    toggle.setForeground(theme.parchment);
    toggle.setFont(theme.bodyFont);
  }

  private Composite createDeckContainer(Composite parent, String title, String subtitle) {
    Composite container = new Composite(parent, SWT.DOUBLE_BUFFERED);
    container.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
    container.setBackground(theme.panelBackgroundAlt);
    GridLayout layout = new GridLayout(1, false);
    layout.marginWidth = 14;
    layout.marginHeight = 14;
    layout.verticalSpacing = 8;
    container.setLayout(layout);
    container.addPaintListener(event -> theme.paintDarkPanel(event.gc, container.getClientArea()));

    Label titleLabel = new Label(container, SWT.CENTER);
    titleLabel.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
    titleLabel.setBackground(theme.panelBackgroundAlt);
    titleLabel.setForeground(theme.mist);
    titleLabel.setFont(theme.sectionTitleFont);
    titleLabel.setText(title);

    Label subtitleLabel = new Label(container, SWT.CENTER | SWT.WRAP);
    subtitleLabel.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
    subtitleLabel.setBackground(theme.panelBackgroundAlt);
    subtitleLabel.setForeground(theme.parchment);
    subtitleLabel.setFont(theme.bodyFont);
    subtitleLabel.setText(subtitle);

    if (eventDeckTitleLabel == null) {
      eventDeckTitleLabel = titleLabel;
      eventDeckSubtitleLabel = subtitleLabel;
    } else if (settlementDeckTitleLabel == null) {
      settlementDeckTitleLabel = titleLabel;
      settlementDeckSubtitleLabel = subtitleLabel;
    } else if (travelDeckTitleLabel == null) {
      travelDeckTitleLabel = titleLabel;
      travelDeckSubtitleLabel = subtitleLabel;
    } else if (treasureDeckTitleLabel == null) {
      treasureDeckTitleLabel = titleLabel;
      treasureDeckSubtitleLabel = subtitleLabel;
    } else {
      objectiveTreasureDeckTitleLabel = titleLabel;
      objectiveTreasureDeckSubtitleLabel = subtitleLabel;
    }

    return container;
  }

  private void applyDeckVisibility() {
    updateDeckVisibility(eventDeckContainer, showEventDeckToggle == null || showEventDeckToggle.getSelection());
    updateDeckVisibility(
        settlementDeckContainer, showSettlementDeckToggle == null || showSettlementDeckToggle.getSelection());
    updateDeckVisibility(travelDeckContainer, showTravelDeckToggle == null || showTravelDeckToggle.getSelection());
    updateDeckVisibility(treasureDeckContainer, showTreasureDeckToggle == null || showTreasureDeckToggle.getSelection());
    updateDeckVisibility(
        objectiveTreasureDeckContainer,
        showObjectiveTreasureDeckToggle == null || showObjectiveTreasureDeckToggle.getSelection());

    if (buttonRow != null && !buttonRow.isDisposed()) {
      buttonRow.layout(true, true);
    }
    if (shell != null && !shell.isDisposed()) {
      shell.layout(true, true);
    }
  }

  private void updateDeckVisibility(Composite container, boolean visible) {
    if (container == null || container.isDisposed()) {
      return;
    }

    Object layoutData = container.getLayoutData();
    if (layoutData instanceof GridData gridData) {
      gridData.exclude = !visible;
    }
    container.setVisible(visible);
  }

  private Button createDeckButton(
      Composite parent,
      String imagePath,
      String fallbackLabel,
      Runnable action) {
    Button button = new Button(parent, SWT.PUSH);
    button.setLayoutData(new GridData(SWT.CENTER, SWT.TOP, true, false));
    button.setBackground(theme.panelBackgroundAlt);

    Image image = loadDeckPreviewImage(imagePath, DECK_BUTTON_WIDTH, DECK_BUTTON_HEIGHT);
    if (image != null) {
      button.setImage(image);
      GridData data = (GridData) button.getLayoutData();
      data.widthHint = DECK_BUTTON_WIDTH;
      data.heightHint = DECK_BUTTON_HEIGHT;
    } else {
      button.setText(fallbackLabel);
      button.setFont(theme.bodyFont);
      button.setForeground(theme.ink);
      GridData data = (GridData) button.getLayoutData();
      data.widthHint = DECK_BUTTON_WIDTH;
      data.heightHint = DECK_BUTTON_HEIGHT;
    }

    button.addListener(SWT.Selection, event -> action.run());
    return button;
  }

  private Image loadDeckPreviewImage(String path, int targetWidth, int targetHeight) {
    Image source = cardWindowManager.loadImage(path);
    if (source == null) {
      return null;
    }

    String cacheKey = path + "#deck-preview:" + targetWidth + "x" + targetHeight;
    Image cached = previewImages.get(cacheKey);
    if (cached != null && !cached.isDisposed()) {
      return cached;
    }

    int maxWidth = Math.max(1, targetWidth - (DECK_IMAGE_PADDING * 2));
    int maxHeight = Math.max(1, targetHeight - (DECK_IMAGE_PADDING * 2));
    Rectangle sourceBounds = source.getBounds();
    double scale = Math.min((double) maxWidth / sourceBounds.width, (double) maxHeight / sourceBounds.height);
    int drawWidth = Math.max(1, (int) Math.round(sourceBounds.width * scale));
    int drawHeight = Math.max(1, (int) Math.round(sourceBounds.height * scale));
    int drawX = (targetWidth - drawWidth) / 2;
    int drawY = (targetHeight - drawHeight) / 2;

    Image preview = new Image(display, targetWidth, targetHeight);
    GC gc = new GC(preview);
    try {
      gc.setAdvanced(true);
      gc.setAntialias(SWT.ON);
      gc.setInterpolation(SWT.HIGH);
      gc.setBackground(theme.panelBackgroundAlt);
      gc.fillRectangle(0, 0, targetWidth, targetHeight);
      gc.drawImage(source, 0, 0, sourceBounds.width, sourceBounds.height, drawX, drawY, drawWidth, drawHeight);
    } finally {
      gc.dispose();
    }

    previewImages.put(cacheKey, preview);
    return preview;
  }

  private void generateNextEvent() {
    generateFromList(eventList);
  }

  private void generateTravelEvent() {
    generateFromList(travelEventList);
  }

  private void generateSettlementEvent() {
    generateFromList(settlementEventList);
  }

  private void generateTreasureEvent() {
    generateFromList(treasureEventList);
  }

  private void generateObjectiveTreasureEvent() {
    generateFromList(objectiveTreasureEventList);
  }

  private void generateFromList(EventList list) {
    if (list.size() < 1) {
      boolean activate =
          SwtDialogs.confirmYesNo(
              shell,
              "Deck Has No Active Entries",
              "It appears that this deck has no active entries. Would you like to activate some tables now?");
      if (activate) {
        openTableSettings(shell);
      }
    }

    if (list.size() > 0) {
      Object entry = list.getEntry();
      if (entry != null) {
        cardWindowManager.resetCascadeStart();
        cardWindowManager.showCard(shell, entry, controller.contentRepository());
      } else {
        System.err.println("Null entry found.");
      }
    }
  }

  public void openTableSettings(Shell parent) {
    TableSettingsDialog dialog = new TableSettingsDialog(resolveParent(parent), controller.contentRepository().tables());
    if (dialog.open()) {
      resetEventList();
      persistOptions();
    }
  }

  public void openPartySettings(Shell parent) {
    Shell owner = resolveParent(parent);
    List<WarriorDefinition> warriors;
    try {
      warriors = loadWarriors();
    } catch (Exception ex) {
      SwtDialogs.showWarning(
          owner,
          I18n.t("dialog.party.title"),
          I18n.t("dialog.party.error.loadWarriors") + ex.getMessage());
      return;
    }

    Shell dialog = new Shell(owner, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.RESIZE);
    AppIcon.inherit(dialog, owner);
    dialog.setText(I18n.t("dialog.party.title"));
    dialog.setLayout(new GridLayout(2, false));

    Label descriptionLabel = new Label(dialog, SWT.WRAP);
    descriptionLabel.setText(I18n.t("dialog.party.description"));
    GridData descriptionData = new GridData(SWT.FILL, SWT.CENTER, true, false);
    descriptionData.horizontalSpan = 2;
    descriptionData.widthHint = 520;
    descriptionLabel.setLayoutData(descriptionData);

    new Label(dialog, SWT.NONE).setText(I18n.t("dialog.party.availableWarrior"));
    org.eclipse.swt.widgets.Combo warriorCombo =
        new org.eclipse.swt.widgets.Combo(dialog, SWT.DROP_DOWN | SWT.READ_ONLY);
    warriorCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    for (WarriorDefinition warrior : warriors) {
      warriorCombo.add(warrior.displayName());
    }
    if (!warriors.isEmpty()) {
      warriorCombo.select(0);
    }

    Composite selectionActions = new Composite(dialog, SWT.NONE);
    GridData selectionActionsData = new GridData(SWT.FILL, SWT.CENTER, true, false);
    selectionActionsData.horizontalSpan = 2;
    selectionActions.setLayoutData(selectionActionsData);
    selectionActions.setLayout(new GridLayout(2, false));

    Button addWarriorButton = new Button(selectionActions, SWT.PUSH);
    addWarriorButton.setText("+");
    addWarriorButton.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));

    Button removeWarriorButton = new Button(selectionActions, SWT.PUSH);
    removeWarriorButton.setText("-");
    removeWarriorButton.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));

    Label selectedWarriorsLabel = new Label(dialog, SWT.NONE);
    selectedWarriorsLabel.setText(I18n.t("dialog.party.selectedWarriors"));
    GridData selectedWarriorsLabelData = new GridData(SWT.FILL, SWT.CENTER, true, false);
    selectedWarriorsLabelData.horizontalSpan = 2;
    selectedWarriorsLabel.setLayoutData(selectedWarriorsLabelData);

    org.eclipse.swt.widgets.List selectedWarriorsList =
        new org.eclipse.swt.widgets.List(dialog, SWT.BORDER | SWT.V_SCROLL);
    GridData selectedWarriorsData = new GridData(SWT.FILL, SWT.FILL, true, true);
    selectedWarriorsData.horizontalSpan = 2;
    selectedWarriorsData.heightHint = 180;
    selectedWarriorsList.setLayoutData(selectedWarriorsData);

    Label partySizeLabel = new Label(dialog, SWT.NONE);
    partySizeLabel.setText(I18n.t("dialog.party.partySize"));
    Label partySizeValueLabel = new Label(dialog, SWT.NONE);
    partySizeValueLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

    LinkedHashMap<String, WarriorDefinition> warriorsById = new LinkedHashMap<>();
    for (WarriorDefinition warrior : warriors) {
      warriorsById.put(warrior.id(), warrior);
    }

    java.util.List<WarriorDefinition> selectedWarriors =
        resolveSelectedWarriors(warriorsById, Settings.getSetting(Settings.PARTY_WARRIORS));

    Runnable refreshSelectedWarriors = () -> {
      selectedWarriorsList.removeAll();
      for (WarriorDefinition warrior : selectedWarriors) {
        selectedWarriorsList.add(warrior.displayName());
      }
      partySizeValueLabel.setText(Integer.toString(selectedWarriors.size()));
      partySizeValueLabel.getParent().layout();
    };
    refreshSelectedWarriors.run();

    addWarriorButton.addListener(
        SWT.Selection,
        event -> {
          int index = warriorCombo.getSelectionIndex();
          if (index < 0 || index >= warriors.size()) {
            return;
          }
          WarriorDefinition selected = warriors.get(index);
          boolean alreadySelected =
              selectedWarriors.stream().anyMatch(warrior -> warrior.id().equals(selected.id()));
          if (!alreadySelected) {
            selectedWarriors.add(selected);
            refreshSelectedWarriors.run();
          }
        });

    removeWarriorButton.addListener(
        SWT.Selection,
        event -> {
          int index = selectedWarriorsList.getSelectionIndex();
          if (index < 0 || index >= selectedWarriors.size()) {
            return;
          }
          selectedWarriors.remove(index);
          refreshSelectedWarriors.run();
        });

    Composite actions = new Composite(dialog, SWT.NONE);
    GridData actionsData = new GridData(SWT.END, SWT.CENTER, true, false);
    actionsData.horizontalSpan = 2;
    actions.setLayoutData(actionsData);
    actions.setLayout(new GridLayout(2, true));

    Button acceptButton = new Button(actions, SWT.PUSH);
    acceptButton.setText(I18n.t("button.accept"));
    acceptButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    acceptButton.addListener(
        SWT.Selection,
        event -> {
          if (selectedWarriors.isEmpty()) {
            SwtDialogs.showWarning(
                owner,
                I18n.t("dialog.party.invalidTitle"),
                I18n.t("dialog.party.invalidMessage"));
            return;
          }
          Settings.setSetting(Settings.PARTY_SIZE, Integer.toString(selectedWarriors.size()));
          Settings.setSetting(
              Settings.PARTY_WARRIORS,
              selectedWarriors.stream()
                  .map(WarriorDefinition::id)
                  .collect(Collectors.joining(",")));
          Settings.save();
          dialog.close();
        });

    Button cancelButton = new Button(actions, SWT.PUSH);
    cancelButton.setText(I18n.t("button.cancel"));
    cancelButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    cancelButton.addListener(SWT.Selection, event -> dialog.close());

    dialog.setDefaultButton(acceptButton);
    dialog.pack();
    dialog.setSize(Math.max(560, dialog.getSize().x), dialog.getSize().y);
    Rectangle ownerBounds = owner.getBounds();
    Point dialogSize = dialog.getSize();
    int x = ownerBounds.x + ((ownerBounds.width - dialogSize.x) / 2);
    int y = ownerBounds.y + ((ownerBounds.height - dialogSize.y) / 2);
    dialog.setLocation(Math.max(0, x), Math.max(0, y));
    dialog.open();

    while (!dialog.isDisposed()) {
      if (!display.readAndDispatch()) {
        display.sleep();
      }
    }
  }

  private java.util.List<WarriorDefinition> loadWarriors() throws Exception {
    Path warriorsDirectory = projectRoot.resolve("data/xml/warriors");
    if (!Files.isDirectory(warriorsDirectory)) {
      return List.of();
    }

    EditableContentTranslations translations =
        EditableContentTranslations.load(projectRoot, I18n.getLanguage());
    Map<String, WarriorDefinition> warriorsById = new LinkedHashMap<>();

    List<Path> files;
    try (var stream = Files.list(warriorsDirectory)) {
      files =
          stream
              .filter(this::isWarriorXmlFile)
              .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase()))
              .toList();
    }

    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(false);

    for (Path file : files) {
      var document = factory.newDocumentBuilder().parse(file.toFile());
      Element root = document.getDocumentElement();
      if (root == null || !"warriors".equals(root.getTagName())) {
        continue;
      }

      NodeList children = root.getChildNodes();
      for (int i = 0; i < children.getLength(); i++) {
        Node node = children.item(i);
        if (node.getNodeType() != Node.ELEMENT_NODE || !"warrior".equals(node.getNodeName())) {
          continue;
        }

        Element warriorElement = (Element) node;
        String id =
            warriorElement.getAttribute("id") == null
                ? ""
                : warriorElement.getAttribute("id").trim();
        if (id.isEmpty()) {
          continue;
        }

        String rawName = childText(warriorElement, "name");
        String rawRace = childText(warriorElement, "race");
        String counter = childText(warriorElement, "counter");
        String rules = childText(warriorElement, "rules");

        warriorsById.put(
            id,
            new WarriorDefinition(
                id,
                translations.t("warrior." + id + ".name", rawName),
                translations.t("warrior." + id + ".race", rawRace),
                counter,
                translations.t("warrior." + id + ".rules", rules)));
      }
    }

    return warriorsById.values().stream()
        .sorted(Comparator.comparing(WarriorDefinition::name, String.CASE_INSENSITIVE_ORDER))
        .toList();
  }

  private boolean isWarriorXmlFile(Path file) {
    if (file == null || !Files.isRegularFile(file)) {
      return false;
    }
    String name = file.getFileName().toString().toLowerCase();
    return name.endsWith(".xml")
        && !name.endsWith(".xml.bak")
        && !"whq-warriors-schema.xsd".equals(name);
  }

  private static java.util.List<WarriorDefinition> resolveSelectedWarriors(
      Map<String, WarriorDefinition> warriorsById, String persistedSelection) {
    LinkedHashSet<String> ids = new LinkedHashSet<>();
    if (persistedSelection != null && !persistedSelection.isBlank()) {
      for (String token : persistedSelection.split(",")) {
        String trimmed = token == null ? "" : token.trim();
        if (!trimmed.isEmpty()) {
          ids.add(trimmed);
        }
      }
    }

    if (ids.isEmpty()) {
      ids.add("warrior-barbarian");
      ids.add("warrior-dwarf");
      ids.add("warrior-elf");
      ids.add("warrior-wizard");
    }

    java.util.List<WarriorDefinition> selected = new ArrayList<>();
    for (String id : ids) {
      WarriorDefinition warrior = warriorsById.get(id);
      if (warrior != null) {
        selected.add(warrior);
      }
    }

    if (selected.isEmpty()) {
      selected.addAll(warriorsById.values());
    }

    return selected;
  }

  private static String childText(Element parent, String tagName) {
    if (parent == null || tagName == null || tagName.isBlank()) {
      return "";
    }
    NodeList children = parent.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node node = children.item(i);
      if (node.getNodeType() == Node.ELEMENT_NODE && tagName.equals(node.getNodeName())) {
        String text = node.getTextContent();
        return text == null ? "" : text.trim();
      }
    }
    return "";
  }

  public void openEventProbabilitySettings(Shell parent) {
    Shell owner = resolveParent(parent);
    Shell dialog = new Shell(owner, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
    AppIcon.inherit(dialog, owner);
    dialog.setText(I18n.t("dialog.probability.title"));
    dialog.setLayout(new GridLayout(2, false));

    new Label(dialog, SWT.WRAP).setText(I18n.t("dialog.probability.dungeonEvent"));
    Text eventProbabilityText = new Text(dialog, SWT.BORDER);
    eventProbabilityText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    eventProbabilityText.setText(Integer.toString(Settings.getSettingAsInt(Settings.EVENT_PROBABILITY)));

    Label eventHint = new Label(dialog, SWT.WRAP);
    eventHint.setText(I18n.t("dialog.probability.dungeonEventHint"));
    GridData eventHintData = new GridData(SWT.FILL, SWT.CENTER, true, false);
    eventHintData.horizontalSpan = 2;
    eventHint.setLayoutData(eventHintData);

    new Label(dialog, SWT.WRAP).setText(I18n.t("dialog.probability.treasureGold"));
    Text treasureGoldProbabilityText = new Text(dialog, SWT.BORDER);
    treasureGoldProbabilityText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    treasureGoldProbabilityText.setText(Integer.toString(Settings.getSettingAsInt(Settings.TREASURE_GOLD_PROBABILITY)));

    Label goldHint = new Label(dialog, SWT.WRAP);
    goldHint.setText(I18n.t("dialog.probability.treasureGoldHint"));
    GridData goldHintData = new GridData(SWT.FILL, SWT.CENTER, true, false);
    goldHintData.horizontalSpan = 2;
    goldHint.setLayoutData(goldHintData);

    Composite actions = new Composite(dialog, SWT.NONE);
    GridData actionsData = new GridData(SWT.END, SWT.CENTER, true, false);
    actionsData.horizontalSpan = 2;
    actions.setLayoutData(actionsData);
    actions.setLayout(new GridLayout(2, true));

    Button acceptButton = new Button(actions, SWT.PUSH);
    acceptButton.setText(I18n.t("button.accept"));
    acceptButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    acceptButton.addListener(
        SWT.Selection,
        event -> {
          try {
            int eventProbability = parseProbability(eventProbabilityText.getText());
            int treasureGoldProbability = parseProbability(treasureGoldProbabilityText.getText());
            Settings.setSetting(Settings.EVENT_PROBABILITY, Integer.toString(eventProbability));
            Settings.setSetting(Settings.TREASURE_GOLD_PROBABILITY, Integer.toString(treasureGoldProbability));
            Settings.save();
            dialog.close();
          } catch (NumberFormatException ex) {
            SwtDialogs.showWarning(
                owner,
                I18n.t("dialog.probability.invalidTitle"),
                I18n.t("dialog.probability.invalidMessage"));
          }
        });

    Button cancelButton = new Button(actions, SWT.PUSH);
    cancelButton.setText(I18n.t("button.cancel"));
    cancelButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    cancelButton.addListener(SWT.Selection, event -> dialog.close());

    dialog.setDefaultButton(acceptButton);
    dialog.pack();
    dialog.setSize(Math.max(540, dialog.getSize().x), dialog.getSize().y);
    Rectangle ownerBounds = owner.getBounds();
    Point dialogSize = dialog.getSize();
    int x = ownerBounds.x + ((ownerBounds.width - dialogSize.x) / 2);
    int y = ownerBounds.y + ((ownerBounds.height - dialogSize.y) / 2);
    dialog.setLocation(Math.max(0, x), Math.max(0, y));
    dialog.open();

    while (!dialog.isDisposed()) {
      if (!display.readAndDispatch()) {
        display.sleep();
      }
    }
  }

  public void showAbout(Shell parent) {
    String monsterImgDir = Settings.getSetting(Settings.MONSTER_IMG_DIR);
    Image image = cardWindowManager.loadImage(monsterImgDir + "minotaur.png");
    AboutDialog.open(resolveParent(parent), image);
  }

  private void reloadControllerContent() {
    controller.loadContent(issue -> SwtDialogs.showWarning(shell, issue.title(), issue.message()));
  }

  private void resetEventList() {
    controller.rebuildDecks();
    eventList = controller.deck(DeckType.DUNGEON);
    travelEventList = controller.deck(DeckType.TRAVEL);
    settlementEventList = controller.deck(DeckType.SETTLEMENT);
    treasureEventList = controller.deck(DeckType.TREASURE);
    objectiveTreasureEventList = controller.deck(DeckType.OBJECTIVE_TREASURE);
  }

  private void persistOptions() {
    AdventureAmbience currentAdventureAmbience = AppState.loadFromSettings().adventureAmbience();
    new AppState(
            DeckMode.fromSimulatedDeck(controller.isSimulateDeckMode()),
            showEventDeckToggle != null && showEventDeckToggle.getSelection(),
            showSettlementDeckToggle != null && showSettlementDeckToggle.getSelection(),
            showTravelDeckToggle != null && showTravelDeckToggle.getSelection(),
            showTreasureDeckToggle != null && showTreasureDeckToggle.getSelection(),
            showObjectiveTreasureDeckToggle != null && showObjectiveTreasureDeckToggle.getSelection(),
            I18n.getLanguage(),
            currentAdventureAmbience)
        .persistToSettings();

    for (Table table : controller.contentRepository().tables().values()) {
      String name = table.getName() + ".active";
      String value = Boolean.toString(table.isActive());
      Settings.setSetting(name, value);
    }

    Settings.save();
  }

  private void persistDeckVisibility() {
    Settings.setSetting(
        Settings.SHOW_EVENT_DECK,
        Boolean.toString(showEventDeckToggle != null && showEventDeckToggle.getSelection()));
    Settings.setSetting(
        Settings.SHOW_SETTLEMENT_DECK,
        Boolean.toString(showSettlementDeckToggle != null && showSettlementDeckToggle.getSelection()));
    Settings.setSetting(
        Settings.SHOW_TRAVEL_DECK,
        Boolean.toString(showTravelDeckToggle != null && showTravelDeckToggle.getSelection()));
    Settings.setSetting(
        Settings.SHOW_TREASURE_DECK,
        Boolean.toString(showTreasureDeckToggle != null && showTreasureDeckToggle.getSelection()));
    Settings.setSetting(
        Settings.SHOW_OBJECTIVE_TREASURE_DECK,
        Boolean.toString(
            showObjectiveTreasureDeckToggle != null && showObjectiveTreasureDeckToggle.getSelection()));
    Settings.save();
  }

  private void disposePreviewImages() {
    for (Image image : previewImages.values()) {
      if (image != null && !image.isDisposed()) {
        image.dispose();
      }
    }
    previewImages.clear();
  }

  private static void centerOnPrimaryMonitor(Shell shell) {
    Rectangle monitorBounds = shell.getDisplay().getPrimaryMonitor().getBounds();
    Point size = shell.getSize();
    int x = monitorBounds.x + ((monitorBounds.width - size.x) / 2);
    int y = monitorBounds.y + ((monitorBounds.height - size.y) / 2);
    shell.setLocation(Math.max(0, x), Math.max(0, y));
  }

  public void setSimulateDeckMode(boolean asDeck) {
    if (controller.isSimulateDeckMode() == asDeck) {
      return;
    }
    controller.setSimulateDeckMode(asDeck);
    resetEventList();
    persistOptions();
  }

  public boolean isSimulateDeckMode() {
    return controller.isSimulateDeckMode();
  }

  public void reloadData() {
    reloadControllerContent();
    resetEventList();
  }

  public void drawSettlementEvent() {
    generateSettlementEvent();
  }

  public void closeAllOpenCards() {
    cardWindowManager.closeAllCards();
  }

  public ContentRepository contentRepository() {
    return controller.contentRepository();
  }

  public void showEntries(Shell parent, List<Object> entries) {
    if (entries == null || entries.isEmpty()) {
      return;
    }
    open();
    focus();
    cardWindowManager.resetCascadeStart();
    for (Object entry : entries) {
      cardWindowManager.showCard(resolveParent(parent), entry, controller.contentRepository());
    }
  }

  public void refreshTexts() {
    if (shell == null || shell.isDisposed()) {
      return;
    }

    shell.setText(I18n.t("event.window.title"));

    if (showEventDeckToggle != null && !showEventDeckToggle.isDisposed()) {
      showEventDeckToggle.setText(I18n.t("toggle.showEventDeck"));
    }
    if (showSettlementDeckToggle != null && !showSettlementDeckToggle.isDisposed()) {
      showSettlementDeckToggle.setText(I18n.t("toggle.showSettlementDeck"));
    }
    if (showTravelDeckToggle != null && !showTravelDeckToggle.isDisposed()) {
      showTravelDeckToggle.setText(I18n.t("toggle.showTravelDeck"));
    }
    if (showTreasureDeckToggle != null && !showTreasureDeckToggle.isDisposed()) {
      showTreasureDeckToggle.setText(I18n.t("toggle.showTreasureDeck"));
    }
    if (showObjectiveTreasureDeckToggle != null && !showObjectiveTreasureDeckToggle.isDisposed()) {
      showObjectiveTreasureDeckToggle.setText(I18n.t("toggle.showObjectiveTreasureDeck"));
    }
    if (closeAllCardsButton != null && !closeAllCardsButton.isDisposed()) {
      closeAllCardsButton.setText(I18n.t("menu.item.closeAllCards"));
    }
    if (eventDeckTitleLabel != null && !eventDeckTitleLabel.isDisposed()) {
      eventDeckTitleLabel.setText(I18n.t("deck.events.title"));
    }
    if (eventDeckSubtitleLabel != null && !eventDeckSubtitleLabel.isDisposed()) {
      eventDeckSubtitleLabel.setText(I18n.t("deck.events.subtitle"));
    }
    if (settlementDeckTitleLabel != null && !settlementDeckTitleLabel.isDisposed()) {
      settlementDeckTitleLabel.setText(I18n.t("deck.settlement.title"));
    }
    if (settlementDeckSubtitleLabel != null && !settlementDeckSubtitleLabel.isDisposed()) {
      settlementDeckSubtitleLabel.setText(I18n.t("deck.settlement.subtitle"));
    }
    if (travelDeckTitleLabel != null && !travelDeckTitleLabel.isDisposed()) {
      travelDeckTitleLabel.setText(I18n.t("deck.travel.title"));
    }
    if (travelDeckSubtitleLabel != null && !travelDeckSubtitleLabel.isDisposed()) {
      travelDeckSubtitleLabel.setText(I18n.t("deck.travel.subtitle"));
    }
    if (treasureDeckTitleLabel != null && !treasureDeckTitleLabel.isDisposed()) {
      treasureDeckTitleLabel.setText(I18n.t("deck.treasure.title"));
    }
    if (treasureDeckSubtitleLabel != null && !treasureDeckSubtitleLabel.isDisposed()) {
      treasureDeckSubtitleLabel.setText(I18n.t("deck.treasure.subtitle"));
    }
    if (objectiveTreasureDeckTitleLabel != null && !objectiveTreasureDeckTitleLabel.isDisposed()) {
      objectiveTreasureDeckTitleLabel.setText(I18n.t("deck.objectiveTreasure.title"));
    }
    if (objectiveTreasureDeckSubtitleLabel != null && !objectiveTreasureDeckSubtitleLabel.isDisposed()) {
      objectiveTreasureDeckSubtitleLabel.setText(I18n.t("deck.objectiveTreasure.subtitle"));
    }
    if (headerCanvas != null && !headerCanvas.isDisposed()) {
      headerCanvas.redraw();
    }

    if (eventButton != null && !eventButton.isDisposed() && eventButton.getImage() == null) {
      eventButton.setText(I18n.t("button.clickHere"));
    }
    if (settlementButton != null && !settlementButton.isDisposed() && settlementButton.getImage() == null) {
      settlementButton.setText(I18n.t("button.clickHere"));
    }
    if (travelButton != null && !travelButton.isDisposed() && travelButton.getImage() == null) {
      travelButton.setText(I18n.t("button.clickHere"));
    }
    if (treasureButton != null && !treasureButton.isDisposed() && treasureButton.getImage() == null) {
      treasureButton.setText(I18n.t("button.clickHere"));
    }
    if (objectiveTreasureButton != null
        && !objectiveTreasureButton.isDisposed()
        && objectiveTreasureButton.getImage() == null) {
      objectiveTreasureButton.setText(I18n.t("button.clickHere"));
    }

    shell.layout(true, true);
  }

  private Shell resolveParent(Shell parent) {
    if (parent != null && !parent.isDisposed()) {
      return parent;
    }
    return shell;
  }

  private static int parseProbability(String value) {
    int probability = Integer.parseInt(value == null ? "" : value.trim());
    if (probability < 0 || probability > 100) {
      throw new NumberFormatException("Probability out of range");
    }
    return probability;
  }
}
