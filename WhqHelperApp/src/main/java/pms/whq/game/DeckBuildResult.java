package pms.whq.game;

import pms.whq.data.EventList;

public record DeckBuildResult(
    EventList eventList,
    EventList travelEventList,
    EventList settlementEventList,
    EventList treasureEventList,
    EventList objectiveTreasureEventList) {
}
