package etri.sdn.controller.module.ovsdb;

import org.codehaus.jackson.annotate.JsonProperty;

public class OVSController {

	private ControllerMap newmap;
	private ControllerMap oldmap;
	
	public ControllerMap getNew() {
		return newmap;
	}

	public void setNew(ControllerMap newmap) {
		this.newmap = newmap;
	}

	public ControllerMap getOld() {
		return oldmap;
	}

	public void setOld(ControllerMap oldmap) {
		this.oldmap = oldmap;
	}

	@Override
	public String toString() {
		return newmap.toString();
	}

	public static class ControllerMap {
		private String target;
		private boolean isConnected;

		public String getTarget() {
			return target;
		}

		public void setTarget(String target) {
			this.target = target;
		}

		@JsonProperty("is_connected")
		public boolean isConnected() {
			return isConnected;
		}

		@JsonProperty("is_connected")
		public void setConnected(boolean isConnected) {
			this.isConnected = isConnected;
		}

		public String toString() {
			return getTarget()+" "+"isConnected:"+isConnected();
		}
	}
}
