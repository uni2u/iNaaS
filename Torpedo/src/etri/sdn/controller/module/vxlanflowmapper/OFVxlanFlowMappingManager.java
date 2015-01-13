package etri.sdn.controller.module.vxlanflowmapper;


import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.projectfloodlight.openflow.protocol.OFFlowRemoved;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.TransportPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import etri.sdn.controller.IService;
import etri.sdn.controller.MessageContext;
import etri.sdn.controller.OFMFilter;
import etri.sdn.controller.OFModel;
import etri.sdn.controller.OFModule;
import etri.sdn.controller.TorpedoProperties;
import etri.sdn.controller.module.statemanager.IStateService;
import etri.sdn.controller.protocol.OFProtocol;
import etri.sdn.controller.protocol.io.Connection;
import etri.sdn.controller.protocol.packet.Ethernet;
import etri.sdn.controller.protocol.packet.IPacket;
import etri.sdn.controller.protocol.packet.IPv4;
import etri.sdn.controller.protocol.packet.UDP;
import etri.sdn.controller.util.MACAddress;

public class OFVxlanFlowMappingManager<K, V> extends OFModule  implements IVxlanFlowMappingService {
	static final Logger logger = LoggerFactory.getLogger(OFVxlanFlowMappingManager.class);
	OFProtocol protocol;
	TorpedoProperties sysconf = TorpedoProperties.loadConfiguration();
	Integer vxlanPortNumber = sysconf.getInt("vxlan-port-number");
	
//	private static ConcurrentHashMap<String, OverlayFlowMappingEntry<String,String>> O2V = new ConcurrentHashMap<String, OverlayFlowMappingEntry<String,String>>();
//	private static ConcurrentHashMap<String, OverlayFlowMappingEntry<String,String>> V2O = new ConcurrentHashMap<String, OverlayFlowMappingEntry<String,String>>();

	private VxlanFlowMappingStorage vxlanFlowMappingStorage;

	IStateService stateService;

	@Override
	protected Collection<Class<? extends IService>> services() {
		List<Class<? extends IService>> ret = new LinkedList<Class<? extends IService>>();
		ret.add(IVxlanFlowMappingService.class);
		return ret;
	}

	@Override
	protected void initialize() {
		// TODO Auto-generated method stub
		this.protocol = getController().getProtocol();
		vxlanFlowMappingStorage = new VxlanFlowMappingStorage(this, "VxlanFlowMappingStorage");
		stateService = (IStateService) getModule(IStateService.class);
		
		registerFilter(
				OFType.PACKET_IN, 
				new OFMFilter() {

					@Override
					public boolean filter(OFMessage m) {
						// we process all PACKET_IN regardless of its version.
						return true;
					}

				}
				);	
		registerFilter(
				OFType.FLOW_REMOVED, 
				new OFMFilter() {
					@Override
					public boolean filter(OFMessage m) {
						return true;
					}
				}
				);

	}
	
	@Override
	public OFModel[] getModels() {
		return new OFModel[] { this.vxlanFlowMappingStorage };
	}

	@Override
	protected boolean handleHandshakedEvent(Connection conn,
			MessageContext context) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	protected boolean handleMessage(Connection conn, MessageContext context,
			OFMessage msg, List<OFMessage> outgoing) {
		
//		System.out.println(pi.getData());
//		Match match = OFFactories.getFactory(packetIn.getVersion()).buildMatch();
		Match match = null;

		switch ( msg.getType() ) {
			case PACKET_IN:
				OFPacketIn pi = (OFPacketIn) msg;
				try {					
					match = pi.getMatch();
				} catch ( UnsupportedOperationException u ) {
					match = this.protocol.loadOFMatchFromPacket(conn.getSwitch(), pi, pi.getInPort(), false);
				}
				
				
				//System.out.println(match);
				
//				OFPort inport = match.get(MatchField.IN_PORT);
				EthType ether = match.get(MatchField.ETH_TYPE);
//				IPv4Address srcip = match.get(MatchField.IPV4_SRC);
//				IPv4Address dstip = match.get(MatchField.IPV4_DST);
				IpProtocol proto = match.get(MatchField.IP_PROTO);
				TransportPort udpdst = match.get(MatchField.UDP_DST);
	
				if (match != null && ether.getValue() == 0x0800 && proto != null && udpdst != null &&
						proto.getIpProtocolNumber() == 0x11 && udpdst.getPort() == vxlanPortNumber.intValue()) {
					 	
					return this.processPacketInMessage(conn, context, (OFPacketIn) msg, outgoing, match);
				}
				
				break;
				
			case FLOW_REMOVED:
				return this.processFlowRemoved(conn, (OFFlowRemoved)msg);
				
			default:
				break;
		}
		
		return true;
	}
	
