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
  public Object getEntry();
  public void addEntry(Object o);
  public void addEntries(Collection<Object> c);
  public int  size();
}
