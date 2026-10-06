package pms.whq.swt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.whq.app.RealContent;

import pms.whq.Settings;
import pms.whq.data.Event;

/** Necesita un servidor grafico: sin el (p. ej. en CI) los tests se saltan. */
class CardTemplateCacheTest {

  private Display display;

  @BeforeEach
  void createDisplay() {
    try {
      display = new Display();
    } catch (Throwable unavailable) {
      Assumptions.abort("Sin servidor grafico para SWT: " + unavailable);
    }
  }

  @AfterEach
  void disposeDisplay() {
    if (display != null && !display.isDisposed()) {
      display.dispose();
    }
  }

  @Test
  void loadsEachTemplateOncePerDisplay() {
    AtomicInteger loads = new AtomicInteger();
    Image first = CardTemplateCache.get(display, "k", () -> {
      loads.incrementAndGet();
      return new Image(display, 4, 4);
    });
    Image second = CardTemplateCache.get(display, "k", () -> {
      loads.incrementAndGet();
      return new Image(display, 4, 4);
    });

    assertSame(first, second);
    assertEquals(1, loads.get());
  }

  @Test
  void disposesTheTemplatesWhenTheDisplayIsDisposed() {
    Image image = CardTemplateCache.get(display, "k", () -> new Image(display, 4, 4));

    display.dispose();

    assertTrue(image.isDisposed());
  }

  @Test
  void doesNotCacheAMissingTemplate() {
    AtomicInteger loads = new AtomicInteger();
    assertNull(CardTemplateCache.get(display, "missing", () -> {
      loads.incrementAndGet();
      return null;
    }));
    assertNull(CardTemplateCache.get(display, "missing", () -> {
      loads.incrementAndGet();
      return null;
    }));

    assertEquals(2, loads.get());
  }

  @Test
  void closingACardKeepsTheSharedTemplateForTheOthers() {
    RealContent.assumeAvailable();
    // Settings es global y otros tests lo dejan apuntando a directorios temporales: las plantillas
    // se resuelven desde el shared home del proyecto.
    Settings.load(Path.of("").toAbsolutePath());
    Shell shell = new Shell(display);
    Composite first = CardFactory.createTreasureCardPreview(shell, new Event());
    Composite second = CardFactory.createTreasureCardPreview(shell, new Event());
    String key = CardFactory.templateKey("resources/treasure-card-template.png");
    Image shared = CardTemplateCache.get(display, key, () -> fail("la plantilla deberia estar cacheada"));
    assertNotNull(shared);

    first.dispose();

    assertFalse(shared.isDisposed());
    assertSame(shared, CardTemplateCache.get(display, key, () -> fail("la plantilla deberia seguir cacheada")));
    second.dispose();
    assertFalse(shared.isDisposed());
    shell.dispose();
  }
}
