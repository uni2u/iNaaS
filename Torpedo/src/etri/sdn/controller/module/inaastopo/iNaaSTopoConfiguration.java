package etri.sdn.controller.module.inaastopo;

import java.util.Arrays;

import etri.sdn.controller.OFModel;

class iNaaSTopoConfiguration extends OFModel {

	private OFMiNaaSTopoManager parent = null;
	private RESTApi[] apis = null;

	public iNaaSTopoConfiguration(OFMiNaaSTopoManager parent) 
	{
		this.parent = parent;
		this.apis = Arrays.asList(
			new RESTApi(RestiNaaSTopo.iNaaSTopoAll, new RestiNaaSTopo(this))
		).toArray( new RESTApi[0] );
	}

	OFMiNaaSTopoManager getModule() {
		return this.parent;
	}

	@Override
	public RESTApi[] getAllRestApi() {
		return this.apis;
	}

}
