package etri.sdn.controller.module.statemanager;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.projectfloodlight.openflow.protocol.OFMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import etri.sdn.controller.IService;
import etri.sdn.controller.MessageContext;
import etri.sdn.controller.OFModel;
import etri.sdn.controller.OFModule;
import etri.sdn.controller.protocol.io.Connection;

/**
 * This module does not handle any OFMessage.
 * The purpose of this module is to handle REST calls 
 * that request status-related information including:
 * (1) controller health 
 * (2) switch description
 * (3) port information
 * (4) aggregated flow statistics 
 * 
 * @author bjlee
 *
 */
public class OFMStateManager extends OFModule {
	
	static final Logger logger = LoggerFactory.getLogger(OFMStateManager.class);
	
	/**
	 * Model of this module. initialized by {@link #initialize()}.
	 */
	private State state;
	
	@Override
	protected Collection<Class<? extends IService>> services() {
		// no service to implement
		return Collections.emptyList();
	}

	/**
	 * initialize the model object of this module.
	 */
	@Override
	protected void initialize() {
		state = new State(this);
	}

	/**
	 * Does nothing except for returning true.
	 */
	@Override
	protected boolean handleHandshakedEvent(Connection conn,
			MessageContext context) {
		return true;
	}

	/**
	 * Does nothing except for returning true.
	 */
	@Override
	protected boolean handleMessage(Connection conn, MessageContext context,
			OFMessage msg, List<OFMessage> outgoing) {
		return true;
	}

	/**
	 * Does nothing except for returning true.
	 */
	@Override
	protected boolean handleDisconnect(Connection conn) {
		return true;
	}

	/**
	 * return the model object {@link #state}.
	 */
	@Override
	public OFModel[] getModels() {
		return new OFModel[] { this.state };
	}

}
