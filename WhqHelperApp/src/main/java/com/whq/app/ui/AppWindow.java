package com.whq.app.ui;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.GC;
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
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Spinner;

import com.whq.app.AppPaths;
import com.whq.app.i18n.I18n;
import com.whq.app.i18n.Language;
import com.whq.app.io.CardCsvService;
import com.whq.app.model.DungeonCard;
import com.whq.app.render.CardRenderer;
import com.whq.app.storage.DungeonCardStorageException;
import com.whq.app.storage.DungeonCardStore;
import com.whq.app.storage.XmlDungeonCardStore;
import com.whq.app.ui.panel.AdventurePanel;
import com.whq.app.ui.panel.AppContext;
import com.whq.app.ui.panel.SettlementPanel;
import com.whq.app.ui.panel.WarriorCounters;
import com.whq.app.ui.panel.WarriorCounters.WarriorCounterDefinition;

import pms.whq.EventDeckApp;
import pms.whq.Settings;
import pms.whq.state.AppState;
import pms.whq.swt.EventContentEditorDialog;

public class AppWindow {
    private final Display display;
    private final Shell shell;
    private final Path projectRoot;
    private final DungeonCardStore cardStore;
    private final CardCsvService csvService;
    private final List<LocalizedUiAction> localizedActions;
    private final AppContext context = new WindowContext();
    private final WarriorCounters warriorCounters = new WarriorCounters(context);
    private final SettlementPanel settlementPanel = new SettlementPanel(context);
    private final AdventurePanel adventurePanel;

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
    private Label modeStatsLabel;
    private Label eventProbabilityStatsLabel;
    private Label treasureProbabilityStatsLabel;
    private Label partyStatsLabel;
    private Label languageStatsLabel;
    private Label browserTitleLabel;
    private Label browserHintLabel;
    private Label previewTitleLabel;
    private Label previewHintLabel;
    private Button newDungeonButton;
    private Button newSettlementButton;
    private Button genWarriorCounterButton;
    private Button closeWarriorCountersButton;
    private Button activateTablesButton;
    private Button contentEditorButton;
    private Group eventDeckGroup;

    private MenuItem playMenuItem;
    private MenuItem contentMenuItem;
    private MenuItem eventCardsMenuItem;
    private MenuItem optionsMenuItem;

    private final LocalizedUiAction newDungeonAction;
    private final LocalizedUiAction newSettlementAction;
    private final LocalizedUiAction genWarriorCounterAction;
    private final LocalizedUiAction closeWarriorCountersAction;
    private final LocalizedUiAction eventContentEditorAction;
    private final LocalizedUiAction importCsvAction;
    private final LocalizedUiAction exportAllCsvAction;
    private final LocalizedUiAction exportEnvironmentCsvAction;
    private final LocalizedUiAction activateTablesAction;
    private final LocalizedUiAction setPartyAction;
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
        AppIcon.apply(this.shell, projectRoot);
        this.cardStore = new XmlDungeonCardStore(projectRoot);
        this.adventurePanel = new AdventurePanel(context);
        this.csvService = new CardCsvService();
        this.localizedActions = new ArrayList<>();
        this.newDungeonAction = registerAction(LocalizedUiAction.push("menu.item.newDungeon", adventurePanel::openNewDungeonDialog));
        this.newSettlementAction = registerAction(LocalizedUiAction.push("menu.item.newSettlement", settlementPanel::openNewSettlementDialog));
        this.genWarriorCounterAction = registerAction(LocalizedUiAction.push("menu.item.openWarriorCounter", warriorCounters::genWarriorCounter));
        this.closeWarriorCountersAction = registerAction(LocalizedUiAction.push("menu.item.closeWarriorCounters", warriorCounters::closeAllWarriorCounters));
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
        this.setPartyAction = registerAction(LocalizedUiAction.push("menu.item.setParty", this::openPartyDialog));
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
        buildEventDeckSection(mainPanel);
        refreshDashboardStats();
        refreshLocalizedTexts();

