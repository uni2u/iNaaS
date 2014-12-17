package etri.sdn.controller.module.tunnelmanager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
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
import etri.sdn.controller.module.ml2.RestNetwork.NetworkDefinition;
import etri.sdn.controller.module.ml2.RestPort.PortDefinition;
import etri.sdn.controller.module.routing.IRoutingDecision;
import etri.sdn.controller.protocol.io.Connection;
import etri.sdn.controller.protocol.packet.Ethernet;

public class OFMTunnelManager extends OFModule implements IOFMTunnelManagerService {
	
	public static final Logger logger = LoggerFactory.getLogger(OFMTunnelManager.class);
	
	public class NodeDefinition {
		public boolean flow_sync = true;
		public boolean tag_sync = true;
		public String node_ip = null;
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
	
	public static final String TUNNEL_TYPE = "vxlan";
	
	private static final String OVSDB_SERVER_REMOTE_PORT = "6640";
	private static final String INTEGRATION_BRIDGE_NAME = "br-int";
	private static final String TUNNELING_BRIDGE_NAME = "br-tun";
	private static final String INT_PEER_PATCH_PORT = "patch-tun";
	private static final String TUN_PEER_PATCH_PORT = "patch-int";
	private static final int DELETE_TIME_GAP = 3;
	
	private static final int MIN_VLAN_TAG = 1;
	private static final int MAX_VLAN_TAG = 4094;
	
	protected static Map<String, NodeDefinition> nodesByIp;
	protected static Map<String, Long> intDpidByIp;
	protected static Map<String, NetworkDefinition> vNetsByGuid;	// List of all created virtual networks
	protected static Map<String, PortDefinition> vPortsByGuid;	// List of all created virtual networks
	
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
		nodesByIp = new ConcurrentHashMap<String, NodeDefinition>();
		intDpidByIp = new ConcurrentHashMap<String, Long>();
		vNetsByGuid = new ConcurrentHashMap<String, NetworkDefinition>();
		vPortsByGuid = new ConcurrentHashMap<String, PortDefinition>();
		
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
		
		this.controller.scheduleTask(
				new IOFTask() {
					@Override
					public boolean execute() {
						syncTunnel();
						return true;
					}
				}, 
				0,
				5 * 1000 /* milliseconds */
				);
		
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
//System.out.println("PACKET_IN : " + conn.getSwitch().getId());
		
		if(intDpidByIp.containsValue(conn.getSwitch().getId())) {
			OFPacketIn pi = (OFPacketIn) msg; 
			
			OFFactory fac = OFFactories.getFactory(pi.getVersion());
			OFFlowMod.Builder fm = fac.buildFlowAdd();
			
			fm.setHardTimeout(0);
			fm.setIdleTimeout(5);
			fm.setPriority(1);
			
			Ethernet etherPacket = new Ethernet();
			etherPacket.deserialize(pi.getData(), 0, pi.getData().length);
			EthType etherType = null;
			etherType = EthType.of(etherPacket.getEtherType());
			
			Match.Builder match = fac.buildMatch();
			match.setExact(MatchField.IN_PORT, pi.getInPort());
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
		}
		
		return true;
	}
	
