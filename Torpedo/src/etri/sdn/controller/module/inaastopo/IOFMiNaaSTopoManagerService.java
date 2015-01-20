package etri.sdn.controller.module.inaastopo;

import etri.sdn.controller.IService;

public interface IOFMiNaaSTopoManagerService extends IService {
	public String getINaaSTopoAll();
	
	public String getVmInstance(String portMac);
	public String getVMportToHost(String portMac);
}
