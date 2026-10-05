package com.whq.app.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Widget;

import com.whq.app.i18n.I18n;

final class LocalizedUiAction {
    private final String textKey;
    private final Runnable handler;
    private final BooleanSupplier enabledSupplier;
    private final BooleanSupplier selectedSupplier;
    private final List<Widget> widgets = new ArrayList<>();

    LocalizedUiAction(
            String textKey,
            Runnable handler,
            BooleanSupplier enabledSupplier,
            BooleanSupplier selectedSupplier) {
        this.textKey = textKey;
        this.handler = handler;
        this.enabledSupplier = enabledSupplier == null ? () -> true : enabledSupplier;
        this.selectedSupplier = selectedSupplier;
    }

    static LocalizedUiAction push(String textKey, Runnable handler) {
        return new LocalizedUiAction(textKey, handler, () -> true, null);
    }

    static LocalizedUiAction push(String textKey, Runnable handler, BooleanSupplier enabledSupplier) {
        return new LocalizedUiAction(textKey, handler, enabledSupplier, null);
    }

    static LocalizedUiAction radio(String textKey, Runnable handler, BooleanSupplier selectedSupplier) {
        return new LocalizedUiAction(textKey, handler, () -> true, selectedSupplier);
    }

    void bind(MenuItem item) {
        widgets.add(item);
        item.addListener(SWT.Selection, event -> {
            if (isToggle(item) && !item.getSelection()) {
                return;
            }
            handler.run();
        });
        refresh();
    }

    void bind(Button button) {
        widgets.add(button);
        button.addListener(SWT.Selection, event -> {
            if (isToggle(button) && !button.getSelection()) {
                return;
            }
            handler.run();
        });
        refresh();
    }

    void refresh() {
        boolean enabled = enabledSupplier.getAsBoolean();
        boolean selected = selectedSupplier != null && selectedSupplier.getAsBoolean();
        String text = I18n.t(textKey);
        for (Widget widget : widgets) {
            if (widget == null || widget.isDisposed()) {
                continue;
            }
            if (widget instanceof MenuItem item) {
                item.setText(text);
                item.setEnabled(enabled);
                if (selectedSupplier != null && isToggle(item)) {
                    item.setSelection(selected);
                }
            } else if (widget instanceof Button button) {
                button.setText(text);
                button.setEnabled(enabled);
                if (selectedSupplier != null && isToggle(button)) {
                    button.setSelection(selected);
                }
            }
        }
    }

    private boolean isToggle(MenuItem item) {
        return (item.getStyle() & (SWT.RADIO | SWT.CHECK)) != 0;
    }

    private boolean isToggle(Button button) {
        return (button.getStyle() & (SWT.RADIO | SWT.CHECK)) != 0;
    }
}
