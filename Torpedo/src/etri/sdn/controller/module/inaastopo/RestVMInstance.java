package etri.sdn.controller.module.inaastopo;

import org.restlet.Request;
import org.restlet.Response;
import org.restlet.Restlet;
import org.restlet.data.MediaType;
import org.restlet.data.Method;
import org.restlet.data.Status;

public class RestVMInstance extends Restlet {
	
	static String iNaaSVMlist = "/wm/inaas/topology/vm/{portMac}";
	
	private iNaaSTopoConfiguration parent = null;

	RestVMInstance(iNaaSTopoConfiguration parent) {
		this.parent = parent;
	}

	iNaaSTopoConfiguration getModel() {
		return this.parent;
	}
	
	@Override
	public void handle(Request request, Response response) {

		Method m = request.getMethod();
		
		String portMac = request.getAttributes().get("portMac") == null ? "" : (String) request.getAttributes().get("portMac");
		
		if (m == Method.GET) {
			response.setEntity(parent.getModule().getVmInstance(portMac), MediaType.APPLICATION_JSON);
			response.setStatus(Status.SUCCESS_OK);
			
		}
	}
}
