package etri.sdn.controller.module.tunnelmanager;

import java.util.Map;

import etri.sdn.controller.IService;

public interface IOFMTunnelManagerService extends IService {

	public void addTunnel(String node_ip_mgt, String node_ip_tun, String node_name, String node_type, String iris_ip);
	
	public Map<Long, String> getBridgeDpid();
}