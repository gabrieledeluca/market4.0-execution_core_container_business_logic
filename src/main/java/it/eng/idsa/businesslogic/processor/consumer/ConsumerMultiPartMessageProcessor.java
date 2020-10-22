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
import it.eng.idsa.businesslogic.service.HttpHeaderService;
import it.eng.idsa.businesslogic.service.MultipartMessageService;
import it.eng.idsa.businesslogic.service.ReceiveDataFromBusinessLogicService;
import it.eng.idsa.businesslogic.service.RejectionMessageService;
import it.eng.idsa.businesslogic.util.RejectionMessageType;
import it.eng.idsa.multipart.builder.MultipartMessageBuilder;
import it.eng.idsa.multipart.domain.MultipartMessage;
import it.eng.idsa.multipart.util.MultipartMessageKey;

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
	private HttpHeaderService headerService;

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
		Map<String, String> headerContentHeaders = null;

		headersParts.put("Is-Enabled-Daps-Interaction", isEnabledDapsInteraction);
		headersParts.put("Is-Enabled-Clearing-House", isEnabledClearingHouse);
		headersParts.put("Is-Enabled-DataApp-WebSocket", isEnabledDataAppWebSocket);

		if (eccHttpSendRouter.equals("http-header")) {

			try {

				// header content headers will be set in he http-headers of the MultipartMessage
				headerContentHeaders = headerService.getHeaderContentHeaders(headersParts);

			} catch (Exception e) {
				logger.error("Mandatory headers of the multipart message header part are missing or null:", e);
				rejectionMessageService.sendRejectionMessage(RejectionMessageType.REJECTION_MESSAGE_COMMON, message);
			}

			if (exchange.getIn().getBody() != null) {
				payload = exchange.getIn().getBody(String.class);
			} else {
				logger.error("Payload is null");
				rejectionMessageService.sendRejectionMessage(RejectionMessageType.REJECTION_MESSAGE_COMMON, message);
			}

			multipartMessage = new MultipartMessageBuilder().withHttpHeader(headerContentHeaders)
					.withPayloadContent(payload).build();

			headersParts.put("Payload-Content-Type", headersParts.get(MultipartMessageKey.CONTENT_TYPE.label));

		} else {

			if (!headersParts.containsKey("header")) {
				logger.error("Multipart message header is missing");
				rejectionMessageService.sendRejectionMessage(RejectionMessageType.REJECTION_MESSAGE_COMMON, message);
			}

			if (!headersParts.containsKey("payload")) {
				logger.error("Multipart message payload is missing");
				rejectionMessageService.sendRejectionMessage(RejectionMessageType.REJECTION_MESSAGE_COMMON, message);
			}

			if (headersParts.get("header") == null) {
				logger.error("Multipart message header is null");
				rejectionMessageService.sendRejectionMessage(RejectionMessageType.REJECTION_MESSAGE_COMMON, message);
			}

			if (headersParts.get("payload") == null) {
				logger.error("Multipart message payload is null");
				rejectionMessageService.sendRejectionMessage(RejectionMessageType.REJECTION_MESSAGE_COMMON, message);
			}

			try {

				// Save the original message header for Usage Control Enforcement
				if (headersParts.containsKey("Original-Message-Header"))
					headersParts.put("Original-Message-Header", headersParts.get("Original-Message-Header").toString());

				if (headersParts.get("header") instanceof String) {
					header = headersParts.get("header").toString();
				}else {
					DataHandler dtHeader = (DataHandler) headersParts.get("header");
					header = IOUtils.toString(dtHeader.getInputStream());
				}
				payload = headersParts.get("payload").toString();

				multipartMessage = new MultipartMessageBuilder().withHeaderContent(header).withPayloadContent(payload)
						.build();

				headersParts.put("Payload-Content-Type",
						headersParts.get("payload.org.eclipse.jetty.servlet.contentType"));
			} catch (Exception e) {
				logger.error("Error parsing multipart message:", e);
				rejectionMessageService.sendRejectionMessage(RejectionMessageType.REJECTION_MESSAGE_COMMON, message);
			}

		}

		exchange.getOut().setHeaders(headersParts);
		exchange.getOut().setBody(multipartMessage);
	}
}