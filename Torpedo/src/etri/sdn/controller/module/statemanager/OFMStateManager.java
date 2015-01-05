package etri.sdn.controller.module.statemanager;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowStatsEntry;
import org.projectfloodlight.openflow.protocol.OFFlowStatsReply;
import org.projectfloodlight.openflow.protocol.OFFlowStatsRequest;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFStatsReply;
import org.projectfloodlight.openflow.types.OFGroup;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TableId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import etri.sdn.controller.IService;
import etri.sdn.controller.MessageContext;
import etri.sdn.controller.OFModel;
import etri.sdn.controller.OFModule;
import etri.sdn.controller.protocol.OFProtocol;
import etri.sdn.controller.protocol.io.Connection;
import etri.sdn.controller.protocol.io.IOFSwitch;
import etri.sdn.controller.util.StackTrace;

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
public class OFMStateManager extends OFModule implements IStateService {
	
	static final Logger logger = LoggerFactory.getLogger(OFMStateManager.class);
	
	/**
	 * Model of this module. initialized by {@link #initialize()}.
	 */
	private State state;

	private OFProtocol protocol;
	
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
		protocol = (OFProtocol)getController().getProtocol();
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

	@Override
	public List<OFFlowStatsEntry> getFlows(Long switchId) {
		// TODO Auto-generated method stub
		IOFSwitch sw = getController().getSwitch(switchId);
		if ( sw == null ) {
			return null;		// switch is not completely set up.
		}
		
		OFFactory fac = OFFactories.getFactory(sw.getVersion());
		
//		HashMap<String, List<OFFlowStatsEntry>> result = 
//			new HashMap<String, List<OFFlowStatsEntry>>();
		List<OFFlowStatsEntry> resultValues = 
			new java.util.LinkedList<OFFlowStatsEntry>();
//		result.put(switchId.toHexString(switchId), resultValues);
							
		OFFlowStatsRequest.Builder req = fac.buildFlowStatsRequest();
		req
		.setMatch( fac.matchWildcardAll() )
		.setOutPort( OFPort.ANY /* NONE for 1.0*/ );
		try {
			req
			.setOutGroup(OFGroup.ANY)
			.setTableId(TableId.ALL);
		} catch ( UnsupportedOperationException u ) {}

		try { 
			List<OFStatsReply> reply = protocol.getSwitchStatistics(sw, req.build());
			for ( OFStatsReply s : reply ) {
				if ( s instanceof OFFlowStatsReply ) {
					resultValues.addAll( ((OFFlowStatsReply)s).getEntries() );
				}
			}
		} catch ( Exception e ) {
			OFMStateManager.logger.error("error={}", StackTrace.of(e));
			return null;
		}
//		System.out.println("++++++++++++++++++++ Flow States: swid = 00:00:0a:14:99:ae:ba:4c " + resultValues);
		return resultValues;
	}

	@Override
	public List<OFFlowStatsEntry> getFlows(Long switchId, Long ethSrc,
			Long ethDst) {
		// TODO Auto-generated method stub
		IOFSwitch sw = getController().getSwitch(switchId);
		if ( sw == null ) {
			return null;		// switch is not completely set up.
		}
		
		OFFactory fac = OFFactories.getFactory(sw.getVersion());
		
//		HashMap<String, List<OFFlowStatsEntry>> result = 
//			new HashMap<String, List<OFFlowStatsEntry>>();
		List<OFFlowStatsEntry> resultValues = 
			new java.util.LinkedList<OFFlowStatsEntry>();
//		result.put(switchId.toHexString(switchId), resultValues);
							
		OFFlowStatsRequest.Builder req = fac.buildFlowStatsRequest();
		req
		.setMatch( fac.matchWildcardAll() )
		.setOutPort( OFPort.ANY /* NONE for 1.0*/ );
		try {
			req
			.setOutGroup(OFGroup.ANY)
			.setTableId(TableId.ALL);
		} catch ( UnsupportedOperationException u ) {}

		try { 
			List<OFStatsReply> reply = protocol.getSwitchStatistics(sw, req.build());
			for ( OFStatsReply s : reply ) {
				if ( s instanceof OFFlowStatsReply ) {
					resultValues.addAll( ((OFFlowStatsReply)s).getEntries() );
				}
			}
		} catch ( Exception e ) {
			OFMStateManager.logger.error("error={}", StackTrace.of(e));
			return null;
		}
//		System.out.println("++++++++++++++++++++ Flow States: swid = 00:00:0a:14:99:ae:ba:4c " + resultValues);
		return resultValues;
		
	}

	@Override
	public List<OFStatsReply> getAggregate(Long switchId) {
		// TODO Auto-generated method stub
		return null;
	}

}
