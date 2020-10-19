package it.eng.idsa.businesslogic.processor.producer;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.http.entity.ContentType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import de.fraunhofer.iais.eis.Message;
import it.eng.idsa.businesslogic.service.MultipartMessageService;
import it.eng.idsa.businesslogic.service.RejectionMessageService;
import it.eng.idsa.businesslogic.util.RejectionMessageType;
import it.eng.idsa.multipart.domain.MultipartMessage;
import it.eng.idsa.multipart.processor.MultipartMessageProcessor;
import it.eng.idsa.multipart.util.MultipartMessageKey;

/**
 * 
 * @author Milan Karajovic and Gabriele De Luca
 *
 */

@Component
public class ProducerParseReceivedDataProcessorBodyBinary implements Processor {

	private static final Logger logger = LogManager.getLogger(ProducerParseReceivedDataProcessorBodyBinary.class);
	
	@Value("${application.isEnabledDapsInteraction}")
	private boolean isEnabledDapsInteraction;

	@Autowired
	private MultipartMessageService multipartMessageService;
	
	@Autowired
	private RejectionMessageService rejectionMessageService;

	@Override
	public void process(Exchange exchange) throws Exception {

		Message message = null;
		Map<String, Object> headesParts = new HashMap<String, Object>();
		String receivedDataBodyBinary = null;
		
		
		// Get from the input "exchange"
		headesParts = exchange.getIn().getHeaders();
		receivedDataBodyBinary = exchange.getIn().getBody(String.class);
		
		

		
		if (receivedDataBodyBinary == null) {			
			logger.error("Body of the received multipart message is null");
			rejectionMessageService.sendRejectionMessage(
					RejectionMessageType.REJECTION_MESSAGE_LOCAL_ISSUES, 
					message);
		}
		
		try {
			MultipartMessage multipartMessage = MultipartMessageProcessor.parseMultipartMessage(receivedDataBodyBinary);
			
			if (!multipartMessage.getHeaderHeader().get(MultipartMessageKey.CONTENT_TYPE.label).equals(MultipartMessageKey.CONTENT_TYPE.label+": "+ContentType.APPLICATION_JSON)) {
				logger.error("Content type of the header must be application/json");
				rejectionMessageService.sendRejectionMessage(
						RejectionMessageType.REJECTION_MESSAGE_LOCAL_ISSUES, 
						message);
			}
			// Create headers parts
			// Put in the header value of the application.property: application.isEnabledDapsInteraction
			headesParts.put("Is-Enabled-Daps-Interaction", isEnabledDapsInteraction);
			headesParts.put("Payload-Content-Type", multipartMessage.getPayloadHeader().get(MultipartMessageKey.CONTENT_TYPE.label));
			
			// Return exchange
			exchange.getOut().setBody(multipartMessage);
			exchange.getOut().setHeaders(headesParts);
			


		} catch (Exception e) {
			logger.error("Error parsing multipart message:", e);
			rejectionMessageService.sendRejectionMessage(
					RejectionMessageType.REJECTION_MESSAGE_LOCAL_ISSUES, 
					message);
		}

	}
	
	

}
