package com.whq.app.render;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Device;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.graphics.TextStyle;
import org.eclipse.swt.graphics.TextLayout;

final class DungeonTextRenderer {
    private static final double BODY_TEXT_ASCENT_RATIO = 0.93;
    private static final double BODY_TEXT_DESCENT_RATIO = 0.78;

    private final Device device;

    DungeonTextRenderer(Device device) {
        this.device = device;
    }

    void drawTitleText(GC gc, String text, Rectangle bounds, Font baseFont, Color color, DungeonCardLayout layout) {
        int horizontalPadding = layout.scaledPx(24);
        Rectangle innerBounds = new Rectangle(
                bounds.x + horizontalPadding,
                bounds.y,
                Math.max(1, bounds.width - (horizontalPadding * 2)),
                Math.max(1, bounds.height));

        drawWrappedCenteredTextAutoFit(gc, text, innerBounds, baseFont, color, layout.scaledFont(30));
    }

    void drawBodyTextAutoFit(
            GC gc,
            String descriptionText,
            String rulesText,
            Rectangle bounds,
            Color color,
            int minFontSize,
            Font descriptionFont,
            Font rulesFont) {
        String safeDescription = descriptionText == null ? "" : descriptionText.trim();
        String safeRules = rulesText == null ? "" : rulesText.trim();
        String combinedText = combineBodyText(safeDescription, safeRules);
        if (combinedText.isEmpty()) {
            return;
        }

        TextLayout layout = new TextLayout(device);
        Font sizedDescriptionFont = null;
        Font sizedRulesFont = null;
        try {
            FontData descriptionBaseData = descriptionFont.getFontData()[0];
            FontData rulesBaseData = rulesFont.getFontData()[0];
            int descStartSize = descriptionBaseData.getHeight();
            int rulesStartSize = rulesBaseData.getHeight();
            int sizeSteps = Math.max(descStartSize - minFontSize, rulesStartSize - minFontSize);

            for (int step = 0; step <= sizeSteps; step++) {
                if (sizedDescriptionFont != null) {
                    sizedDescriptionFont.dispose();
                }
                if (sizedRulesFont != null) {
                    sizedRulesFont.dispose();
                }

                int descriptionSize = Math.max(minFontSize, descStartSize - step);
                int rulesSize = Math.max(minFontSize, rulesStartSize - step);

                sizedDescriptionFont = new Font(
                        device,
                        descriptionBaseData.getName(),
                        descriptionSize,
                        descriptionBaseData.getStyle());
                sizedRulesFont = new Font(
                        device,
                        rulesBaseData.getName(),
                        rulesSize,
                        rulesBaseData.getStyle());

                configureBodyLayout(
                        layout,
                        combinedText,
                        safeDescription,
                        safeRules,
                        sizedDescriptionFont,
                        sizedRulesFont,
                        bounds.width);
                if (layout.getBounds().height <= bounds.height || (descriptionSize == minFontSize && rulesSize == minFontSize)) {
                    break;
                }
            }

            gc.setForeground(color);
            layout.draw(gc, bounds.x, bounds.y);
        } finally {
            if (sizedDescriptionFont != null && !sizedDescriptionFont.isDisposed()) {
                sizedDescriptionFont.dispose();
            }
            if (sizedRulesFont != null && !sizedRulesFont.isDisposed()) {
                sizedRulesFont.dispose();
            }
            layout.dispose();
        }
    }

    void drawWrappedCenteredText(GC gc, String text, Rectangle bounds, Font font, Color color) {
        TextLayout layout = new TextLayout(device);
        try {
            String safeText = text == null ? "" : text;
            layout.setText(safeText);
            layout.setFont(font);
            layout.setWidth(bounds.width);
            layout.setAlignment(SWT.CENTER);
            gc.setForeground(color);

            int textTop = getTextTop(layout);
            int textHeight = getTextHeight(layout);

            int y = bounds.y + Math.max(0, (bounds.height - textHeight) / 2) - textTop;
            layout.draw(gc, bounds.x, y);
        } finally {
            layout.dispose();
        }
    }

