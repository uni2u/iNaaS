package etri.sdn.controller.module.tunnelmanager;


import java.net.InetAddress;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;
import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.util.HexString;
import org.restlet.Context;
import org.restlet.data.MediaType;
import org.restlet.data.Method;
import org.restlet.representation.StringRepresentation;
import org.restlet.resource.ClientResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import etri.sdn.controller.IOFTask;
import etri.sdn.controller.IService;
import etri.sdn.controller.MessageContext;
import etri.sdn.controller.OFMFilter;
import etri.sdn.controller.OFModel;
import etri.sdn.controller.OFModule;
import etri.sdn.controller.TorpedoProperties;
import etri.sdn.controller.module.ml2.IOpenstackML2ConnectorService;
import etri.sdn.controller.module.ml2.PortDefinition;
import etri.sdn.controller.module.ml2.RestNetwork.NetworkDefinition;
import etri.sdn.controller.module.routing.IRoutingDecision;
import etri.sdn.controller.protocol.io.Connection;
import etri.sdn.controller.protocol.packet.Ethernet;


public class OFMTunnelManager extends OFModule implements IOFMTunnelManagerService {
	
	public static final Logger logger = LoggerFactory.getLogger(OFMTunnelManager.class);
	
	public class NodeDefinition {
//		public boolean flow_sync = false;
//		public boolean tag_sync = false;
		public String node_ip_mgt = null;
		public String node_ip_tun = null;
		public String node_name = null;
		public String node_type = null;
		public String current_time = null;
		public ArrayList<Integer> available_local_vlans = null;
		public Map<String, NetworkDefinition> used_local_vNetsByVlanid = null;
		public Map<String, NetworkDefinition> used_local_vNetsByGuid = null;
		public ArrayList<String> local_vlan_ofports = null;
		public Map<String, String> local_vNetidToVlanid = null;
		public Map<String, PortDefinition> used_local_vPortsByGuid = null;
	}
	
	private TunnelConfiguration tunConf = null;
	
	TorpedoProperties sysconf = TorpedoProperties.loadConfiguration();
	private final String TUNNEL_TYPE = sysconf.getString("tunnel-type");
	private final String TUNNEL_PORT = sysconf.getString("vxlan-port-number");
	private final String INAAS_AGENT_REST_PORT = sysconf.getString("iNaaSAgent-rest-port");
	
	private static final String INTEGRATION_BRIDGE_NAME = "br-int";
	private static final String TUNNELING_BRIDGE_NAME = "br-tun";
//	private static final String INT_PEER_PATCH_PORT = "patch-tun";
	private static final String TUN_PEER_PATCH_PORT = "patch-int";
	private static final int DELETE_TIME_GAP = 3;
	
	private static final int MIN_VLAN_TAG = 1;
	private static final int MAX_VLAN_TAG = 4094;
	
	private static final String PATCH_LV_TO_TUN = "1";
	private static final String GRE_TUN_TO_LV = "2";
	private static final String VXLAN_TUN_TO_LV = "3";
	private static final String LEARN_FROM_TUN = "10";
	private static final String UCAST_TO_TUN = "20";
	private static final String FLOOD_TO_TUN = "21";
	private static final String CANARY_TABLE = "22";
	
	protected static Map<String, NodeDefinition> nodesByIp;
	protected static Map<String, Long> intDpidByIp;
	protected static Map<Long, String> nodeIpByDpid;
	protected static Map<String, NetworkDefinition> vNetsByGuid;	// List of all created virtual networks
	protected static Map<String, PortDefinition> vPortsByGuid;	// List of all created virtual networks
	protected static Map<String, PortDefinition> vmByGuid;
	
	protected IOpenstackML2ConnectorService openstackML2Connector;
	
	@Override
	protected Collection<Class<? extends IService>> services() {
		List<Class<? extends IService>> ret = new LinkedList<>();
		ret.add( IOFMTunnelManagerService.class);
		return ret;
	}

	public OFMTunnelManager() {
		this.tunConf = new TunnelConfiguration(this);
	}

	@Override
	protected void initialize() {
		openstackML2Connector = (IOpenstackML2ConnectorService) getModule(IOpenstackML2ConnectorService.class);
		
		nodesByIp = new ConcurrentHashMap<String, NodeDefinition>();
		intDpidByIp = new ConcurrentHashMap<String, Long>();
		nodeIpByDpid = new ConcurrentHashMap<Long, String>();
		vNetsByGuid = new ConcurrentHashMap<String, NetworkDefinition>();
		vPortsByGuid = new ConcurrentHashMap<String, PortDefinition>();
		vmByGuid = new ConcurrentHashMap<String, PortDefinition>();
		
		// when restarting controller neutron info initialize
		Thread nii = new NeutronInfoInitialize();
		nii.start();
		
		registerFilter(
				OFType.PACKET_IN, 
				new OFMFilter() {
					@Override
					public boolean filter(OFMessage m) {
						// we process all PACKET_IN regardless of its version.
						OFPacketIn pi = (OFPacketIn) m;
						
						byte[] packet = pi.getData();
						
						// this checks if the Packet-In is for LLDP!
						// This is very important to guarantee maximum performance. (-_-;)
						if ( packet[12] != (byte)0x88 || packet[13] != (byte)0xcc ) {
							// LLDP packet is not mine!
							return true;
						}
						return false;
					}
					
				});
		
//		this.controller.scheduleTask(
//				new IOFTask() {
//					@Override
//					public boolean execute() {
//						syncTunnel();
//						return true;
//					}
//				}, 
//				0,
//				1 * 1000 /* milliseconds */
//				);
		
		this.controller.scheduleTask(
				new IOFTask() {
					@Override
					public boolean execute() {
						delTunnel();
						return true;
					}
				}, 
				0,
				60 * 1000 /* milliseconds */
				);
		
	}
	
	@Override
	protected boolean handleHandshakedEvent(Connection conn, MessageContext context) {
		return true;
	}

	@Override
	protected boolean handleMessage(Connection conn, MessageContext context, OFMessage msg, List<OFMessage> outgoing) {
		
		switch(msg.getType()) {
		case PACKET_IN:
			IRoutingDecision decision = null;
			if (context != null) {
				decision = (IRoutingDecision) context.get(MessageContext.ROUTING_DECISION);

				return this.processPacketInMessage(conn, msg, decision, context);
			}
			break;
		default:
			break;
		}
		
		return true;
	}

	@Override
	protected boolean handleDisconnect(Connection conn) {
		return true;
	}

	@Override
	public OFModel[] getModels() {
		return new OFModel[] { this.tunConf };
	}
	
