package etri.sdn.controller.module.vxlanflowmapper;

import com.google.common.base.MoreObjects;

public class OrginalPacketHeader {
	private String srcVmMac;
	private String dstVmMac;
	private String srcVmIp;
	private String dstVmIp;
	private String vnid;
	private String etherType;	// 0x0800	Internet Protocol version 4 (IPv4)

	public OrginalPacketHeader() {
		
	}
	
	public OrginalPacketHeader(String srcVmMac, String dstVmMac, String srcVmIP, String dstVmIP, String vnid, String etherType) {
		super();
		this.srcVmMac = srcVmMac;
		this.dstVmMac = dstVmMac;
		this.srcVmIp = srcVmIP;
		this.dstVmIp = dstVmIP;
		this.vnid = vnid;
		this.etherType = etherType;
	}
	
	public String toString() {
		return MoreObjects.toStringHelper(this)
				.add("srcMac", srcVmMac)
				.add("dstMac", dstVmMac)
				.add("srcIp", srcVmIp)
				.add("dstIp", dstVmIp)
				.add("vnid", vnid)
				.add("etherType", etherType)
				.toString();
	}
	
	public String getSrcVmMac() {
		return srcVmMac;
	}
	
	public void setSrcVmMac(String srcVmMac) {
		this.srcVmMac = srcVmMac;
	}
	
	public String getDstVmMac() {
		return dstVmMac;
	}
	
	public void setDstVmMac(String dstVmMac) {
		this.dstVmMac = dstVmMac;
	}
	
	public String getSrcVmIp() {
		return srcVmIp;
	}
	
	public void setSrcVmIp(String srcVmIp) {
		this.srcVmIp = srcVmIp;
	}
	public String getDstVmIp() {
		return dstVmIp;
	}
	public void setDstVmIp(String dstVmIp) {
		this.dstVmIp = dstVmIp;
	}
	public String getVnid() {
		return vnid;
	}
	public void setVnid(String vnid) {
		this.vnid = vnid;
	}

	public String getEtherType() {
		return etherType;
	}

	public void setEtherType(String etherType) {
		this.etherType = etherType;
	}

	public static class Builder {
		String srcVmMac;
		String dstVmMac;
		String srcVmIp;
		String dstVmIp;
		String vnid;
		String etherType;
		
		public Builder srcMac(String mac) {
			this.srcVmMac = mac;
			return this;
		}
		
		public Builder dstMac(String mac) {
			this.dstVmMac = mac;
			return this;
		}
		
		public Builder srcIp(String ip) {
			this.srcVmIp = ip;
			return this;
		}
		
		public Builder dstIp(String ip) {
			this.dstVmIp = ip;
			return this;
		}
		public Builder vnid(String vnid) {
			this.vnid = vnid;
			return this;
		}
		public Builder etherType(String type) {
			this.etherType = type;
			return this;
		}
		public OrginalPacketHeader build() {
			return new OrginalPacketHeader(srcVmMac, dstVmMac, srcVmIp, dstVmIp, vnid, etherType);
		}
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((dstVmIp == null) ? 0 : dstVmIp.hashCode());
		result = prime * result + ((dstVmMac == null) ? 0 : dstVmMac.hashCode());
		result = prime * result + ((srcVmIp == null) ? 0 : srcVmIp.hashCode());
		result = prime * result + ((srcVmMac == null) ? 0 : srcVmMac.hashCode());
		result = prime * result + ((srcVmMac == null) ? 0 :vnid.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		
		if (obj == null) {
			return false;
		}
		if (!(obj instanceof OrginalPacketHeader)) {
			return false;
		}
		OrginalPacketHeader other = (OrginalPacketHeader) obj;
		if (dstVmIp == null) {
			if (other.dstVmIp != null) {
				return false;
			}
		} else if (!dstVmIp.equals(other.dstVmIp)) {
			return false;
		}
		if (dstVmMac == null) {
			if (other.dstVmMac != null) {
				return false;
			}
		} else if (!dstVmMac.equals(other.dstVmMac)) {
			return false;
		}
		if (srcVmIp == null) {
			if (other.srcVmIp != null) {
				return false;
			}
		} else if (!srcVmIp.equals(other.srcVmIp)) {
			return false;
		}
		if (srcVmMac == null) {
			if (other.srcVmMac != null) {
				return false;
			}
		} else if (!srcVmMac.equals(other.srcVmMac)) {
			return false;
		}
		if(vnid != other.vnid) {
			return false;
		}
		return true;
	}
}
