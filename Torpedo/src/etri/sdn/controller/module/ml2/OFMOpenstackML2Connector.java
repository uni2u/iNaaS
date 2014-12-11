package etri.sdn.controller.module.ml2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;
import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TableId;
import org.projectfloodlight.openflow.types.U64;
import org.projectfloodlight.openflow.util.HexString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import etri.sdn.controller.IService;
import etri.sdn.controller.Main;
import etri.sdn.controller.MessageContext;
import etri.sdn.controller.OFMFilter;
import etri.sdn.controller.OFModel;
import etri.sdn.controller.OFModule;
import etri.sdn.controller.module.devicemanager.IDevice;
import etri.sdn.controller.module.devicemanager.IDeviceListener;
import etri.sdn.controller.module.forwarding.ForwardingBase;
import etri.sdn.controller.module.ml2.RestNetwork.NetworkDefinition;
import etri.sdn.controller.module.ml2.RestPort.PortDefinition;
import etri.sdn.controller.module.ml2.RestSubnet.SubnetDefinition;
import etri.sdn.controller.module.routing.IRoutingDecision;
import etri.sdn.controller.module.routing.RoutingDecision;
import etri.sdn.controller.module.tunnelmanager.OFMTunnelManager;
import etri.sdn.controller.protocol.OFProtocol;
import etri.sdn.controller.protocol.io.Connection;
import etri.sdn.controller.protocol.io.IOFSwitch;
import etri.sdn.controller.protocol.packet.DHCP;
import etri.sdn.controller.protocol.packet.DHCP.DHCPOptionCode;
import etri.sdn.controller.protocol.packet.DHCPOption;
import etri.sdn.controller.protocol.packet.Ethernet;
import etri.sdn.controller.protocol.packet.IPacket;
import etri.sdn.controller.protocol.packet.IPv4;
import etri.sdn.controller.util.AppCookie;
import etri.sdn.controller.util.MACAddress;


public class OFMOpenstackML2Connector extends OFModule implements IOpenstackML2ConnectorService, IDeviceListener {
	
	public static final Logger logger = LoggerFactory.getLogger(OFMOpenstackML2Connector.class);

	private NetworkConfiguration netConf = null;
	@SuppressWarnings("unused")
	private OFProtocol protocol;

	// Our internal state
	protected Map<String, String> netNameToGuid;		// Logical name -> Network ID
	protected Map<String, VirtualNetwork> vNetsByGuid;	// List of all created virtual networks
	protected Map<String, VirtualSubnet> vSubsByGuid;	// List of all created virtual subnets
	protected Map<String, String> subIdToNetId;			// Subnet ID -> Network Id
	protected Map<String, VirtualPort> vPorsByGuid;		// List of all created virtual ports
	protected Map<MACAddress, String> macToGuid; 		// Host MAC -> Network ID
	protected Map<MACAddress, String> macToGateway; 	// Host MAC -> Gateway IP
	protected Map<String, Set<String>> gatewayToGuid; 	// Gateway IP -> Network ID
	protected Map<String, String> guidToGateway; 		// Network ID -> Gateway IP
	protected Map<String, MACAddress> portToMac; 		// Host MAC -> logical port name
	

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
		vPorsByGuid = new ConcurrentHashMap<String, VirtualPort>();
		macToGuid = new ConcurrentHashMap<MACAddress, String>();
		macToGateway = new ConcurrentHashMap<MACAddress, String>();
		gatewayToGuid = new ConcurrentHashMap<String, Set<String>>();
		guidToGateway = new ConcurrentHashMap<String, String>();
		
		registerFilter(
				OFType.PACKET_IN, 
				new OFMFilter() {
					@Override
					public boolean filter(OFMessage m) {
						return true;
					}
				}
		);
		
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
	
