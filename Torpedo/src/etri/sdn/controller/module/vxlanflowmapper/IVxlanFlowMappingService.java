package etri.sdn.controller.module.vxlanflowmapper;

import etri.sdn.controller.IService;

public interface IVxlanFlowMappingService extends IService{
	public int getVnid(String srcMac, String dstMac, String srcIp, String dstIp, int srcUdpPort); 
}
