package etri.sdn.controller.module.inaastopo;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFType;

import etri.sdn.controller.IService;
import etri.sdn.controller.MessageContext;
import etri.sdn.controller.OFMFilter;
import etri.sdn.controller.OFModel;
import etri.sdn.controller.OFModule;
import etri.sdn.controller.module.devicemanager.IDeviceService;
import etri.sdn.controller.module.routing.IRoutingDecision;
import etri.sdn.controller.protocol.io.Connection;
import etri.sdn.controller.protocol.io.IOFSwitch;

public class OFMiNaaSTopoManager extends OFModule implements IOFMiNaaSTopoManagerService {

	private iNaaSTopoConfiguration topoConf = null;
	
	protected IDeviceService device;
	
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
		
		String switchlist = "";
		
		if(getController().getSwitches().size() > 0) {
			switchlist += "\"switchlist\":[";
			int switchCnt = 0;
			for (IOFSwitch sw : getController().getSwitches()) {
				System.out.println(sw.getId());
				if(switchCnt == 0) {
					switchlist += "{\"dpid\":\"" + sw.getId() + "\"}";
				} else {
					switchlist += ",{\"dpid\":\"" + sw.getId() + "\"}";
				}
				switchCnt++;
			}
			switchlist += "]";
		}
		
System.out.println(">>>>> switchlist : " + switchlist);
		
		
		
//		device.getAllDevices();
//		
//		device.
		
		
		return "";
	}
	
	
}