	private boolean processFlowRemoved(Connection conn, OFFlowRemoved fr) {
		// TODO Auto-generated method stub
		Match match = fr.getMatch();
		
//		System.out.println(match);
		MacAddress outerSrcMac = match.get(MatchField.ETH_SRC);
		MacAddress outerDstMac = match.get(MatchField.ETH_DST);
		IPv4Address outerSrcIP = match.get(MatchField.IPV4_SRC);
		IPv4Address outerDstIP = match.get(MatchField.IPV4_DST);
		IpProtocol ipProtocol = match.get(MatchField.IP_PROTO);
		TransportPort outerSrcPort = null;
		TransportPort outerDstPort = null;
		if (ipProtocol.getIpProtocolNumber() == 0x11) {
			outerSrcPort = match.get(MatchField.UDP_SRC);
			outerDstPort = match.get(MatchField.UDP_DST);
			
			if (outerDstPort.getPort() == vxlanPortNumber.intValue()) {
		
				String o2VKey = outerSrcMac.toString().toUpperCase() + " " + outerDstMac.toString().toUpperCase() + " " + outerSrcIP.toString() + " " + outerDstIP.toString() + " " + outerSrcPort.toString();
				OverlayFlowMappingEntry<String,String>  flowMapping = vxlanFlowMappingStorage.getO2VEnrty(o2VKey);
				if (flowMapping != null) {
						String v2OKey = flowMapping.get("V2OKey");
						vxlanFlowMappingStorage.removeV2OEnrty(v2OKey);
				}
				vxlanFlowMappingStorage.removeO2VEnrty(o2VKey);
				
				
				
//				System.out.println("REMOVED KEY: " + o2VKey);
//				O2V.remove(o2VKey);
//				System.out.println ("=========================================== : " + O2V.keySet().size());
//				Iterator<String> iter = O2V.keySet().iterator();
//				while (iter != null && iter.hasNext()) {
//					System.out.println(O2V.get(iter.next()));
//				}
			}
		}
		
		
//		System.out.println("stateServer : " + stateService);
//		System.out.println("stateServer.getFLows() : " + stateService.getFlows(HexString.toLong("00:00:0a:14:99:ae:ba:4c"), HexString.toLong("08:00:27:84:a2:ed"), HexString.toLong("08:00:27:f4:7e:ba")));
//		System.out.println("++++++++++++++++++++ Flow States: swid = 00:00:0a:14:99:ae:ba:4c" + stateService.getFlows(HexString.toLong("00:00:0a:14:99:ae:ba:4c"), HexString.toLong("08:00:27:84:a2:ed"), HexString.toLong("08:00:27:f4:7e:ba")));
		return true;
	}