	private boolean processPacketInMessage(Connection conn, OFMessage msg, IRoutingDecision decision, MessageContext cntx){
		
		if(intDpidByIp.containsValue(conn.getSwitch().getId())) {
			OFPacketIn pi = (OFPacketIn) msg; 
			
			OFFactory fac = OFFactories.getFactory(pi.getVersion());
			OFFlowMod.Builder fm = fac.buildFlowAdd();
			
			fm.setHardTimeout(0)
			  .setIdleTimeout(5)
			  .setPriority(1);
			
			Ethernet etherPacket = new Ethernet();
			etherPacket.deserialize(pi.getData(), 0, pi.getData().length);
			EthType etherType = null;
			etherType = EthType.of(etherPacket.getEtherType());
			
			if(etherType.getValue() == 0xffff86dd) {
				etherType = EthType.IPv6;
			}
			
			Match.Builder match = fac.buildMatch();
			if("local".equals(pi.getInPort().toString().toLowerCase())) {
				match.setExact(MatchField.IN_PORT, OFPort.LOCAL);
			} else {
				match.setExact(MatchField.IN_PORT, pi.getInPort());
			}
			match.setExact(MatchField.ETH_TYPE, etherType);
			
			fm.setMatch(match.build());

			List<OFAction> actions = new ArrayList<OFAction>();
			OFActionOutput.Builder action = fac.actions().buildOutput();
			action.setPort(OFPort.NORMAL);
			actions.add(action.build());
			fm.setActions(actions);

			if ( pi.getBufferId() != OFBufferId.NO_BUFFER ) {
				fm.setBufferId( pi.getBufferId() );
			} else {
				OFPacketOut.Builder po = fac.buildPacketOut();
				try {
					po
					.setData( pi.getData() )
					.setInPort( pi.getInPort() );
				} catch (UnsupportedOperationException e) {
					// this exception might be because of setInPort (1.3 does not support it.)
					// just ignore.
				}
				
				po.setActions( Arrays.<OFAction>asList( fac.actions().output(OFPort.NORMAL, 0) ) );
				
				conn.write(po.build());
			}

			conn.write(fm.build());

//System.out.println("========================================================");
//System.out.println("intDpidByIp : " + intDpidByIp);
//System.out.println("conn.getSwitch() : " + conn.getSwitch());
//System.out.println("conn.getSwitch().getId() : " + conn.getSwitch().getId());
//System.out.println("pi.getInPort() : " + pi.getInPort());
//System.out.println("pi.getBufferId() : " + pi.getBufferId());
//System.out.println("etherType : " + etherType);
//System.out.println("fm.build() : " + fm.build());
//System.out.println("========================================================");
			
			return false;
		} else {
			return true;
		}
	}
	
	@Override
	public void addTunnel(String node_ip_mgt, String node_ip_tun, String node_name, String node_type, String iris_ip) {
		Date now = new Date(System.currentTimeMillis());
		SimpleDateFormat simpledateformat = new SimpleDateFormat("yyyyMMddHHmm");
		String current_time = simpledateformat.format(now);
		
		try {
			if(!nodesByIp.containsKey(node_ip_mgt)) {
				ArrayList<Integer> init_vlans = new ArrayList<Integer>();
				for (int i = MIN_VLAN_TAG ; i <= MAX_VLAN_TAG ; i++) {
					init_vlans.add(i);
				}
				
				NodeDefinition nodeInfo = new NodeDefinition();
				nodeInfo.node_ip_mgt = node_ip_mgt;
				nodeInfo.node_ip_tun = node_ip_tun;
				nodeInfo.node_name = node_name;
				nodeInfo.node_type = node_type;
				nodeInfo.current_time = current_time;
				nodeInfo.available_local_vlans = init_vlans;
				
				nodesByIp.put(node_ip_mgt, nodeInfo);
				long intDpid = Long.parseLong(rest_get(node_ip_mgt, INAAS_AGENT_REST_PORT, "sudo ovs-vsctl get Bridge "+INTEGRATION_BRIDGE_NAME+" datapath_id").get(0).replaceAll("\"", ""), 16);
				long tunDpid = Long.parseLong(rest_get(node_ip_mgt, INAAS_AGENT_REST_PORT, "sudo ovs-vsctl get Bridge "+TUNNELING_BRIDGE_NAME+" datapath_id").get(0).replaceAll("\"", ""), 16);
				intDpidByIp.put(node_ip_mgt, intDpid);
				nodeIpByDpid.put(intDpid, node_ip_mgt);
				nodeIpByDpid.put(tunDpid, node_ip_mgt);
				
				setup_bridge(node_ip_mgt, iris_ip);
				
				// tunnel create ( new node <--> exist node )
				if(!nodesByIp.isEmpty() && nodesByIp.size() > 1) {
					for(Entry<String, NodeDefinition> entry : nodesByIp.entrySet()) {
						String remote_ip_mgt = entry.getKey();
						String remote_ip_tun = entry.getValue().node_ip_tun;
						
						if(!remote_ip_mgt.equals(node_ip_mgt)) {
							setup_tunnel_port(node_ip_mgt,
									TUNNELING_BRIDGE_NAME,
									TUNNEL_TYPE + "-" + HexString.toHexString(InetAddress.getByName(remote_ip_tun).getAddress()).replaceAll(":", ""),
									node_ip_tun,
									remote_ip_tun);
							
							setup_tunnel_port(remote_ip_mgt,
									TUNNELING_BRIDGE_NAME,
									TUNNEL_TYPE + "-" + HexString.toHexString(InetAddress.getByName(node_ip_tun).getAddress()).replaceAll(":", ""),
									remote_ip_tun,
									node_ip_tun);
						}
					}
				}
			}
			
			nodesByIp.get(node_ip_mgt).current_time = current_time;
			nodesByIp.get(node_ip_mgt).node_name = node_name;
			nodesByIp.get(node_ip_mgt).node_type = node_type;
		} catch (Exception e) {
			logger.error("Unable to create tunnel port. {}", e.getMessage());
		}
	}
	
