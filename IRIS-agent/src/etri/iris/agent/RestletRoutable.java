package etri.iris.agent;

import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.routing.Router;

import etri.iris.agent.tunnelmanager.TunnelManagerAgent;

public class RestletRoutable {
	public String basePath() {
        return "/wm";
    }
	
	public Restlet getRestlet(Context context) {
        Router router = new Router(context);
        router.attach("/tunnel/iNaaSAgent", TunnelManagerAgent.class);
        router.attach("/tunnel/iNaaSAgent/{command}", TunnelManagerAgent.class);
        return router;
    }
}