	/**
	* Checks whether the frame is destined to or from a gateway.
	* @param frame The ethernet frame to check.
	* @return True if it is to/from a gateway, false otherwise.
	*/
	protected boolean isDefaultGateway(Ethernet frame) {
		if (macToGateway.containsKey(frame.getSourceMAC()))
			return true;
		
		String gwIp = macToGateway.get(frame.getDestinationMAC());
		
		if (gwIp != null) {
			MACAddress host = frame.getSourceMAC();
			String srcNet = macToGuid.get(host);
			
			if (srcNet != null) {
				String gwIpSrcNet = guidToGateway.get(srcNet);
				
				if ((gwIpSrcNet != null) && (gwIp.equals(gwIpSrcNet))) {
//System.out.println("isDefaultGateway = "+gwIpSrcNet);
					return true;
				}
			}
		}
		
		return false;
	}
	
	/**
	* Checks to see if two MAC Addresses are on the same network.
	* @param m1 The first MAC.
	* @param m2 The second MAC.
	* @return True if they are on the same virtual network,
	* false otherwise.
	*/
	protected boolean oneSameNetwork(MACAddress m1, MACAddress m2) {
		String net1 = macToGuid.get(m1);
		String net2 = macToGuid.get(m2);
	
		if (net1 == null)
			return false;
	
		if (net2 == null)
			return false;
	
		return net1.equals(net2);
	}
	