	private boolean processPacketInMessage(Connection conn, MessageContext context, OFMessage msg, List<OFMessage> out, Match match) {
		
		//for overlay packet
		MACAddress outerSrcMac=null;
		MACAddress outerDstMac=null;
		EthType outerEtherType=null;
		IPv4Address outerSrcIP=null;
		IPv4Address outerDstIP=null;
		TransportPort outerSrcPort=null;
		TransportPort outerDstPort=null;
		
		//for original packet
		MACAddress innerSrcMac=null;
		MACAddress innerDstMac=null;
		EthType innerEtherType=null;
		IPv4Address innerSrcIP=null;
		IPv4Address innerDstIP=null;
		
		if ( conn.getSwitch() == null ) {
			logger.error("Connection is not fully handshaked");
			return true;
		}
		OFPacketIn  pi = (OFPacketIn) msg;
//		ByteBuffer packetDataBB = ByteBuffer.wrap(pi.getData());
//		int limit = packetDataBB.limit();
//		packetDataBB.position(12); //skip mac addresses
//		short etherType = packetDataBB.getShort(); //get ether type
//		if (etherType == (short)0x8100) { //has vlan tag
//			packetDataBB.getShort(); //skip vlan TCI
//			etherType = packetDataBB.getShort();
//		}
		
//		System.out.println("###################### time:" + System.currentTimeMillis() + " dpid:" + conn.getSwitch().getId());
//		System.out.println(match);
		
		
	
	
		Ethernet etherPacket = new Ethernet();
		etherPacket.deserialize(pi.getData(), 0, pi.getData().length);
		outerSrcMac = etherPacket.getSourceMAC();
		outerDstMac = etherPacket.getDestinationMAC();
		outerEtherType = EthType.of(etherPacket.getEtherType());
//		System.out.println("OverlaySrcMac: " + outerSrcMac);
//		System.out.println("OverlayDstMac: " + outerDstMac);
//		System.out.println("OverlayEhterType: " + outerEtherType);
				
//		IPv4 ipV4Packet = new IPv4();
//		IPacket etherData = etherPacket.getPayload();
//		byte[] etherPayload = etherData.serialize();
		
		IPv4 ipV4Packet = (IPv4) etherPacket.getPayload();
		
		
		//ipV4Packet.deserialize(etherPayload, 0, etherPayload.length );
		outerSrcIP =IPv4Address.of(ipV4Packet.getSourceAddress());
		outerDstIP =IPv4Address.of(ipV4Packet.getDestinationAddress());
		
		
//		
//		System.out.println("OverlaySrcIP: " + outerSrcIP);
//		System.out.println("OverlayDStIP: " + outerDstIP);
		
//		//IPacket ipData = new Data();
//		IPacket ipData =  ipV4Packet.getPayload();
//		byte[] ipPayload = ipData.serialize();
//		UDP udpPacket = new UDP();
//		udpPacket.deserialize(ipPayload, 0, ipPayload.length);
		
		UDP udpPacket = (UDP) ipV4Packet.getPayload();
		outerSrcPort = TransportPort.of(0x0000ffff & udpPacket.getSourcePort());
		outerDstPort = TransportPort.of(0x0000ffff & udpPacket.getDestinationPort());
//		System.out.println("OverlaySrcPort: " + outerSrcPort);
//		System.out.println("OverlayDStPort: " + outerDstPort);
		
		IPacket udpData = udpPacket.getPayload();
		byte[] udpPayload =udpData.serialize();
		ByteBuffer vxlanDataBB = ByteBuffer.wrap(udpPayload);
		vxlanDataBB.position(4);
		Integer vnid = new Integer(vxlanDataBB.getInt()>>8);
//		System.out.println("VNID: " + vnid);
		
		Ethernet innerEtherPacket = new Ethernet();
		innerEtherPacket.deserialize(udpPayload, 8, udpPayload.length-8); //skip vxlan header
		innerSrcMac = innerEtherPacket.getSourceMAC();
		innerDstMac = innerEtherPacket.getDestinationMAC();
		innerEtherType = EthType.of(innerEtherPacket.getEtherType());
//		System.out.println("OrigianlSrcMac: " + innerSrcMac);
//		System.out.println("OrigianlDstMac: " + innerDstMac);
//		System.out.println("OrigianlEhterType: " + innerEtherType);
		
		
		switch (innerEtherType.getValue()) {
		case 0x800: //ipv4
//			IPv4 innerIpv4Packet = new IPv4();
//			IPacket innerEtherData = innerEtherPacket.getPayload();
//			byte[] innerEtherPayload = innerEtherData.serialize();
//			innerIpv4Packet.deserialize(innerEtherPayload, 0, innerEtherPayload.length );
			IPv4 innerIpv4Packet = (IPv4) innerEtherPacket.getPayload();
			innerSrcIP =IPv4Address.of(innerIpv4Packet.getSourceAddress());
			innerDstIP =IPv4Address.of(innerIpv4Packet.getDestinationAddress());
//		
//			System.out.println("OrigianlSrcIP: " + innerSrcIP);
//			System.out.println("OrigianlDStIP: " + innerDstIP);
			break;
		case 0x0806: // ARP
				
			break;

		default:
			break;
		}
		
		OverlayFlowMappingEntry<String,String>  flowMapping = new OverlayFlowMappingEntry<String,String> ();
		
		flowMapping.put("outerSrcMac", outerSrcMac.toString());
		flowMapping.put("outerDstMac", outerDstMac.toString());
		flowMapping.put("outerSrcIP", outerSrcIP.toString());
		flowMapping.put("outerDstIP", outerDstIP.toString());
		flowMapping.put("outerSrcPort", outerSrcPort.toString());
		flowMapping.put("innerSrcMac", innerSrcMac.toString());
		flowMapping.put("innerDstMac", innerDstMac.toString());
		flowMapping.put("innerEtherType", innerEtherType.toString());
		if (innerSrcIP != null) flowMapping.put("innerSrcIP", innerSrcIP.toString());
		if (innerDstIP != null) flowMapping.put("innerDstIP", innerDstIP.toString());
		flowMapping.put("vnid", vnid.toString());
		
		String o2VKey = outerSrcMac.toString() + " " + outerDstMac.toString() + " " + outerSrcIP.toString() + " " + outerDstIP.toString() + " " + outerSrcPort.toString();
		String v2OKey;
		if (innerSrcIP != null && innerDstIP !=null) {
			v2OKey = innerSrcMac.toString() + " " + innerDstMac.toString() + " " + innerEtherType.toString() + " " + innerSrcIP.toString() + " " + innerDstIP.toString();
		} else {
			v2OKey = innerSrcMac.toString() + " " + innerDstMac.toString() + " " + innerEtherType.toString();
		}
		flowMapping.put("V2OKey", v2OKey);
		
		vxlanFlowMappingStorage.addO2VEnrty(o2VKey, flowMapping);
		vxlanFlowMappingStorage.addV2OEnrty(v2OKey, flowMapping);
		
		
		
		return true;
	}


