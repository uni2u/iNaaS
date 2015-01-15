package etri.sdn.controller.module.ml2;

import java.util.List;
import java.util.Map;

public class PortDefinition {
	public String binding_host_id = null;
	public List<String> allowed_address_pairs = null;
	public List<String> extra_dhcp_opts = null;
	public String device_owner = null;
	public Map<String, String> binding_profile = null;
	public List<Map<String, String>> fixed_ips = null;
	public String portId = null;
	public List<Map<String, Object>> security_groups = null;
	public String device_id = null;
	public String portName = null;
	public String admin_state_up = null;
	public String network_id = null;
	public String tenant_id = null;
	public Map<String, String> binding_vif_details = null;
	public String binding_vnic_type = null;
	public String binding_vif_type = null;
	public String mac_address = null;
	public Boolean flow_exec = false;
	
	public PortDefinition() {
		
	}
	
	public PortDefinition(VirtualPort vPort) {
		this.binding_host_id = vPort.getBinding_host_id();
		this.allowed_address_pairs = vPort.getAllowed_address_pairs();
		this.extra_dhcp_opts = vPort.getExtra_dhcp_opts();
		this.device_owner = vPort.getDevice_owner();
		this.binding_profile = vPort.getBinding_profile();
		this.fixed_ips = vPort.getFixed_ips();
		this.portId = vPort.getPortId();
		this.security_groups = vPort.getSecurity_groups();
		this.device_id = vPort.getDevice_id();
		this.portName = vPort.getPortName();
		this.admin_state_up = vPort.getAdmin_state_up();
		this.network_id = vPort.getNetwork_id();
		this.tenant_id = vPort.getTenant_id();
		this.binding_vif_details = vPort.getBinding_vif_details();
		this.binding_vnic_type = vPort.getBinding_vnic_type();
		this.binding_vif_type = vPort.getBinding_vif_type();
		this.mac_address = vPort.getMac_address();
		this.flow_exec = vPort.getFlow_exec();
	}

}
