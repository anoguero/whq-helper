package com.whq.app.ui.panel;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Spinner;

import com.whq.app.i18n.I18n;

import pms.whq.Settings;
import com.whq.app.ui.AppIcon;

/** Dialogo de valores por defecto de una nueva mazmorra. */
public final class DungeonDefaultsDialog extends AppPanel {

    public DungeonDefaultsDialog(AppContext context) {
        super(context);
    }

    public void openDungeonDefaultsDialog() {
        Shell dialog = new Shell(shell(), SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
        AppIcon.inherit(dialog, shell());
        dialog.setText(I18n.t("dialog.dungeonDefaults.title"));
        dialog.setBackground(theme().shellBackground);
        dialog.setLayout(new GridLayout(1, false));
        dialog.setSize(620, 430);

        createDialogHeader(
                dialog,
                I18n.t("dialog.dungeonDefaults.title"),
                I18n.t("dialog.dungeonDefaults.hint"));

        Composite formPanel = createDarkPanel(dialog, 2);
        formPanel.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        Label deckSizeLabel = new Label(formPanel, SWT.NONE);
        deckSizeLabel.setText(I18n.t("dialog.dungeonDefaults.deckSize"));
        styleDarkLabel(deckSizeLabel, false);

        Spinner deckSizeSpinner = new Spinner(formPanel, SWT.BORDER);
        styleSpinner(deckSizeSpinner);
        deckSizeSpinner.setMinimum(2);
        deckSizeSpinner.setMaximum(200);
        deckSizeSpinner.setSelection(Math.max(2, Settings.getSettingAsInt(Settings.ADVENTURE_DEFAULT_DECK_SIZE)));
        deckSizeSpinner.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Label roomCountLabel = new Label(formPanel, SWT.NONE);
        roomCountLabel.setText(I18n.t("dialog.dungeonDefaults.roomCount"));
        styleDarkLabel(roomCountLabel, false);

        Spinner roomCountSpinner = new Spinner(formPanel, SWT.BORDER);
        styleSpinner(roomCountSpinner);
        roomCountSpinner.setMinimum(1);
        roomCountSpinner.setMaximum(Math.max(1, deckSizeSpinner.getSelection() - 1));
        roomCountSpinner.setSelection(
                Math.max(
                        1,
                        Math.min(
                                Settings.getSettingAsInt(Settings.ADVENTURE_DEFAULT_ROOM_COUNT),
                                roomCountSpinner.getMaximum())));
        roomCountSpinner.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Label hintLabel = new Label(formPanel, SWT.WRAP);
        hintLabel.setText(I18n.t("dialog.dungeonDefaults.hint"));
        hintLabel.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 2, 1));
        styleDarkLabel(hintLabel, false);

        deckSizeSpinner.addListener(SWT.Modify, event -> {
            int maxRooms = Math.max(1, deckSizeSpinner.getSelection() - 1);
            roomCountSpinner.setMaximum(maxRooms);
            if (roomCountSpinner.getSelection() > maxRooms) {
                roomCountSpinner.setSelection(maxRooms);
            }
        });

        Composite actions = new Composite(formPanel, SWT.NONE);
        actions.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false, 2, 1));
        actions.setBackground(theme().panelBackground);
        GridLayout actionsLayout = new GridLayout(2, true);
        actionsLayout.marginWidth = 0;
        actions.setLayout(actionsLayout);

        Button acceptButton = new Button(actions, SWT.PUSH);
        acceptButton.setText(I18n.t("button.accept"));
        styleActionButton(acceptButton);
        acceptButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        acceptButton.addListener(SWT.Selection, event -> {
            int deckSize = deckSizeSpinner.getSelection();
            int roomCount = roomCountSpinner.getSelection();
            if (deckSize < 2 || roomCount < 1 || roomCount >= deckSize) {
                showError(
                        I18n.t("dialog.dungeonDefaults.invalidTitle"),
                        I18n.t("dialog.dungeonDefaults.invalidMessage"));
                return;
            }

            Settings.setSetting(Settings.ADVENTURE_DEFAULT_DECK_SIZE, Integer.toString(deckSize));
            Settings.setSetting(Settings.ADVENTURE_DEFAULT_ROOM_COUNT, Integer.toString(roomCount));
            Settings.save();
            dialog.close();
        });

        Button cancelButton = new Button(actions, SWT.PUSH);
        cancelButton.setText(I18n.t("button.cancel"));
        styleActionButton(cancelButton);
        cancelButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        cancelButton.addListener(SWT.Selection, event -> dialog.close());

        dialog.open();
        while (!dialog.isDisposed()) {
            if (!display().readAndDispatch()) {
                display().sleep();
            }
        }
    }
}
