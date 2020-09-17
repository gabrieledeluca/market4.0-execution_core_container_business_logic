package it.eng.idsa.businesslogic.processor.consumer;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import it.eng.idsa.businesslogic.configuration.WebSocketServerConfigurationB;
import it.eng.idsa.businesslogic.processor.consumer.websocket.server.ResponseMessageBufferBean;
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

	@Autowired(required = false)
	private WebSocketServerConfigurationB webSocketServerConfiguration;

	@Override
	public void process(Exchange exchange) throws Exception {

		Map<String, Object> headesParts = exchange.getIn().getHeaders();

		// Put in the header value of the application.property:
		// application.isEnabledClearingHouse
		headesParts.put("Is-Enabled-Clearing-House", isEnabledClearingHouse);
		if (!eccHttpSendRouter.equals("http-header")) {
			Map<String, Object> multipartMessagePartsReceived = exchange.getIn().getBody(HashMap.class);
			// Get header, payload and message
			String header = multipartMessagePartsReceived.get("header").toString();
			String payload = null;
			if (multipartMessagePartsReceived.containsKey("payload")) {
				payload = multipartMessagePartsReceived.get("payload").toString();
			}

			MultipartMessage responseMessage = new MultipartMessageBuilder().withHeaderContent(header)
					.withPayloadContent(payload).build();
			String responseString = MultipartMessageProcessor.multipartMessagetoString(responseMessage, false);
			String contentType = responseMessage.getHttpHeaders().getOrDefault("Content-Type", "multipart/mixed");
			headesParts.put("Content-Type", contentType);
			exchange.getOut().setBody(responseString);
			// TODO: Send The MultipartMessage message to the WebSocket
			if (isEnabledIdscp || isEnabledWebSocket) { // TODO Try to remove this config property
				ResponseMessageBufferBean responseMessageServerBean = webSocketServerConfiguration
						.responseMessageBufferWebSocket();
				responseMessageServerBean.add(responseString.getBytes());
			}
		} else {
			exchange.getOut().setBody(exchange.getIn().getBody());
		}
		exchange.getOut().setHeaders(headesParts);

	}
}
