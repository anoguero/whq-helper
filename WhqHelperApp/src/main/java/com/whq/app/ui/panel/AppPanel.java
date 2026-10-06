package com.whq.app.ui.panel;

import java.nio.file.Path;
import java.util.List;

import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
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
}
