package pms.whq.swt;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import pms.whq.Settings;
import pms.whq.content.ContentRepository;
import pms.whq.data.Event;
import pms.whq.data.EventEntry;
import pms.whq.data.Monster;
import pms.whq.data.MonsterEntry;

public class CardWindowManager {

  private final Display display;
  private final List<Shell> currentCards = new ArrayList<>();
  private final Map<String, Image> images = new TreeMap<>();

  private Point firstCardLocation;
  private Point nextCardLocation;

  public CardWindowManager(Display display) {
    this.display = display;
  }

  public void showCard(Shell parent, Object entry, ContentRepository contentRepository) {
    if (entry instanceof List<?> nestedList) {
      for (Object nested : nestedList) {
        showCard(parent, nested, contentRepository);
      }
      return;
    }

    Shell cardShell = null;

    if (entry instanceof MonsterEntry monsterEntry) {
      Monster monster = contentRepository.findMonster(monsterEntry.id);
      if (monster == null) {
        SwtDialogs.showWarning(parent, "Monster Not Found", "Unable to find monster: " + monsterEntry.id);
        return;
      }

      int partySize = Settings.getSettingAsInt(Settings.PARTY_SIZE);
      int numAppearing = monsterEntry.getNumber();
      if (numAppearing != 0) {
        numAppearing = Math.max(1, (numAppearing * partySize) / 4);
      }

      cardShell =
          CardFactory.createMonsterCard(
              parent,
              monster,
              getMonsterTitle(monster, numAppearing),
              monsterEntry,
              monsterEntry.appendSpecials,
              contentRepository.rules(),
              getMonsterImage(monster),
              Settings.getSettingAsInt(Settings.CARD_WIDTH),
              Settings.getSettingAsInt(Settings.CARD_HEIGHT));
    } else if (entry instanceof EventEntry eventEntry) {
      Event event = contentRepository.findAnyEvent(eventEntry.id);
      if (event == null) {
        SwtDialogs.showWarning(parent, "Event Not Found", "Unable to find event: " + eventEntry.id);
        return;
      }

      cardShell =
          event.treasure
              ? CardFactory.createTreasureCard(
                  parent,
                  event,
                  Settings.getSettingAsInt(Settings.CARD_WIDTH),
                  Settings.getSettingAsInt(Settings.CARD_HEIGHT))
              : CardFactory.createEventCard(
                  parent,
                  event,
                  Settings.getSettingAsInt(Settings.CARD_WIDTH),
                  Settings.getSettingAsInt(Settings.CARD_HEIGHT));
    } else {
      String type = entry == null ? "null" : entry.getClass().getName();
      SwtDialogs.showWarning(parent, "Unknown Entry Type", "Unknown entry type: " + type);
      return;
    }

    if (cardShell != null) {
      if (nextCardLocation == null) {
        nextCardLocation = new Point(0, 0);
      }

      cardShell.setLocation(nextCardLocation);
      cardShell.open();

      Point size = cardShell.getSize();
      nextCardLocation = new Point(nextCardLocation.x + size.x, nextCardLocation.y);

      Rectangle screen = display.getPrimaryMonitor().getClientArea();
      if ((nextCardLocation.x + size.x) > screen.width) {
        int startX = firstCardLocation == null ? 0 : firstCardLocation.x;
        int startY = firstCardLocation == null ? 0 : firstCardLocation.y;

        nextCardLocation.x = startX;
        nextCardLocation.y += size.y;

        if ((nextCardLocation.y + size.y) > screen.height) {
          nextCardLocation.x = startX + 5;
          nextCardLocation.y = startY + 5;
        }
      }

      currentCards.add(cardShell);
    }
  }

  public void closeAllCards() {
    firstCardLocation = null;

    Iterator<Shell> iterator = currentCards.iterator();
    while (iterator.hasNext()) {
      Shell card = iterator.next();
      if (card != null && !card.isDisposed()) {
        if (firstCardLocation == null) {
          firstCardLocation = card.getLocation();
        }
        card.dispose();
      }
      iterator.remove();
    }

    if (firstCardLocation == null) {
      firstCardLocation = new Point(0, 0);
    }
    nextCardLocation = new Point(firstCardLocation.x, firstCardLocation.y);
  }

  public void resetCascadeStart() {
    if (firstCardLocation == null) {
      firstCardLocation = new Point(0, 0);
    }
    nextCardLocation = new Point(firstCardLocation.x, firstCardLocation.y);
  }

  public void disposeImages() {
    for (Image image : images.values()) {
      if (image != null && !image.isDisposed()) {
        image.dispose();
      }
    }
    images.clear();
  }

  public Image loadImage(String path) {
    if (path == null || path.isBlank()) {
      return null;
    }

    Image cached = images.get(path);
    if (cached != null && !cached.isDisposed()) {
      return cached;
    }

    Image image = null;
    try (InputStream stream = CardWindowManager.class.getResourceAsStream("/" + path)) {
      if (stream != null) {
        image = new Image(display, stream);
      }
    } catch (IOException ioex) {
      // Ignore and fallback to filesystem.
    }

    if (image == null) {
      File file = new File(path);
      if (file.exists()) {
        try {
          image = new Image(display, file.getAbsolutePath());
        } catch (RuntimeException ignored) {
          // Ignore malformed image files.
        }
      }
    }

    if (image != null) {
      images.put(path, image);
    }

    return image;
  }

  private Image getMonsterImage(Monster monster) {
    String monsterImgDir = Settings.getSetting(Settings.MONSTER_IMG_DIR);

    String idName = monster.id == null ? "" : monster.id.toLowerCase().replace(' ', '-');
    Image image = loadImage(monsterImgDir + idName + ".png");
    if (image != null) {
      return image;
    }

    String monsterName = monster.name == null ? "" : monster.name.toLowerCase().replace(' ', '-');
    return loadImage(monsterImgDir + monsterName + ".png");
  }

  private String getMonsterTitle(Monster monster, int number) {
    if (number > 0) {
      if (number > 1) {
        return number + " " + monster.plural;
      }
      return number + " " + monster.name;
    }
    return "* " + monster.name;
  }
}
