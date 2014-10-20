package etri.sdn.controller.module.ovsdb;

import javax.net.ssl.SSLEngine;

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.ssl.SslHandler;

public class OVSDBClientPipelineFactory implements ChannelPipelineFactory {

	private IOVSDB currtsw;
	private Object statusObject;
	private boolean useSSL;
	
	public void setCurSwitch(IOVSDB tsw) {
		currtsw = tsw;
	}
	
	public void setUseSSL(boolean useSSL) {
		this.useSSL = useSSL;
	}
	
	@Override
	public ChannelPipeline getPipeline() throws Exception {
		JSONDecoder jsonRpcDecoder = new JSONDecoder();
		JSONEncoder jsonRpcEncoder = new JSONEncoder();
		ChannelPipeline pipeline = Channels.pipeline();
	
		if (useSSL) {
			// Add SSL handler first to encrypt and decrypt everything.
			SSLEngine engine = BSNSslContextFactory.getClientContext().createSSLEngine();
			engine.setUseClientMode(true);
			// OVSDB supports *only* TLSv1
			engine.setEnabledProtocols(new String[] { "TLSv1" } );
			pipeline.addLast("ssl", new SslHandler(engine));
		}
		
		pipeline.addLast("jsondecoder", jsonRpcDecoder);
		pipeline.addLast("jsonencoder", jsonRpcEncoder);
		pipeline.addLast("jsonhandler", new JSONMsgHandler(currtsw, statusObject));
	
		return pipeline;
	}
	
	public void setStatusObject(Object statusObject) {
		this.statusObject = statusObject;
	}
}
