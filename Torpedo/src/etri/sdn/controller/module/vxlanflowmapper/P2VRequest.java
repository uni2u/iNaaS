package etri.sdn.controller.module.vxlanflowmapper;

import java.util.List;

public class P2VRequest {
	
	private List<OuterPacketHeader> outerList;

	public P2VRequest(List<OuterPacketHeader> outerList) {
		super();
		this.outerList = outerList;
	}

	public List<OuterPacketHeader> getOuterList() {
		return outerList;
	}

	public void setOuterList(List<OuterPacketHeader> outerList) {
		this.outerList = outerList;
	}
	
}