	public void setup_bridge(String ovsdb_server_remote_ip, String iris_ip) {
		try {
			String patch_int_ofport = rest_get(ovsdb_server_remote_ip, INAAS_AGENT_REST_PORT, "sudo ovs-vsctl get Interface "+TUN_PEER_PATCH_PORT+" ofport").get(0);
			
			rest_post(ovsdb_server_remote_ip, INAAS_AGENT_REST_PORT, "sudo ovs-vsctl del-controller "+INTEGRATION_BRIDGE_NAME);
			Thread.sleep(100);
			rest_post(ovsdb_server_remote_ip, INAAS_AGENT_REST_PORT, "sudo ovs-vsctl del-controller "+TUNNELING_BRIDGE_NAME);
			Thread.sleep(100);
			rest_post(ovsdb_server_remote_ip, INAAS_AGENT_REST_PORT, "sudo ovs-vsctl set-controller "+INTEGRATION_BRIDGE_NAME+" tcp:"+iris_ip+":"+sysconf.getString("port-number"));
			Thread.sleep(100);
			rest_post(ovsdb_server_remote_ip, INAAS_AGENT_REST_PORT, "sudo ovs-vsctl set-controller "+TUNNELING_BRIDGE_NAME+" tcp:"+iris_ip+":"+sysconf.getString("port-number"));
			Thread.sleep(100);
			
//			rest_post(ovsdb_server_remote_ip, INAAS_AGENT_REST_PORT, "sudo ovs-ofctl del-flows "+INTEGRATION_BRIDGE_NAME);
//			Thread.sleep(100);
//			rest_post(ovsdb_server_remote_ip, INAAS_AGENT_REST_PORT, "sudo ovs-ofctl del-flows "+TUNNELING_BRIDGE_NAME);
//			Thread.sleep(100);
			
	        rest_post(ovsdb_server_remote_ip, INAAS_AGENT_REST_PORT, "sudo ovs-ofctl add-flow "+INTEGRATION_BRIDGE_NAME+" hard_timeout=0,idle_timeout=0,priority=100,dl_type=0x88cc,actions=CONTROLLER");
	        Thread.sleep(100);
//	        rest_post(ovsdb_server_remote_ip, INAAS_AGENT_REST_PORT, "sudo ovs-ofctl add-flow "+INTEGRATION_BRIDGE_NAME+" hard_timeout=0,idle_timeout=0,priority=1,actions=normal");
	        rest_post(ovsdb_server_remote_ip, INAAS_AGENT_REST_PORT, "sudo ovs-ofctl add-flow "+INTEGRATION_BRIDGE_NAME+" hard_timeout=0,idle_timeout=0,table="+CANARY_TABLE+",priority=0,actions=drop");
	        Thread.sleep(100);
	        
			rest_post(ovsdb_server_remote_ip, INAAS_AGENT_REST_PORT, "sudo ovs-ofctl add-flow "+TUNNELING_BRIDGE_NAME+" hard_timeout=0,idle_timeout=0,priority=100,dl_type=0x88cc,actions=CONTROLLER");
			Thread.sleep(100);
			rest_post(ovsdb_server_remote_ip, INAAS_AGENT_REST_PORT, "sudo ovs-ofctl add-flow "+TUNNELING_BRIDGE_NAME+" hard_timeout=0,idle_timeout=0,priority=1,in_port="+patch_int_ofport+",actions=resubmit(,"+PATCH_LV_TO_TUN+")");
			Thread.sleep(100);
			rest_post(ovsdb_server_remote_ip, INAAS_AGENT_REST_PORT, "sudo ovs-ofctl add-flow "+TUNNELING_BRIDGE_NAME+" hard_timeout=0,idle_timeout=0,priority=0,actions=drop");
			Thread.sleep(100);
			rest_post(ovsdb_server_remote_ip, INAAS_AGENT_REST_PORT, "sudo ovs-ofctl add-flow "+TUNNELING_BRIDGE_NAME+" hard_timeout=0,idle_timeout=0,table="+PATCH_LV_TO_TUN+",priority=1,dl_dst=00:00:00:00:00:00/01:00:00:00:00:00,actions=resubmit(,"+UCAST_TO_TUN+")");
			Thread.sleep(100);
			rest_post(ovsdb_server_remote_ip, INAAS_AGENT_REST_PORT, "sudo ovs-ofctl add-flow "+TUNNELING_BRIDGE_NAME+" hard_timeout=0,idle_timeout=0,table="+PATCH_LV_TO_TUN+",priority=1,dl_dst=01:00:00:00:00:00/01:00:00:00:00:00,actions=resubmit(,"+FLOOD_TO_TUN+")");
			Thread.sleep(100);
			if("gre".equals(TUNNEL_TYPE)) {
				rest_post(ovsdb_server_remote_ip, INAAS_AGENT_REST_PORT, "sudo ovs-ofctl add-flow "+TUNNELING_BRIDGE_NAME+" hard_timeout=0,idle_timeout=0,table="+GRE_TUN_TO_LV+",priority=0,actions=drop");
				Thread.sleep(100);
			} else if("vxlan".equals(TUNNEL_TYPE)) {
				rest_post(ovsdb_server_remote_ip, INAAS_AGENT_REST_PORT, "sudo ovs-ofctl add-flow "+TUNNELING_BRIDGE_NAME+" hard_timeout=0,idle_timeout=0,table="+VXLAN_TUN_TO_LV+",priority=0,actions=drop");
				Thread.sleep(100);
			}
			rest_post(ovsdb_server_remote_ip, INAAS_AGENT_REST_PORT, "sudo ovs-ofctl add-flow "+TUNNELING_BRIDGE_NAME+" hard_timeout=0,idle_timeout=0,table="+LEARN_FROM_TUN+",priority=1,actions=learn(table="+UCAST_TO_TUN+",priority=1,hard_timeout=300,NXM_OF_VLAN_TCI[0..11],NXM_OF_ETH_DST[]=NXM_OF_ETH_SRC[],load:0->NXM_OF_VLAN_TCI[],load:NXM_NX_TUN_ID[]->NXM_NX_TUN_ID[],output:NXM_OF_IN_PORT[]),output:"+patch_int_ofport);
			Thread.sleep(100);
			rest_post(ovsdb_server_remote_ip, INAAS_AGENT_REST_PORT, "sudo ovs-ofctl add-flow "+TUNNELING_BRIDGE_NAME+" hard_timeout=0,idle_timeout=0,table="+UCAST_TO_TUN+",priority=0,actions=resubmit(,"+FLOOD_TO_TUN+")");
			Thread.sleep(100);
			rest_post(ovsdb_server_remote_ip, INAAS_AGENT_REST_PORT, "sudo ovs-ofctl add-flow "+TUNNELING_BRIDGE_NAME+" hard_timeout=0,idle_timeout=0,table="+FLOOD_TO_TUN+",priority=0,actions=drop");
		} catch(Exception e) {
			logger.error("Unable to setup_bridge. Exception:  {}", e.getMessage());
		}
	}
	
	public void setup_tunnel_port(String ovsdb_server_remote_ip, String bridge_name, String port_name, String local_ip_tun, String remote_ip_tun) {
		try {
			rest_post(ovsdb_server_remote_ip, INAAS_AGENT_REST_PORT, "sudo ovs-vsctl --may-exist add-port "+bridge_name+" "+port_name+" -- set Interface "+port_name+" type="+TUNNEL_TYPE+" options:in_key=flow options:local_ip="+local_ip_tun+" options:out_key=flow options:remote_ip="+remote_ip_tun+" options:dst_port="+TUNNEL_PORT);
			Thread.sleep(100);
			String ofport = rest_get(ovsdb_server_remote_ip, INAAS_AGENT_REST_PORT, "sudo ovs-vsctl get Interface "+port_name+" ofport").get(0);
			
			rest_post(ovsdb_server_remote_ip, INAAS_AGENT_REST_PORT, "sudo ovs-ofctl add-flow "+TUNNELING_BRIDGE_NAME+" hard_timeout=0,idle_timeout=0,priority=1,in_port="+ofport+",actions=resubmit(,"+VXLAN_TUN_TO_LV+")");
			
			if(nodesByIp.get(ovsdb_server_remote_ip).local_vlan_ofports == null) {
				nodesByIp.get(ovsdb_server_remote_ip).local_vlan_ofports = new ArrayList<String>();
			}
			if(!nodesByIp.get(ovsdb_server_remote_ip).local_vlan_ofports.contains(ofport)) {
				nodesByIp.get(ovsdb_server_remote_ip).local_vlan_ofports.add(ofport);
			}
			
			// change sync true
//			nodesByIp.get(ovsdb_server_remote_ip).flow_sync = true;
			ofports_flow(ovsdb_server_remote_ip);
		} catch(Exception e) {
			logger.error("Unable to setup_tunnel_port. Exception:  {}", e.getMessage());
		}
	}
	
