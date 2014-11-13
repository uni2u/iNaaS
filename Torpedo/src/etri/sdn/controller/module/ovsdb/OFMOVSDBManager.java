package etri.sdn.controller.module.ovsdb;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import etri.sdn.controller.IService;
import etri.sdn.controller.Main;
import etri.sdn.controller.MessageContext;
import etri.sdn.controller.OFModel;
import etri.sdn.controller.OFModule;
import etri.sdn.controller.protocol.io.Connection;
import etri.sdn.controller.protocol.io.IOFHandler;
import etri.sdn.controller.protocol.io.IOFHandler.Role;


public class OFMOVSDBManager extends OFModule implements IOVSDBManagerService {

	public static final Logger logger = LoggerFactory.getLogger(OFMOVSDBManager.class);
	
	/**
	* OVSDB objects that are connected to the controller on the OpenFlow
	* channel. Indexed by dpid.
	*/
	public static Map<Long, IOVSDB> ovsSwitchMap = new ConcurrentHashMap<Long, IOVSDB>();
	/**
	* OVSDB objects that are not connected to the controller on the OpenFlow
	* channel but are reachable on the JSON RPC channel. Indexed by management
	* IP address.
	*/
	public static Map<String, IOVSDB> ovsSwitchMapNC = new ConcurrentHashMap<String, IOVSDB>();

	ClientBootstrap bootstrap;
	OVSDBClientPipelineFactory ovsdbcFact;
	
	public Collection<IOVSDBListener> listeners;
	
	IOFHandler controller;
	
	public void shutDown() {
	}
	
	//*****************************
	// Getters/Setters
	//*****************************
	public void setControllerProvider(IOFHandler controller) {
		this.controller = controller;
	}
	
	@Override
	protected Collection<Class<? extends IService>> services() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected void initialize() {
		// TODO Auto-generated method stub
//		System.out.println("OVSDB Module is START!!!");
		setupNettyClient();
	}

	@Override
	protected boolean handleHandshakedEvent(Connection conn, MessageContext context) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	protected boolean handleMessage(Connection conn, MessageContext context, OFMessage msg, List<OFMessage> outgoing) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	protected boolean handleDisconnect(Connection conn) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public OFModel[] getModels() {
		// TODO Auto-generated method stub
		return null;
	}

	//**************
	// IOVSDBManager
	//**************
	@Override
	public Collection<IOVSDB> getOVSDB() {
		// TODO Auto-generated method stub
		return ovsSwitchMap.values();
	}

	@Override
	public IOVSDB getOVSDB(long dpid) {
		// TODO Auto-generated method stub
		return ovsSwitchMap.get(dpid);
	}

	@Override
	public void addOVSDBListener(IOVSDBListener l) {
		// TODO Auto-generated method stub
		if (listeners == null)
			listeners = new ArrayList<IOVSDBListener>();
		listeners.add(l);
	}

	@Override
	public IOVSDB removeOVSDB(long dpid) {
		// TODO Auto-generated method stub
		IOVSDB o = ovsSwitchMap.remove(dpid);
		if (Main.debug && o != null) {
			logger.debug("removing OVSDB obj from ovs map for dpid {}", dpid);
		}
		
		System.out.println("removing OVSDB obj from ovs map for dpid { "+dpid+" }");
		
		return o;
	}

	@Override
	public IOVSDB addOVSDB(long dpid) {
		// TODO Auto-generated method stub
		if (ovsSwitchMap.containsKey(dpid)) {
//			Logger.debug("OVS Switch {} already connected", dpid);
			System.out.println("OVS Switch { "+dpid+" } already connected");
			return ovsSwitchMap.get(dpid);
		} else {
			//create new ovsdb instance for OVSDBManager
			Object statusObject = new Object();
			if (controller.getSwitches() != null && controller.getSwitch(dpid) != null) {
				String mgmtIPAddr = controller.getSwitch(dpid).toString();
				mgmtIPAddr = mgmtIPAddr.substring(1, mgmtIPAddr.indexOf(':'));
				
				if (ovsSwitchMapNC.containsKey(mgmtIPAddr)) {
					ovsSwitchMapNC.remove(mgmtIPAddr);
//					Logger.debug("removed ovsdb object in NC map " +"for ovs @ {}", mgmtIPAddr);
					System.out.println("removed ovsdb object in NC map for ovs @ { "+mgmtIPAddr+" }");
				}
				
//				Logger.debug("adding new OVSDB object to ovs map for " + "dpid {} at mgmt. ip {}", dpid, mgmtIPAddr);
				System.out.println("adding new OVSDB object to ovs map for dpid { "+dpid+" } at mgmt. ip { "+mgmtIPAddr+" }");
			
				OVSDBImpl tsw = new OVSDBImpl(dpid, mgmtIPAddr, ovsdbcFact, bootstrap, statusObject);
				tsw.getTunnelIPAddress(); //populate tunnel-IP address
				ovsSwitchMap.put(dpid, tsw);
				return tsw;
			}
			return null;
		}
	}

	@Override
	public boolean addPort(long dpid, String portname) {
		// TODO Auto-generated method stub
		IOVSDB ovs = ovsSwitchMap.get(dpid);
		if (ovs != null) {
			ovs.addPort(portname, null, null, false);
			return true;
		}
		return false;
	}

