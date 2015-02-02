package etri.iris.agent;

import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;
import org.restlet.Context;
import org.restlet.data.MediaType;
import org.restlet.data.Method;
import org.restlet.representation.StringRepresentation;
import org.restlet.resource.ClientResource;

import etri.iris.agent.tunnelmanager.TunnelManagerAgent;
import etri.iris.agent.util.PropertiesUtil;

public class Main {
	private static Logger logger = Logger.getLogger(Main.class);
	private static String loggerPath = System.getProperty("user.dir") + "/log4j.properties";

	public static void main(String[] args) {
		PropertyConfigurator.configure(loggerPath);

		try {
String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(System.currentTimeMillis()));
logger.debug("==================================================");
logger.debug("===== IRIS-agent start : " + now + " =====");
logger.debug("==================================================");
			PropertiesUtil pu = new PropertiesUtil();
			
			// rest server start
			RESTApiServer rest_api_server = new RESTApiServer();
			rest_api_server.start(Integer.parseInt(pu.getProp("node_rest_port")));
			
//			// setup ovsdb server ptcp port
//			TunnelManagerAgent tma = new TunnelManagerAgent();
//			tma.setup_ovsdb_tcp();
			
			// br-int, br-tun create
			TunnelManagerAgent tma = new TunnelManagerAgent();
			tma.create_bridge();
//			tma.setup_integration_br();
//			tma.setup_tunnel_br();
			
			// timmer start
			callRest cr = new callRest();
			Timer tm = new Timer();
			int repeatTime = Integer.parseInt(pu.getProp("repeat_time")) * 1000;
			tm.scheduleAtFixedRate(cr, 1000, repeatTime);
			
		} catch(Exception e) {
			e.printStackTrace();
			return;
		}
	}
}

class callRest extends TimerTask {
	public void run() {
		ClientResource resource = null;
		StringRepresentation stringRep = null;
		
		try {
			PropertiesUtil pu = new PropertiesUtil();
			
			String restUri = "http://"+pu.getProp("controller_ip") + ":" + pu.getProp("controller_rest_port") + "/wm/tunnel/nodeInfo";

			Context context = new Context();
			context.getParameters().add("socketTimeout", "1000");
			context.getParameters().add("idleTimeout", "1000");
			
			resource = new ClientResource(context, restUri);
			resource.setMethod(Method.POST);
			resource.getReference().addQueryParameter("format", "json");
			
			ObjectMapper om = new ObjectMapper();
			ObjectNode on = om.createObjectNode();
			on.put("node_ip_mgt", pu.getProp("node_ip_mgt").toString());
			on.put("node_ip_tun", pu.getProp("node_ip_tun").toString());
			on.put("node_name", InetAddress.getLocalHost().getHostName());
			on.put("node_type", pu.getProp("node_type").toString());
			on.put("iris_ip", pu.getProp("controller_ip").toString());
			
			stringRep = new StringRepresentation(on.toString());
			stringRep.setMediaType(MediaType.APPLICATION_JSON);
			
//			resource.post(stringRep).write(System.out);
			resource.post(stringRep);
		} catch(Exception e) {
			e.printStackTrace();
		} finally {
			stringRep.release();
			resource.release();
		}
	}
}