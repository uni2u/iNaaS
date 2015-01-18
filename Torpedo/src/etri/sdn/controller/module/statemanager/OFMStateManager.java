package etri.sdn.controller.module.statemanager;


import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import org.projectfloodlight.openflow.protocol.OFActionType;
import org.projectfloodlight.openflow.protocol.OFAggregateStatsRequest;
import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowStatsEntry;
import org.projectfloodlight.openflow.protocol.OFFlowStatsReply;
import org.projectfloodlight.openflow.protocol.OFFlowStatsRequest;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFStatsReply;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IpDscp;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFGroup;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;
import org.projectfloodlight.openflow.types.TableId;
import org.projectfloodlight.openflow.types.TransportPort;
import org.projectfloodlight.openflow.types.VlanPcp;
import org.projectfloodlight.openflow.util.HexString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import etri.sdn.controller.IService;
import etri.sdn.controller.MessageContext;
import etri.sdn.controller.OFModel;
import etri.sdn.controller.OFModule;
import etri.sdn.controller.protocol.OFProtocol;
import etri.sdn.controller.protocol.io.Connection;
import etri.sdn.controller.protocol.io.IOFSwitch;
import etri.sdn.controller.module.linkdiscovery.ILinkDiscoveryService;
import etri.sdn.controller.module.linkdiscovery.PrettyLink;
import etri.sdn.controller.util.StackTrace;


/**
 * This module does not handle any OFMessage.
 * The purpose of this module is to handle REST calls 
 * that request status-related information including:
 * (1) controller health 
 * (2) switch description
 * (3) port information
 * (4) aggregated flow statistics 
 * 
 * @author bjlee
 *
 */
public class OFMStateManager extends OFModule implements IStateService{
	
	private OFProtocol protocol;
	private ILinkDiscoveryService linkDiscoveryService;
	
	static final Logger logger = LoggerFactory.getLogger(OFMStateManager.class);
	
	/**
	 * Model of this module. initialized by {@link #initialize()}.
	 */
	private State state;
	

	/**
	 * initialize the model object of this module.
	 */
	@Override
	protected void initialize() {
		state = new State(this);
		protocol = (OFProtocol)getController().getProtocol();
		linkDiscoveryService = (ILinkDiscoveryService) getModule(ILinkDiscoveryService.class);
	}

	/**
	 * Does nothing except for returning true.
	 */
	@Override
	protected boolean handleHandshakedEvent(Connection conn,
			MessageContext context) {
		return true;
	}

	/**
	 * Does nothing except for returning true.
	 */
	@Override
	protected boolean handleMessage(Connection conn, MessageContext context,
			OFMessage msg, List<OFMessage> outgoing) {
		return true;
	}

	/**
	 * Does nothing except for returning true.
	 */
	@Override
	protected boolean handleDisconnect(Connection conn) {
		return true;
	}


	/**
	 * return the model object {@link #state}.
	 */
	@Override
	public OFModel[] getModels() {
		return new OFModel[] { this.state };
	}
	
	

	@Override
	protected Collection<Class<? extends IService>> services() {
		List<Class<? extends IService>> ret = new LinkedList<Class<? extends IService>>();
		ret.add(IStateService.class);
		return ret;
	}
	
	public List<OFStatsReply>  getAggregateFlow(Long switchId) {
		
		IOFSwitch sw = getController().getSwitch(switchId);
		List<OFStatsReply> resultValues = 
				new java.util.LinkedList<OFStatsReply>();
		
		if ( sw == null ) {
			return resultValues;		// switch is not completely set up.
		}
		
		OFFactory fac = OFFactories.getFactory(sw.getVersion());
		
//		HashMap<String, List<OFFlowStatsEntry>> result = 
//			new HashMap<String, List<OFFlowStatsEntry>>();
		
//		result.put(switchId.toHexString(switchId), resultValues);
							
		OFAggregateStatsRequest.Builder req = fac.buildAggregateStatsRequest();
		req
		.setMatch( fac.matchWildcardAll() )
		.setOutPort( OFPort.ANY /* NONE for 1.0*/ );
		
		try {
			req
			.setOutGroup(OFGroup.ANY)
			.setTableId(TableId.ALL);
		} catch ( UnsupportedOperationException u ) {}

		try { 
			resultValues = protocol.getSwitchStatistics(sw, req.build());
		} catch ( Exception e ) {
			OFMStateManager.logger.error("error={}", StackTrace.of(e));
			return resultValues;
		}
		return resultValues;
		
	}
	