	public void rest_post(String node_ip, String node_port, String command) {
		logger.debug("rest_post({}:{}) : {}", node_ip, node_port, command);
		
		ClientResource resource = null;
		StringRepresentation stringRep = null;
		
		try {
			String restUri = "http://" + node_ip + ":" + node_port + "/wm/tunnel/iNaaSAgent";
			
			Context context = new Context();
			context.getParameters().add("socketTimeout", "1000");
			context.getParameters().add("idleTimeout", "1000");
			
			resource = new ClientResource(context, restUri);
			resource.setMethod(Method.POST);
			resource.getReference().addQueryParameter("format", "json");
			
			stringRep = new StringRepresentation(command);
			stringRep.setMediaType(MediaType.APPLICATION_JSON);
			
			resource.post(stringRep);
		} catch(Exception e) {
			logger.error("========== Method Name : rest_post(String node_ip, String node_port, String command) ==========");
			e.printStackTrace();
		} finally {
			stringRep.release();
			resource.release();
		}
	}
	
	public ArrayList<String> rest_get(String node_ip, String node_port, String command) {
		logger.debug("rest_get({}:{}) : {}", node_ip, node_port, command);
		
		ArrayList<String> returnVal = new ArrayList<String>();
		ClientResource resource = null;
		
		try {
			String restUri = "http://" + node_ip + ":" + node_port + "/wm/tunnel/iNaaSAgent";
			
			Context context = new Context();
			context.getParameters().add("socketTimeout", "1000");
			context.getParameters().add("idleTimeout", "1000");
			
			resource = new ClientResource(context, restUri+"/"+URLEncoder.encode(command, "UTF-8"));
			resource.setMethod(Method.GET);
			resource.get();
			
			ObjectMapper om = new ObjectMapper();
			returnVal = om.readValue(resource.getResponse().getEntityAsText(), new TypeReference<ArrayList<String>>(){});
		} catch(Exception e) {
			logger.error("========== Method Name : rest_get(String node_ip, String node_port, String command) ==========");
			e.printStackTrace();
		} finally {
			resource.release();
		}
		
		return returnVal;
	}
	
	public void create_network_flow(NetworkDefinition network) {
		vNetsByGuid.put(network.netId, network);
		
		String network_node_ip = "";
		
		for(Entry<String, NodeDefinition> entryMap : nodesByIp.entrySet()) {
			if("network".equals(entryMap.getValue().node_type)) {
				network_node_ip = entryMap.getKey();
			}
		}
		
		if(!"".equals(network_node_ip)) {
			try{
				ArrayList<Integer> available_vlans = new ArrayList<Integer>();
				available_vlans = nodesByIp.get(network_node_ip).available_local_vlans;
				Collections.sort(available_vlans);
				
				String mod_vlan_vid = available_vlans.get(0).toString();
				available_vlans.remove(0);
				
				nodesByIp.get(network_node_ip).available_local_vlans = available_vlans;
				
				if(nodesByIp.get(network_node_ip).used_local_vNetsByVlanid == null) {
					nodesByIp.get(network_node_ip).used_local_vNetsByVlanid = new ConcurrentHashMap<String, NetworkDefinition>();
				}
				nodesByIp.get(network_node_ip).used_local_vNetsByVlanid.put(mod_vlan_vid, network);
				
				if(nodesByIp.get(network_node_ip).used_local_vNetsByGuid == null) {
					nodesByIp.get(network_node_ip).used_local_vNetsByGuid = new ConcurrentHashMap<String, NetworkDefinition>();
				}
				nodesByIp.get(network_node_ip).used_local_vNetsByGuid.put(network.netId, network);
				
				if(nodesByIp.get(network_node_ip).local_vNetidToVlanid == null) {
					nodesByIp.get(network_node_ip).local_vNetidToVlanid = new ConcurrentHashMap<String, String>();
				}
				nodesByIp.get(network_node_ip).local_vNetidToVlanid.put(network.netId, mod_vlan_vid);

				String tun_id = "0x"+Integer.toHexString(Integer.parseInt(network.provider_segmentation_id)).toString();

				rest_post(network_node_ip, INAAS_AGENT_REST_PORT, "sudo ovs-ofctl add-flow "+TUNNELING_BRIDGE_NAME+" hard_timeout=0,idle_timeout=0,table="+VXLAN_TUN_TO_LV+",priority=1,tun_id="+tun_id+",actions=mod_vlan_vid:"+mod_vlan_vid+",resubmit(,"+LEARN_FROM_TUN+")");
				
				// change sync true
//				nodesByIp.get(network_node_ip).flow_sync = true;
				ofports_flow(network_node_ip);
			} catch(Exception e) {
				logger.error("Unable to create_network_flow. Exception : {}", e.getMessage());
			}
		}
	}
	
