package pms.whq.data;

import org.w3c.dom.Node;

import pms.whq.util.XMLUtil;

public class SettlementEvent extends Event {

	public SettlementEvent(Node node) {
		id = XMLUtil.getAttribute(node, "id");
	    name = XMLUtil.getAttribute(node, "name");
	    flavor = null;
	    rules = XMLUtil.getChildValue(node, "rules");
	    special = null;
	    
	    treasure = false;
	}

}
