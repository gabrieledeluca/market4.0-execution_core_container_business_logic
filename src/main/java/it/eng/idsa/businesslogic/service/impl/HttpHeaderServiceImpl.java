package it.eng.idsa.businesslogic.service.impl;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.json.simple.JsonObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.eng.idsa.businesslogic.service.HttpHeaderService;

@Service
public class HttpHeaderServiceImpl implements HttpHeaderService {

	@Value("${application.isEnabledDapsInteraction}")
	private boolean isEnabledDapsInteraction;

	@Override
	public String getHeaderMessagePartFromHttpHeadersWithToken(Map<String, Object> headers)
			throws JsonProcessingException {

		Map<String, Object> headerAsMap = getHeaderMessagePartAsMap(headers);
		Map<String, Object> tokenAsMap = addTokenHeadersToReceivedMessageHeaders(headers);

		JsonObject jsonHeader = new JsonObject(headerAsMap);
		jsonHeader.put("authorizationToken", tokenAsMap);

		removeTokenHeaders(headers);

		String header = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(jsonHeader);

		return header;
	}

	private Map<String, Object> addTokenHeadersToReceivedMessageHeaders(Map<String, Object> headers) {
		Map<String, Object> tokenAsMap = new HashMap<>();
		Map<String, Object> tokenFormatAsMap = new HashMap<>();
		tokenAsMap.put("@type", headers.get("IDS-SecurityToken-Type"));
		tokenAsMap.put("@id", headers.get("IDS-SecurityToken-Id"));
		tokenFormatAsMap.put("@id", headers.get("IDS-SecurityToken-TokenFormat"));
		tokenAsMap.put("tokenFormat", tokenFormatAsMap);
		tokenAsMap.put("tokenValue", headers.get("IDS-SecurityToken-TokenValue"));
		return tokenAsMap;
	}

	@Override
	public void removeTokenHeaders(Map<String, Object> headers) {
		headers.remove("IDS-SecurityToken-Type");
		headers.remove("IDS-SecurityToken-Id");
		headers.remove("IDS-SecurityToken-TokenFormat");
		headers.remove("IDS-SecurityToken-TokenValue");
	}

	@Override
	public Map<String, Object> prepareMessageForSendingAsHttpHeadersWithToken(String header)
			throws JsonParseException, JsonMappingException, IOException {

		Map<String, Object> messageAsMap = prepareMessageForSendingAsHttpHeadersWithoutToken(header);

		addTokenToPreparedMessage(header, messageAsMap);

		return messageAsMap;

	}

	private void addTokenToPreparedMessage(String header, Map<String, Object> messageAsMap) throws IOException {
		Map<String, Object> messageAsMapWithToken = new ObjectMapper().readValue(header, Map.class);

		Map<String, Object> tokenAsMap = (Map<String, Object>) messageAsMapWithToken.get("authorizationToken");
		messageAsMap.put("IDS-SecurityToken-Type", tokenAsMap.get("@type").toString());
		messageAsMap.put("IDS-SecurityToken-Id", tokenAsMap.get("@id").toString());
		Map<String, Object> tokenFormatAsMap = (Map<String, Object>) tokenAsMap.get("tokenFormat");
		messageAsMap.put("IDS-SecurityToken-TokenFormat", tokenFormatAsMap.get("@id").toString());
		messageAsMap.put("IDS-SecurityToken-TokenValue", tokenAsMap.get("tokenValue").toString());

	}

	@Override
	public String getHeaderMessagePartFromHttpHeadersWithoutToken(Map<String, Object> headers)
			throws JsonProcessingException {

		Map<String, Object> headerAsMap = getHeaderMessagePartAsMap(headers);

		removeMessageHeadersWithoutToken(headers);

		String header = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(headerAsMap);

		return header;
	}

	private Map<String, Object> getHeaderMessagePartAsMap(Map<String, Object> headers) {
		Map<String, Object> headerAsMap = new HashMap<>();

		if (headers.get("IDS-Messagetype") != null) {
			headerAsMap.put("@type", headers.get("IDS-Messagetype"));
		}
		if (headers.get("IDS-Messagetype") != null) {
			headerAsMap.put("@id", headers.get("IDS-Id"));
		}
		if (headers.get("IDS-Issued") != null) {
			headerAsMap.put("issued", headers.get("IDS-Issued"));
		}
		if (headers.get("IDS-ModelVersion") != null) {
			headerAsMap.put("modelVersion", headers.get("IDS-ModelVersion"));
		}
		if (headers.get("IDS-IssuerConnector") != null) {
			headerAsMap.put("issuerConnector", headers.get("IDS-IssuerConnector"));
		}
		if (headers.get("IDS-TransferContract") != null) {
			headerAsMap.put("transferContract", headers.get("IDS-TransferContract"));
		}
		if (headers.get("IDS-CorrelationMessage") != null) {
			headerAsMap.put("correlationMessage", headers.get("IDS-CorrelationMessage"));
		}
		if (headers.get("IDS-RequestedArtifact") != null) {
			headerAsMap.put("requestedArtifact", headers.get("IDS-RequestedArtifact"));
		}
		return headerAsMap;
	}

