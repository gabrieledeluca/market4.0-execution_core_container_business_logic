package it.eng.idsa.businesslogic.processor.consumer;

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import de.fraunhofer.iais.eis.Message;
import it.eng.idsa.businesslogic.configuration.WebSocketServerConfigurationB;
import it.eng.idsa.businesslogic.processor.consumer.websocket.server.ResponseMessageBufferBean;
import it.eng.idsa.businesslogic.service.MultipartMessageService;
import it.eng.idsa.businesslogic.service.RejectionMessageService;
import it.eng.idsa.businesslogic.util.HeaderCleaner;
import it.eng.idsa.multipart.builder.MultipartMessageBuilder;
import it.eng.idsa.multipart.domain.MultipartMessage;
import it.eng.idsa.multipart.processor.MultipartMessageProcessor;

/**
 * 
 * @author Milan Karajovic and Gabriele De Luca
 *
 */

@Component
public class ConsumerSendDataToBusinessLogicProcessor implements Processor {

	@Value("${application.isEnabledClearingHouse}")
	private boolean isEnabledClearingHouse;

	@Value("${application.idscp.isEnabled}")
	private boolean isEnabledIdscp;

	@Value("${application.websocket.isEnabled}")
	private boolean isEnabledWebSocket;

	@Value("${application.eccHttpSendRouter}")
	private String eccHttpSendRouter;
	
	@Value("${application.isEnabledDapsInteraction}")
	private boolean isEnabledDapsInteraction;
	
	@Autowired
	private MultipartMessageService multipartMessageService;

	@Autowired(required = false)
	private WebSocketServerConfigurationB webSocketServerConfiguration;

	@Override
	public void process(Exchange exchange) throws Exception {

		Map<String, Object> headersParts = exchange.getIn().getHeaders();
		MultipartMessage multipartMessage = exchange.getIn().getBody(MultipartMessage.class);
		String responseString = null;
		String contentType = null;

		// Put in the header value of the application.property:
		// application.isEnabledClearingHouse
		headersParts.put("Is-Enabled-Clearing-House", isEnabledClearingHouse);
		if (eccHttpSendRouter.equals("http-header")) {
			responseString = multipartMessage.getPayloadContent();
			contentType = headersParts.get("Payload-Content-Type").toString();
			headersParts.putAll(multipartMessage.getHttpHeaders());
		} else {
//			Message msgWithToken = addToken(multipartMessage.getHeaderContent(), multipartMessage.getToken())
			String message ;
			if(isEnabledDapsInteraction) {
//				String messageWithToken = multipartMessageService.addToken(multipartMessage.getHeaderContent(), multipartMessage.getToken());
//				Message msgToken = multipartMessageService.getMessage(messageWithToken);
//				MultipartMessage multipartmessageWithToken = new MultipartMessageBuilder()
//						.withHttpHeader(multipartMessage.getHttpHeaders())
//						.withHeaderHeader(multipartMessage.getHeaderHeader())
//						.withHeaderContent(messageWithToken)
//						.withPayloadHeader(multipartMessage.getPayloadHeader())
//						.withPayloadContent(multipartMessage.getPayloadContent())
//						.withToken(multipartMessage.getToken()).build(); 
				MultipartMessage multipartMessageWithToken = multipartMessageService.addTokenToMultipartMessage(multipartMessage);
				responseString = MultipartMessageProcessor.multipartMessagetoString(multipartMessageWithToken, false);
//				responseString = multipartMessageService.addToken(multipartMessage.getHeaderContent(), multipartMessage.getToken());
			} else {
				responseString = multipartMessage.getHeaderContentString();
			}
			contentType = headersParts.getOrDefault("Content-Type", "multipart/mixed").toString();
		}

		// TODO: Send The MultipartMessage message to the WebSocket
		if (isEnabledIdscp || isEnabledWebSocket) { // TODO Try to remove this config property
			ResponseMessageBufferBean responseMessageServerBean = webSocketServerConfiguration
					.responseMessageBufferWebSocket();
			responseMessageServerBean.add(responseString.getBytes());
		}

		if (isEnabledClearingHouse) {
			// Put in the header value of the application.property:
			// application.isEnabledClearingHouse
			headersParts.put("Is-Enabled-Clearing-House", isEnabledClearingHouse);
		}
		if (isEnabledWebSocket) {
			headersParts.put("Is-Enabled-DataApp-WebSocket", isEnabledWebSocket);
		}
		HeaderCleaner.removeTechnicalHeaders(headersParts);
		headersParts.put("Content-Type", contentType);
		exchange.getOut().setBody(responseString);
		exchange.getOut().setHeaders(headersParts);

	}
}
