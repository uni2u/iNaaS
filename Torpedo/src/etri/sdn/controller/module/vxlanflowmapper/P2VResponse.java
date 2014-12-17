package etri.sdn.controller.module.vxlanflowmapper;

import java.util.List;

public class P2VResponse {
	public List<HeaderInfoPair> p2vMapList;

	public P2VResponse(List<HeaderInfoPair> p2vMapList) {
		super();
		this.p2vMapList = p2vMapList;
	}

	public List<HeaderInfoPair> getP2vMapList() {
		return p2vMapList;
	}

	public void setP2vMapList(List<HeaderInfoPair> p2vMapList) {
		this.p2vMapList = p2vMapList;
	}
	
}
