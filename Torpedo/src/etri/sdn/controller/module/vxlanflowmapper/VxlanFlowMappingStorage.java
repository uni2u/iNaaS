package etri.sdn.controller.module.vxlanflowmapper;


import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.codehaus.jackson.map.MappingJsonFactory;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.Restlet;
import org.restlet.data.MediaType;
import org.slf4j.Logger;

import etri.sdn.controller.OFModel;
import etri.sdn.controller.OFModel.RESTApi;
import etri.sdn.controller.module.staticentrymanager.IStaticFlowEntryService;

import java.net.MalformedURLException;

import javax.swing.text.html.HTMLDocument.Iterator;


public class VxlanFlowMappingStorage extends OFModel  {
	private final static Logger logger = OFVxlanFlowMappingManager.logger;

	public OFVxlanFlowMappingManager<?, ?> manager;

	private String name;
	
	private  ConcurrentHashMap<String, OverlayFlowMappingEntry<String,String>> O2V;
	private  ConcurrentHashMap<String, OverlayFlowMappingEntry<String,String>> V2O;
	private  HashSet<URL> notificationList;

	
	VxlanFlowMappingStorage(OFVxlanFlowMappingManager<?, ?> manager, String name) {
		this.manager = manager;
		this.name = name;
		O2V = new ConcurrentHashMap<String, OverlayFlowMappingEntry<String,String>>();
		V2O = new ConcurrentHashMap<String, OverlayFlowMappingEntry<String,String>>();
		notificationList = new HashSet<URL>();
		
	}

