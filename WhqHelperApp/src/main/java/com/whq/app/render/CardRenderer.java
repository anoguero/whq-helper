package com.whq.app.render;

import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Device;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Display;

import com.whq.app.AppPaths;
import com.whq.app.i18n.I18n;
import com.whq.app.model.DungeonCard;
import com.whq.app.ui.FontResources;
import com.whq.app.ui.WhqUiTheme;

public class CardRenderer {
    private static final int TITLE_FONT_BASE = 56;
    private static final int DESC_FONT_BASE = 56;
    private static final int RULES_FONT_BASE = 50;
    private static final int TYPE_FONT_BASE = 54;
    private static final String TEMPLATE_PATH = "resources/dungeon-card-template.png";
    private static final int BLANK_TEMPLATE_WIDTH = 818;
    private static final int BLANK_TEMPLATE_HEIGHT = 1270;

    private final Device device;
    private final Image template;
    private final int templateWidth;
    private final int templateHeight;
    private final DungeonCardLayout layout;
    private final DungeonTextRenderer textRenderer;
    private final DungeonTileRenderer tileRenderer;

    private final Font titleFont;
    private final Font descriptionFont;
    private final Font rulesFont;
    private final Font typeFont;

    public CardRenderer(Device device, Path projectRoot) {
        this.device = device;
        if (device instanceof Display display) {
            FontResources.loadBundledFonts(display, projectRoot);
        }
        this.template = loadTemplate(device, projectRoot);

        Rectangle templateBounds = template.getBounds();
        this.templateWidth = templateBounds.width;
        this.templateHeight = templateBounds.height;
        this.layout = new DungeonCardLayout(templateWidth, templateHeight);
        this.textRenderer = new DungeonTextRenderer(device);
        this.tileRenderer = new DungeonTileRenderer(device, projectRoot);

        this.titleFont = createBestFont(
                new String[] {"CasablancaAntique", "Casablanca Antique", "Caslon Antique", "Cinzel", "Trajan Pro", "Times New Roman", "Serif"},
                layout.scaledFont(TITLE_FONT_BASE),
                SWT.BOLD);
        this.descriptionFont = createBestFont(
                new String[] {"Newtext Bk BT", "Book Antiqua", "Georgia", "Times New Roman", "Serif"},
                layout.scaledFont(DESC_FONT_BASE),
                SWT.BOLD | SWT.ITALIC);
        this.rulesFont = createBestFont(
                new String[] {"Newtext Bk BT", "Book Antiqua", "Trebuchet MS", "Garamond", "Arial", "Sans"},
                layout.scaledFont(RULES_FONT_BASE),
                SWT.NORMAL);
        this.typeFont = createBestFont(
                new String[] {"Copperplate Gothic Bold", "Copperplate", "Trebuchet MS", "Verdana", "Arial", "Sans"},
                layout.scaledFont(TYPE_FONT_BASE),
                SWT.BOLD);
    }

    // Sin paquete de contenido no hay plantilla: un lienzo en blanco del mismo tamano que la plantilla
    // original mantiene intacta la maquetacion (DungeonCardLayout se calcula sobre ese tamano).
    private static Image loadTemplate(Device device, Path projectRoot) {
        Path path = AppPaths.contentPath(projectRoot, TEMPLATE_PATH);
        if (Files.isRegularFile(path)) {
            try {
                return new Image(device, path.toString());
            } catch (SWTException ex) {
                // Plantilla ilegible: se usa el lienzo en blanco.
            }
        }
        Image blank = new Image(device, BLANK_TEMPLATE_WIDTH, BLANK_TEMPLATE_HEIGHT);
        GC gc = new GC(blank);
        try {
            gc.setBackground(device.getSystemColor(SWT.COLOR_WHITE));
            gc.fillRectangle(0, 0, BLANK_TEMPLATE_WIDTH, BLANK_TEMPLATE_HEIGHT);
        } finally {
            gc.dispose();
        }
        return blank;
    }

