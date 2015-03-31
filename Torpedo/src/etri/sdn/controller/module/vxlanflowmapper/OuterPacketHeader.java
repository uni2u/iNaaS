package etri.sdn.controller.module.vxlanflowmapper;
import com.google.common.base.MoreObjects;

public class OuterPacketHeader {
	
	private String srcMac;
	private String dstMac;
	private String srcIp;
	private String dstIp;
	private String srcUdpPort;
	
	public OuterPacketHeader() {
		
	}
	
	public OuterPacketHeader(String srcMac, String dstMac, String srcIP, String dstIP, String srcUDPPort) {
		super();
		this.srcMac = srcMac;
		this.dstMac = dstMac;
		this.srcIp = srcIP;
		this.dstIp = dstIP;
		this.srcUdpPort = srcUDPPort;
	}
	
	public String toString() {
		return MoreObjects.toStringHelper(this)
				.add("srcMac", srcMac)
				.add("dstMac", dstMac)
				.add("srcIp", srcIp)
				.add("dstIp", dstIp)
				.add("srcUdpPort", srcUdpPort)
				.toString();
	}
	
	public String getSrcMac() {
		return srcMac;
	}

	public void setSrcMac(String srcMac) {
		this.srcMac = srcMac;
	}

	public String getDstMac() {
		return dstMac;
	}

	public void setDstMac(String dstMac) {
		this.dstMac = dstMac;
	}

	public String getSrcIp() {
		return srcIp;
	}

	public void setSrcIp(String srcIp) {
		this.srcIp = srcIp;
	}

	public String getDstIp() {
		return dstIp;
	}

	public void setDstIp(String dstIp) {
		this.dstIp = dstIp;
	}

	public String getSrcUdpPort() {
		return srcUdpPort;
	}

	public void setSrcUdpPort(String srcUdpPort) {
		this.srcUdpPort = srcUdpPort;
	}



	public static class Builder {
		String srcMac;
		String dstMac;
		String srcIp;
		String dstIp;
		String srcUdpPort;
		
		public Builder srcMac(String mac) {
			this.srcMac = mac;
			return this;
		}
		
		public Builder dstMac(String mac) {
			this.dstMac = mac;
			return this;
		}
		
		public Builder srcIp(String ip) {
			this.srcIp = ip;
			return this;
		}
		
		public Builder dstIp(String ip) {
			this.dstIp = ip;
			return this;
		}
		public Builder udpPort(String port) {
			this.srcUdpPort = port;
			return this;
		}
		
		public OuterPacketHeader build() {
			return new OuterPacketHeader(srcMac, dstMac, srcIp, dstIp, srcUdpPort);
		}
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((dstIp == null) ? 0 : dstIp.hashCode());
		result = prime * result + ((dstMac == null) ? 0 : dstMac.hashCode());
		result = prime * result + ((srcIp == null) ? 0 : srcIp.hashCode());
		result = prime * result + ((srcMac == null) ? 0 : srcMac.hashCode());
		result = prime * result + ((srcMac == null) ? 0 :srcUdpPort.hashCode());
		//result = prime * result + ((vnid == null) ? 0 : vnid.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		
		if (obj == null) {
			return false;
		}
		if (!(obj instanceof OuterPacketHeader)) {
			return false;
		}
		OuterPacketHeader other = (OuterPacketHeader) obj;
		if (dstIp == null) {
			if (other.dstIp != null) {
				return false;
			}
		} else if (!dstIp.equals(other.dstIp)) {
			return false;
		}
		if (dstMac == null) {
			if (other.dstMac != null) {
				return false;
			}
		} else if (!dstMac.equals(other.dstMac)) {
			return false;
		}
		if (srcIp == null) {
			if (other.srcIp != null) {
				return false;
			}
		} else if (!srcIp.equals(other.srcIp)) {
			return false;
		}
		if (srcMac == null) {
			if (other.srcMac != null) {
				return false;
			}
		} else if (!srcMac.equals(other.srcMac)) {
			return false;
		}
		if (srcUdpPort != other.srcUdpPort) {
			return false;
		}
//		if (vnid == null) {
//			if (other.vnid != null) {
//				return false;
//			}
//		} else if (!vnid.equals(other.vnid)) {
//			return false;
//		}
		return true;
	}
	public static void main(String[] args) {
	
	}
}