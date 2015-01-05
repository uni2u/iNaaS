package etri.sdn.controller.module.vxlanflowmapper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.TransportPort;

import etri.sdn.controller.util.MACAddress;

public class OverlayFlowMappingEntry<K,V> extends HashMap<K,V> {

	
	HashSet keySet;
	
	public OverlayFlowMappingEntry() {
		keySet = new HashSet();
		keySet.add("outerSrcMac");
		keySet.add("outerDstMac");
		keySet.add("outerSrcIP");
		keySet.add("outerDstIP");
		keySet.add("outerSrcPort");
		keySet.add("innerSrcMac");
		keySet.add("innerDstMac");	
		keySet.add("innerEtherType");
		keySet.add("innerSrcIP");
		keySet.add("innerDstIP");	
		keySet.add("vnid");
		keySet.add("V2OKey");
	}
	
	
	@Override
	public V put (K key, V value) {
		if (keySet.contains(key)) {    	
			super.put(key, value);		
			return value;
		} else return null;
		
    }
	@Override
	public String toString() {
		StringBuffer ret = new StringBuffer();
				
		ret.append("outerSrcMac=" + super.get("outerSrcMac") + " ");
		ret.append("outerDstMac=" + super.get("outerDstMac") + " ");
		ret.append("outerSrcIP=" + super.get("outerSrcIP") + " ");
		ret.append("outerDstIP=" + super.get("outerDstIP") + " ");
		ret.append("outerSrcPort=" + super.get("outerSrcPort") + " ");
		ret.append("innerSrcMac=" + super.get("innerSrcMac") + " ");
		ret.append("innerDstMac=" + super.get("innerDstMac") + " ");
		ret.append("innerEtherType=" + super.get("innerEtherType") + " ");
		ret.append("innerSrcIP=" + super.get("innerSrcIP") + " ");
		ret.append("innerDstIP=" + super.get("innerDstIP") + " ");
		ret.append("vnid=" + super.get("vnid") + " ");
		ret.append("V2OKey=" + super.get("V2OKey") + " ");
		
		
		return ret.toString();
		
	}
}