	public void delete_network_flow(String network_id) {
		if(vNetsByGuid.containsKey(network_id)) {
			vNetsByGuid.remove(network_id);
		}
		
		if(!nodesByIp.isEmpty()) {
			for(Entry<String, NodeDefinition> entry : nodesByIp.entrySet()) {
				if(nodesByIp.get(entry.getKey()).used_local_vNetsByGuid != null && nodesByIp.get(entry.getKey()).used_local_vNetsByGuid.containsKey(network_id)) {
					String vlan_id = nodesByIp.get(entry.getKey()).local_vNetidToVlanid.get(network_id);
					String segmentation_id = nodesByIp.get(entry.getKey()).used_local_vNetsByGuid.get(network_id).provider_segmentation_id;
					String tun_id = "0x"+Integer.toHexString(Integer.parseInt(segmentation_id)).toString();
					
					logger.debug("delFlow(delete_network_flow) : IP - {}, FLOW - {}", entry.getKey(), "sudo ovs-ofctl del-flows "+TUNNELING_BRIDGE_NAME+" table="+FLOOD_TO_TUN+",dl_vlan="+vlan_id);
					rest_post(entry.getKey(), INAAS_AGENT_REST_PORT, "sudo ovs-ofctl del-flows "+TUNNELING_BRIDGE_NAME+" table="+FLOOD_TO_TUN+",dl_vlan="+vlan_id);
					logger.debug("delFlow(delete_network_flow) : IP - {}, FLOW - {}", entry.getKey(), "sudo ovs-ofctl del-flows "+TUNNELING_BRIDGE_NAME+" table="+VXLAN_TUN_TO_LV+",tun_id="+tun_id);
					rest_post(entry.getKey(), INAAS_AGENT_REST_PORT, "sudo ovs-ofctl del-flows "+TUNNELING_BRIDGE_NAME+" table="+VXLAN_TUN_TO_LV+",tun_id="+tun_id);
					
					nodesByIp.get(entry.getKey()).available_local_vlans.add(Integer.parseInt(vlan_id));
					nodesByIp.get(entry.getKey()).used_local_vNetsByVlanid.remove(vlan_id);
					nodesByIp.get(entry.getKey()).used_local_vNetsByGuid.remove(network_id);
					nodesByIp.get(entry.getKey()).local_vNetidToVlanid.remove(network_id);
				}
			}
		}
	}
	
//	public void create_port_flow(VirtualPort port) {
//		vPortsByGuid.put(port.getPortId(), port);
//
//		if("network:dhcp".equals(port.device_owner) || "network:router_interface".equals(port.device_owner)) {
//			String network_node_ip = "";
//			
//			for(Entry<String, NodeDefinition> entryMap : nodesByIp.entrySet()) {
//				if("network".equals(entryMap.getValue().node_type)) {
//					network_node_ip = entryMap.getKey();
//				}
//			}
//			
//			if(!"".equals(network_node_ip)) {
//				if(nodesByIp.get(network_node_ip).used_local_vPortsByGuid == null) {
//					nodesByIp.get(network_node_ip).used_local_vPortsByGuid = new ConcurrentHashMap<String, PortDefinition>();
//				}
//				nodesByIp.get(network_node_ip).used_local_vPortsByGuid.put(port.portId, port);
//				
//				// change sync true
//				nodesByIp.get(network_node_ip).tag_sync = true;
//			}
//		} else if("compute:nova".equals(port.device_owner) || "compute:None".equals(port.device_owner)) {
////		} else if("compute:".equals(port.device_owner.substring(0, 8))) {
//			String compute_node_ip = "";
//		
//			for(Entry<String, NodeDefinition> entryMap : nodesByIp.entrySet()) {
//				if(port.binding_host_id.equals(entryMap.getValue().node_name)) {
//					compute_node_ip = entryMap.getKey();
//				}
//			}
//			if(!"".equals(compute_node_ip)) {
//				try{
//					if(nodesByIp.get(compute_node_ip).used_local_vNetsByGuid == null || 
//							(nodesByIp.get(compute_node_ip).used_local_vNetsByGuid != null && !nodesByIp.get(compute_node_ip).used_local_vNetsByGuid.containsKey(port.network_id))) {
//						ArrayList<Integer> available_vlans = new ArrayList<Integer>();
//						available_vlans = nodesByIp.get(compute_node_ip).available_local_vlans;
//						Collections.sort(available_vlans);
//						
//						String mod_vlan_vid = available_vlans.get(0).toString();
//						available_vlans.remove(0);
//						
//						nodesByIp.get(compute_node_ip).available_local_vlans = available_vlans;
//						
//						if(nodesByIp.get(compute_node_ip).used_local_vNetsByVlanid == null) {
//							nodesByIp.get(compute_node_ip).used_local_vNetsByVlanid = new ConcurrentHashMap<String, NetworkDefinition>();
//						}
//						nodesByIp.get(compute_node_ip).used_local_vNetsByVlanid.put(mod_vlan_vid, vNetsByGuid.get(port.network_id));
//						
//						if(nodesByIp.get(compute_node_ip).used_local_vNetsByGuid == null) {
//							nodesByIp.get(compute_node_ip).used_local_vNetsByGuid = new ConcurrentHashMap<String, NetworkDefinition>();
//						}
//						nodesByIp.get(compute_node_ip).used_local_vNetsByGuid.put(port.network_id, vNetsByGuid.get(port.network_id));
//						
//						if(nodesByIp.get(compute_node_ip).local_vNetidToVlanid == null) {
//							nodesByIp.get(compute_node_ip).local_vNetidToVlanid = new ConcurrentHashMap<String, String>();
//						}
//						nodesByIp.get(compute_node_ip).local_vNetidToVlanid.put(port.network_id, mod_vlan_vid);
//	
//						String tun_id = "0x"+Integer.toHexString(Integer.parseInt(vNetsByGuid.get(port.network_id).provider_segmentation_id)).toString();
//	
//						rest_post(compute_node_ip, INAAS_AGENT_REST_PORT, "sudo ovs-ofctl add-flow "+TUNNELING_BRIDGE_NAME+" hard_timeout=0,idle_timeout=0,table="+VXLAN_TUN_TO_LV+",priority=1,tun_id="+tun_id+",actions=mod_vlan_vid:"+mod_vlan_vid+",resubmit(,"+LEARN_FROM_TUN+")");
//					}
//					
//					if(nodesByIp.get(compute_node_ip).used_local_vPortsByGuid == null) {
//						nodesByIp.get(compute_node_ip).used_local_vPortsByGuid = new ConcurrentHashMap<String, PortDefinition>();
//					}
//					nodesByIp.get(compute_node_ip).used_local_vPortsByGuid.put(port.portId, port);
//					
//					vmByGuid.put(port.portId, port);
//					
//					// change sync true
//					nodesByIp.get(compute_node_ip).flow_sync = true;
//					nodesByIp.get(compute_node_ip).tag_sync = true;
//				} catch(Exception e) {
//					logger.error("Unable to create_port_flow. Exception : {}", e.getMessage());
//					e.printStackTrace();
//				}
//			}
//		}
//	}
	public void create_port_flow(PortDefinition port) {
		vPortsByGuid.put(port.portId, port);

		if("network:dhcp".equals(port.device_owner) || "network:router_interface".equals(port.device_owner)) {
			String network_node_ip = "";
			
			for(Entry<String, NodeDefinition> entryMap : nodesByIp.entrySet()) {
				if("network".equals(entryMap.getValue().node_type)) {
					network_node_ip = entryMap.getKey();
				}
			}
			
			if(!"".equals(network_node_ip)) {
				if(nodesByIp.get(network_node_ip).used_local_vPortsByGuid == null) {
					nodesByIp.get(network_node_ip).used_local_vPortsByGuid = new ConcurrentHashMap<String, PortDefinition>();
				}
				nodesByIp.get(network_node_ip).used_local_vPortsByGuid.put(port.portId, port);
				
				// change sync true
//				nodesByIp.get(network_node_ip).tag_sync = true;
				port_tagging(network_node_ip, port.portId, port.network_id);
			}
		} else if("compute:nova".equals(port.device_owner) || "compute:None".equals(port.device_owner)) {
//		} else if("compute:".equals(port.device_owner.substring(0, 8))) {
			String compute_node_ip = "";
		
			for(Entry<String, NodeDefinition> entryMap : nodesByIp.entrySet()) {
				if(port.binding_host_id.equals(entryMap.getValue().node_name)) {
					compute_node_ip = entryMap.getKey();
				}
			}
			if(!"".equals(compute_node_ip)) {
				try{
					if(nodesByIp.get(compute_node_ip).used_local_vNetsByGuid == null || 
							(nodesByIp.get(compute_node_ip).used_local_vNetsByGuid != null && !nodesByIp.get(compute_node_ip).used_local_vNetsByGuid.containsKey(port.network_id))) {
						ArrayList<Integer> available_vlans = new ArrayList<Integer>();
						available_vlans = nodesByIp.get(compute_node_ip).available_local_vlans;
						Collections.sort(available_vlans);
						
						String mod_vlan_vid = available_vlans.get(0).toString();
						available_vlans.remove(0);
						
						nodesByIp.get(compute_node_ip).available_local_vlans = available_vlans;
						
						if(nodesByIp.get(compute_node_ip).used_local_vNetsByVlanid == null) {
							nodesByIp.get(compute_node_ip).used_local_vNetsByVlanid = new ConcurrentHashMap<String, NetworkDefinition>();
						}
						nodesByIp.get(compute_node_ip).used_local_vNetsByVlanid.put(mod_vlan_vid, vNetsByGuid.get(port.network_id));
						
						if(nodesByIp.get(compute_node_ip).used_local_vNetsByGuid == null) {
							nodesByIp.get(compute_node_ip).used_local_vNetsByGuid = new ConcurrentHashMap<String, NetworkDefinition>();
						}
						nodesByIp.get(compute_node_ip).used_local_vNetsByGuid.put(port.network_id, vNetsByGuid.get(port.network_id));
						
						if(nodesByIp.get(compute_node_ip).local_vNetidToVlanid == null) {
							nodesByIp.get(compute_node_ip).local_vNetidToVlanid = new ConcurrentHashMap<String, String>();
						}
						nodesByIp.get(compute_node_ip).local_vNetidToVlanid.put(port.network_id, mod_vlan_vid);
	
						String tun_id = "0x"+Integer.toHexString(Integer.parseInt(vNetsByGuid.get(port.network_id).provider_segmentation_id)).toString();

						rest_post(compute_node_ip, INAAS_AGENT_REST_PORT, "sudo ovs-ofctl add-flow "+TUNNELING_BRIDGE_NAME+" hard_timeout=0,idle_timeout=0,table="+VXLAN_TUN_TO_LV+",priority=1,tun_id="+tun_id+",actions=mod_vlan_vid:"+mod_vlan_vid+",resubmit(,"+LEARN_FROM_TUN+")");
					}
					
					if(nodesByIp.get(compute_node_ip).used_local_vPortsByGuid == null) {
						nodesByIp.get(compute_node_ip).used_local_vPortsByGuid = new ConcurrentHashMap<String, PortDefinition>();
					}
					nodesByIp.get(compute_node_ip).used_local_vPortsByGuid.put(port.portId, port);
					
					vmByGuid.put(port.portId, port);
					
					// change sync true
//					nodesByIp.get(compute_node_ip).flow_sync = true;
//					nodesByIp.get(compute_node_ip).tag_sync = true;
					ofports_flow(compute_node_ip);
					port_tagging(compute_node_ip, port.portId, port.network_id);
				} catch(Exception e) {
					logger.error("Unable to create_port_flow. Exception : {}", e.getMessage());
					e.printStackTrace();
				}
			}
		}
	}
	
