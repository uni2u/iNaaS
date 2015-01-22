package etri.sdn.controller.module.tunnelmanager;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;
import org.restlet.Context;
import org.restlet.data.MediaType;
import org.restlet.data.Method;
import org.restlet.engine.header.Header;
import org.restlet.representation.StringRepresentation;
import org.restlet.resource.ClientResource;
import org.restlet.util.Series;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import etri.sdn.controller.TorpedoProperties;

public class NeutronInfoInitialize extends Thread {
	public static final Logger logger = LoggerFactory.getLogger(NeutronInfoInitialize.class);
	
	TorpedoProperties sysconf = TorpedoProperties.loadConfiguration();
	
	public void run() {
//logger.debug("=================== NeutronInfoInitialize Start ===================");
//logger.debug("Start time : " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(System.currentTimeMillis())));
		try {
			long waitingTime = Long.parseLong(sysconf.getString("iNaaSAgent-check-time")) + 10;
			Thread.sleep(waitingTime * 1000);
			
logger.debug("=================== NeutronInfoInitialize Start ===================");
logger.debug("Network Initialize Start time : " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(System.currentTimeMillis())));
			String token_id = getTokenId();

			if(!"".equals(token_id) && token_id != null) {
				for(Object neutronNetwork : getNeutronNetworks().get("networks")) {
					ClientResource network_cr =  null;
					StringRepresentation network_sr = null;
					
					try {
						ObjectMapper nom = new ObjectMapper();
						String neutronNetworkStr = "{\"network\": " + nom.writeValueAsString(neutronNetwork) + "}";
logger.debug(">>>>> neutronNetworkStr : " + neutronNetworkStr);
						
						network_cr = new ClientResource("http://127.0.0.1:"+sysconf.getString("web-server-port")+"/wm/ml2/networks");
						network_cr.setMethod(Method.POST);
						network_cr.getReference().addQueryParameter("format", "json");
						
						network_sr = new StringRepresentation(neutronNetworkStr);
						network_sr.setMediaType(MediaType.APPLICATION_JSON);
						network_cr.post(network_sr);
						
						Thread.sleep(500);
					} catch(Exception e) {
						logger.error("Network Initialize Exception: {}", e.getMessage());
					}	finally {
						network_sr.release();
						network_cr.release();
					}
				}
				
logger.debug("Subnet Initialize Start time : " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(System.currentTimeMillis())));
				for(Object neutronSubnet : getNeutronSubnets().get("subnets")) {
					ClientResource subnet_cr = null;
					StringRepresentation subnet_sr = null;
					
					try {
						ObjectMapper som = new ObjectMapper();
						String neutronSubnetStr = "{\"subnet\": " + som.writeValueAsString(neutronSubnet) + "}";
logger.debug(">>>>> neutronSubnetStr : " + neutronSubnetStr);
						
						subnet_cr = new ClientResource("http://127.0.0.1:"+sysconf.getString("web-server-port")+"/wm/ml2/subnets");
						subnet_cr.setMethod(Method.POST);
						subnet_cr.getReference().addQueryParameter("format", "json");
						
						subnet_sr = new StringRepresentation(neutronSubnetStr);
						subnet_sr.setMediaType(MediaType.APPLICATION_JSON);
						subnet_cr.post(subnet_sr);
						
						Thread.sleep(500);
					} catch(Exception e) {
						logger.error("Subnet Initialize Exception: {}", e.getMessage());
					} finally {
						subnet_sr.release();
						subnet_cr.release();
					}
				}
	
logger.debug("Port Initialize Start time : " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(System.currentTimeMillis())));
				for(Object neutronPort : getNeutronPorts().get("ports")) {
					ClientResource port_cr = null;
					StringRepresentation port_sr = null;
					
					try {
						ObjectMapper pom = new ObjectMapper();
						String neutronPortStr = "{\"port\": " + pom.writeValueAsString(neutronPort) + "}";
logger.debug(">>>>> neutronPortStr : " + neutronPortStr);
						
						port_cr = new ClientResource("http://127.0.0.1:"+sysconf.getString("web-server-port")+"/wm/ml2/ports");
						port_cr.setMethod(Method.POST);
						port_cr.getReference().addQueryParameter("format", "json");
						
						port_sr = new StringRepresentation(neutronPortStr);
						port_sr.setMediaType(MediaType.APPLICATION_JSON);
						port_cr.post(port_sr);
						
						Thread.sleep(500);
					} catch(Exception e) {
						logger.error("Port Initialize Exception: {}", e.getMessage());
					} finally {
						port_sr.release();
						port_cr.release();
					}
				}
			}
		} catch(Exception e) {
			logger.error("NeutronInfoInitialize run() Exception: {}", e.getMessage());
		}