	public List<OFFlowStatsEntry>  getFlows(Long switchId) {
		
		IOFSwitch sw = getController().getSwitch(switchId);
		List<OFFlowStatsEntry> resultValues = 
				new java.util.LinkedList<OFFlowStatsEntry>();
		
		if ( sw == null ) {
			return resultValues;		// switch is not completely set up.
		}
		
		OFFactory fac = OFFactories.getFactory(sw.getVersion());
		
//		HashMap<String, List<OFFlowStatsEntry>> result = 
//			new HashMap<String, List<OFFlowStatsEntry>>();
		
//		result.put(switchId.toHexString(switchId), resultValues);
							
		OFFlowStatsRequest.Builder req = fac.buildFlowStatsRequest();
		req
		.setMatch( fac.matchWildcardAll() )
		.setOutPort( OFPort.ANY /* NONE for 1.0*/ );
		try {
			req
			.setOutGroup(OFGroup.ANY)
			.setTableId(TableId.ALL);
		} catch ( UnsupportedOperationException u ) {}

		try { 
			List<OFStatsReply> reply = protocol.getSwitchStatistics(sw, req.build());
			for ( OFStatsReply s : reply ) {
				if ( s instanceof OFFlowStatsReply ) {
					resultValues.addAll( ((OFFlowStatsReply)s).getEntries() );
				}
			}
		} catch ( Exception e ) {
			OFMStateManager.logger.error("error={}", StackTrace.of(e));
			return resultValues;
		}
//		System.out.println("++++++++++++++++++++ Flow States: swid = 00:00:0a:14:99:ae:ba:4c " + resultValues);
		return resultValues;
		
	}
	
	public List<OFFlowStatsEntry>  getFlows(Long switchId, Long ethSrc, Long ethDst) {
		IOFSwitch sw = getController().getSwitch(switchId);
		List<OFFlowStatsEntry> resultValues = 
				new java.util.LinkedList<OFFlowStatsEntry>();
		if ( sw == null ) {
			return resultValues;		// switch is not completely set up.
		}
		
		OFFactory fac = OFFactories.getFactory(sw.getVersion());
		Match.Builder ret = OFFactories.getFactory(sw.getVersion()).buildMatch();
		ret.setExact(MatchField.ETH_SRC,  MacAddress.of(ethSrc));
		ret.setExact(MatchField.ETH_DST,  MacAddress.of(ethDst));
		
		
//		HashMap<String, List<OFFlowStatsEntry>> result = 
//			new HashMap<String, List<OFFlowStatsEntry>>();

//		result.put(switchId.toHexString(switchId), resultValues);
							
		OFFlowStatsRequest.Builder req = fac.buildFlowStatsRequest();
		req
		.setMatch(ret.build())
		.setOutPort( OFPort.ANY /* NONE for 1.0*/ );
		try {
			req
			.setOutGroup(OFGroup.ANY)
			.setTableId(TableId.ALL);
		} catch ( UnsupportedOperationException u ) {}

		try { 
			List<OFStatsReply> reply = protocol.getSwitchStatistics(sw, req.build());
			for ( OFStatsReply s : reply ) {
				if ( s instanceof OFFlowStatsReply ) {
					resultValues.addAll( ((OFFlowStatsReply)s).getEntries() );
				}
			}
		} catch ( Exception e ) {
			OFMStateManager.logger.error("error={}", StackTrace.of(e));
			System.out.println(e.getStackTrace());
			return resultValues;
		}
		return resultValues;
	}
	
	
	public List<OFFlowStatsEntry>  getFlows(Long switchId, int in_port, int out_port) {
		IOFSwitch sw = getController().getSwitch(switchId);
		List<OFFlowStatsEntry> resultValues = 
				new java.util.LinkedList<OFFlowStatsEntry>();
		if ( sw == null ) {
			return resultValues;		// switch is not completely set up.
		}
		
		OFFactory fac = OFFactories.getFactory(sw.getVersion());
		Match.Builder ret = OFFactories.getFactory(sw.getVersion()).buildMatch();
		
		if (in_port > 0) {
			ret.setExact(MatchField.IN_PORT, OFPort.ofInt(in_port));
		}
							
		OFFlowStatsRequest.Builder req = fac.buildFlowStatsRequest();
		req.setMatch(ret.build());
		
		if (out_port <= 0) {
			req.setOutPort( OFPort.ANY /* NONE for 1.0*/ );
		} else {
			req.setOutPort(OFPort.ofInt(out_port));
		}
		try {
			req
			.setOutGroup(OFGroup.ANY)
			.setTableId(TableId.ALL);
		} catch ( UnsupportedOperationException u ) {}

		try { 
			List<OFStatsReply> reply = protocol.getSwitchStatistics(sw, req.build());
			for ( OFStatsReply s : reply ) {
				if ( s instanceof OFFlowStatsReply ) {
					resultValues.addAll( ((OFFlowStatsReply)s).getEntries() );
				}
			}
		} catch ( Exception e ) {
			OFMStateManager.logger.error("error={}", StackTrace.of(e));
			System.out.println(e.getStackTrace());
			return resultValues;
		}
		return resultValues;
	}

