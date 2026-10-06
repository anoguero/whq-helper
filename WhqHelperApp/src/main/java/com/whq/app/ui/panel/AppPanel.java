package com.whq.app.ui.panel;

import java.nio.file.Path;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.graphics.TextLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Spinner;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.whq.app.model.DungeonCard;
import com.whq.app.render.CardRenderer;
import com.whq.app.storage.DungeonCardStore;
import com.whq.app.ui.WhqUiTheme;

import pms.whq.EventDeckApp;

/**
 * Base de las zonas extraidas de AppWindow. Expone el contexto con los mismos nombres que usaba la
 * ventana (theme(), cards()... en lugar de los campos, y showError, refreshCards... como antes),
 * para que el codigo de cada zona se mueva con el unico cambio de campo a accesor.
 */
public abstract class AppPanel {

    protected final AppContext context;

    protected AppPanel(AppContext context) {
        this.context = context;
    }

    protected Display display() {
        return context.display();
    }

    protected Shell shell() {
        return context.shell();
    }

    protected Path projectRoot() {
        return context.projectRoot();
    }

    protected WhqUiTheme theme() {
        return context.theme();
    }

    protected CardRenderer renderer() {
        return context.renderer();
    }

    protected List<DungeonCard> cards() {
        return context.cards();
    }

    protected DungeonCardStore cardStore() {
        return context.cardStore();
    }

    protected EventDeckApp getOrCreateEventDeckApp() {
        return context.eventDeckApp();
    }

    protected void showError(String title, String message) {
        context.showError(title, message);
    }

    protected void showInfo(String title, String message) {
        context.showInfo(title, message);
    }

    protected void refreshCards() {
        context.refreshCards();
    }

    public static String childText(Element parent, String tagName) {
        Element child = directChild(parent, tagName);
        if (child == null || child.getTextContent() == null) {
            return "";
        }
        return child.getTextContent().trim();
    }

    public static Element directChild(Element parent, String tagName) {
        if (parent == null || tagName == null || tagName.isBlank()) {
            return null;
        }
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && tagName.equals(child.getNodeName())) {
                return (Element) child;
            }
        }
        return null;
    }

    protected Canvas createDialogHeader(Composite parent, String title, String subtitle) {
        Canvas header = new Canvas(parent, SWT.DOUBLE_BUFFERED);
        GridData data = new GridData(SWT.FILL, SWT.TOP, true, false);
        data.heightHint = 210;
        header.setLayoutData(data);
        header.addPaintListener(event -> {
            Rectangle area = header.getClientArea();
            theme().paintHeroBanner(event.gc, area);
            event.gc.setForeground(theme().mist);
            Font titleFont = theme().heroTitleFont;
            Font resizedTitleFont = null;
            try {
                int availableWidth = Math.max(120, area.width - 96);
                Point titleExtent = measureHeaderText(event.gc, titleFont, title);
                if (titleExtent.x > availableWidth) {
                    FontData baseData = theme().heroTitleFont.getFontData()[0];
                    for (int size = baseData.getHeight() - 1; size >= 14; size--) {
                        Font candidate = new Font(display(), baseData.getName(), size, baseData.getStyle());
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
            TextLayout subtitleLayout = new TextLayout(display());
            try {
                subtitleLayout.setFont(theme().bodyFont);
                subtitleLayout.setText(subtitle == null ? "" : subtitle);
                subtitleLayout.setWidth(Math.max(120, area.width - 96));
                event.gc.setForeground(theme().parchment);
                subtitleLayout.draw(event.gc, area.x + 32, area.y + 78);
            } finally {
                subtitleLayout.dispose();
            }
        });
        return header;
    }

    protected Point measureHeaderText(org.eclipse.swt.graphics.GC gc, Font font, String text) {
        gc.setFont(font);
        return gc.textExtent(text == null ? "" : text, SWT.DRAW_TRANSPARENT);
    }

    protected Composite createDarkPanel(Composite parent, int columns) {
        Composite panel = new Composite(parent, SWT.DOUBLE_BUFFERED);
        panel.setBackground(theme().panelBackground);
        GridLayout layout = new GridLayout(columns, false);
        layout.marginWidth = 18;
        layout.marginHeight = 18;
        layout.horizontalSpacing = 12;
        layout.verticalSpacing = 10;
        panel.setLayout(layout);
        panel.addPaintListener(event -> theme().paintDarkPanel(event.gc, panel.getClientArea()));
        return panel;
    }

    protected Composite createParchmentPanel(Composite parent, int columns) {
        Composite panel = new Composite(parent, SWT.DOUBLE_BUFFERED);
        panel.setBackground(theme().parchment);
        GridLayout layout = new GridLayout(columns, false);
        layout.marginWidth = 18;
        layout.marginHeight = 18;
        layout.horizontalSpacing = 12;
        layout.verticalSpacing = 10;
        panel.setLayout(layout);
        panel.addPaintListener(event -> theme().paintParchmentPanel(event.gc, panel.getClientArea()));
        return panel;
    }

    protected void styleDarkLabel(Label label, boolean title) {
        label.setBackground(theme().panelBackground);
        label.setForeground(title ? theme().mist : theme().parchment);
        label.setFont(title ? theme().sectionTitleFont : theme().bodyFont);
    }

    protected void styleParchmentLabel(Label label, boolean title) {
        label.setBackground(theme().parchment);
        label.setForeground(title ? theme().ink : theme().mutedInk);
        label.setFont(title ? theme().sectionTitleFont : theme().bodyFont);
    }

    protected void styleActionButton(Button button) {
        button.setFont(theme().bodyFont);
        button.setBackground(theme().brassDark);
        button.setForeground(theme().parchment);
    }

    protected void styleCombo(org.eclipse.swt.widgets.Combo combo) {
        combo.setBackground(theme().mist);
        combo.setForeground(theme().ink);
        combo.setFont(theme().bodyFont);
    }

    protected void styleSpinner(Spinner spinner) {
        spinner.setBackground(theme().mist);
        spinner.setForeground(theme().ink);
        spinner.setFont(theme().bodyFont);
    }
}
