package com.whq.app.ui.panel;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import javax.xml.parsers.DocumentBuilderFactory;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.graphics.Transform;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Shell;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.whq.app.AppPaths;
import com.whq.app.i18n.EditableContentTranslations;
import com.whq.app.i18n.I18n;
import com.whq.app.io.SafeXml;
import com.whq.app.ui.AppIcon;

import pms.whq.Settings;

/** Contadores de aventurero: la reserva de contadores del grupo y sus ventanas. */
public final class WarriorCounters extends AppPanel {

    public record WarriorCounterDefinition(
            String id,
            String name,
            String race,
            String counterPath,
            String rulesPath) {
    }

    private final java.util.List<WarriorCounterDefinition> remainingWarriorCounters = new ArrayList<>();
    private final java.util.List<Shell> openWarriorCounterShells = new ArrayList<>();
    private String warriorCounterPartySignature = "";
    private boolean warriorCounterPoolInitialized;
    private Point nextWarriorCounterLocation;

    public WarriorCounters(AppContext context) {
        super(context);
    }

    public void genWarriorCounter() {
        try {
            refreshWarriorCounterPoolIfNeeded();
        } catch (Exception ex) {
            showError(I18n.t("dialog.warriorCounters.title"), I18n.t("dialog.warriorCounters.error.load", Map.of("error", String.valueOf(ex.getMessage()))));
            return;
        }

        if (remainingWarriorCounters.isEmpty()) {
            showInfo(I18n.t("dialog.warriorCounters.title"), I18n.t("dialog.warriorCounters.noneRemaining"));
            return;
        }

        WarriorCounterDefinition selectedCounter = remainingWarriorCounters.remove(new Random().nextInt(remainingWarriorCounters.size()));
        openWarriorCounterWindow(selectedCounter);
    }

    public void closeAllWarriorCounters() {
        java.util.List<Shell> shells = new ArrayList<>(openWarriorCounterShells);
        for (Shell counterShell : shells) {
            if (counterShell != null && !counterShell.isDisposed()) {
                counterShell.close();
            }
        }
        openWarriorCounterShells.removeIf(counterShell -> counterShell == null || counterShell.isDisposed());
    }

    public void resetWarriorCounterPoolCache() {
        remainingWarriorCounters.clear();
        warriorCounterPartySignature = "";
        warriorCounterPoolInitialized = false;
    }

    public List<String> configuredPartyIds() {
        String partySetting = Settings.getSetting(Settings.PARTY_WARRIORS);
        List<String> ids = new ArrayList<>();
        if (partySetting != null && !partySetting.isBlank()) {
            for (String token : partySetting.split(",")) {
                String id = token == null ? "" : token.trim();
                if (!id.isEmpty() && !ids.contains(id)) {
                    ids.add(id);
                }
            }
        }
        if (ids.isEmpty()) {
            ids.add("warrior-barbarian");
            ids.add("warrior-dwarf");
            ids.add("warrior-elf");
            ids.add("warrior-wizard");
        }
        return ids;
    }

    private void refreshWarriorCounterPoolIfNeeded() throws Exception {
        String partySetting = Settings.getSetting(Settings.PARTY_WARRIORS);
        String normalizedSignature = partySetting == null ? "" : partySetting.trim();
        if (warriorCounterPoolInitialized && normalizedSignature.equals(warriorCounterPartySignature)) {
            return;
        }

        java.util.List<WarriorCounterDefinition> allWarriors = loadWarriorCounters();
        Map<String, WarriorCounterDefinition> warriorsById = new LinkedHashMap<>();
        for (WarriorCounterDefinition warrior : allWarriors) {
            warriorsById.put(warrior.id(), warrior);
        }

        LinkedHashMap<String, WarriorCounterDefinition> configuredWarriors = new LinkedHashMap<>();
        if (partySetting != null && !partySetting.isBlank()) {
            for (String token : partySetting.split(",")) {
                String id = token == null ? "" : token.trim();
                if (id.isEmpty()) {
                    continue;
                }
                WarriorCounterDefinition warrior = warriorsById.get(id);
                if (warrior != null) {
                    configuredWarriors.put(id, warrior);
                }
            }
        }

        if (configuredWarriors.isEmpty()) {
            for (String id : List.of("warrior-barbarian", "warrior-dwarf", "warrior-elf", "warrior-wizard")) {
                WarriorCounterDefinition warrior = warriorsById.get(id);
                if (warrior != null) {
                    configuredWarriors.put(id, warrior);
                }
            }
        }

        remainingWarriorCounters.clear();
        remainingWarriorCounters.addAll(configuredWarriors.values());
        warriorCounterPartySignature = normalizedSignature;
        warriorCounterPoolInitialized = true;
    }

