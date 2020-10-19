package it.eng.idsa.businesslogic.service;

import java.io.IOException;
import java.util.Map;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;

public interface HttpHeaderService {
	
	String getHeaderMessagePartFromHttpHeadersWithoutToken(Map<String, Object> headers) throws JsonProcessingException;

	Map<String, Object> prepareMessageForSendingAsHttpHeadersWithToken(String header) throws JsonParseException, JsonMappingException, IOException;

	String getHeaderMessagePartFromHttpHeadersWithToken(Map<String, Object> headers) throws JsonProcessingException;

	Map<String, Object> prepareMessageForSendingAsHttpHeadersWithoutToken(String header) throws JsonParseException, JsonMappingException, IOException;
	
	void removeTokenHeaders(Map<String, Object> headers);

	Map<String, String> getHeaderContentHeaders(Map<String, Object> headersParts);
}
