/*
 * EventList.java
 *
 * Created on September 29, 2005, 2:49 PM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package pms.whq.data;

import java.util.*;

/**
 *
 * @author psiegel
 */
public interface EventList {
  public DrawableEntry getEntry();
  public void addEntry(DrawableEntry entry);
  public void addEntries(Collection<DrawableEntry> entries);
  public int  size();
}
