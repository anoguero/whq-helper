package com.whq.app.ui;

import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

public final class AppIcon {
    private static final String ICON_PATH = "resources/logo.png";

    private static Display iconDisplay;
    private static Image iconImage;

    private AppIcon() {
    }

    public static void apply(Shell shell, Path projectRoot) {
        if (shell == null || shell.isDisposed() || projectRoot == null) {
            return;
        }

        Image image = load(shell.getDisplay(), projectRoot);
        if (image != null) {
            shell.setImage(image);
        }
    }

    public static void inherit(Shell shell, Shell parent) {
        if (shell == null || shell.isDisposed() || parent == null || parent.isDisposed()) {
            return;
        }

        Image[] images = parent.getImages();
        if (images != null && images.length > 0) {
            shell.setImages(images);
            return;
        }

        Image image = parent.getImage();
        if (image != null && !image.isDisposed()) {
            shell.setImage(image);
        }
    }

    private static Image load(Display display, Path projectRoot) {
        if (display == null || projectRoot == null) {
            return null;
        }

        if (iconImage != null && !iconImage.isDisposed() && iconDisplay == display) {
            return iconImage;
        }

        Path iconPath = projectRoot.resolve(ICON_PATH).normalize();
        if (!Files.isRegularFile(iconPath)) {
            return null;
        }

        if (iconImage != null && !iconImage.isDisposed()) {
            iconImage.dispose();
        }

        try {
            iconImage = new Image(display, iconPath.toString());
            iconDisplay = display;
            return iconImage;
        } catch (RuntimeException ex) {
            iconImage = null;
            iconDisplay = null;
            return null;
        }
    }
}
