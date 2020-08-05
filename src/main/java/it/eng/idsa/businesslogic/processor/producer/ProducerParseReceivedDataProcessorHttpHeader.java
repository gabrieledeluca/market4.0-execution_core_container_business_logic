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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.fraunhofer.iais.eis.Message;
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

	@Override
	public void process(Exchange exchange) throws Exception {
		
		String header = null;
		String payload = null;
		Message message = null;
		Map<String, Object> headersParts = new HashMap<String, Object>();
		Map<String, Object> multipartMessageParts = new HashMap<String, Object>();
		String receivedDataBodyBinary = null;

		// Get from the input "exchange"
		headersParts = exchange.getIn().getHeaders();
		receivedDataBodyBinary = exchange.getIn().getBody(String.class);
		if (receivedDataBodyBinary == null) {
			logger.error("Body of the received multipart message is null");
			rejectionMessageService.sendRejectionMessage(RejectionMessageType.REJECTION_MESSAGE_LOCAL_ISSUES, message);

		}
		try {
			// Put in the header value of the application.property: application.isEnabledDapsInteraction
			headersParts.put("Is-Enabled-Daps-Interaction", isEnabledDapsInteraction);

			// Create multipart message parts
			
			String headerFromHeaders = getHeaderFromHeadersMap(headersParts);
			header = multipartMessageService.getHeaderContentString(headerFromHeaders);
			multipartMessageParts.put("header", header);
			payload = multipartMessageService.getPayloadContent(receivedDataBodyBinary);
			if(payload!=null) {
				multipartMessageParts.put("payload", payload);
			}
			message = multipartMessageService.getMessage(multipartMessageParts.get("header"));
			
			// Return exchange
			exchange.getOut().setHeaders(headersParts);
			exchange.getOut().setBody(multipartMessageParts);

		} catch (Exception e) {
			logger.error("Error parsing multipart message:" + e);
			rejectionMessageService.sendRejectionMessage(
					RejectionMessageType.REJECTION_MESSAGE_LOCAL_ISSUES, 
					message);
		}
	
	}

	private String getHeaderFromHeadersMap(Map<String, Object> headers) throws JsonProcessingException {

		Map<String, String> headerAsMap = new HashMap<String, String>();
		headerAsMap.put("@type", headers.get("IDS-Messagetype").toString());
		headerAsMap.put("@id", headers.get("IDS-Id").toString());
		headerAsMap.put("issued", headers.get("IDS-Issued").toString());
		headerAsMap.put("modelVersion", headers.get("IDS-ModelVersion").toString());
		headerAsMap.put("issuerConnector", headers.get("IDS-IssuerConnector").toString());
		headerAsMap.put("transferContract", headers.get("IDS-TransferContract").toString());
		headerAsMap.put("correlationMessage", headers.get("IDS-CorrelationMessage").toString());

		removeMessageHeaders(headers);

		String header = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(headerAsMap);

		return header;

	}

	private void removeMessageHeaders(Map<String, Object> headers) {
		headers.remove("IDS-Messagetype");                     
		headers.remove("IDS-Id");                                
	    headers.remove("IDS-Issued");                         
        headers.remove("IDS-ModelVersion");             
        headers.remove("IDS-IssuerConnector");       
        headers.remove("IDS-TransferContract");     
        headers.remove("IDS-CorrelationMessage"); 
	}
}