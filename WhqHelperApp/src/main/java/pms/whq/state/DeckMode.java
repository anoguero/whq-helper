package pms.whq.state;

public enum DeckMode {
  TABLE(false),
  DECK(true);

  private final boolean simulatedDeck;

  DeckMode(boolean simulatedDeck) {
    this.simulatedDeck = simulatedDeck;
  }

  public boolean isDeck() {
    return simulatedDeck;
  }

  public static DeckMode fromSimulatedDeck(boolean simulatedDeck) {
    return simulatedDeck ? DECK : TABLE;
  }
}
