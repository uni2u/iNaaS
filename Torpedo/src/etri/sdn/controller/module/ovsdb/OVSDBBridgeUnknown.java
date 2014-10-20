package etri.sdn.controller.module.ovsdb;

public class OVSDBBridgeUnknown extends Exception {

	private static final long serialVersionUID = 1L;
	long dpid;
	
	public long getDpid() {
		return dpid;
	}
	
	public void setDpid(long dpid) {
		this.dpid = dpid;
	}
	
	public OVSDBBridgeUnknown(long dpid) {
		this.dpid = dpid;
	}
}