	@Override
	public void addTunnel(String node_ip, String node_name, String node_type, String iris_ip) {
		Date now = new Date(System.currentTimeMillis());
		SimpleDateFormat simpledateformat = new SimpleDateFormat("yyyyMMddHHmm");
		String current_time = simpledateformat.format(now);
		
		String local_ip = node_ip;
		String remote_ip = "";
		
		try {
			if(!nodesByIp.containsKey(local_ip)) {
				ArrayList<Integer> init_vlans = new ArrayList<Integer>();
				for (int i = MIN_VLAN_TAG ; i <= MAX_VLAN_TAG ; i++) {
					init_vlans.add(i);
				}
				
				NodeDefinition nodeInfo = new NodeDefinition();
				nodeInfo.node_ip = local_ip;
				nodeInfo.node_name = node_name;
				nodeInfo.node_type = node_type;
				nodeInfo.current_time = current_time;
				nodeInfo.available_local_vlans = init_vlans;
				
				nodesByIp.put(local_ip, nodeInfo);
				
				setup_bridge(local_ip, OVSDB_SERVER_REMOTE_PORT, iris_ip);
				
//System.out.println("intDpidByIp >>> " + local_ip + " : " + TunnelOvs.get_sw_dpid(local_ip, OVSDB_SERVER_REMOTE_PORT, INTEGRATION_BRIDGE_NAME));
//				intDpidByIp.put(local_ip, TunnelOvs.get_sw_dpid(local_ip, OVSDB_SERVER_REMOTE_PORT, INTEGRATION_BRIDGE_NAME));
				
				// tunnel create ( new node <--> exist node )
				if(!nodesByIp.isEmpty() && nodesByIp.size() > 1) {
					for(Entry<String, NodeDefinition> entry : nodesByIp.entrySet()) {
						remote_ip = entry.getKey();
						
						if(!remote_ip.equals(local_ip)) {
							setup_tunnel_port(local_ip,
									OVSDB_SERVER_REMOTE_PORT,
									TUNNELING_BRIDGE_NAME,
									TUNNEL_TYPE + "-" + HexString.toHexString(InetAddress.getByName(remote_ip).getAddress()).replaceAll(":", ""),
									remote_ip);
							
							setup_tunnel_port(remote_ip,
									OVSDB_SERVER_REMOTE_PORT,
									TUNNELING_BRIDGE_NAME,
									TUNNEL_TYPE + "-" + HexString.toHexString(InetAddress.getByName(local_ip).getAddress()).replaceAll(":", ""),
									local_ip);
						}
					}
				}
			}
			
			nodesByIp.get(local_ip).current_time = current_time;
			nodesByIp.get(local_ip).node_name = node_name;
			nodesByIp.get(local_ip).node_type = node_type;
		} catch (Exception e) {
			logger.error("Unable to create tunnel port. {}", e.getMessage());
		}
	}
	
