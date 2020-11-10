package it.eng.idsa.businesslogic.processor.producer;

import java.nio.charset.StandardCharsets;
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
public class ProducerParseReceivedResponseMessage implements Processor {

	private static final Logger logger = LogManager.getLogger(ProducerParseReceivedResponseMessage.class);

	@Autowired
	private RejectionMessageService rejectionMessageService;

	@Autowired
	private HttpHeaderService headerService;

	@Autowired
	private MultipartMessageService multipartMessageService;

	@Value("${application.eccHttpSendRouter}")
	private String eccHttpSendRouter;

	@Value("${application.isEnabledDapsInteraction}")
	private boolean isEnabledDapsInteraction;

	@Override
	public void process(Exchange exchange) throws Exception {

		String header = null;
		String payload = null;
		Map<String, Object> headersParts = exchange.getIn().getHeaders();
		Message message = null;
		MultipartMessage multipartMessage = null;
		String token = null;

		if (eccHttpSendRouter.equals("http-header")) {
			payload = exchange.getIn().getBody(String.class);
			header = headerService.getHeaderMessagePartFromHttpHeadersWithoutToken(headersParts);
			headersParts.put("Payload-Content-Type", headersParts.get(MultipartMessageKey.CONTENT_TYPE.label));

			if (headersParts.get("IDS-SecurityToken-TokenValue") != null) {
				token = headersParts.get("IDS-SecurityToken-TokenValue").toString();
				headersParts.remove("IDS-SecurityToken-TokenValue");
			}
			multipartMessage = new MultipartMessageBuilder()
					.withHeaderContent(header)
					.withPayloadContent(payload)
					.withToken(token).build();

		} else {
			if (!headersParts.containsKey("header")) {
				logger.error("Multipart message header is missing");
				rejectionMessageService.sendRejectionMessage(RejectionMessageType.REJECTION_MESSAGE_COMMON, message);
			}


			if (headersParts.get("header") == null) {
				logger.error("Multipart message header is null");
				rejectionMessageService.sendRejectionMessage(RejectionMessageType.REJECTION_MESSAGE_COMMON, message);
			}

			try {

				// Save the original message header for Usage Control Enforcement
				if (headersParts.containsKey("Original-Message-Header"))
					headersParts.put("Original-Message-Header", headersParts.get("Original-Message-Header").toString());

				if (headersParts.get("header") instanceof String) {
					header = headersParts.get("header").toString();
				} else {
					DataHandler dtHeader = (DataHandler) headersParts.get("header");
					header = IOUtils.toString(dtHeader.getInputStream(), StandardCharsets.UTF_8);
				}
				message = multipartMessageService.getMessage(header);
				if(headersParts.get("payload")!=null) {
					payload = headersParts.get("payload").toString();
				}
				
				if (isEnabledDapsInteraction) {
					token = multipartMessageService.getToken(message);
				}
				multipartMessage = new MultipartMessageBuilder().withHeaderContent(header).withPayloadContent(payload)
						.withToken(token).build();
			} catch (Exception e) {
				logger.error("Error parsing multipart message:", e);
				rejectionMessageService.sendRejectionMessage(RejectionMessageType.REJECTION_MESSAGE_COMMON, message);
			}
		}
		exchange.getOut().setHeaders(headersParts);
		exchange.getOut().setBody(multipartMessage);
	}
}
