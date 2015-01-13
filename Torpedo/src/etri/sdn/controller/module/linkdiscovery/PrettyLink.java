package etri.sdn.controller.module.linkdiscovery;


import java.nio.ByteBuffer;

import org.codehaus.jackson.annotate.JsonProperty;
import org.projectfloodlight.openflow.types.OFPort;

class PrettyLink {
	@JsonProperty("src-switch")
	public String srcdpid;
	
	@JsonProperty("src-port")
	public OFPort srcport;
	
//	@JsonProperty("src-port-state")
//	public Set<OFPortState> srcstatus;
	
	@JsonProperty("dst-switch")
	public String dstdpid;
	
	@JsonProperty("dst-port")
	public OFPort dstport;
	
//	@JsonProperty("dst-port-state")
//	public Set<OFPortState> dststatus;
	
//	public String type;
	
	public PrettyLink (Link l) {
		byte[] bDPID = ByteBuffer.allocate(8).putLong(l.getSrc()).array();
		this.srcdpid = String.format("%02x:%02x:%02x:%02x:%02x:%02x:%02x:%02x",
				bDPID[0], bDPID[1], bDPID[2], bDPID[3], bDPID[4], bDPID[5], bDPID[6], bDPID[7]);
		
		bDPID = ByteBuffer.allocate(8).putLong(l.getDst()).array();
		this.dstdpid = String.format("%02x:%02x:%02x:%02x:%02x:%02x:%02x:%02x",
				bDPID[0], bDPID[1], bDPID[2], bDPID[3], bDPID[4], bDPID[5], bDPID[6], bDPID[7]);
		
		this.srcport = l.getSrcPort();
		this.dstport = l.getDstPort();
		
//		LinkInfo linkInfo = links.get( l );
//		this.srcstatus = linkInfo.getSrcPortState();
//		this.dststatus = linkInfo.getDstPortState();
//		this.type = linkInfo.getLinkType().toString();
	}
}