	public void delete_port_flow(String portId) {
		String device_owner = vPortsByGuid.get(portId).device_owner;
		String binding_host_id = vPortsByGuid.get(portId).binding_host_id;
		String network_id = vPortsByGuid.get(portId).network_id;
		
		if(vPortsByGuid.containsKey(portId)) {
			vPortsByGuid.remove(portId);
		}
		
		if("network:dhcp".equals(device_owner) || "network:router_interface".equals(device_owner)) {
			String network_node_ip = "";
			
			for(Entry<String, NodeDefinition> entryMap : nodesByIp.entrySet()) {
				if("network".equals(entryMap.getValue().node_type)) {
					network_node_ip = entryMap.getKey();
				}
			}
			
			if(!"".equals(network_node_ip)) {
				if(nodesByIp.get(network_node_ip).used_local_vPortsByGuid != null && nodesByIp.get(network_node_ip).used_local_vPortsByGuid.containsKey(portId)) {
					nodesByIp.get(network_node_ip).used_local_vPortsByGuid.remove(portId);
				}
			}
		}
//		else if("compute:nova".equals(device_owner)) {
		else if("compute:nova".equals(device_owner) || "compute:None".equals(device_owner)) {
			String compute_node_ip = "";
			
			for(Entry<String, NodeDefinition> entryMap : nodesByIp.entrySet()) {
				if(binding_host_id.equals(entryMap.getValue().node_name)) {
					compute_node_ip = entryMap.getKey();
				}
			}
			
			if(!"".equals(compute_node_ip)) {
				String vlan_id = nodesByIp.get(compute_node_ip).local_vNetidToVlanid.get(network_id);
				String tun_id = "0x"+Integer.toHexString(Integer.parseInt(vNetsByGuid.get(network_id).provider_segmentation_id)).toString();

				if(nodesByIp.get(compute_node_ip).used_local_vPortsByGuid != null && nodesByIp.get(compute_node_ip).used_local_vPortsByGuid.containsKey(portId)) {
					nodesByIp.get(compute_node_ip).used_local_vPortsByGuid.remove(portId);
					
					vmByGuid.remove(portId);
				}
				
				if(nodesByIp.get(compute_node_ip).used_local_vPortsByGuid == null || nodesByIp.get(compute_node_ip).used_local_vPortsByGuid.isEmpty()) {
					logger.debug("delFlow(delete_port_flow) : IP - {}, FLOW - {}", compute_node_ip, "sudo ovs-ofctl del-flows "+TUNNELING_BRIDGE_NAME+" table="+FLOOD_TO_TUN+",dl_vlan="+vlan_id);
					rest_post(compute_node_ip, INAAS_AGENT_REST_PORT, "sudo ovs-ofctl del-flows "+TUNNELING_BRIDGE_NAME+" table="+FLOOD_TO_TUN+",dl_vlan="+vlan_id);
					logger.debug("delFlow(delete_port_flow) : IP - {}, FLOW - {}", compute_node_ip, "sudo ovs-ofctl del-flows "+TUNNELING_BRIDGE_NAME+" table="+VXLAN_TUN_TO_LV+",tun_id="+tun_id);
					rest_post(compute_node_ip, INAAS_AGENT_REST_PORT, "sudo ovs-ofctl del-flows "+TUNNELING_BRIDGE_NAME+" table="+VXLAN_TUN_TO_LV+",tun_id="+tun_id);
					
					nodesByIp.get(compute_node_ip).available_local_vlans.add(Integer.parseInt(vlan_id));
					nodesByIp.get(compute_node_ip).used_local_vNetsByVlanid.remove(vlan_id);
					nodesByIp.get(compute_node_ip).used_local_vNetsByGuid.remove(network_id);
					nodesByIp.get(compute_node_ip).local_vNetidToVlanid.remove(network_id);
				}
			}
		}
	}
	
//	public void syncTunnel() {
//		if(!nodesByIp.isEmpty()) {
//			for(Entry<String, NodeDefinition> entry : nodesByIp.entrySet()) {
//				if(entry.getValue().flow_sync) {
//					if(nodesByIp.get(entry.getKey()).local_vlan_ofports != null) {
//						String ofports = "";
//						for(int i = 0 ; i < nodesByIp.get(entry.getKey()).local_vlan_ofports.size() ; i++) {
//							if(i == 0) {
//								ofports = nodesByIp.get(entry.getKey()).local_vlan_ofports.get(i);
//							} else {
//								ofports += "," + nodesByIp.get(entry.getKey()).local_vlan_ofports.get(i);
//							}
//						}
//						
//						if(nodesByIp.get(entry.getKey()).used_local_vNetsByVlanid != null && !nodesByIp.get(entry.getKey()).used_local_vNetsByVlanid.isEmpty()) {
//							for(Entry<String, NetworkDefinition> vlanMap : nodesByIp.get(entry.getKey()).used_local_vNetsByVlanid.entrySet()) {
//								String network_id = vlanMap.getValue().netId;
//								String tun_id = "0x"+Integer.toHexString(Integer.parseInt(vlanMap.getValue().provider_segmentation_id)).toString();
//								String vlan_id = vlanMap.getKey().toString();
//								
//								if("".equals(ofports)) {
//									logger.debug("delFlow(syncTunnel) : IP - {}, FLOW - {}", entry.getKey(), "sudo ovs-ofctl del-flows "+TUNNELING_BRIDGE_NAME+" table="+FLOOD_TO_TUN+",dl_vlan="+vlan_id);
//									rest_post(entry.getKey(), INAAS_AGENT_REST_PORT, "sudo ovs-ofctl del-flows "+TUNNELING_BRIDGE_NAME+" table="+FLOOD_TO_TUN+",dl_vlan="+vlan_id);
//									logger.debug("delFlow(syncTunnel) : IP - {}, FLOW - {}", entry.getKey(), "sudo ovs-ofctl del-flows "+TUNNELING_BRIDGE_NAME+" table="+VXLAN_TUN_TO_LV+",tun_id="+tun_id);
//									rest_post(entry.getKey(), INAAS_AGENT_REST_PORT, "sudo ovs-ofctl del-flows "+TUNNELING_BRIDGE_NAME+" table="+VXLAN_TUN_TO_LV+",tun_id="+tun_id);
//									
//									nodesByIp.get(entry.getKey()).available_local_vlans.add(Integer.parseInt(vlan_id));
//									nodesByIp.get(entry.getKey()).used_local_vNetsByVlanid.remove(vlan_id);
//									nodesByIp.get(entry.getKey()).used_local_vNetsByGuid.remove(network_id);
//									nodesByIp.get(entry.getKey()).local_vNetidToVlanid.remove(network_id);
//								} else {
//									rest_post(entry.getKey(), INAAS_AGENT_REST_PORT, "sudo ovs-ofctl add-flow "+TUNNELING_BRIDGE_NAME+" table="+FLOOD_TO_TUN+",dl_vlan="+vlan_id+",actions=strip_vlan,set_tunnel:"+tun_id+",output:"+ofports);
//								}
//							}
//						}
//						nodesByIp.get(entry.getKey()).flow_sync = false;
//					}
//				}
//				
//					
//				if(entry.getValue().tag_sync) {
//					// sync tag setting
//					if(nodesByIp.get(entry.getKey()).used_local_vPortsByGuid != null && !nodesByIp.get(entry.getKey()).used_local_vPortsByGuid.isEmpty()) {
//						int vPortsCnt = nodesByIp.get(entry.getKey()).used_local_vPortsByGuid.size();
//						int successCnt = 0;
//						
//						for(Entry<String, PortDefinition> localPortMap : nodesByIp.get(entry.getKey()).used_local_vPortsByGuid.entrySet()) {
//							String tag = "";
//							if(nodesByIp.get(entry.getKey()).local_vNetidToVlanid != null && localPortMap.getValue().network_id != null) {
//								if(nodesByIp.get(entry.getKey()).local_vNetidToVlanid.containsKey(localPortMap.getValue().network_id)) {
//									tag = nodesByIp.get(entry.getKey()).local_vNetidToVlanid.get(localPortMap.getValue().network_id);
//								}
//							}
//							
//							if(localPortMap.getValue().portId != null && !"".equals(localPortMap.getValue().portId) && !"".equals(tag)) {
//								try {
//									for(String readPortName : rest_get(entry.getKey(), INAAS_AGENT_REST_PORT, "sudo ovs-vsctl list-ports "+INTEGRATION_BRIDGE_NAME)) {
//										if(localPortMap.getValue().portId.substring(0,11).equals(readPortName.substring(3))) {
//											rest_post(entry.getKey(), INAAS_AGENT_REST_PORT, "sudo ovs-vsctl set Port "+readPortName+" tag="+tag);
//											
//											successCnt++;
//										}
//									}
//								} catch(Exception e) {
//									logger.error("Unable to sync tag. \n Exception: {}", e.getMessage());
//								}
//							}
//						}
//						
//						if(vPortsCnt == successCnt) {
//							nodesByIp.get(entry.getKey()).tag_sync = false;
//						}
//					}
//				}
//			}
//		}
//	}
	
