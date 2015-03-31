package etri.sdn.controller.module.vxlanflowmapper;

public class HeaderInfoPair {
	public OuterPacketHeader outer;
	public OrginalPacketHeader orginal;
	public HeaderInfoPair(OuterPacketHeader outer, OrginalPacketHeader orginal) {
		super();
		this.outer = outer;
		this.orginal = orginal;
	}
}