package etri.sdn.controller.module.vxlanflowmapper;

import java.util.List;

public class V2PRequest {
	
	private List<OrginalPacketHeader> orginalList;
	
	public V2PRequest(List<OrginalPacketHeader> orginalList) {
		super();
		this.orginalList = orginalList;
	}
	public List<OrginalPacketHeader> getOrginalList() {
		return orginalList;
	}

	public void setOrginalList(List<OrginalPacketHeader> orginalList) {
		this.orginalList = orginalList;
	}
}
