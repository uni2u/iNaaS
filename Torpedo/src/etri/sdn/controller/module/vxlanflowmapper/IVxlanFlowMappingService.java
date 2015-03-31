package etri.sdn.controller.module.vxlanflowmapper;

import org.projectfloodlight.openflow.protocol.OFMessage;

import etri.sdn.controller.IService;

public interface IVxlanFlowMappingService extends IService{
	public int getVnid(String srcMac, String dstMac, String srcIp, String dstIp, int srcUdpPort); 
	public int getVnid(OFMessage msg);
}