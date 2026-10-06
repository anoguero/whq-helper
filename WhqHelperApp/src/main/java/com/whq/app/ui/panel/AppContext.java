package com.whq.app.ui.panel;

import java.nio.file.Path;
import java.util.List;

import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import com.whq.app.model.DungeonCard;
import com.whq.app.render.CardRenderer;
import com.whq.app.storage.DungeonCardStore;
import com.whq.app.ui.WhqUiTheme;

import pms.whq.EventDeckApp;

/**
 * Lo que las zonas de la ventana principal comparten con ella. Cada accesor devuelve el valor
 * actual: el tema y el renderizador se crean en open() y las cartas se recargan, asi que las zonas
 * no deben quedarse con una copia.
 */
public interface AppContext {

    Display display();

    Shell shell();

    Path projectRoot();

    WhqUiTheme theme();

    CardRenderer renderer();

    List<DungeonCard> cards();

    DungeonCardStore cardStore();

    /** El panel de mazos de eventos, creandolo si hace falta. */
    EventDeckApp eventDeckApp();

    void showError(String title, String message);

    void showInfo(String title, String message);

    /** Recarga las cartas de mazmorra y la lista de la ventana. */
    void refreshCards();
}
