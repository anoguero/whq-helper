package pms.whq.data;

import java.util.ArrayList;

public final class MonsterGroup extends ArrayList<DrawableEntry> implements DrawableEntry {

  private static final long serialVersionUID = 1L;

  public int level;

  public MonsterGroup() {
    super();
    level = 1;
  }
}
