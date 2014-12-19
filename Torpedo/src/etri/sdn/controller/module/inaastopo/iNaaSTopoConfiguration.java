package etri.sdn.controller.module.inaastopo;

import java.util.Arrays;

import etri.sdn.controller.OFModel;

class iNaaSTopoConfiguration extends OFModel {

//	private OFMOpenstackML2Connector parent = null;
//	private RESTApi[] apis = null;
//
//	public iNaaSTopoConfiguration(OFMOpenstackML2Connector parent) 
//	{
//		this.parent = parent;
//		this.apis = Arrays.asList(
//			new RESTApi(RestNetwork.neutronNetAll, new RestNetwork(this)),
//			new RESTApi(RestNetwork.neutronNet, new RestNetwork(this)),
//			new RESTApi(RestNetwork.neutronNetIRIS, new RestNetwork(this)),
//			new RESTApi(RestPort.neutronPortAll, new RestPort(this)),
//			new RESTApi(RestPort.neutronPort, new RestPort(this)),
//			new RESTApi(RestPort.neutronPortIRIS, new RestPort(this)),
//			new RESTApi(RestSubnet.neutronSubnetAll, new RestSubnet(this)),
//			new RESTApi(RestSubnet.neutronSubnet, new RestSubnet(this)),
//			new RESTApi(RestSubnet.neutronSubnetIRIS, new RestSubnet(this))
//		).toArray( new RESTApi[0] );
//	}
//
//	OFMOpenstackML2Connector getModule() {
//		return this.parent;
//	}

	@Override
	public RESTApi[] getAllRestApi() {
//		return this.apis;
		return null;
	}

}