	@Override
	protected boolean handleDisconnect(Connection conn) {
		// TODO Auto-generated method stub
		return false;
	}
	
	public int getVnid(String srcMac, String dstMac, String srcIp, String dstIp, int srcUdpPort) {
		return vxlanFlowMappingStorage.getVnid( srcMac,  dstMac,  srcIp,  dstIp,  srcUdpPort);
	}
	
	public int getVnid(OFMessage msg) {
		OFPacketIn  pi = (OFPacketIn) msg;
		Ethernet etherPacket = new Ethernet();
		etherPacket.deserialize(pi.getData(), 0, pi.getData().length);
		
		EthType outerEtherType = EthType.of(etherPacket.getEtherType());
//		System.out.println ("EtherType = " + outerEtherType.getValue());
		if (outerEtherType.getValue() != 0x0800) return -1;  //packet-in is not ip packet
		
		
		etherPacket.deserialize(pi.getData(), 0, pi.getData().length);
		IPv4 ipV4Packet = (IPv4) etherPacket.getPayload();
		
		IpProtocol protocol =  IpProtocol.of((short)ipV4Packet.getProtocol());
//		System.out.println ("IpProtocol = " + protocol.getIpProtocolNumber());
		if (protocol.getIpProtocolNumber() != 0x11) return -1;  //packet-in is not udp packet
		
		UDP udpPacket = (UDP) ipV4Packet.getPayload();
		TransportPort udpDstPort = TransportPort.of(0x0000ffff & udpPacket.getDestinationPort());		
		if (udpDstPort.getPort() != vxlanPortNumber.intValue()) return -1;  //packet-in is not vxlan packet
		
		IPacket udpData = udpPacket.getPayload();
		byte[] udpPayload =udpData.serialize();
		ByteBuffer vxlanDataBB = ByteBuffer.wrap(udpPayload);
		vxlanDataBB.position(4);
		Integer vnid = new Integer(vxlanDataBB.getInt()>>8);
		return vnid.intValue();
	}



}
