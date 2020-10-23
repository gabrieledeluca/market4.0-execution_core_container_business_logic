package it.eng.idsa.businesslogic.processor.producer;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.http.Header;
import org.apache.http.ParseException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.asynchttpclient.ws.WebSocket;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import de.fhg.aisec.ids.comm.client.IdscpClient;
import de.fraunhofer.iais.eis.Message;
import it.eng.idsa.businesslogic.configuration.WebSocketClientConfiguration;
import it.eng.idsa.businesslogic.processor.producer.websocket.client.FileStreamingBean;
import it.eng.idsa.businesslogic.processor.producer.websocket.client.IdscpClientBean;
import it.eng.idsa.businesslogic.processor.producer.websocket.client.MessageWebSocketOverHttpSender;
import it.eng.idsa.businesslogic.service.HttpHeaderService;
import it.eng.idsa.businesslogic.service.MultipartMessageService;
import it.eng.idsa.businesslogic.service.RejectionMessageService;
import it.eng.idsa.businesslogic.service.impl.SendDataToBusinessLogicServiceImpl;
import it.eng.idsa.businesslogic.util.RejectionMessageType;
import it.eng.idsa.multipart.builder.MultipartMessageBuilder;
import it.eng.idsa.multipart.domain.MultipartMessage;
import it.eng.idsa.multipart.processor.MultipartMessageProcessor;

/**
 * @author Milan Karajovic and Gabriele De Luca
 */

@Component
public class ProducerSendDataToBusinessLogicProcessor implements Processor {

	private static final Logger logger = LogManager.getLogger(ProducerSendDataToBusinessLogicProcessor.class);
	// example for the webSocketURL: idscp://localhost:8099
	public static final String REGEX_IDSCP = "(idscp://)([^:^/]*)(:)(\\d*)";
	public static final String REGEX_WSS = "(wss://)([^:^/]*)(:)(\\d*)";

	@Value("${application.idscp.isEnabled}")
	private boolean isEnabledIdscp;

	@Value("${application.websocket.isEnabled}")
	private boolean isEnabledWebSocket;

	@Value("${application.eccHttpSendRouter}")
	private String eccHttpSendRouter;

	@Value("${camel.component.jetty.use-global-ssl-context-parameters}")
	private boolean isJettySSLEnabled;

	@Value("${application.isEnabledDapsInteraction}")
	private boolean isEnabledDapsInteraction;

	@Value("${application.openDataAppReceiverRouter}")
	private String openDataAppReceiverRouter;

	@Autowired
	private MultipartMessageService multipartMessageService;

	@Autowired
	private RejectionMessageService rejectionMessageService;

	@Autowired
	private HttpHeaderService headerService;
	
	@Autowired
	private SendDataToBusinessLogicServiceImpl sendDataToBusinessLogicService;

	@Autowired
	private WebSocketClientConfiguration webSocketClientConfiguration;

	@Autowired
	private MessageWebSocketOverHttpSender messageWebSocketOverHttpSender;

	private String webSocketHost;
	private Integer webSocketPort;

