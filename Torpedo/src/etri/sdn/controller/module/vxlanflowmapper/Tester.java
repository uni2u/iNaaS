package etri.sdn.controller.module.vxlanflowmapper;



import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.codehaus.jackson.map.ObjectMapper;

public class Tester {
	public static void main(String[] args) {

		testV2PRequest();
		testV2PResponse();

	}
	public static void testV2PResponse() {
		HeaderInfoPair pair1 = new HeaderInfoPair(
				new OuterPacketHeader.Builder()
					.srcMac("00:00:00:00:00:11")
					.dstMac("00:00:00:00:00:22").
					srcIp("10.0.0.11").
					dstIp("10.0.0.22").
					udpPort("1001")
					.build(), 
				new OrginalPacketHeader.Builder()
					.srcMac("00:00:00:00:00:11")
					.dstMac("00:00:00:00:00:22")
					.srcIp("10.0.0.11")
					.dstIp("10.0.0.22")
					.vnid("1001")
					.build() );
	
		List<HeaderInfoPair> pairs = Arrays.asList(pair1);

		V2PResponse response = new V2PResponse(pairs);


		ObjectMapper mapper = new ObjectMapper();
		
		String output = null;
		try {
			output = mapper.defaultPrettyPrintingWriter().writeValueAsString(response);
			System.out.println(output);
		} catch (IOException e) {
			e.printStackTrace();
		}		
	}
	
	public static void testV2PRequest() {
		OuterPacketHeader orgHeader = new OuterPacketHeader("00:00:00:00:00:01", "00:00:00:00:00:02", "10.0.0.1", "10.0.0.2", "1234");
		List<OuterPacketHeader> headers= Arrays.asList(orgHeader);
		P2VRequest request = new P2VRequest(headers);
		
//		request.outerList = headers;
		
		ObjectMapper mapper = new ObjectMapper();
		List<OuterPacketHeader> switchs = new ArrayList<>();
		String output = null;
		try {
			output = mapper.defaultPrettyPrintingWriter().writeValueAsString(request);
			System.out.println(output);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
