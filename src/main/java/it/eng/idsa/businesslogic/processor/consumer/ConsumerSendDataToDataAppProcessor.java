package it.eng.idsa.businesslogic.processor.consumer;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
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
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;

import de.fraunhofer.iais.eis.Message;
import it.eng.idsa.businesslogic.configuration.ApplicationConfiguration;
import it.eng.idsa.businesslogic.service.HttpHeaderService;
import it.eng.idsa.businesslogic.service.MultipartMessageService;
import it.eng.idsa.businesslogic.service.RejectionMessageService;
import it.eng.idsa.businesslogic.service.impl.SendDataToBusinessLogicServiceImpl;
import it.eng.idsa.businesslogic.util.HeaderCleaner;
import it.eng.idsa.businesslogic.util.RejectionMessageType;
import it.eng.idsa.businesslogic.util.communication.HttpClientGenerator;
import it.eng.idsa.businesslogic.util.config.keystore.AcceptAllTruststoreConfig;
import it.eng.idsa.multipart.domain.MultipartMessage;

/**
 * 
 * @author Milan Karajovic and Gabriele De Luca
 *
 */

@Component
public class ConsumerSendDataToDataAppProcessor implements Processor {

	private static final Logger logger = LogManager.getLogger(ConsumerSendDataToDataAppProcessor.class);

	@Value("${application.openDataAppReceiverRouter}")
	private String openDataAppReceiverRouter;

	@Value("${application.isEnabledDapsInteraction}")
	private boolean isEnabledDapsInteraction;
	
	@Value("${application.eccHttpSendRouter}")
	private String eccHttpSendRouter;

	@Value("${application.isEnabledUsageControl:false}")
	private boolean isEnabledUsageControl;

	@Autowired
	private ApplicationConfiguration configuration;

	@Autowired
	private MultipartMessageService multipartMessageService;
	
	@Autowired
	private RejectionMessageService rejectionMessageService;

	private String originalHeader;

	@Autowired
	private HttpHeaderService headerService;
	
	@Autowired
	private SendDataToBusinessLogicServiceImpl sendDataToBusinessLogicService;

	@Value("${application.idscp.isEnabled}")
	private boolean isEnabledIdscp;

	@Value("${application.websocket.isEnabled}")
	private boolean isEnabledWebSocket;
	
	@Override
	public void process(Exchange exchange) throws Exception {

		Map<String, Object> headerParts = exchange.getIn().getHeaders();
		Map<String, Object> multipartMessageParts = null;
		MultipartMessage multipartMessage  = exchange.getIn().getBody(MultipartMessage.class);
		
		// Get header, payload and message
		String header = null;
		String payload = null;
		Message message = null;


		this.originalHeader = header;
		// Send data to the endpoint F for the Open API Data App
		CloseableHttpResponse response = null;
		switch(openDataAppReceiverRouter) {
		case "mixed": {
			response = sendDataToBusinessLogicService.sendMessageBinary(configuration.getOpenDataAppReceiver(), multipartMessage, headerParts);
			break;
		}
		case "form": {
			//response = forwardMessageFormData(configuration.getOpenDataAppReceiver(), header, payload, headerParts);
			response = sendDataToBusinessLogicService.sendMessageFormData(configuration.getOpenDataAppReceiver(), multipartMessage, headerParts);
			break;
		}
		case "http-header":
		{
			response =  sendDataToBusinessLogicService.sendMessageHttpHeader(configuration.getOpenDataAppReceiver(), multipartMessage, headerParts);
			break;
		}
		default: {
			logger.error("Applicaton property: application.openDataAppReceiverRouter is not properly set");
			rejectionMessageService.sendRejectionMessage(
					RejectionMessageType.REJECTION_MESSAGE_LOCAL_ISSUES, 
					message);
		}
		}

		// Handle response
		handleResponse(exchange, message, response, configuration.getOpenDataAppReceiver());

		if(response!=null) {
			response.close();
		}	

	}


	private CloseableHttpResponse forwardMessageHttpHeader(String address, String payload, Map<String, Object> headerParts) throws IOException {
		logger.info("Forwarding Message: Body: http-header");
		
		// Set F address
		HttpPost httpPost = new HttpPost(address);
		
		addHeadersToHttpPost(headerParts, httpPost);
		
		if (payload != null) {
			StringEntity payloadEntity = new StringEntity(payload);
			httpPost.setEntity(payloadEntity);
		}
		CloseableHttpResponse response;

		try {
			response = getHttpClient().execute(httpPost);
		} catch (ClientProtocolException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}

		return response;
	}