	public void setup_bridge(String ovsdb_server_remote_ip, String ovsdb_server_remote_port, String iris_ip) {
		try {
			TunnelOvs.delete_bridge(ovsdb_server_remote_ip, ovsdb_server_remote_port, INTEGRATION_BRIDGE_NAME);
			Thread.sleep(500);
			TunnelOvs.delete_bridge(ovsdb_server_remote_ip, ovsdb_server_remote_port, TUNNELING_BRIDGE_NAME);
			Thread.sleep(500);
			
			TunnelOvs.add_bridge(ovsdb_server_remote_ip, ovsdb_server_remote_port, INTEGRATION_BRIDGE_NAME);
			Thread.sleep(500);
			TunnelOvs.set_secure_mode(ovsdb_server_remote_ip, ovsdb_server_remote_port, INTEGRATION_BRIDGE_NAME);
			Thread.sleep(500);
			TunnelOvs.add_bridge(ovsdb_server_remote_ip, ovsdb_server_remote_port, TUNNELING_BRIDGE_NAME);
			Thread.sleep(500);
			intDpidByIp.put(ovsdb_server_remote_ip, TunnelOvs.get_sw_dpid(ovsdb_server_remote_ip, ovsdb_server_remote_port, INTEGRATION_BRIDGE_NAME));
			
			TunnelOvs.add_patch_port(ovsdb_server_remote_ip, ovsdb_server_remote_port, INTEGRATION_BRIDGE_NAME, INT_PEER_PATCH_PORT, TUN_PEER_PATCH_PORT);
			Thread.sleep(500);
			String patch_int_ofport = TunnelOvs.add_patch_port(ovsdb_server_remote_ip, ovsdb_server_remote_port, TUNNELING_BRIDGE_NAME, TUN_PEER_PATCH_PORT, INT_PEER_PATCH_PORT);
			Thread.sleep(500);
			
			TunnelOvs.connController(ovsdb_server_remote_ip, ovsdb_server_remote_port, iris_ip, INTEGRATION_BRIDGE_NAME);
			Thread.sleep(500);
			TunnelOvs.connController(ovsdb_server_remote_ip, ovsdb_server_remote_port, iris_ip, TUNNELING_BRIDGE_NAME);
			Thread.sleep(500);
			
			run_cmd_rest(ovsdb_server_remote_ip, "8000", "sudo ovs-ofctl del-flows "+INTEGRATION_BRIDGE_NAME);
			Thread.sleep(500);
			run_cmd_rest(ovsdb_server_remote_ip, "8000", "sudo ovs-ofctl del-flows "+TUNNELING_BRIDGE_NAME);
			Thread.sleep(500);
			
	        run_cmd_rest(ovsdb_server_remote_ip, "8000", "sudo ovs-ofctl add-flow "+INTEGRATION_BRIDGE_NAME+" hard_timeout=0,idle_timeout=0,priority=100,dl_type=0x88cc,actions=CONTROLLER");
	        Thread.sleep(500);
//	        run_cmd_rest(ovsdb_server_remote_ip, "8000", "sudo ovs-ofctl add-flow "+INTEGRATION_BRIDGE_NAME+" hard_timeout=0,idle_timeout=0,priority=1,actions=normal");
	        run_cmd_rest(ovsdb_server_remote_ip, "8000", "sudo ovs-ofctl add-flow "+INTEGRATION_BRIDGE_NAME+" hard_timeout=0,idle_timeout=0,table="+TunnelFlow.CANARY_TABLE+",priority=0,actions=drop");
	        Thread.sleep(500);
	        
			run_cmd_rest(ovsdb_server_remote_ip, "8000", "sudo ovs-ofctl add-flow "+TUNNELING_BRIDGE_NAME+" hard_timeout=0,idle_timeout=0,priority=100,dl_type=0x88cc,actions=CONTROLLER");
			Thread.sleep(500);
			run_cmd_rest(ovsdb_server_remote_ip, "8000", "sudo ovs-ofctl add-flow "+TUNNELING_BRIDGE_NAME+" hard_timeout=0,idle_timeout=0,priority=1,in_port="+patch_int_ofport+",actions=resubmit(,"+TunnelFlow.PATCH_LV_TO_TUN+")");
			Thread.sleep(500);
			run_cmd_rest(ovsdb_server_remote_ip, "8000", "sudo ovs-ofctl add-flow "+TUNNELING_BRIDGE_NAME+" hard_timeout=0,idle_timeout=0,priority=0,actions=drop");
			Thread.sleep(500);
			run_cmd_rest(ovsdb_server_remote_ip, "8000", "sudo ovs-ofctl add-flow "+TUNNELING_BRIDGE_NAME+" hard_timeout=0,idle_timeout=0,table="+TunnelFlow.PATCH_LV_TO_TUN+",priority=1,dl_dst=00:00:00:00:00:00/01:00:00:00:00:00,actions=resubmit(,"+TunnelFlow.UCAST_TO_TUN+")");
			Thread.sleep(500);
			run_cmd_rest(ovsdb_server_remote_ip, "8000", "sudo ovs-ofctl add-flow "+TUNNELING_BRIDGE_NAME+" hard_timeout=0,idle_timeout=0,table="+TunnelFlow.PATCH_LV_TO_TUN+",priority=1,dl_dst=01:00:00:00:00:00/01:00:00:00:00:00,actions=resubmit(,"+TunnelFlow.FLOOD_TO_TUN+")");
			Thread.sleep(500);
			if("gre".equals(TUNNEL_TYPE)) {
				run_cmd_rest(ovsdb_server_remote_ip, "8000", "sudo ovs-ofctl add-flow "+TUNNELING_BRIDGE_NAME+" hard_timeout=0,idle_timeout=0,table="+TunnelFlow.GRE_TUN_TO_LV+",priority=0,actions=drop");
				Thread.sleep(500);
			} else if("vxlan".equals(TUNNEL_TYPE)) {
				run_cmd_rest(ovsdb_server_remote_ip, "8000", "sudo ovs-ofctl add-flow "+TUNNELING_BRIDGE_NAME+" hard_timeout=0,idle_timeout=0,table="+TunnelFlow.VXLAN_TUN_TO_LV+",priority=0,actions=drop");
				Thread.sleep(500);
			}
			run_cmd_rest(ovsdb_server_remote_ip, "8000", "sudo ovs-ofctl add-flow "+TUNNELING_BRIDGE_NAME+" hard_timeout=0,idle_timeout=0,table="+TunnelFlow.LEARN_FROM_TUN+",priority=1,actions=learn(table="+TunnelFlow.UCAST_TO_TUN+",priority=1,hard_timeout=300,NXM_OF_VLAN_TCI[0..11],NXM_OF_ETH_DST[]=NXM_OF_ETH_SRC[],load:0->NXM_OF_VLAN_TCI[],load:NXM_NX_TUN_ID[]->NXM_NX_TUN_ID[],output:NXM_OF_IN_PORT[]),output:"+patch_int_ofport);
			Thread.sleep(500);
			run_cmd_rest(ovsdb_server_remote_ip, "8000", "sudo ovs-ofctl add-flow "+TUNNELING_BRIDGE_NAME+" hard_timeout=0,idle_timeout=0,table="+TunnelFlow.UCAST_TO_TUN+",priority=0,actions=resubmit(,"+TunnelFlow.FLOOD_TO_TUN+")");
			Thread.sleep(500);
			run_cmd_rest(ovsdb_server_remote_ip, "8000", "sudo ovs-ofctl add-flow "+TUNNELING_BRIDGE_NAME+" hard_timeout=0,idle_timeout=0,table="+TunnelFlow.FLOOD_TO_TUN+",priority=0,actions=drop");
		} catch(Exception e) {
			logger.error("Unable to setup_bridge. Exception:  {}", e.getMessage());
		}
	}
	
