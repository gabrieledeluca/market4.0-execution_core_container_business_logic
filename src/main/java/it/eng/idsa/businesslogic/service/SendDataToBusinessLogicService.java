package it.eng.idsa.businesslogic.service;

import java.io.UnsupportedEncodingException;
import java.util.Map;

import org.apache.http.client.methods.CloseableHttpResponse;

import com.fasterxml.jackson.core.JsonProcessingException;

import it.eng.idsa.multipart.domain.MultipartMessage;

public interface SendDataToBusinessLogicService {

	CloseableHttpResponse sendMessageBinary(String address, MultipartMessage message, Map<String, Object> httpHeaders)
			throws UnsupportedEncodingException, JsonProcessingException;

//	CloseableHttpResponse sendMessageFormData(String address, String header, String payload,
//			Map<String, Object> headesParts);

	CloseableHttpResponse sendMessageHttpHeader(String address, MultipartMessage multipartMessage,
			Map<String, Object> headerParts);
	
	CloseableHttpResponse sendMessageFormData(String address, MultipartMessage message,
			Map<String, Object> headerParts) throws UnsupportedEncodingException;

}
