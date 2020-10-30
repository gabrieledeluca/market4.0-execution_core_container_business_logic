package it.eng.idsa.businesslogic.processor.producer;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import it.eng.idsa.businesslogic.configuration.WebSocketServerConfigurationA;
import it.eng.idsa.businesslogic.service.MultipartMessageService;
import it.eng.idsa.businesslogic.util.HeaderCleaner;
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

	@Value("${application.eccHttpSendRouter}")
	private String eccHttpSendRouter;

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

		switch (openDataAppReceiverRouter) {
		case "form":
		case "mixed":
			MultipartMessage multipartMessageWithoutToken = multipartMessageService
					.removeTokenFromMultipart(multipartMessage);
			responseString = MultipartMessageProcessor.multipartMessagetoString(multipartMessageWithoutToken, false);
			Optional<String> boundary = getMessageBoundaryFromMessage(responseString);
			contentType = "multipart/mixed; boundary=" + boundary.orElse("---aaa") + ";charset=UTF-8";
			break;
		case "http-header":
			responseString = multipartMessage.getPayloadContent();
			contentType = headerParts.get("Payload-Content-Type").toString();
			break;
		default:
			break;
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
	
	private Optional<String> getMessageBoundaryFromMessage(String message) {
        String boundary = null;
        Stream<String> lines = message.lines();
        boundary = lines.filter(line -> line.startsWith("--"))
                .findFirst()
                .get();
        return Optional.ofNullable(boundary);
    }
}