	public List<OFFlowStatsEntry>  getFlows(Long switchId, HashMap<String, String> matchMap) {
		
		IOFSwitch sw = getController().getSwitch(switchId);
		List<OFFlowStatsEntry> resultValues = 
				new java.util.LinkedList<OFFlowStatsEntry>();
		if ( sw == null ) {
			return resultValues;		// switch is not completely set up.
		}
		
		OFFactory fac = OFFactories.getFactory(sw.getVersion());
		OFFlowStatsRequest.Builder req = fac.buildFlowStatsRequest();
		req.setMatch(getMatch(sw, matchMap));
		req.setOutPort( OFPort.ANY /* NONE for 1.0*/ );
		
		try {
			req
			.setOutGroup(OFGroup.ANY)
			.setTableId(TableId.ALL);
		} catch  ( UnsupportedOperationException u ) {}
		try { 
			List<OFStatsReply> reply = protocol.getSwitchStatistics(sw, req.build());
			for ( OFStatsReply s : reply ) {
				if ( s instanceof OFFlowStatsReply ) {
					resultValues.addAll( ((OFFlowStatsReply)s).getEntries() );
				}
			}
		} catch ( Exception e ) {
			OFMStateManager.logger.error("error={}", StackTrace.of(e));
			System.out.println(e.getStackTrace());
			return resultValues;
		}
		return resultValues;
	}
	

	public List<PrettyLink> getOutLinks(Long switchId, List<OFFlowStatsEntry> flowEntries) {
		
		List<PrettyLink> links = new LinkedList<PrettyLink>();
		HashSet<Integer> ports = new HashSet<Integer>();
		PrettyLink link;
		
		for (OFFlowStatsEntry entry : flowEntries) {
			for (OFAction action : entry.getActions()) {
				if (action.getType().equals(OFActionType.OUTPUT)) {
//					System.out.println("ACtion " + action);
					OFActionOutput actionoutput = (OFActionOutput) action;
					int outPortNumber = actionoutput.getPort().getPortNumber();
					link = linkDiscoveryService.getOutLink(switchId, outPortNumber);
//					System.out.println("LINK " + link);
					if (link != null && ports.add(outPortNumber)) {
						links.add(link);			
					}
				}
			}
		}
		return links;
	}
	
	
	public List<PrettyLink> getOutGoingPath(Long switchId, HashMap<String, String> matchMap) {
		IOFSwitch sw = getController().getSwitch(switchId);
		List<PrettyLink> returnedLinks = new java.util.LinkedList<PrettyLink>();
		if ( sw == null ) {
			return returnedLinks;		// switch is not completely set up.
		}
		
		List<OFFlowStatsEntry> flows = getFlows(switchId, matchMap);
		List<PrettyLink> outLinks = getOutLinks(switchId, flows);
		returnedLinks.addAll(outLinks);
		for (PrettyLink l : outLinks) {
			Long dstSwitch = l.getDstSwitch();
			int dstPort = l.getDstPort().getPortNumber();
			matchMap.remove("in_port");
			matchMap.put("in_port", new Integer(dstPort).toString());
			List<PrettyLink> links = getOutGoingPath(dstSwitch, matchMap);
			returnedLinks.addAll(links);
		}
		return returnedLinks;
	}
	
	public List<PrettyLink> getIncommingLinks(Long switchId, List<OFFlowStatsEntry> flowEntries) {
		
		List<PrettyLink> links = new LinkedList<PrettyLink>();
		HashSet<Integer> ports = new HashSet<Integer>();
		PrettyLink link = null;
		
		for (OFFlowStatsEntry entry : flowEntries) {
			Match match = entry.getMatch();
			OFPort inPort = match.get(MatchField.IN_PORT);
			System.out.println("============ sw: " + HexString.toHexString(switchId) + " flow:  " + entry + " inPort: " + inPort );
			if (inPort != null) {
				link = linkDiscoveryService.getOutLink(switchId, inPort.getPortNumber());
//					System.out.println("LINK " + link.getSrcSwitch());
			}
			if (link != null && ports.add(inPort.getPortNumber())) {
				links.add(link.getReverseLink());			
			}
		}
		
		return links;
	}
	
