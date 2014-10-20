package etri.sdn.controller.module.ovsdb;

import java.io.IOException;
import java.net.ConnectException;
import java.nio.channels.ClosedChannelException;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.exc.UnrecognizedPropertyException;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.projectfloodlight.openflow.util.HexString;

import etri.sdn.controller.Main;
import etri.sdn.controller.module.ovsdb.JSONShowReplyMsg.ShowResult;
import etri.sdn.controller.util.Logger;


public class JSONMsgHandler extends SimpleChannelUpstreamHandler {

	private IOVSDB tsw;
	private Object statusObject;
	
	private static final int SHOW_REPLY = 0;
	private static final int ADD_PORT_REPLY = 1;
	private static final int DEL_PORT_REPLY = 2;
	private static final int SET_DPID_REPLY = 3;
	private static final int SET_CIP_REPLY = 4;
	private static int MSG = -1;
	
	public JSONMsgHandler(IOVSDB tsw, Object statusObject) {
		this.tsw = tsw;
		this.statusObject = statusObject;
	}
	
	@Override
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
		JsonNode jn = (JsonNode)e.getMessage();
		
		if(Main.debug) {
			Logger.debug("receveid message: {}", e.toString());
		}
		
		if (jn.get("id") == null) return;
		
		if (jn.get("id").isNumber()) {
			if(Main.debug) {
				Logger.debug("got result for id: {}", jn.get("id").getIntValue());
			}
			
			MSG = tsw.getExpectedMessage(jn.get("id").getIntValue());
			
			switch (MSG) {
				case SHOW_REPLY:
					handleShowReply(jn);
					synchronized(statusObject) {
						statusObject.notify();
					}
					break;
				case ADD_PORT_REPLY:
					handleAddPortReply(jn);
					synchronized(statusObject) {
						statusObject.notify();
					}
					break;
				case DEL_PORT_REPLY:
					handleDelPortReply(jn);
					synchronized(statusObject) {
						statusObject.notify();
					}
					break;
				case SET_DPID_REPLY:
					//noop
					break;
				case SET_CIP_REPLY:
					//noop
					break; //FIXME check for errors
				default :
					//noop
					Logger.error("Unexpected Message Reply id {}", jn.get("id").getIntValue());
			}
		} else {
			if (jn.get("method") == null) return;
			// handle JSON RPC notifications
			if (jn.get("id").getTextValue().equals("null") &&	jn.get("method").getTextValue().equals("update")) {
				// got an update message
				Logger.debug("GOT an UPDATE");

				handleUpdateNotification(jn);
				
				synchronized(statusObject) {
					statusObject.notify();
				}
			} else if (jn.get("id").getTextValue().equals("echo") && jn.get("method").getTextValue().equals("echo")) {
				// no op
			}
		}
	}
	
	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
		if (e.getCause() instanceof IllegalStateException) {
			// hiding exception logging - expected because of the way we do
			// JSON message decoding
			// logger.debug("Illegal State exception ",
			// e.getCause().toString());
		} else if (e.getCause() instanceof UnrecognizedPropertyException) {
			Logger.error("Jackson unrecognized property error {}",
			e.getCause());
		} else if (e.getCause() instanceof JsonMappingException) {
			Logger.error("Jackson mapping error {}",
			e.getCause());
		} else if (e.getCause() instanceof JsonParseException) {
			Logger.error("Jackson parsing error {}",
			e.getCause());
		} else if (e.getCause() instanceof ClosedChannelException) {
			Logger.error("Netty closed channel error", e.getCause());
		} else if (e.getCause() instanceof ConnectException) {
			Logger.error("Connection refused", e.getCause());
		} else if (e.getCause() instanceof IOException) {
			Logger.error("IO problem", e.getCause());
		} else {
			super.exceptionCaught(ctx, e);
		}
	}
	
	private void handleAddPortReply(JsonNode jn) throws Exception {
		ObjectMapper mapper = new ObjectMapper();
		JSONAddPortReplyMsg rep = mapper.treeToValue(jn, JSONAddPortReplyMsg.class);
		//just check for errors
		String returned = rep.getResult().toString();
		
		if (returned.contains("error")) {
			Logger.error("ovsdb-server at sw {} returned error {}", HexString.toHexString(tsw.getDpid()), returned.substring(returned.indexOf("error")));
		}
	}
	
	private void handleDelPortReply(JsonNode jn) throws Exception {
		ObjectMapper mapper = new ObjectMapper();
		JSONDelPortReplyMsg rep = mapper.treeToValue(jn, JSONDelPortReplyMsg.class);
		//just check for errors
		String returned = rep.getResult().toString();
		
		if (returned.contains("error")) {
			Logger.error("ovsdb-server at sw {} returned error {}", HexString.toHexString(tsw.getDpid()), returned.substring(returned.indexOf("error")));
		}
	}
	
	private void handleUpdateNotification(JsonNode jn) throws Exception {
		ObjectMapper mapper = new ObjectMapper();
		JSONUpdateMsg rep = mapper.treeToValue(jn, JSONUpdateMsg.class);
		ShowResult res = rep.getParams().get(1);
		tsw.updateTunnelSwitchFromUpdate(res);
		//debugUpdateOrShow(res);
	}
	
	private void handleShowReply(JsonNode jn) throws Exception {
		ObjectMapper mapper = new ObjectMapper();
		JSONShowReplyMsg rep = mapper.treeToValue(jn, JSONShowReplyMsg.class);
		tsw.updateTunnelSwitchFromShow(rep);
		//debugUpdateOrShow(rep.getResult());
	}
	
	public void debugUpdateOrShow(ShowResult sr) {
		if (Main.debug) {
			if (sr.getOpen_vSwitch() != null) {
				Logger.debug("DB UPDATE: " + sr.getOpen_vSwitch().toString());
			}
			if (sr.getController() != null) {
				Logger.debug("CNTL UPDATE: " + sr.getController().toString());
			}
			if (sr.getInterface() != null) {
				Logger.debug("INTF UPDATE: " + sr.getInterface().toString());
			}
			if (sr.getPort() != null) {
				Logger.debug("PORT UPDATE: " + sr.getPort().toString());
			}
			if (sr.getBridge() != null) {
				Logger.debug("BRIDGE UPDATE: " + sr.getBridge().toString());
			}
		}
	}
}