	private CloseableHttpResponse forwardMessageBinary(String address, String header, String payload,
			Map<String, Object> headerParts) throws ClientProtocolException, IOException {
		logger.info("Forwarding Message: Body: binary");

		// Covert to ContentBody
		ContentBody cbHeader = convertToContentBody(header, ContentType.APPLICATION_JSON, "header");
		ContentBody cbPayload = null;
		if(payload!=null) {
			cbPayload = convertToContentBody(payload, ContentType.DEFAULT_TEXT, "payload");
		}

		// Set F address
		HttpPost httpPost = new HttpPost(address);
		addHeadersToHttpPost(headerParts, httpPost);

		HttpEntity reqEntity = payload==null ?
			MultipartEntityBuilder.create()
				.addPart("header", cbHeader)
				.build()	
				:
			MultipartEntityBuilder.create()
				.addPart("header", cbHeader)
				.addPart("payload", cbPayload)
				.build();

		httpPost.setEntity(reqEntity);

		CloseableHttpResponse response;
		
		try {
			response = getHttpClient().execute(httpPost);
		} catch (IOException e) {
			logger.error("Error executing binary request", e);
			return null;
		}

		return response;
	}

	private CloseableHttpResponse forwardMessageFormData(String address, String header, String payload,
			Map<String, Object> headerParts) throws ClientProtocolException, IOException {
		logger.info("Forwarding Message: Body: form-data");

		// Set F address
		HttpPost httpPost = new HttpPost(address);
		addHeadersToHttpPost(headerParts, httpPost);
		//TODO izracunati pravu vrednost ctPAyload
		HttpEntity reqEntity = multipartMessageService.createMultipartMessage(header, payload, null,ContentType.DEFAULT_TEXT);
		httpPost.setEntity(reqEntity);

		CloseableHttpResponse response;
		
		try {
			response = getHttpClient().execute(httpPost);
		} catch (IOException e) {
			logger.error("Error executing form data request", e);
			return null;
		}

		return response;
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

	private CloseableHttpClient getHttpClient() {
		AcceptAllTruststoreConfig config = new AcceptAllTruststoreConfig();

		CloseableHttpClient httpClient = HttpClientGenerator.get(config, true);
		logger.warn("Created Accept-All Http Client");

		return httpClient;
	}

	private String filterHeader(String header) throws JsonMappingException, JsonProcessingException {
		Message message = multipartMessageService.getMessage(header);
		return multipartMessageService.removeToken(message);
	}

	private ContentBody convertToContentBody(String value, ContentType contentType, String valueName) throws UnsupportedEncodingException {
		byte[] valueBiteArray = value.getBytes("utf-8");
		ContentBody cbValue = new ByteArrayBody(valueBiteArray, contentType, valueName);
		return cbValue;
	}

	private void handleResponse(Exchange exchange, Message message, CloseableHttpResponse response, String openApiDataAppAddress) throws UnsupportedOperationException, IOException {
		if (response==null) {
			logger.info("...communication error with: " + openApiDataAppAddress);
			rejectionMessageService.sendRejectionMessage(
					RejectionMessageType.REJECTION_COMMUNICATION_LOCAL_ISSUES, 
					message);
		} else {
			String responseString=new String(response.getEntity().getContent().readAllBytes());
			logger.info("content type response received from the DataAPP="+response.getFirstHeader("Content-Type"));
			logger.info("response received from the DataAPP="+responseString);

			int statusCode = response.getStatusLine().getStatusCode();
			logger.info("status code of the response message is: " + statusCode);
			if (statusCode >=300) { 
				logger.info("data sent to destination: "+openApiDataAppAddress);
				rejectionMessageService.sendRejectionMessage(
						RejectionMessageType.REJECTION_MESSAGE_COMMON, 
						message);
			}else { 
				logger.info("data sent to destination: "+openApiDataAppAddress);
				logger.info("Successful response: "+ responseString);

				exchange.getOut().setHeaders(returnHeadersAsMap(response.getAllHeaders()));
				if (openDataAppReceiverRouter.equals("http-header")) {
					exchange.getOut().setBody(responseString);
				}else {
					exchange.getOut().setHeader("header", multipartMessageService.getHeaderContentString(responseString));
					exchange.getOut().setHeader("payload", multipartMessageService.getPayloadContent(responseString));

				}
				
				if(isEnabledUsageControl) {
					exchange.getOut().setHeader("Original-Message-Header", originalHeader);
				}
			}
		}
	}
	
	 private Map<String, Object> returnHeadersAsMap(Header[] allHeaders) {
	    	Map<String, Object> headersMap = new HashMap<>();
	    	for (Header header : allHeaders) {
				headersMap.put(header.getName(), header.getValue());
			}
	    	return headersMap;
	}

}
