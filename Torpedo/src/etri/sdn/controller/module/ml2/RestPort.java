package etri.sdn.controller.module.ml2;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.Restlet;
import org.restlet.data.MediaType;
import org.restlet.data.Method;
import org.restlet.data.Status;


public class RestPort extends Restlet {

	static String neutronPortAll = "/wm/ml2/ports";
	static String neutronPort = "/wm/ml2/ports/{portUUID}";
	static String neutronPortIRIS = "/wm/ml2/ports/{portKey}/{portValue}";

	private NetworkConfiguration parent = null;

	RestPort(NetworkConfiguration parent) {
		this.parent = parent;
	}

	NetworkConfiguration getModel() {
		return this.parent;
	}

	public class PortDefinition {
		public String binding_host_id = null;
		public List<String> allowed_address_pairs = null;
		public List<String> extra_dhcp_opts = null;
		public String device_owner = null;
		public Map<String, String> binding_profile = null;
		public List<Map<String, String>> fixed_ips = null;
		public String portId = null;
		public List<Map<String, Object>> security_groups = null;
		public String device_id = null;
		public String portName = null;
		public String admin_state_up = null;
		public String network_id = null;
		public String tenant_id = null;
		public Map<String, String> binding_vif_details = null;
		public String binding_vnic_type = null;
		public String binding_vif_type = null;
		public String mac_address = null;
	}

	protected void jsonToPortDefinition(String json, PortDefinition port) throws IOException {
		// convert json to map
		ObjectMapper om = new ObjectMapper();
		Map<String, Object> pframe = om.readValue(json, new TypeReference<Map<String, Object>>(){});

		ObjectMapper omm = new ObjectMapper();
		String pInfoJson = omm.writeValueAsString(pframe.get("port"));
		Map<String, Object> pInfo = omm.readValue(pInfoJson, new TypeReference<Map<String, Object>>(){});

		if(pInfo.get("binding:host_id") != null) {
			port.binding_host_id = pInfo.get("binding:host_id").toString();
		}
		if(pInfo.get("allowed_address_pairs") != null) {
			ObjectMapper omaap = new ObjectMapper();
			port.allowed_address_pairs = omaap.readValue(omaap.writeValueAsString(pInfo.get("allowed_address_pairs")), new TypeReference<List<String>>(){});
		}
		if(pInfo.get("extra_dhcp_opts") != null) {
			ObjectMapper omedo = new ObjectMapper();
			port.extra_dhcp_opts = omedo.readValue(omedo.writeValueAsString(pInfo.get("extra_dhcp_opts")), new TypeReference<List<String>>(){});
		}
		if(pInfo.get("device_owner") != null) {
			port.device_owner = pInfo.get("device_owner").toString();
		}
		if(pInfo.get("binding:profile") != null) {
			ObjectMapper ombp = new ObjectMapper();
			port.binding_profile = ombp.readValue(ombp.writeValueAsString(pInfo.get("binding:profile")), new TypeReference<Map<String, String>>(){});
		}
		if(pInfo.get("fixed_ips") != null) {
			ObjectMapper omfi = new ObjectMapper();
			port.fixed_ips = omfi.readValue(omfi.writeValueAsString(pInfo.get("fixed_ips")), new TypeReference<List<Map<String, String>>>(){});
		}
		if(pInfo.get("id") != null) {
			port.portId = pInfo.get("id").toString();
		}
		if(pInfo.get("security_groups") != null) {
			ObjectMapper omedo = new ObjectMapper();
			port.security_groups = omedo.readValue(omedo.writeValueAsString(pInfo.get("security_groups")), new TypeReference<List<Map<String, Object>>>(){});
		}
		if(pInfo.get("device_id") != null) {
			port.device_id = pInfo.get("device_id").toString();
		}
		if(pInfo.get("name") != null) {
			port.portName = pInfo.get("name").toString();
		}
		if(pInfo.get("admin_state_up") != null) {
			port.admin_state_up = pInfo.get("admin_state_up").toString();
		}
		if(pInfo.get("network_id") != null) {
			port.network_id = pInfo.get("network_id").toString();
		}
		if(pInfo.get("tenant_id") != null) {
			port.tenant_id = pInfo.get("tenant_id").toString();
		}
		if(pInfo.get("mac_address") != null) {
			port.mac_address = pInfo.get("mac_address").toString();
		}
		if(pInfo.get("binding:vif_details") != null) {
			ObjectMapper ombp = new ObjectMapper();
			port.binding_vif_details = ombp.readValue(ombp.writeValueAsString(pInfo.get("binding:vif_details")), new TypeReference<Map<String, String>>(){});
		}
		if(pInfo.get("binding:vnic_type") != null) {
			port.binding_vnic_type = pInfo.get("binding:vnic_type").toString();
		}
		if(pInfo.get("binding:vif_type") != null) {
			port.binding_vif_type = pInfo.get("binding:vif_type").toString();
		}
	}

	@Override
	public void handle(Request request, Response response) {

		Method m = request.getMethod();
		String portUUID = request.getAttributes().get("portUUID") == null ? "" : (String) request.getAttributes().get("portUUID");

		if (m == Method.POST || m == Method.PUT) {
			PortDefinition port = new PortDefinition();
			
			String actionType = "";
			if("".equals(portUUID)) {
				actionType = "create";
			} else {
				actionType = "update";
			}

			try {
				jsonToPortDefinition(request.getEntityAsText(), port);
			} catch (IOException e) {
				OFMOpenstackML2Connector.logger.error("RestPort Could not parse JSON {}", e.getMessage());
			}

			// We try to get the ID from the URI only if it's not
			// in the POST data
			if (port.portId == null) {
				if(!"".equals(portUUID)) {
					port.portId = portUUID;
				}
			}

			parent.getModule().createPort(port, actionType);
			response.setStatus(Status.SUCCESS_OK);

		} else if (m == Method.GET) {
			
			String portKey = request.getAttributes().get("portKey") == null ? "" : (String) request.getAttributes().get("portKey");
			String portValue = request.getAttributes().get("portValue") == null ? "" : (String) request.getAttributes().get("portValue");
			
			response.setEntity(parent.getModule().listPorts(portUUID, portKey, portValue), MediaType.APPLICATION_JSON);
			response.setStatus(Status.SUCCESS_OK);
			
		} else if (m == Method.DELETE) {
			
			parent.getModule().deletePort(portUUID);
			response.setStatus(Status.SUCCESS_OK);
			
		}
	}

}
