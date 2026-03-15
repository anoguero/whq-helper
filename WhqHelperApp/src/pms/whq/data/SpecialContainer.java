/*
 * SpecialContainer.java
 *
 * Created on November 1, 2005, 10:32 AM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package pms.whq.data;


import java.util.HashMap;
import java.util.Map;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import pms.whq.util.XMLUtil;

/**
 *
 * @author psiegel
 */
public abstract class SpecialContainer {
  
  public String     special;
  public Map<String, String>       specialLinks;
  public String     magicType;
  public int        magicLevel;
    
  /** Creates a new instance of SpecialContainer */
  public SpecialContainer() {
    specialLinks = new HashMap<>();
  }
  
  public SpecialContainer(Node node) {
    this();

    Node links = XMLUtil.getNamedChild(node, "special");
    if (links != null) {
      NodeList nodeList = links.getChildNodes();      
      for (int i=0;i<nodeList.getLength();i++) {
        Node specialNode = nodeList.item(i);
        String type = specialNode.getNodeName();
        
        if ("rule".equals(type)) {
          String id = XMLUtil.getAttribute(specialNode, "id");
          String text = XMLUtil.getText(specialNode);

          if ((id.length() > 0) && (text.length() > 0)) {
            specialLinks.put(id, text);
          }
        } else if ("magic".equals(type)) {
          magicType = XMLUtil.getAttribute(specialNode, "id");
          try {
            magicLevel = Integer.parseInt(XMLUtil.getAttribute(specialNode, "level"));
          } catch (NumberFormatException nfe) {
            magicLevel = 0;
          }
        } else if ("text".equals(type)) {
          special = XMLUtil.getText(specialNode);
        } 
      }
    }
  }
    
  public boolean hasSpecialText() {
    return (special != null) && (special.length() > 0);
  }
  
  public boolean hasSpecialLinks() {
    return specialLinks.size() > 0;
  }
  
  public boolean hasMagic() {
    return (magicType != null) && (magicType.length() > 0);
  }
  
  public boolean hasAnySpecial() {
    return hasSpecialText() || hasSpecialLinks() || hasMagic();
  }
}
