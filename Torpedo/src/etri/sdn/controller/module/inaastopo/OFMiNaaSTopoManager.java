package etri.sdn.controller.module.inaastopo;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;

import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.util.HexString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import etri.sdn.controller.IService;
import etri.sdn.controller.MessageContext;
import etri.sdn.controller.OFMFilter;
import etri.sdn.controller.OFModel;
import etri.sdn.controller.OFModule;
import etri.sdn.controller.module.devicemanager.IDevice;
import etri.sdn.controller.module.devicemanager.IDeviceService;
import etri.sdn.controller.module.linkdiscovery.ILinkDiscoveryService;
import etri.sdn.controller.module.linkdiscovery.Link;
import etri.sdn.controller.module.linkdiscovery.LinkInfo;
import etri.sdn.controller.module.ml2.RestPort.PortDefinition;
import etri.sdn.controller.module.routing.IRoutingDecision;
import etri.sdn.controller.module.tunnelmanager.IOFMTunnelManagerService;
import etri.sdn.controller.protocol.io.Connection;
import etri.sdn.controller.protocol.io.IOFSwitch;
import etri.sdn.controller.protocol.packet.IPv4;

public class OFMiNaaSTopoManager extends OFModule implements IOFMiNaaSTopoManagerService {
	
	public static final Logger logger = LoggerFactory.getLogger(OFMiNaaSTopoManager.class);

	private iNaaSTopoConfiguration topoConf = null;
	
	protected IDeviceService device;
	protected IOFMTunnelManagerService tunnelManager;
	protected ILinkDiscoveryService linkDiscovery;
	
	@Override
	protected Collection<Class<? extends IService>> services() {
		List<Class<? extends IService>> ret = new LinkedList<>();
		ret.add(IOFMiNaaSTopoManagerService.class);
		return ret;
	}

	public OFMiNaaSTopoManager() {
		this.topoConf = new iNaaSTopoConfiguration(this);
	}

	@Override
	protected void initialize() {
		device = (IDeviceService) getModule(IDeviceService.class);
		tunnelManager = (IOFMTunnelManagerService) getModule(IOFMTunnelManagerService.class);
		linkDiscovery = (ILinkDiscoveryService) getModule(ILinkDiscoveryService.class);
		
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
		return new OFModel[] { this.topoConf };
	}
	
	private boolean processPacketInMessage(Connection conn, OFMessage msg, IRoutingDecision decision, MessageContext cntx){
		return true;
	}
	
	@Override
	public String getINaaSTopoAll() {
		StringBuffer jsonStr = new StringBuffer();
		jsonStr.append("{");
		jsonStr.append(getSwitchList());
		jsonStr.append(",");
		jsonStr.append(getHostList());
		jsonStr.append(",");
		jsonStr.append(getLinkList());
		jsonStr.append(",");
		jsonStr.append(getVmList());
		jsonStr.append("}");
		
		return jsonStr.toString();
	}
	
	public String getSwitchList() {
		StringBuffer switchlist = new StringBuffer();
		
		switchlist.append("\"switchlist\":[");
		if(getController().getSwitches().size() > 0) {
			int switchCnt = 0;
			for (IOFSwitch sw : getController().getSwitches()) {
				if(!tunnelManager.getBridgeDpid().containsKey(sw.getId())) {
					if(switchCnt == 0) {
						switchlist.append("{");
					} else {
						switchlist.append(",{");
					}
					switchlist.append("\"dpid\":\"" + HexString.toHexString(sw.getId()) + "\"");
					switchlist.append("}");
					
					switchCnt++;
				}
			}
		}
		switchlist.append("]");
		
		return switchlist.toString();
	}
	
