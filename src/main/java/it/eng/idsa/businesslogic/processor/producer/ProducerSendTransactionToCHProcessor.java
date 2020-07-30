package it.eng.idsa.businesslogic.processor.producer;

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import de.fraunhofer.iais.eis.Message;
import it.eng.idsa.businesslogic.service.ClearingHouseService;
import it.eng.idsa.businesslogic.service.MultipartMessageService;

/**
 * 
 * @author Milan Karajovic and Gabriele De Luca
 *
 */

@Component
public class ProducerSendTransactionToCHProcessor implements Processor {

	private static final Logger logger = LogManager.getLogger(ProducerSendTransactionToCHProcessor.class);
	
	@Autowired
	private MultipartMessageService multipartMessageService;
	
	@Autowired
	private ClearingHouseService clearingHouseService;
	
	@Override
	public void process(Exchange exchange) throws Exception {
		
		// In the multipartMessageBody is original header and payload
		String multipartMessageBody = exchange.getIn().getHeader("multipartMessageBody").toString();
		
		// Prepare data for CH
		String header = multipartMessageService.getHeaderContentString(multipartMessageBody);
		String payload = multipartMessageService.getPayloadContent(multipartMessageBody);
		Message message = multipartMessageService.getMessage(header);
		// Send data to CH
		//clearingHouseService.registerTransaction(message, payload);
		logger.info("Successfully wrote down in the Clearing House");

		// clear from Headers multipartMessageBody (it is not unusable for the Open Data App)
		Map<String, Object> headers = exchange.getIn().getHeaders();
		headers.remove("multipartMessageBody");
		
		exchange.getOut().setHeaders(headers);
		exchange.getOut().setBody(exchange.getIn().getBody());
	}

}
