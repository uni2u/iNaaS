package etri.sdn.controller.module.ovsdb;

import java.util.Iterator;
import java.util.Map.Entry;

import org.jboss.netty.buffer.ChannelBuffer;

import etri.sdn.controller.Main;


public class JSONSetDpidMsg extends JSONMsg {

	private String setdpidmsg;
	private String dpidstr;
	private OVSDBImpl dsw;
	private int id;
	private String tunnelIP;
	
	public JSONSetDpidMsg(String dpidstr, OVSDBImpl dsw, int messageId) throws OVSDBBridgeUnknown {
		this.dpidstr = dpidstr;
		this.dsw = dsw;
		this.id = messageId;
		this.tunnelIP = null;
		buildSetDpidMsgString();
	}
	
	private void buildSetDpidMsgString() throws OVSDBBridgeUnknown {
		String bridgeuuid = getOvsbr0Bridgeuuid();
		OVSBridge br = dsw.bridge.get(bridgeuuid);
		
		if (br == null ) {
			throw new RuntimeException("tsw.bridge.get("+ bridgeuuid + ")" + " returned Null in setDpid msg");
		}
		
		setdpidmsg = "{\"method\":\"transact\",\"id\":" + id +
				",\"params\":[\"Open_vSwitch\", {\"where\":[[\"_uuid\",\"==\"," +
				"[\"uuid\",\""+ bridgeuuid +"\"]]],\"op\":\"update\",\"table\":" +
				"\"Bridge\",\"row\":{\"other_config\":[\"map\"," +
				"[[\"datapath-id\",\"" + dpidstr + "\"],[\"datapath_type\"," +
				"\"system\"]";
		
		if (hasTunnelIp(bridgeuuid)) {
			setdpidmsg += ",[\"tunnel-ip\",\""+tunnelIP+"\"]]]}},";
		} else {
			setdpidmsg += "]]}},";
		}
		
		setdpidmsg += "{\"comment\":\"ovs-vsctl: ovs-vsctl --no-wait set " +
				"bridge br-tun other-config:datapath-id="+ dpidstr +"\"," +
				"\"op\":\"comment\"}]}"; // br-tun -> ovs-br0
	}
	
	private String getOvsbr0Bridgeuuid() throws OVSDBBridgeUnknown {
		Iterator<Entry<String, OVSBridge>> iter = dsw.bridge.entrySet().iterator();
		
		while (iter.hasNext()) {
			Entry<String, OVSBridge> e = iter.next();
			String bruuid = e.getKey();
			OVSBridge br = e.getValue();
			if (br.getNew().getName().equals("br-tun")) { // br-tun -> ovs-br0
				return bruuid;
			}
		}
		return null;
	}
	
	private boolean hasTunnelIp(String bridgeuuid) {
		tunnelIP = dsw.bridge.get(bridgeuuid).getNew().getTunnelIPAddress();
		if (tunnelIP == null) return false;
		return true;
	}
	
	@Override
	public void writeTo(ChannelBuffer buf) {
		if (Main.debug) {
			OFMOVSDBManager.logger.debug("sent set-dpid message (id:{}) to sw@: {} ", id, dsw.getMgmtIPAddr());
		}
		buf.writeBytes(setdpidmsg.getBytes());
	}
	
	@Override
	public int getLengthU() {
		return setdpidmsg.length();
	}
}
