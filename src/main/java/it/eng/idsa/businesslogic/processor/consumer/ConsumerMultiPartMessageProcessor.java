package it.eng.idsa.businesslogic.processor.consumer;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import de.fraunhofer.iais.eis.Message;
import it.eng.idsa.businesslogic.service.MultipartMessageService;
import it.eng.idsa.businesslogic.service.RejectionMessageService;
import it.eng.idsa.businesslogic.util.RejectionMessageType;

/**
 * 
 * @author Milan Karajovic and Gabriele De Luca
 *
 */

@Component
public class ConsumerMultiPartMessageProcessor implements Processor {

	private static final Logger logger = LogManager.getLogger(ConsumerMultiPartMessageProcessor.class);

	@Value("${application.isEnabledDapsInteraction}")
	private boolean isEnabledDapsInteraction;

	@Value("${application.dataApp.websocket.isEnabled}")
	private boolean isEnabledDataAppWebSocket;

	@Value("${application.isEnabledClearingHouse}")
	private boolean isEnabledClearingHouse;

	@Value("${application.eccHttpSendRouter}")
	private String eccHttpSendRouter;

	@Autowired
	private MultipartMessageService multipartMessageService;

	@Autowired
	private RejectionMessageService rejectionMessageService;

	@Override
	public void process(Exchange exchange) throws Exception {

		String header;
		String payload;
		Message message = null;
		Map<String, Object> headesParts = exchange.getIn().getHeaders();
		headesParts.put("Is-Enabled-Daps-Interaction", isEnabledDapsInteraction);
		headesParts.put("Is-Enabled-Clearing-House", isEnabledClearingHouse);
		headesParts.put("Is-Enabled-DataApp-WebSocket", isEnabledDataAppWebSocket);

		if (eccHttpSendRouter.equals("http-header")) {
			exchange.getOut().setBody(exchange.getIn().getBody());
		} else {
			if (!exchange.getIn().getHeaders().containsKey("header")) {
				logger.error("Multipart message header is null");
				rejectionMessageService.sendRejectionMessage(RejectionMessageType.REJECTION_MESSAGE_COMMON, message);
			}
			Map<String, Object> multipartMessageParts = new HashMap<String, Object>();
			try {

				// Create headers parts
				// Put in the header value of the application.property:
				// application.isEnabledDapsInteraction

				// Save the original message header for Usage Control Enforcement
				if (exchange.getIn().getHeaders().containsKey("Original-Message-Header"))
					headesParts.put("Original-Message-Header",
							exchange.getIn().getHeaders().get("Original-Message-Header").toString());

				if (exchange.getIn().getHeaders().containsKey("payload")) {
					payload = exchange.getIn().getHeader("payload").toString();
					if (payload.equals("RejectionMessage")) {
						// Create multipart message for the RejectionMessage
						header = multipartMessageService
								.getHeaderContentString(exchange.getIn().getHeader("header").toString());
						multipartMessageParts.put("header", header);
					} else {
						// Create multipart message with payload
						header = exchange.getIn().getHeader("header").toString();
						multipartMessageParts.put("header", header);
						payload = exchange.getIn().getHeader("payload").toString();
						multipartMessageParts.put("payload", payload);
						message = multipartMessageService.getMessage(multipartMessageParts.get("header"));
					}
				} else {
					// Create multipart message without payload
					header = exchange.getIn().getHeader("header").toString();
					multipartMessageParts.put("header", header);
					message = multipartMessageService.getMessage(multipartMessageParts.get("header"));
				}

				// Return exchange
				exchange.getOut().setBody(multipartMessageParts);

			} catch (Exception e) {
				logger.error("Error parsing multipart message:", e);
				rejectionMessageService.sendRejectionMessage(RejectionMessageType.REJECTION_MESSAGE_COMMON, message);
			}
		}
		exchange.getOut().setHeaders(headesParts);
	}
}