    private void drawWrappedCenteredTextAutoFit(
            GC gc,
            String text,
            Rectangle bounds,
            Font baseFont,
            Color color,
            int minFontSize) {
        TextLayout layout = new TextLayout(device);
        Font sizedFont = null;
        Rectangle previousClip = gc.getClipping();
        try {
            FontData baseData = baseFont.getFontData()[0];
            String fontName = baseData.getName();
            int fontStyle = baseData.getStyle();
            int startSize = Math.max(minFontSize, baseData.getHeight());
            String safeText = text == null ? "" : text.trim();

            layout.setAlignment(SWT.CENTER);
            layout.setWidth(bounds.width);

            boolean fitsHeight = false;
            for (int size = startSize; size >= minFontSize; size--) {
                if (sizedFont != null) {
                    sizedFont.dispose();
                }
                sizedFont = new Font(device, fontName, size, fontStyle);
                layout.setFont(sizedFont);
                layout.setText(safeText);

                if (getTextHeight(layout) <= bounds.height) {
                    fitsHeight = true;
                    break;
                }
            }

            if (!fitsHeight) {
                String trimmed = safeText;
                layout.setText(trimmed);
                while (!trimmed.isEmpty() && getTextHeight(layout) > bounds.height) {
                    trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
                    String candidate = trimmed.isEmpty() ? "" : trimmed + "...";
                    layout.setText(candidate);
                }
            }

            gc.setForeground(color);
            gc.setClipping(bounds);
            int textTop = getTextTop(layout);
            int textHeight = getTextHeight(layout);
            int y = bounds.y + Math.max(0, (bounds.height - textHeight) / 2) - textTop + 10;
            layout.draw(gc, bounds.x, y);
        } finally {
            gc.setClipping(previousClip);
            if (sizedFont != null && !sizedFont.isDisposed()) {
                sizedFont.dispose();
            }
            layout.dispose();
        }
    }

    private void configureBodyLayout(
            TextLayout layout,
            String combinedText,
            String descriptionText,
            String rulesText,
            Font descriptionBodyFont,
            Font rulesBodyFont,
            int bodyWidth) {
        layout.setText(combinedText);
        layout.setWidth(Math.max(1, bodyWidth));
        applyCompactBodyLineMetrics(layout);

        if (!descriptionText.isEmpty()) {
            layout.setStyle(new TextStyle(descriptionBodyFont, null, null), 0, descriptionText.length() - 1);
        }

        if (!rulesText.isEmpty()) {
            int rulesStart = descriptionText.isEmpty() ? 0 : descriptionText.length() + 2;
            layout.setStyle(new TextStyle(rulesBodyFont, null, null), rulesStart, combinedText.length() - 1);
        }
    }

    private String combineBodyText(String descriptionText, String rulesText) {
        if (descriptionText.isEmpty()) {
            return rulesText;
        }
        if (rulesText.isEmpty()) {
            return descriptionText;
        }
        return descriptionText + "\n\n" + rulesText;
    }

    private void applyCompactBodyLineMetrics(TextLayout layout) {
        int ascent = layout.getAscent();
        int descent = layout.getDescent();
        if (ascent <= 0 || descent <= 0) {
            return;
        }
        layout.setAscent(Math.max(1, (int) Math.round(ascent * BODY_TEXT_ASCENT_RATIO)));
        layout.setDescent(Math.max(1, (int) Math.round(descent * BODY_TEXT_DESCENT_RATIO)));
    }

    private int getTextTop(TextLayout layout) {
        if (layout.getLineCount() <= 0) {
            return 0;
        }
        return layout.getLineBounds(0).y;
    }

    private int getTextHeight(TextLayout layout) {
        int lineCount = layout.getLineCount();
        if (lineCount <= 0) {
            return 0;
        }

        Rectangle firstLine = layout.getLineBounds(0);
        Rectangle lastLine = layout.getLineBounds(lineCount - 1);
        return Math.max(1, (lastLine.y + lastLine.height) - firstLine.y);
    }
}
