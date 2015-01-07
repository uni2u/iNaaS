package etri.sdn.controller.module.tunnelmanager;

import java.io.IOException;
import java.util.Map;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.Restlet;
import org.restlet.data.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RestTunnel extends Restlet {
	public static final Logger logger = LoggerFactory.getLogger(RestTunnel.class);

	static String nodeInfo = "/wm/tunnel/nodeInfo";
	
	private TunnelConfiguration parent = null;

	RestTunnel(TunnelConfiguration parent) {
		this.parent = parent;
	}

	TunnelConfiguration getModel() {
		return this.parent;
	}
	
	@Override
	public void handle(Request request, Response response) {

		Method m = request.getMethod();
		
		if (m == Method.POST) {
			
			try {
				
				ObjectMapper om = new ObjectMapper();
				Map<String, Object> tInfo = om.readValue(request.getEntityAsText(), new TypeReference<Map<String, Object>>(){});

				String node_ip_mgt = tInfo.get("node_ip_mgt").toString();
				String node_ip_tun = tInfo.get("node_ip_tun").toString();
				String node_name = tInfo.get("node_name").toString();
				String node_type = tInfo.get("node_type").toString();
				String iris_ip = tInfo.get("iris_ip").toString();
				
				parent.getModule().addTunnel(node_ip_mgt, node_ip_tun, node_name, node_type, iris_ip);
				
			} catch (IOException e) {
				logger.error("Could not parse JSON {}", e.getMessage());
			}

		}
	}
}