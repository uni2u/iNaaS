package etri.sdn.controller.module.tunnelmanager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TunnelOvs {
	public static final Logger logger = LoggerFactory
			.getLogger(TunnelOvs.class);
	public static final String DEFAULT_OVS_VSCTL_TIMEOUT = "10";

	public static void add_bridge(String ovsdb_server_remote_ip,
			String ovsdb_server_remote_port, String bridge_name) {
		String[] command = {
				"--db=tcp:" + ovsdb_server_remote_ip + ":"
						+ ovsdb_server_remote_port,
				// "--", "--may-exist", "add-br", bridge_name};
				"add-br", bridge_name };
		run_vsctl(command, "");
	}

	public static void set_bridge_protocol(String ovsdb_server_remote_ip,
			String ovsdb_server_remote_port, String bridge_name) {
		String[] command = {
				"--db=tcp:" + ovsdb_server_remote_ip + ":"
						+ ovsdb_server_remote_port, "set", "bridge",
				bridge_name, "protocols=OpenFlow13" };
		run_vsctl(command, "");
	}

	public static void connController(String ovsdb_server_remote_ip,
			String ovsdb_server_remote_port, String iris_ip, String bridge_name) {
		String[] command = {
				"--db=tcp:" + ovsdb_server_remote_ip + ":"
						+ ovsdb_server_remote_port, "set-controller",
				bridge_name, "tcp:" + iris_ip + ":6633" };
		run_vsctl(command, "");
	}

	public static void disConnController(String ovsdb_server_remote_ip,
			String ovsdb_server_remote_port, String bridge_name) {
		String[] command = {
				"--db=tcp:" + ovsdb_server_remote_ip + ":"
						+ ovsdb_server_remote_port, "del-controller",
				bridge_name };
		run_vsctl(command, "");
	}

	public static void delete_bridge(String ovsdb_server_remote_ip,
			String ovsdb_server_remote_port, String bridge_name) {
		String[] command = {
				"--db=tcp:" + ovsdb_server_remote_ip + ":"
						+ ovsdb_server_remote_port,
				// "--", "--if-exists", "del-br", bridge_name};
				"del-br", bridge_name };
		run_vsctl(command, "");
	}

	public static void set_secure_mode(String ovsdb_server_remote_ip,
			String ovsdb_server_remote_port, String bridge_name) {
		String[] command = {
				"--db=tcp:" + ovsdb_server_remote_ip + ":"
						+ ovsdb_server_remote_port, "--", "set-fail-mode",
				bridge_name, "secure" };
		run_vsctl(command, "");
	}

	public static void add_port(String ovsdb_server_remote_ip,
			String ovsdb_server_remote_port, String bridge_name,
			String port_name) {
		String[] command = {
				"--db=tcp:" + ovsdb_server_remote_ip + ":"
						+ ovsdb_server_remote_port,
				// "--", "--may-exist", "add-port", bridge_name, port_name};
				"add-port", bridge_name, port_name };
		run_vsctl(command, "");
	}

	public static void delete_port(String ovsdb_server_remote_ip,
			String ovsdb_server_remote_port, String bridge_name,
			String port_name) {
		String[] command = {
				"--db=tcp:" + ovsdb_server_remote_ip + ":"
						+ ovsdb_server_remote_port,
				// "--", "--if-exists", "del-port", bridge_name, port_name};
				"del-port", bridge_name, port_name };
		run_vsctl(command, "");
	}

	public static String add_patch_port(String ovsdb_server_remote_ip,
			String ovsdb_server_remote_port, String bridge_name,
			String local_name, String remote_name) {
		String[] command = {
				"--db=tcp:" + ovsdb_server_remote_ip + ":"
						+ ovsdb_server_remote_port, "add-port", bridge_name,
				local_name, "--", "set", "Interface", local_name, "type=patch",
				"options:peer=" + remote_name };
		run_vsctl(command, "");
		return get_port_ofport(ovsdb_server_remote_ip,
				ovsdb_server_remote_port, local_name);
	}

	public static String add_tunnel_port(String ovsdb_server_remote_ip,
			String ovsdb_server_remote_port, String bridge_name,
			String port_name, String local_ip_tun, String remote_ip_tun) {
		String[] command = {
				"--db=tcp:" + ovsdb_server_remote_ip + ":"
						+ ovsdb_server_remote_port,
				// "--", "--may-exist", "add-port", bridge_name, port_name,
				"add-port",
				bridge_name,
				port_name,
				"--",
				"set",
				"Interface",
				port_name,
				"type=" + OFMTunnelManager.TUNNEL_TYPE,
				"options:in_key=flow options:local_ip=" + local_ip_tun
						+ " options:out_key=flow options:remote_ip="
						+ remote_ip_tun };
		run_vsctl(command, "");
		return get_port_ofport(ovsdb_server_remote_ip,
				ovsdb_server_remote_port, port_name);
	}

	public static String get_port_ofport(String ovsdb_server_remote_ip,
			String ovsdb_server_remote_port, String port_name) {
		String command = "";
		Process ofportProcess = null;
		String ofport = "";
		try {
			command = "sudo ovs-vsctl --db=tcp:" + ovsdb_server_remote_ip + ":"
					+ ovsdb_server_remote_port + " get Interface " + port_name
					+ " ofport";
			ofportProcess = Runtime.getRuntime().exec(command);
			ofportProcess.waitFor();
			ofport = new BufferedReader(new InputStreamReader(
					ofportProcess.getInputStream())).readLine();
		} catch (Exception e) {
			logger.error("Unable to execute {}. \n Exception: {}", command,
					e.getMessage());
		}
		return ofport;
	}

	public static long get_sw_dpid(String ovsdb_server_remote_ip,
			String ovsdb_server_remote_port, String bridge_name) {
		String command = "";
		Process swDpidProcess = null;
		long swDpid = 0;
		try {
			command = "sudo ovs-vsctl --db=tcp:" + ovsdb_server_remote_ip + ":"
					+ ovsdb_server_remote_port + " get Bridge " + bridge_name
					+ " datapath_id";
			swDpidProcess = Runtime.getRuntime().exec(command);
			swDpidProcess.waitFor();
			swDpid = Long
					.parseLong(new BufferedReader(new InputStreamReader(
							swDpidProcess.getInputStream())).readLine()
							.replaceAll("\"", ""), 16);
		} catch (Exception e) {
			logger.error("Unable to execute {}. \n Exception: {}", command,
					e.getMessage());
		}
		return swDpid;
	}

	public static void run_vsctl(String[] command, String vsctl_timeout) {
		// String vsctl_command = "";
		String vsctl_command = "sudo ovs-vsctl";
		// if("".equals(vsctl_timeout)) {
		// vsctl_timeout = DEFAULT_OVS_VSCTL_TIMEOUT;
		// }
		//
		// vsctl_command += "sudo ovs-vsctl --timeout="+vsctl_timeout;
		for (String arg : command) {
			vsctl_command += " " + arg;
		}
		try {
			logger.debug("vsctl_command : {}", vsctl_command);
			Runtime.getRuntime().exec(vsctl_command).waitFor();
		} catch (Exception e) {
			logger.error("Unable to execute {}. \n Exception: {}",
					vsctl_command, e.getMessage());
		}
	}
}