package etri.sdn.controller.module.ovsdb;

import java.util.ArrayList;

import org.codehaus.jackson.annotate.JsonProperty;

public class OVSPort {

	private PortMap newmap;
	private PortMap oldmap;
	
	public PortMap getNew() {
		return newmap;
	}
	
	public void setNew(PortMap newmap) {
		this.newmap = newmap;
	}
	
	public PortMap getOld() {
		return oldmap;
	}
	
	public void setOld(PortMap oldmap) {
		this.oldmap = oldmap;
	}
	
	@Override
	public String toString() {
		return newmap.toString();
	}
	
	public static class PortMap {
		private String name;
		private boolean fake_bridge;
		//trunks maps to an array ["set",[]]
		private Object trunks;
		//interfaces maps to an array ["uuid","6accdb5a-2746-4899b04d-13a54e5628a2"]
		private ArrayList<String> interfaces;
		//tag maps to an array ["set",[]]
		private Object tag;
		public Object getTrunks() {
			return trunks;
		}
	
		public void setTrunks(Object trunks) {
			this.trunks = trunks;
		}
	
		public ArrayList<String> getInterfaces() {
			return interfaces;
		}
	
		public void setInterfaces(ArrayList<String> interfaces) {
			this.interfaces = interfaces;
		}
	
		public String getName() {
			return name;
		}
	
		public void setName(String name) {
			this.name = name;
		}
	
		public Object getTag() {
			return tag;
		}
	
		public void setTag(Object tag) {
			this.tag = tag;
		}
	
		@JsonProperty("fake_bridge")
		public boolean isFake_bridge() {
			return fake_bridge;
		}
	
		@JsonProperty("fake_bridge")
		public void setFake_bridge(boolean fake_bridge) {
			this.fake_bridge = fake_bridge;
		}
	
		@Override
		public String toString() {
			return "name:" + name + " " + "interface:"+getInterfaces().get(1);
		}
	}
}
