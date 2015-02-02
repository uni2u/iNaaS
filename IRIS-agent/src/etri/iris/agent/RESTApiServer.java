package etri.iris.agent;

import org.restlet.Application;
import org.restlet.Component;
import org.restlet.Restlet;
import org.restlet.data.Protocol;
import org.restlet.routing.Router;

/**
 * This is a REST API server application class 
 * required to create a REST API Server (({@link RESTApiServer}).
 * 
 * @author bjlee
 *
 */
class RestApiServerApplication extends Application {
	
	/**
	 * create an inbound root object.
	 * The type of the object is Router, which is a subclass of Restlet.
	 */
	@Override 
	public Restlet createInboundRoot() {
		Router router = new Router(getContext());
		RestletRoutable rr = new RestletRoutable();
		
		router.attach(rr.basePath(), rr.getRestlet(getContext()));
		
		return router;
	}
}

public class RESTApiServer {
	
	/**
	 * Start the RESTApiServer.
	 */
	public void start(int port) {
	
		// Start listening for REST requests
		try {
			final Component component = new Component();
			component.getServers().add(Protocol.HTTP, port);
			component.getClients().add(Protocol.HTTP);
			component.getDefaultHost().attach(new RestApiServerApplication());
			component.start();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
}