    public java.util.List<WarriorCounterDefinition> loadWarriorCounters() throws Exception {
        Path warriorsDirectory = AppPaths.contentPath(projectRoot(), "data/xml/warriors");
        if (!Files.isDirectory(warriorsDirectory)) {
            return List.of();
        }

        EditableContentTranslations translations = EditableContentTranslations.load(projectRoot(), I18n.getLanguage());
        Map<String, WarriorCounterDefinition> warriorsById = new LinkedHashMap<>();

        List<Path> files;
        try (var stream = AppPaths.listContentFiles(projectRoot(), "data/xml/warriors").stream()) {
            files = stream
                    .filter(this::isWarriorXmlFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase()))
                    .toList();
        }

        DocumentBuilderFactory factory = SafeXml.newFactory();
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
                String id = warriorElement.getAttribute("id") == null ? "" : warriorElement.getAttribute("id").trim();
                if (id.isEmpty()) {
                    continue;
                }

                String rawName = childText(warriorElement, "name");
                String rawRace = childText(warriorElement, "race");
                String counterPath = childText(warriorElement, "counter");
                String rawRulesPath = childText(warriorElement, "rules");

                warriorsById.put(
                        id,
                        new WarriorCounterDefinition(
                                id,
                                translations.t("warrior." + id + ".name", rawName),
                                translations.t("warrior." + id + ".race", rawRace),
                                counterPath,
                                translations.t("warrior." + id + ".rules", rawRulesPath)));
            }
        }

        return warriorsById.values().stream()
                .sorted(Comparator.comparing(WarriorCounterDefinition::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private boolean isWarriorXmlFile(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return false;
        }
        String name = file.getFileName().toString().toLowerCase();
        return name.endsWith(".xml") && !name.endsWith(".xml.bak");
    }

    // La imagen del contador es contenido: se busca en el paquete de contenido (o, si es del usuario,
    // en el runtime home), no junto a la aplicacion.
    static Path counterImagePath(Path runtimeHome, String counterPath) {
        return AppPaths.resolveContent(runtimeHome, counterPath);
    }

    private void openWarriorCounterWindow(WarriorCounterDefinition warrior) {
        if (warrior == null || warrior.counterPath() == null || warrior.counterPath().isBlank()) {
            showError(I18n.t("dialog.warriorCounters.title"), I18n.t("dialog.warriorCounters.error.missingImage", Map.of("path", "")));
            return;
        }

        Path imagePath = counterImagePath(projectRoot(), warrior.counterPath());
        if (!Files.isRegularFile(imagePath)) {
            showError(I18n.t("dialog.warriorCounters.title"), I18n.t("dialog.warriorCounters.error.missingImage", Map.of("path", String.valueOf(imagePath))));
            return;
        }

        Image image;
        try {
            image = new Image(display(), imagePath.toString());
        } catch (RuntimeException ex) {
            showError(I18n.t("dialog.warriorCounters.title"), I18n.t("dialog.warriorCounters.error.missingImage", Map.of("path", String.valueOf(imagePath))));
            return;
        }

        Rectangle bounds = image.getBounds();
        Shell dialog = new Shell(shell(), SWT.SHELL_TRIM | SWT.CLOSE);
        String partySignatureAtOpen = warriorCounterPartySignature;
        openWarriorCounterShells.add(dialog);
        AppIcon.inherit(dialog, shell());
        dialog.setText(warrior.name());
        dialog.setBackground(theme().shellBackground);
        dialog.setLayout(new FillLayout());

        Canvas canvas = new Canvas(dialog, SWT.DOUBLE_BUFFERED);
        canvas.setBackground(theme().shellBackground);
        canvas.addPaintListener(event -> {
            event.gc.drawImage(image, 0, 0);
            paintWarriorCounterLabel(event, canvas, warrior.name());
        });
        canvas.addDisposeListener(event -> {
            if (!image.isDisposed()) {
                image.dispose();
            }
        });
        dialog.addDisposeListener(event -> {
            openWarriorCounterShells.remove(dialog);
            if (!partySignatureAtOpen.equals(warriorCounterPartySignature)) {
                return;
            }
            boolean alreadyAvailable = remainingWarriorCounters.stream()
                    .anyMatch(candidate -> candidate.id().equals(warrior.id()));
            if (!alreadyAvailable) {
                remainingWarriorCounters.add(warrior);
                remainingWarriorCounters.sort(Comparator.comparing(WarriorCounterDefinition::name, String.CASE_INSENSITIVE_ORDER));
            }
        });

        Rectangle trim = dialog.computeTrim(0, 0, bounds.width, bounds.height);
        dialog.setSize(trim.width, trim.height);
        Rectangle screen = display().getPrimaryMonitor().getClientArea();
        if (nextWarriorCounterLocation == null) {
            Point parentLocation = shell().getLocation();
            nextWarriorCounterLocation = new Point(parentLocation.x + 40, parentLocation.y + 40);
        }
        dialog.setLocation(nextWarriorCounterLocation);
        int nextX = nextWarriorCounterLocation.x + 36;
        int nextY = nextWarriorCounterLocation.y + 36;
        if (nextX + trim.width > screen.x + screen.width || nextY + trim.height > screen.y + screen.height) {
            Point parentLocation = shell().getLocation();
            nextWarriorCounterLocation = new Point(parentLocation.x + 40, parentLocation.y + 40);
        } else {
            nextWarriorCounterLocation = new Point(nextX, nextY);
        }
        dialog.open();
    }

    private void paintWarriorCounterLabel(PaintEvent event, Canvas canvas, String label) {
        if (label == null || label.isBlank()) {
            return;
        }
        Rectangle area = canvas.getClientArea();
        if (area.width <= 0 || area.height <= 0) {
            return;
        }

        String text = label.trim().toUpperCase();
        int bandX = Math.round(area.width * 0.225f);
        int bandWidth = Math.round(area.width * 0.54f);
        int baselineY = Math.round(area.height * 0.775f);
        int fontHeight = 24;

        Font font = createWarriorCounterFont(canvas, fontHeight);
        GC gc = event.gc;
        gc.setAdvanced(true);
        gc.setAntialias(SWT.ON);
        gc.setTextAntialias(SWT.ON);
        gc.setForeground(display().getSystemColor(SWT.COLOR_BLACK));
        gc.setFont(font);
        try {
            drawFittedSingleLineText(gc, text, bandX, baselineY, bandWidth);
        } finally {
            font.dispose();
        }
    }

    private Font createWarriorCounterFont(Canvas canvas, int height) {
        String[] families = {
                "Copperplate Gothic Bold",
                "Copperplate Gothic",
                "Copperplate",
                "Times New Roman"
        };
        for (String family : families) {
            FontData data = new FontData(family, height, SWT.BOLD);
            Font font = new Font(canvas.getDisplay(), data);
            FontData[] actual = font.getFontData();
            if (actual != null && actual.length > 0) {
                String actualName = actual[0].getName();
                if (actualName != null && actualName.equalsIgnoreCase(family)) {
                    return font;
                }
            }
            font.dispose();
        }
        return new Font(canvas.getDisplay(), "Times New Roman", height, SWT.BOLD);
    }

    private void drawFittedSingleLineText(GC gc, String text, int x, int baselineY, int maxWidth) {
        if (text.isBlank()) {
            return;
        }
        int[] glyphWidths = new int[text.length()];
        int glyphWidthSum = 0;
        for (int i = 0; i < text.length(); i++) {
            Point extent = gc.textExtent(String.valueOf(text.charAt(i)), SWT.DRAW_TRANSPARENT);
            glyphWidths[i] = extent.x;
            glyphWidthSum += extent.x;
        }

        int letterCount = Math.max(0, text.length() - 1);
        int spacing = letterCount > 0 ? Math.min(6, Math.max(0, (maxWidth - glyphWidthSum) / letterCount)) : 0;
        int naturalWidth = glyphWidthSum + spacing * letterCount;

        Point textExtent = gc.textExtent(text, SWT.DRAW_TRANSPARENT);
        int drawHeight = textExtent.y;
        int drawY = baselineY - drawHeight / 2;

        if (naturalWidth <= maxWidth) {
            int drawX = x + (maxWidth - naturalWidth) / 2;
            drawTextWithSpacing(gc, text, glyphWidths, drawX, drawY, spacing);
            return;
        }

        float scaleX = (float) maxWidth / (float) Math.max(1, naturalWidth);
        float scaledWidth = naturalWidth * scaleX;
        float drawX = x + (maxWidth - scaledWidth) / 2f;

        Transform original = new Transform(gc.getDevice());
        gc.getTransform(original);
        Transform scaled = new Transform(gc.getDevice());
        scaled.translate(drawX, drawY);
        scaled.scale(scaleX, 1f);
        gc.setTransform(scaled);
        drawTextWithSpacing(gc, text, glyphWidths, 0, 0, spacing);
        gc.setTransform(original);
        scaled.dispose();
        original.dispose();
    }

    private void drawTextWithSpacing(GC gc, String text, int[] glyphWidths, int startX, int startY, int spacing) {
        int cursor = startX;
        for (int i = 0; i < text.length(); i++) {
            String glyph = String.valueOf(text.charAt(i));
            gc.drawText(glyph, cursor, startY, SWT.DRAW_TRANSPARENT);
            cursor += glyphWidths[i];
            if (i + 1 < text.length()) {
                cursor += spacing;
            }
        }
    }
}
