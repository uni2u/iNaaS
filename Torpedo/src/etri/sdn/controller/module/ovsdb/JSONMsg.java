package etri.sdn.controller.module.ovsdb;

import org.jboss.netty.buffer.ChannelBuffer;


public class JSONMsg {

	protected int length = 0;
	String jsonstr = " ";
	
	public int getLengthU() {
		return length;
	}
	
	public void writeTo(ChannelBuffer buf) {
		buf.writeBytes(jsonstr.getBytes());
	}
}
