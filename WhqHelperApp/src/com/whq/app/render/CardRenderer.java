package com.whq.app.render;

import java.nio.file.Path;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Device;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Display;

import com.whq.app.model.DungeonCard;
import com.whq.app.ui.FontResources;

public class CardRenderer {
    private static final int TITLE_FONT_BASE = 56;
    private static final int DESC_FONT_BASE = 56;
    private static final int RULES_FONT_BASE = 50;
    private static final int TYPE_FONT_BASE = 54;

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
        this.template = new Image(device, projectRoot.resolve("resources/dungeon-card-template.png").toString());

        Rectangle templateBounds = template.getBounds();
        this.templateWidth = templateBounds.width;
        this.templateHeight = templateBounds.height;
        this.layout = new DungeonCardLayout(templateWidth, templateHeight);
        this.textRenderer = new DungeonTextRenderer(device);
        this.tileRenderer = new DungeonTileRenderer(device, projectRoot);

        this.titleFont = createBestFont(
                new String[] {"Caslon Antique", "Casablanca Antique", "Cinzel", "Trajan Pro", "Times New Roman", "Serif"},
                layout.scaledFont(TITLE_FONT_BASE),
                SWT.BOLD);
        this.descriptionFont = createBestFont(
                new String[] {"Book Antiqua", "Newtext Bk BT", "Georgia", "Times New Roman", "Serif"},
                layout.scaledFont(DESC_FONT_BASE),
                SWT.BOLD | SWT.ITALIC);
        this.rulesFont = createBestFont(
                new String[] {"Book Antiqua", "Newtext Bk BT", "Trebuchet MS", "Garamond", "Arial", "Sans"},
                layout.scaledFont(RULES_FONT_BASE),
                SWT.NORMAL);
        this.typeFont = createBestFont(
                new String[] {"Copperplate Gothic Bold", "Copperplate", "Trebuchet MS", "Verdana", "Arial", "Sans"},
                layout.scaledFont(TYPE_FONT_BASE),
                SWT.BOLD);
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
        Color titleColor = new Color(
                device,
                card.getType().getAccentRed(),
                card.getType().getAccentGreen(),
                card.getType().getAccentBlue());

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

        textRenderer.drawWrappedCenteredText(gc, card.getType().getLabel(), layout.typeBox(), typeFont, textColor);
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
