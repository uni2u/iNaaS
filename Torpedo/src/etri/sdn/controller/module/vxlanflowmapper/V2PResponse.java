package etri.sdn.controller.module.vxlanflowmapper;

import java.util.List;

public class V2PResponse {
	public List<HeaderInfoPair> v2pMapList;

	public V2PResponse(List<HeaderInfoPair> v2pMapList) {
		super();
		this.v2pMapList = v2pMapList;
	}

	public List<HeaderInfoPair> getV2pMapList() {
		return v2pMapList;
	}

	public void setV2pMapList(List<HeaderInfoPair> v2pMapList) {
		this.v2pMapList = v2pMapList;
	}
	
}