	@Override
	public void process(Exchange exchange) throws Exception {

		
		MultipartMessage multipartMessage = exchange.getIn().getBody(MultipartMessage.class);
		Map<String, Object> headerParts = exchange.getIn().getHeaders();

		String messageWithToken = null;

		String payload = null;
		Message message = null;
		String header =null;

		String multipartMessageString = null;
		payload = multipartMessage.getPayloadContent();
		header= multipartMessage.getHeaderContentString();
		message = multipartMessage.getHeaderContent();

		//message needed for clearing house usage
		MultipartMessage multipartMessageForClearinghouse = new MultipartMessageBuilder().withHeaderContent(message)
				.withPayloadContent(payload).build();
		multipartMessageString = MultipartMessageProcessor.multipartMessagetoString(multipartMessageForClearinghouse);

		String forwardTo = headerParts.get("Forward-To").toString();

		if (isEnabledIdscp) {
			// check & exstract IDSCP WebSocket IP and Port
			try {
				this.extractWebSocketIPAndPort(forwardTo, REGEX_IDSCP);
			} catch (Exception e) {
				logger.info("... bad idscp URL - '{}' {}", forwardTo, e.getMessage());
				rejectionMessageService.sendRejectionMessage(RejectionMessageType.REJECTION_COMMUNICATION_LOCAL_ISSUES,
						message);
			}
			// -- Send data using IDSCP - (Client) - WebSocket
			String response;
			if (Boolean.parseBoolean(headerParts.get("Is-Enabled-Daps-Interaction").toString())) {
				response = this.sendMultipartMessageWebSocket(this.webSocketHost, this.webSocketPort, messageWithToken,
						payload, message);
			} else {
				response = this.sendMultipartMessageWebSocket(this.webSocketHost, this.webSocketPort, header, payload,
						message);
			}
			// Handle response
			this.handleResponseWebSocket(exchange, message, response, forwardTo, multipartMessageString);
		} else if (isEnabledWebSocket) {
			// check & exstract HTTPS WebSocket IP and Port
			try {
				this.extractWebSocketIPAndPort(forwardTo, REGEX_WSS);
			} catch (Exception e) {
				logger.info("... bad wss URL - '{}', {}", forwardTo, e.getMessage());
				rejectionMessageService.sendRejectionMessage(RejectionMessageType.REJECTION_COMMUNICATION_LOCAL_ISSUES,
						message);
			}

			// -- Send data using HTTPS - (Client) - WebSocket
			String response;
			if (Boolean.parseBoolean(headerParts.get("Is-Enabled-Daps-Interaction").toString())) {
				response = messageWebSocketOverHttpSender.sendMultipartMessageWebSocketOverHttps(this.webSocketHost,
						this.webSocketPort, messageWithToken, payload, message);
			} else {
				response = messageWebSocketOverHttpSender.sendMultipartMessageWebSocketOverHttps(this.webSocketHost,
						this.webSocketPort, header, payload, message);
			}
			// Handle response
			this.handleResponseWebSocket(exchange, message, response, forwardTo, multipartMessageString);
		} else {
			// Send MultipartMessage HTTPS
			CloseableHttpResponse response = this.sendMultipartMessage(headerParts,	forwardTo, message,  multipartMessage);
			// Handle response
			this.handleResponse(exchange, message, response, forwardTo, multipartMessageString);

			if (response != null) {
				response.close();
			}
		}

	}

