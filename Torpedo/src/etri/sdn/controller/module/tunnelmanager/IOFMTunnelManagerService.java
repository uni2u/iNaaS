package etri.sdn.controller.module.tunnelmanager;

import etri.sdn.controller.IService;

public interface IOFMTunnelManagerService extends IService {

	public void addTunnel(String node_ip, String node_name, String node_type, String iris_ip);
	
}
