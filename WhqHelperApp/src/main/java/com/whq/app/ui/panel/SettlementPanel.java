package com.whq.app.ui.panel;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.whq.app.AppPaths;
import com.whq.app.i18n.EditableContentTranslations;
import com.whq.app.i18n.I18n;
import com.whq.app.io.SafeXml;
import com.whq.app.ui.AppIcon;

import pms.whq.EventDeckApp;
import pms.whq.Settings;
import pms.whq.swt.CardFactory;

/** Dialogo de Nuevo Asentamiento: localizaciones del asentamiento y mazo de eventos de asentamiento. */
public final class SettlementPanel extends AppPanel {

    private static final String SETTLEMENT_TYPE_ANY = "any";

    private record SettlementLocation(
            String id,
            String name,
            String description,
            String rules,
            List<String> visitors,
            Set<String> availableTypes) {
    }

    public SettlementPanel(AppContext context) {
        super(context);
    }

    private Image loadSettlementDeckPreviewImage() {
        String imgDir = Settings.getSetting(Settings.IMG_DIR);
        if (imgDir == null || imgDir.isBlank()) {
            return null;
        }
        Path path = Path.of(imgDir).resolve("settlement.png").normalize();
        if (!java.nio.file.Files.isRegularFile(path)) {
            return null;
        }
        return new Image(display(), path.toString());
    }

    private void paintSettlementDeckBack(PaintEvent event, Canvas canvas, Image image) {
        Rectangle area = canvas.getClientArea();
        theme().paintDarkPanel(event.gc, area);
        if (image == null || image.isDisposed() || area.width <= 0 || area.height <= 0) {
            return;
        }

        Rectangle source = image.getBounds();
        int targetWidth = Math.max(1, area.width - 24);
        int targetHeight = Math.max(1, area.height - 24);
        double scale = Math.min((double) targetWidth / source.width, (double) targetHeight / source.height);
        int drawWidth = Math.max(1, (int) Math.round(source.width * scale));
        int drawHeight = Math.max(1, (int) Math.round(source.height * scale));
        int drawX = area.x + (area.width - drawWidth) / 2;
        int drawY = area.y + (area.height - drawHeight) / 2;

        event.gc.setAdvanced(true);
        event.gc.setAntialias(SWT.ON);
        event.gc.setInterpolation(SWT.HIGH);
        event.gc.drawImage(image, 0, 0, source.width, source.height, drawX, drawY, drawWidth, drawHeight);
    }

