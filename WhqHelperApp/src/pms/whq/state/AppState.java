package pms.whq.state;

import com.whq.app.i18n.Language;

import pms.whq.Settings;

public record AppState(
    DeckMode deckMode,
    boolean showEventDeck,
    boolean showSettlementDeck,
    boolean showTravelDeck,
    boolean showTreasureDeck,
    boolean showObjectiveTreasureDeck,
    Language language,
    AdventureAmbience adventureAmbience) {

  public static AppState loadFromSettings() {
    return new AppState(
        DeckMode.fromSimulatedDeck(Settings.getSettingAsBool(Settings.SIMULATE_DECK)),
        Settings.getSettingAsBool(Settings.SHOW_EVENT_DECK),
        Settings.getSettingAsBool(Settings.SHOW_SETTLEMENT_DECK),
        Settings.getSettingAsBool(Settings.SHOW_TRAVEL_DECK),
        Settings.getSettingAsBool(Settings.SHOW_TREASURE_DECK),
        Settings.getSettingAsBool(Settings.SHOW_OBJECTIVE_TREASURE_DECK),
        Settings.getLanguage(),
        AdventureAmbience.fromStorageValue(Settings.getSetting(Settings.ADVENTURE_AMBIENCE)));
  }

  public void persistToSettings() {
    Settings.setSetting(Settings.SIMULATE_DECK, Boolean.toString(deckMode.isDeck()));
    Settings.setSetting(Settings.SHOW_EVENT_DECK, Boolean.toString(showEventDeck));
    Settings.setSetting(Settings.SHOW_SETTLEMENT_DECK, Boolean.toString(showSettlementDeck));
    Settings.setSetting(Settings.SHOW_TRAVEL_DECK, Boolean.toString(showTravelDeck));
    Settings.setSetting(Settings.SHOW_TREASURE_DECK, Boolean.toString(showTreasureDeck));
    Settings.setSetting(Settings.SHOW_OBJECTIVE_TREASURE_DECK, Boolean.toString(showObjectiveTreasureDeck));
    Settings.setLanguage(language);
    Settings.setSetting(
        Settings.ADVENTURE_AMBIENCE,
        adventureAmbience == null ? AdventureAmbience.GENERIC.storageValue() : adventureAmbience.storageValue());
  }
}