	public OFVxlanFlowMappingManager<?, ?> getManager() {
		return this.manager;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

	public OverlayFlowMappingEntry<String, String> addO2VEnrty (String o2VKey, OverlayFlowMappingEntry<String, String> entry) {
		
		OverlayFlowMappingEntry<String, String> ret = O2V.put(o2VKey, entry);
	
		if (ret == null) {
			Set<String> keySet = O2V.keySet();
//			System.out.println ("+++++ADDED KEY: " + o2VKey +", O2V Table Size is " + keySet.size() + "\n" + keySet);
			try {
				
				for (URL url : notificationList) {
					sendVxlanFlowNotification(url, entry, true);	
				}
			}  catch ( Exception e ) {
				e.printStackTrace();			
			}
			
//			System.out.println(keySet);
//			Iterator<String> iter = O2V.keySet().iterator();
//			while (iter != null && iter.hasNext()) {
//				System.out.println(O2V.get(iter.next()));
//			}
		}

		return ret;
	}
	
	public OverlayFlowMappingEntry<String, String> addV2OEnrty (String v2OKey, OverlayFlowMappingEntry<String, String> entry) {
		OverlayFlowMappingEntry<String, String> ret = V2O.put(v2OKey, entry);
		if (ret == null) { 
			Set<String> keySet = V2O.keySet();
//			System.out.println ("+++++ADDED KEY: " + v2OKey +", V2O Table Size is " + keySet.size() + "\n" + keySet);
			//System.out.println(keySet);
//			Iterator<String> iter = V2O.keySet().iterator();
//			while (iter != null && iter.hasNext()) {
//				System.out.println(V2O.get(iter.next()));
//			}
		}
	
		return ret;
	}
	
	public OverlayFlowMappingEntry<String, String> removeO2VEnrty (String o2VKey) {
		
		OverlayFlowMappingEntry<String, String> ret = O2V.remove(o2VKey);
		if (ret != null) {
			Set<String> keySet = O2V.keySet();
			try {
				
				for (URL url : notificationList) {
					sendVxlanFlowNotification(url, ret, false);	
				}
			}  catch ( Exception e ) {
				e.printStackTrace();			
			}
			
//			System.out.println ("-----REMOVED KEY: " + o2VKey +", O2V Table Size is " + keySet.size() + "\n" + keySet);
//			System.out.println ("-----REMOVED return: " + ret);
			
			
//			System.out.println(keySet);
//			Iterator<String> iter = O2V.keySet().iterator();
//			while (iter != null && iter.hasNext()) {
//				System.out.println(O2V.get(iter.next()));
//			}
		}
			
		return ret;
	}
	


	public OverlayFlowMappingEntry<String, String> removeV2OEnrty (String v2OKey) {
		OverlayFlowMappingEntry<String, String> ret = V2O.remove(v2OKey);
		if (ret != null) {
			Set<String> keySet = V2O.keySet();
//			System.out.println ("-----REMOVED KEY: " + v2OKey +", V2O Table Size is " + keySet.size() + "\n" + keySet);
//			System.out.println(keySet);
//			Iterator<String> iter = V2O.keySet().iterator();
//			while (iter != null && iter.hasNext()) {
//				System.out.println(V2O.get(iter.next()));
//			}
		}
		return ret;
	}
	
	public OverlayFlowMappingEntry<String, String> getV2OEnrty (String v2OKey) {
		return V2O.get(v2OKey);
	}
	
	public OverlayFlowMappingEntry<String, String> getO2VEnrty (String o2VKey) {
		return O2V.get(o2VKey);
	}
	
	
	public int getVnid(String srcMac, String dstMac, String srcIp, String dstIp, int srcUdpPort) {
		int vnid = -1;
		String key = srcMac.toUpperCase() + " " + dstMac.toUpperCase() + " " + srcIp + " " + dstIp + " " + srcUdpPort;
		Map<String, String> o2VEntry = O2V.get(key);
		if (o2VEntry != null) vnid = Integer.parseInt(o2VEntry.get("vnid"));
		return vnid;
	}
	
	private boolean sendVxlanFlowNotification (URL url, OverlayFlowMappingEntry<String, String> entry, boolean flowAddFlag) {
		String outerSrcMac = (String) entry.get("outerSrcMac");
		String outerDstMac = (String) entry.get("outerDstMac");
		String outerSrcIp = (String) entry.get("outerSrcIP");
		String outerDstIp = (String) entry.get("outerDstIP");
		String outerSrcUdpPort = (String) entry.get("outerSrcPort");
		String innerSrcMac = entry.get("innerSrcMac");
		String innerDstMac = entry.get("innerDstMac");
		String innerEtherType = entry.get("innerEtherType");
		String innerSrcIp = entry.get("innerSrcIP");
		String innerDstIp = entry.get("innerDstIP");
		String vnid = entry.get("vnid");
		
		List<HeaderInfoPair> pairs = new LinkedList<HeaderInfoPair>();
		OuterPacketHeader outerPacket = new OuterPacketHeader.Builder().srcMac(outerSrcMac).dstMac(outerDstMac).srcIp(outerSrcIp).dstIp(outerDstIp).udpPort(outerSrcUdpPort).build();
		OrginalPacketHeader orgPacket = new OrginalPacketHeader.Builder().srcMac(innerSrcMac).dstMac(innerDstMac).srcIp(innerSrcIp).dstIp(innerDstIp).vnid(vnid).etherType(innerEtherType).build();
		pairs.add(new HeaderInfoPair(outerPacket, orgPacket));

		try {
			MappingJsonFactory f = new MappingJsonFactory();	
			ObjectMapper mapper = new ObjectMapper(f);
			String output;
			if (flowAddFlag == true) {
				P2VResponse reply = new P2VResponse(pairs);
				output =  mapper.defaultPrettyPrintingWriter().writeValueAsString(reply);
			} else {
				P2VRemovedResponse reply =  new P2VRemovedResponse(pairs);
				output =  mapper.defaultPrettyPrintingWriter().writeValueAsString(reply);
//				System.out.println ("outerPacket : " + outerPacket);
//				System.out.println ("orgPacket : " + orgPacket);
			}
		
//			System.out.println (output);
					
			HttpURLConnection connection  = (HttpURLConnection)url.openConnection();
			connection.setRequestMethod("POST");
            connection.setRequestProperty("X-HTTP-Method-Override", "DELETE");
            connection.setConnectTimeout(1000);
            connection.setReadTimeout(1000);
            connection.setDoInput(true);
            connection.setDoOutput(true);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
			OutputStream os = connection.getOutputStream();
			os.write(output.getBytes());
			os.flush();
			os.close();			
			BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
			br.close();
			connection.disconnect();
		} catch (SocketTimeoutException  e ){
			notificationList.remove(url);
			e.printStackTrace();			
		}  catch (ProtocolException e) {
			notificationList.remove(url);
			e.printStackTrace();	
	    } catch (IOException e) {
	    	notificationList.remove(url);
	    	e.printStackTrace();	
	    } catch ( Exception e ) {
	    	notificationList.remove(url);
			e.printStackTrace();			
		}
		return true;
	}
	
	private RESTApi[] apis = {
			/*
			 * LIST example
			 * OF1.0,1.3:	curl http://{controller_ip}:{rest_api_port}/wm/staticflowentry/list/all/json
			 * 				curl http://{controller_ip}:{rest_api_port}/wm/staticflowentry/list/00:00:00:00:00:00:00:01/json
			 */
			new RESTApi("/wm/vxlanflowmapper/geto2v/json",
					new Restlet() {
						public  void handle(Request request, Response response) {
							MappingJsonFactory f = new MappingJsonFactory();	
							ObjectMapper mapper = new ObjectMapper(f);
							String req = request.getEntityAsText();
							
							
							
							try {
								Map<String, Object> map = mapper.readValue(req, new TypeReference<Map<String, Object>>() {});
								ArrayList<?> list =  (ArrayList<?>) map.get("outerList");
								List<HeaderInfoPair> pairs = new LinkedList<HeaderInfoPair>();
								for (int i=0; i < list.size(); i++) {
									HashMap<?, ?> m = (HashMap<?, ?>) list.get(i);
									String outerSrcMac = (String) m.get("srcMac");
									String outerDstMac = (String) m.get("dstMac");
									String outerSrcIp = (String) m.get("srcIp");
									String outerDstIp = (String) m.get("dstIp");
									String outerSrcUdpPort = (String) m.get("srcUdpPort");
									
									String key = outerSrcMac.toUpperCase() + " " + outerDstMac.toUpperCase() + " " + outerSrcIp + " " + outerDstIp + " " + outerSrcUdpPort;
									Map<String, String> o2VEntry = O2V.get(key);
									OuterPacketHeader outerPacket = new OuterPacketHeader.Builder().srcMac(outerSrcMac).dstMac(outerDstMac).srcIp(outerSrcIp).dstIp(outerDstIp).udpPort(outerSrcUdpPort).build();
									OrginalPacketHeader orgPacket;
									
									if (o2VEntry != null) {
										String innerSrcMac = o2VEntry.get("innerSrcMac");
										String innerDstMac = o2VEntry.get("innerDstMac");
										String innerEtherType = o2VEntry.get("innerEtherType");
										String innerSrcIp = o2VEntry.get("innerSrcIP");
										String innerDstIp = o2VEntry.get("innerDstIP");
										String vnid = o2VEntry.get("vnid");
										orgPacket = new OrginalPacketHeader.Builder().srcMac(innerSrcMac).dstMac(innerDstMac).srcIp(innerSrcIp).dstIp(innerDstIp).vnid(vnid).etherType(innerEtherType).build();
									} else orgPacket = new OrginalPacketHeader(null, null, null, null, null, null);
													
									pairs.add(new HeaderInfoPair(outerPacket, orgPacket));
															
								}
								P2VResponse reply = new P2VResponse(pairs);
								String output = null;
								output = mapper.defaultPrettyPrintingWriter().writeValueAsString(reply);
								response.setEntity(output, MediaType.APPLICATION_JSON);
							} catch ( Exception e ) {
								e.printStackTrace();
							
							}
						}
			}),
			new RESTApi("/wm/vxlanflowmapper/register_test1/json",
					new Restlet() {
						public  void handle(Request request, Response response) {
								
							String req = request.getEntityAsText();
							System.out.println("register_test1 " + req);
														
						}
			}),
			new RESTApi("/wm/vxlanflowmapper/register_test2/json",
					new Restlet() {
						public  void handle(Request request, Response response) {
								
							String req = request.getEntityAsText();
							System.out.println("register_test2 " + req);
														
						}
			}),
			new RESTApi("/wm/vxlanflowmapper/register/json",
					new Restlet() {
						public  void handle(Request request, Response response) {
							try {
								MappingJsonFactory f = new MappingJsonFactory();	
								ObjectMapper mapper = new ObjectMapper(f);
								String req = request.getEntityAsText();
								URL url = new URL(req);
								boolean b = notificationList.add(url);
								String reply;
								if (b)  {
										reply = "URL Registered : " + url.toString();
								} else reply = "URL Already Registered : " + url.toString();
								String r = mapper.writeValueAsString(reply);
								response.setEntity(r, MediaType.APPLICATION_JSON);
//								System.out.println(notificationList);
								
							} catch (MalformedURLException  e) {
								e.printStackTrace();
							} catch ( Exception e ) {
								e.printStackTrace();							
							}
							
						}
			}),
			new RESTApi("/wm/vxlanflowmapper/unregister/json",
					new Restlet() {
						public  void handle(Request request, Response response) {
							try {
								MappingJsonFactory f = new MappingJsonFactory();	
								ObjectMapper mapper = new ObjectMapper(f);
								String req = request.getEntityAsText();
								URL url = new URL(req);
								boolean b = notificationList.remove(url);
								String reply;
								if (b)  {
										reply = "URL Unregistered : " + url.toString();
								} else reply = "URL Already Unregistered : " + url.toString();
								String r = mapper.writeValueAsString(reply);
								response.setEntity(r, MediaType.APPLICATION_JSON);
								System.out.println(notificationList);
								
							} catch (MalformedURLException  e) {
								e.printStackTrace();
							} catch ( Exception e ) {
								e.printStackTrace();							
							}
							
						}
			})
			
	};
	
	@Override
	public RESTApi[] getAllRestApi() {
		// TODO Auto-generated method stub
		return apis;
	}
}