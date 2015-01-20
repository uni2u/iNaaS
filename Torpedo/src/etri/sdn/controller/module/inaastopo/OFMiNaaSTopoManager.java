package etri.sdn.controller.module.inaastopo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.util.HexString;
import org.restlet.data.Method;
import org.restlet.resource.ClientResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import etri.sdn.controller.IService;
import etri.sdn.controller.MessageContext;
import etri.sdn.controller.OFMFilter;
import etri.sdn.controller.OFModel;
import etri.sdn.controller.OFModule;
import etri.sdn.controller.TorpedoProperties;
import etri.sdn.controller.module.devicemanager.IDevice;
import etri.sdn.controller.module.devicemanager.IDeviceService;
import etri.sdn.controller.module.linkdiscovery.ILinkDiscoveryService;
import etri.sdn.controller.module.ml2.PortDefinition;
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
				String host_name = tunnelManager.getNodeInfo().containsKey(host_ip) ? tunnelManager.getNodeInfo().get(host_ip).node_name : tunnelManager.getHostName(host_ip);
				String host_mac = deviceEntry.getMACAddressString();
				
				if(!tunnelManager.getVmByIp().containsKey(host_ip)) {
					if(!host_ip.equals(host_name)) {
						if(hostCnt == 0) {
							hostlist.append("{");
						} else {
							hostlist.append(",{");
						}
						
						hostlist.append("\"host_ip\":\""+host_ip+"\",");
						hostlist.append("\"host_name\":\""+host_name+"\",");
						hostlist.append("\"mac\":\""+host_mac+"\",");
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
		}
		hostlist.append("]");
		
		return hostlist.toString();
	}
	