	public List<PrettyLink> getIncommingPath (Long switchId, HashMap<String, String> matchMap) {
		IOFSwitch sw = getController().getSwitch(switchId);
		List<PrettyLink> returnedLinks = new java.util.LinkedList<PrettyLink>();
		if ( sw == null ) {
			return returnedLinks;		// switch is not completely set up.
		}
		
		List<OFFlowStatsEntry> flows = getFlows(switchId, matchMap);
		flows = getFlows(switchId, matchMap);

		List<PrettyLink> inLinks = getIncommingLinks(switchId, flows);
		returnedLinks.addAll(inLinks);
		for (PrettyLink l : inLinks) {
			Long connectedSw = l.getSrcSwitch();
//			int dstPort = l.getDstPort().getPortNumber();
			matchMap.remove("in_port");
//			matchMap.put("in_port", new Integer(dstPort).toString());
			if (connectedSw != null) {
				List<PrettyLink> links = getIncommingPath(connectedSw, matchMap);
				returnedLinks.addAll(links);
			}
		}
		return returnedLinks;
		
	}
	
	public List<PrettyLink> getPath (Long switchId, HashMap<String, String> matchMap) {
		IOFSwitch sw = getController().getSwitch(switchId);
		List<PrettyLink> path =  new java.util.LinkedList<PrettyLink>();
		if ( sw == null ) {
			return path;		// switch is not completely set up.
		}
		
		List<PrettyLink> inPath = getIncommingPath(switchId, matchMap);
		List<PrettyLink> outPath = getOutGoingPath(switchId, matchMap);
		path.addAll(inPath);
		path.addAll(outPath);
	
		
		return path;
	}
	
	public Match getMatch(IOFSwitch switchId, HashMap<String, String> matchMap) {
		
		Match.Builder builder = OFFactories.getFactory(switchId.getVersion()).buildMatch();
		try {
			for (String key : matchMap.keySet()) {
				if (key.toLowerCase().equals("in_port")) {
					builder.setExact(MatchField.IN_PORT, OFPort.of(Integer.valueOf((String) matchMap.get("in_port"))));
				}
				else if (key.toLowerCase().equals("eth_dst")) {
					builder.setExact(MatchField.ETH_DST, MacAddress.of((String) matchMap.get("eth_dst")));
				}
				else if (key.toLowerCase().equals("eth_src")) {
					builder.setExact(MatchField.ETH_SRC, MacAddress.of((String) matchMap.get("eth_src")));
				}
				else if (key.toLowerCase().equals("vlan_vid")) {
					builder.setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(Integer.valueOf((String) matchMap.get("vlan_vid"))));
				}
				else if (key.toLowerCase().equals("vlan_pcp")) {
					builder.setExact(MatchField.VLAN_PCP, VlanPcp.of(Byte.valueOf((String) matchMap.get("vlan_pcp"))));
				}
				else if (key.toLowerCase().equals("eth_type")) {
					builder.setExact(MatchField.ETH_TYPE, EthType.of(Integer.valueOf((String) matchMap.get("eth_type"), 16)));
				}
				else if (key.toLowerCase().equals("ip_proto")) {
					builder.setExact(MatchField.IP_PROTO, IpProtocol.of((short)(Integer.valueOf((String) matchMap.get("ip_proto"), 16).intValue())));
				}
				else if (key.toLowerCase().equals("tcp_src")) {
					builder.setExact(MatchField.TCP_SRC, TransportPort.of(Integer.valueOf((String) matchMap.get("tcp_src")).intValue()));
				}
				else if (key.toLowerCase().equals("tcp_dst")) {
					builder.setExact(MatchField.TCP_DST, TransportPort.of(Integer.valueOf((String) matchMap.get("tcp_dst")).intValue()));
				}
				else if (key.toLowerCase().equals("udp_src")) {
					builder.setExact(MatchField.UDP_SRC, TransportPort.of(Integer.valueOf((String) matchMap.get("udp_src")).intValue()));
				}
				else if (key.toLowerCase().equals("udp_dst")) {
					builder.setExact(MatchField.UDP_DST, TransportPort.of(Integer.valueOf((String) matchMap.get("udp_dst")).intValue()));
				}
				else if (key.toLowerCase().equals("ipv4_src")) {
					builder.setExact(MatchField.IPV4_SRC, IPv4Address.of((String) matchMap.get("ipv4_src")));
				}
				else if (key.toLowerCase().equals("ipv4_dst")) {
					builder.setExact(MatchField.IPV4_DST, IPv4Address.of((String) matchMap.get("ipv4_dst")));
				}
				else if (key.toLowerCase().equals("ip_dscp")) {
					short s;
					String dscp = (String)matchMap.get("ip_dscp");
					s = Integer.valueOf(dscp).shortValue();
					builder.setExact(MatchField.IP_DSCP, IpDscp.of((byte)((0b11111100 & s) >> 2)));
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		return builder.build();
	}	
	
	@Override
	public List<OFStatsReply> getAggregate(Long switchId) {
		// TODO Auto-generated method stub
		
		return null;
	}
}
