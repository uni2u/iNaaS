package etri.sdn.controller.module.ovsdb;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.oneone.OneToOneEncoder;

public class JSONEncoder extends OneToOneEncoder {

	@Override
	protected Object encode(ChannelHandlerContext ctx, Channel channel, Object msg) throws Exception {
		// TODO Auto-generated method stub
		JSONMsg jsonmsg = (JSONMsg)msg;
		ChannelBuffer buf = ChannelBuffers.buffer(jsonmsg.getLengthU());
		jsonmsg.writeTo(buf);
		return buf;
	}

}
