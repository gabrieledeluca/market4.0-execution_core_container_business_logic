package it.eng.idsa.businesslogic.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;

public class HttpHeaderServiceImplTest {
	
	private HttpHeaderServiceImpl httpHeaderServiceImpl;
	
	private Map<String, Object> headers;
	
	private String headerMessagePart;
	
	@BeforeEach
	public void init() {
		httpHeaderServiceImpl = new HttpHeaderServiceImpl();
		
		headerMessagePart = "{\r\n" +
				"  \"correlationMessage\" : \"http://industrialdataspace.org/connectorUnavailableMessage/1a421b8c-3407-44a8-aeb9-253f145c869a\",\r\n" + 
				"  \"issuerConnector\" : \"http://iais.fraunhofer.de/ids/mdm-connector\",\r\n" + 
				"  \"@type\" : \"ids:ArtifactResponseMessage\",\r\n" + 
				"  \"modelVersion\" : \"4.0.0\",\r\n" + 
				"  \"requestedArtifact\" : \"http://mdm-connector.ids.isst.fraunhofer.de/artifact/1\",\r\n" + 
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
		headers.put("IDS-ModelVersion","4.0.0");
		headers.put("IDS-RequestedArtifact", "http://mdm-connector.ids.isst.fraunhofer.de/artifact/1");
		headers.put("foo", "bar");
		headers.put("Forward-To", "https://forwardToURL");
	}
	
	
	@Test
	public void headerMessagePartFromHttpHeadersWithToken() throws JsonProcessingException, JSONException {
		assertEquals(headerMessagePart, httpHeaderServiceImpl.getHeaderMessagePartFromHttpHeadersWithoutToken(headers));
	}
	
	@Test
	public void getHeaderContentHeadersTest() {
		Map<String, Object> result = httpHeaderServiceImpl.getHeaderContentHeaders(headers);
		assertNotNull(result);
		assertNull(result.get("foo"));
		verifyIDSHeadersPresent(result);
	}


	private void verifyIDSHeadersPresent(Map<String, Object> result) {
		assertNotNull(result.get("IDS-Messagetype"));
		assertNotNull(result.get("IDS-Issued"));
		assertNotNull(result.get("IDS-IssuerConnector"));
		assertNotNull(result.get("IDS-CorrelationMessage"));
		assertNotNull(result.get("IDS-TransferContract"));
		assertNotNull(result.get("IDS-Id"));
		assertNotNull(result.get("IDS-ModelVersion"));
	}
	
	@Test
	public void getHeaderMessagePartAsMapTest() {
		Map<String, Object> result = httpHeaderServiceImpl.getHeaderMessagePartAsMap(headers);
		assertNotNull(result.get("@type"));
		assertNotNull(result.get("@id"));
		assertNotNull(result.get("issued"));
		assertNotNull(result.get("modelVersion"));
		assertNotNull(result.get("issuerConnector"));
		assertNotNull(result.get("transferContract"));
		assertNotNull(result.get("correlationMessage"));
		assertNotNull(result.get("requestedArtifact"));
	}
	
	@Test
	public void prepareMessageForSendingAsHttpHeadersWithoutTokenTest() throws IOException {
		Map<String, Object> result = httpHeaderServiceImpl.prepareMessageForSendingAsHttpHeadersWithoutToken(headerMessagePart);
		verifyIDSHeadersPresent(result);
	}

}
