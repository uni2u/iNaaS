package etri.sdn.controller.module.vxlanflowmapper;

import java.util.List;

public class P2VDelResponse {
	public List<HeaderInfoPair> p2vMapDelList;

	public P2VDelResponse(List<HeaderInfoPair> p2vMapDelList) {
		super();
		this.p2vMapDelList = p2vMapDelList;
	}

	public List<HeaderInfoPair> getP2vMapDelList() {
		return p2vMapDelList;
	}

	public void setP2vMapDelList(List<HeaderInfoPair> p2vMapDelList) {
		this.p2vMapDelList = p2vMapDelList;
	}
	
}
