package it.eng.idsa.businesslogic.processor.consumer;

import java.util.Map;

import javax.activation.DataHandler;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import de.fraunhofer.iais.eis.Message;
import it.eng.idsa.businesslogic.service.MultipartMessageService;
import it.eng.idsa.businesslogic.service.ReceiveDataFromBusinessLogicService;
import it.eng.idsa.businesslogic.service.RejectionMessageService;
import it.eng.idsa.businesslogic.util.RejectionMessageType;
import it.eng.idsa.multipart.builder.MultipartMessageBuilder;
import it.eng.idsa.multipart.domain.MultipartMessage;

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

	@Value("${ecc.ecc.format}")
	private String eccEccFormat;

	@Autowired
	private MultipartMessageService multipartMessageService;

	@Autowired
	private RejectionMessageService rejectionMessageService;

	@Autowired
	private ReceiveDataFromBusinessLogicService receiveDataFromBusinessLogicService;

	@Override
	public void process(Exchange exchange) throws Exception {

		String header = null;
		String payload = null;
		Map<String, Object> headersParts = exchange.getIn().getHeaders();
		Message message = null;
		MultipartMessage multipartMessage = null;

		headersParts.put("Is-Enabled-Daps-Interaction", isEnabledDapsInteraction);
		headersParts.put("Is-Enabled-Clearing-House", isEnabledClearingHouse);
		headersParts.put("Is-Enabled-DataApp-WebSocket", isEnabledDataAppWebSocket);

		
		
		if (!headersParts.containsKey("header")) {
			logger.error("Multipart message header is null");
			rejectionMessageService.sendRejectionMessage(RejectionMessageType.REJECTION_MESSAGE_COMMON, message);
		}

		try {

			// Save the original message header for Usage Control Enforcement
			if (headersParts.containsKey("Original-Message-Header"))
				headersParts.put("Original-Message-Header", headersParts.get("Original-Message-Header").toString());

			DataHandler dtHeader = (DataHandler) headersParts.get("header");
			header = IOUtils.toString(dtHeader.getInputStream());

			payload = headersParts.get("payload").toString();

		} catch (Exception e) {
			logger.error("Error parsing multipart message:", e);
			rejectionMessageService.sendRejectionMessage(RejectionMessageType.REJECTION_MESSAGE_COMMON, message);
		}

		multipartMessage = new MultipartMessageBuilder().withHeaderContent(header).withPayloadContent(payload).build();
		headersParts.put("Payload-Content-Type", headersParts.get("payload.org.eclipse.jetty.servlet.contentType"));

		exchange.getOut().setHeaders(headersParts);
		exchange.getOut().setBody(multipartMessage);
	}
}