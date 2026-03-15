package com.whq.app.render;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.swt.graphics.Device;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;

import com.whq.app.model.DungeonCard;

final class DungeonTileRenderer {
    private final Device device;
    private final Path projectRoot;
    private final Map<String, Image> imageCache = new HashMap<>();

    DungeonTileRenderer(Device device, Path projectRoot) {
        this.device = device;
        this.projectRoot = projectRoot;
    }

    void drawTile(GC gc, DungeonCard card, Rectangle tileBox) {
        Image tileImage = loadImage(card.getTileImagePath());
        if (tileImage == null) {
            return;
        }

        Rectangle img = tileImage.getBounds();
        int drawX = tileBox.x + (tileBox.width - img.width) / 2;
        int drawY = tileBox.y + (tileBox.height - img.height) / 2;
        gc.drawImage(tileImage, drawX, drawY);
    }

    void dispose() {
        for (Image image : imageCache.values()) {
            if (image != null && !image.isDisposed()) {
                image.dispose();
            }
        }
        imageCache.clear();
    }

    private Image loadImage(String relativePath) {
        return imageCache.computeIfAbsent(relativePath, key -> {
            Path path = projectRoot.resolve(key).normalize();
            if (!Files.exists(path)) {
                return null;
            }
            return new Image(device, path.toString());
        });
    }
}
