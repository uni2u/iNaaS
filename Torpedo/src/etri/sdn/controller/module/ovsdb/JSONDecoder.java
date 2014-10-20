package etri.sdn.controller.module.ovsdb;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.map.MappingJsonFactory;
import org.codehaus.jackson.map.ObjectMapper;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.frame.FrameDecoder;

public class JSONDecoder extends FrameDecoder {

	// remainderBytes is used to save any bytes left over from
	// the previous frame, since Jackson will aggressively read
	// ahead in the buffer
	byte[] remainderBytes = null;
	ObjectMapper mapper = new ObjectMapper();
	MappingJsonFactory mjf = new MappingJsonFactory(mapper);
	int ctr = -1;
	
	@Override
	protected Object decode(ChannelHandlerContext chc, Channel channel, ChannelBuffer cb) throws Exception {
		// TODO Auto-generated method stub
		ctr++;
		cb.markReaderIndex();
		ChannelBuffer compositeBuffer = cb;
		
		if (remainderBytes != null) {
			ChannelBuffer remainderBuffer = ChannelBuffers.wrappedBuffer(remainderBytes);
			compositeBuffer = ChannelBuffers.wrappedBuffer(remainderBuffer, cb);
			remainderBytes = null;
		}
		
		ChannelBufferInputStream cbis = new ChannelBufferInputStream(compositeBuffer);
		JsonParser jp = mjf.createJsonParser(cbis);
		JsonNode node = null;
		
		try {
			node = jp.readValueAsTree();
		} catch (EOFException e) {
			return null;
		} catch (JsonProcessingException e) {
			// This is pretty inefficient but seems to be the only way
			// to deal with this.
			if (e.getMessage().contains("end-of-input")) {
				cb.resetReaderIndex();
				return null;
			} else {
				throw e;
			}
		}
		
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		jp.releaseBuffered(os);
		
		if (os.size() > 0) {
			remainderBytes = os.toByteArray();
		}
		return node;
	}

}