	private CloseableHttpResponse sendMultipartMessage(Map<String, Object> headerParts, String forwardTo, Message message, MultipartMessage multipartMessage)
			throws IOException, KeyManagementException, NoSuchAlgorithmException, InterruptedException,
			ExecutionException, UnsupportedEncodingException {
		CloseableHttpResponse response = null;
		// -- Send message using HTTPS
			switch (eccHttpSendRouter) {
			case "mixed": {
				response = sendDataToBusinessLogicService.sendMessageBinary(forwardTo, multipartMessage, headerParts);
				break;
			}
			case "form": {
				response =sendDataToBusinessLogicService.sendMessageFormData(forwardTo, multipartMessage, headerParts);
				break;
			}
			case "http-header": {
				response = sendDataToBusinessLogicService.sendMessageHttpHeader(forwardTo, multipartMessage, headerParts);
				break;
			}
			default:
				logger.error("Applicaton property: application.eccHttpSendRouter is not properly set");
				rejectionMessageService.sendRejectionMessage(RejectionMessageType.REJECTION_MESSAGE_LOCAL_ISSUES,
						message);
			}
		return response;
	}

//	private CloseableHttpResponse forwardMessageHttpHeader(String address, String payload,
//			Map<String, Object> headerParts) throws IOException {
//		logger.info("Forwarding Message: Body: http-header");
//
//		// Set F address
//		HttpPost httpPost = new HttpPost(address);
//
//		// Add header message part to the http headers
////		if (isEnabledDapsInteraction) {
////			headerParts.putAll(headerService.prepareMessageForSendingAsHttpHeadersWithToken(header));
////		}else {
////			headerParts.putAll(headerService.prepareMessageForSendingAsHttpHeadersWithoutToken(header));
////		}
//
//		addHeadersToHttpPost(headerParts, httpPost);
//		
//		if (payload != null) {
//			StringEntity payloadEntity = new StringEntity(payload, 
//					ContentType.create((String)headerParts.get("Content-Type")));
//			httpPost.setEntity(payloadEntity);
//		}
//		CloseableHttpResponse response;
//
//		try {
//			response = getHttpClient().execute(httpPost);
//		} catch (IOException e) {
//			logger.error("Error while calling Consumer", e);
//			return null;
//		} 
//		return response;
//	}

//	private CloseableHttpResponse forwardMessageBinary(String address, String header, String payload,
//			Map<String, Object> headerParts) throws UnsupportedEncodingException {
//		logger.info("Forwarding Message: Body: binary");
//
//		// Covert to ContentBody
//		ContentBody cbHeader = this.convertToContentBody(header, ContentType.DEFAULT_TEXT, "header");
//		ContentBody cbPayload = null;
//
//		if (payload != null) {
//			cbPayload = convertToContentBody(payload, ContentType.DEFAULT_TEXT, "payload");
//		}
//
//		// Set F address
//		HttpPost httpPost = new HttpPost(address);
//		addHeadersToHttpPost(headerParts, httpPost);
//
//		HttpEntity reqEntity = payload == null ? MultipartEntityBuilder.create().addPart("header", cbHeader).build()
//				: MultipartEntityBuilder.create().addPart("header", cbHeader).addPart("payload", cbPayload).build();
//
//		httpPost.setEntity(reqEntity);
//
//		CloseableHttpResponse response;
//		try {
//			response = getHttpClient().execute(httpPost);
//		} catch (IOException e) {
//			logger.error(e);
//			return null;
//		}
//
//		return response;
//	}

//	private void addHeadersToHttpPost(Map<String, Object> headesParts, HttpPost httpPost) {
//		HeaderCleaner.removeTechnicalHeaders(headesParts);
//
//		headesParts.forEach((name, value) -> {
//			if (!name.equals("Content-Length") && !name.equals("Content-Type")) {
//				if (value != null) {
//					httpPost.setHeader(name, value.toString());
//				} else {
//					httpPost.setHeader(name, null);
//				}
//
//			}
//		});
//	}

//	private CloseableHttpResponse forwardMessageFormData(String address, String header, String payload,
//			Map<String, Object> headesParts) throws ClientProtocolException, IOException {
//		logger.info("Forwarding Message: Body: form-data");
//
//		// Set F address
//		HttpPost httpPost = new HttpPost(address);
//		addHeadersToHttpPost(headesParts, httpPost);
//		HttpEntity reqEntity = multipartMessageService.createMultipartMessage(header, payload, null);
//		httpPost.setEntity(reqEntity);
//
//		CloseableHttpResponse response;
//
//		try {
//			response = getHttpClient().execute(httpPost);
//		} catch (IOException e) {
//			logger.error(e);
//			return null;
//		}
//		return response;
//	}

//	private ContentBody convertToContentBody(String value, ContentType contentType, String valueName)
//			throws UnsupportedEncodingException {
//		byte[] valueBiteArray = value.getBytes("utf-8");
//		ContentBody cbValue = new ByteArrayBody(valueBiteArray, contentType, valueName);
//		return cbValue;
//	}

//	private CloseableHttpClient getHttpClient() {
//		AcceptAllTruststoreConfig config = new AcceptAllTruststoreConfig();
//
//		CloseableHttpClient httpClient = HttpClientGenerator.get(config, isJettySSLEnabled);
//		logger.warn("Created Accept-All Http Client");
//
//		return httpClient;
//	}

