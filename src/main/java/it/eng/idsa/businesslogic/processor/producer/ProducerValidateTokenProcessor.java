package it.eng.idsa.businesslogic.processor.producer;

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
public class ProducerValidateTokenProcessor implements Processor {

	private static final Logger logger = LogManager.getLogger(ProducerValidateTokenProcessor.class);
	
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
		
		Map<String, Object> headersParts = exchange.getIn().getHeaders();
		MultipartMessage multipartMessage = exchange.getIn().getBody(MultipartMessage.class);
		
		String token = multipartMessage.getToken();
		Message message = multipartMessage.getHeaderContent();
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
		exchange.getOut().setHeaders(headersParts);
		if (eccHttpSendRouter.equals("http-header")) {
			exchange.getOut().setBody(multipartMessage);
		}else {
			// not used
//			multipartMessageParts.put("isTokenValid", isTokenValid);
			exchange.getOut().setBody(multipartMessage);
		}
	}

}
