package it.eng.idsa.businesslogic.processor.producer;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import de.fraunhofer.iais.eis.Message;
import it.eng.idsa.businesslogic.service.MultipartMessageService;
import it.eng.idsa.businesslogic.service.RejectionMessageService;
import it.eng.idsa.businesslogic.util.RejectionMessageType;

public class ProducerParseReceivedDataProcessorHttpHeader implements Processor{
	
	private static final Logger logger = LogManager.getLogger(ProducerParseReceivedDataProcessorHttpHeader.class);

	@Value("${application.isEnabledDapsInteraction}")
	private boolean isEnabledDapsInteraction;
	
	@Autowired
	private MultipartMessageService multipartMessageService;
	
	@Autowired
	private RejectionMessageService rejectionMessageService;

	@Override
	public void process(Exchange exchange) throws Exception {
		
		String contentType;
		String forwardTo;
		String type;
		String issued;
		String issuerConnector;
		String correlationMessage;
		String transferContract;
		String modelVersion;
		String id;
		String header = null;
		String payload = null;
		Message message = null;
		Map<String, Object> headers = new HashMap<String, Object>();
		Map<String, Object> multipartMessageParts = new HashMap<String, Object>();

		// Get from the input "exchange"
		Map<String, Object> receivedDataHeader = exchange.getIn().getHeaders();
		
		try {
			// Create headers parts
			// Put in the header value of the application.property: application.isEnabledDapsInteraction
			headers.put("Is-Enabled-Daps-Interaction", isEnabledDapsInteraction);
			contentType = receivedDataHeader.get("Content-Type").toString();
			headers.put("Content-Type", contentType);
			forwardTo = receivedDataHeader.get("Forward-To").toString();
			headers.put("Forward-To", forwardTo);
			type = receivedDataHeader.get("type").toString();
			headers.put("type", type);
			issued = receivedDataHeader.get("issued").toString();
			headers.put("issued", issued);
			issuerConnector = receivedDataHeader.get("issuerConnector").toString();
			headers.put("issuerConnector", issuerConnector);
			correlationMessage = receivedDataHeader.get("correlationMessage").toString();
			headers.put("correlationMessage", correlationMessage);
			transferContract = receivedDataHeader.get("transferContract").toString();
			headers.put("transferContract", transferContract);
			modelVersion = receivedDataHeader.get("modelVersion").toString();
			headers.put("modelVersion", modelVersion);
			id = receivedDataHeader.get("id").toString();
			headers.put("id", id);

			// Create multipart message parts
			header = receivedDataHeader.get("header").toString();
			multipartMessageParts.put("header", header);
			if(receivedDataHeader.containsKey("payload")) {
				payload = receivedDataHeader.get("payload").toString();
				multipartMessageParts.put("payload", payload);
			}
			message = multipartMessageService.getMessage(multipartMessageParts.get("header"));
			
			// Return exchange
			exchange.getOut().setHeaders(headers);
			exchange.getOut().setBody(multipartMessageParts);

		} catch (Exception e) {			
			logger.error("Error parsing multipart message:" + e);
			rejectionMessageService.sendRejectionMessage(
					RejectionMessageType.REJECTION_MESSAGE_LOCAL_ISSUES, 
					message);
		}		
	}

}
