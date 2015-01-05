package etri.sdn.controller.module.vxlanflowmapper;

import java.util.List;

public class P2VRemovedResponse {
	public List<HeaderInfoPair> p2vMapRemovedList;

	public P2VRemovedResponse(List<HeaderInfoPair> p2vMapRemovedList) {
		super();
		this.p2vMapRemovedList = p2vMapRemovedList;
	}

	public List<HeaderInfoPair> getP2vMapRemovedList() {
		return p2vMapRemovedList;
	}

	public void setP2vMapRemovedList(List<HeaderInfoPair> p2vMapRemovedList) {
		this.p2vMapRemovedList = p2vMapRemovedList;
	}
	
}
