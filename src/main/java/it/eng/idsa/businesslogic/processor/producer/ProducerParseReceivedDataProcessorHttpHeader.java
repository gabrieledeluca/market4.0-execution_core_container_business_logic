package it.eng.idsa.businesslogic.processor.producer;

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
import it.eng.idsa.businesslogic.service.HttpHeaderService;
import it.eng.idsa.businesslogic.service.MultipartMessageService;
import it.eng.idsa.businesslogic.service.RejectionMessageService;
import it.eng.idsa.businesslogic.util.RejectionMessageType;

@Component
public class ProducerParseReceivedDataProcessorHttpHeader implements Processor{
	
	private static final Logger logger = LogManager.getLogger(ProducerParseReceivedDataProcessorHttpHeader.class);

	@Value("${application.isEnabledDapsInteraction}")
	private boolean isEnabledDapsInteraction;
	
	@Autowired
	private MultipartMessageService multipartMessageService;
	
	@Autowired
	private RejectionMessageService rejectionMessageService;
	
	@Autowired
	private HttpHeaderService headerService;

	@Override
	public void process(Exchange exchange) throws Exception {
		
		Message message = null;
		Map<String, Object> headersParts = new HashMap<String, Object>();
		String payload = null;

		// Get from the input "exchange"
		headersParts = exchange.getIn().getHeaders();
		payload = exchange.getIn().getBody(String.class);
		if (payload == null) {
			logger.error("Body of the received multipart message is null");
			rejectionMessageService.sendRejectionMessage(RejectionMessageType.REJECTION_MESSAGE_LOCAL_ISSUES, message);

		}
		try {
			// Put in the header value of the application.property: application.isEnabledDapsInteraction
			headersParts.put("Is-Enabled-Daps-Interaction", isEnabledDapsInteraction);

			// Create multipart message parts
			
//			header = headerService.getHeaderMessagePartFromHttpHeadersWithoutToken(headersParts);
//			multipartMessageParts.put("header", header);
			//payload = multipartMessageService.getPayloadContent(receivedDataBodyBinary);
//			message = multipartMessageService.getMessage(multipartMessageParts.get("header"));
			
			// Return exchange
			exchange.getOut().setHeaders(headersParts);
			exchange.getOut().setBody(payload);

		} catch (Exception e) {
			logger.error("Error parsing multipart message:" + e);
			rejectionMessageService.sendRejectionMessage(
					RejectionMessageType.REJECTION_MESSAGE_LOCAL_ISSUES, 
					message);
		}
	
	}

}