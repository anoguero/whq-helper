package com.whq.app.render;

import org.eclipse.swt.graphics.Rectangle;

final class DungeonCardLayout {
    private static final double REF_WIDTH = 1024.0;
    private static final double REF_HEIGHT = 1536.0;

    private static final double TITLE_X = 110.0 / REF_WIDTH;
    private static final double TITLE_Y = 124.0 / REF_HEIGHT;
    private static final double TITLE_W = 806.0 / REF_WIDTH;
    private static final double TITLE_H = 96.0 / REF_HEIGHT;

    private static final double DESC_X = 125.0 / REF_WIDTH;
    private static final double DESC_Y = 258.0 / REF_HEIGHT;
    private static final double DESC_W = 770.0 / REF_WIDTH;
    private static final double DESC_H = 228.0 / REF_HEIGHT;

    private static final double RULES_X = 125.0 / REF_WIDTH;
    private static final double RULES_Y = 540.0 / REF_HEIGHT;
    private static final double RULES_W = 770.0 / REF_WIDTH;
    private static final double RULES_H = 262.0 / REF_HEIGHT;

    private static final double TILE_X = 243.0 / REF_WIDTH;
    private static final double TILE_Y = 842.0 / REF_HEIGHT;
    private static final double TILE_W = 540.0 / REF_WIDTH;
    private static final double TILE_H = 352.0 / REF_HEIGHT;

    private static final double BAND_LEFT_X = 108.0 / REF_WIDTH;
    private static final double BAND_RIGHT_X = 915.0 / REF_WIDTH;
    private static final double BAND_TOP_Y = 1328.0 / REF_HEIGHT;
    private static final double BAND_BOTTOM_Y = 1404.0 / REF_HEIGHT;

    private final int templateWidth;
    private final int templateHeight;
    private final double layoutScale;

    DungeonCardLayout(int templateWidth, int templateHeight) {
        this.templateWidth = templateWidth;
        this.templateHeight = templateHeight;
        this.layoutScale = Math.min(templateWidth / REF_WIDTH, templateHeight / REF_HEIGHT);
    }

    Rectangle titleBox() {
        return scaledRect(TITLE_X, TITLE_Y, TITLE_W, TITLE_H);
    }

    Rectangle bodyBox() {
        Rectangle descBox = scaledRect(DESC_X, DESC_Y, DESC_W, DESC_H);
        Rectangle rulesBox = scaledRect(RULES_X, RULES_Y, RULES_W, RULES_H);
        return new Rectangle(
                descBox.x,
                descBox.y,
                Math.max(descBox.width, rulesBox.width),
                (rulesBox.y + rulesBox.height) - descBox.y);
    }

    Rectangle tileBox() {
        return scaledRect(TILE_X, TILE_Y, TILE_W, TILE_H);
    }

    Rectangle typeBox() {
        int leftX = scaledX(BAND_LEFT_X);
        int rightX = scaledX(BAND_RIGHT_X);
        int topY = scaledY(BAND_TOP_Y);
        int bottomY = scaledY(BAND_BOTTOM_Y);
        return new Rectangle(
                leftX,
                topY + scaledPx(6),
                rightX - leftX,
                Math.max(scaledPx(10), bottomY - topY - scaledPx(10)));
    }

    int bandLeftX() {
        return scaledX(BAND_LEFT_X);
    }

    int bandRightX() {
        return scaledX(BAND_RIGHT_X);
    }

    int bandTopY() {
        return scaledY(BAND_TOP_Y);
    }

    int bandBottomY() {
        return scaledY(BAND_BOTTOM_Y);
    }

    int scaledPx(int valueAtReferenceSize) {
        return Math.max(1, (int) Math.round(valueAtReferenceSize * layoutScale));
    }

    int scaledFont(int baseSize) {
        return Math.max(12, scaledPx(baseSize));
    }

    private Rectangle scaledRect(double relX, double relY, double relW, double relH) {
        int x = scaledX(relX);
        int y = scaledY(relY);
        int width = Math.max(1, (int) Math.round(templateWidth * relW));
        int height = Math.max(1, (int) Math.round(templateHeight * relH));
        return new Rectangle(x, y, width, height);
    }

    private int scaledX(double relative) {
        return (int) Math.round(templateWidth * relative);
    }

    private int scaledY(double relative) {
        return (int) Math.round(templateHeight * relative);
    }
}