    public void drawCard(GC gc, Rectangle targetBounds, DungeonCard card) {
        if (card == null) {
            gc.drawImage(template, 0, 0, templateWidth, templateHeight,
                    targetBounds.x, targetBounds.y, targetBounds.width, targetBounds.height);
            return;
        }

        Rectangle titleBox = layout.titleBox();
        Rectangle bodyBox = layout.bodyBox();
        Rectangle tileBox = layout.tileBox();

        Image offscreen = new Image(device, templateWidth, templateHeight);
        GC offscreenGc = new GC(offscreen);

        Color black = new Color(device, 0, 0, 0);
        Color titleColor = new Color(device, WhqUiTheme.cardTypeAccent(card.getType()));

        try {
            offscreenGc.setAntialias(SWT.ON);
            offscreenGc.setInterpolation(SWT.HIGH);
            offscreenGc.drawImage(template, 0, 0);

            int round = layout.scaledPx(28);
            offscreenGc.setBackground(black);
            offscreenGc.fillRoundRectangle(titleBox.x, titleBox.y, titleBox.width, titleBox.height, round, round);

            textRenderer.drawTitleText(offscreenGc, card.getName(), titleBox, titleFont, titleColor, layout);
            textRenderer.drawBodyTextAutoFit(
                    offscreenGc,
                    card.getDescriptionText(),
                    card.getRulesText(),
                    bodyBox,
                    black,
                    24,
                    descriptionFont,
                    rulesFont);

            tileRenderer.drawTile(offscreenGc, card, tileBox);
            drawBottomTypeBand(offscreenGc, card, black, titleColor);

            gc.setInterpolation(SWT.HIGH);
            gc.drawImage(offscreen, 0, 0, templateWidth, templateHeight,
                    targetBounds.x, targetBounds.y, targetBounds.width, targetBounds.height);
        } finally {
            black.dispose();
            titleColor.dispose();
            offscreenGc.dispose();
            offscreen.dispose();
        }
    }

    private void drawBottomTypeBand(GC gc, DungeonCard card, Color textColor, Color accentColor) {
        int leftX = layout.bandLeftX();
        int rightX = layout.bandRightX();
        int topY = layout.bandTopY();
        int bottomY = layout.bandBottomY();

        gc.setForeground(accentColor);
        gc.setLineWidth(layout.scaledPx(8));
        gc.drawLine(leftX, topY, rightX, topY);
        gc.drawLine(leftX, bottomY, rightX, bottomY);

        textRenderer.drawWrappedCenteredText(gc, localizedTypeLabel(card), layout.typeBox(), typeFont, textColor);
    }

    private String localizedTypeLabel(DungeonCard card) {
        if (card == null || card.getType() == null) {
            return "";
        }
        return switch (card.getType()) {
            case DUNGEON_ROOM -> I18n.t("cardType.dungeonRoom");
            case OBJECTIVE_ROOM -> I18n.t("cardType.objectiveRoom");
            case CORRIDOR -> I18n.t("cardType.corridor");
            case SPECIAL -> I18n.t("cardType.special");
        };
    }

    private Font createBestFont(String[] preferredNames, int pixelHeight, int style) {
        String selected = FontResources.pickAvailableFont(
                device,
                preferredNames[preferredNames.length - 1],
                preferredNames);
        return new Font(device, selected, pixelHeight, style);
    }

    public Point scaleToFit(Rectangle available) {
        double ratio = Math.min((double) available.width / templateWidth, (double) available.height / templateHeight);
        int width = Math.max(1, (int) Math.floor(templateWidth * ratio));
        int height = Math.max(1, (int) Math.floor(templateHeight * ratio));
        return new Point(width, height);
    }

    public void dispose() {
        template.dispose();
        titleFont.dispose();
        descriptionFont.dispose();
        rulesFont.dispose();
        typeFont.dispose();
        tileRenderer.dispose();
    }
}
