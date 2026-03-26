package com.whq.app.ui;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.graphics.TextLayout;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Text;

import com.whq.app.adventure.ObjectiveRoomAdventure;
import com.whq.app.adventure.ObjectiveRoomAdventureRepository;
import com.whq.app.adventure.ObjectiveRoomAdventureRepositoryException;
import com.whq.app.adventure.XmlObjectiveRoomAdventureRepository;
import com.whq.app.i18n.I18n;
import com.whq.app.i18n.Language;
import com.whq.app.io.CardCsvService;
import com.whq.app.model.CardType;
import com.whq.app.model.DungeonCard;
import com.whq.app.render.CardRenderer;
import com.whq.app.storage.DungeonCardStorageException;
import com.whq.app.storage.DungeonCardStore;
import com.whq.app.storage.XmlDungeonCardStore;
import pms.whq.EventDeckApp;
import pms.whq.Settings;
import pms.whq.content.ContentRepository;
import pms.whq.data.MonsterEntry;
import pms.whq.data.MonsterGroup;
import pms.whq.data.Table;
import pms.whq.data.TableReferenceEntry;
import pms.whq.data.TableKind;
import pms.whq.game.TableDrawService;
import pms.whq.state.AppState;
import pms.whq.state.AdventureAmbience;
import pms.whq.swt.CardFactory;
import pms.whq.swt.EventContentEditorDialog;

public class AppWindow {
    private static final TableDrawService TABLE_DRAW_SERVICE = new TableDrawService();

    private record ObjectiveMonsterDifficulty(String labelKey, int[] offsets) {
    }

    private static final List<ObjectiveMonsterDifficulty> OBJECTIVE_MONSTER_DIFFICULTIES = List.of(
            new ObjectiveMonsterDifficulty("difficulty.easy", new int[] {0, 0}),
            new ObjectiveMonsterDifficulty("difficulty.normal", new int[] {0, 0, 0}),
            new ObjectiveMonsterDifficulty("difficulty.hard", new int[] {1, 0, 0}),
            new ObjectiveMonsterDifficulty("difficulty.veryHard", new int[] {1, 1, 0}),
            new ObjectiveMonsterDifficulty("difficulty.extreme", new int[] {2, 1, 0}));

    private final Display display;
    private final Shell shell;
    private final Path projectRoot;
    private final DungeonCardStore cardStore;
    private final ObjectiveRoomAdventureRepository objectiveRoomAdventureRepository;
    private final CardCsvService csvService;
    private final List<LocalizedUiAction> localizedActions;

    private CardRenderer renderer;
    private java.util.List<DungeonCard> cards;
    private DungeonCard selected;
    private WhqUiTheme theme;
    private Canvas renderCanvas;
    private Canvas heroArtCanvas;
    private org.eclipse.swt.widgets.List cardList;
    private EventDeckApp eventDeckApp;
    private final Runnable languageListener = this::refreshLocalizedTexts;

    private Label heroTitleLabel;
    private Label heroSubtitleLabel;
    private Label collectionStatsLabel;
    private Label environmentStatsLabel;
    private Label availabilityStatsLabel;
    private Label browserTitleLabel;
    private Label browserHintLabel;
    private Label previewTitleLabel;
    private Label previewHintLabel;
    private Button newDungeonButton;
    private Button openEventDecksButton;
    private Button activateTablesButton;
    private Button contentEditorButton;

    private MenuItem playMenuItem;
    private MenuItem viewMenuItem;
    private MenuItem contentMenuItem;
    private MenuItem eventCardsMenuItem;
    private MenuItem optionsMenuItem;

    private final LocalizedUiAction newDungeonAction;
    private final LocalizedUiAction openEventDecksAction;
    private final LocalizedUiAction eventContentEditorAction;
    private final LocalizedUiAction importCsvAction;
    private final LocalizedUiAction exportAllCsvAction;
    private final LocalizedUiAction exportEnvironmentCsvAction;
    private final LocalizedUiAction activateTablesAction;
    private final LocalizedUiAction setPartySizeAction;
    private final LocalizedUiAction setEventProbabilityAction;
    private final LocalizedUiAction simulateDeckAction;
    private final LocalizedUiAction simulateTableAction;
    private final LocalizedUiAction dungeonDefaultsAction;
    private final LocalizedUiAction spanishLanguageAction;
    private final LocalizedUiAction englishLanguageAction;

    public AppWindow(Display display, Path projectRoot) {
        this.display = display;
        this.projectRoot = projectRoot;
        this.shell = new Shell(display);
        this.cardStore = new XmlDungeonCardStore(projectRoot);
        this.objectiveRoomAdventureRepository = new XmlObjectiveRoomAdventureRepository(projectRoot);
        this.csvService = new CardCsvService();
        this.localizedActions = new ArrayList<>();
        this.newDungeonAction = registerAction(LocalizedUiAction.push("menu.item.newDungeon", this::openNewDungeonDialog));
        this.openEventDecksAction = registerAction(LocalizedUiAction.push("menu.item.openEventDecks", this::openEventDecks));
        this.eventContentEditorAction = registerAction(LocalizedUiAction.push("menu.item.contentEditor", this::openEventContentEditor));
        this.importCsvAction = registerAction(LocalizedUiAction.push("menu.item.importCsv", this::handleImportCsv));
        this.exportAllCsvAction = registerAction(LocalizedUiAction.push(
                "menu.item.exportAllCsv",
                () -> handleExportCsv(cards),
                () -> cards != null && !cards.isEmpty()));
        this.exportEnvironmentCsvAction = registerAction(LocalizedUiAction.push(
                "menu.item.exportEnvironmentCsv",
                this::handleExportSelectedEnvironment,
                () -> selected != null));
        this.activateTablesAction = registerAction(LocalizedUiAction.push("menu.item.activateTables", this::openActivateTablesDialog));
        this.setPartySizeAction = registerAction(LocalizedUiAction.push("menu.item.setPartySize", this::openPartySizeDialog));
        this.setEventProbabilityAction = registerAction(LocalizedUiAction.push(
                "menu.item.setEventProbability",
                this::openEventProbabilityDialog,
                () -> !isSimulateDeckModeSelected()));
        this.simulateDeckAction = registerAction(LocalizedUiAction.radio(
                "menu.item.simulateDeck",
                () -> setSimulateDeckMode(true),
                this::isSimulateDeckModeSelected));
        this.simulateTableAction = registerAction(LocalizedUiAction.radio(
                "menu.item.simulateTable",
                () -> setSimulateDeckMode(false),
                () -> !isSimulateDeckModeSelected()));
        this.dungeonDefaultsAction = registerAction(LocalizedUiAction.push(
                "menu.item.dungeonDefaults",
                this::openDungeonDefaultsDialog));
        this.spanishLanguageAction = registerAction(LocalizedUiAction.radio(
                "menu.item.spanish",
                () -> setLanguageAndPersist(Language.ES),
                () -> I18n.getLanguage() == Language.ES));
        this.englishLanguageAction = registerAction(LocalizedUiAction.radio(
                "menu.item.english",
                () -> setLanguageAndPersist(Language.EN),
                () -> I18n.getLanguage() == Language.EN));
    }

    public void open() {
        Settings.load(projectRoot);
        I18n.setLanguage(Settings.getLanguage());
        shell.setText(I18n.t("app.title"));
        shell.setLayout(new FillLayout());
        shell.setSize(1180, 760);
        shell.setMaximized(true);

        createMenuBar();
        I18n.addListener(languageListener);

        theme = new WhqUiTheme(display, projectRoot);
        renderer = new CardRenderer(display, projectRoot);
        cards = loadCards();
        if (!cards.isEmpty()) {
            selected = cards.get(0);
        }

        Composite mainPanel = new Composite(shell, SWT.NONE);
        mainPanel.setBackground(theme.shellBackground);
        GridLayout mainLayout = new GridLayout(1, false);
        mainLayout.marginWidth = 18;
        mainLayout.marginHeight = 18;
        mainLayout.verticalSpacing = 16;
        mainPanel.setLayout(mainLayout);

        buildHeroSection(mainPanel);
        refreshDashboardStats();
        refreshLocalizedTexts();

        shell.addListener(SWT.Dispose, event -> {
            renderer.dispose();
            if (theme != null) {
                theme.dispose();
            }
            I18n.removeListener(languageListener);
            Settings.save();
        });
        shell.addListener(SWT.Activate, event -> refreshLocalizedTexts());
        shell.open();
        getOrCreateEventDeckApp().open();
        shell.forceActive();
    }

    private void createMenuBar() {
        Menu menuBar = new Menu(shell, SWT.BAR);
        shell.setMenuBar(menuBar);

        playMenuItem = new MenuItem(menuBar, SWT.CASCADE);
        Menu playMenu = new Menu(shell, SWT.DROP_DOWN);
        playMenuItem.setMenu(playMenu);
        createActionMenuItem(playMenu, SWT.PUSH, newDungeonAction);

        viewMenuItem = new MenuItem(menuBar, SWT.CASCADE);
        Menu viewMenu = new Menu(shell, SWT.DROP_DOWN);
        viewMenuItem.setMenu(viewMenu);
        createActionMenuItem(viewMenu, SWT.PUSH, openEventDecksAction);

        contentMenuItem = new MenuItem(menuBar, SWT.CASCADE);
        Menu contentMenu = new Menu(shell, SWT.DROP_DOWN);
        contentMenuItem.setMenu(contentMenu);
        createActionMenuItem(contentMenu, SWT.PUSH, eventContentEditorAction);
        new MenuItem(contentMenu, SWT.SEPARATOR);
        createActionMenuItem(contentMenu, SWT.PUSH, importCsvAction);
        createActionMenuItem(contentMenu, SWT.PUSH, exportAllCsvAction);
        createActionMenuItem(contentMenu, SWT.PUSH, exportEnvironmentCsvAction);

        eventCardsMenuItem = new MenuItem(menuBar, SWT.CASCADE);
        Menu eventCardsMenu = new Menu(shell, SWT.DROP_DOWN);
        eventCardsMenuItem.setMenu(eventCardsMenu);
        createActionMenuItem(eventCardsMenu, SWT.PUSH, activateTablesAction);
        createActionMenuItem(eventCardsMenu, SWT.PUSH, setPartySizeAction);
        createActionMenuItem(eventCardsMenu, SWT.PUSH, setEventProbabilityAction);
        new MenuItem(eventCardsMenu, SWT.SEPARATOR);
        createActionMenuItem(eventCardsMenu, SWT.RADIO, simulateDeckAction);
        createActionMenuItem(eventCardsMenu, SWT.RADIO, simulateTableAction);

        optionsMenuItem = new MenuItem(menuBar, SWT.CASCADE);
        Menu optionsMenu = new Menu(shell, SWT.DROP_DOWN);
        optionsMenuItem.setMenu(optionsMenu);
        createActionMenuItem(optionsMenu, SWT.PUSH, dungeonDefaultsAction);
        new MenuItem(optionsMenu, SWT.SEPARATOR);
        createActionMenuItem(optionsMenu, SWT.RADIO, spanishLanguageAction);
        createActionMenuItem(optionsMenu, SWT.RADIO, englishLanguageAction);

        refreshLocalizedTexts();
    }

    private void openEventDecks() {
        eventDeckApp = getOrCreateEventDeckApp();
        eventDeckApp.open();
        eventDeckApp.focus();
    }

    private LocalizedUiAction registerAction(LocalizedUiAction action) {
        localizedActions.add(action);
        return action;
    }

    private MenuItem createActionMenuItem(Menu menu, int style, LocalizedUiAction action) {
        MenuItem item = new MenuItem(menu, style);
        action.bind(item);
        return item;
    }

    private void refreshLocalizedActions() {
        for (LocalizedUiAction action : localizedActions) {
            action.refresh();
        }
    }

    private void openEventContentEditor() {
        EventContentEditorDialog editor = new EventContentEditorDialog(
                shell,
                projectRoot,
                () -> {
                    if (eventDeckApp != null && !eventDeckApp.isDisposed()) {
                        eventDeckApp.reloadData();
                    }
                });
        editor.open();
    }

    private EventDeckApp getOrCreateEventDeckApp() {
        if (eventDeckApp == null || eventDeckApp.isDisposed()) {
            eventDeckApp = new EventDeckApp(display, projectRoot);
        }
        eventDeckApp.refreshTexts();
        return eventDeckApp;
    }

    private void openActivateTablesDialog() {
        getOrCreateEventDeckApp().openTableSettings(shell);
        refreshLocalizedTexts();
    }

    private void openPartySizeDialog() {
        getOrCreateEventDeckApp().openPartySizeSettings(shell);
        refreshLocalizedTexts();
    }

