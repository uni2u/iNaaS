package etri.iris.agent.tunnelmanager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.util.ArrayList;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;

public class TunnelManagerAgent extends ServerResource {
	private Logger logger = Logger.getLogger(getClass());
	private String loggerPath = System.getProperty("user.dir") + "/log4j.properties";
	
	private static final String INTEGRATION_BRIDGE_NAME = "br-int";
	private static final String TUNNELING_BRIDGE_NAME = "br-tun";
	private static final String INT_PEER_PATCH_PORT = "patch-tun";
	private static final String TUN_PEER_PATCH_PORT = "patch-int";
	
	@Post
	public void run_cmd(String command) {
		PropertyConfigurator.configure(loggerPath);
		int resultVal = -1;
		try {
logger.debug(">>>>> run_cmd : " + command);
			resultVal = Runtime.getRuntime().exec(command).waitFor();
logger.debug(">>>>> run_cmd_result : " + resultVal);
		} catch (Exception e) {
			logger.error("========== Method Name : run_cmd(String command) ==========");
			resultVal = -1;
		}
	}
	
	@Get
	public ArrayList<String> run_cmd_return() {
		PropertyConfigurator.configure(loggerPath);
		
		Process ofportProcess = null;
		ArrayList<String> returnVal = new ArrayList<String>();
		int resultVal = -1;
		
		if(getAttribute("command") != null) {
			try {
				String command = URLDecoder.decode((String)getAttribute("command"), "UTF-8");
logger.debug(">>>>> run_cmd_return : " + command);
				ofportProcess = Runtime.getRuntime().exec(command);
				resultVal = ofportProcess.waitFor();
logger.debug(">>>>> run_cmd_return_result : " + resultVal);
				BufferedReader br = new BufferedReader(new InputStreamReader(ofportProcess.getInputStream()));
				String line = null;
				
				while((line = br.readLine()) != null) {
					returnVal.add(line);
				}
			} catch (Exception e) {
				logger.error("========== Method Name : run_cmd_return(String command) ==========");
				resultVal = -1;
			}
		}
		
		return returnVal;
	}
	
	public void setup_ovsdb_tcp() {
		try {
			run_cmd("sudo ovs-appctl -t ovsdb-server ovsdb-server/add-remote ptcp:6640");
		} catch(Exception e) {
			PropertyConfigurator.configure(loggerPath);
			logger.error("========== Method Name : setup_ovsdb_tcp() ==========");
		}
	}
	
	public void create_bridge() {
		try {
			run_cmd("sudo ovs-vsctl --timeout=10 -- --if-exists del-br "+INTEGRATION_BRIDGE_NAME);
			Thread.sleep(100);
			run_cmd("sudo ovs-vsctl --timeout=10 -- --may-exist add-br "+INTEGRATION_BRIDGE_NAME);
			Thread.sleep(100);
			run_cmd("sudo ovs-vsctl --timeout=10 -- set-fail-mode "+INTEGRATION_BRIDGE_NAME+" secure");
			Thread.sleep(100);
			run_cmd("sudo ovs-vsctl --timeout=10 -- --if-exists del-port "+INTEGRATION_BRIDGE_NAME+" "+INT_PEER_PATCH_PORT);
			Thread.sleep(100);
			
			run_cmd("sudo ovs-vsctl --timeout=10 -- --if-exists del-br "+TUNNELING_BRIDGE_NAME);
			Thread.sleep(100);
			run_cmd("sudo ovs-vsctl --timeout=10 -- --may-exist add-br "+TUNNELING_BRIDGE_NAME);
			Thread.sleep(100);
			run_cmd("sudo ovs-vsctl --timeout=10 -- set-fail-mode "+TUNNELING_BRIDGE_NAME+" secure");
			Thread.sleep(100);
			run_cmd("sudo ovs-vsctl --timeout=10 -- --if-exists del-port "+INTEGRATION_BRIDGE_NAME+" "+INT_PEER_PATCH_PORT);
			Thread.sleep(100);
			run_cmd("sudo ovs-vsctl --timeout=10 -- --if-exists del-port "+TUNNELING_BRIDGE_NAME+" "+TUN_PEER_PATCH_PORT);
			Thread.sleep(100);
			run_cmd("sudo ovs-vsctl --timeout=10 -- --may-exist add-port "+INTEGRATION_BRIDGE_NAME+" "+INT_PEER_PATCH_PORT+" -- set Interface "+INT_PEER_PATCH_PORT+" type=patch options:peer="+TUN_PEER_PATCH_PORT);
			Thread.sleep(100);
			run_cmd("sudo ovs-vsctl --timeout=10 -- --may-exist add-port "+TUNNELING_BRIDGE_NAME+" "+TUN_PEER_PATCH_PORT+" -- set Interface "+TUN_PEER_PATCH_PORT+" type=patch options:peer="+INT_PEER_PATCH_PORT);
			Thread.sleep(100);
		} catch(Exception e) {
			PropertyConfigurator.configure(loggerPath);
			logger.error("========== Method Name : create_bridge() ==========");
		}
	}
	
	public String get_port_ofport(String port_name) {
		String command = "";
		Process ofportProcess = null;
		String ofport = "";
		
		try {
			command = "sudo ovs-vsctl --timeout=10 get Interface "+port_name+" ofport";
			ofportProcess = Runtime.getRuntime().exec(command);
			ofportProcess.waitFor();
			ofport = new BufferedReader(new InputStreamReader(ofportProcess.getInputStream())).readLine();
		} catch(Exception e) {
			PropertyConfigurator.configure(loggerPath);
			logger.error("========== Method Name : get_port_ofport(String port_name) ==========");
		}
		
		return ofport;
	}
	
}
