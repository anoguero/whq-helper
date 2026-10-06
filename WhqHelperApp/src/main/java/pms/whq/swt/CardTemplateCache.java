package pms.whq.swt;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;

/**
 * Plantillas de carta compartidas por Display. Cada plantilla se decodifica una vez y la usan todas
 * las cartas abiertas; se disponen al cerrarse el Display.
 *
 * <p>Las imagenes que devuelve son compartidas: quien las pide no debe disponerlas.
 */
final class CardTemplateCache {

  private static final Map<Display, Map<String, Image>> CACHE = new HashMap<>();

  private CardTemplateCache() {
  }

  /**
   * Devuelve la imagen cacheada para key en ese Display, cargandola con loader la primera vez. Si
   * loader devuelve null no se cachea nada, y se volvera a intentar en la siguiente llamada.
   */
  static synchronized Image get(Display display, String key, Supplier<Image> loader) {
    Map<String, Image> images = CACHE.get(display);
    if (images == null) {
      images = new HashMap<>();
      CACHE.put(display, images);
      display.disposeExec(() -> disposeAll(display));
    }
    Image image = images.get(key);
    if (image == null || image.isDisposed()) {
      image = loader.get();
      if (image == null) {
        images.remove(key);
        return null;
      }
      images.put(key, image);
    }
    return image;
  }

  private static synchronized void disposeAll(Display display) {
    Map<String, Image> images = CACHE.remove(display);
    if (images == null) {
      return;
    }
    for (Image image : images.values()) {
      if (!image.isDisposed()) {
        image.dispose();
      }
    }
  }
}
