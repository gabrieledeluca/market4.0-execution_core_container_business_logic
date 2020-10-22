package it.eng.idsa.businesslogic.processor.producer;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;

import de.fraunhofer.iais.eis.Message;
import it.eng.idsa.businesslogic.configuration.WebSocketServerConfigurationA;
import it.eng.idsa.businesslogic.processor.consumer.websocket.server.ResponseMessageBufferBean;
import it.eng.idsa.businesslogic.service.MultipartMessageService;
import it.eng.idsa.businesslogic.service.impl.HttpHeaderServiceImpl;
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
public class ProducerSendResponseToDataAppProcessor implements Processor {

	@Value("${application.isEnabledClearingHouse}")
	private boolean isEnabledClearingHouse;

	@Autowired
	private MultipartMessageService multipartMessageService;

	@Value("${application.dataApp.websocket.isEnabled}")
	private boolean isEnabledWebSocket;

	@Value("${application.openDataAppReceiverRouter}")
	private String openDataAppReceiverRouter;

	@Value("${application.isEnabledUsageControl:false}")
	private boolean isEnabledUsageControl;

	@Autowired(required = false)
	WebSocketServerConfigurationA webSocketServerConfiguration;

	@Override
	public void process(Exchange exchange) throws Exception {

		Map<String, Object> headerParts = exchange.getIn().getHeaders();
		MultipartMessage multipartMessage = exchange.getIn().getBody(MultipartMessage.class);
		
		String responseString = null;
		String contentType = null;

		// Put in the header value of the application.property:
		// application.isEnabledClearingHouse
		headerParts.put("Is-Enabled-Clearing-House", isEnabledClearingHouse);

		if (openDataAppReceiverRouter.equals("http-header")) {
			removeTokenFromHeaders(headerParts);
			if (!isEnabledClearingHouse) {
				// clear from Headers multipartMessageBody (it is not usable for the Open Data App)
				headerParts.remove("multipartMessageBody");
				headerParts.remove("Is-Enabled-Clearing-House");
			}
			responseString = multipartMessage.getPayloadContent();
			contentType = headerParts.get("Payload-Content-Type").toString();
		} else {

			responseString = MultipartMessageProcessor.multipartMessagetoString(multipartMessage, false);
			contentType = headerParts.getOrDefault("Content-Type", "multipart/mixed").toString();
			}

			
			if (!isEnabledClearingHouse) {
				// clear from Headers multipartMessageBody (it is not unusable for the Open Data App)
				headerParts.remove("multipartMessageBody");
				headerParts.remove("Is-Enabled-Clearing-House");
			}

			// TODO: Send The MultipartMessage message to the WebSocket
//			if (isEnabledWebSocket && !isEnabledUsageControl) {
//				ResponseMessageBufferBean responseMessageServerBean = webSocketServerConfiguration
//						.responseMessageBufferWebSocket();
//				responseMessageServerBean.add(responseMultipartMessageString.getBytes());
//			}
//			exchange.getOut().setBody(responseMultipartMessageString);
		
		
		HeaderCleaner.removeTechnicalHeaders(headerParts);
		headerParts.put("Content-Type", contentType);
		exchange.getOut().setBody(responseString);
		exchange.getOut().setHeaders(headerParts);

	}

	private void removeTokenFromHeaders(Map<String, Object> headerParts) {
		headerParts.remove("IDS-SecurityToken-Type");
		headerParts.remove("IDS-SecurityToken-Id");
		headerParts.remove("IDS-SecurityToken-TokenFormat");
		headerParts.remove("IDS-SecurityToken-TokenValue");

	}

	private String filterHeader(String header) throws JsonMappingException, JsonProcessingException {
		Message message = multipartMessageService.getMessage(header);
		return multipartMessageService.removeToken(message);
	}

	private String filterRejectionMessageHeader(String header) throws JsonMappingException, JsonProcessingException {
		Message message = multipartMessageService.getMessage(header);
		return multipartMessageService.removeToken(message);
	}

}