	public void setup_tunnel_port(String ovsdb_server_remote_ip, String ovsdb_server_remote_port, String bridge_name, String port_name, String remote_ip) {
		String ofport = TunnelOvs.add_tunnel_port(ovsdb_server_remote_ip, ovsdb_server_remote_port, bridge_name, port_name, remote_ip);
		
		run_cmd_rest(ovsdb_server_remote_ip, "8000", "sudo ovs-ofctl add-flow "+TUNNELING_BRIDGE_NAME+" hard_timeout=0,idle_timeout=0,priority=1,in_port="+ofport+",actions=resubmit(,"+TunnelFlow.VXLAN_TUN_TO_LV+")");
		
		if(nodesByIp.get(ovsdb_server_remote_ip).local_vlan_ofports == null) {
			nodesByIp.get(ovsdb_server_remote_ip).local_vlan_ofports = new ArrayList<String>();
		}
		nodesByIp.get(ovsdb_server_remote_ip).local_vlan_ofports.add(ofport);
		
		// change sync true
		nodesByIp.get(ovsdb_server_remote_ip).flow_sync = true;
	}
	
	public void run_cmd_rest(String node_ip, String node_port, String command) {
		logger.debug("ofctl_command({}) : {}", node_ip, command);
		
		try {
			String restUri = "http://" + node_ip + ":" + node_port + "/wm/tunnel/flow";
			
			ClientResource resource = new ClientResource(restUri);
			resource.setMethod(Method.POST);
			resource.getReference().addQueryParameter("format", "json");
			
			StringRepresentation stringRep = new StringRepresentation(command);
			stringRep.setMediaType(MediaType.APPLICATION_JSON);
			
			resource.post(stringRep).write(System.out);
		} catch(IOException e) {
			e.printStackTrace();
		}
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

				run_cmd_rest(network_node_ip, "8000", "sudo ovs-ofctl add-flow "+TUNNELING_BRIDGE_NAME+" hard_timeout=0,idle_timeout=0,table="+TunnelFlow.VXLAN_TUN_TO_LV+",priority=1,tun_id="+tun_id+",actions=mod_vlan_vid:"+mod_vlan_vid+",resubmit(,"+TunnelFlow.LEARN_FROM_TUN+")");
				
				// change sync true
				nodesByIp.get(network_node_ip).flow_sync = true;
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
					
					logger.debug("delFlow(delete_network_flow) : IP - {}, FLOW - {}", entry.getKey(), "sudo ovs-ofctl del-flows "+TUNNELING_BRIDGE_NAME+" table="+TunnelFlow.FLOOD_TO_TUN+",dl_vlan="+vlan_id);
					run_cmd_rest(entry.getKey(), "8000", "sudo ovs-ofctl del-flows "+TUNNELING_BRIDGE_NAME+" table="+TunnelFlow.FLOOD_TO_TUN+",dl_vlan="+vlan_id);
					logger.debug("delFlow(delete_network_flow) : IP - {}, FLOW - {}", entry.getKey(), "sudo ovs-ofctl del-flows "+TUNNELING_BRIDGE_NAME+" table="+TunnelFlow.VXLAN_TUN_TO_LV+",tun_id="+tun_id);
					run_cmd_rest(entry.getKey(), "8000", "sudo ovs-ofctl del-flows "+TUNNELING_BRIDGE_NAME+" table="+TunnelFlow.VXLAN_TUN_TO_LV+",tun_id="+tun_id);
					
					nodesByIp.get(entry.getKey()).available_local_vlans.add(Integer.parseInt(vlan_id));
					nodesByIp.get(entry.getKey()).used_local_vNetsByVlanid.remove(vlan_id);
					nodesByIp.get(entry.getKey()).used_local_vNetsByGuid.remove(network_id);
					nodesByIp.get(entry.getKey()).local_vNetidToVlanid.remove(network_id);
				}
			}
		}
	}
	
	public void create_port_flow(PortDefinition port) {
		vPortsByGuid.put(port.porId, port);
		
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
				nodesByIp.get(network_node_ip).used_local_vPortsByGuid.put(port.porId, port);
				
				// change sync true
				nodesByIp.get(network_node_ip).tag_sync = true;
			}
		} else if("compute:nova".equals(port.device_owner)) {
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
	
						run_cmd_rest(compute_node_ip, "8000", "sudo ovs-ofctl add-flow "+TUNNELING_BRIDGE_NAME+" hard_timeout=0,idle_timeout=0,table="+TunnelFlow.VXLAN_TUN_TO_LV+",priority=1,tun_id="+tun_id+",actions=mod_vlan_vid:"+mod_vlan_vid+",resubmit(,"+TunnelFlow.LEARN_FROM_TUN+")");
					}
					
					if(nodesByIp.get(compute_node_ip).used_local_vPortsByGuid == null) {
						nodesByIp.get(compute_node_ip).used_local_vPortsByGuid = new ConcurrentHashMap<String, PortDefinition>();
					}
					nodesByIp.get(compute_node_ip).used_local_vPortsByGuid.put(port.porId, port);
					
					// change sync true
					nodesByIp.get(compute_node_ip).flow_sync = true;
					nodesByIp.get(compute_node_ip).tag_sync = true;
				} catch(Exception e) {
					logger.error("Unable to create_port_flow. Exception : {}", e.getMessage());
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
		} else if("compute:nova".equals(device_owner)) {
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
				}
				
				if(nodesByIp.get(compute_node_ip).used_local_vPortsByGuid == null || nodesByIp.get(compute_node_ip).used_local_vPortsByGuid.isEmpty()) {
					logger.debug("delFlow(delete_port_flow) : IP - {}, FLOW - {}", compute_node_ip, "sudo ovs-ofctl del-flows "+TUNNELING_BRIDGE_NAME+" table="+TunnelFlow.FLOOD_TO_TUN+",dl_vlan="+vlan_id);
					run_cmd_rest(compute_node_ip, "8000", "sudo ovs-ofctl del-flows "+TUNNELING_BRIDGE_NAME+" table="+TunnelFlow.FLOOD_TO_TUN+",dl_vlan="+vlan_id);
					logger.debug("delFlow(delete_port_flow) : IP - {}, FLOW - {}", compute_node_ip, "sudo ovs-ofctl del-flows "+TUNNELING_BRIDGE_NAME+" table="+TunnelFlow.VXLAN_TUN_TO_LV+",tun_id="+tun_id);
					run_cmd_rest(compute_node_ip, "8000", "sudo ovs-ofctl del-flows "+TUNNELING_BRIDGE_NAME+" table="+TunnelFlow.VXLAN_TUN_TO_LV+",tun_id="+tun_id);
					
					nodesByIp.get(compute_node_ip).available_local_vlans.add(Integer.parseInt(vlan_id));
					nodesByIp.get(compute_node_ip).used_local_vNetsByVlanid.remove(vlan_id);
					nodesByIp.get(compute_node_ip).used_local_vNetsByGuid.remove(network_id);
					nodesByIp.get(compute_node_ip).local_vNetidToVlanid.remove(network_id);
				}
			}
		}
	}
	
	public void syncTunnel() {
		if(!nodesByIp.isEmpty()) {
			for(Entry<String, NodeDefinition> entry : nodesByIp.entrySet()) {
				if(entry.getValue().flow_sync) {
					if(nodesByIp.get(entry.getKey()).local_vlan_ofports != null) {
						// sync mod_flow(table=FLOOD_TO_TUN, dl_vlan=vlan_mapping.vlan, actions="strip_vlan," "set_tunnel:%s,output:%s" % (vlan_mapping.segmentation_id, ofports));
						String ofports = "";
						for(int i = 0 ; i < nodesByIp.get(entry.getKey()).local_vlan_ofports.size() ; i++) {
							if(i == 0) {
								ofports = nodesByIp.get(entry.getKey()).local_vlan_ofports.get(i);
							} else {
								ofports += "," + nodesByIp.get(entry.getKey()).local_vlan_ofports.get(i);
							}
						}
						
						if(nodesByIp.get(entry.getKey()).used_local_vNetsByVlanid != null && !nodesByIp.get(entry.getKey()).used_local_vNetsByVlanid.isEmpty()) {
							for(Entry<String, NetworkDefinition> vlanMap : nodesByIp.get(entry.getKey()).used_local_vNetsByVlanid.entrySet()) {
								String network_id = vlanMap.getValue().netId;
								String tun_id = "0x"+Integer.toHexString(Integer.parseInt(vlanMap.getValue().provider_segmentation_id)).toString();
								String vlan_id = vlanMap.getKey().toString();
								
								if("".equals(ofports)) {
									logger.debug("delFlow(syncTunnel) : IP - {}, FLOW - {}", entry.getKey(), "sudo ovs-ofctl del-flows "+TUNNELING_BRIDGE_NAME+" table="+TunnelFlow.FLOOD_TO_TUN+",dl_vlan="+vlan_id);
									run_cmd_rest(entry.getKey(), "8000", "sudo ovs-ofctl del-flows "+TUNNELING_BRIDGE_NAME+" table="+TunnelFlow.FLOOD_TO_TUN+",dl_vlan="+vlan_id);
									logger.debug("delFlow(syncTunnel) : IP - {}, FLOW - {}", entry.getKey(), "sudo ovs-ofctl del-flows "+TUNNELING_BRIDGE_NAME+" table="+TunnelFlow.VXLAN_TUN_TO_LV+",tun_id="+tun_id);
									run_cmd_rest(entry.getKey(), "8000", "sudo ovs-ofctl del-flows "+TUNNELING_BRIDGE_NAME+" table="+TunnelFlow.VXLAN_TUN_TO_LV+",tun_id="+tun_id);
									
									nodesByIp.get(entry.getKey()).available_local_vlans.add(Integer.parseInt(vlan_id));
									nodesByIp.get(entry.getKey()).used_local_vNetsByVlanid.remove(vlan_id);
									nodesByIp.get(entry.getKey()).used_local_vNetsByGuid.remove(network_id);
									nodesByIp.get(entry.getKey()).local_vNetidToVlanid.remove(network_id);
								} else {
									run_cmd_rest(entry.getKey(), "8000", "sudo ovs-ofctl add-flow "+TUNNELING_BRIDGE_NAME+" table="+TunnelFlow.FLOOD_TO_TUN+",dl_vlan="+vlan_id+",actions=strip_vlan,set_tunnel:"+tun_id+",output:"+ofports);
								}
							}
						}
						nodesByIp.get(entry.getKey()).flow_sync = false;
					}
				}
				
					
				if(entry.getValue().tag_sync) {
					// sync tag setting
					if(nodesByIp.get(entry.getKey()).used_local_vPortsByGuid != null && !nodesByIp.get(entry.getKey()).used_local_vPortsByGuid.isEmpty()) {
						int vPortsCnt = nodesByIp.get(entry.getKey()).used_local_vPortsByGuid.size();
						int successCnt = 0;
						
						for(Entry<String, PortDefinition> localPortMap : nodesByIp.get(entry.getKey()).used_local_vPortsByGuid.entrySet()) {
							String port_name = "";
							String tag = "";
							if(nodesByIp.get(entry.getKey()).local_vNetidToVlanid != null) {
								if(nodesByIp.get(entry.getKey()).local_vNetidToVlanid.containsKey(localPortMap.getValue().network_id)) {
									tag = nodesByIp.get(entry.getKey()).local_vNetidToVlanid.get(localPortMap.getValue().network_id);
								}
							}
							
							if("compute:nova".equals(localPortMap.getValue().device_owner) || "network:dhcp".equals(localPortMap.getValue().device_owner)) {
								port_name = "tap" + localPortMap.getValue().porId.substring(0,11);
							} else if("network:router_interface".equals(localPortMap.getValue().device_owner)) {
								port_name = "qr-" + localPortMap.getValue().porId.substring(0,11);
							}
							
							if(!"".equals(port_name) && !"".equals(tag)) {
								try {
									Process getTagListProcess = Runtime.getRuntime().exec("sudo ovs-vsctl --db=tcp:"+entry.getKey()+":"+OVSDB_SERVER_REMOTE_PORT +" list-ports "+INTEGRATION_BRIDGE_NAME);
									getTagListProcess.waitFor();
									
									BufferedReader br = new BufferedReader(new InputStreamReader(getTagListProcess.getInputStream()));
									String line = null;
									
									while((line = br.readLine()) != null) {
										String readPortNamae = line;
										if(port_name.equals(readPortNamae)) {
											String setTagCommand = "sudo ovs-vsctl --db=tcp:"+entry.getKey()+":"+OVSDB_SERVER_REMOTE_PORT +" set Port "+port_name+" tag="+tag;

											Process setTagProcess = Runtime.getRuntime().exec(setTagCommand);
											setTagProcess.waitFor();

											successCnt++;
										}
									}
									
									
								} catch(Exception e) {
									logger.error("Unable to sync tag. \n Exception: {}", e.getMessage());
								}
							}
						}
						
						if(vPortsCnt == successCnt) {
							nodesByIp.get(entry.getKey()).tag_sync = false;
						}
					}
				}
			}
		}
	}
	
	public void delTunnel() {
		Date now = new Date(System.currentTimeMillis()); 
		SimpleDateFormat simpledateformat = new SimpleDateFormat("yyyyMMddHHmm");
		Long current_time = Long.parseLong(simpledateformat.format(now));
		
		try {
			if(!nodesByIp.isEmpty()) {
				for(Entry<String, NodeDefinition> entry : nodesByIp.entrySet()) {
					Long existing_time = Long.parseLong(entry.getValue().current_time);
					Long time_gap = current_time - existing_time;
					
					if(time_gap >= DELETE_TIME_GAP) {
						String delTunName = TUNNEL_TYPE + "-" + HexString.toHexString(InetAddress.getByName(entry.getKey()).getAddress()).replaceAll(":", "");
						
						nodesByIp.remove(entry.getKey());
						intDpidByIp.remove(entry.getKey());
						
						for(Entry<String, NodeDefinition> deleteEntry : nodesByIp.entrySet()) {
							String in_port = TunnelOvs.get_port_ofport(deleteEntry.getKey(), OVSDB_SERVER_REMOTE_PORT, delTunName);
							
							logger.debug("delTunnelPort : {}", delTunName);
							Runtime.getRuntime().exec("sudo ovs-vsctl --db=tcp:"+deleteEntry.getKey()+":"+OVSDB_SERVER_REMOTE_PORT+" del-port "+TUNNELING_BRIDGE_NAME+" " + delTunName);
							
							logger.debug("delFlow(delTunnel) : IP - {}, FLOW - {}", deleteEntry.getKey(), "sudo ovs-ofctl del-flows "+TUNNELING_BRIDGE_NAME+" in_port="+in_port);
							run_cmd_rest(deleteEntry.getKey(), "8000", "sudo ovs-ofctl del-flows "+TUNNELING_BRIDGE_NAME+" in_port="+in_port);
							
							if(nodesByIp.get(deleteEntry.getKey()).local_vlan_ofports != null) {
								nodesByIp.get(deleteEntry.getKey()).local_vlan_ofports.remove(in_port);
							}
							
							nodesByIp.get(deleteEntry.getKey()).flow_sync = true;
						}
					}
				} 
			}
		} catch(Exception e) {
			logger.error("Unable to delete tunnel port. {}", e.getMessage());
		}
	}
}