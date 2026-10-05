package pms.whq.content;

import java.util.Map;
import java.util.TreeMap;

import pms.whq.data.Event;
import pms.whq.data.Monster;
import pms.whq.data.Rule;
import pms.whq.data.SettlementEvent;
import pms.whq.data.Table;
import pms.whq.data.TravelEvent;

public class ContentRepository {

  private final Map<String, Monster> monsters = new TreeMap<>();
  private final Map<String, Event> events = new TreeMap<>();
  private final Map<String, Table> tables = new TreeMap<>();
  private final Map<String, Rule> rules = new TreeMap<>();
  private final Map<String, TravelEvent> travelEvents = new TreeMap<>();
  private final Map<String, SettlementEvent> settlementEvents = new TreeMap<>();

  public Map<String, Monster> monsters() {
    return monsters;
  }

  public Map<String, Event> events() {
    return events;
  }

  public Map<String, Table> tables() {
    return tables;
  }

  public Map<String, Rule> rules() {
    return rules;
  }

  public Map<String, TravelEvent> travelEvents() {
    return travelEvents;
  }

  public Map<String, SettlementEvent> settlementEvents() {
    return settlementEvents;
  }

  public Monster findMonster(String id) {
    return monsters.get(id);
  }

  public Event findEvent(String id) {
    return events.get(id);
  }

  public TravelEvent findTravelEvent(String id) {
    return travelEvents.get(id);
  }

  public SettlementEvent findSettlementEvent(String id) {
    return settlementEvents.get(id);
  }

  public Event findAnyEvent(String id) {
    Event event = findEvent(id);
    if (event != null) {
      return event;
    }

    TravelEvent travelEvent = findTravelEvent(id);
    if (travelEvent != null) {
      return travelEvent;
    }

    return findSettlementEvent(id);
  }

  public boolean containsDungeonEvent(String id) {
    return events.containsKey(id);
  }
}
