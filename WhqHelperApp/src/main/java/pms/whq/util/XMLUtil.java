/*
 * XMLUtil.java
 *
 * Created on September 23, 2005, 3:56 PM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package pms.whq.util;

import org.w3c.dom.*;

/**
 *
 * @author psiegel
 */
public class XMLUtil {
  
  /** Creates a new instance of XMLUtil */
  private XMLUtil() {
  }
  
  public static String getAttribute(Node node, String name) {
    if (node == null || name == null || name.isEmpty()) {
      return "";
    }

    String value = "";
    
    NamedNodeMap attributes = node.getAttributes();
    if (attributes == null) {
      return value;
    }

    Node attribute = attributes.getNamedItem(name);
    if (attribute != null) {
      value = attribute.getNodeValue();
    }
    
    return value;
  }
  
  public static String getText(Node node) {
    if (node == null) {
      return "";
    }

    String value = "";
    
    NodeList children = node.getChildNodes();
    for (int k=0;k<children.getLength();k++) {
      Node child = children.item(k);
      short type = child.getNodeType();
      if (type == Node.TEXT_NODE || type == Node.CDATA_SECTION_NODE) {
        value += child.getNodeValue();
      }
    }
    
    return value.trim();
  }
  
  public static Node getNamedChild(Node node, String name) {
    if (node == null || name == null || name.isEmpty()) {
      return null;
    }

    Node retVal = null;
    
    NodeList children = node.getChildNodes();
    for (int i=0;i<children.getLength();i++) {
      Node child = children.item(i);
      if (name.equals(child.getNodeName())) {
        retVal = child;
        break;
      }
    }
    
    return retVal;
  }    
  
  public static String getChildValue(Node node, String childName) {
    String value = "";

    Node child = getNamedChild(node, childName);
    if (child != null) {
      value = getText(child);
    }
    
    return value;
  }
  
  public static void addChildValue(Document doc, Node parent, String childName, 
                                   String childValue) {
    Element element = doc.createElement(childName);
    Text text = doc.createTextNode(childValue);
    element.appendChild(text);
    parent.appendChild(element);
  }
  
}
