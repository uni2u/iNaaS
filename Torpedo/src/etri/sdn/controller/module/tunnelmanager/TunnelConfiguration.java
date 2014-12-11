package etri.sdn.controller.module.tunnelmanager;

import java.util.Arrays;

import etri.sdn.controller.OFModel;

public class TunnelConfiguration extends OFModel {

	private OFMTunnelManager parent = null;
	private RESTApi[] apis = null;
	
	public TunnelConfiguration(OFMTunnelManager parent) {
		this.parent = parent;
		this.apis = Arrays.asList(
			new RESTApi(RestTunnel.nodeInfo, new RestTunnel(this))
		).toArray( new RESTApi[0] );
	}
	
	public OFMTunnelManager getModule() {
		return this.parent;
	}
	
	@Override
	public RESTApi[] getAllRestApi() {
		return this.apis;
	}
}
