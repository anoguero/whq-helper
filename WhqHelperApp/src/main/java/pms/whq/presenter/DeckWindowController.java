package pms.whq.presenter;

import java.nio.file.Path;
import java.util.function.Consumer;

import pms.whq.content.ContentIssue;
import pms.whq.content.ContentRepository;
import pms.whq.content.RuntimeContentService;
import pms.whq.data.EventList;
import pms.whq.game.DeckBuildResult;
import pms.whq.game.DeckBuilderService;

public class DeckWindowController {

  private final RuntimeContentService contentService;
  private final DeckBuilderService deckBuilderService;

  private ContentRepository contentRepository = new ContentRepository();
  private EventList eventList;
  private EventList travelEventList;
  private EventList settlementEventList;
  private EventList treasureEventList;
  private EventList objectiveTreasureEventList;
  private boolean simulateDeckMode;

  public DeckWindowController(Path projectRoot, boolean simulateDeckMode) {
    this.contentService = new RuntimeContentService(projectRoot);
    this.deckBuilderService = new DeckBuilderService();
    this.simulateDeckMode = simulateDeckMode;
  }

  public void loadContent(Consumer<ContentIssue> issueConsumer) {
    contentRepository = contentService.load(issueConsumer);
    rebuildDecks();
  }

  public void rebuildDecks() {
    DeckBuildResult result = deckBuilderService.build(contentRepository, simulateDeckMode);
    eventList = result.eventList();
    travelEventList = result.travelEventList();
    settlementEventList = result.settlementEventList();
    treasureEventList = result.treasureEventList();
    objectiveTreasureEventList = result.objectiveTreasureEventList();
  }

  public ContentRepository contentRepository() {
    return contentRepository;
  }

  public EventList deck(DeckType deckType) {
    return switch (deckType) {
      case DUNGEON -> eventList;
      case TRAVEL -> travelEventList;
      case SETTLEMENT -> settlementEventList;
      case TREASURE -> treasureEventList;
      case OBJECTIVE_TREASURE -> objectiveTreasureEventList;
    };
  }

  public void setSimulateDeckMode(boolean simulateDeckMode) {
    if (this.simulateDeckMode == simulateDeckMode) {
      return;
    }
    this.simulateDeckMode = simulateDeckMode;
    rebuildDecks();
  }

  public boolean isSimulateDeckMode() {
    return simulateDeckMode;
  }
}