	/**
	* Checks to see if an Ethernet frame is a DHCP packet.
	* @param frame The Ethernet frame.
	* @return True if it is a DHCP frame, false otherwise.
	*/
	protected boolean isDhcpPacket(Ethernet frame) {		
		IPacket payload = frame.getPayload(); // IP
		if (payload == null) {
			return false;
		}
		
		IPacket p2 = payload.getPayload(); // TCP or UDP
		if (p2 == null) {
			return false;
		}
		
		IPacket p3 = p2.getPayload(); // Application
		if ((p3 != null) && (p3 instanceof DHCP)) {
			// Todo...
			// Forwarding udhcpc start!!!!!!!!!!!!!!!!!!!!!!!
//System.out.println("#################### dhcp REPLY = "+DHCP.OPCODE_REPLY);
//System.out.println("#################### dhcp REQUEST = "+DHCP.OPCODE_REQUEST);
//System.out.println("#################### dhcp HW Type = "+DHCP.HWTYPE_ETHERNET);
			return true;
		}
		
		return false;
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
		
		Ethernet eth = (Ethernet) cntx.get(MessageContext.ETHER_PAYLOAD);

		String srcNetwork = macToGuid.get(eth.getSourceMAC());
				
		// If the host is on an unknown network we deny it.
		// We make exceptions for ARP and DHCP.
		OFPort inPort = getInputPort(pi);
		
		IDevice srcDevice = (IDevice) cntx.get(MessageContext.SRC_DEVICE);
		IDevice dstDevice = (IDevice) cntx.get(MessageContext.DST_DEVICE);
		
		
//System.out.println("#################### srcDevice = "+srcDevice);
//System.out.println("#################### dstDevice = "+dstDevice);
				
//		if (eth.isBroadcast() || eth.isMulticast() || isDefaultGateway(eth) == true || isDhcpPacket(eth) == true) {
		if (eth.isBroadcast() == true || eth.isMulticast() == true) {
//			isDefaultGateway(eth);
//			isDhcpPacket(eth);
			
			boolean allowDhcp = true;
			boolean allowDefauleGW = true;
			
			allowDhcp = isDhcpPacket(eth);
			allowDefauleGW = isDefaultGateway(eth);
			
			
			OFFactory fac = OFFactories.getFactory(sw.getVersion());
			OFPacketIn.Builder ofPin = fac.buildPacketIn();
			OFFlowMod.Builder fm = null;
			Match.Builder match = fac.buildMatch();
		
			if (allowDhcp == true) {
				logger.debug("isDhcpPacket is true = {}", pi);				

				return true;

			} else if (allowDhcp == false) {
//				logger.debug("isDhcpPacket is false = {}", pi);
				
				return false;
			}
			
			if (allowDefauleGW == true) {
				logger.debug("isDefaultGateway is true = {}", pi);
				
				decision = new RoutingDecision(
						sw.getId(),
						inPort, 
						(IDevice) cntx.get(MessageContext.SRC_DEVICE),
						IRoutingDecision.RoutingAction.MULTICAST);
				decision.addToContext(cntx);
			} else if (allowDefauleGW == false) {
//				logger.debug("isDefaultGateway is false = {}", pi);
				
				decision = new RoutingDecision(
						sw.getId(),
						inPort, 
						(IDevice) cntx.get(MessageContext.SRC_DEVICE),
						IRoutingDecision.RoutingAction.DROP);
				decision.addToContext(cntx);
			}
			
			
			return true;
		} else if (srcNetwork == null) {
			logger.error("Blocking traffic from host {} because it is not attached to any network.", HexString.toHexString(eth.getSourceMACAddress()));
			return false;
		} else if (oneSameNetwork(eth.getSourceMAC(), eth.getDestinationMAC())) {
			// if they are on the same network continue
			return true;
		}
		
		if (Main.debug)
			logger.debug("Results for flow between {} and {}", new Object[] {eth.getSourceMAC(), eth.getDestinationMAC()});
						
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

	/**
	* Adds a gateway to a virtual network.
	* @param guid The ID (not name) of the network.
	* @param ip The IP addresses of the gateway.
	*/
	protected void addGateway(String guid, String ip) {
		if (ip != null) {
			guidToGateway.put(guid, ip);
			if (gatewayToGuid.containsKey(ip)) {
				Set<String> gSet = gatewayToGuid.get(ip);
				gSet.add(guid);
			} else {
				Set<String> gSet = Collections.synchronizedSet(new HashSet<String>());
				gSet.add(guid);
				gatewayToGuid.put(ip, gSet);
			}
		}
	}
	
	/**
	* Deletes a gateway for a virtual network.
	* @param guid The ID (not name) of the network to delete
	* the gateway for.
	*/
	protected void deleteGateway(String guid) {
		String gwIp = guidToGateway.remove(guid);
		if (gwIp == null) return;
		Set<String> gSet = gatewayToGuid.get(gwIp);
		gSet.remove(guid);
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
			logger.debug("Could not delete network with ID {}, network doesn't exist", netId);
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
		
		Collection<MACAddress> deleteList = new ArrayList<MACAddress>();
		
		for (MACAddress host : macToGuid.keySet()) {
			if (macToGuid.get(host).equals(netId)) {
				deleteList.add(host);
			}
		}
		
		for (MACAddress mac : deleteList) {
			macToGuid.remove(mac);
			for (Entry<String, MACAddress> entry : portToMac.entrySet()) {
				if (entry.getValue().equals(mac)) {
					portToMac.remove(entry.getKey());
					break;
				}
			}
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
		
		if (gatewayIp != null) {
			addGateway(netId, gatewayIp);
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
		
		deleteGateway(netId);
	}

	@Override
	public String listPorts(String porId, String porKey, String porValue) {
		String listStr = "";
		
		try {
			if(!"".equals(porId)) {

				ObjectMapper omp = new ObjectMapper();
				listStr = "{\"port\":" + omp.writeValueAsString(vPorsByGuid.get(porId)) + "}";

			} else {
				int cnt = 0;
				for(Entry<String, VirtualPort> entry : vPorsByGuid.entrySet()) {
					ObjectMapper omm = new ObjectMapper();
					String jsonStr = omm.writeValueAsString(entry.getValue());
					
					if(!"".equals(porKey)) {
						Map<String, Object> vInfo = omm.readValue(jsonStr, new TypeReference<Map<String, Object>>(){});
						
						for(Entry<String, Object> vEntry : vInfo.entrySet()) {
							String vEntryKey = vEntry.getKey() == null ? "null" : vEntry.getKey().toString();
							String vEntryValue = vEntry.getValue() == null ? "null" : vEntry.getValue().toString();
							
							if(porKey.equals(vEntryKey) && porValue.equals(vEntryValue)) {
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
	public void createPort(PortDefinition port) {
		
		String porId = port.porId;
		String binding_host_id = port.binding_host_id;
		List<String> allowed_address_pairs = port.allowed_address_pairs;
		List<String> extra_dhcp_opts = port.extra_dhcp_opts;
		String device_owner = port.device_owner;
		Map<String, String> binding_profile = port.binding_profile;
		List<Map<String, Object>> security_groups = port.security_groups;
		String device_id = port.device_id;
		String porName = port.porName;
		String admin_state_up = port.admin_state_up;
		Map<String, String> binding_vif_details = port.binding_vif_details;
		String binding_vif_detail = port.binding_vif_detail;
		String binding_vnic_type = port.binding_vnic_type;
		String binding_vif_type = port.binding_vif_type;
		String mac_address = port.mac_address;
		
		if(vPorsByGuid.containsKey(porId)) {
			vPorsByGuid.get(porId).setBindingHostId(binding_host_id);				// port already exists, just updating binding:host_id
			vPorsByGuid.get(porId).setAllowedAddressPairs(allowed_address_pairs);	// port already exists, just updating allowed_address_pairs
			vPorsByGuid.get(porId).setExtraDhcpOpts(extra_dhcp_opts);				// port already exists, just updating extra_dhcp_opts
			vPorsByGuid.get(porId).setDeviceOwner(device_owner);					// port already exists, just updating device_owner
			vPorsByGuid.get(porId).setBindingProfile(binding_profile);				// port already exists, just updating binding_profile
			vPorsByGuid.get(porId).setSecurityGroups(security_groups);				// port already exists, just updating security_groups
			vPorsByGuid.get(porId).setDeviceId(device_id);							// port already exists, just updating device_id
			vPorsByGuid.get(porId).setPorName(porName);								// port already exists, just updating name
			vPorsByGuid.get(porId).setAdminStateUp(admin_state_up);					// port already exists, just updating admin_state_up
			vPorsByGuid.get(porId).setBindingVifDetails(binding_vif_details);		// port already exists, just updating binding:vif_details
			vPorsByGuid.get(porId).setBindingVifDetails(binding_vif_detail);
			vPorsByGuid.get(porId).setBindingVnicType(binding_vnic_type);			// port already exists, just updating binding:vnic_type
			vPorsByGuid.get(porId).setBindingVifType(binding_vif_type);				// port already exists, just updating binding:vif_type
			vPorsByGuid.get(porId).setMACAddress(mac_address);
		} else {
			vPorsByGuid.put(porId, new VirtualPort(port));	// create new port
			
			OFMTunnelManager tm = new OFMTunnelManager();
			tm.create_port_flow(port);
		}
		
		if (port.mac_address != null && port.device_owner != null) {
			MACAddress mac = MACAddress.valueOf(mac_address);
			macToGuid.put(mac, porId);
		}
		
	}

	@Override
	public void deletePort(String porId) {
		
		if(vPorsByGuid.get(porId) != null){
			vPorsByGuid.remove(porId);
		}
		
		macToGuid.remove(porId);
		
		OFMTunnelManager tm = new OFMTunnelManager();
		tm.delete_port_flow(porId);
	}
	
	@Override
	public void deviceAdded(IDevice device) {
		// TODO Auto-generated method stub
		if (device.getIPv4Addresses() == null) return;
		for (Integer i : device.getIPv4Addresses()) {
			if (gatewayToGuid.containsKey(i)) {
				MACAddress mac = MACAddress.valueOf(device.getMACAddress());
				
System.out.println("Adding MAC {"+HexString.toHexString(mac.toBytes())+"} with IP {"+IPv4.fromIPv4Address(i)+"} a a gateway");

				macToGateway.put(mac, i.toString());
			}
		}
	}
	
	@Override
	public void deviceRemoved(IDevice device) {
		// TODO Auto-generated method stub
		MACAddress mac = MACAddress.valueOf(device.getMACAddress());
		if (macToGateway.containsKey(mac)) {

System.out.println("Removing MAC {"+HexString.toHexString(mac.toBytes())+"} as a gateway");

			macToGateway.remove(mac);
		}
	}

	@Override
	public void deviceMoved(IDevice device) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void deviceIPV4AddrChanged(IDevice device) {
		// TODO Auto-generated method stub
		deviceAdded(device);
	}

	@Override
	public void deviceVlanChanged(IDevice device) {
		// TODO Auto-generated method stub
		
	}

}
