package it.eng.idsa.businesslogic.processor.producer;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.fasterxml.jackson.core.JsonProcessingException;

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
		Map<String, Object> headersParts = new HashMap<String, Object>();
		Map<String, Object> multipartMessageParts = new HashMap<String, Object>();
		String receivedDataBodyBinary = null;

		// Get from the input "exchange"
		Map<String, Object> receivedDataHeader = exchange.getIn().getHeaders();
		receivedDataBodyBinary = exchange.getIn().getBody(String.class);
		if (receivedDataBodyBinary == null) {
			logger.error("Body of the received multipart message is null");
			rejectionMessageService.sendRejectionMessage(RejectionMessageType.REJECTION_MESSAGE_LOCAL_ISSUES, message);

		}
		try {
			// Create headers parts
			// Put in the header value of the application.property: application.isEnabledDapsInteraction
			headersParts.put("Is-Enabled-Daps-Interaction", isEnabledDapsInteraction);
			contentType = receivedDataHeader.get("Content-Type").toString();
			headersParts.put("Content-Type", contentType);
			forwardTo = receivedDataHeader.get("Forward-To").toString();
			headersParts.put("Forward-To", forwardTo);

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
//		try {
			// Create headers parts
			// Put in the header value of the application.property: application.isEnabledDapsInteraction
//			headers.put("Is-Enabled-Daps-Interaction", isEnabledDapsInteraction);
//			contentType = receivedDataHeader.get("Content-Type").toString();
//			headers.put("Content-Type", contentType);
//			forwardTo = receivedDataHeader.get("Forward-To").toString();
//			headers.put("Forward-To", forwardTo);
//			type = receivedDataHeader.get("type").toString();
//			headers.put("type", type);
//			issued = receivedDataHeader.get("issued").toString();
//			headers.put("issued", issued);
//			issuerConnector = receivedDataHeader.get("issuerConnector").toString();
//			headers.put("issuerConnector", issuerConnector);
//			correlationMessage = receivedDataHeader.get("correlationMessage").toString();
//			headers.put("correlationMessage", correlationMessage);
//			transferContract = receivedDataHeader.get("transferContract").toString();
//			headers.put("transferContract", transferContract);
//			modelVersion = receivedDataHeader.get("modelVersion").toString();
//			headers.put("modelVersion", modelVersion);
//			id = receivedDataHeader.get("id").toString();
//			headers.put("id", id);

			
			
			// Return exchange
//			exchange.getOut().setHeaders(headers);

//		} catch (Exception e) {			
//			logger.error("Error parsing multipart message:" + e);
//			rejectionMessageService.sendRejectionMessage(
//					RejectionMessageType.REJECTION_MESSAGE_LOCAL_ISSUES, 
//					message);
//		}		
	}

private String getHeaderFromHeadersMap(Map<String, Object> headers) throws JsonProcessingException {
		
		StringBuffer sb = new StringBuffer();

		sb.append("{" + System.lineSeparator());
		sb.append(appendKeyAndValue("type", headers));
		sb.append(appendKeyAndValue("issued", headers));
		sb.append(appendKeyAndValue("issuerConnector", headers));
		sb.append(appendKeyAndValue("correlationMessage", headers));
		sb.append(appendKeyAndValue("transferContract", headers));
		sb.append(appendKeyAndValue("modelVersion", headers));
		sb.append(appendKeyAndValue("id", headers));
		sb.append("}");

		return sb.toString();
		
		
	}

	private String appendKeyAndValue(String key, Map<String, Object> headersMap) {
		StringBuffer sb = new StringBuffer();
		String lineStart = "  \"";
		String lineEnd = "\",";
		String spaceBetweenKeyAndValue = "\" : \"";
		String value = headersMap.get(key).toString();
		
		sb.append(lineStart);
		if (key.equals("type") || key.equals("id")) {
			sb.append("@");
		}
		sb.append(key);
		sb.append(spaceBetweenKeyAndValue);
		sb.append(value);
		if (!key.equals("id")) {
			sb.append(lineEnd);
		}else {
			sb.append("\"");
		}
		sb.append(System.lineSeparator());
		
		return sb.toString();
	}
}
