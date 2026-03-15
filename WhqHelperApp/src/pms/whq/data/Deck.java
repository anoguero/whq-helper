package pms.whq.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class Deck implements EventList {

  private final List<Object> draw;
  private final List<Object> discard;

  public Deck() {
    draw = new ArrayList<>();
    discard = new ArrayList<>();
  }

  @Override
  public void addEntry(Object entry) {
    draw.add(entry);
  }

  @Override
  public void addEntries(Collection<Object> entries) {
    draw.addAll(entries);
  }

  @Override
  public Object getEntry() {
    if (draw.isEmpty()) {
      shuffle();
    }
    if (draw.isEmpty()) {
      return null;
    }

    Object entry = draw.remove(0);
    discard.add(entry);
    return entry;
  }

  @Override
  public int size() {
    return draw.size() + discard.size();
  }

  public void shuffle() {
    draw.addAll(discard);
    discard.clear();
    Collections.shuffle(draw);
  }
}
