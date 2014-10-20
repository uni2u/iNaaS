package etri.sdn.controller.module.ovsdb;

import java.util.ArrayList;

import org.codehaus.jackson.annotate.JsonProperty;

import etri.sdn.controller.util.Logger;

public class OVSBridge {

	private BridgeMap newmap;
	private BridgeMap oldmap;
	
	public BridgeMap getNew() {
		return newmap;
	}
	
	public void setNew(BridgeMap newmap) {
		this.newmap = newmap;
	}
	
	public BridgeMap getOld() {
		return oldmap;
	}
	
	public void setOld(BridgeMap oldmap) {
		this.oldmap = oldmap;
	}
	
	@Override
	public String toString() {
		return newmap.toString();
	}
	
	public static class BridgeMap {
		private String name;
		//ports = ["set",[["uuid","xxx"],["uuid","yyy"]]]
		private ArrayList<Object> ports;
		//controller = ["uuid","zzz"] if there is a single controller
		// = ["set", []] if there are none
		// = ["set", [["uuid","zzz"], ["uuid","zxy"]] if more than one
		private ArrayList<Object> controller;
		private Object fail_mode;
		private String datapath_id;
		//other_config = ["map",[["datapath-id","z"],
		//["datapath_type","system"],["tunnel-ip","x.x.x.x"]]]
		private ArrayList<Object> otherConfigArray;
	
		public String getName() {
			return name;
		}
	
		public void setName(String name) {
			this.name = name;
		}
	
		@JsonProperty("datapath_id")
		public String getDPID() {
			return datapath_id;
		}
	
		@JsonProperty("datapath_id")
		public void setDPID(String datapath_id) {
			this.datapath_id = datapath_id;
		}
	
		public ArrayList<Object> getPorts() {
			return ports;
		}
	
		public void setPorts(ArrayList<Object> ports) {
			this.ports = ports;
		}
	
		public ArrayList<Object> getController() {
			return controller;
		}
	
		public void setController(ArrayList<Object> controller) {
			this.controller = controller;
		}
	
		public Object getFail_mode() {
			return fail_mode;
		}
	
		public void setFail_mode(Object fail_mode) {
			this.fail_mode = fail_mode;
		}
	
		@JsonProperty("other_config")
		public ArrayList<Object> getOCArray() {
			return otherConfigArray;
		}
	
		@JsonProperty("other_config")
		public void setOCArray(ArrayList<Object> otherConfigArray) {
			this.otherConfigArray = otherConfigArray;
		}
	
		@Override
		public String toString() {
			return name+" "+controller.toString()+" "+ datapath_id + " "+ports.toString() + getTunnelIPAddress();
		}
	
		@SuppressWarnings("unchecked")
		public ArrayList<String> getPortUuids() {
			ArrayList<String> ulist = new ArrayList<String>();
			String indicator = (String) ports.get(0);
			
			if (indicator.equals("set")) {
				ArrayList<Object> o = (ArrayList<Object>) ports.get(1);
				
				for(int i =0; i<o.size(); i++) {
					ArrayList<String> str = (ArrayList<String>)o.get(i);
					ulist.add(str.get(1)); // 0 is "uuid"
				}
			} else if (indicator.equals("uuid")) {
				ulist.add((String)ports.get(1));
			}
			return ulist;
		}
	
		@SuppressWarnings("unchecked")
		public String getTunnelIPAddress() {
			ArrayList<ArrayList<String>> a = (ArrayList<ArrayList<String>>) otherConfigArray.get(1);
	
			for (int i=0; i<a.size(); i++) {
				if(a.get(i).get(0).equals("tunnel-ip") && a.get(i).size() == 2) {
					return a.get(i).get(1);
				}
			}
			return null;
		}
	
		public long getReportedDpid() {
			String dpidstr = getReportedDpidString();
			if (dpidstr == null) return -1;
			//convert dpidstr to a long value
			return Long.parseLong(dpidstr, 16);
		}
	
		@SuppressWarnings("unchecked")
		public String getReportedDpidString() {
			String dpidstr = null;
			// try other-config column first
			ArrayList<ArrayList<String>> a = (ArrayList<ArrayList<String>>) otherConfigArray.get(1);
	
			for (int i=0; i<a.size(); i++) {
				if(a.get(i).get(0).equals("datapath-id") && a.get(i).size() == 2) {
					dpidstr = a.get(i).get(1);
				}
			}
	
			if (dpidstr != null) {
				if (!dpidstr.equals(getDPID())) {
					Logger.debug("Dpids don't match dpid-col:{}, other-config:{}", getDPID(), dpidstr);
				}
				return dpidstr;
			}
			// try datapath_id column
			return getDPID();
		}
	}
}