    private void openEventProbabilityDialog() {
        getOrCreateEventDeckApp().openEventProbabilitySettings(shell);
        refreshLocalizedTexts();
    }

    private void setSimulateDeckMode(boolean asDeck) {
        EventDeckApp app = getOrCreateEventDeckApp();
        if (app.isSimulateDeckMode() == asDeck) {
            refreshLocalizedTexts();
            return;
        }
        app.setSimulateDeckMode(asDeck);
        refreshLocalizedTexts();
    }

    private boolean isSimulateDeckModeSelected() {
        if (eventDeckApp != null && !eventDeckApp.isDisposed()) {
            return eventDeckApp.isSimulateDeckMode();
        }
        return AppState.loadFromSettings().deckMode().isDeck();
    }

    private void refreshEventCardsMenuState() {
        refreshLocalizedActions();
    }

    private void refreshLocalizedTexts() {
        shell.setText(I18n.t("app.title"));
        playMenuItem.setText(I18n.t("menu.play"));
        viewMenuItem.setText(I18n.t("menu.view"));
        contentMenuItem.setText(I18n.t("menu.content"));
        eventCardsMenuItem.setText(I18n.t("menu.eventCards"));
        optionsMenuItem.setText(I18n.t("menu.options"));
        refreshLocalizedActions();

        if (heroTitleLabel != null && !heroTitleLabel.isDisposed()) {
            heroTitleLabel.setText(I18n.t("app.title"));
        }
        if (heroSubtitleLabel != null && !heroSubtitleLabel.isDisposed()) {
            heroSubtitleLabel.setText(I18n.t("dashboard.hero.subtitle"));
        }

        refreshDashboardStats();
        refreshEventCardsMenuState();

        if (eventDeckApp != null && !eventDeckApp.isDisposed()) {
            eventDeckApp.refreshTexts();
        }
    }

    private void buildHeroSection(Composite parent) {
        Composite heroSection = new Composite(parent, SWT.DOUBLE_BUFFERED);
        heroSection.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        heroSection.setBackground(theme.panelBackground);
        GridLayout heroLayout = new GridLayout(2, false);
        heroLayout.marginWidth = 18;
        heroLayout.marginHeight = 18;
        heroLayout.horizontalSpacing = 20;
        heroSection.setLayout(heroLayout);

        heroSection.addPaintListener(event -> theme.paintDarkPanel(event.gc, heroSection.getClientArea()));

        Composite heroCopy = new Composite(heroSection, SWT.NONE);
        heroCopy.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        heroCopy.setBackground(theme.panelBackground);
        GridLayout copyLayout = new GridLayout(1, false);
        copyLayout.marginWidth = 0;
        copyLayout.marginHeight = 0;
        copyLayout.verticalSpacing = 10;
        heroCopy.setLayout(copyLayout);

        heroTitleLabel = new Label(heroCopy, SWT.WRAP);
        heroTitleLabel.setBackground(theme.panelBackground);
        heroTitleLabel.setForeground(theme.mist);
        heroTitleLabel.setFont(theme.heroTitleFont);
        heroTitleLabel.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        heroSubtitleLabel = new Label(heroCopy, SWT.WRAP);
        heroSubtitleLabel.setBackground(theme.panelBackground);
        heroSubtitleLabel.setForeground(theme.parchment);
        heroSubtitleLabel.setFont(theme.heroSubtitleFont);
        GridData subtitleData = new GridData(SWT.FILL, SWT.TOP, true, false);
        subtitleData.widthHint = 520;
        heroSubtitleLabel.setLayoutData(subtitleData);

        Composite actionRow = new Composite(heroCopy, SWT.NONE);
        actionRow.setBackground(theme.panelBackground);
        actionRow.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        GridLayout actionsLayout = new GridLayout(2, true);
        actionsLayout.marginWidth = 0;
        actionsLayout.marginHeight = 4;
        actionsLayout.horizontalSpacing = 10;
        actionsLayout.verticalSpacing = 10;
        actionRow.setLayout(actionsLayout);

        newDungeonButton = createHeroButton(actionRow, newDungeonAction);
        openEventDecksButton = createHeroButton(actionRow, openEventDecksAction);
        activateTablesButton = createHeroButton(actionRow, activateTablesAction);
        contentEditorButton = createHeroButton(actionRow, eventContentEditorAction);

        heroArtCanvas = new Canvas(heroSection, SWT.DOUBLE_BUFFERED);
        GridData artData = new GridData(SWT.FILL, SWT.FILL, false, true);
        artData.widthHint = 470;
        artData.heightHint = 230;
        heroArtCanvas.setLayoutData(artData);
        heroArtCanvas.addPaintListener(event -> theme.paintHeroBanner(event.gc, heroArtCanvas.getClientArea()));
    }

    private Button createHeroButton(Composite parent, LocalizedUiAction action) {
        Button button = new Button(parent, SWT.PUSH);
        button.setFont(theme.bodyFont);
        button.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        action.bind(button);
        return button;
    }

    private Canvas createDialogHeader(Composite parent, String title, String subtitle) {
        Canvas header = new Canvas(parent, SWT.DOUBLE_BUFFERED);
        GridData data = new GridData(SWT.FILL, SWT.TOP, true, false);
        data.heightHint = 210;
        header.setLayoutData(data);
        header.addPaintListener(event -> {
            Rectangle area = header.getClientArea();
            theme.paintHeroBanner(event.gc, area);
            event.gc.setForeground(theme.mist);
            Font titleFont = theme.heroTitleFont;
            Font resizedTitleFont = null;
            try {
                int availableWidth = Math.max(120, area.width - 96);
                Point titleExtent = measureHeaderText(event.gc, titleFont, title);
                if (titleExtent.x > availableWidth) {
                    FontData baseData = theme.heroTitleFont.getFontData()[0];
                    for (int size = baseData.getHeight() - 1; size >= 14; size--) {
                        Font candidate = new Font(display, baseData.getName(), size, baseData.getStyle());
                        Point candidateExtent = measureHeaderText(event.gc, candidate, title);
                        if (candidateExtent.x <= availableWidth) {
                            resizedTitleFont = candidate;
                            titleFont = candidate;
                            break;
                        }
                        candidate.dispose();
                    }
                }
                event.gc.setFont(titleFont);
                event.gc.drawText(title, area.x + 28, area.y + 26, true);
            } finally {
                if (resizedTitleFont != null && !resizedTitleFont.isDisposed()) {
                    resizedTitleFont.dispose();
                }
            }
            TextLayout subtitleLayout = new TextLayout(display);
            try {
                subtitleLayout.setFont(theme.bodyFont);
                subtitleLayout.setText(subtitle == null ? "" : subtitle);
                subtitleLayout.setWidth(Math.max(120, area.width - 96));
                event.gc.setForeground(theme.parchment);
                subtitleLayout.draw(event.gc, area.x + 32, area.y + 78);
            } finally {
                subtitleLayout.dispose();
            }
        });
        return header;
    }

    private Point measureHeaderText(org.eclipse.swt.graphics.GC gc, Font font, String text) {
        gc.setFont(font);
        return gc.textExtent(text == null ? "" : text, SWT.DRAW_TRANSPARENT);
    }

    private Composite createDarkPanel(Composite parent, int columns) {
        Composite panel = new Composite(parent, SWT.DOUBLE_BUFFERED);
        panel.setBackground(theme.panelBackground);
        GridLayout layout = new GridLayout(columns, false);
        layout.marginWidth = 18;
        layout.marginHeight = 18;
        layout.horizontalSpacing = 12;
        layout.verticalSpacing = 10;
        panel.setLayout(layout);
        panel.addPaintListener(event -> theme.paintDarkPanel(event.gc, panel.getClientArea()));
        return panel;
    }

    private Composite createParchmentPanel(Composite parent, int columns) {
        Composite panel = new Composite(parent, SWT.DOUBLE_BUFFERED);
        panel.setBackground(theme.parchment);
        GridLayout layout = new GridLayout(columns, false);
        layout.marginWidth = 18;
        layout.marginHeight = 18;
        layout.horizontalSpacing = 12;
        layout.verticalSpacing = 10;
        panel.setLayout(layout);
        panel.addPaintListener(event -> theme.paintParchmentPanel(event.gc, panel.getClientArea()));
        return panel;
    }

    private void styleDarkLabel(Label label, boolean title) {
        label.setBackground(theme.panelBackground);
        label.setForeground(title ? theme.mist : theme.parchment);
        label.setFont(title ? theme.sectionTitleFont : theme.bodyFont);
    }

    private void styleParchmentLabel(Label label, boolean title) {
        label.setBackground(theme.parchment);
        label.setForeground(title ? theme.ink : theme.mutedInk);
        label.setFont(title ? theme.sectionTitleFont : theme.bodyFont);
    }

    private void styleActionButton(Button button) {
        button.setFont(theme.bodyFont);
        button.setBackground(theme.brassDark);
        button.setForeground(theme.parchment);
    }

    private void styleCombo(org.eclipse.swt.widgets.Combo combo) {
        combo.setBackground(theme.mist);
        combo.setForeground(theme.ink);
        combo.setFont(theme.bodyFont);
    }

    private void styleSpinner(Spinner spinner) {
        spinner.setBackground(theme.mist);
        spinner.setForeground(theme.ink);
        spinner.setFont(theme.bodyFont);
    }

    private Label createStatLabel(Composite parent) {
        Label label = new Label(parent, SWT.WRAP | SWT.CENTER);
        label.setBackground(theme.panelBackgroundAlt);
        label.setForeground(theme.parchment);
        label.setFont(theme.statFont);
        GridData data = new GridData(SWT.FILL, SWT.FILL, true, true);
        data.heightHint = 64;
        label.setLayoutData(data);
        return label;
    }

    private void buildWorkspaceSection(Composite parent) {
        SashForm split = new SashForm(parent, SWT.NONE);
        split.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        split.setBackground(theme.brassDark);

        buildLeftPanel(split);
        buildRenderPanel(split);

        split.setWeights(new int[] {32, 68});
    }

