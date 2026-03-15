/*
 * Rule.java
 *
 * Created on October 7, 2005, 11:25 PM
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
public class Rule {
  public String     id;
  public String     name;
  public String     text;
  public String     type;
  
  /** Creates a new instance of Rule */
  public Rule() {
  }
  
  public Rule(Node node) {
    type = node.getNodeName();
    id = XMLUtil.getAttribute(node, "id");
    name = XMLUtil.getAttribute(node, "name");
    text = XMLUtil.getText(node);
  }
  
}
