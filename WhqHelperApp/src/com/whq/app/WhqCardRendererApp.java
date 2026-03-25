package com.whq.app;

import java.nio.file.Path;

import org.eclipse.swt.widgets.Display;

import com.whq.app.ui.AppWindow;

public class WhqCardRendererApp {
    public static void main(String[] args) {
        Path projectRoot = AppPaths.resolveAppHome();
        Display display = new Display();

        try {
            AppWindow window = new AppWindow(display, projectRoot);
            window.open();

            while (!window.getShell().isDisposed()) {
                if (!display.readAndDispatch()) {
                    display.sleep();
                }
            }
        } finally {
            display.dispose();
        }
    }
}
