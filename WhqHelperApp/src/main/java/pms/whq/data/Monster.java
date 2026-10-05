/*
 * Monster.java
 *
 * Created on April 21, 2005, 4:00 PM
 */

package pms.whq.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.w3c.dom.Node;

import pms.whq.util.XMLUtil;

/**
 *
 * @author psiegel
 */
public class Monster extends SpecialContainer {
  public String     id;
  public String     name;
  public String     plural;
  public String     move;
  public String     weaponskill;
  public String     ballisticskill;
  public String     strength;
  public String     toughness;
  public String     wounds;
  public String     initiative;
  public String     attacks;
  public String     gold;
  public String     armor;
  public String     damage;
  public List<String> factions;
  
  /** Creates a new instance of Monster */
  public Monster() {
    super();
  }
  
  public Monster(Node node) {
    super(node);
    
    id = XMLUtil.getAttribute(node, "id");
    name = XMLUtil.getAttribute(node, "name");
    plural = XMLUtil.getAttribute(node, "plural");
    factions = new ArrayList<>();
    
    String facts = XMLUtil.getAttribute(node, "factions");
    if (facts != null) {
    	for (String s : facts.split(" ")) {
    		factions.add(s);
    	}
    }
    move = XMLUtil.getChildValue(node, "move");
    weaponskill = XMLUtil.getChildValue(node, "weaponskill");
    ballisticskill = XMLUtil.getChildValue(node, "ballisticskill");
    strength = XMLUtil.getChildValue(node, "strength");
    toughness = XMLUtil.getChildValue(node, "toughness");
    wounds = XMLUtil.getChildValue(node, "wounds");
    initiative = XMLUtil.getChildValue(node, "initiative");
    attacks = XMLUtil.getChildValue(node, "attacks");
    gold = XMLUtil.getChildValue(node, "gold");
    armor = XMLUtil.getChildValue(node, "armor");
    damage = XMLUtil.getChildValue(node, "damage");
  }  
}