//	public String getLinkList() {
//		StringBuffer linklist = new StringBuffer();
//		linklist.append("\"linklist\":[");
//		
//		if(linkDiscovery.getLinks() != null) {
//			int linkCnt = 0;
//			for(Entry<Link, LinkInfo> linkEntry : linkDiscovery.getLinks().entrySet()) {
//				if(!tunnelManager.getBridgeDpid().containsKey(linkEntry.getKey().getSrc()) &&
//						!tunnelManager.getBridgeDpid().containsKey(linkEntry.getKey().getDst())) {
//					if(linkCnt == 0) {
//						linklist.append("{");
//					} else {
//						linklist.append(",{");
//					}
//					linklist.append("\"src_sw\":\""+HexString.toHexString(linkEntry.getKey().getSrc())+"\",");
//					linklist.append("\"src_port\":\""+linkEntry.getKey().getSrcPort().getPortNumber()+",");
//					linklist.append("\"dst_sw\":\""+HexString.toHexString(linkEntry.getKey().getDst())+"\",");
//					linklist.append("\"dst_port\":\""+linkEntry.getKey().getDstPort().getPortNumber());
//					linklist.append("}");
//					
//					linkCnt++;
//				}
//			}
//		}
//		linklist.append("]");
//		
//		return linklist.toString();
//	}
	public String getLinkList() {
		TorpedoProperties sysconf = TorpedoProperties.loadConfiguration();
		String rest_port = sysconf.getString("web-server-port");
		String restUri = "http://localhost:"+rest_port+"/wm/topology/links/json";
		
		StringBuffer linklist = new StringBuffer();
		linklist.append("\"linklist\":[");
		
		try {
			ClientResource resource = new ClientResource(restUri);
			resource.setMethod(Method.GET);
			resource.get();

			ObjectMapper om = new ObjectMapper();
			List<Map<String, Object>> resultVal = om.readValue(resource.getResponse().getEntityAsText(), new TypeReference<List<Map<String, Object>>>(){});
			
			int linkCnt = 0;
			for(Map<String, Object> linkMap : resultVal) {
				long srcSwitchDpid = Long.parseLong(linkMap.get("src-switch").toString().replaceAll(":", ""), 16);
				long dstSwitchDpid = Long.parseLong(linkMap.get("dst-switch").toString().replaceAll(":", ""), 16);
				
				if(!tunnelManager.getBridgeDpid().containsKey(srcSwitchDpid) && !tunnelManager.getBridgeDpid().containsKey(dstSwitchDpid)) {
					if(linkCnt == 0) {
						linklist.append("{");
					} else {
						linklist.append(",{");
					}
					linklist.append("\"src_sw\":\""+linkMap.get("src-switch").toString()+"\",");
					linklist.append("\"src_port\":"+linkMap.get("src-port")+",");
					linklist.append("\"dst_sw\":\""+linkMap.get("dst-switch").toString()+"\",");
					linklist.append("\"dst_port\":"+linkMap.get("dst-port"));
					linklist.append("}");
					
					linkCnt++;
				}
			}
		} catch(IOException e) {
			logger.debug("linklist json parse exception.");
		}
		
		linklist.append("]");
		
		return linklist.toString();
	}
	
	public String getVmList() {
		StringBuffer vmlist = new StringBuffer();
		vmlist.append("\"vmlist\":[");
		
		
		Map<String, List<PortDefinition>> vmMap = new ConcurrentHashMap<String, List<PortDefinition>>();
		for(Entry<String, PortDefinition> portEntry : tunnelManager.getVmByGuid().entrySet()) {
			if(vmMap.containsKey(portEntry.getValue().device_id)) {
				vmMap.get(portEntry.getValue().device_id).add(portEntry.getValue());
			} else {
				List<PortDefinition> portList = new ArrayList<PortDefinition>();
				portList.add(portEntry.getValue());
				vmMap.put(portEntry.getValue().device_id, portList);
			}
		}
		
		int vmCnt = 0;
		for(Entry<String, List<PortDefinition>> vmEntry : vmMap.entrySet()) {
			if(vmCnt == 0) {
				vmlist.append("{");
			} else {
				vmlist.append(",{");
			}
			vmlist.append("\"vm_id\":\""+vmEntry.getKey()+"\",");
			vmlist.append("\"connected_host\":\""+vmEntry.getValue().get(0).binding_host_id+"\",");			
			
			// added : vmlist into vm attach mac address
			for(IDevice deviceEntry : device.getAllDevices()) {
				if(deviceEntry.getIPv4Addresses().length > 0) {
					String host_ip = IPv4.fromIPv4Address(deviceEntry.getIPv4Addresses()[0]);
					String host_name = tunnelManager.getNodeInfo().containsKey(host_ip) ? tunnelManager.getNodeInfo().get(host_ip).node_name : tunnelManager.getHostName(host_ip);
					String host_mac = deviceEntry.getMACAddressString();

					if(host_name.equals(vmEntry.getValue().get(0).binding_host_id)) {
						vmlist.append("\"connected_mac\":\""+host_mac+"\",");
					}
				}
			}
			
			vmlist.append("\"vnics\":[");
			int vnicCnt = 0;
			for(PortDefinition port : vmEntry.getValue()) {
				if(vnicCnt == 0) {
					vmlist.append("{");
				} else {
					vmlist.append(",{");
				}
				vmlist.append("\"mac\":\""+port.mac_address+"\",");
				String vm_ip = "";
				String subnet_id = "";
				if(port.fixed_ips.size() > 0) {
					vm_ip = port.fixed_ips.get(0).get("ip_address");
					subnet_id = port.fixed_ips.get(0).get("subnet_id");
				}
				vmlist.append("\"vm_ip\":\""+vm_ip+"\",");
				vmlist.append("\"tenant_id\":\""+port.tenant_id+"\",");
				vmlist.append("\"network_id\":\""+port.network_id+"\",");
				vmlist.append("\"subnet_id\":\""+subnet_id+"\",");
				vmlist.append("\"port_id\":\""+port.portId+"\"");
				vmlist.append("}");
				
				vnicCnt++;
			}
			vmlist.append("]");
			vmlist.append("}");
			
			vmCnt++;
		}
		
//		for(Entry<String, PortDefinition> vmEntry : tunnelManager.getVmByGuid().entrySet()) {
//			if(vmCnt == 0) {
//				vmlist.append("{");
//			} else {
//				vmlist.append(",{");
//			}
//			vmlist.append("\"vm_id\":\""+vmEntry.getValue().device_id+"\",");
//			vmlist.append("\"connected_host\":\""+vmEntry.getValue().binding_host_id+"\",");
//			vmlist.append("\"mac\":\""+vmEntry.getValue().mac_address+"\",");
//			String vm_ip = "";
//			String subnet_id = "";
//			if(vmEntry.getValue().fixed_ips.size() > 0) {
//				vm_ip = vmEntry.getValue().fixed_ips.get(0).get("ip_address");
//				subnet_id = vmEntry.getValue().fixed_ips.get(0).get("subnet_id");
//			}
//			vmlist.append("\"vm_ip\":\""+vm_ip+"\",");
//			vmlist.append("\"tenant_id\":\""+vmEntry.getValue().tenant_id+"\",");
//			vmlist.append("\"network_id\":\""+vmEntry.getValue().network_id+"\",");
//			vmlist.append("\"subnet_id\":\""+subnet_id+"\"");
//			vmlist.append("}");
//			
//			vmCnt++;
//		}
		vmlist.append("]");
		
		return vmlist.toString();
	}

	public String getVmInstance(String portMac) {
		StringBuffer vmInstance = new StringBuffer();

		for (Entry<String, PortDefinition> portEntry : tunnelManager.getVmByGuid().entrySet()) {

			if (portMac.equals(portEntry.getValue().mac_address)) {
				vmInstance.append("{");
				vmInstance.append("\"vm_id\":\"" + portEntry.getValue().device_id + "\",");
				vmInstance.append("\"connected_host\":\"" + portEntry.getValue().binding_host_id + "\",");

				for (IDevice deviceEntry : device.getAllDevices()) {
					if (deviceEntry.getIPv4Addresses().length > 0) {
						String host_ip = IPv4.fromIPv4Address(deviceEntry.getIPv4Addresses()[0]);
						String host_name = tunnelManager.getNodeInfo().containsKey(host_ip) ? tunnelManager.getNodeInfo().get(host_ip).node_name: tunnelManager.getHostName(host_ip);
						String host_mac = deviceEntry.getMACAddressString();
						if (host_name.equals(portEntry.getValue().binding_host_id)) {
							vmInstance.append("\"connected_mac\":\"" + host_mac	+ "\",");
						}
					}
				}

				vmInstance.append("\"vnics\":[");
				vmInstance.append("{");
				vmInstance.append("\"mac\":\"" + portEntry.getValue().mac_address + "\",");
				String vm_ip = "";
				String subnet_id = "";
				if (portEntry.getValue().fixed_ips.size() > 0) {
					vm_ip = portEntry.getValue().fixed_ips.get(0).get("ip_address");
					subnet_id = portEntry.getValue().fixed_ips.get(0).get("subnet_id");
				}
				vmInstance.append("\"vm_ip\":\"" + vm_ip + "\",");
				vmInstance.append("\"tenant_id\":\"" + portEntry.getValue().tenant_id + "\",");
				vmInstance.append("\"network_id\":\"" + portEntry.getValue().network_id + "\",");
				vmInstance.append("\"subnet_id\":\"" + subnet_id + "\",");
				vmInstance.append("\"port_id\":\"" + portEntry.getValue().portId + "\"");
				vmInstance.append("}");
				vmInstance.append("]");
				vmInstance.append("}");
			}
		}

		return vmInstance.toString();
	}
	
	public String getVMportToHost(String portMac) {
		StringBuffer hostConnection = new StringBuffer();
		
		for (Entry<String, PortDefinition> portEntry : tunnelManager.getVmByGuid().entrySet()) {

			if (portMac.equals(portEntry.getValue().mac_address)) {				
				for(IDevice deviceEntry : device.getAllDevices()) {
					String host_ip = IPv4.fromIPv4Address(deviceEntry.getIPv4Addresses()[0]);
					String host_name = tunnelManager.getNodeInfo().containsKey(host_ip) ? tunnelManager.getNodeInfo().get(host_ip).node_name : tunnelManager.getHostName(host_ip);
					String host_mac = deviceEntry.getMACAddressString();
					
					if (host_name.equals(portEntry.getValue().binding_host_id)) {					
						hostConnection.append("{");
						hostConnection.append("\"host_ip\":\"" + host_ip + "\",");
						hostConnection.append("\"host_name\":\"" + host_name + "\",");
						hostConnection.append("\"mac\":\""+host_mac+"\",");
						
						if(deviceEntry.getAttachmentPoints().length > 0) {
							hostConnection.append("\"connected_sw\":\""+HexString.toHexString(deviceEntry.getAttachmentPoints()[0].getSwitchDPID())+"\",");
							hostConnection.append("\"connected_port\":"+deviceEntry.getAttachmentPoints()[0].getPort().getPortNumber());
						} else {
							hostConnection.append("\"connected_sw\":\"\",");
							hostConnection.append("\"connected_port\":\"\"");
						}
						hostConnection.append("}");
					}
					
				}
			}
		}
		
		return hostConnection.toString();
	}
}