    public void openNewSettlementDialog() {
        List<SettlementLocation> allLocations;
        try {
            allLocations = loadSettlementLocations();
        } catch (Exception ex) {
            showError(I18n.t("dialog.newSettlement.title"), I18n.t("dialog.newSettlement.error.loadLocations", Map.of("error", String.valueOf(ex.getMessage()))));
            return;
        }

        Shell dialog = new Shell(shell(), SWT.SHELL_TRIM | SWT.RESIZE | SWT.MAX);
        AppIcon.inherit(dialog, shell());
        dialog.setText(I18n.t("dialog.newSettlement.title"));
        dialog.setBackground(theme().shellBackground);
        dialog.setLayout(new GridLayout(1, false));
        dialog.setSize(1180, 760);
        dialog.setMaximized(true);

        createDialogHeader(
                dialog,
                I18n.t("dialog.newSettlement.title"),
                I18n.t("dialog.newSettlement.subtitle"));

        SashForm horizontalSplit = new SashForm(dialog, SWT.HORIZONTAL);
        horizontalSplit.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        horizontalSplit.setBackground(theme().brassDark);

        SashForm leftSplit = new SashForm(horizontalSplit, SWT.VERTICAL);
        leftSplit.setBackground(theme().brassDark);

        Composite locationsPanel = createDarkPanel(leftSplit, 1);
        Group locationsGroup = new Group(locationsPanel, SWT.NONE);
        locationsGroup.setText(I18n.t("dialog.newSettlement.group.locations"));
        locationsGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        locationsGroup.setBackground(theme().panelBackground);
        locationsGroup.setForeground(theme().mist);
        locationsGroup.setFont(theme().sectionTitleFont);
        GridLayout locationsLayout = new GridLayout(1, false);
        locationsLayout.marginWidth = 12;
        locationsLayout.marginHeight = 12;
        locationsLayout.verticalSpacing = 10;
        locationsGroup.setLayout(locationsLayout);

        Label settlementTypeLabel = new Label(locationsGroup, SWT.NONE);
        settlementTypeLabel.setText(I18n.t("dialog.newSettlement.label.settlementType"));
        styleDarkLabel(settlementTypeLabel, false);

        org.eclipse.swt.widgets.Combo settlementTypeCombo = new org.eclipse.swt.widgets.Combo(
                locationsGroup,
                SWT.DROP_DOWN | SWT.READ_ONLY);
        String[] settlementTypeValues = new String[] {
                SETTLEMENT_TYPE_ANY,
                "city",
                "town",
                "village",
                "outskirts",
                "special"
        };
        settlementTypeCombo.setItems(new String[] {
                I18n.t("dialog.newSettlement.type.any"),
                I18n.t("dialog.newSettlement.type.city"),
                I18n.t("dialog.newSettlement.type.town"),
                I18n.t("dialog.newSettlement.type.village"),
                I18n.t("dialog.newSettlement.type.outskirts"),
                I18n.t("dialog.newSettlement.type.special")
        });
        settlementTypeCombo.select(0);
        settlementTypeCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        styleCombo(settlementTypeCombo);

        org.eclipse.swt.widgets.List locationsList =
                new org.eclipse.swt.widgets.List(locationsGroup, SWT.BORDER | SWT.V_SCROLL);
        locationsList.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        locationsList.setBackground(theme().mist);
        locationsList.setForeground(theme().ink);
        locationsList.setFont(theme().bodyFont);

        Label emptyLocationsLabel = new Label(locationsGroup, SWT.WRAP);
        emptyLocationsLabel.setText(
                allLocations.isEmpty()
                        ? I18n.t("dialog.newSettlement.locations.empty")
                        : I18n.t("dialog.newSettlement.locations.noneForType"));
        emptyLocationsLabel.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false));
        styleDarkLabel(emptyLocationsLabel, false);

        Composite deckPanel = createDarkPanel(leftSplit, 1);
        Group deckGroup = new Group(deckPanel, SWT.NONE);
        deckGroup.setText(I18n.t("dialog.newSettlement.group.deck"));
        deckGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        deckGroup.setBackground(theme().panelBackground);
        deckGroup.setForeground(theme().mist);
        deckGroup.setFont(theme().sectionTitleFont);
        GridLayout deckLayout = new GridLayout(2, false);
        deckLayout.marginWidth = 16;
        deckLayout.marginHeight = 16;
        deckLayout.horizontalSpacing = 12;
        deckGroup.setLayout(deckLayout);

        Canvas settlementDeckCanvas = new Canvas(deckGroup, SWT.DOUBLE_BUFFERED | SWT.BORDER);
        GridData settlementDeckCanvasData = new GridData(SWT.FILL, SWT.FILL, true, true);
        settlementDeckCanvasData.minimumWidth = 220;
        settlementDeckCanvas.setLayoutData(settlementDeckCanvasData);
        settlementDeckCanvas.setBackground(theme().panelBackgroundAlt);
        settlementDeckCanvas.setToolTipText(I18n.t("button.clickHere"));
        Image settlementDeckBack = loadSettlementDeckPreviewImage();
        if (settlementDeckBack != null) {
            settlementDeckCanvas.addPaintListener(event -> paintSettlementDeckBack(event, settlementDeckCanvas, settlementDeckBack));
            settlementDeckCanvas.addDisposeListener(event -> {
                if (!settlementDeckBack.isDisposed()) {
                    settlementDeckBack.dispose();
                }
            });
        } else {
            settlementDeckCanvas.addPaintListener(event -> {
                Rectangle area = settlementDeckCanvas.getClientArea();
                theme().paintDarkPanel(event.gc, area);
                event.gc.setForeground(theme().parchment);
                event.gc.setFont(theme().bodyFont);
                org.eclipse.swt.graphics.Point extent = event.gc.textExtent(I18n.t("button.clickHere"), SWT.DRAW_TRANSPARENT);
                int x = area.x + Math.max(12, (area.width - extent.x) / 2);
                int y = area.y + Math.max(12, (area.height - extent.y) / 2);
                event.gc.drawText(I18n.t("button.clickHere"), x, y, true);
            });
        }
        settlementDeckCanvas.addListener(SWT.MouseUp, event -> {
            EventDeckApp eventApp = getOrCreateEventDeckApp();
            eventApp.reloadData();
            eventApp.drawSettlementEvent();
        });

        Button closeAllSettlementCardsButton = new Button(deckGroup, SWT.PUSH);
        closeAllSettlementCardsButton.setText(I18n.t("menu.item.closeAllCards"));
        closeAllSettlementCardsButton.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, false, false));
        closeAllSettlementCardsButton.setBackground(theme().panelBackgroundAlt);
        closeAllSettlementCardsButton.setForeground(theme().mist);
        closeAllSettlementCardsButton.setFont(theme().bodyFont);
        closeAllSettlementCardsButton.addListener(SWT.Selection, event -> getOrCreateEventDeckApp().closeAllOpenCards());

        Composite previewPanel = createParchmentPanel(horizontalSplit, 1);
        Group previewGroup = new Group(previewPanel, SWT.NONE);
        previewGroup.setText(I18n.t("dialog.newSettlement.group.preview"));
        previewGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        previewGroup.setBackground(theme().parchment);
        previewGroup.setForeground(theme().ink);
        previewGroup.setFont(theme().sectionTitleFont);
        GridLayout previewLayout = new GridLayout(1, false);
        previewLayout.marginWidth = 18;
        previewLayout.marginHeight = 18;
        previewGroup.setLayout(previewLayout);

        Composite previewHost = new Composite(previewGroup, SWT.NONE);
        previewHost.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        FillLayout previewHostLayout = new FillLayout();
        previewHostLayout.marginWidth = 0;
        previewHostLayout.marginHeight = 0;
        previewHost.setLayout(previewHostLayout);
        previewHost.setBackground(theme().parchment);

        List<SettlementLocation> visibleLocations = new ArrayList<>();

        Runnable refreshPreview =
                () -> {
                    for (org.eclipse.swt.widgets.Control child : previewHost.getChildren()) {
                        child.dispose();
                    }

                    int selectionIndex = locationsList.getSelectionIndex();
                    if (selectionIndex < 0 || selectionIndex >= visibleLocations.size()) {
                        Label previewPlaceholder = new Label(previewHost, SWT.WRAP | SWT.CENTER);
                        previewPlaceholder.setText(I18n.t("dialog.newSettlement.preview.pending"));
                        styleParchmentLabel(previewPlaceholder, false);
                    } else {
                        SettlementLocation selectedLocation = visibleLocations.get(selectionIndex);
                        CardFactory.createSettlementLocationCardPreview(
                                previewHost,
                                selectedLocation.name(),
                                selectedLocation.description(),
                                selectedLocation.rules(),
                                selectedLocation.visitors());
                    }
                    previewHost.layout(true, true);
                };

        Runnable refreshLocations =
                () -> {
                    visibleLocations.clear();
                    locationsList.removeAll();

                    String selectedType =
                            settlementTypeCombo.getSelectionIndex() >= 0
                                    ? settlementTypeValues[settlementTypeCombo.getSelectionIndex()]
                                    : SETTLEMENT_TYPE_ANY;

                    for (SettlementLocation location : allLocations) {
                        if (SETTLEMENT_TYPE_ANY.equals(selectedType)
                                || location.availableTypes().contains(selectedType)) {
                            visibleLocations.add(location);
                            locationsList.add(location.name());
                        }
                    }

                    visibleLocations.sort(Comparator.comparing(SettlementLocation::name, String.CASE_INSENSITIVE_ORDER));
                    locationsList.removeAll();
                    for (SettlementLocation location : visibleLocations) {
                        locationsList.add(location.name());
                    }

                    boolean hasLocations = !visibleLocations.isEmpty();
                    emptyLocationsLabel.setVisible(!hasLocations);
                    emptyLocationsLabel.setText(
                            allLocations.isEmpty()
                                    ? I18n.t("dialog.newSettlement.locations.empty")
                                    : I18n.t("dialog.newSettlement.locations.noneForType"));

                    if (hasLocations) {
                        locationsList.select(0);
                    }
                    refreshPreview.run();
                    locationsGroup.layout(true, true);
                };

        settlementTypeCombo.addListener(SWT.Selection, event -> refreshLocations.run());
        locationsList.addListener(SWT.Selection, event -> refreshPreview.run());

        leftSplit.setWeights(new int[] {50, 50});
        horizontalSplit.setWeights(new int[] {50, 50});
        refreshLocations.run();

        dialog.open();
    }

    private List<SettlementLocation> loadSettlementLocations() throws Exception {
        Path locationsDirectory = AppPaths.contentPath(projectRoot(), "data/xml/locations");
        if (!Files.isDirectory(locationsDirectory)) {
            return List.of();
        }

        EditableContentTranslations translations = EditableContentTranslations.load(projectRoot(), I18n.getLanguage());
        Map<String, SettlementLocation> locationsById = new LinkedHashMap<>();

        List<Path> files;
        try (var stream = AppPaths.listContentFiles(projectRoot(), "data/xml/locations").stream()) {
            files = stream
                    .filter(this::isSettlementLocationXmlFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase()))
                    .toList();
        }

        DocumentBuilderFactory factory = SafeXml.newFactory();
        factory.setNamespaceAware(false);

        for (Path file : files) {
            var document = factory.newDocumentBuilder().parse(file.toFile());
            Element root = document.getDocumentElement();
            if (root == null || !"locations".equals(root.getTagName())) {
                continue;
            }

            NodeList children = root.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node node = children.item(i);
                if (node.getNodeType() != Node.ELEMENT_NODE || !"location".equals(node.getNodeName())) {
                    continue;
                }

                Element locationElement = (Element) node;
                String id = locationElement.getAttribute("id") == null ? "" : locationElement.getAttribute("id").trim();
                if (id.isEmpty()) {
                    continue;
                }

                String rawName = childText(locationElement, "name");
                String rawDescription = childText(locationElement, "description");
                String rawRules = childText(locationElement, "rules");

                String translatedName = translations.t("location." + id + ".name", rawName);
                String translatedDescription = translations.t("location." + id + ".description", rawDescription);
                String translatedRules = translations.t("location." + id + ".rules", rawRules);

                Set<String> availableTypes = extractAvailableTypes(locationElement);
                List<String> visitors = extractVisitors(locationElement);
                locationsById.put(
                        id,
                        new SettlementLocation(
                                id,
                                translatedName,
                                translatedDescription,
                                translatedRules,
                                visitors,
                                availableTypes));
            }
        }

        return locationsById.values().stream()
                .sorted(Comparator.comparing(SettlementLocation::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private boolean isSettlementLocationXmlFile(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return false;
        }
        String fileName = file.getFileName().toString().toLowerCase();
        return fileName.endsWith(".xml")
                && !fileName.endsWith("-schema.xsd")
                && !fileName.endsWith(".bak")
                && !fileName.startsWith("#");
    }

    private static Set<String> extractAvailableTypes(Element locationElement) {
        Element availableElement = directChild(locationElement, "available");
        if (availableElement == null) {
            return Set.of();
        }
        java.util.LinkedHashSet<String> types = new java.util.LinkedHashSet<>();
        NodeList children = availableElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE || !"type".equals(child.getNodeName())) {
                continue;
            }
            String type = child.getTextContent() == null ? "" : child.getTextContent().trim().toLowerCase();
            if (!type.isEmpty()) {
                types.add(type);
            }
        }
        return types;
    }

    private static List<String> extractVisitors(Element locationElement) {
        Element visitorsElement = directChild(locationElement, "visitors");
        if (visitorsElement == null) {
            return List.of();
        }
        List<String> visitors = new ArrayList<>();
        NodeList children = visitorsElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE || !"visitor".equals(child.getNodeName())) {
                continue;
            }
            String visitor = child.getTextContent() == null ? "" : child.getTextContent().trim();
            if (!visitor.isEmpty()) {
                visitors.add(visitor);
            }
        }
        return List.copyOf(visitors);
    }
}
