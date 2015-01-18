package etri.sdn.controller.module.statemanager;


import java.util.HashMap;
import java.util.List;

import org.projectfloodlight.openflow.protocol.OFFlowStatsEntry;
import org.projectfloodlight.openflow.protocol.OFStatsReply;

import etri.sdn.controller.IService;


public interface IStateService extends IService {
	public List<OFFlowStatsEntry>  getFlows(Long switchId); 
	public List<OFFlowStatsEntry>  getFlows(Long switchId, Long ethSrc, Long ethDst);
	public List<OFFlowStatsEntry>  getFlows(Long switchId, int in_port, int out_port);
	public List<OFFlowStatsEntry>  getFlows(Long switchId, HashMap<String, String> matchMap);
	public List<OFStatsReply>  getAggregate(Long switchId); 
}
