package etri.sdn.controller.module.inaastopo;

import org.restlet.Request;
import org.restlet.Response;
import org.restlet.Restlet;
import org.restlet.data.MediaType;
import org.restlet.data.Method;
import org.restlet.data.Status;

public class RestiNaaSTopo extends Restlet {
	
	static String iNaaSTopoAll = "/wm/inaas/topology/json";
	
	private iNaaSTopoConfiguration parent = null;

	RestiNaaSTopo(iNaaSTopoConfiguration parent) {
		this.parent = parent;
	}

	iNaaSTopoConfiguration getModel() {
		return this.parent;
	}
	
	@Override
	public void handle(Request request, Response response) {
System.out.println("><><><><><>");

		Method m = request.getMethod();
		
		if (m == Method.GET) {
			response.setEntity(parent.getModule().getINaaSTopoAll(), MediaType.APPLICATION_JSON);
			response.setStatus(Status.SUCCESS_OK);
		}
	}
}