	public void ofports_flow(String node_ip) {
		try {
			if(nodesByIp.get(node_ip).local_vlan_ofports != null) {
				String ofports = "";
				for(int i = 0 ; i < nodesByIp.get(node_ip).local_vlan_ofports.size() ; i++) {
					if(i == 0) {
						ofports = nodesByIp.get(node_ip).local_vlan_ofports.get(i);
					} else {
						ofports += "," + nodesByIp.get(node_ip).local_vlan_ofports.get(i);
					}
				}
				
				if(nodesByIp.get(node_ip).used_local_vNetsByVlanid != null && !nodesByIp.get(node_ip).used_local_vNetsByVlanid.isEmpty()) {
					for(Entry<String, NetworkDefinition> vlanMap : nodesByIp.get(node_ip).used_local_vNetsByVlanid.entrySet()) {
						String network_id = vlanMap.getValue().netId;
						String tun_id = "0x"+Integer.toHexString(Integer.parseInt(vlanMap.getValue().provider_segmentation_id)).toString();
						String vlan_id = vlanMap.getKey().toString();
						
						if("".equals(ofports)) {
							logger.debug("delFlow(syncTunnel) : IP - {}, FLOW - {}", node_ip, "sudo ovs-ofctl del-flows "+TUNNELING_BRIDGE_NAME+" table="+FLOOD_TO_TUN+",dl_vlan="+vlan_id);
							rest_post(node_ip, INAAS_AGENT_REST_PORT, "sudo ovs-ofctl del-flows "+TUNNELING_BRIDGE_NAME+" table="+FLOOD_TO_TUN+",dl_vlan="+vlan_id);
							logger.debug("delFlow(syncTunnel) : IP - {}, FLOW - {}", node_ip, "sudo ovs-ofctl del-flows "+TUNNELING_BRIDGE_NAME+" table="+VXLAN_TUN_TO_LV+",tun_id="+tun_id);
							rest_post(node_ip, INAAS_AGENT_REST_PORT, "sudo ovs-ofctl del-flows "+TUNNELING_BRIDGE_NAME+" table="+VXLAN_TUN_TO_LV+",tun_id="+tun_id);
							
							nodesByIp.get(node_ip).available_local_vlans.add(Integer.parseInt(vlan_id));
							nodesByIp.get(node_ip).used_local_vNetsByVlanid.remove(vlan_id);
							nodesByIp.get(node_ip).used_local_vNetsByGuid.remove(network_id);
							nodesByIp.get(node_ip).local_vNetidToVlanid.remove(network_id);
						} else {
							rest_post(node_ip, INAAS_AGENT_REST_PORT, "sudo ovs-ofctl add-flow "+TUNNELING_BRIDGE_NAME+" table="+FLOOD_TO_TUN+",dl_vlan="+vlan_id+",actions=strip_vlan,set_tunnel:"+tun_id+",output:"+ofports);
						}
					}
				}
			}
		} catch(Exception e) {
			logger.error("node({}) : ofports_flow setting error. \n Exception: {}", node_ip, e.getMessage());
		}
	}
	
