package com.whq.app.ui.panel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Text;

import com.whq.app.AppPaths;
import com.whq.app.adventure.ObjectiveRoomAdventure;
import com.whq.app.adventure.ObjectiveRoomAdventureRepository;
import com.whq.app.adventure.ObjectiveRoomAdventureRepositoryException;
import com.whq.app.adventure.XmlObjectiveRoomAdventureRepository;
import com.whq.app.game.AdventureDeckBuilder;
import com.whq.app.game.AdventureDeckException;
import com.whq.app.game.AdventureSession;
import com.whq.app.game.SavedAdventure;
import com.whq.app.game.ObjectiveRoomGenerator;
import com.whq.app.i18n.I18n;
import com.whq.app.model.DungeonCard;
import com.whq.app.model.WhiteDwarfRoomReferences;
import com.whq.app.storage.DungeonCardStorageException;
import com.whq.app.storage.AdventureSessionStorageException;
import com.whq.app.storage.XmlAdventureSessionStore;

import pms.whq.EventDeckApp;
import pms.whq.Settings;
import pms.whq.content.ContentIssue;
import pms.whq.content.ContentRepository;
import pms.whq.data.DrawableEntry;
import pms.whq.data.Event;
import pms.whq.data.EventEntry;
import pms.whq.data.Table;
import pms.whq.data.TableKind;
import pms.whq.state.AdventureAmbience;
import pms.whq.swt.CardFactory;
import pms.whq.swt.RuleDialog;
import com.whq.app.ui.AppIcon;

/**
 * Aventura de mazmorra: el dialogo de Nueva Mazmorra, el simulador del mazo, la busqueda de tesoro,
 * las misiones y el guardado y la reanudacion de la partida en curso.
 */
public final class AdventurePanel extends AppPanel {

    private enum ResumeChoice {
        RESUME,
        DISCARD,
        LATER
    }

    private final ObjectiveRoomAdventureRepository objectiveRoomAdventureRepository;
    private final XmlAdventureSessionStore adventureSessionStore;
    private boolean adventureSaveErrorShown;
    private final Random adventureRandom = new Random();
    private final ObjectiveRoomGenerator objectiveRoomGenerator = new ObjectiveRoomGenerator(adventureRandom);

    public AdventurePanel(AppContext context) {
        super(context);
        this.objectiveRoomAdventureRepository = new XmlObjectiveRoomAdventureRepository(projectRoot());
        this.adventureSessionStore = new XmlAdventureSessionStore(projectRoot());
    }

