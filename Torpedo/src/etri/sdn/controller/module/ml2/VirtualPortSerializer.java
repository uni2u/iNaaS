package etri.sdn.controller.module.ml2;

import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;

import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.map.JsonSerializer;
import org.codehaus.jackson.map.SerializerProvider;

public class VirtualPortSerializer extends JsonSerializer<VirtualPort> {

	@Override
	public void serialize(VirtualPort vPort, JsonGenerator jGen, SerializerProvider serializer) throws IOException, JsonProcessingException {
		
		jGen.writeStartObject();
		jGen.writeStringField("binding:host_id", vPort.binding_host_id);
		jGen.writeArrayFieldStart("allowed_address_pairs");		
		if (vPort.allowed_address_pairs != null) {
			for (String allowed_address_pair : vPort.allowed_address_pairs) {
				jGen.writeString(allowed_address_pair);
			}
		}
		jGen.writeEndArray();
		jGen.writeArrayFieldStart("extra_dhcp_opts");		
		if (vPort.extra_dhcp_opts != null) {
			for (String extra_dhcp_opt : vPort.extra_dhcp_opts) {
				jGen.writeString(extra_dhcp_opt);
			}
		}
		jGen.writeEndArray();
		jGen.writeStringField("device_owner", vPort.device_owner);
		jGen.writeObjectFieldStart("binding_profile");		
		if (vPort.binding_profile != null) {
			if(vPort.binding_profile != null) {
				for (Entry<String, String> entry : vPort.binding_profile.entrySet()) {
					jGen.writeStringField(entry.getKey().toString().toLowerCase(), entry.getValue().toString());
				}
			}
		}
		jGen.writeEndObject();
		jGen.writeArrayFieldStart("fixed_ips");		
		if (vPort.fixed_ips != null) {
			for (Map<String, String> fiMap : vPort.fixed_ips) {
				jGen.writeStartObject();
				for (Entry<String, String> entry : fiMap.entrySet()) {
					jGen.writeStringField(entry.getKey().toString().toLowerCase(), entry.getValue().toString());
				}
				jGen.writeEndObject();
			}
		}
		jGen.writeEndArray();
		jGen.writeStringField("id", vPort.portId);
		jGen.writeArrayFieldStart("security_groups");	
		if (vPort.security_groups != null) {
			for (Map<String, Object> sgMap : vPort.security_groups) {
				for (Entry<String, Object> entry : sgMap.entrySet()) {
					if("id".equals(entry.getKey().toString().toLowerCase())) {
						jGen.writeString(entry.getValue().toString());
					}
				}
			}
		}
		jGen.writeEndArray();
		jGen.writeStringField("device_id", vPort.device_id);
		jGen.writeStringField("name", vPort.portName);
		jGen.writeStringField("admin_state_up", vPort.admin_state_up);
		jGen.writeStringField("network_id", vPort.network_id);
		jGen.writeStringField("tenant_id", vPort.tenant_id);
		jGen.writeObjectFieldStart("binding:vif_details");
			if(vPort.binding_vif_details != null) {
				for (Entry<String, String> entry : vPort.binding_vif_details.entrySet()) {
					jGen.writeStringField(entry.getKey().toString().toLowerCase(), entry.getValue().toString());
				}
			}
		jGen.writeEndObject();
		jGen.writeStringField("binding:vnic_type", vPort.binding_vnic_type);
		jGen.writeStringField("binding:vif_type", vPort.binding_vif_type);
		jGen.writeStringField("mac_address", vPort.mac_address);
		jGen.writeEndObject();
	}
}