	public void port_tagging(String node_ip, String port_id, String network_id) {
		Thread t = new PortTagging(node_ip, port_id, network_id);
		t.start();
	}
	
	public class PortTagging extends Thread {
		String node_ip;
		String port_id;
		String network_id;
		
		public PortTagging(String node_ip, String port_id, String network_id) {
			this.node_ip = node_ip;
			this.port_id = port_id;
			this.network_id = network_id;
		}
		
		public void run() {
			try {
				String tag = "";
				if(nodesByIp.get(this.node_ip).local_vNetidToVlanid != null && !"".equals(this.network_id)) {
					if(nodesByIp.get(this.node_ip).local_vNetidToVlanid.containsKey(this.network_id)) {
						tag = nodesByIp.get(this.node_ip).local_vNetidToVlanid.get(this.network_id);
					}
				}

				int repeatTime = 10;
				boolean repeat = true;
				
				for(int i = 0; i < repeatTime; i++) {
					for(String readPortName : rest_get(this.node_ip, INAAS_AGENT_REST_PORT, "sudo ovs-vsctl list-ports "+INTEGRATION_BRIDGE_NAME)) {
						if((this.port_id).substring(0,11).equals(readPortName.substring(3))) {
							rest_post(this.node_ip, INAAS_AGENT_REST_PORT, "sudo ovs-vsctl set Port "+readPortName+" tag="+tag);
							repeat = false;
						}
					}
					
					if(!repeat) {
						break;
					} else {
						if(i == (repeatTime - 1)) {
							logger.debug("{} does not exist.", this.port_id);
							openstackML2Connector.deletePort(this.port_id);
						} else {
							Thread.sleep(1000);
						}
					}
				}
			} catch(Exception e) {
				logger.error("Unable to sync tag. \n Exception: {}", e.getMessage());
			}
		}
	}
	
	public void delTunnel() {
		Date now = new Date(System.currentTimeMillis()); 
		SimpleDateFormat simpledateformat = new SimpleDateFormat("yyyyMMddHHmm");
		String current_time = simpledateformat.format(now);
		
		try {
			if(!nodesByIp.isEmpty()) {
				for(Entry<String, NodeDefinition> entry : nodesByIp.entrySet()) {
					String existing_time = entry.getValue().current_time;
					Long time_gap = (simpledateformat.parse(current_time).getTime() - simpledateformat.parse(existing_time).getTime()) / 60000;
					
//System.out.println("========================================");
//System.out.println(">>> IP : " + entry.getKey());
//System.out.println(">>> existing_time : " + existing_time);
//System.out.println(">>> current_time : " + current_time);
//System.out.println(">>> time_gap : " + time_gap);
//System.out.println("========================================");
					logger.debug("checked Tunnel IP {} existing time {} current time {} time gap {}", entry.getKey(), existing_time, current_time, time_gap);
					if(time_gap >= DELETE_TIME_GAP) {
						String delTunName = TUNNEL_TYPE + "-" + HexString.toHexString(InetAddress.getByName(entry.getValue().node_ip_tun).getAddress()).replaceAll(":", "");
						
						nodesByIp.remove(entry.getKey());
						intDpidByIp.remove(entry.getKey());
						
						for(Entry<Long, String> swEntry : nodeIpByDpid.entrySet()) {
							if(entry.getKey().equals(swEntry.getValue())) {
								nodeIpByDpid.remove(swEntry.getKey());
							}
						}
						
						for(Entry<String, NodeDefinition> deleteEntry : nodesByIp.entrySet()) {
							String in_port = rest_get(deleteEntry.getKey(), INAAS_AGENT_REST_PORT, "sudo ovs-vsctl get Interface "+delTunName+" ofport").get(0);
							
							logger.debug("delTunnelPort : {}", delTunName);
							rest_post(deleteEntry.getKey(), INAAS_AGENT_REST_PORT, "sudo ovs-vsctl del-port "+TUNNELING_BRIDGE_NAME+" "+delTunName);
							
							logger.debug("delFlow(delTunnel) : IP - {}, FLOW - {}", deleteEntry.getKey(), "sudo ovs-ofctl del-flows "+TUNNELING_BRIDGE_NAME+" in_port="+in_port);
							rest_post(deleteEntry.getKey(), INAAS_AGENT_REST_PORT, "sudo ovs-ofctl del-flows "+TUNNELING_BRIDGE_NAME+" in_port="+in_port);
							
							if(nodesByIp.get(deleteEntry.getKey()).local_vlan_ofports != null) {
								nodesByIp.get(deleteEntry.getKey()).local_vlan_ofports.remove(in_port);
							}
							
//							nodesByIp.get(deleteEntry.getKey()).flow_sync = true;
							ofports_flow(deleteEntry.getKey());
						}
					}
				} 
			}
		} catch(Exception e) {
			logger.error("Unable to delete tunnel port. {}", e.getMessage());
		}
	}
	
	@Override
	public Map<Long, String> getBridgeDpid() {
		return nodeIpByDpid;
	}
	
	@Override
	public Map<String, NodeDefinition> getNodeInfo() {
		return nodesByIp;
	}
	
	@Override
	public Map<String, PortDefinition> getVmByGuid() {
		return vmByGuid;
	}
	
	@Override
	public Map<String, PortDefinition> getVmByIp() {
		Map<String, PortDefinition> vmByIp = new ConcurrentHashMap<String, PortDefinition>();
		
		for(Entry<String, PortDefinition> vmEntry : vmByGuid.entrySet()) {
			if(vmEntry.getValue().fixed_ips.size() > 0) {
				String vm_ip = vmEntry.getValue().fixed_ips.get(0).get("ip_address");
				
				vmByIp.put(vm_ip, vmEntry.getValue());
			}
		}
		
		return vmByIp;
	}
	
	@Override
	public String getHostName(String host_ip) {
		String host_name = host_ip;
		
		for(Entry<String, NodeDefinition> nodeEntry : nodesByIp.entrySet()) {
			if(host_ip.equals(nodeEntry.getValue().node_ip_tun)) {
				host_name = nodeEntry.getValue().node_name;
			}
		}
		
		return host_name;
	}
}