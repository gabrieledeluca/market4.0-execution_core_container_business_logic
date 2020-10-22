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
import it.eng.idsa.multipart.domain.MultipartMessage;

/**
 * 
 * @author Milan Karajovic and Gabriele De Luca
 *
 */

@Component
public class ConsumerValidateTokenProcessor implements Processor {
	
	private static final Logger logger = LogManager.getLogger(ConsumerValidateTokenProcessor.class);
	
	@Value("${application.eccHttpSendRouter}")
	private String eccHttpSendRouter;
	
	@Autowired
	DapsService dapsService;
	
	@Autowired
	private MultipartMessageService multipartMessageService;
	
	@Autowired
	private RejectionMessageService rejectionMessageService;

	@Override
	public void process(Exchange exchange) throws Exception {
		
		Message message = null;
		
		MultipartMessage multipartMessage = exchange.getIn().getBody(MultipartMessage.class);
		
		Map<String, Object> multipartMessageParts = null;
		
		String token = null;
		if (eccHttpSendRouter.equals("http-header")) {
			token = multipartMessage.getHttpHeaders().get("IDS-SecurityToken-TokenValue");
		}else {
			// Get "multipartMessageParts" from the input "exchange"
//			multipartMessageParts = exchange.getIn().getBody(HashMap.class);
//			message = multipartMessageService.getMessage(multipartMessageParts.get("header"));
			// Get "token" from the input "multipartMessageParts"
			token = multipartMessageService.getToken(multipartMessage.getHeaderContent());
			
		}
		logger.info("token: ", token);
		
		// Check is "token" valid
		boolean isTokenValid = dapsService.validateToken(token);
		
		if(isTokenValid==false) {			
			logger.error("Token is invalid");
			rejectionMessageService.sendRejectionMessage(
					RejectionMessageType.REJECTION_TOKEN, 
					message);
		}
		
		logger.info("is token valid: "+isTokenValid);
		exchange.getOut().setHeaders(exchange.getIn().getHeaders());
		if (eccHttpSendRouter.equals("http-header")) {
			exchange.getOut().setBody(exchange.getIn().getBody());
		}else {
//			multipartMessageParts.put("isTokenValid", isTokenValid);
			exchange.getOut().setBody(multipartMessageParts);
		}
		exchange.getOut().setBody(multipartMessage);
	}

}