logger.debug("End time : " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(System.currentTimeMillis())));
logger.debug("=================== NeutronInfoInitialize End ===================");
	}

	public String getTokenId() {	
		Connection conn = null;
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		
		String token_id = "";
		
		try {
			conn = DriverManager.getConnection("jdbc:mysql://"+sysconf.getString("controlnode-ip")+":"+sysconf.getString("mysql-port")+"/keystone", sysconf.getString("mysql-id"), sysconf.getString("mysql-pw"));
			
			pstmt = conn.prepareStatement("SELECT id FROM token WHERE user_id = (SELECT id FROM user WHERE NAME = 'neutron') ORDER BY expires DESC limit 1");
			rs = pstmt.executeQuery();

			if(rs.next()) {
				token_id = rs.getString("id");
			}
			
			rs.close();
			pstmt.close();
			conn.close();
			
		} catch(Exception e) {
			logger.error("========== Method Name : getTokenId() ==========");
			logger.error("Exception : " + e.getMessage());
		} finally {
			if(rs != null) {
				try {
					rs.close();
				} catch(Exception e) {
					logger.error("finally ResultSet close Exception : " + e.getMessage());
				}
			}
			
			if(pstmt != null) {
				try {
					pstmt.close();
				} catch(Exception e) {
					logger.error("finally PreparedStatement close Exception : " + e.getMessage());
				}
			}
			
			if(conn != null) {
				try {
					conn.close();
				} catch(Exception e) {
					logger.error("finally Connection close Exception : " + e.getMessage());
				}
			}
		}
		
		return token_id;
	}
	
	public Map<String, ArrayList<Object>> getNeutronNetworks() {
		Map<String, ArrayList<Object>> neutronNetworks = new ConcurrentHashMap<String, ArrayList<Object>>();
		ClientResource resource = null;
		
		try {
			String restUri = "http://"+sysconf.getString("controlnode-ip")+":"+sysconf.getString("neutron-rest-port")+"/v2.0/networks";
			
			Context context = new Context();
			context.getParameters().add("socketTimeout", "1000");
			context.getParameters().add("idleTimeout", "1000");
			
			resource = new ClientResource(context, restUri);
			
			Series<Header> headers = new Series<Header>(Header.class);
			headers.add("X-Auth-Token", getTokenId());
			resource.getRequestAttributes().put("org.restlet.http.headers", headers);
			resource.get();
			
			ObjectMapper om = new ObjectMapper();
			neutronNetworks = om.readValue(resource.getResponse().getEntityAsText(), new TypeReference<Map<String, ArrayList<Object>>>(){});
		} catch(Exception e) {
			logger.error("========== Method Name : getNeutronNetworks() ==========");
			e.printStackTrace();
		} finally {
			resource.release();
		}
		
		return neutronNetworks;
	}
	
	public Map<String, ArrayList<Object>> getNeutronSubnets() {
		Map<String, ArrayList<Object>> neutronSubnets = new ConcurrentHashMap<String, ArrayList<Object>>();
		ClientResource resource = null;
				
		try {
			String restUri = "http://"+sysconf.getString("controlnode-ip")+":"+sysconf.getString("neutron-rest-port")+"/v2.0/subnets";
			
			Context context = new Context();
			context.getParameters().add("socketTimeout", "1000");
			context.getParameters().add("idleTimeout", "1000");
			
			resource = new ClientResource(context, restUri);
			
			Series<Header> headers = new Series<Header>(Header.class);
			headers.add("X-Auth-Token", getTokenId());
			resource.getRequestAttributes().put("org.restlet.http.headers", headers);
			resource.get();
			
			ObjectMapper om = new ObjectMapper();
			neutronSubnets = om.readValue(resource.getResponse().getEntityAsText(), new TypeReference<Map<String, ArrayList<Object>>>(){});
		} catch(Exception e) {
			logger.error("========== Method Name : getNeutronSubnets() ==========");
			e.printStackTrace();
		} finally {
			resource.release();
		}
		
		return neutronSubnets;
	}
	
	public Map<String, ArrayList<Object>> getNeutronPorts() {
		Map<String, ArrayList<Object>> neutronPorts = new ConcurrentHashMap<String, ArrayList<Object>>();
		ClientResource resource = null;
		
		try {
			String restUri = "http://"+sysconf.getString("controlnode-ip")+":"+sysconf.getString("neutron-rest-port")+"/v2.0/ports";
			
			Context context = new Context();
			context.getParameters().add("socketTimeout", "1000");
			context.getParameters().add("idleTimeout", "1000");
			
			resource = new ClientResource(context, restUri);
			
			Series<Header> headers = new Series<Header>(Header.class);
			headers.add("X-Auth-Token", getTokenId());
			resource.getRequestAttributes().put("org.restlet.http.headers", headers);
			resource.get();
			
			ObjectMapper om = new ObjectMapper();
			neutronPorts = om.readValue(resource.getResponse().getEntityAsText(), new TypeReference<Map<String, ArrayList<Object>>>(){});
		} catch(Exception e) {
			logger.error("========== Method Name : getNeutronPorts() ==========");
			e.printStackTrace();
		} finally {
			resource.release();
		}
		
		return neutronPorts;
	}
	
}
