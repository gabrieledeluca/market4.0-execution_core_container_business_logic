package it.eng.idsa.businesslogic.service.impl;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.ByteArrayBody;
import org.apache.http.entity.mime.content.ContentBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;

import de.fraunhofer.iais.eis.Message;
import it.eng.idsa.businesslogic.service.HttpHeaderService;
import it.eng.idsa.businesslogic.service.MultipartMessageService;
import it.eng.idsa.businesslogic.service.RejectionMessageService;
import it.eng.idsa.businesslogic.service.SendDataToBusinessLogicService;
import it.eng.idsa.businesslogic.util.HeaderCleaner;
import it.eng.idsa.businesslogic.util.RejectionMessageType;
import it.eng.idsa.businesslogic.util.communication.HttpClientGenerator;
import it.eng.idsa.businesslogic.util.config.keystore.AcceptAllTruststoreConfig;
import it.eng.idsa.multipart.domain.MultipartMessage;

@Service
public class SendDataToBusinessLogicServiceImpl implements SendDataToBusinessLogicService {

	private static final Logger logger = LogManager.getLogger(SendDataToBusinessLogicServiceImpl.class);

	@Value("${camel.component.jetty.use-global-ssl-context-parameters}")
	private boolean isJettySSLEnabled;

	@Value("${application.isEnabledDapsInteraction}")
	private boolean isEnabledDapsInteraction;
	
	@Value("${application.openDataAppReceiverRouter}")
	private String openDataAppReceiverRouter;
	
	@Autowired
	private RejectionMessageService rejectionMessageService;

	@Autowired
	private MultipartMessageService multipartMessageService;
	
	@Autowired
	private HttpHeaderService headerService;

	@Override
	public CloseableHttpResponse sendMessageBinary(String address, MultipartMessage multipartMessage,
			Map<String, Object> headerParts, boolean eccCommunication) throws UnsupportedEncodingException, JsonProcessingException {

		String header = null;
		String payload = multipartMessage.getPayloadContent();
		Message messageForExcepiton = multipartMessage.getHeaderContent();

		logger.info("Forwarding Message: Body: binary");
		
		if(!eccCommunication) {
			// sending to DataApp, remove token from message
			header = multipartMessageService.removeToken(multipartMessage.getHeaderContent());
		} else {
			header = multipartMessage.getHeaderContentString();
		}
		ContentType ctPayload;
		String contentTypeRemoval = "Content-Type: ";
		if (headerParts.get("Payload-Content-Type") != null) {
			ctPayload = ContentType
					.parse(headerParts.get("Payload-Content-Type").toString().replaceFirst(contentTypeRemoval, ""));
		} else {
			ctPayload = ContentType.TEXT_PLAIN;
		}
		// Covert to ContentBody
		ContentBody cbHeader = this.convertToContentBody(header, ContentType.APPLICATION_JSON, "header");
		ContentBody cbPayload = null;

		if (payload != null) {
			cbPayload = convertToContentBody(payload, ctPayload, "payload");
		}

		// Set F address
		HttpPost httpPost = new HttpPost(address);

		addHeadersToHttpPost(headerParts, httpPost);

//		httpPost.addHeader("headerHeaders", headerHeadersAsString);
//		httpPost.addHeader("payloadHeaders", payloadHeadersAsString);

		HttpEntity reqEntity = payload == null ? MultipartEntityBuilder.create().addPart("header", cbHeader).build()
				: MultipartEntityBuilder.create().addPart("header", cbHeader).addPart("payload", cbPayload).build();

		httpPost.setEntity(reqEntity);

		CloseableHttpResponse response = null;
		try {
			response = getHttpClient().execute(httpPost);
		} catch (IOException e) {
			logger.error(e);
			rejectionMessageService.sendRejectionMessage(RejectionMessageType.REJECTION_COMMUNICATION_LOCAL_ISSUES,
					messageForExcepiton);
		}

		return response;
	}

