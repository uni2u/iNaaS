package etri.sdn.controller.module.tunnelmanager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowDelete;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionNiciraResubmitTable;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TableId;
import org.projectfloodlight.openflow.types.U64;

import etri.sdn.controller.protocol.io.IOFSwitch;

public class TunnelFlow {
	
	public static final String PATCH_LV_TO_TUN = "1";
	public static final String GRE_TUN_TO_LV = "2";
	public static final String VXLAN_TUN_TO_LV = "3";
	public static final String LEARN_FROM_TUN = "10";
	public static final String UCAST_TO_TUN = "20";
	public static final String FLOOD_TO_TUN = "21";
	public static final String CANARY_TABLE = "22";
	
	private static final short IDLE_TIMEOUT_DEFAULT = 0;
	private static final short HARD_TIMEOUT_DEFAULT = 0;
	private static final short PRIORITY_DEFAULT = 0;
	
	public static void add_integration_flow(IOFSwitch sw) {
		remove_all_flows(sw);
		
		Map<String, Object> entry = new ConcurrentHashMap<String, Object>();
		Map<String, Object> action_entry = new ConcurrentHashMap<String, Object>();
		
		entry.clear();
		entry.put("priority", 1);
		action_entry.clear();
		action_entry.put("normal", "");
		entry.put("action_entry", action_entry);
		
		add_flow(sw, entry);
		
		
		entry.clear();
		entry.put("table", 22);
		action_entry.clear();
		action_entry.put("drop", "");
		entry.put("action_entry", action_entry);
		
		add_flow(sw, entry);
		
	}
	
	public static void add_tunnel_flow(IOFSwitch sw, String patch_int_ofport) {
		remove_all_flows(sw);
		
//		add_flow(sw, );
		
//		// Table 0 (default) will sort incoming traffic depending on in_port
//		add_flow(priority=1, in_port=patch_int_ofport,actions="resubmit(,%s)" % PATCH_LV_TO_TUN);
//		add_flow(priority=0, actions="drop");
//		// PATCH_LV_TO_TUN table will handle packets coming from patch_int
//		// unicasts go to table UCAST_TO_TUN where remote adresses are learnt
//		add_flow(table=PATCH_LV_TO_TUN, dl_dst="00:00:00:00:00:00/01:00:00:00:00:00", actions="resubmit(,%s)" % UCAST_TO_TUN);
//		// Broadcasts/multicasts go to table FLOOD_TO_TUN that handles flooding
//		add_flow(table=PATCH_LV_TO_TUN, dl_dst="01:00:00:00:00:00/01:00:00:00:00:00", actions="resubmit(,%s)" % FLOOD_TO_TUN);
//		// Tables [tunnel_type]_TUN_TO_LV will set lvid depending on tun_id
//		// for each tunnel type, and resubmit to table LEARN_FROM_TUN where
//		// remote mac adresses will be learnt
//		if("gre".equals(TUNNEL_TYPE) || "vxlan".equals(TUNNEL_TYPE)) {
//			add_flow(table=GRE_TUN_TO_LV, priority=0, actions="drop");
//			add_flow(table=VXLAN_TUN_TO_LV, priority=0, actions="drop");
//		}
//		// LEARN_FROM_TUN table will have a single flow using a learn action to
//		// dynamically set-up flows in UCAST_TO_TUN corresponding to remote mac
//		// adresses (assumes that lvid has already been set by a previous flow)
//		String learned_flow = "table="+UCAST_TO_TUN+" priority=1, hard_timeout=300, NXM_OF_VLAN_TCI[0..11], NXM_OF_ETH_DST[]=NXM_OF_ETH_SRC[], load:0->NXM_OF_VLAN_TCI[], load:NXM_NX_TUN_ID[]->NXM_NX_TUN_ID[], output:NXM_OF_IN_PORT[]";
//		add_flow(table=LEARN_FROM_TUN, priority=1, actions="learn("+learned_flow+"),output:"+patch_int_ofport);
//		// Egress unicast will be handled in table UCAST_TO_TUN, where remote
//		// mac adresses will be learned. For now, just add a default flow that
//		// will resubmit unknown unicasts to table FLOOD_TO_TUN to treat them
//		// as broadcasts/multicasts
//		add_flow(table=UCAST_TO_TUN, priority=0, actions="resubmit(,%s)" % FLOOD_TO_TUN);
//		// FLOOD_TO_TUN will handle flooding in tunnels based on lvid,
//		// for now, add a default drop action
//		add_flow(table=FLOOD_TO_TUN, priority=0, actions="drop");
	}
	
	public static void remove_all_flows(IOFSwitch sw) {
		OFFactory fac = OFFactories.getFactory(sw.getVersion());
		OFFlowDelete.Builder fd = fac.buildFlowDelete();
		sw.getConnection().write(fd.build());
	}
	