	private void handleResponse(Exchange exchange, Message message, CloseableHttpResponse response, String forwardTo,
			String multipartMessageBody) throws UnsupportedOperationException, IOException {
		if (response == null) {
			logger.info("...communication error");
			rejectionMessageService.sendRejectionMessage(RejectionMessageType.REJECTION_COMMUNICATION_LOCAL_ISSUES,
					message);
		} else {
			String responseString = new String(response.getEntity().getContent().readAllBytes());
			logger.info("response received from the DataAPP=" + responseString);

			int statusCode = response.getStatusLine().getStatusCode();
			logger.info("status code of the response message is: " + statusCode);
			if (statusCode >= 300) {
				if (statusCode == 404) {
					logger.info("...communication error - bad forwardTo URL " + forwardTo);
					rejectionMessageService
							.sendRejectionMessage(RejectionMessageType.REJECTION_COMMUNICATION_LOCAL_ISSUES, message);
				}
				logger.info("data sent unuccessfully to destination " + forwardTo);
				rejectionMessageService.sendRejectionMessage(RejectionMessageType.REJECTION_MESSAGE_COMMON, message);
			} else {
				logger.info("data sent to destination " + forwardTo);
				logger.info("Successful response: " + responseString);
				// TODO:
				// Set original body which is created using the original payload and header
				exchange.getOut().setHeaders(returnHeadersAsMap(response.getAllHeaders()));
				exchange.getOut().setHeader("multipartMessageBody", multipartMessageBody);
				exchange.getOut().setBody(responseString);
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

	private void handleResponseWebSocket(Exchange exchange, Message message, String responseString, String forwardTo,
			String multipartMessageBody) {
		if (responseString == null) {
			logger.info("...communication error");
			rejectionMessageService.sendRejectionMessage(RejectionMessageType.REJECTION_COMMUNICATION_LOCAL_ISSUES,
					message);
		} else {
			logger.info("response received from the DataAPP=" + responseString);
			logger.info("data sent to destination " + forwardTo);
			logger.info("Successful response: " + responseString);
			// TODO:
			// Set original body which is created using the original payload and header
			exchange.getOut().setHeaders(exchange.getIn().getHeaders());
			exchange.getOut().setHeader("multipartMessageBody", multipartMessageBody);
			exchange.getOut().setBody(responseString);
		}
	}

	private String sendMultipartMessageWebSocket(String webSocketHost, Integer webSocketPort, String header,
			String payload, Message message) throws Exception, ParseException, IOException, KeyManagementException,
			NoSuchAlgorithmException, InterruptedException, ExecutionException {
		// Create idscpClient
		IdscpClientBean idscpClientBean = webSocketClientConfiguration.idscpClientServiceWebSocket();
		this.initializeIdscpClient(message, idscpClientBean);
		IdscpClient idscpClient = idscpClientBean.getClient();

		MultipartMessage multipartMessage = new MultipartMessageBuilder().withHeaderContent(header)
				.withPayloadContent(payload).build();
		String multipartMessageString = MultipartMessageProcessor.multipartMessagetoString(multipartMessage);

		// Send multipartMessage as a frames
		FileStreamingBean fileStreamingBean = webSocketClientConfiguration.fileStreamingWebSocket();
		WebSocket wsClient = this.createWebSocketConnection(idscpClient, webSocketHost, webSocketPort, message);
		// Try to connect to the Server. Wait until you are not connected to the server.
		wsClient.addWebSocketListener(webSocketClientConfiguration.inputStreamSocketListenerWebSocketClient());
		fileStreamingBean.setup(wsClient);
		fileStreamingBean.sendMultipartMessage(multipartMessageString);
		// We don't have status of the response (is it 200 OK or not). We have only the
		// content of the response.
		String responseMessage = new String(
				webSocketClientConfiguration.responseMessageBufferWebSocketClient().remove());
		this.closeWSClient(wsClient);
		logger.info("received response: " + responseMessage);

		return responseMessage;
	}

	private void initializeIdscpClient(Message message, IdscpClientBean idscpClientBean) {
		try {
			idscpClientBean.createIdscpClient();
		} catch (Exception e) {
			logger.info("... can not initilize the IdscpClient");
			logger.error(e);
			rejectionMessageService.sendRejectionMessage(RejectionMessageType.REJECTION_COMMUNICATION_LOCAL_ISSUES,
					message);
		}
	}

	private WebSocket createWebSocketConnection(IdscpClient idscpClient, String webSocketHost, Integer webSocketPort,
			Message message) {
		WebSocket wsClient = null;
		try {
			wsClient = idscpClient.connect(webSocketHost, webSocketPort);
		} catch (Exception e) {
			logger.info("... can not create the WebSocket connection IDSCP");
			logger.error(e);
			rejectionMessageService.sendRejectionMessage(RejectionMessageType.REJECTION_COMMUNICATION_LOCAL_ISSUES,
					message);
		}
		return wsClient;
	}

	private void extractWebSocketIPAndPort(String forwardTo, String regexForwardTo) {
		// Split URL into protocol, host, port
		Pattern pattern = Pattern.compile(regexForwardTo);
		Matcher matcher = pattern.matcher(forwardTo);
		matcher.find();

		this.webSocketHost = matcher.group(2);
		this.webSocketPort = Integer.parseInt(matcher.group(4));
	}

	private void closeWSClient(WebSocket wsClient) {
		// Send the close frame 200 (OK), "Shutdown"; in this method we also close the
		// wsClient.
		try {
			wsClient.sendCloseFrame(200, "Shutdown");
		} catch (Exception e) {
			logger.error(e);
		}
	}
}
