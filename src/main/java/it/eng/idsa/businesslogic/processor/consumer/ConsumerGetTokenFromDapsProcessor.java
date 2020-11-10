package it.eng.idsa.businesslogic.processor.consumer;

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import de.fraunhofer.iais.eis.Message;
import it.eng.idsa.businesslogic.service.DapsService;
import it.eng.idsa.businesslogic.service.MultipartMessageService;
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
public class ConsumerGetTokenFromDapsProcessor implements Processor {

	private static final Logger logger = LogManager.getLogger(ConsumerGetTokenFromDapsProcessor.class);

	@Value("${application.eccHttpSendRouter}")
	private String eccHttpSendRouter;

	@Autowired
	private MultipartMessageService multipartMessageService;

	@Autowired
	private RejectionMessageService rejectionMessageService;

	@Autowired
	private DapsService dapsService;

	@Override
	public void process(Exchange exchange) throws Exception {

		Map<String, Object> headersParts = exchange.getIn().getHeaders();
		MultipartMessage multipartMessage = exchange.getIn().getBody(MultipartMessage.class);
		Message message = multipartMessage.getHeaderContent();
		logger.info("message id=" + message.getId());

		// Get the Token from the DAPS
		String token = null;
		try {
			token = dapsService.getJwtToken();
		} catch (Exception e) {
			logger.error("Can not get the token from the DAPS server ", e);
			rejectionMessageService.sendRejectionMessage(RejectionMessageType.REJECTION_TOKEN_LOCAL_ISSUES, message);

		}

		if (token == null) {
			logger.error("Can not get the token from the DAPS server");
			rejectionMessageService.sendRejectionMessage(RejectionMessageType.REJECTION_COMMUNICATION_LOCAL_ISSUES,
					message);
		}

		if (token.isEmpty()) {
			logger.error("The token from the DAPS server is empty");
			rejectionMessageService.sendRejectionMessage(RejectionMessageType.REJECTION_TOKEN_LOCAL_ISSUES, message);
		}

		logger.info("token=" + token);
		if (eccHttpSendRouter.equals("http-header")) {
			multipartMessage = new MultipartMessageBuilder().withHttpHeader(multipartMessage.getHttpHeaders())
					.withHeaderHeader(multipartMessage.getHeaderHeader())
					.withHeaderContent(multipartMessage.getHeaderContent())
					.withPayloadHeader(multipartMessage.getPayloadHeader())
					.withPayloadContent(multipartMessage.getPayloadContent()).withToken(token).build();
		} else {
			String messageStringWithToken = multipartMessageService.addToken(message, token);
			logger.info("messageStringWithToken=" + messageStringWithToken);

			multipartMessage = new MultipartMessageBuilder().withHttpHeader(multipartMessage.getHttpHeaders())
					.withHeaderHeader(multipartMessage.getHeaderHeader()).withHeaderContent(messageStringWithToken)
					.withPayloadHeader(multipartMessage.getPayloadHeader())
					.withPayloadContent(multipartMessage.getPayloadContent()).withToken(token).build();

		}

		// Return exchange
		exchange.getOut().setBody(multipartMessage);
		exchange.getOut().setHeaders(headersParts);
	}
}
