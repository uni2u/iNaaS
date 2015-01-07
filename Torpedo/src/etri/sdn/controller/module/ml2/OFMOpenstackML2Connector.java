package etri.sdn.controller.module.ml2;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.OFPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import etri.sdn.controller.IService;
import etri.sdn.controller.Main;
import etri.sdn.controller.MessageContext;
import etri.sdn.controller.OFModel;
import etri.sdn.controller.OFModule;
import etri.sdn.controller.module.ml2.RestNetwork.NetworkDefinition;
import etri.sdn.controller.module.ml2.RestPort.PortDefinition;
import etri.sdn.controller.module.ml2.RestSubnet.SubnetDefinition;
import etri.sdn.controller.module.routing.IRoutingDecision;
import etri.sdn.controller.module.tunnelmanager.OFMTunnelManager;
import etri.sdn.controller.protocol.OFProtocol;
import etri.sdn.controller.protocol.io.Connection;
import etri.sdn.controller.protocol.io.IOFSwitch;


public class OFMOpenstackML2Connector extends OFModule implements IOpenstackML2ConnectorService {
	
	public static final Logger logger = LoggerFactory.getLogger(OFMOpenstackML2Connector.class);

	private NetworkConfiguration netConf = null;
	@SuppressWarnings("unused")
	private OFProtocol protocol;

	// Our internal state
	protected Map<String, String> netNameToGuid;		// Logical name -> Network ID
	protected Map<String, VirtualNetwork> vNetsByGuid;	// List of all created virtual networks
	protected Map<String, VirtualSubnet> vSubsByGuid;	// List of all created virtual subnets
	protected Map<String, String> subIdToNetId;			// Subnet ID -> Network Id
	protected Map<String, VirtualPort> vPortsByGuid;		// List of all created virtual ports
	

	@Override
	protected Collection<Class<? extends IService>> services() {
		List<Class<? extends IService>> ret = new LinkedList<>();
		ret.add( IOpenstackML2ConnectorService.class);
		return ret;
	}

	public OFMOpenstackML2Connector() {
		this.netConf = new NetworkConfiguration(this);
	}

	@Override
	protected void initialize() {
		// because this module does not receive any message from the Openflow layer,
		// there's nothing to do here.

		netNameToGuid = new ConcurrentHashMap<String, String>();
		vNetsByGuid = new ConcurrentHashMap<String, VirtualNetwork>();
		vSubsByGuid = new ConcurrentHashMap<String, VirtualSubnet>();
		subIdToNetId = new ConcurrentHashMap<String, String>();
		vPortsByGuid = new ConcurrentHashMap<String, VirtualPort>();
		
	}

	@Override
	protected boolean handleHandshakedEvent(Connection conn, MessageContext context) {
		return true;
	}

	@Override
	protected boolean handleMessage(Connection conn, MessageContext context, OFMessage msg, List<OFMessage> outgoing) {
		
		switch (msg.getType()) {
		case PACKET_IN:
			IRoutingDecision decision = null;
						
			if(context != null) {
				decision = (IRoutingDecision) context.get(MessageContext.ROUTING_DECISION);		
				return this.processPacketIn(conn.getSwitch(), (OFPacketIn) msg, decision, context);
			}
			break;

		default:
			break;
		}
		
		return true;
	}	
	
	protected OFPort getInputPort(OFPacketIn pi) {
		if ( pi == null ) {
			throw new AssertionError("pi cannot refer null");
		}
		try {
			return pi.getInPort();
		} catch ( UnsupportedOperationException e ) {
			return pi.getMatch().get(MatchField.IN_PORT);
		}
	}
	
	private boolean processPacketIn(IOFSwitch sw, OFPacketIn pi, IRoutingDecision decision, MessageContext cntx) {

		return true;
	}

	@Override
	protected boolean handleDisconnect(Connection conn) {
		return true;
	}

	@Override
	public OFModel[] getModels() {
		return new OFModel[] { this.netConf };
	}
	