	@Override
	public boolean delPort(long dpid, String portname) {
		// TODO Auto-generated method stub
		IOVSDB ovs = ovsSwitchMap.get(dpid);
		if (ovs != null) {
			ovs.delPort(portname);
			return true;
		}
		return false;
	}
	
	private IOVSDB findOrCreateBridge(String mgmtIPAddr) {
		for (IOVSDB o : ovsSwitchMap.values()) {
			if (o.getMgmtIPAddr().equals(mgmtIPAddr)) {
				return o;
			}
		}
		
		if (ovsSwitchMapNC.containsKey(mgmtIPAddr)) {
			return ovsSwitchMapNC.get(mgmtIPAddr);
		}
		
		// otherwise create a new OVSDB object with dummy dpid
		Object statusObject = new Object();
		OVSDBImpl dsw = new OVSDBImpl(-1, mgmtIPAddr, ovsdbcFact, bootstrap, statusObject);
		ovsSwitchMapNC.put(mgmtIPAddr, dsw);
		return dsw;
	}
	
	@Override
	public String getBridgeDpid(String mgmtIPAddr) {
		IOVSDB dsw = findOrCreateBridge(mgmtIPAddr);
		return dsw.getBridgeDpid();
	}

	@Override
	public void setBridgeDpid(String mgmtIPAddr, String dpidstr) {
		// TODO Auto-generated method stub
		// We may have stale objects with same dpid in NC map, remove conflicts
		Long dpid = Long.parseLong(dpidstr, 16);
		for (IOVSDB o : ovsSwitchMapNC.values()) {
			if (o.getDpid() == dpid && !o.getMgmtIPAddr().equals(mgmtIPAddr)) {
				if (Main.debug) {
					logger.debug("Removing stale object with IP {} and dpid {}", mgmtIPAddr, dpidstr);
				}
				
				System.out.println("setBridgeDpid IP { "+mgmtIPAddr+" } and dpid { "+dpidstr+" }");
				
				ovsSwitchMapNC.remove(o.getMgmtIPAddr());
			}
		}
		IOVSDB dsw = findOrCreateBridge(mgmtIPAddr);
		dsw.setBridgeDpid(dpidstr);
	}
	
	//*****************
	// Internal helpers
	//*****************
	protected void setupNettyClient() {
		// Configure the client.
		bootstrap = new ClientBootstrap(new NioClientSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool()));
		// Set up the event pipeline factory.
		// TODO: intelligently decide between SSL and plain
		ovsdbcFact = new OVSDBClientPipelineFactory();
		bootstrap.setPipelineFactory(ovsdbcFact);
		bootstrap.setOption("reuseAddr", true);
		bootstrap.setOption("child.keepAlive", true);
		bootstrap.setOption("child.tcpNoDelay", true);
		
	}
	
	// IModule
	@Override
	public Collection<Class<? extends IService>> getModuleServices() {
		Collection<Class<? extends IService>> l = new ArrayList<Class<? extends IService>>();
		l.add(IOVSDBManagerService.class);
		return l;
	}
	
	@Override
	public Map<Class<? extends IService>, IService> getServiceImpls() {
		Map<Class<? extends IService>, IService> m = new HashMap<Class<? extends IService>, IService>();
		m.put(IOVSDBManagerService.class, this);
		return m;
	}
	
	@Override
	public Collection<Class<? extends IService>> getModuleDependencies() {
		Collection<Class<? extends IService>> l = new ArrayList<Class<? extends IService>>();
		l.add(IService.class);
		return l;
	}
	
	@Override
	public void setControllerIPAddresses(long dpid, ArrayList<String> cntrIP) {
		// TODO Auto-generated method stub
		if (cntrIP == null || cntrIP.size() == 0) {
			logger.error("must specify at least one controller-ip to set" + "at dpid {}", dpid);
			return;
		}
			
		if (ovsSwitchMap.containsKey(dpid)) {
			ovsSwitchMap.get(dpid).setControllerIPs(cntrIP);
			return;
		} else {
			for (IOVSDB o : ovsSwitchMapNC.values()) {
				if (o.getDpid() == dpid) {
					o.setControllerIPs(cntrIP);
					return;
				}
			}
		}
		logger.error("Switch dpid {} not set - could not find ovsdb object" + " when trying to set controller-IPs", dpid);
	}

	@Override
	public ArrayList<String> getControllerIPAddresses(long dpid) {
		// TODO Auto-generated method stub
		if (ovsSwitchMap.containsKey(dpid))
			return ovsSwitchMap.get(dpid).getControllerIPs();
		
		//check in map for switches not connected on OF channel
		for (IOVSDB o : ovsSwitchMapNC.values()) {
			if (o.getDpid() == dpid) {
				return o.getControllerIPs();
			}
		}
		return null;
	}

	@Override
	public void roleChanged(Role oldRole, Role newRole) {
		switch(newRole) {
			case MASTER:
				// no-op for now
				break;
			case SLAVE:
				logger.debug("Clearing OVS Switch Maps due to " + "HA change to SLAVE");
				ovsSwitchMap.clear();
				ovsSwitchMapNC.clear();
				break;
			default:
				logger.debug("Unknow controller role: {}", newRole);
				break;
		}
	}
	
	@Override
	public void controllerNodeIPsChanged(Map<String, String> curControllerNodeIPs, Map<String, String> addedControllerNodeIPs, Map<String, String> removedControllerNodeIPs) {
	// ignore
	}
}