	public String getHostList() {
		StringBuffer hostlist = new StringBuffer();
		hostlist.append("\"hostlist\":[");
		
		int hostCnt = 0;
		for(IDevice deviceEntry : device.getAllDevices()) {
			if(deviceEntry.getIPv4Addresses().length > 0) {
				String host_ip = IPv4.fromIPv4Address(deviceEntry.getIPv4Addresses()[0]);
				String host_name = tunnelManager.getNodeInfo().containsKey(host_ip) ? tunnelManager.getNodeInfo().get(host_ip).node_name : "";
				
				if(!tunnelManager.getVmByIp().containsKey(host_ip)) {
					if(hostCnt == 0) {
						hostlist.append("{");
					} else {
						hostlist.append(",{");
					}
					hostlist.append("\"host_ip\":\""+host_ip+"\",");
					hostlist.append("\"host_name\":\""+host_name+"\",");
					if(deviceEntry.getAttachmentPoints().length > 0) {
						hostlist.append("\"connected_sw\":\""+HexString.toHexString(deviceEntry.getAttachmentPoints()[0].getSwitchDPID())+"\",");
						hostlist.append("\"connected_port\":"+deviceEntry.getAttachmentPoints()[0].getPort().getPortNumber());
					} else {
						hostlist.append("\"connected_sw\":\"\",");
						hostlist.append("\"connected_port\":\"\"");
					}
					hostlist.append("}");
					
					hostCnt++;
				}
			}
		}
		hostlist.append("]");
		
		return hostlist.toString();
	}
	
	public String getLinkList() {
		StringBuffer linklist = new StringBuffer();
		linklist.append("\"linklist\":[");
		
		if(linkDiscovery.getLinks() != null) {
			int linkCnt = 0;
			for(Entry<Link, LinkInfo> linkEntry : linkDiscovery.getLinks().entrySet()) {
				if(!tunnelManager.getBridgeDpid().containsKey(linkEntry.getKey().getSrc()) &&
						!tunnelManager.getBridgeDpid().containsKey(linkEntry.getKey().getDst())) {
					if(linkCnt == 0) {
						linklist.append("{");
					} else {
						linklist.append(",{");
					}
					linklist.append("\"src_sw\":\""+HexString.toHexString(linkEntry.getKey().getSrc())+"\",");
					linklist.append("\"src_port\":\""+linkEntry.getKey().getSrcPort().getPortNumber()+",");
					linklist.append("\"dst_sw\":\""+HexString.toHexString(linkEntry.getKey().getDst())+"\",");
					linklist.append("\"dst_port\":\""+linkEntry.getKey().getDstPort().getPortNumber());
					linklist.append("}");
					
					linkCnt++;
				}
			}
		}
		linklist.append("]");
		
		return linklist.toString();
	}
	
	public String getVmList() {
		StringBuffer vmlist = new StringBuffer();
		vmlist.append("\"vmlist\":[");
		
		int vmCnt = 0;
		for(Entry<String, PortDefinition> vmEntry : tunnelManager.getVmByGuid().entrySet()) {
			if(vmCnt == 0) {
				vmlist.append("{");
			} else {
				vmlist.append(",{");
			}
			vmlist.append("\"vm_id\":\""+vmEntry.getValue().device_id+"\",");
			vmlist.append("\"connected_host\":\""+vmEntry.getValue().binding_host_id+"\",");
			vmlist.append("\"mac\":\""+vmEntry.getValue().mac_address+"\",");
			String vm_ip = "";
			String subnet_id = "";
			if(vmEntry.getValue().fixed_ips.size() > 0) {
				vm_ip = vmEntry.getValue().fixed_ips.get(0).get("ip_address");
				subnet_id = vmEntry.getValue().fixed_ips.get(0).get("subnet_id");
			}
			vmlist.append("\"vm_ip\":\""+vm_ip+"\",");
			vmlist.append("\"tenant_id\":\""+vmEntry.getValue().tenant_id+"\",");
			vmlist.append("\"network_id\":\""+vmEntry.getValue().network_id+"\",");
			vmlist.append("\"subnet_id\":\""+subnet_id+"\"");
			
//			vmlist.append("\"vm_id\":\""++"\",");
//			vmlist.append("\"vm_id\":\""++"\",");
			
			vmlist.append("}");
			
			vmCnt++;
		}
		vmlist.append("]");
		
		return vmlist.toString();
	}
}