	@Override
	public CloseableHttpResponse sendMessageHttpHeader(String address, MultipartMessage multipartMessage,
			Map<String, Object> headerParts) {
		logger.info("Forwarding Message: http-header");

		if(!openDataAppReceiverRouter.equals("http-header")) {
			// DataApp endpoint not http-header, must convert message to http headers
			headerParts.putAll(headerService.prepareMessageForSendingAsHttpHeaders(multipartMessage));
		}
		// Set F address
		HttpPost httpPost = new HttpPost(address);

		ContentType ctPayload;

		String contentTypeRemoval = "Content-Type: ";

		if (headerParts.get("Payload-Content-Type") != null) {
			ctPayload = ContentType
					.parse(headerParts.get("Payload-Content-Type").toString().replaceFirst(contentTypeRemoval, ""));
		} else {
			ctPayload = ContentType.TEXT_PLAIN;
		}
		if (multipartMessage.getPayloadContent() != null) {
			StringEntity payloadEntity = new StringEntity(multipartMessage.getPayloadContent(),ctPayload);
			httpPost.setEntity(payloadEntity);
		}
		
		headerParts.putAll(multipartMessage.getHttpHeaders());
		addHeadersToHttpPost(headerParts, httpPost);
		

		CloseableHttpResponse response;

		try {
			response = getHttpClient().execute(httpPost);
		} catch (IOException e) {
			logger.error("Error while calling Consumer", e);
			return null;
		}
		return response;
	}

	private ContentBody convertToContentBody(String value, ContentType contentType, String valueName)
			throws UnsupportedEncodingException {
		byte[] valueBiteArray = value.getBytes("utf-8");
		ContentBody cbValue = new ByteArrayBody(valueBiteArray, contentType, valueName);
		return cbValue;
	}

	private CloseableHttpClient getHttpClient() {
		AcceptAllTruststoreConfig config = new AcceptAllTruststoreConfig();

		CloseableHttpClient httpClient = HttpClientGenerator.get(config, isJettySSLEnabled);
		logger.warn("Created Accept-All Http Client");

		return httpClient;
	}

	private void addHeadersToHttpPost(Map<String, Object> headesParts, HttpPost httpPost) {
		HeaderCleaner.removeTechnicalHeaders(headesParts);

		headesParts.forEach((name, value) -> {
			if (!name.equals("Content-Length") && !name.equals("Content-Type")) {
				if (value != null) {
					httpPost.setHeader(name, value.toString());
				} else {
					httpPost.setHeader(name, null);
				}

			}
		});
	}


	@Override
	public CloseableHttpResponse sendMessageFormData(String address, MultipartMessage multipartMessage,
			Map<String, Object> headerParts, boolean eccCommunication) throws UnsupportedEncodingException {
		
		logger.info("Forwarding Message: Body: form-data");
		
		String header = null;
		String payload = multipartMessage.getPayloadContent();
		Message messageForException = multipartMessage.getHeaderContent();
		
		if(!eccCommunication) {
			// sending to DataApp, remove token from message
			header = multipartMessageService.removeToken(multipartMessage.getHeaderContent());
		} else {
			header = multipartMessage.getHeaderContentString();
		}
		
		HttpPost httpPost = new HttpPost(address);
		ContentType ctPayload;
		if (headerParts.get("payload.org.eclipse.jetty.servlet.contentType") != null) {
			ctPayload = ContentType
					.parse(headerParts.get("payload.org.eclipse.jetty.servlet.contentType").toString());
		} else {
			ctPayload = ContentType.TEXT_PLAIN;
		}
		if (multipartMessage.getPayloadContent() != null) {
			StringEntity payloadEntity = new StringEntity(multipartMessage.getPayloadContent(),ctPayload);
			httpPost.setEntity(payloadEntity);
		}
		addHeadersToHttpPost(headerParts, httpPost);
		HttpEntity reqEntity = multipartMessageService.createMultipartMessage(header, payload, null,ctPayload);
		httpPost.setEntity(reqEntity);
		CloseableHttpResponse response=null;
		
		try {
			response = getHttpClient().execute(httpPost);
		} catch (IOException e) {
			logger.error(e);
			rejectionMessageService.sendRejectionMessage(RejectionMessageType.REJECTION_COMMUNICATION_LOCAL_ISSUES,
					messageForException);
		}
		return response;
	}

}