    private void buildLeftPanel(Composite parent) {
        Composite left = new Composite(parent, SWT.DOUBLE_BUFFERED);
        left.setBackground(theme.parchment);
        left.setLayout(new GridLayout(1, false));
        left.addPaintListener(event -> theme.paintParchmentPanel(event.gc, left.getClientArea()));

        browserTitleLabel = new Label(left, SWT.NONE);
        browserTitleLabel.setBackground(theme.parchment);
        browserTitleLabel.setForeground(theme.ink);
        browserTitleLabel.setFont(theme.sectionTitleFont);
        browserTitleLabel.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        cardList = new org.eclipse.swt.widgets.List(left, SWT.BORDER | SWT.V_SCROLL);
        cardList.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        cardList.setBackground(theme.mist);
        cardList.setForeground(theme.ink);
        cardList.setFont(theme.bodyFont);
        reloadCardList();

        cardList.addListener(SWT.Selection, event -> {
            int index = cardList.getSelectionIndex();
            if (index >= 0 && index < cards.size()) {
                selected = cards.get(index);
                if (renderCanvas != null && !renderCanvas.isDisposed()) {
                    renderCanvas.redraw();
                }
            }
        });

        browserHintLabel = new Label(left, SWT.WRAP);
        browserHintLabel.setBackground(theme.parchment);
        browserHintLabel.setForeground(theme.mutedInk);
        browserHintLabel.setFont(theme.bodyFont);
        browserHintLabel.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false));
    }

    private void buildRenderPanel(Composite parent) {
        Composite right = new Composite(parent, SWT.DOUBLE_BUFFERED);
        right.setBackground(theme.panelBackground);
        GridLayout rightLayout = new GridLayout(1, false);
        rightLayout.marginWidth = 16;
        rightLayout.marginHeight = 16;
        rightLayout.verticalSpacing = 10;
        right.setLayout(rightLayout);
        right.addPaintListener(event -> theme.paintDarkPanel(event.gc, right.getClientArea()));

        previewTitleLabel = new Label(right, SWT.NONE);
        previewTitleLabel.setBackground(theme.panelBackground);
        previewTitleLabel.setForeground(theme.mist);
        previewTitleLabel.setFont(theme.sectionTitleFont);
        previewTitleLabel.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        previewHintLabel = new Label(right, SWT.WRAP);
        previewHintLabel.setBackground(theme.panelBackground);
        previewHintLabel.setForeground(theme.parchment);
        previewHintLabel.setFont(theme.bodyFont);
        previewHintLabel.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        renderCanvas = new Canvas(right, SWT.DOUBLE_BUFFERED | SWT.BORDER);
        renderCanvas.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        renderCanvas.addPaintListener(new PaintListener() {
            @Override
            public void paintControl(PaintEvent event) {
                Rectangle area = renderCanvas.getClientArea();
                theme.paintDarkPanel(event.gc, area);
                Rectangle inner = new Rectangle(area.x + 12, area.y + 12, area.width - 24, area.height - 24);

                if (selected == null) {
                    event.gc.setForeground(theme.mist);
                    event.gc.setFont(theme.bodyFont);
                    event.gc.drawText(I18n.t("dashboard.preview.hint"), inner.x + 18, inner.y + 18, true);
                    return;
                }

                Point scaled = renderer.scaleToFit(area);
                int x = area.x + (area.width - scaled.x) / 2;
                int y = area.y + (area.height - scaled.y) / 2;
                renderer.drawCard(event.gc, new Rectangle(x, y, scaled.x, scaled.y), selected);
            }
        });

        right.addListener(SWT.Resize, event -> renderCanvas.redraw());

        shell.addListener(SWT.KeyDown, event -> {
            if (event.keyCode == SWT.F5) {
                renderCanvas.redraw();
            }
        });
    }

    private void refreshDashboardStats() {
        if (cards == null || theme == null) {
            return;
        }

        long environments = cards.stream()
                .map(DungeonCard::getEnvironment)
                .filter(environment -> environment != null && !environment.isBlank())
                .distinct()
                .count();
        long enabled = cards.stream()
                .filter(DungeonCard::isEnabled)
                .count();

        if (collectionStatsLabel != null && !collectionStatsLabel.isDisposed()) {
            collectionStatsLabel.setText(String.format(I18n.t("dashboard.stats.cards"), cards.size()));
        }
        if (environmentStatsLabel != null && !environmentStatsLabel.isDisposed()) {
            environmentStatsLabel.setText(String.format(I18n.t("dashboard.stats.environments"), environments));
        }
        if (availabilityStatsLabel != null && !availabilityStatsLabel.isDisposed()) {
            availabilityStatsLabel.setText(String.format(I18n.t("dashboard.stats.enabled"), enabled));
        }
    }

    private void setLanguageAndPersist(Language language) {
        Settings.setLanguage(language);
        Settings.save();
        I18n.setLanguage(language);
        if (eventDeckApp != null && !eventDeckApp.isDisposed()) {
            eventDeckApp.reloadData();
        }
        refreshLocalizedTexts();
    }

    private void openDungeonDefaultsDialog() {
        Shell dialog = new Shell(shell, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
        dialog.setText(I18n.t("dialog.dungeonDefaults.title"));
        dialog.setBackground(theme.shellBackground);
        dialog.setLayout(new GridLayout(1, false));
        dialog.setSize(620, 430);

        createDialogHeader(
                dialog,
                I18n.t("dialog.dungeonDefaults.title"),
                I18n.t("dialog.dungeonDefaults.hint"));

        Composite formPanel = createDarkPanel(dialog, 2);
        formPanel.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        Label deckSizeLabel = new Label(formPanel, SWT.NONE);
        deckSizeLabel.setText(I18n.t("dialog.dungeonDefaults.deckSize"));
        styleDarkLabel(deckSizeLabel, false);

        Spinner deckSizeSpinner = new Spinner(formPanel, SWT.BORDER);
        styleSpinner(deckSizeSpinner);
        deckSizeSpinner.setMinimum(2);
        deckSizeSpinner.setMaximum(200);
        deckSizeSpinner.setSelection(Math.max(2, Settings.getSettingAsInt(Settings.ADVENTURE_DEFAULT_DECK_SIZE)));
        deckSizeSpinner.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Label roomCountLabel = new Label(formPanel, SWT.NONE);
        roomCountLabel.setText(I18n.t("dialog.dungeonDefaults.roomCount"));
        styleDarkLabel(roomCountLabel, false);

        Spinner roomCountSpinner = new Spinner(formPanel, SWT.BORDER);
        styleSpinner(roomCountSpinner);
        roomCountSpinner.setMinimum(1);
        roomCountSpinner.setMaximum(Math.max(1, deckSizeSpinner.getSelection() - 1));
        roomCountSpinner.setSelection(
                Math.max(
                        1,
                        Math.min(
                                Settings.getSettingAsInt(Settings.ADVENTURE_DEFAULT_ROOM_COUNT),
                                roomCountSpinner.getMaximum())));
        roomCountSpinner.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Label hintLabel = new Label(formPanel, SWT.WRAP);
        hintLabel.setText(I18n.t("dialog.dungeonDefaults.hint"));
        hintLabel.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 2, 1));
        styleDarkLabel(hintLabel, false);

        deckSizeSpinner.addListener(SWT.Modify, event -> {
            int maxRooms = Math.max(1, deckSizeSpinner.getSelection() - 1);
            roomCountSpinner.setMaximum(maxRooms);
            if (roomCountSpinner.getSelection() > maxRooms) {
                roomCountSpinner.setSelection(maxRooms);
            }
        });

        Composite actions = new Composite(formPanel, SWT.NONE);
        actions.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false, 2, 1));
        actions.setBackground(theme.panelBackground);
        GridLayout actionsLayout = new GridLayout(2, true);
        actionsLayout.marginWidth = 0;
        actions.setLayout(actionsLayout);

        Button acceptButton = new Button(actions, SWT.PUSH);
        acceptButton.setText(I18n.t("button.accept"));
        styleActionButton(acceptButton);
        acceptButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        acceptButton.addListener(SWT.Selection, event -> {
            int deckSize = deckSizeSpinner.getSelection();
            int roomCount = roomCountSpinner.getSelection();
            if (deckSize < 2 || roomCount < 1 || roomCount >= deckSize) {
                showError(
                        I18n.t("dialog.dungeonDefaults.invalidTitle"),
                        I18n.t("dialog.dungeonDefaults.invalidMessage"));
                return;
            }

            Settings.setSetting(Settings.ADVENTURE_DEFAULT_DECK_SIZE, Integer.toString(deckSize));
            Settings.setSetting(Settings.ADVENTURE_DEFAULT_ROOM_COUNT, Integer.toString(roomCount));
            Settings.save();
            dialog.close();
        });

        Button cancelButton = new Button(actions, SWT.PUSH);
        cancelButton.setText(I18n.t("button.cancel"));
        styleActionButton(cancelButton);
        cancelButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        cancelButton.addListener(SWT.Selection, event -> dialog.close());

        dialog.open();
        while (!dialog.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }
    }

    private void handleImportCsv() {
        FileDialog dialog = new FileDialog(shell, SWT.OPEN);
        dialog.setText("Importar cartas desde CSV");
        dialog.setFilterExtensions(new String[] {"*.csv", "*.*"});
        dialog.setFilterNames(new String[] {"CSV files", "All files"});

        String selectedPath = dialog.open();
        if (selectedPath == null) {
            return;
        }

        try {
            List<DungeonCard> importedCards = csvService.importFromCsv(Path.of(selectedPath));
            cardStore.insertCards(importedCards);
            refreshCards();
            showInfo("Importación completada", importedCards.size() + " cartas importadas correctamente.");
        } catch (IOException | DungeonCardStorageException | IllegalArgumentException ex) {
            showError("Error al importar CSV", ex.getMessage());
        }
    }

    private void handleExportCsv(List<DungeonCard> cardsToExport) {
        if (cardsToExport == null || cardsToExport.isEmpty()) {
            showInfo("Exportación", "No hay cartas para exportar.");
            return;
        }

        FileDialog dialog = new FileDialog(shell, SWT.SAVE);
        dialog.setText("Exportar cartas a CSV");
        dialog.setFileName("dungeon-cards.csv");
        dialog.setFilterExtensions(new String[] {"*.csv", "*.*"});
        dialog.setFilterNames(new String[] {"CSV files", "All files"});

        String selectedPath = dialog.open();
        if (selectedPath == null) {
            return;
        }

        try {
            csvService.exportToCsv(Path.of(selectedPath), cardsToExport);
            showInfo("Exportación completada", cardsToExport.size() + " cartas exportadas.");
        } catch (IOException ex) {
            showError("Error al exportar CSV", ex.getMessage());
        }
    }

    private void handleExportSelectedEnvironment() {
        if (selected == null) {
            showInfo("Exportación", "Selecciona una carta para exportar su grupo de entorno.");
            return;
        }

        String environment = selected.getEnvironment();
        List<DungeonCard> environmentGroup = cards.stream()
                .filter(card -> environment.equalsIgnoreCase(card.getEnvironment()))
                .collect(Collectors.toList());

        handleExportCsv(environmentGroup);
    }

    private void openCardMaintenanceDialog() {
        Shell dialog = new Shell(shell, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.RESIZE);
        dialog.setText(I18n.t("dialog.maintenance.title"));
        dialog.setLayout(new GridLayout(2, true));
        dialog.setSize(980, 620);

        org.eclipse.swt.widgets.List maintenanceList = new org.eclipse.swt.widgets.List(dialog, SWT.BORDER | SWT.V_SCROLL);
        maintenanceList.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        Composite details = new Composite(dialog, SWT.NONE);
        details.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        details.setLayout(new GridLayout(2, false));

        Label bulkEnvironmentLabel = new Label(details, SWT.NONE);
        bulkEnvironmentLabel.setText(I18n.t("dialog.tileConfig.environment"));
        org.eclipse.swt.widgets.Combo bulkEnvironmentCombo = new org.eclipse.swt.widgets.Combo(
                details,
                SWT.DROP_DOWN | SWT.READ_ONLY);
        bulkEnvironmentCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Composite bulkActions = new Composite(details, SWT.NONE);
        bulkActions.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
        GridLayout bulkActionsLayout = new GridLayout(2, true);
        bulkActionsLayout.marginWidth = 0;
        bulkActionsLayout.marginHeight = 0;
        bulkActions.setLayout(bulkActionsLayout);

        org.eclipse.swt.widgets.Button enableEnvironmentButton = new org.eclipse.swt.widgets.Button(bulkActions, SWT.PUSH);
        enableEnvironmentButton.setText(I18n.t("dialog.tileConfig.enableEnvironment"));
        enableEnvironmentButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        org.eclipse.swt.widgets.Button disableEnvironmentButton = new org.eclipse.swt.widgets.Button(bulkActions, SWT.PUSH);
        disableEnvironmentButton.setText(I18n.t("dialog.tileConfig.disableEnvironment"));
        disableEnvironmentButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Label nameLabel = new Label(details, SWT.NONE);
        nameLabel.setText("Nombre:");
        Label nameValue = new Label(details, SWT.WRAP);
        nameValue.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Label typeLabel = new Label(details, SWT.NONE);
        typeLabel.setText("Tipo:");
        Label typeValue = new Label(details, SWT.WRAP);
        typeValue.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Label environmentLabel = new Label(details, SWT.NONE);
        environmentLabel.setText("Entorno:");
        Label environmentValue = new Label(details, SWT.WRAP);
        environmentValue.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Label copiesLabel = new Label(details, SWT.NONE);
        copiesLabel.setText("Número de copias:");
        Spinner copiesSpinner = new Spinner(details, SWT.BORDER);
        copiesSpinner.setMinimum(0);
        copiesSpinner.setMaximum(999);
        copiesSpinner.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Label enabledLabel = new Label(details, SWT.NONE);
        enabledLabel.setText("Habilitada:");
        org.eclipse.swt.widgets.Button enabledCheckbox = new org.eclipse.swt.widgets.Button(details, SWT.CHECK);
        enabledCheckbox.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

        Label tilePathLabel = new Label(details, SWT.NONE);
        tilePathLabel.setText("Tile path:");
        Composite tilePathRow = new Composite(details, SWT.NONE);
        tilePathRow.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        GridLayout tilePathLayout = new GridLayout(2, false);
        tilePathLayout.marginWidth = 0;
        tilePathLayout.marginHeight = 0;
        tilePathRow.setLayout(tilePathLayout);

        Text tilePathText = new Text(tilePathRow, SWT.BORDER);
        tilePathText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        org.eclipse.swt.widgets.Button browseTileButton = new org.eclipse.swt.widgets.Button(tilePathRow, SWT.PUSH);
        browseTileButton.setText("Examinar...");
        browseTileButton.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));

        Composite actions = new Composite(details, SWT.NONE);
        actions.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false, 2, 1));
        GridLayout actionsLayout = new GridLayout(3, true);
        actionsLayout.marginWidth = 0;
        actions.setLayout(actionsLayout);

        org.eclipse.swt.widgets.Button saveButton = new org.eclipse.swt.widgets.Button(actions, SWT.PUSH);
        saveButton.setText(I18n.t("button.saveChanges"));
        saveButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        org.eclipse.swt.widgets.Button deleteButton = new org.eclipse.swt.widgets.Button(actions, SWT.PUSH);
        deleteButton.setText(I18n.t("button.deleteCard"));
        deleteButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        org.eclipse.swt.widgets.Button closeButton = new org.eclipse.swt.widgets.Button(actions, SWT.PUSH);
        closeButton.setText(I18n.t("button.close"));
        closeButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        List<DungeonCard> maintenanceCards = new ArrayList<>();
        final long[] selectedCardId = new long[] {-1L};

        final Runnable[] syncSelectionDetails = new Runnable[1];
        syncSelectionDetails[0] = () -> {
            int selectedIndex = maintenanceList.getSelectionIndex();
            boolean hasSelection = selectedIndex >= 0 && selectedIndex < maintenanceCards.size();
            saveButton.setEnabled(hasSelection);
            deleteButton.setEnabled(hasSelection);
            copiesSpinner.setEnabled(hasSelection);
            enabledCheckbox.setEnabled(hasSelection);
            tilePathText.setEnabled(hasSelection);
            browseTileButton.setEnabled(hasSelection);
            boolean hasEnvironmentSelection = bulkEnvironmentCombo.getSelectionIndex() >= 0;
            enableEnvironmentButton.setEnabled(hasEnvironmentSelection);
            disableEnvironmentButton.setEnabled(hasEnvironmentSelection);

            if (!hasSelection) {
                nameValue.setText("");
                typeValue.setText("");
                environmentValue.setText("");
                copiesSpinner.setSelection(0);
                enabledCheckbox.setSelection(false);
                tilePathText.setText("");
                details.layout(true, true);
                return;
            }

            DungeonCard card = maintenanceCards.get(selectedIndex);
            selectedCardId[0] = card.getId();
            nameValue.setText(card.getName());
            typeValue.setText(card.getType().getLabel());
            environmentValue.setText(card.getEnvironment());
            copiesSpinner.setSelection(card.getCopyCount());
            enabledCheckbox.setSelection(card.isEnabled());
            tilePathText.setText(card.getTileImagePath());
            details.layout(true, true);
        };

        Runnable reloadMaintenanceCards = () -> {
            try {
                maintenanceCards.clear();
                maintenanceCards.addAll(cardStore.loadCards());
            } catch (DungeonCardStorageException ex) {
                showError("Mantenimiento", "No se han podido cargar las cartas: " + ex.getMessage());
                return;
            }

            maintenanceList.removeAll();
            for (DungeonCard card : maintenanceCards) {
                maintenanceList.add(formatMaintenanceCardEntry(card));
            }

            String previouslySelectedEnvironment = bulkEnvironmentCombo.getText();
            java.util.List<String> environments = maintenanceCards.stream()
                    .map(DungeonCard::getEnvironment)
                    .filter(environment -> environment != null && !environment.isBlank())
                    .distinct()
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
            bulkEnvironmentCombo.setItems(environments.toArray(String[]::new));
            if (!previouslySelectedEnvironment.isBlank()) {
                int selectedIndex = bulkEnvironmentCombo.indexOf(previouslySelectedEnvironment);
                if (selectedIndex >= 0) {
                    bulkEnvironmentCombo.select(selectedIndex);
                }
            }
            if (bulkEnvironmentCombo.getSelectionIndex() < 0 && !environments.isEmpty()) {
                bulkEnvironmentCombo.select(0);
            }

            int indexToSelect = -1;
            if (selectedCardId[0] >= 0) {
                for (int i = 0; i < maintenanceCards.size(); i++) {
                    if (maintenanceCards.get(i).getId() == selectedCardId[0]) {
                        indexToSelect = i;
                        break;
                    }
                }
            }

            if (indexToSelect < 0 && !maintenanceCards.isEmpty()) {
                indexToSelect = 0;
                selectedCardId[0] = maintenanceCards.get(0).getId();
            }

            if (indexToSelect >= 0) {
                maintenanceList.select(indexToSelect);
            }
            syncSelectionDetails[0].run();
            refreshCards();
        };

        bulkEnvironmentCombo.addListener(SWT.Selection, event -> syncSelectionDetails[0].run());

        final java.util.function.Consumer<Boolean> applyEnvironmentEnabled = enabled -> {
            String selectedEnvironment = bulkEnvironmentCombo.getText();
            if (selectedEnvironment == null || selectedEnvironment.isBlank()) {
                return;
            }

            int changed = 0;
            for (DungeonCard card : maintenanceCards) {
                if (!selectedEnvironment.equalsIgnoreCase(card.getEnvironment()) || card.isEnabled() == enabled) {
                    continue;
                }
                try {
                    cardStore.updateCard(new DungeonCard(
                            card.getId(),
                            card.getName(),
                            card.getType(),
                            card.getEnvironment(),
                            card.getCopyCount(),
                            enabled,
                            card.getDescriptionText(),
                            card.getRulesText(),
                            card.getTileImagePath()));
                    changed++;
                } catch (DungeonCardStorageException ex) {
                    showError(
                            I18n.t("dialog.maintenance.title"),
                            I18n.t("dialog.tileConfig.bulkUpdateError") + ": " + ex.getMessage());
                    return;
                }
            }

            if (changed > 0) {
                showInfo(
                        I18n.t("dialog.maintenance.title"),
                        String.format(I18n.t("dialog.tileConfig.bulkUpdated"), changed, selectedEnvironment));
            }
            reloadMaintenanceCards.run();
        };

        enableEnvironmentButton.addListener(SWT.Selection, event -> applyEnvironmentEnabled.accept(Boolean.TRUE));
        disableEnvironmentButton.addListener(SWT.Selection, event -> applyEnvironmentEnabled.accept(Boolean.FALSE));

        maintenanceList.addListener(SWT.Selection, event -> syncSelectionDetails[0].run());

        browseTileButton.addListener(SWT.Selection, event -> {
            int selectedIndex = maintenanceList.getSelectionIndex();
            if (selectedIndex < 0 || selectedIndex >= maintenanceCards.size()) {
                return;
            }

            FileDialog fileDialog = new FileDialog(dialog, SWT.OPEN);
            fileDialog.setText("Seleccionar tile");
            fileDialog.setFilterExtensions(new String[] {"*.png;*.jpg;*.jpeg;*.gif;*.bmp", "*.*"});
            fileDialog.setFilterNames(new String[] {"Image files", "All files"});
            String selectedPath = fileDialog.open();
            if (selectedPath == null || selectedPath.isBlank()) {
                return;
            }

            Path absolutePath = Path.of(selectedPath).toAbsolutePath().normalize();
            Path normalizedProjectRoot = projectRoot.toAbsolutePath().normalize();
            if (absolutePath.startsWith(normalizedProjectRoot)) {
                tilePathText.setText(normalizedProjectRoot.relativize(absolutePath).toString().replace('\\', '/'));
            } else {
                tilePathText.setText(absolutePath.toString().replace('\\', '/'));
            }
        });

        saveButton.addListener(SWT.Selection, event -> {
            int selectedIndex = maintenanceList.getSelectionIndex();
            if (selectedIndex < 0 || selectedIndex >= maintenanceCards.size()) {
                return;
            }

            DungeonCard card = maintenanceCards.get(selectedIndex);
            try {
                cardStore.updateCard(new DungeonCard(
                        card.getId(),
                        card.getName(),
                        card.getType(),
                        card.getEnvironment(),
                        copiesSpinner.getSelection(),
                        enabledCheckbox.getSelection(),
                        card.getDescriptionText(),
                        card.getRulesText(),
                        tilePathText.getText().trim()));
                selectedCardId[0] = card.getId();
                reloadMaintenanceCards.run();
            } catch (DungeonCardStorageException ex) {
                showError("Mantenimiento", "No se han podido guardar los cambios: " + ex.getMessage());
            }
        });

        deleteButton.addListener(SWT.Selection, event -> {
            int selectedIndex = maintenanceList.getSelectionIndex();
            if (selectedIndex < 0 || selectedIndex >= maintenanceCards.size()) {
                return;
            }

            DungeonCard card = maintenanceCards.get(selectedIndex);
            MessageBox confirmBox = new MessageBox(dialog, SWT.ICON_WARNING | SWT.YES | SWT.NO);
            confirmBox.setText("Eliminar carta");
            confirmBox.setMessage("¿Seguro que quieres eliminar la carta \"" + card.getName() + "\"?");
            if (confirmBox.open() != SWT.YES) {
                return;
            }

            try {
                cardStore.deleteCard(card.getId());
                selectedCardId[0] = -1L;
                reloadMaintenanceCards.run();
            } catch (DungeonCardStorageException ex) {
                showError("Mantenimiento", "No se ha podido eliminar la carta: " + ex.getMessage());
            }
        });

        closeButton.addListener(SWT.Selection, event -> dialog.close());
        reloadMaintenanceCards.run();
        dialog.open();
        while (!dialog.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }
    }

    private void openNewDungeonDialog() {
        java.util.List<String> environments;
        try {
            environments = cardStore.loadEnvironments();
        } catch (DungeonCardStorageException ex) {
            showError(I18n.t("dialog.newDungeon.title"), I18n.t("dialog.newDungeon.error.loadEnvironments") + ex.getMessage());
            return;
        }

        if (environments.isEmpty()) {
            showInfo(I18n.t("dialog.newDungeon.title"), I18n.t("dialog.newDungeon.info.noCards"));
            return;
        }

        Shell dialog = new Shell(shell, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.RESIZE);
        dialog.setText(I18n.t("dialog.newDungeon.title"));
        dialog.setBackground(theme.shellBackground);
        dialog.setLayout(new GridLayout(1, false));
        dialog.setSize(980, 820);

        createDialogHeader(
                dialog,
                I18n.t("dialog.newDungeon.title"),
                I18n.t("dialog.newDungeon.subtitle"));

        ScrolledComposite scroll = new ScrolledComposite(dialog, SWT.V_SCROLL);
        scroll.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        scroll.setExpandHorizontal(true);
        scroll.setExpandVertical(true);
        scroll.setBackground(theme.shellBackground);

        Composite content = new Composite(scroll, SWT.NONE);
        content.setBackground(theme.shellBackground);
        GridLayout contentLayout = new GridLayout(1, false);
        contentLayout.marginWidth = 0;
        contentLayout.marginHeight = 0;
        contentLayout.verticalSpacing = 14;
        content.setLayout(contentLayout);

        Composite formPanel = createDarkPanel(content, 4);
        formPanel.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        Label environmentLabel = new Label(formPanel, SWT.NONE);
        environmentLabel.setText(I18n.t("dialog.newDungeon.environment"));
        styleDarkLabel(environmentLabel, false);
        environmentLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

        org.eclipse.swt.widgets.Combo environmentCombo = new org.eclipse.swt.widgets.Combo(
                formPanel,
                SWT.DROP_DOWN | SWT.READ_ONLY);
        styleCombo(environmentCombo);
        environmentCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        environmentCombo.setItems(environments.toArray(String[]::new));
        environmentCombo.select(0);

        Label objectiveRoomLabel = new Label(formPanel, SWT.NONE);
        objectiveRoomLabel.setText(I18n.t("dialog.newDungeon.objectiveRoom"));
        styleDarkLabel(objectiveRoomLabel, false);
        objectiveRoomLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

        org.eclipse.swt.widgets.Combo objectiveRoomCombo = new org.eclipse.swt.widgets.Combo(
                formPanel,
                SWT.DROP_DOWN | SWT.READ_ONLY);
        styleCombo(objectiveRoomCombo);
        objectiveRoomCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Label missionLabel = new Label(formPanel, SWT.NONE);
        missionLabel.setText(I18n.t("dialog.newDungeon.mission"));
        styleDarkLabel(missionLabel, false);
        missionLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

        org.eclipse.swt.widgets.Combo missionCombo = new org.eclipse.swt.widgets.Combo(
                formPanel,
                SWT.DROP_DOWN | SWT.READ_ONLY);
        styleCombo(missionCombo);
        missionCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Label ambienceLabel = new Label(formPanel, SWT.NONE);
        ambienceLabel.setText(I18n.t("dialog.newDungeon.ambience"));
        styleDarkLabel(ambienceLabel, false);
        ambienceLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

        org.eclipse.swt.widgets.Combo ambienceCombo = new org.eclipse.swt.widgets.Combo(
                formPanel,
                SWT.DROP_DOWN | SWT.READ_ONLY);
        styleCombo(ambienceCombo);
        ambienceCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        ambienceCombo.setItems(AdventureAmbience.displayNames());
        ambienceCombo.select(0);

        Label levelLabel = new Label(formPanel, SWT.NONE);
        levelLabel.setText(I18n.t("dialog.newDungeon.level"));
        styleDarkLabel(levelLabel, false);
        levelLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

        Spinner levelSpinner = new Spinner(formPanel, SWT.BORDER);
        styleSpinner(levelSpinner);
        levelSpinner.setMinimum(1);
        levelSpinner.setMaximum(10);
        levelSpinner.setSelection(Math.max(1, Math.min(10, Settings.getSettingAsInt(Settings.ADVENTURE_LEVEL))));
        levelSpinner.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Label deckSizeLabel = new Label(formPanel, SWT.NONE);
        deckSizeLabel.setText(I18n.t("dialog.newDungeon.deckSize"));
        styleDarkLabel(deckSizeLabel, false);
        deckSizeLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

        int defaultDeckSize = Math.max(2, Settings.getSettingAsInt(Settings.ADVENTURE_DEFAULT_DECK_SIZE));
        int defaultRoomCount = Math.max(1, Settings.getSettingAsInt(Settings.ADVENTURE_DEFAULT_ROOM_COUNT));

        Spinner deckSizeSpinner = new Spinner(formPanel, SWT.BORDER);
        styleSpinner(deckSizeSpinner);
        deckSizeSpinner.setMinimum(2);
        deckSizeSpinner.setMaximum(200);
        deckSizeSpinner.setSelection(defaultDeckSize);
        deckSizeSpinner.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Label roomCountLabel = new Label(formPanel, SWT.NONE);
        roomCountLabel.setText(I18n.t("dialog.newDungeon.roomCount"));
        styleDarkLabel(roomCountLabel, false);
        roomCountLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

        Spinner roomCountSpinner = new Spinner(formPanel, SWT.BORDER);
        styleSpinner(roomCountSpinner);
        roomCountSpinner.setMinimum(1);
        roomCountSpinner.setMaximum(deckSizeSpinner.getSelection() - 1);
        roomCountSpinner.setSelection(Math.min(defaultRoomCount, roomCountSpinner.getMaximum()));
        roomCountSpinner.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Label helpText = new Label(formPanel, SWT.WRAP);
        GridData helpData = new GridData(SWT.FILL, SWT.TOP, true, false, 4, 1);
        helpText.setLayoutData(helpData);
        helpText.setText(I18n.t("dialog.newDungeon.help"));
        styleDarkLabel(helpText, false);

        Composite missionPanel = createDarkPanel(content, 1);
        missionPanel.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        Label missionRulesLabel = new Label(missionPanel, SWT.NONE);
        missionRulesLabel.setText(I18n.t("dialog.newDungeon.specialRules"));
        styleDarkLabel(missionRulesLabel, false);
        missionRulesLabel.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false));

        Text missionRulesText = new Text(missionPanel, SWT.BORDER | SWT.WRAP | SWT.MULTI | SWT.V_SCROLL | SWT.READ_ONLY);
        GridData missionRulesData = new GridData(SWT.FILL, SWT.FILL, true, false);
        missionRulesData.heightHint = 150;
        missionRulesText.setLayoutData(missionRulesData);
        missionRulesText.setBackground(theme.mist);
        missionRulesText.setForeground(theme.ink);

        Composite weightsPanel = createDarkPanel(content, 5);
        weightsPanel.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        Label objectiveMonsterWeightsLabel = new Label(weightsPanel, SWT.WRAP);
        objectiveMonsterWeightsLabel.setText(I18n.t("dialog.objectiveMonsters.weights"));
        objectiveMonsterWeightsLabel.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 5, 1));
        styleDarkLabel(objectiveMonsterWeightsLabel, true);

        Label objectiveMonsterWeightsHint = new Label(weightsPanel, SWT.WRAP);
        objectiveMonsterWeightsHint.setText(I18n.t("dialog.objectiveMonsters.weightsHint"));
        objectiveMonsterWeightsHint.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 5, 1));
        styleDarkLabel(objectiveMonsterWeightsHint, false);

        Label easyWeightLabel = new Label(weightsPanel, SWT.NONE);
        easyWeightLabel.setText(I18n.t("dialog.objectiveMonsters.easy"));
        styleDarkLabel(easyWeightLabel, false);
        easyWeightLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false));

        Label normalWeightLabel = new Label(weightsPanel, SWT.NONE);
        normalWeightLabel.setText(I18n.t("dialog.objectiveMonsters.normal"));
        styleDarkLabel(normalWeightLabel, false);
        normalWeightLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false));

        Label hardWeightLabel = new Label(weightsPanel, SWT.NONE);
        hardWeightLabel.setText(I18n.t("dialog.objectiveMonsters.hard"));
        styleDarkLabel(hardWeightLabel, false);
        hardWeightLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false));

        Label veryHardWeightLabel = new Label(weightsPanel, SWT.NONE);
        veryHardWeightLabel.setText(I18n.t("dialog.objectiveMonsters.veryHard"));
        styleDarkLabel(veryHardWeightLabel, false);
        veryHardWeightLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false));

        Label extremeWeightLabel = new Label(weightsPanel, SWT.NONE);
        extremeWeightLabel.setText(I18n.t("dialog.objectiveMonsters.extreme"));
        styleDarkLabel(extremeWeightLabel, false);
        extremeWeightLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false));

        Spinner easyWeightSpinner = new Spinner(weightsPanel, SWT.BORDER);
        styleSpinner(easyWeightSpinner);
        easyWeightSpinner.setMinimum(0);
        easyWeightSpinner.setMaximum(99);
        easyWeightSpinner.setSelection(Math.max(0, Settings.getSettingAsInt(Settings.OBJECTIVE_MONSTER_EASY_WEIGHT)));
        easyWeightSpinner.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Spinner normalWeightSpinner = new Spinner(weightsPanel, SWT.BORDER);
        styleSpinner(normalWeightSpinner);
        normalWeightSpinner.setMinimum(0);
        normalWeightSpinner.setMaximum(99);
        normalWeightSpinner.setSelection(Math.max(0, Settings.getSettingAsInt(Settings.OBJECTIVE_MONSTER_NORMAL_WEIGHT)));
        normalWeightSpinner.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Spinner hardWeightSpinner = new Spinner(weightsPanel, SWT.BORDER);
        styleSpinner(hardWeightSpinner);
        hardWeightSpinner.setMinimum(0);
        hardWeightSpinner.setMaximum(99);
        hardWeightSpinner.setSelection(Math.max(0, Settings.getSettingAsInt(Settings.OBJECTIVE_MONSTER_HARD_WEIGHT)));
        hardWeightSpinner.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Spinner veryHardWeightSpinner = new Spinner(weightsPanel, SWT.BORDER);
        styleSpinner(veryHardWeightSpinner);
        veryHardWeightSpinner.setMinimum(0);
        veryHardWeightSpinner.setMaximum(99);
        veryHardWeightSpinner.setSelection(Math.max(0, Settings.getSettingAsInt(Settings.OBJECTIVE_MONSTER_VERY_HARD_WEIGHT)));
        veryHardWeightSpinner.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Spinner extremeWeightSpinner = new Spinner(weightsPanel, SWT.BORDER);
        styleSpinner(extremeWeightSpinner);
        extremeWeightSpinner.setMinimum(0);
        extremeWeightSpinner.setMaximum(99);
        extremeWeightSpinner.setSelection(Math.max(0, Settings.getSettingAsInt(Settings.OBJECTIVE_MONSTER_EXTREME_WEIGHT)));
        extremeWeightSpinner.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        scroll.setContent(content);
        scroll.addListener(SWT.Resize, event -> {
            Rectangle area = scroll.getClientArea();
            Point size = content.computeSize(Math.max(680, area.width), SWT.DEFAULT);
            content.setSize(size);
            scroll.setMinSize(size);
        });

        Composite buttons = new Composite(dialog, SWT.NONE);
        buttons.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false));
        GridLayout buttonsLayout = new GridLayout(2, true);
        buttonsLayout.marginWidth = 0;
        buttons.setLayout(buttonsLayout);
        buttons.setBackground(theme.shellBackground);

        org.eclipse.swt.widgets.Button startButton = new org.eclipse.swt.widgets.Button(buttons, SWT.PUSH);
        startButton.setText(I18n.t("button.startAdventure"));
        styleActionButton(startButton);
        startButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        org.eclipse.swt.widgets.Button cancelButton = new org.eclipse.swt.widgets.Button(buttons, SWT.PUSH);
        cancelButton.setText(I18n.t("button.cancel"));
        styleActionButton(cancelButton);
        cancelButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Map<String, DungeonCard> objectiveByName = new LinkedHashMap<>();
        Map<String, ObjectiveRoomAdventure> adventureByName = new LinkedHashMap<>();

        Runnable syncAdventurePreview = () -> {
            ObjectiveRoomAdventure adventure = adventureByName.get(missionCombo.getText());
            if (adventure == null) {
                missionRulesText.setText("");
                return;
            }
            missionRulesText.setText(adventure.rulesText());
        };

        Runnable reloadAdventures = () -> {
            String selectedObjectiveRoomName = objectiveRoomCombo.getText();
            missionCombo.removeAll();
            adventureByName.clear();

            if (selectedObjectiveRoomName == null || selectedObjectiveRoomName.isBlank()) {
                missionRulesText.setText("");
                startButton.setEnabled(false);
                return;
            }

            try {
                List<ObjectiveRoomAdventure> adventures = objectiveRoomAdventureRepository
                        .loadAdventuresForObjectiveRoom(selectedObjectiveRoomName);
                for (ObjectiveRoomAdventure adventure : adventures) {
                    adventureByName.put(adventure.name(), adventure);
                    missionCombo.add(adventure.name());
                }
                if (!adventures.isEmpty()) {
                    missionCombo.select(0);
                    syncAdventurePreview.run();
                    startButton.setEnabled(true);
                } else {
                    missionRulesText.setText("");
                    startButton.setEnabled(false);
                }
            } catch (ObjectiveRoomAdventureRepositoryException ex) {
                showError(I18n.t("dialog.newDungeon.title"), I18n.t("dialog.newDungeon.error.loadAdventures") + ex.getMessage());
                missionRulesText.setText("");
                startButton.setEnabled(false);
            }
        };

        Runnable reloadObjectives = () -> {
            String environment = environmentCombo.getText();
            java.util.List<DungeonCard> objectiveRooms;
            try {
                objectiveRooms = cardStore.loadObjectiveRoomsByEnvironment(environment);
            } catch (DungeonCardStorageException ex) {
                showError(I18n.t("dialog.newDungeon.title"), I18n.t("dialog.newDungeon.error.loadObjectiveRooms") + ex.getMessage());
                objectiveRooms = List.of();
            }

            objectiveByName.clear();
            objectiveRoomCombo.removeAll();
            for (DungeonCard card : objectiveRooms) {
                objectiveByName.put(card.getName(), card);
                objectiveRoomCombo.add(card.getName());
            }

            if (!objectiveRooms.isEmpty()) {
                objectiveRoomCombo.select(0);
                reloadAdventures.run();
            } else {
                startButton.setEnabled(false);
                missionCombo.removeAll();
                missionRulesText.setText("");
                showInfo(I18n.t("dialog.newDungeon.title"), I18n.t("dialog.newDungeon.info.noObjectiveRooms"));
            }
        };

        environmentCombo.addListener(SWT.Selection, event -> reloadObjectives.run());
        objectiveRoomCombo.addListener(SWT.Selection, event -> reloadAdventures.run());
        missionCombo.addListener(SWT.Selection, event -> syncAdventurePreview.run());

        deckSizeSpinner.addListener(SWT.Modify, event -> {
            int maxRooms = Math.max(1, deckSizeSpinner.getSelection() - 1);
            roomCountSpinner.setMaximum(maxRooms);
            if (roomCountSpinner.getSelection() > maxRooms) {
                roomCountSpinner.setSelection(maxRooms);
            }
        });

        startButton.addListener(SWT.Selection, event -> {
            if (objectiveRoomCombo.getSelectionIndex() < 0) {
                showError(I18n.t("dialog.newDungeon.title"), I18n.t("dialog.newDungeon.error.selectObjectiveRoom"));
                return;
            }
            if (missionCombo.getSelectionIndex() < 0) {
                showError(I18n.t("dialog.newDungeon.title"), I18n.t("dialog.newDungeon.error.selectMission"));
                return;
            }
            String selectedObjectiveName = objectiveRoomCombo.getText();
            DungeonCard objectiveRoom = objectiveByName.get(selectedObjectiveName);
            if (objectiveRoom == null) {
                showError(I18n.t("dialog.newDungeon.title"), I18n.t("dialog.newDungeon.error.resolveObjectiveRoom"));
                return;
            }
            ObjectiveRoomAdventure selectedAdventure = adventureByName.get(missionCombo.getText());
            if (selectedAdventure == null) {
                showError(I18n.t("dialog.newDungeon.title"), I18n.t("dialog.newDungeon.error.resolveMission"));
                return;
            }

            try {
                String selectedEnvironment = environmentCombo.getText();
                Settings.setSetting(Settings.OBJECTIVE_MONSTER_EASY_WEIGHT, Integer.toString(easyWeightSpinner.getSelection()));
                Settings.setSetting(Settings.OBJECTIVE_MONSTER_NORMAL_WEIGHT, Integer.toString(normalWeightSpinner.getSelection()));
                Settings.setSetting(Settings.OBJECTIVE_MONSTER_HARD_WEIGHT, Integer.toString(hardWeightSpinner.getSelection()));
                Settings.setSetting(Settings.OBJECTIVE_MONSTER_VERY_HARD_WEIGHT, Integer.toString(veryHardWeightSpinner.getSelection()));
                Settings.setSetting(Settings.OBJECTIVE_MONSTER_EXTREME_WEIGHT, Integer.toString(extremeWeightSpinner.getSelection()));
                List<DungeonCard> deck = buildAdventureDeck(
                        selectedEnvironment,
                        objectiveRoom,
                        deckSizeSpinner.getSelection(),
                        roomCountSpinner.getSelection());
                String selectedAmbience = ambienceCombo.getText();
                int selectedLevel = Math.max(1, Math.min(10, levelSpinner.getSelection()));
                setActiveAdventureContext(AdventureAmbience.fromDisplayName(selectedAmbience), selectedLevel);
                dialog.close();
                openAdventureSimulator(
                        deck,
                        selectedAdventure,
                        selectedAmbience,
                        selectedLevel,
                        selectedEnvironment);
            } catch (IllegalArgumentException ex) {
                showError(I18n.t("dialog.newDungeon.title"), ex.getMessage());
            }
        });

        cancelButton.addListener(SWT.Selection, event -> dialog.close());

        reloadObjectives.run();
        fitShellToDisplay(dialog, 48);
        dialog.open();
        while (!dialog.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }
    }

    private List<DungeonCard> buildAdventureDeck(
            String environment,
            DungeonCard objectiveRoom,
            int deckSize,
            int dungeonRoomCount) {
        if (deckSize < 2) {
            throw new IllegalArgumentException(I18n.t("dialog.newDungeon.error.deckTooSmall"));
        }
        if (dungeonRoomCount < 1) {
            throw new IllegalArgumentException(I18n.t("dialog.newDungeon.error.noRoomCards"));
        }
        if (dungeonRoomCount > deckSize - 1) {
            throw new IllegalArgumentException(I18n.t("dialog.newDungeon.error.roomCountTooLarge"));
        }

        List<DungeonCard> environmentCards = cards.stream()
                .filter(card -> environment.equalsIgnoreCase(card.getEnvironment()))
                .filter(DungeonCard::isEnabled)
                .filter(card -> card.getCopyCount() > 0)
                .collect(Collectors.toList());

        if (!objectiveRoom.isEnabled() || objectiveRoom.getCopyCount() <= 0) {
            throw new IllegalArgumentException(I18n.t("dialog.newDungeon.error.objectiveRoomUnavailable"));
        }

        List<DungeonCard> dungeonRoomPool = environmentCards.stream()
                .filter(card -> card.getType() == CardType.DUNGEON_ROOM)
                .collect(Collectors.toList());
        int availableDungeonRoomCopies = dungeonRoomPool.stream().mapToInt(DungeonCard::getCopyCount).sum();
        if (availableDungeonRoomCopies <= 0) {
            throw new IllegalArgumentException(I18n.t("dialog.newDungeon.error.noDungeonRoomCards"));
        }
        if (dungeonRoomCount > availableDungeonRoomCopies) {
            throw new IllegalArgumentException(I18n.t("dialog.newDungeon.error.notEnoughDungeonRoomCopies"));
        }

        List<DungeonCard> corridorAndSpecialPool = environmentCards.stream()
                .filter(card -> card.getType() == CardType.CORRIDOR || card.getType() == CardType.SPECIAL)
                .collect(Collectors.toList());
        int fillerCount = deckSize - dungeonRoomCount - 1;
        int availableFillerCopies = corridorAndSpecialPool.stream().mapToInt(DungeonCard::getCopyCount).sum();
        if (fillerCount > 0 && availableFillerCopies <= 0) {
            throw new IllegalArgumentException(I18n.t("dialog.newDungeon.error.noFillerCards"));
        }
        if (fillerCount > availableFillerCopies) {
            throw new IllegalArgumentException(I18n.t("dialog.newDungeon.error.notEnoughFillerCopies"));
        }

        Random random = new Random();
        List<DungeonCard> dungeonRooms = pickCardsByAvailableCopies(dungeonRoomPool, dungeonRoomCount, random);
        List<DungeonCard> fillers = pickCardsByAvailableCopies(corridorAndSpecialPool, fillerCount, random);

        List<DungeonCard> nonObjectiveCards = new ArrayList<>(deckSize - 1);
        nonObjectiveCards.addAll(dungeonRooms);
        nonObjectiveCards.addAll(fillers);
        Collections.shuffle(nonObjectiveCards, random);

        List<DungeonCard> deck = new ArrayList<>(Collections.nCopies(deckSize, null));
        int minObjectiveIndex = Math.max(0, deckSize - 5);
        int objectiveIndex = minObjectiveIndex + random.nextInt(deckSize - minObjectiveIndex);
        deck.set(objectiveIndex, objectiveRoom);

        int nonObjectiveIndex = 0;
        for (int i = 0; i < deck.size(); i++) {
            if (deck.get(i) == null) {
                deck.set(i, nonObjectiveCards.get(nonObjectiveIndex));
                nonObjectiveIndex++;
            }
        }
        return deck;
    }

    private List<DungeonCard> pickCardsByAvailableCopies(List<DungeonCard> pool, int count, Random random) {
        if (count <= 0) {
            return new ArrayList<>();
        }

        List<DungeonCard> expandedPool = new ArrayList<>();
        for (DungeonCard card : pool) {
            for (int i = 0; i < card.getCopyCount(); i++) {
                expandedPool.add(card);
            }
        }
        Collections.shuffle(expandedPool, random);
        return new ArrayList<>(expandedPool.subList(0, count));
    }

    private Set<Long> collectAdventureCardIds(AdventureSimulatorState state) {
        java.util.Set<Long> ids = new java.util.HashSet<>();
        for (List<DungeonCard> pile : state.piles) {
            for (DungeonCard card : pile) {
                ids.add(card.getId());
            }
        }
        for (List<DungeonCard> history : state.histories) {
            for (DungeonCard card : history) {
                ids.add(card.getId());
            }
        }
        if (state.selectedCard != null) {
            ids.add(state.selectedCard.getId());
        }
        return ids;
    }

    private List<DungeonCard> pickAdditionalAdventureCards(String environment, Set<Long> existingIds) {
        List<DungeonCard> eligible = cards.stream()
                .filter(card -> environment.equalsIgnoreCase(card.getEnvironment()))
                .filter(DungeonCard::isEnabled)
                .filter(card -> card.getCopyCount() > 0)
                .filter(card -> card.getType() != CardType.OBJECTIVE_ROOM)
                .filter(card -> !existingIds.contains(card.getId()))
                .collect(Collectors.toCollection(ArrayList::new));

        Collections.shuffle(eligible, new Random());
        return eligible;
    }

    private int objectiveMonsterWeight(ObjectiveMonsterDifficulty difficulty) {
        if (difficulty == null) {
            return 0;
        }
        return switch (difficulty.labelKey()) {
            case "difficulty.easy" -> Math.max(0, Settings.getSettingAsInt(Settings.OBJECTIVE_MONSTER_EASY_WEIGHT));
            case "difficulty.normal" -> Math.max(0, Settings.getSettingAsInt(Settings.OBJECTIVE_MONSTER_NORMAL_WEIGHT));
            case "difficulty.hard" -> Math.max(0, Settings.getSettingAsInt(Settings.OBJECTIVE_MONSTER_HARD_WEIGHT));
            case "difficulty.veryHard" -> Math.max(0, Settings.getSettingAsInt(Settings.OBJECTIVE_MONSTER_VERY_HARD_WEIGHT));
            default -> Math.max(0, Settings.getSettingAsInt(Settings.OBJECTIVE_MONSTER_EXTREME_WEIGHT));
        };
    }

    private ObjectiveMonsterDifficulty rollObjectiveMonsterDifficulty() {
        int totalWeight = OBJECTIVE_MONSTER_DIFFICULTIES.stream()
                .mapToInt(this::objectiveMonsterWeight)
                .sum();
        if (totalWeight <= 0) {
            return null;
        }

        int roll = new Random().nextInt(totalWeight);
        for (ObjectiveMonsterDifficulty difficulty : OBJECTIVE_MONSTER_DIFFICULTIES) {
            roll -= objectiveMonsterWeight(difficulty);
            if (roll < 0) {
                return difficulty;
            }
        }
        return OBJECTIVE_MONSTER_DIFFICULTIES.get(OBJECTIVE_MONSTER_DIFFICULTIES.size() - 1);
    }

    private List<Object> activeDungeonMonsterEntries(ContentRepository repository) {
        AdventureAmbience selectedAmbience =
                AdventureAmbience.fromStorageValue(Settings.getSetting(Settings.ADVENTURE_AMBIENCE));

        List<Object> activeEntries = repository.tables().values().stream()
                .filter(Table::isActive)
                .filter(table -> table.getTableKind() == TableKind.DUNGEON)
                .flatMap(table -> table.getMonsterEntries().stream())
                .collect(Collectors.toCollection(ArrayList::new));

        if (selectedAmbience.isGeneric()) {
            return activeEntries;
        }

        List<Object> ambienceFiltered = activeEntries.stream()
                .filter(entry -> matchesObjectiveMonsterAmbience(entry, selectedAmbience))
                .collect(Collectors.toCollection(ArrayList::new));
        return ambienceFiltered.isEmpty() ? activeEntries : ambienceFiltered;
    }

    private boolean matchesObjectiveMonsterAmbience(Object entry, AdventureAmbience selectedAmbience) {
        if (selectedAmbience == null || selectedAmbience.isGeneric()) {
            return true;
        }
        if (entry instanceof MonsterEntry monsterEntry) {
            return selectedAmbience.matches(monsterEntry.ambiences);
        }
        if (entry instanceof TableReferenceEntry tableReferenceEntry) {
            return tableReferenceEntry.ambiences.isEmpty()
                    || selectedAmbience.matches(tableReferenceEntry.ambiences);
        }
        if (entry instanceof MonsterGroup group) {
            if (group.isEmpty()) {
                return false;
            }
            for (Object nested : group) {
                if (!matchesObjectiveMonsterAmbience(nested, selectedAmbience)) {
                    return false;
                }
            }
            return true;
        }
        return true;
    }

    private int entryLevel(Object entry) {
        if (entry instanceof MonsterEntry monsterEntry) {
            return monsterEntry.level;
        }
        if (entry instanceof TableReferenceEntry tableReferenceEntry) {
            return tableReferenceEntry.level;
        }
        if (entry instanceof MonsterGroup group) {
            return group.level;
        }
        return 1;
    }

    private List<Integer> availableObjectiveMonsterLevels(ContentRepository repository) {
        return activeDungeonMonsterEntries(repository).stream()
                .map(this::entryLevel)
                .distinct()
                .sorted()
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private List<Integer> resolveObjectiveEncounterLevels(ContentRepository repository, int requestedLevel) {
        List<Integer> availableLevels = availableObjectiveMonsterLevels(repository);
        if (availableLevels.isEmpty()) {
            return List.of();
        }
        if (availableLevels.contains(requestedLevel)) {
            return List.of(requestedLevel);
        }

        for (int i = availableLevels.size() - 1; i >= 0; i--) {
            int availableLevel = availableLevels.get(i);
            if (availableLevel <= requestedLevel) {
                return List.of(availableLevel, availableLevel);
            }
        }

        int fallbackLevel = availableLevels.get(0);
        return List.of(fallbackLevel, fallbackLevel);
    }

    private Object pickRandomObjectiveMonsterEntry(ContentRepository repository, int level) {
        List<Object> entries = activeDungeonMonsterEntries(repository).stream()
                .filter(entry -> entryLevel(entry) == level)
                .collect(Collectors.toCollection(ArrayList::new));
        if (entries.isEmpty()) {
            return null;
        }
        Object selected = entries.get(new Random().nextInt(entries.size()));
        if (selected instanceof TableReferenceEntry tableReferenceEntry) {
            return TABLE_DRAW_SERVICE.resolveEntry(tableReferenceEntry);
        }
        return selected;
    }

    private List<Object> generateObjectiveRoomMonsterEntries(ContentRepository repository, int dungeonLevel) {
        ObjectiveMonsterDifficulty difficulty = rollObjectiveMonsterDifficulty();
        if (difficulty == null) {
            return null;
        }

        showInfo(
                I18n.t("button.generateObjectiveRoomMonsters"),
                String.format(I18n.t("simulator.objectiveMonstersDifficulty"), I18n.t(difficulty.labelKey())));

        List<Object> entries = new ArrayList<>();
        for (int offset : difficulty.offsets()) {
            List<Integer> resolvedLevels = resolveObjectiveEncounterLevels(repository, dungeonLevel + offset);
            for (int resolvedLevel : resolvedLevels) {
                Object entry = pickRandomObjectiveMonsterEntry(repository, resolvedLevel);
                if (entry != null) {
                    entries.add(entry);
                }
            }
        }
        return entries;
    }

    private String buildAdventureSimulatorSubtitle(
            ObjectiveRoomAdventure selectedAdventure,
            String selectedAmbience,
            int selectedLevel) {
        String adventureName = selectedAdventure == null ? "Generica" : selectedAdventure.name();
        String ambience = selectedAmbience == null || selectedAmbience.isBlank() ? "Generica" : selectedAmbience;
        return "Mision: " + adventureName
                + " | Ambientacion: " + ambience
                + " | Nivel: " + Math.max(1, Math.min(10, selectedLevel))
                + "\nDivide el mazo, revela cartas y revisa el historial con la misma estetica de tablero que el resto de pantallas.";
    }

    private void openAdventureSimulator(
            List<DungeonCard> deck,
            ObjectiveRoomAdventure selectedAdventure,
            String selectedAmbience,
            int selectedLevel,
            String environment) {
        Shell simulator = new Shell(shell, SWT.SHELL_TRIM | SWT.RESIZE);
        simulator.setText(I18n.t("dialog.adventureSimulator.title"));
        simulator.setBackground(theme.shellBackground);
        simulator.setLayout(new GridLayout(1, false));
        simulator.setSize(1380, 920);

        AdventureSimulatorState state = new AdventureSimulatorState();
        state.piles.add(new ArrayList<>(deck));
        state.histories.add(new ArrayList<>());

        Image dungeonBack = new Image(display, projectRoot.resolve("resources/dungeon-back.jpeg").toString());
        simulator.addListener(SWT.Dispose, event -> {
            if (!dungeonBack.isDisposed()) {
                dungeonBack.dispose();
            }
            clearActiveAdventureContext();
        });

        createDialogHeader(
                simulator,
                I18n.t("dialog.adventureSimulator.title"),
                buildAdventureSimulatorSubtitle(selectedAdventure, selectedAmbience, selectedLevel));

        Composite body = new Composite(simulator, SWT.NONE);
        body.setBackground(theme.shellBackground);
        body.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        GridLayout bodyLayout = new GridLayout(2, true);
        bodyLayout.marginWidth = 0;
        bodyLayout.marginHeight = 0;
        bodyLayout.horizontalSpacing = 16;
        bodyLayout.verticalSpacing = 16;
        body.setLayout(bodyLayout);

        Composite deckArea = createDarkPanel(body, 1);
        deckArea.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        Label deckTitle = new Label(deckArea, SWT.NONE);
        deckTitle.setText(I18n.t("dialog.adventureSimulator.deckPiles"));
        styleDarkLabel(deckTitle, true);
        deckTitle.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        Label deckHint = new Label(deckArea, SWT.WRAP);
        deckHint.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        deckHint.setText(I18n.t("dialog.adventureSimulator.deckHint"));
        styleDarkLabel(deckHint, false);

        Label deckStatus = new Label(deckArea, SWT.WRAP);
        deckStatus.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        styleDarkLabel(deckStatus, false);

        Composite pilesContainer = new Composite(deckArea, SWT.NONE);
        pilesContainer.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        pilesContainer.setBackground(theme.panelBackground);
        GridLayout pilesLayout = new GridLayout(3, true);
        pilesLayout.marginWidth = 0;
        pilesLayout.marginHeight = 0;
        pilesLayout.horizontalSpacing = 12;
        pilesLayout.verticalSpacing = 12;
        pilesContainer.setLayout(pilesLayout);

        Composite revealArea = createParchmentPanel(body, 1);
        revealArea.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        Label revealTitle = new Label(revealArea, SWT.NONE);
        revealTitle.setText(I18n.t("dialog.adventureSimulator.revealedCard"));
        styleParchmentLabel(revealTitle, true);
        revealTitle.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        Label revealStatus = new Label(revealArea, SWT.WRAP);
        revealStatus.setText(I18n.t("dialog.adventureSimulator.revealStatus"));
        revealStatus.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        styleParchmentLabel(revealStatus, false);

        Canvas revealCanvas = new Canvas(revealArea, SWT.DOUBLE_BUFFERED);
        revealCanvas.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        revealCanvas.addPaintListener(event -> {
            Rectangle area = revealCanvas.getClientArea();
            theme.paintParchmentPanel(event.gc, area);
            if (state.selectedCard == null) {
                event.gc.setForeground(theme.ink);
                event.gc.setFont(theme.bodyFont);
                event.gc.drawText(I18n.t("dialog.adventureSimulator.revealCanvasHint"), 18, 18, true);
                return;
            }

            Point scaled = renderer.scaleToFit(area);
            int x = area.x + (area.width - scaled.x) / 2;
            int y = area.y + (area.height - scaled.y) / 2;
            renderer.drawCard(event.gc, new Rectangle(x, y, scaled.x, scaled.y), state.selectedCard);
        });

        final Runnable[] refreshSimulatorUi = new Runnable[1];
        refreshSimulatorUi[0] = () -> {
            for (org.eclipse.swt.widgets.Control child : pilesContainer.getChildren()) {
                child.dispose();
            }

            if (state.piles.size() <= 1) {
                int remaining = state.piles.isEmpty() ? 0 : state.piles.get(0).size();
                deckStatus.setText(String.format(I18n.t("dialog.adventureSimulator.singlePileStatus"), remaining));
            } else {
                int remaining = state.piles.stream().mapToInt(List::size).sum();
                deckStatus.setText(String.format(I18n.t("dialog.adventureSimulator.multiPileStatus"), state.piles.size(), remaining));
            }

            for (int i = 0; i < state.piles.size(); i++) {
                int pileIndex = i;
                List<DungeonCard> pile = state.piles.get(i);

                Composite pileBox = createDarkPanel(pilesContainer, 1);
                pileBox.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
                pileBox.setBackground(theme.panelBackgroundAlt);

                Label pileLabel = new Label(pileBox, SWT.CENTER);
                pileLabel.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
                pileLabel.setText(String.format(I18n.t("dialog.adventureSimulator.pileLabel"), pileIndex + 1, pile.size()));
                pileLabel.setBackground(theme.panelBackground);
                pileLabel.setForeground(theme.mist);
                pileLabel.setFont(theme.sectionTitleFont);

                Canvas pileCanvas = new Canvas(pileBox, SWT.DOUBLE_BUFFERED);
                GridData pileCanvasData = new GridData(SWT.CENTER, SWT.TOP, false, false);
                pileCanvasData.widthHint = 180;
                pileCanvasData.heightHint = 260;
                pileCanvas.setLayoutData(pileCanvasData);

                pileCanvas.addPaintListener(event -> {
                    Rectangle area = pileCanvas.getClientArea();
                    theme.paintDarkPanel(event.gc, area);
                    event.gc.setAntialias(SWT.ON);
                    event.gc.setInterpolation(SWT.HIGH);

                    if (pile.isEmpty()) {
                        event.gc.setForeground(theme.mist);
                        event.gc.setFont(theme.bodyFont);
                        event.gc.drawRectangle(10, 10, area.width - 20, area.height - 20);
                        event.gc.drawText(I18n.t("dialog.adventureSimulator.noCards"), area.width / 2 - 28, area.height / 2 - 8, true);
                        return;
                    }

                    Rectangle img = dungeonBack.getBounds();
                    int targetWidth = area.width - 12;
                    int targetHeight = area.height - 12;
                    event.gc.drawImage(dungeonBack, 0, 0, img.width, img.height, 6, 6, targetWidth, targetHeight);
                });

                pileCanvas.addListener(SWT.MouseDown, event -> {
                    if (event.button == 1) {
                        if (pile.isEmpty()) {
                            showInfo(I18n.t("dialog.adventureSimulator.title"), String.format(I18n.t("dialog.adventureSimulator.info.emptyPile"), pileIndex + 1));
                            return;
                        }

                        DungeonCard drawn = pile.remove(0);
                        List<DungeonCard> pileHistory = state.histories.get(pileIndex);
                        pileHistory.add(0, drawn);
                        state.selectedCard = drawn;
                        state.selectedPile = pileIndex;
                        revealStatus.setText(String.format(I18n.t("dialog.adventureSimulator.selectedCard"), drawn.getName(), pileIndex + 1));
                        revealCanvas.redraw();
                        refreshSimulatorUi[0].run();
                    }
                });

                Menu menu = new Menu(pileCanvas);
                MenuItem splitItem = new MenuItem(menu, SWT.PUSH);
                splitItem.setText(I18n.t("menu.item.splitDeck"));
                splitItem.addListener(SWT.Selection, event -> {
                    int pileSize = pile.size();
                    if (pileSize < 2) {
                        showInfo(
                                I18n.t("dialog.adventureSimulator.title"),
                                String.format(I18n.t("dialog.adventureSimulator.info.notEnoughToSplit"), pileIndex + 1));
                        return;
                    }

                    Integer requestedPiles = askPileCount(simulator, pileSize);
                    if (requestedPiles == null) {
                        return;
                    }
                    state.piles = splitSelectedPile(state.piles, pileIndex, requestedPiles);
                    state.histories = splitSelectedPileHistories(state.histories, pileIndex, requestedPiles);
                    state.selectedCard = null;
                    state.selectedPile = -1;
                    revealStatus.setText(I18n.t("dialog.adventureSimulator.splitStatus"));
                    revealCanvas.redraw();
                    refreshSimulatorUi[0].run();
                });

                MenuItem addCardsItem = new MenuItem(menu, SWT.PUSH);
                addCardsItem.setText(I18n.t("button.addCardsToDeck"));
                addCardsItem.addListener(SWT.Selection, event -> {
                    List<DungeonCard> availableCards = pickAdditionalAdventureCards(environment, collectAdventureCardIds(state));
                    if (availableCards.isEmpty()) {
                        showInfo(
                                I18n.t("button.addCardsToDeck"),
                                String.format(I18n.t("simulator.addCardsUnavailable"), 0));
                        return;
                    }

                    Integer requestedCards = askAdditionalCardCount(simulator, availableCards.size());
                    if (requestedCards == null) {
                        return;
                    }

                    pile.addAll(availableCards.subList(0, requestedCards));
                    Collections.shuffle(pile, new Random());
                    state.selectedPile = pileIndex;
                    revealStatus.setText(String.format(I18n.t("simulator.addCardsDone"), requestedCards, pileIndex + 1));
                    revealCanvas.redraw();
                    refreshSimulatorUi[0].run();
                });
                pileCanvas.setMenu(menu);

                Label historyLabel = new Label(pileBox, SWT.NONE);
                historyLabel.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
                historyLabel.setText(I18n.t("dialog.adventureSimulator.history"));
                historyLabel.setBackground(theme.panelBackground);
                historyLabel.setForeground(theme.parchment);
                historyLabel.setFont(theme.bodyFont);

                org.eclipse.swt.widgets.List historyList = new org.eclipse.swt.widgets.List(
                        pileBox,
                        SWT.BORDER | SWT.V_SCROLL);
                GridData historyData = new GridData(SWT.FILL, SWT.FILL, true, true);
                historyData.heightHint = 130;
                historyList.setLayoutData(historyData);
                historyList.setBackground(theme.mist);
                historyList.setForeground(theme.ink);
                historyList.setFont(theme.bodyFont);

                List<DungeonCard> history = state.histories.get(pileIndex);
                for (DungeonCard historicCard : history) {
                    historyList.add(historicCard.getName() + " [" + historicCard.getType().getLabel() + "]");
                }

                historyList.addListener(SWT.Selection, event -> {
                    int selectedIndex = historyList.getSelectionIndex();
                    if (selectedIndex < 0 || selectedIndex >= history.size()) {
                        return;
                    }
                    DungeonCard selectedHistoryCard = history.get(selectedIndex);
                    state.selectedCard = selectedHistoryCard;
                    state.selectedPile = pileIndex;
                    revealStatus.setText(String.format(I18n.t("dialog.adventureSimulator.selectedCard"), selectedHistoryCard.getName(), pileIndex + 1));
                    revealCanvas.redraw();
                });

                if (state.selectedPile == pileIndex && state.selectedCard != null) {
                    int selectedIndex = history.indexOf(state.selectedCard);
                    if (selectedIndex >= 0) {
                        historyList.setSelection(selectedIndex);
                    }
                }
            }

            pilesContainer.layout(true, true);
            deckArea.layout(true, true);
        };

        Composite actions = new Composite(simulator, SWT.NONE);
        actions.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false, 2, 1));
        boolean showMissionButton = selectedAdventure != null && !selectedAdventure.generic();
        GridLayout actionsLayout = new GridLayout(showMissionButton ? 3 : 2, false);
        actionsLayout.marginWidth = 0;
        actionsLayout.horizontalSpacing = 12;
        actions.setLayout(actionsLayout);
        actions.setBackground(theme.shellBackground);

        if (showMissionButton) {
            org.eclipse.swt.widgets.Button missionButton = new org.eclipse.swt.widgets.Button(actions, SWT.PUSH);
            missionButton.setText(I18n.t("button.showMission"));
            styleActionButton(missionButton);
            missionButton.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
            missionButton.addListener(SWT.Selection, event -> openAdventureMissionDialog(selectedAdventure));
        }

        org.eclipse.swt.widgets.Button objectiveMonstersButton = new org.eclipse.swt.widgets.Button(actions, SWT.PUSH);
        objectiveMonstersButton.setText(I18n.t("button.generateObjectiveRoomMonsters"));
        styleActionButton(objectiveMonstersButton);
        objectiveMonstersButton.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
        objectiveMonstersButton.addListener(SWT.Selection, event -> {
            EventDeckApp eventApp = getOrCreateEventDeckApp();
            ContentRepository contentRepository = eventApp.contentRepository();
            List<Object> entries = generateObjectiveRoomMonsterEntries(contentRepository, selectedLevel);
            if (entries == null) {
                showError(
                        I18n.t("button.generateObjectiveRoomMonsters"),
                        I18n.t("simulator.objectiveMonstersInvalidWeights"));
                return;
            }
            if (entries.isEmpty()) {
                showInfo(
                        I18n.t("button.generateObjectiveRoomMonsters"),
                        I18n.t("simulator.objectiveMonstersNoEntries"));
                return;
            }
            eventApp.showEntries(simulator, entries);
        });

        org.eclipse.swt.widgets.Button finishButton = new org.eclipse.swt.widgets.Button(actions, SWT.PUSH);
        finishButton.setText(I18n.t("button.finishAdventure"));
        styleActionButton(finishButton);
        finishButton.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
        finishButton.addListener(SWT.Selection, event -> {
            simulator.close();
            if (!shell.isDisposed()) {
                shell.forceActive();
            }
        });

        refreshSimulatorUi[0].run();
        simulator.open();
    }

    private void openAdventureMissionDialog(ObjectiveRoomAdventure selectedAdventure) {
        if (selectedAdventure == null || selectedAdventure.generic()) {
            return;
        }

        Shell missionDialog = CardFactory.createAdventureMissionCard(
                shell,
                selectedAdventure.name(),
                selectedAdventure.flavorText(),
                selectedAdventure.rulesText(),
                620,
                760);
        missionDialog.setText("Mision: " + selectedAdventure.name());
        fitShellToDisplay(missionDialog, 48);
        missionDialog.open();
        missionDialog.forceActive();

        while (!missionDialog.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }
    }

    private void setActiveAdventureContext(AdventureAmbience ambience, int level) {
        AdventureAmbience resolved = ambience == null ? AdventureAmbience.GENERIC : ambience;
        Settings.setSetting(Settings.ADVENTURE_AMBIENCE, resolved.storageValue());
        Settings.setSetting(Settings.ADVENTURE_LEVEL, Integer.toString(Math.max(1, Math.min(10, level))));
        Settings.setSetting(Settings.ADVENTURE_ACTIVE, "true");
        Settings.save();
    }

    private void clearActiveAdventureContext() {
        Settings.setSetting(Settings.ADVENTURE_AMBIENCE, AdventureAmbience.GENERIC.storageValue());
        Settings.setSetting(Settings.ADVENTURE_LEVEL, "1");
        Settings.setSetting(Settings.ADVENTURE_ACTIVE, "false");
        Settings.save();
    }

    private void fitShellToDisplay(Shell targetShell, int margin) {
        if (targetShell == null || targetShell.isDisposed()) {
            return;
        }

        Rectangle clientArea = display.getPrimaryMonitor().getClientArea();
        Point currentSize = targetShell.getSize();
        int clampedWidth = Math.min(currentSize.x, Math.max(320, clientArea.width - margin));
        int clampedHeight = Math.min(currentSize.y, Math.max(240, clientArea.height - margin));
        targetShell.setSize(clampedWidth, clampedHeight);

        int x = clientArea.x + Math.max(0, (clientArea.width - clampedWidth) / 2);
        int y = clientArea.y + Math.max(0, (clientArea.height - clampedHeight) / 2);
        targetShell.setLocation(x, y);
    }

    private Integer askPileCount(Shell parent, int maxCards) {
        Shell dialog = new Shell(parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
        dialog.setText(I18n.t("dialog.splitDeck.title"));
        dialog.setBackground(theme.shellBackground);
        dialog.setLayout(new GridLayout(1, false));
        dialog.setSize(520, 360);

        createDialogHeader(
                dialog,
                I18n.t("dialog.splitDeck.title"),
                I18n.t("dialog.splitDeck.subtitle"));

        Composite formPanel = createDarkPanel(dialog, 2);
        formPanel.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        Label countLabel = new Label(formPanel, SWT.NONE);
        countLabel.setText(I18n.t("dialog.splitDeck.count"));
        styleDarkLabel(countLabel, false);
        countLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

        Spinner pileSpinner = new Spinner(formPanel, SWT.BORDER);
        styleSpinner(pileSpinner);
        pileSpinner.setMinimum(2);
        pileSpinner.setMaximum(maxCards);
        pileSpinner.setSelection(Math.min(3, maxCards));
        pileSpinner.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Composite actions = new Composite(formPanel, SWT.NONE);
        actions.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false, 2, 1));
        GridLayout actionsLayout = new GridLayout(2, true);
        actionsLayout.marginWidth = 0;
        actions.setLayout(actionsLayout);
        actions.setBackground(theme.panelBackground);

        final int[] result = new int[] {-1};

        org.eclipse.swt.widgets.Button okButton = new org.eclipse.swt.widgets.Button(actions, SWT.PUSH);
        okButton.setText(I18n.t("button.accept"));
        styleActionButton(okButton);
        okButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        okButton.addListener(SWT.Selection, event -> {
            result[0] = pileSpinner.getSelection();
            dialog.close();
        });

        org.eclipse.swt.widgets.Button cancelButton = new org.eclipse.swt.widgets.Button(actions, SWT.PUSH);
        cancelButton.setText(I18n.t("button.cancel"));
        styleActionButton(cancelButton);
        cancelButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        cancelButton.addListener(SWT.Selection, event -> dialog.close());

        dialog.open();
        while (!dialog.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }

        if (result[0] < 2) {
            return null;
        }
        return result[0];
    }

    private Integer askAdditionalCardCount(Shell parent, int maxCards) {
        Shell dialog = new Shell(parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
        dialog.setText(I18n.t("button.addCardsToDeck"));
        dialog.setBackground(theme.shellBackground);
        dialog.setLayout(new GridLayout(1, false));
        dialog.setSize(520, 360);

        createDialogHeader(
                dialog,
                I18n.t("button.addCardsToDeck"),
                I18n.t("simulator.addCardsPrompt"));

        Composite formPanel = createDarkPanel(dialog, 2);
        formPanel.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        Label countLabel = new Label(formPanel, SWT.NONE);
        countLabel.setText(I18n.t("simulator.addCardsPrompt"));
        styleDarkLabel(countLabel, false);
        countLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

        Spinner cardSpinner = new Spinner(formPanel, SWT.BORDER);
        styleSpinner(cardSpinner);
        cardSpinner.setMinimum(1);
        cardSpinner.setMaximum(maxCards);
        cardSpinner.setSelection(1);
        cardSpinner.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Composite actions = new Composite(formPanel, SWT.NONE);
        actions.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false, 2, 1));
        GridLayout actionsLayout = new GridLayout(2, true);
        actionsLayout.marginWidth = 0;
        actions.setLayout(actionsLayout);
        actions.setBackground(theme.panelBackground);

        final int[] result = new int[] {-1};

        org.eclipse.swt.widgets.Button okButton = new org.eclipse.swt.widgets.Button(actions, SWT.PUSH);
        okButton.setText(I18n.t("button.accept"));
        styleActionButton(okButton);
        okButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        okButton.addListener(SWT.Selection, event -> {
            result[0] = cardSpinner.getSelection();
            dialog.close();
        });

        org.eclipse.swt.widgets.Button cancelButton = new org.eclipse.swt.widgets.Button(actions, SWT.PUSH);
        cancelButton.setText(I18n.t("button.cancel"));
        styleActionButton(cancelButton);
        cancelButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        cancelButton.addListener(SWT.Selection, event -> dialog.close());

        dialog.open();
        while (!dialog.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }

        if (result[0] < 1) {
            return null;
        }
        return result[0];
    }

    private List<List<DungeonCard>> splitSelectedPile(
            List<List<DungeonCard>> piles,
            int selectedPileIndex,
            int pileCount) {
        List<List<DungeonCard>> result = new ArrayList<>();

        for (int i = 0; i < piles.size(); i++) {
            List<DungeonCard> sourcePile = piles.get(i);
            if (i != selectedPileIndex) {
                result.add(new ArrayList<>(sourcePile));
                continue;
            }

            List<List<DungeonCard>> splitPiles = new ArrayList<>();
            for (int j = 0; j < pileCount; j++) {
                splitPiles.add(new ArrayList<>());
            }
            for (int cardIndex = 0; cardIndex < sourcePile.size(); cardIndex++) {
                splitPiles.get(cardIndex % pileCount).add(sourcePile.get(cardIndex));
            }
            result.addAll(splitPiles);
        }

        return result;
    }

    private List<List<DungeonCard>> splitSelectedPileHistories(
            List<List<DungeonCard>> currentHistories,
            int selectedPileIndex,
            int pileCount) {
        List<List<DungeonCard>> result = new ArrayList<>();

        for (int i = 0; i < currentHistories.size(); i++) {
            List<DungeonCard> sourceHistory = currentHistories.get(i);
            if (i != selectedPileIndex) {
                result.add(new ArrayList<>(sourceHistory));
                continue;
            }

            for (int j = 0; j < pileCount; j++) {
                result.add(new ArrayList<>());
            }
            if (!sourceHistory.isEmpty()) {
                result.get(result.size() - pileCount).addAll(sourceHistory);
            }
        }

        return result;
    }

    private static class AdventureSimulatorState {
        private List<List<DungeonCard>> piles = new ArrayList<>();
        private List<List<DungeonCard>> histories = new ArrayList<>();
        private DungeonCard selectedCard;
        private int selectedPile = -1;
    }

    private void refreshCards() {
        cards = loadCards();
        if (cards.isEmpty()) {
            selected = null;
        } else {
            selected = cards.get(0);
        }

        refreshDashboardStats();
        reloadCardList();
        if (renderCanvas != null && !renderCanvas.isDisposed()) {
            renderCanvas.redraw();
        }
    }

    private void reloadCardList() {
        if (cardList == null || cardList.isDisposed()) {
            return;
        }

        cardList.removeAll();
        for (DungeonCard card : cards) {
            cardList.add(formatCardListEntry(card));
        }

        if (!cards.isEmpty()) {
            cardList.select(0);
        }
    }

    private java.util.List<DungeonCard> loadCards() {
        try {
            return cardStore.loadCards();
        } catch (DungeonCardStorageException ex) {
            showError("Storage error", "No se han podido cargar las cartas: " + ex.getMessage());
            throw new IllegalStateException("Cannot load cards", ex);
        }
    }

    private void showError(String title, String message) {
        MessageBox box = new MessageBox(shell, SWT.ICON_ERROR | SWT.OK);
        box.setText(title);
        box.setMessage(message);
        box.open();
    }

    private void showInfo(String title, String message) {
        MessageBox box = new MessageBox(shell, SWT.ICON_INFORMATION | SWT.OK);
        box.setText(title);
        box.setMessage(message);
        box.open();
    }

    private String formatCardListEntry(DungeonCard card) {
        String availability = card.isEnabled() && card.getCopyCount() > 0 ? "disponible" : "no disponible";
        return card.getName() + " (" + card.getType().getLabel() + " - " + card.getEnvironment()
                + ", copias: " + card.getCopyCount() + ", " + availability + ")";
    }

    private String formatMaintenanceCardEntry(DungeonCard card) {
        return "#" + card.getId() + " - " + formatCardListEntry(card);
    }

    public Shell getShell() {
        return shell;
    }
}