	@Override
	public String getVxlanIDtoNetwork(String vnid) {
		String vkey = "provider:segmentation_id";
		String vNetId = "";
		try {
			
			for (Entry<String, VirtualNetwork> entry : vNetsByGuid.entrySet()) {
				ObjectMapper omm = new ObjectMapper();
				String vNets = omm.writeValueAsString(entry.getValue());

				Map<String, Object> vInfo = omm.readValue(vNets, new TypeReference<Map<String, Object>>() {});
				for (Entry<String, Object> vEntry : vInfo.entrySet()) {
					String vEntryKey = vEntry.getKey() == null ? "null" : vEntry.getKey().toString();
					String vEntryValue = vEntry.getValue() == null ? "null" : vEntry.getValue().toString();								
					if (vkey.equals(vEntryKey) && vnid.equals(vEntryValue)) {
						vNetId = vInfo.get("id").toString();	
					}
				}
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return vNetId;
	}
	
	@Override
	public String getVxlanIDtoTenant(String vnid) {
		
		String vkey = "provider:segmentation_id";
		String vTenantId = "";
		try {
			
			for (Entry<String, VirtualNetwork> entry : vNetsByGuid.entrySet()) {
				ObjectMapper omm = new ObjectMapper();
				String vNets = omm.writeValueAsString(entry.getValue());

				Map<String, Object> vInfo = omm.readValue(vNets, new TypeReference<Map<String, Object>>() {});
				for (Entry<String, Object> vEntry : vInfo.entrySet()) {
					String vEntryKey = vEntry.getKey() == null ? "null" : vEntry.getKey().toString();
					String vEntryValue = vEntry.getValue() == null ? "null" : vEntry.getValue().toString();								
					if (vkey.equals(vEntryKey) && vnid.equals(vEntryValue)) {
						vTenantId = vInfo.get("tenant_id").toString();	
					}
				}
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return vTenantId;
	}
	
	@Override
	public String listNetworks(String netId, String netKey, String netValue) {
		String listStr = "";

		try {
			if(!"".equals(netId)) {

				ObjectMapper oms = new ObjectMapper();
				listStr = "{\"network\":" + oms.writeValueAsString(vNetsByGuid.get(netId)) + "}";

			} else {
				int cnt = 0;
				for(Entry<String, VirtualNetwork> entry : vNetsByGuid.entrySet()) {
					ObjectMapper omm = new ObjectMapper();
					String jsonStr = omm.writeValueAsString(entry.getValue());
					
					if(!"".equals(netKey)) {
						Map<String, Object> vInfo = omm.readValue(jsonStr, new TypeReference<Map<String, Object>>(){});
						
						for(Entry<String, Object> vEntry : vInfo.entrySet()) {
							String vEntryKey = vEntry.getKey() == null ? "null" : vEntry.getKey().toString();
							String vEntryValue = vEntry.getValue() == null ? "null" : vEntry.getValue().toString();
							
							if(netKey.equals(vEntryKey) && netValue.equals(vEntryValue)) {
								if(cnt == 0) {
									listStr = jsonStr;
								} else {
									listStr += "," + jsonStr;
								}
								cnt++;
							}
						}
					} else {
						if(cnt == 0) {
							listStr = jsonStr;
						} else {
							listStr += "," + jsonStr;
						}
						cnt++;
					}
				}
				
				if(cnt > 0) {
					listStr = "{\"networks\":[" + listStr + "]}";
				}
			}

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return listStr;
	}

	@Override
	public void createNetwork(NetworkDefinition network) {
		String netId = network.netId;
		String netName = network.netName;
		String provider_physical_network = network.provider_physical_network;
		String admin_state_up = network.admin_state_up;
		String provider_network_type = network.provider_network_type;
		String router_external = network.router_external;
		String shared = network.shared;
		String provider_segmentation_id = network.provider_segmentation_id;

		if(!netNameToGuid.isEmpty()) {
			// We have to iterate all the networks to handle name/gateway changes
			for(Entry<String, String> entry : netNameToGuid.entrySet()) {
				if(entry.getValue().equals(netId)) {
					netNameToGuid.remove(entry.getKey());
					break;
				}
			}
		}

		if(netName != null) {
			netNameToGuid.put(netName, netId);
		}

		if(vNetsByGuid.containsKey(netId)) {
			vNetsByGuid.get(netId).setNetName(netName);										//network already exists, just updating name
			vNetsByGuid.get(netId).setProviderPhysicalNetwork(provider_physical_network);	//network already exists, just updating provider:physical_network
			vNetsByGuid.get(netId).setAdminStateUp(admin_state_up);							//network already exists, just updating admin_state_up
			vNetsByGuid.get(netId).setProviderNetworkType(provider_network_type);			//network already exists, just updating provider:network_type
			vNetsByGuid.get(netId).setRouterExternal(router_external);						//network already exists, just updating router:external
			vNetsByGuid.get(netId).setShared(shared);										//network already exists, just updating shared
			vNetsByGuid.get(netId).setProviderSegmentationId(provider_segmentation_id);		//network already exists, just updating provider:segmentation_id
		} else {
			vNetsByGuid.put(netId, new VirtualNetwork(network)); //new network
			
			OFMTunnelManager tm = new OFMTunnelManager();
			tm.create_network_flow(network);
		}

	}

	@Override
	public void deleteNetwork(String netId) {
		String netName = null;

		if(netNameToGuid.isEmpty()) {
			logger.debug("_Could not delete network with ID {}, network doesn't exist", netId);
			return;
		}

		for(Entry<String, String> entry : netNameToGuid.entrySet()) {
			if (entry.getValue().equals(netId)) {
				netName = entry.getKey();
				break;
			}
			logger.debug("Could not delete network with ID {}, network doesn't exist", netId);
		}

		if(Main.debug) {
			logger.debug("Deleting network with name {} ID {}", netName, netId);
		}

		netNameToGuid.remove(netName);

		if(vNetsByGuid.get(netId) != null){
			vNetsByGuid.remove(netId);
		}
		
		OFMTunnelManager tm = new OFMTunnelManager();
		tm.delete_network_flow(netId);
	}

	@Override
	public String listSubnets(String subId, String subKey, String subValue) {
		String listStr = "";

		try {
			if(!"".equals(subId)) {

				ObjectMapper oms = new ObjectMapper();
				listStr = "{\"subnet\":" + oms.writeValueAsString(vSubsByGuid.get(subId)) + "}";

			} else {
				int cnt = 0;
				for(Entry<String, VirtualSubnet> entry : vSubsByGuid.entrySet()) {
					ObjectMapper omm = new ObjectMapper();
					String jsonStr = omm.writeValueAsString(entry.getValue());
					
					if(!"".equals(subKey)) {
						Map<String, Object> vInfo = omm.readValue(jsonStr, new TypeReference<Map<String, Object>>(){});
						
						for(Entry<String, Object> vEntry : vInfo.entrySet()) {
							String vEntryKey = vEntry.getKey() == null ? "null" : vEntry.getKey().toString();
							String vEntryValue = vEntry.getValue() == null ? "null" : vEntry.getValue().toString();
							
							if(subKey.equals(vEntryKey) && subValue.equals(vEntryValue)) {
								if(cnt == 0) {
									listStr = jsonStr;
								} else {
									listStr += "," + jsonStr;
								}
								cnt++;
							}
						}
					} else {
						if(cnt == 0) {
							listStr = jsonStr;
						} else {
							listStr += "," + jsonStr;
						}
						cnt++;
					}
				}
				
				if(cnt > 0) {
					listStr = "{\"subnets\":[" + listStr + "]}";
				}
			}

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return listStr;
	}

	@Override
	public void createSubnet(SubnetDefinition subnet) {
		String subId = subnet.subId;
		String subName = subnet.subName;
		String netId = subnet.network_id;
		String enableDhcp = subnet.enable_dhcp;
		String gatewayIp = subnet.gateway_ip;
		String shared = subnet.shared;
		List<String> dnsNameservers = subnet.dns_nameservers;
		List<String> hostRoutes = subnet.host_routes;
		String ipv6_ra_mode = subnet.ipv6_ra_mode;
		String ipv6_address_mode = subnet.ipv6_address_mode;

		if(!subIdToNetId.isEmpty()) {
			for(Entry<String, String> entry : subIdToNetId.entrySet()) {
				if(entry.getKey().equals(subId)) {
					if(netId == null) {
						netId = entry.getValue();
					}
					subIdToNetId.remove(entry.getKey());
					break;
				}
			}
		}

		if(subId != null) {
			subIdToNetId.put(subId, netId);
		}

		if(vSubsByGuid.containsKey(subId)) {
			vSubsByGuid.get(subId).setSubName(subName);						// subnet already exists, just updating name
			vSubsByGuid.get(subId).setEnableDhcp(enableDhcp);				// subnet already exists, just updating enable_dhcp
			vSubsByGuid.get(subId).setGatewayIp(gatewayIp);					// subnet already exists, just updating gateway_ip
			vSubsByGuid.get(subId).setShared(shared);						// subnet already exists, just updating shared
			vSubsByGuid.get(subId).setDnsNameservers(dnsNameservers);		// subnet already exists, just updating dns_nameservers
			vSubsByGuid.get(subId).setHostRoutes(hostRoutes);				// subnet already exists, just updating host_routes
			vSubsByGuid.get(subId).setIpv6RaMode(ipv6_ra_mode);				// subnet already exists, just updating ipv6_ra_mode
			vSubsByGuid.get(subId).setIpv6AddressMode(ipv6_address_mode);	// subnet already exists, just updating ipv6_address_mode
		} else {
			vSubsByGuid.put(subId, new VirtualSubnet(subnet));	// create new subnet
		}

		if(vNetsByGuid.containsKey(netId)) {
			vNetsByGuid.get(netId).addSubnets(subId, subName);	// network subnets add
		}
	}

	@Override
	public void deleteSubnet(String subId) {
		String netId = null;

		if(subIdToNetId.isEmpty()) {
			logger.debug("Could not delete subnet with ID {}, subnet doesn't exist", subId);
			return;
		}

		for(Entry<String, String> entry : subIdToNetId.entrySet()) {
			if (entry.getKey().equals(subId)) {
				netId = entry.getValue();
				break;
			}
			logger.debug("Could not delete subnet with ID {}, subnet doesn't exist", subId);
		}

		subIdToNetId.remove(subId);

		if(vSubsByGuid.get(subId) != null){
			vSubsByGuid.remove(subId);
		}

		if(vNetsByGuid.containsKey(netId)) {
			vNetsByGuid.get(netId).delSubnets(subId); // network subnets delete
		}
		
	}

	@Override
	public String listPorts(String portId, String portKey, String portValue) {
		String listStr = "";
		
		try {
			if(!"".equals(portId)) {

				ObjectMapper omp = new ObjectMapper();
				listStr = "{\"port\":" + omp.writeValueAsString(vPortsByGuid.get(portId)) + "}";

			} else {
				int cnt = 0;
				for(Entry<String, VirtualPort> entry : vPortsByGuid.entrySet()) {
					ObjectMapper omm = new ObjectMapper();
					String jsonStr = omm.writeValueAsString(entry.getValue() == null ? "" : entry.getValue());
					
					if(!"".equals(portKey)) {
						Map<String, Object> vInfo = omm.readValue(jsonStr, new TypeReference<Map<String, Object>>(){});
						
						for(Entry<String, Object> vEntry : vInfo.entrySet()) {
							String vEntryKey = vEntry.getKey() == null ? "null" : vEntry.getKey().toString();
							String vEntryValue = vEntry.getValue() == null ? "null" : vEntry.getValue().toString();
							
							if(portKey.equals(vEntryKey) && portValue.equals(vEntryValue)) {
								if(cnt == 0) {
									listStr = jsonStr;
								} else {
									listStr += "," + jsonStr;
								}
								cnt++;
							}
						}
					} else {
						if(cnt == 0) {
							listStr = jsonStr;
						} else {
							listStr += "," + jsonStr;
						}
						cnt++;
					}
				}
				
				if(cnt > 0) {
					listStr = "{\"ports\":[" + listStr + "]}";
				}
			}

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return listStr;
	}

	@Override
	public void createPort(PortDefinition port, String actionType) {
		
		String portId = port.portId;
		String binding_host_id = port.binding_host_id;
		List<String> allowed_address_pairs = port.allowed_address_pairs;
		List<String> extra_dhcp_opts = port.extra_dhcp_opts;
		String device_owner = port.device_owner;
		Map<String, String> binding_profile = port.binding_profile;
		List<Map<String, Object>> security_groups = port.security_groups;
		String device_id = port.device_id;
		String portName = port.portName;
		String admin_state_up = port.admin_state_up;
		Map<String, String> binding_vif_details = port.binding_vif_details;
		String binding_vnic_type = port.binding_vnic_type;
		String binding_vif_type = port.binding_vif_type;
		
		if(vPortsByGuid.containsKey(portId)) {
			vPortsByGuid.get(portId).setBindingHostId(binding_host_id);				// port already exists, just updating binding:host_id
			vPortsByGuid.get(portId).setAllowedAddressPairs(allowed_address_pairs);	// port already exists, just updating allowed_address_pairs
			vPortsByGuid.get(portId).setExtraDhcpOpts(extra_dhcp_opts);				// port already exists, just updating extra_dhcp_opts
			vPortsByGuid.get(portId).setDeviceOwner(device_owner);					// port already exists, just updating device_owner
			vPortsByGuid.get(portId).setBindingProfile(binding_profile);				// port already exists, just updating binding_profile
			vPortsByGuid.get(portId).setSecurityGroups(security_groups);				// port already exists, just updating security_groups
			vPortsByGuid.get(portId).setDeviceId(device_id);							// port already exists, just updating device_id
			vPortsByGuid.get(portId).setPorName(portName);								// port already exists, just updating name
			vPortsByGuid.get(portId).setAdminStateUp(admin_state_up);					// port already exists, just updating admin_state_up
			vPortsByGuid.get(portId).setBindingVifDetails(binding_vif_details);		// port already exists, just updating binding:vif_details
			vPortsByGuid.get(portId).setBindingVnicType(binding_vnic_type);			// port already exists, just updating binding:vnic_type
			vPortsByGuid.get(portId).setBindingVifType(binding_vif_type);				// port already exists, just updating binding:vif_type

		} else {
			vPortsByGuid.put(portId, new VirtualPort(port));	// create new port

			if("create".equals(actionType)) {
				OFMTunnelManager tm = new OFMTunnelManager();
				tm.create_port_flow(port);
			}
			
		}
		
	}

	@Override
	public void deletePort(String portId) {
		
		if(vPortsByGuid.get(portId) != null){
			vPortsByGuid.remove(portId);
		}
		
		OFMTunnelManager tm = new OFMTunnelManager();
		tm.delete_port_flow(portId);
	}
}