	public static void add_create_network_flow(IOFSwitch sw, String tun_id, String mod_vlan_vid) {
System.out.println(">>>>>>>>>>>>>>>>>>>>>>>> add_create_network_flow <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
		OFFactory fac = OFFactories.getFactory(sw.getVersion());
		OFFlowMod.Builder fm = fac.buildFlowAdd();
		
		fm.setHardTimeout(0);
		fm.setIdleTimeout(0);
		fm.setPriority(1);
		fm.setTableId(TableId.of(Byte.valueOf(VXLAN_TUN_TO_LV)));
		
		Match.Builder match = fac.buildMatch();
		match.setExact(MatchField.TUNNEL_ID, U64.of(Long.valueOf(tun_id)));
		fm.setMatch(match.build());
		
		List<OFAction> actions = new ArrayList<OFAction>();
		
//		OFActionSetField asf = fac.actions().setField( fac.oxms().vlanVid(OFVlanVidMatch.ofVlan(Integer.parseInt(mod_vlan_vid))));
//		actions.add( asf );
		
//		OFActionSetVlanVid.Builder actionVlanVid = fac.actions().buildSetVlanVid();
//		actionVlanVid.setVlanVid(VlanVid.ofVlan(Integer.parseInt(mod_vlan_vid)));
//		actions.add(actionVlanVid.build());
//		
//		fm.setActions(actions);
		
//		OFActionPushVlan.Builder aaa = fac.actions().buildPushVlan();
//		aaa.setEthertype(EthType.IPv4);
//		actions.add(aaa.build());
		
//		OFActionSetField bbb = fac.actions().setField(fac.oxms().vlanVid(OFVlanVidMatch.ofVlan(Integer.parseInt(mod_vlan_vid))));
//		actions.add(bbb);

//		OFActionNiciraSetTunnel.Builder ccc = fac.actions().buildNiciraSetTunnel();
//		ccc.setTunnelId(Short.valueOf(tun_id));
//		actions.add(ccc.build());
		
//		OFActionNiciraModVlanVid.Builder aaa = fac.actions().buildNiciraModVlanVid();
////		aaa.setVlanVid(Integer.parseInt(mod_vlan_vid));
//		aaa.setVlanVid(Integer.parseInt("1"));
//		actions.add(aaa.build());
		
		OFActionNiciraResubmitTable.Builder actionResubmit = fac.actions().buildNiciraResubmitTable();
		actionResubmit.setTable(Short.valueOf(LEARN_FROM_TUN));
		actions.add(actionResubmit.build());
		
		
//		OFActionNiciraResubmit.Builder actionResubmit = fac.actions().buildNiciraResubmit();
//		actionResubmit.setTable(Short.valueOf(LEARN_FROM_TUN));
//		actions.add(actionResubmit.build());
		
		List<OFInstruction> instructions = 
				Arrays.<OFInstruction>asList( fac.instructions().applyActions( actions ) );
		
//		fm.setActions(actions);
		fm.setInstructions( instructions );
		
		sw.getConnection().write(fm.build());
	}
	
	@SuppressWarnings("unchecked")
	public static void add_flow(IOFSwitch sw, Map<String, Object> entry) {
		OFFactory fac = OFFactories.getFactory(sw.getVersion());
		OFFlowMod.Builder fm = fac.buildFlowAdd();
		
		
		
		fm = setDefaultFlowModFields(fm);
		
		for(Entry<String, Object> entryMap : entry.entrySet()) {
			if("hard_timeout".equals(entryMap.getKey().toLowerCase())) {
				fm.setHardTimeout(Integer.valueOf(entryMap.getValue().toString()));
			}
			else if("idle_timeout".equals(entryMap.getKey().toLowerCase())) {
				fm.setIdleTimeout(Integer.valueOf(entryMap.getValue().toString()));
			}
			else if("priority".equals(entryMap.getKey().toLowerCase())) {
				fm.setPriority(Integer.valueOf(entryMap.getValue().toString()));
			}
			else if("table".equals(entryMap.getKey().toLowerCase())) {
				fm.setTableId(TableId.of(Byte.valueOf(entryMap.getValue().toString())));
			}
			else if("action_entry".equals(entryMap.getKey().toLowerCase())) {
				List<OFAction> actions = new ArrayList<OFAction>();
				
				
				for(Entry<String,Object> actionMap : ((Map<String, Object>) entryMap.getValue()).entrySet()) {
					if("normal".equals(actionMap.getKey().toLowerCase())) {
						OFActionOutput.Builder action = fac.actions().buildOutput();
						action.setPort(OFPort.NORMAL);
						actions.add(action.build());
					}
//					else if("resubmit".equals(actionMap.getKey().toLowerCase())) {
//						OFActionNiciraResubmitTable.Builder action = fac.actions().buildNiciraResubmitTable();
//						
//						
//						actions.add(action.build());
//					}
				}
				
				
				fm.setActions(actions);
			}
		}
		
		
		sw.getConnection().write(fm.build());
	}
	
	private static OFFlowMod.Builder setDefaultFlowModFields(OFFlowMod.Builder fm) {
		try{
			fm
			.setHardTimeout(HARD_TIMEOUT_DEFAULT)
			.setIdleTimeout(IDLE_TIMEOUT_DEFAULT)
			.setPriority(PRIORITY_DEFAULT);
		}
		catch (UnsupportedOperationException e) {
			//does nothing
		}

		return fm;
	}
	
}
