/*
 * Event.java
 *
 * Created on September 28, 2005, 9:26 AM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package pms.whq.data;

import org.w3c.dom.*;

import pms.whq.util.XMLUtil;

/**
 *
 * @author psiegel
 */
public class Event {
  public String     id;
  public String     name;
  public String     flavor;
  public String     rules;
  public String     special;
  public String     goldValue;
  public String     users;
  public boolean    treasure;
  
  /** Creates a new instance of Event */
  public Event() {
  }
  
  public Event(Node node) {
    id = XMLUtil.getAttribute(node, "id");
    name = XMLUtil.getAttribute(node, "name");
    flavor = XMLUtil.getChildValue(node, "flavor");
    rules = XMLUtil.getChildValue(node, "rules");
    special = XMLUtil.getChildValue(node, "special");
    goldValue = XMLUtil.getChildValue(node, "goldValue");
    users = XMLUtil.getChildValue(node, "users");
    
    treasure = true;
    String hasTreasure = XMLUtil.getChildValue(node, "treasure");
    if (hasTreasure.length() > 0) {
      treasure = Boolean.parseBoolean(hasTreasure);
    }    
  }
}