        shell.addListener(SWT.Dispose, event -> {
            warriorCounters.closeAllWarriorCounters();
            renderer.dispose();
            if (theme != null) {
                theme.dispose();
            }
            I18n.removeListener(languageListener);
            Settings.save();
        });
        shell.addListener(SWT.Activate, event -> refreshLocalizedTexts());
        shell.open();
        if (!AppPaths.hasContent(projectRoot)) {
            display.asyncExec(this::showMissingContentNotice);
        }
        display.asyncExec(adventurePanel::offerPendingAdventure);
    }

    // La aplicacion se distribuye sin contenido de juego: sin paquete arranca vacia y explica donde
    // colocarlo, en lugar de fallar.
    private void showMissingContentNotice() {
        if (shell.isDisposed()) {
            return;
        }
        MessageBox box = new MessageBox(shell, SWT.ICON_INFORMATION | SWT.OK);
        box.setText(I18n.t("content.missing.title"));
        box.setMessage(I18n.t(
                "content.missing.body",
                Map.of(
                        "path", String.valueOf(AppPaths.contentHome(projectRoot)),
                        "sample", String.valueOf(AppPaths.sharedPath(projectRoot, "sample")))));
        box.open();
    }

    private void createMenuBar() {
        Menu menuBar = new Menu(shell, SWT.BAR);
        shell.setMenuBar(menuBar);

        playMenuItem = new MenuItem(menuBar, SWT.CASCADE);
        Menu playMenu = new Menu(shell, SWT.DROP_DOWN);
        playMenuItem.setMenu(playMenu);
        createActionMenuItem(playMenu, SWT.PUSH, newDungeonAction);
        createActionMenuItem(playMenu, SWT.PUSH, newSettlementAction);

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
        createActionMenuItem(eventCardsMenu, SWT.PUSH, setPartyAction);
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

    private void openPartyDialog() {
        getOrCreateEventDeckApp().openPartySettings(shell);
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
        if (eventDeckGroup != null && !eventDeckGroup.isDisposed()) {
            eventDeckGroup.setText(I18n.t("event.window.title"));
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

        Composite statsRow = new Composite(heroCopy, SWT.NONE);
        statsRow.setBackground(theme.panelBackground);
        statsRow.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        GridLayout statsLayout = new GridLayout(5, true);
        statsLayout.marginWidth = 0;
        statsLayout.marginHeight = 0;
        statsLayout.horizontalSpacing = 8;
        statsLayout.verticalSpacing = 8;
        statsRow.setLayout(statsLayout);

        modeStatsLabel = createHeroStatLabel(statsRow);
        eventProbabilityStatsLabel = createHeroStatLabel(statsRow);
        treasureProbabilityStatsLabel = createHeroStatLabel(statsRow);
        partyStatsLabel = createHeroStatLabel(statsRow);
        languageStatsLabel = createHeroStatLabel(statsRow);

        Composite actionRow = new Composite(heroCopy, SWT.NONE);
        actionRow.setBackground(theme.panelBackground);
        actionRow.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        GridLayout actionsLayout = new GridLayout(3, true);
        actionsLayout.marginWidth = 0;
        actionsLayout.marginHeight = 4;
        actionsLayout.horizontalSpacing = 10;
        actionsLayout.verticalSpacing = 10;
        actionRow.setLayout(actionsLayout);

        newDungeonButton = createHeroButton(actionRow, newDungeonAction);
        newSettlementButton = createHeroButton(actionRow, newSettlementAction);
        genWarriorCounterButton = createHeroButton(actionRow, genWarriorCounterAction);
        closeWarriorCountersButton = createHeroButton(actionRow, closeWarriorCountersAction);
        activateTablesButton = createHeroButton(actionRow, activateTablesAction);
        contentEditorButton = createHeroButton(actionRow, eventContentEditorAction);

        heroArtCanvas = new Canvas(heroSection, SWT.DOUBLE_BUFFERED);
        GridData artData = new GridData(SWT.FILL, SWT.FILL, false, true);
        artData.widthHint = 470;
        artData.heightHint = 230;
        heroArtCanvas.setLayoutData(artData);
        heroArtCanvas.addPaintListener(event -> theme.paintHeroBanner(event.gc, heroArtCanvas.getClientArea()));
    }

    private Label createHeroStatLabel(Composite parent) {
        Label label = new Label(parent, SWT.CENTER | SWT.WRAP);
        label.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        label.setBackground(theme.panelBackgroundAlt);
        label.setForeground(theme.mist);
        label.setFont(theme.bodyFont);
        return label;
    }

    private void buildEventDeckSection(Composite parent) {
        eventDeckGroup = new Group(parent, SWT.NONE);
        eventDeckGroup.setText(I18n.t("event.window.title"));
        eventDeckGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        eventDeckGroup.setBackground(theme.shellBackground);
        eventDeckGroup.setForeground(theme.mist);
        eventDeckGroup.setFont(theme.sectionTitleFont);
        GridLayout layout = new GridLayout(1, false);
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        eventDeckGroup.setLayout(layout);

        eventDeckApp = new EventDeckApp(display, projectRoot, eventDeckGroup);
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

    private void refreshDashboardStats() {
        if (cards == null || theme == null) {
            return;
        }

        AppState appState = AppState.loadFromSettings();
        String mode = appState.deckMode().isDeck()
                ? I18n.t("dashboard.value.mode.deck")
                : I18n.t("dashboard.value.mode.table");
        String language = I18n.getLanguage() == Language.EN
                ? I18n.t("dashboard.value.language.en")
                : I18n.t("dashboard.value.language.es");

        if (modeStatsLabel != null && !modeStatsLabel.isDisposed()) {
            modeStatsLabel.setText(I18n.t("dashboard.stats.mode", Map.of("mode", mode)));
        }
        if (eventProbabilityStatsLabel != null && !eventProbabilityStatsLabel.isDisposed()) {
            eventProbabilityStatsLabel.setText(I18n.t(
                    "dashboard.stats.eventProbability",
                    Map.of("value", Settings.getSettingAsInt(Settings.EVENT_PROBABILITY))));
        }
        if (treasureProbabilityStatsLabel != null && !treasureProbabilityStatsLabel.isDisposed()) {
            treasureProbabilityStatsLabel.setText(I18n.t(
                    "dashboard.stats.treasureProbability",
                    Map.of("value", Settings.getSettingAsInt(Settings.TREASURE_GOLD_PROBABILITY))));
        }
        if (partyStatsLabel != null && !partyStatsLabel.isDisposed()) {
            partyStatsLabel.setText(I18n.t("dashboard.stats.party", Map.of("party", activePartySummary())));
        }
        if (languageStatsLabel != null && !languageStatsLabel.isDisposed()) {
            languageStatsLabel.setText(I18n.t("dashboard.stats.language", Map.of("language", language)));
        }
    }

    private void setLanguageAndPersist(Language language) {
        Settings.setLanguage(language);
        Settings.save();
        I18n.setLanguage(language);
        warriorCounters.resetWarriorCounterPoolCache();
        if (eventDeckApp != null && !eventDeckApp.isDisposed()) {
            eventDeckApp.reloadData();
        }
        refreshLocalizedTexts();
    }

    private String activePartySummary() {
        try {
            Map<String, WarriorCounterDefinition> warriorsById = new LinkedHashMap<>();
            for (WarriorCounterDefinition warrior : warriorCounters.loadWarriorCounters()) {
                warriorsById.put(warrior.id(), warrior);
            }

            List<String> ids = warriorCounters.configuredPartyIds();
            List<String> names = new ArrayList<>();
            for (String id : ids) {
                WarriorCounterDefinition warrior = warriorsById.get(id);
                names.add(warrior == null ? id : warrior.name());
            }
            return names.isEmpty() ? I18n.t("dashboard.value.party.none") : String.join(", ", names);
        } catch (Exception ignored) {
            return I18n.t("dashboard.value.party.unavailable");
        }
    }

    private void openDungeonDefaultsDialog() {
        Shell dialog = new Shell(shell, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
        AppIcon.inherit(dialog, shell);
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

    /** Al arrancar: si quedo una aventura sin terminar, ofrece continuarla o descartarla. */
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

    // Lo que ven las zonas extraidas (com.whq.app.ui.panel): siempre el valor actual de cada campo.
    private final class WindowContext implements AppContext {
        @Override
        public Display display() {
            return display;
        }

        @Override
        public Shell shell() {
            return shell;
        }

        @Override
        public Path projectRoot() {
            return projectRoot;
        }

        @Override
        public WhqUiTheme theme() {
            return theme;
        }

        @Override
        public CardRenderer renderer() {
            return renderer;
        }

        @Override
        public java.util.List<DungeonCard> cards() {
            return cards;
        }

        @Override
        public DungeonCardStore cardStore() {
            return cardStore;
        }

        @Override
        public EventDeckApp eventDeckApp() {
            return getOrCreateEventDeckApp();
        }

        @Override
        public void showError(String title, String message) {
            AppWindow.this.showError(title, message);
        }

        @Override
        public void showInfo(String title, String message) {
            AppWindow.this.showInfo(title, message);
        }

        @Override
        public void refreshCards() {
            AppWindow.this.refreshCards();
        }
    }
}
