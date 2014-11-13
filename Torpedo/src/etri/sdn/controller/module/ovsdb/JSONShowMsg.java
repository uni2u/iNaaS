package etri.sdn.controller.module.ovsdb;

import org.jboss.netty.buffer.ChannelBuffer;

import etri.sdn.controller.Main;


public class JSONShowMsg extends JSONMsg {

	String showstr;
	
	public JSONShowMsg(int id) {
		showstr = " {\"method\":\"monitor\",\"id\":"+id+",\"params\":[ "+
			" \"Open_vSwitch\", "+
			" null, "+
			" {\"Port\":{\"columns\":[\"interfaces\",\"name\",\"tag\",\"trunks\"]},"+
			" \"Controller\":{\"columns\":[\"is_connected\",\"target\"]}, "+
			" \"Interface\":{\"columns\":[\"name\",\"options\",\"type\"]}, "+
			" \"Open_vSwitch\":{\"columns\":[\"bridges\",\"cur_cfg\"," +
			"\"manager_options\",\"ovs_version\"]}, "+
			" \"Manager\":{\"columns\":[\"is_connected\",\"target\"]}, "+
			" \"Bridge\":{\"columns\":[\"controller\",\"fail_mode\",\"name\"," +
			"\"ports\",\"datapath_id\",\"other_config\"]}} "+
			" ] "+
			" } ";
	}
		
	@Override
	public int getLengthU() {
		return showstr.length();
	}
		
	@Override
	public void writeTo(ChannelBuffer buf) {
		if (Main.debug) {
			OFMOVSDBManager.logger.debug("sent show message");
		}
		buf.writeBytes(showstr.getBytes());
	}
}