	private void removeMessageHeadersWithoutToken(Map<String, Object> headers) {
		headers.remove("IDS-Messagetype");
		headers.remove("IDS-Id");
		headers.remove("IDS-Issued");
		headers.remove("IDS-ModelVersion");
		headers.remove("IDS-IssuerConnector");
		headers.remove("IDS-TransferContract");
		headers.remove("IDS-CorrelationMessage");
		headers.remove("IDS-RequestedArtifact");

	}

	@Override
	public Map<String, Object> prepareMessageForSendingAsHttpHeadersWithoutToken(String header)
			throws JsonParseException, JsonMappingException, IOException {
		Map<String, Object> messageAsMap = new ObjectMapper().readValue(header, Map.class);

		Map<String, Object> headers = new HashMap<>();

		if (messageAsMap.get("@type") != null) {
			headers.put("IDS-Messagetype", (String) messageAsMap.get("@type"));
		}
		if (messageAsMap.get("@id") != null) {
			headers.put("IDS-Id", messageAsMap.get("@id"));
		}
		if (messageAsMap.get("issued") != null) {
			headers.put("IDS-Issued", messageAsMap.get("issued"));
		}
		if (messageAsMap.get("modelVersion") != null) {
			headers.put("IDS-ModelVersion", messageAsMap.get("modelVersion"));
		}
		if(messageAsMap.get("issuerConnector") != null) {
			headers.put("IDS-IssuerConnector", messageAsMap.get("issuerConnector"));
		}
		if(messageAsMap.get("transferContract") != null) {
			headers.put("IDS-TransferContract", messageAsMap.get("transferContract"));
		}
		if(messageAsMap.get("correlationMessage") != null) {
			headers.put("IDS-CorrelationMessage", messageAsMap.get("correlationMessage"));
		}
		if(messageAsMap.get("requestedArtifact") != null) {
			headers.put("IDS-RequestedArtifact", messageAsMap.get("requestedArtifact"));
		}

		return headers;
	}

	@Override
	public Map<String, String> getHeaderContentHeaders(Map<String, Object> headersParts) {
		
		Map<String, String> headerContentHeaders = new HashMap<>();
		
		if (headersParts.get("IDS-Messagetype") != null) {
			headerContentHeaders.put("IDS-Messagetype", headersParts.get("IDS-Messagetype").toString());
		}
		if (headersParts.get("IDS-Messagetype") != null) {
			headerContentHeaders.put("IDS-Id", headersParts.get("IDS-Id").toString());
		}
		if (headersParts.get("IDS-Issued") != null) {
			headerContentHeaders.put("IDS-Issued", headersParts.get("IDS-Issued").toString());
		}
		if (headersParts.get("IDS-ModelVersion") != null) {
			headerContentHeaders.put("IDS-ModelVersion", headersParts.get("IDS-ModelVersion").toString());
		}
		if (headersParts.get("IDS-IssuerConnector") != null) {
			headerContentHeaders.put("IDS-IssuerConnector", headersParts.get("IDS-IssuerConnector").toString());
		}
		if (headersParts.get("IDS-TransferContract") != null) {
			headerContentHeaders.put("IDS-TransferContract", headersParts.get("IDS-TransferContract").toString());
		}
		if (headersParts.get("IDS-CorrelationMessage") != null) {
			headerContentHeaders.put("IDS-CorrelationMessage", headersParts.get("IDS-CorrelationMessage").toString());
		}	
		if (headersParts.get("IDS-RequestedArtifact") != null) {
			headerContentHeaders.put("IDS-RequestedArtifact", headersParts.get("IDS-RequestedArtifact").toString());
		}
		
		if (isEnabledDapsInteraction && headersParts.get("IDS-SecurityToken-TokenValue") != null) {
			headerContentHeaders.put("IDS-SecurityToken-Type", headersParts.get("IDS-SecurityToken-Type").toString());
			headerContentHeaders.put("IDS-SecurityToken-Id", headersParts.get("IDS-SecurityToken-Id").toString());
			headerContentHeaders.put("IDS-SecurityToken-TokenFormat", headersParts.get("IDS-SecurityToken-TokenFormat").toString());
			headerContentHeaders.put("IDS-SecurityToken-TokenValue", headersParts.get("IDS-SecurityToken-TokenValue").toString());
		}
		
		return headerContentHeaders;
	}

}
