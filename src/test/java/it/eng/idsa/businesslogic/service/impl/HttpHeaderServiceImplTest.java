package it.eng.idsa.businesslogic.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.json.JSONException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.core.JsonProcessingException;

import it.eng.idsa.multipart.processor.MultipartMessageProcessor;

public class HttpHeaderServiceImplTest {
	
	private HttpHeaderServiceImpl httpHeaderServiceImpl;
	
	private Map<String, Object> headers;
	
	private String headerMessagePart;
	
	@BeforeEach
	public void init() {
		
		httpHeaderServiceImpl = new HttpHeaderServiceImpl();
		
		/*headerMessagePart = "{\r\n" + 
				"  \"@type\" : \"ids:ArtifactResponseMessage\",\r\n" + 
				"  \"issued\" : \"2019-05-27T13:09:42.306Z\",\r\n" + 
				"  \"issuerConnector\" : \"http://iais.fraunhofer.de/ids/mdm-connector\",\r\n" + 
				"  \"correlationMessage\" : \"http://industrialdataspace.org/connectorUnavailableMessage/1a421b8c-3407-44a8-aeb9-253f145c869a\",\r\n" + 
				"  \"transferContract\" : \"https://mdm-connector.ids.isst.fraunhofer.de/examplecontract/bab-bayern-sample/\",\r\n" + 
				"  \"modelVersion\" : \"1.0.2-SNAPSHOT\",\r\n" + 
				"  \"@id\" : \"https://w3id.org/idsa/autogen/artifactResponseMessage/eb3ab487-dfb0-4d18-b39a-585514dd044f\"\r\n" + 
				"}"; */
		
		headerMessagePart = "{\r\n" +
				"  \"correlationMessage\" : \"http://industrialdataspace.org/connectorUnavailableMessage/1a421b8c-3407-44a8-aeb9-253f145c869a\",\r\n" + 
				"  \"issuerConnector\" : \"http://iais.fraunhofer.de/ids/mdm-connector\",\r\n" + 
				"  \"@type\" : \"ids:ArtifactResponseMessage\",\r\n" + 
				"  \"modelVersion\" : \"1.0.2-SNAPSHOT\",\r\n" + 
				"  \"@id\" : \"https://w3id.org/idsa/autogen/artifactResponseMessage/eb3ab487-dfb0-4d18-b39a-585514dd044f\",\r\n" + 
				"  \"issued\" : \"2019-05-27T13:09:42.306Z\",\r\n" + 
				"  \"transferContract\" : \"https://mdm-connector.ids.isst.fraunhofer.de/examplecontract/bab-bayern-sample/\"\r\n" + 
				"}";
		
		headers = new HashMap<String, Object>();
		headers.put("IDS-Messagetype","ids:ArtifactResponseMessage");
		headers.put("IDS-Issued","2019-05-27T13:09:42.306Z");
		headers.put("IDS-IssuerConnector","http://iais.fraunhofer.de/ids/mdm-connector");
		headers.put("IDS-CorrelationMessage","http://industrialdataspace.org/connectorUnavailableMessage/1a421b8c-3407-44a8-aeb9-253f145c869a");
		headers.put("IDS-TransferContract","https://mdm-connector.ids.isst.fraunhofer.de/examplecontract/bab-bayern-sample/");
		headers.put("IDS-Id","https://w3id.org/idsa/autogen/artifactResponseMessage/eb3ab487-dfb0-4d18-b39a-585514dd044f");
		headers.put("IDS-ModelVersion","1.0.2-SNAPSHOT");

		
	}
	
	
	@Test
	public void headerMessagePartFromHttpHeadersWithToken() throws JsonProcessingException, JSONException {
		assertEquals(headerMessagePart, httpHeaderServiceImpl.getHeaderMessagePartFromHttpHeadersWithoutToken(headers));
		
	}

}
