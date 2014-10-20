package etri.sdn.controller.module.ovsdb;

import java.util.ArrayList;

public class OVSInterface {

	private InterfaceMap newmap;
	private InterfaceMap oldmap;
	
	public InterfaceMap getNew() {
		return newmap;
	}
	
	public void setNew(InterfaceMap newmap) {
		this.newmap = newmap;
	}
	
	public InterfaceMap getOld() {
		return oldmap;
	}
	
	public void setOld(InterfaceMap oldmap) {
		this.oldmap = oldmap;
	}
	
	@Override
	public String toString() {
		return newmap.toString();
	}
	
	public static class InterfaceMap {
		private String name;
		private String type;
		//options = ["map",[["remote_ip","10.20.30.40"]]]
		private ArrayList<Object> options;
	
		public String getName() {
			return name;
		}
	
		public void setName(String name) {
			this.name = name;
		}
	
		public String getType() {
			return type;
		}
	
		public void setType(String type) {
			this.type = type;
		}
	
		public ArrayList<Object> getOptions() {
			return options;
		}
	
		public void setOptions(ArrayList<Object> options) {
			this.options = options;
		}
	
		@Override
		public String toString() {
			return name+" "+type+" "+options.toString();
		}
	
		public String getRemoteIP() {
			if(options.toString().contains("remote_ip")) {
				int index = options.toString().indexOf("remote_ip");
				String left = options.toString().substring(index+11);
				return left.substring(0, left.indexOf("]"));
			} else {
				return null;
			}
		}
	
		@SuppressWarnings("unchecked")
		public ArrayList<String> getRemoteIPs() {
			ArrayList<String> iplist = new ArrayList<String>();
			ArrayList<Object> o = (ArrayList<Object>) options.get(1);
			
			for(int i =0; i<o.size(); i++) {
				ArrayList<String> str = (ArrayList<String>)o.get(i);
				iplist.add(str.get(1)); // 0 is "remote_ip"
			}
			return iplist;
		}
	}
}
