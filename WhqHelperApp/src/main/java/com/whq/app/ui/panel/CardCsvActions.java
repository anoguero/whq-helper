package com.whq.app.ui.panel;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.FileDialog;

import com.whq.app.io.CardCsvService;
import com.whq.app.model.DungeonCard;
import com.whq.app.storage.DungeonCardStorageException;

/** Importar y exportar cartas de mazmorra en CSV. */
public final class CardCsvActions extends AppPanel {

    private final CardCsvService csvService;

    public CardCsvActions(AppContext context) {
        super(context);
        this.csvService = new CardCsvService();
    }

    public void handleImportCsv() {
        FileDialog dialog = new FileDialog(shell(), SWT.OPEN);
        dialog.setText("Importar cartas desde CSV");
        dialog.setFilterExtensions(new String[] {"*.csv", "*.*"});
        dialog.setFilterNames(new String[] {"CSV files", "All files"});

        String selectedPath = dialog.open();
        if (selectedPath == null) {
            return;
        }

        try {
            List<DungeonCard> importedCards = csvService.importFromCsv(Path.of(selectedPath));
            cardStore().insertCards(importedCards);
            refreshCards();
            showInfo("Importación completada", importedCards.size() + " cartas importadas correctamente.");
        } catch (IOException | DungeonCardStorageException | IllegalArgumentException ex) {
            showError("Error al importar CSV", ex.getMessage());
        }
    }

    public void handleExportCsv(List<DungeonCard> cardsToExport) {
        if (cardsToExport == null || cardsToExport.isEmpty()) {
            showInfo("Exportación", "No hay cartas para exportar.");
            return;
        }

        FileDialog dialog = new FileDialog(shell(), SWT.SAVE);
        dialog.setText("Exportar cartas a CSV");
        dialog.setFileName("dungeon-cards.csv");
        dialog.setFilterExtensions(new String[] {"*.csv", "*.*"});
        dialog.setFilterNames(new String[] {"CSV files", "All files"});

        String selectedPath = dialog.open();
        if (selectedPath == null) {
            return;
        }

        try {
            csvService.exportToCsv(Path.of(selectedPath), cardsToExport);
            showInfo("Exportación completada", cardsToExport.size() + " cartas exportadas.");
        } catch (IOException ex) {
            showError("Error al exportar CSV", ex.getMessage());
        }
    }

    public void handleExportSelectedEnvironment() {
        if (selected() == null) {
            showInfo("Exportación", "Selecciona una carta para exportar su grupo de entorno.");
            return;
        }

        String environment = selected().getEnvironment();
        List<DungeonCard> environmentGroup = cards().stream()
                .filter(card -> environment.equalsIgnoreCase(card.getEnvironment()))
                .collect(Collectors.toList());

        handleExportCsv(environmentGroup);
    }
}