    public void openNewDungeonDialog() {
        java.util.List<String> environments;
        try {
            environments = cardStore().loadEnvironments();
        } catch (DungeonCardStorageException ex) {
            showError(I18n.t("dialog.newDungeon.title"), I18n.t("dialog.newDungeon.error.loadEnvironments", Map.of("error", String.valueOf(ex.getMessage()))));
            return;
        }

        if (environments.isEmpty()) {
            showInfo(I18n.t("dialog.newDungeon.title"), I18n.t("dialog.newDungeon.info.noCards"));
            return;
        }

        Shell dialog = new Shell(shell(), SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.RESIZE);
        AppIcon.inherit(dialog, shell());
        dialog.setText(I18n.t("dialog.newDungeon.title"));
        dialog.setBackground(theme().shellBackground);
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
        scroll.setBackground(theme().shellBackground);

        Composite content = new Composite(scroll, SWT.NONE);
        content.setBackground(theme().shellBackground);
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
        missionRulesText.setBackground(theme().mist);
        missionRulesText.setForeground(theme().ink);

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
        buttons.setBackground(theme().shellBackground);

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
                        .loadAdventuresForObjectiveRoom(objectiveByName.get(selectedObjectiveRoomName));
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
                showError(I18n.t("dialog.newDungeon.title"), I18n.t("dialog.newDungeon.error.loadAdventures", Map.of("error", String.valueOf(ex.getMessage()))));
                missionRulesText.setText("");
                startButton.setEnabled(false);
            }
        };

        Runnable reloadObjectives = () -> {
            String environment = environmentCombo.getText();
            java.util.List<DungeonCard> objectiveRooms;
            try {
                objectiveRooms = cardStore().loadObjectiveRoomsByEnvironment(environment);
            } catch (DungeonCardStorageException ex) {
                showError(I18n.t("dialog.newDungeon.title"), I18n.t("dialog.newDungeon.error.loadObjectiveRooms", Map.of("error", String.valueOf(ex.getMessage()))));
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
                List<DungeonCard> deck = new AdventureDeckBuilder(cards(), adventureRandom).buildAdventureDeck(
                        selectedEnvironment,
                        objectiveRoom,
                        deckSizeSpinner.getSelection(),
                        roomCountSpinner.getSelection());
                String selectedAmbience = ambienceCombo.getText();
                int selectedLevel = Math.max(1, Math.min(10, levelSpinner.getSelection()));
                setActiveAdventureContext(AdventureAmbience.fromDisplayName(selectedAmbience), selectedLevel);
                dialog.close();
                discardAdventureSession();
                openAdventureSimulator(
                        new AdventureSession(deck),
                        objectiveRoom.getId(),
                        selectedAdventure,
                        selectedAmbience,
                        selectedLevel,
                        selectedEnvironment);
            } catch (AdventureDeckException ex) {
                showError(I18n.t("dialog.newDungeon.title"), I18n.t(ex.i18nKey()));
            }
        });

        cancelButton.addListener(SWT.Selection, event -> dialog.close());

        reloadObjectives.run();
        fitShellToDisplay(dialog, 48);
        dialog.open();
        while (!dialog.isDisposed()) {
            if (!display().readAndDispatch()) {
                display().sleep();
            }
        }
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
            AdventureSession session,
            long objectiveRoomCardId,
            ObjectiveRoomAdventure selectedAdventure,
            String selectedAmbience,
            int selectedLevel,
            String environment) {
        Shell simulator = new Shell(shell(), SWT.SHELL_TRIM | SWT.RESIZE);
        AppIcon.inherit(simulator, shell());
        simulator.setText(I18n.t("dialog.adventureSimulator.title"));
        simulator.setBackground(theme().shellBackground);
        simulator.setLayout(new GridLayout(1, false));
        simulator.setSize(1380, 920);

        // Auto-guardado: la sesion en curso se sobrescribe tras cada cambio de los montones.
        String ambienceStorageValue = AdventureAmbience.fromDisplayName(selectedAmbience).storageValue();
        String missionId = selectedAdventure == null ? null : selectedAdventure.id();
        adventureSaveErrorShown = false;
        Runnable autoSave = () -> saveAdventureSession(new SavedAdventure(
                environment,
                selectedLevel,
                ambienceStorageValue,
                Settings.getSettingAsInt(Settings.PARTY_SIZE),
                objectiveRoomCardId,
                null,
                missionId,
                session));
        session.setChangeListener(autoSave);
        autoSave.run();

        Image dungeonBack = new Image(display(), AppPaths.contentPath(projectRoot(), "resources/dungeon-back.jpeg").toString());
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
        body.setBackground(theme().shellBackground);
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
        pilesContainer.setBackground(theme().panelBackground);
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
        if (session.selectedCard() != null) {
            // Sesion reanudada con una carta ya seleccionada.
            revealStatus.setText(I18n.t("dialog.adventureSimulator.selectedCard", Map.of(
                    "name", String.valueOf(session.selectedCard().getName()), "pile", session.selectedPile() + 1)));
        }
        revealStatus.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        styleParchmentLabel(revealStatus, false);

        Button whiteDwarfReferenceButton = new Button(revealArea, SWT.PUSH);
        whiteDwarfReferenceButton.setText(I18n.t("dialog.whiteDwarfReference.button"));
        whiteDwarfReferenceButton.setLayoutData(new GridData(SWT.BEGINNING, SWT.TOP, false, false));
        whiteDwarfReferenceButton.setEnabled(false);

        Canvas revealCanvas = new Canvas(revealArea, SWT.DOUBLE_BUFFERED);
        revealCanvas.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        revealCanvas.addPaintListener(event -> {
            Rectangle area = revealCanvas.getClientArea();
            theme().paintParchmentPanel(event.gc, area);
            if (session.selectedCard() == null) {
                event.gc.setForeground(theme().ink);
                event.gc.setFont(theme().bodyFont);
                event.gc.drawText(I18n.t("dialog.adventureSimulator.revealCanvasHint"), 18, 18, true);
                return;
            }

            Point scaled = renderer().scaleToFit(area);
            int x = area.x + (area.width - scaled.x) / 2;
            int y = area.y + (area.height - scaled.y) / 2;
            renderer().drawCard(event.gc, new Rectangle(x, y, scaled.x, scaled.y), session.selectedCard());
        });

        RuleDialog whiteDwarfReferenceDialog = new RuleDialog(simulator);
        simulator.addDisposeListener(event -> whiteDwarfReferenceDialog.dispose());

        Runnable refreshWhiteDwarfReferenceButton = () -> {
            boolean hasReference = session.selectedCard() != null && WhiteDwarfRoomReferences.find(session.selectedCard()).isPresent();
            whiteDwarfReferenceButton.setEnabled(hasReference);
        };

        whiteDwarfReferenceButton.addListener(SWT.Selection, event -> {
            if (session.selectedCard() == null) {
                return;
            }
            WhiteDwarfRoomReferences.find(session.selectedCard()).ifPresent(reference -> whiteDwarfReferenceDialog.showContent(
                    reference.title(I18n.getLanguage()),
                    reference.source() + "\n\n" + reference.text(I18n.getLanguage())));
        });

        final Runnable[] refreshSimulatorUi = new Runnable[1];
        refreshSimulatorUi[0] = () -> {
            for (org.eclipse.swt.widgets.Control child : pilesContainer.getChildren()) {
                child.dispose();
            }

            if (session.pileCount() <= 1) {
                int remaining = session.pileCount() == 0 ? 0 : session.pile(0).size();
                deckStatus.setText(I18n.t("dialog.adventureSimulator.singlePileStatus", Map.of("count", remaining)));
            } else {
                int remaining = session.totalRemainingCards();
                deckStatus.setText(I18n.t("dialog.adventureSimulator.multiPileStatus", Map.of("piles", session.pileCount(), "cards", remaining)));
            }

            for (int i = 0; i < session.pileCount(); i++) {
                int pileIndex = i;
                List<DungeonCard> pile = session.pile(i);

                Composite pileBox = createDarkPanel(pilesContainer, 1);
                pileBox.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
                pileBox.setBackground(theme().panelBackgroundAlt);

                Label pileLabel = new Label(pileBox, SWT.CENTER);
                pileLabel.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
                pileLabel.setText(I18n.t("dialog.adventureSimulator.pileLabel", Map.of("pile", pileIndex + 1, "cards", pile.size())));
                pileLabel.setBackground(theme().panelBackground);
                pileLabel.setForeground(theme().mist);
                pileLabel.setFont(theme().sectionTitleFont);

                Canvas pileCanvas = new Canvas(pileBox, SWT.DOUBLE_BUFFERED);
                GridData pileCanvasData = new GridData(SWT.CENTER, SWT.TOP, false, false);
                pileCanvasData.widthHint = 180;
                pileCanvasData.heightHint = 260;
                pileCanvas.setLayoutData(pileCanvasData);

                pileCanvas.addPaintListener(event -> {
                    Rectangle area = pileCanvas.getClientArea();
                    theme().paintDarkPanel(event.gc, area);
                    event.gc.setAntialias(SWT.ON);
                    event.gc.setInterpolation(SWT.HIGH);

                    if (pile.isEmpty()) {
                        event.gc.setForeground(theme().mist);
                        event.gc.setFont(theme().bodyFont);
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
                            showInfo(I18n.t("dialog.adventureSimulator.title"), I18n.t("dialog.adventureSimulator.info.emptyPile", Map.of("pile", pileIndex + 1)));
                            return;
                        }

                        DungeonCard drawn = session.drawFrom(pileIndex);
                        revealStatus.setText(I18n.t("dialog.adventureSimulator.selectedCard", Map.of("name", String.valueOf(drawn.getName()), "pile", pileIndex + 1)));
                        revealCanvas.redraw();
                        refreshWhiteDwarfReferenceButton.run();
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
                                I18n.t("dialog.adventureSimulator.info.notEnoughToSplit", Map.of("pile", pileIndex + 1)));
                        return;
                    }

                    Integer requestedPiles = askPileCount(simulator, pileSize);
                    if (requestedPiles == null) {
                        return;
                    }
                    session.splitPile(pileIndex, requestedPiles);
                    revealStatus.setText(I18n.t("dialog.adventureSimulator.splitStatus"));
                    revealCanvas.redraw();
                    refreshSimulatorUi[0].run();
                });

                MenuItem addCardsItem = new MenuItem(menu, SWT.PUSH);
                addCardsItem.setText(I18n.t("button.addCardsToDeck"));
                addCardsItem.addListener(SWT.Selection, event -> {
                    List<DungeonCard> availableCards = new AdventureDeckBuilder(cards(), adventureRandom)
                            .pickAdditionalAdventureCards(environment, session.collectAdventureCardIds());
                    if (availableCards.isEmpty()) {
                        showInfo(
                                I18n.t("button.addCardsToDeck"),
                                I18n.t("simulator.addCardsUnavailable", Map.of("max", 0)));
                        return;
                    }

                    Integer requestedCards = askAdditionalCardCount(simulator, availableCards.size());
                    if (requestedCards == null) {
                        return;
                    }

                    session.addCardsToPile(pileIndex, availableCards.subList(0, requestedCards), adventureRandom);
                    revealStatus.setText(I18n.t("simulator.addCardsDone", Map.of("count", requestedCards, "pile", pileIndex + 1)));
                    revealCanvas.redraw();
                    refreshSimulatorUi[0].run();
                });
                pileCanvas.setMenu(menu);

                Label historyLabel = new Label(pileBox, SWT.NONE);
                historyLabel.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
                historyLabel.setText(I18n.t("dialog.adventureSimulator.history"));
                historyLabel.setBackground(theme().panelBackground);
                historyLabel.setForeground(theme().parchment);
                historyLabel.setFont(theme().bodyFont);

                org.eclipse.swt.widgets.List historyList = new org.eclipse.swt.widgets.List(
                        pileBox,
                        SWT.BORDER | SWT.V_SCROLL);
                GridData historyData = new GridData(SWT.FILL, SWT.FILL, true, true);
                historyData.heightHint = 130;
                historyList.setLayoutData(historyData);
                historyList.setBackground(theme().mist);
                historyList.setForeground(theme().ink);
                historyList.setFont(theme().bodyFont);

                List<DungeonCard> history = session.history(pileIndex);
                for (DungeonCard historicCard : history) {
                    historyList.add(historicCard.getName() + " [" + historicCard.getType().getLabel() + "]");
                }

                historyList.addListener(SWT.Selection, event -> {
                    int selectedIndex = historyList.getSelectionIndex();
                    if (selectedIndex < 0 || selectedIndex >= history.size()) {
                        return;
                    }
                    DungeonCard selectedHistoryCard = history.get(selectedIndex);
                    session.selectFromHistory(pileIndex, selectedHistoryCard);
                    revealStatus.setText(I18n.t("dialog.adventureSimulator.selectedCard", Map.of("name", String.valueOf(selectedHistoryCard.getName()), "pile", pileIndex + 1)));
                    revealCanvas.redraw();
                    refreshWhiteDwarfReferenceButton.run();
                });

                if (session.selectedPile() == pileIndex && session.selectedCard() != null) {
                    int selectedIndex = history.indexOf(session.selectedCard());
                    if (selectedIndex >= 0) {
                        historyList.setSelection(selectedIndex);
                    }
                }
            }

            pilesContainer.layout(true, true);
            deckArea.layout(true, true);
            refreshWhiteDwarfReferenceButton.run();
        };

        Composite actions = new Composite(simulator, SWT.NONE);
        actions.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false, 2, 1));
        boolean showMissionButton = selectedAdventure != null && !selectedAdventure.generic();
        GridLayout actionsLayout = new GridLayout(showMissionButton ? 4 : 3, false);
        actionsLayout.marginWidth = 0;
        actionsLayout.horizontalSpacing = 12;
        actions.setLayout(actionsLayout);
        actions.setBackground(theme().shellBackground);

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
            ObjectiveRoomGenerator.ObjectiveRoomEncounter encounter =
                    objectiveRoomGenerator.generateObjectiveRoomMonsterEntries(contentRepository, selectedLevel);
            if (encounter == null) {
                showError(
                        I18n.t("button.generateObjectiveRoomMonsters"),
                        I18n.t("simulator.objectiveMonstersInvalidWeights"));
                return;
            }
            showInfo(
                    I18n.t("button.generateObjectiveRoomMonsters"),
                    I18n.t("simulator.objectiveMonstersDifficulty", Map.of("difficulty", I18n.t(encounter.difficulty().labelKey()))));
            List<DrawableEntry> entries = encounter.entries();
            if (entries.isEmpty()) {
                showInfo(
                        I18n.t("button.generateObjectiveRoomMonsters"),
                        I18n.t("simulator.objectiveMonstersNoEntries"));
                return;
            }
            eventApp.showEntries(simulator, entries);
        });

        org.eclipse.swt.widgets.Button treasureSearchButton = new org.eclipse.swt.widgets.Button(actions, SWT.PUSH);
        treasureSearchButton.setText(I18n.t("treasureSearch.button"));
        styleActionButton(treasureSearchButton);
        treasureSearchButton.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
        treasureSearchButton.addListener(SWT.Selection, event -> openTreasureSearchDialog(simulator));

        org.eclipse.swt.widgets.Button finishButton = new org.eclipse.swt.widgets.Button(actions, SWT.PUSH);
        finishButton.setText(I18n.t("button.finishAdventure"));
        styleActionButton(finishButton);
        finishButton.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
        finishButton.addListener(SWT.Selection, event -> {
            discardAdventureSession();
            simulator.close();
            if (!shell().isDisposed()) {
                shell().forceActive();
            }
        });

        // En GTK, una List sin seleccion que recibe el foco selecciona su primera fila y emite Selection:
        // al abrir (o reanudar) el simulador eso cambiaria la carta seleccionada. El foco va a los botones.
        simulator.setTabList(new Control[] {actions, body});
        refreshSimulatorUi[0].run();
        simulator.open();
    }

    private void openTreasureSearchDialog(Shell parent) {
        EventDeckApp eventApp = getOrCreateEventDeckApp();
        ContentRepository contentRepository = eventApp.contentRepository();
        List<Event> treasures = activeTreasureEvents(contentRepository);
        if (treasures.isEmpty()) {
            showInfo(I18n.t("treasureSearch.title"), I18n.t("treasureSearch.empty"));
            return;
        }

        Shell dialog = new Shell(parent == null || parent.isDisposed() ? shell() : parent, SWT.DIALOG_TRIM | SWT.RESIZE);
        AppIcon.inherit(dialog, shell());
        dialog.setText(I18n.t("treasureSearch.title"));
        dialog.setBackground(theme().shellBackground);
        dialog.setLayout(new GridLayout(1, false));
        dialog.setSize(560, 620);

        Label filterLabel = new Label(dialog, SWT.NONE);
        filterLabel.setText(I18n.t("treasureSearch.filter"));
        styleDarkLabel(filterLabel, false);

        Text filterText = new Text(dialog, SWT.BORDER | SWT.SEARCH | SWT.ICON_SEARCH | SWT.CANCEL);
        filterText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        filterText.setMessage(I18n.t("treasureSearch.placeholder"));
        filterText.setFont(theme().bodyFont);

        Label resultsLabel = new Label(dialog, SWT.NONE);
        resultsLabel.setText(I18n.t("treasureSearch.results"));
        styleDarkLabel(resultsLabel, true);

        org.eclipse.swt.widgets.List resultList = new org.eclipse.swt.widgets.List(dialog, SWT.BORDER | SWT.SINGLE | SWT.V_SCROLL);
        resultList.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        resultList.setFont(theme().bodyFont);
        resultList.setBackground(theme().mist);
        resultList.setForeground(theme().ink);

        Composite actions = new Composite(dialog, SWT.NONE);
        actions.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false));
        actions.setBackground(theme().shellBackground);
        GridLayout actionsLayout = new GridLayout(2, false);
        actionsLayout.marginWidth = 0;
        actionsLayout.horizontalSpacing = 12;
        actions.setLayout(actionsLayout);

        Button showButton = new Button(actions, SWT.PUSH);
        showButton.setText(I18n.t("treasureSearch.show"));
        styleActionButton(showButton);

        Button closeButton = new Button(actions, SWT.PUSH);
        closeButton.setText(I18n.t("button.close"));
        styleActionButton(closeButton);

        List<Event> filteredTreasures = new ArrayList<>();
        Runnable refreshResults = () -> {
            String filter = filterText.getText() == null ? "" : filterText.getText().trim().toLowerCase();
            filteredTreasures.clear();
            filteredTreasures.addAll(treasures.stream()
                    .filter(treasure -> {
                        String name = treasure.name == null ? "" : treasure.name;
                        return filter.isEmpty() || name.toLowerCase().contains(filter);
                    })
                    .toList());
            resultList.removeAll();
            for (Event treasure : filteredTreasures) {
                resultList.add(treasure.name == null || treasure.name.isBlank() ? treasure.id : treasure.name);
            }
            showButton.setEnabled(!filteredTreasures.isEmpty());
            if (!filteredTreasures.isEmpty()) {
                resultList.setSelection(0);
            }
        };

        Runnable showSelectedTreasure = () -> {
            int index = resultList.getSelectionIndex();
            if (index < 0 || index >= filteredTreasures.size()) {
                return;
            }
            Event treasure = filteredTreasures.get(index);
            eventApp.showEntries(dialog, List.of(new EventEntry(treasure.id)));
        };

        filterText.addListener(SWT.Modify, event -> refreshResults.run());
        resultList.addListener(SWT.DefaultSelection, event -> showSelectedTreasure.run());
        showButton.addListener(SWT.Selection, event -> showSelectedTreasure.run());
        closeButton.addListener(SWT.Selection, event -> dialog.close());

        refreshResults.run();
        fitShellToDisplay(dialog, 40);
        dialog.open();
    }

    private List<Event> activeTreasureEvents(ContentRepository contentRepository) {
        if (contentRepository == null) {
            return List.of();
        }

        Map<String, Event> treasures = new LinkedHashMap<>();
        for (Table table : contentRepository.tables().values()) {
            if (table == null || !table.isActive() || table.getTableKind() != TableKind.TREASURE) {
                continue;
            }
            for (DrawableEntry entry : table.getEventEntries()) {
                if (!(entry instanceof EventEntry eventEntry)) {
                    continue;
                }
                Event event = contentRepository.findEvent(eventEntry.id);
                if (event != null && event.treasure) {
                    treasures.put(event.id, event);
                }
            }
        }
        return treasures.values().stream()
                .sorted(Comparator.comparing(event -> event.name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private void openAdventureMissionDialog(ObjectiveRoomAdventure selectedAdventure) {
        if (selectedAdventure == null || selectedAdventure.generic()) {
            return;
        }

        Shell missionDialog = CardFactory.createAdventureMissionCard(
                shell(),
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
            if (!display().readAndDispatch()) {
                display().sleep();
            }
        }
    }

    private void saveAdventureSession(SavedAdventure adventure) {
        try {
            adventureSessionStore.save(adventure);
        } catch (AdventureSessionStorageException ex) {
            // Un aviso por simulador: si el disco falla, no hay que repetirlo en cada carta.
            if (!adventureSaveErrorShown) {
                adventureSaveErrorShown = true;
                showError(I18n.t("dialog.adventureSimulator.title"), I18n.t(
                        "dialog.adventureSimulator.error.save", Map.of("error", String.valueOf(rootMessage(ex)))));
            }
        }
    }

    private void discardAdventureSession() {
        try {
            adventureSessionStore.discard();
        } catch (AdventureSessionStorageException ex) {
            showError(I18n.t("dialog.adventureSimulator.title"), I18n.t(
                    "dialog.adventureSimulator.error.save", Map.of("error", String.valueOf(rootMessage(ex)))));
        }
    }

    private static String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    public void offerPendingAdventure() {
        if (shell().isDisposed() || !adventureSessionStore.hasPendingSession()) {
            return;
        }

        Map<Long, DungeonCard> catalog = new LinkedHashMap<>();
        for (DungeonCard card : cards()) {
            catalog.putIfAbsent(card.getId(), card);
        }
        List<ContentIssue> issues = new ArrayList<>();
        Optional<SavedAdventure> loaded = adventureSessionStore.loadPending(catalog, issues::add);
        if (loaded.isEmpty()) {
            showContentIssues(issues);
            return;
        }

        SavedAdventure saved = loaded.get();
        ObjectiveRoomAdventure mission = resolveMission(saved, issues);
        showContentIssues(issues);

        ResumeChoice choice = promptResumeAdventure(saved);
        if (choice == ResumeChoice.DISCARD) {
            discardAdventureSession();
            return;
        }
        if (choice != ResumeChoice.RESUME) {
            return;
        }

        AdventureAmbience ambience = AdventureAmbience.fromStorageValue(saved.ambience());
        int level = Math.max(1, Math.min(10, saved.adventureLevel()));
        setActiveAdventureContext(ambience, level);
        openAdventureSimulator(
                saved.session(),
                saved.objectiveRoomCardId(),
                mission,
                ambience.displayName(),
                level,
                saved.environment());
    }

    private ObjectiveRoomAdventure resolveMission(SavedAdventure saved, List<ContentIssue> issues) {
        if (saved.missionId() == null || saved.missionId().isBlank() || saved.objectiveRoom() == null) {
            return null;
        }
        try {
            for (ObjectiveRoomAdventure adventure
                    : objectiveRoomAdventureRepository.loadAdventuresForObjectiveRoom(saved.objectiveRoom())) {
                if (saved.missionId().equals(adventure.id())) {
                    return adventure;
                }
            }
        } catch (ObjectiveRoomAdventureRepositoryException ignored) {
            // Se reporta abajo igual que una mision que ya no existe.
        }
        issues.add(new ContentIssue(
                I18n.t("session.issue.title"),
                I18n.t("session.issue.missionLost", Map.of("id", saved.missionId()))));
        return null;
    }

    private void showContentIssues(List<ContentIssue> issues) {
        if (issues.isEmpty()) {
            return;
        }
        StringBuilder message = new StringBuilder();
        for (ContentIssue issue : issues) {
            if (message.length() > 0) {
                message.append("\n\n");
            }
            message.append(issue.message());
        }
        MessageBox box = new MessageBox(shell(), SWT.ICON_WARNING | SWT.OK);
        box.setText(issues.get(0).title());
        box.setMessage(message.toString());
        box.open();
    }

    private ResumeChoice promptResumeAdventure(SavedAdventure saved) {
        Shell dialog = new Shell(shell(), SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
        AppIcon.inherit(dialog, shell());
        dialog.setText(I18n.t("dialog.resumeAdventure.title"));
        dialog.setBackground(theme().shellBackground);
        dialog.setLayout(new GridLayout(1, false));

        String objectiveRoomName = saved.objectiveRoom() == null
                ? I18n.t("dialog.resumeAdventure.unknownObjectiveRoom")
                : saved.objectiveRoom().getName();
        createDialogHeader(
                dialog,
                I18n.t("dialog.resumeAdventure.title"),
                I18n.t("dialog.resumeAdventure.message", Map.of(
                        "objectiveRoom", objectiveRoomName,
                        "level", saved.adventureLevel())));

        Composite actions = new Composite(dialog, SWT.NONE);
        actions.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false));
        GridLayout actionsLayout = new GridLayout(2, false);
        actionsLayout.marginWidth = 0;
        actionsLayout.horizontalSpacing = 12;
        actions.setLayout(actionsLayout);
        actions.setBackground(theme().shellBackground);

        ResumeChoice[] choice = {ResumeChoice.LATER};
        Button discardButton = new Button(actions, SWT.PUSH);
        discardButton.setText(I18n.t("button.discardAdventure"));
        styleActionButton(discardButton);
        discardButton.addListener(SWT.Selection, event -> {
            choice[0] = ResumeChoice.DISCARD;
            dialog.close();
        });
        Button resumeButton = new Button(actions, SWT.PUSH);
        resumeButton.setText(I18n.t("button.resumeAdventure"));
        styleActionButton(resumeButton);
        resumeButton.addListener(SWT.Selection, event -> {
            choice[0] = ResumeChoice.RESUME;
            dialog.close();
        });
        dialog.setDefaultButton(resumeButton);

        dialog.pack();
        Point size = dialog.getSize();
        dialog.setSize(Math.max(size.x, 640), size.y);
        Rectangle parentBounds = shell().getBounds();
        dialog.setLocation(
                parentBounds.x + (parentBounds.width - dialog.getSize().x) / 2,
                parentBounds.y + (parentBounds.height - dialog.getSize().y) / 2);
        dialog.open();
        while (!dialog.isDisposed()) {
            if (!display().readAndDispatch()) {
                display().sleep();
            }
        }
        return choice[0];
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

        Rectangle clientArea = display().getPrimaryMonitor().getClientArea();
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
        AppIcon.inherit(dialog, parent);
        dialog.setText(I18n.t("dialog.splitDeck.title"));
        dialog.setBackground(theme().shellBackground);
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
        actions.setBackground(theme().panelBackground);

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
            if (!display().readAndDispatch()) {
                display().sleep();
            }
        }

        if (result[0] < 2) {
            return null;
        }
        return result[0];
    }

    private Integer askAdditionalCardCount(Shell parent, int maxCards) {
        Shell dialog = new Shell(parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
        AppIcon.inherit(dialog, parent);
        dialog.setText(I18n.t("button.addCardsToDeck"));
        dialog.setBackground(theme().shellBackground);
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
        actions.setBackground(theme().panelBackground);

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
            if (!display().readAndDispatch()) {
                display().sleep();
            }
        }

        if (result[0] < 1) {
            return null;
        }
        return result[0];
    }
}
