package it.eng.idsa.businesslogic.processor.consumer;

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
import it.eng.idsa.businesslogic.service.MultipartMessageService;
import it.eng.idsa.businesslogic.service.RejectionMessageService;
import it.eng.idsa.businesslogic.util.RejectionMessageType;

/**
 * 
 * @author Milan Karajovic and Gabriele De Luca
 *
 */

@Component
public class ConsumerMultiPartMessageProcessor implements Processor {
	
	private static final Logger logger = LogManager.getLogger(ConsumerMultiPartMessageProcessor.class);
	
	@Value("${application.isEnabledDapsInteraction}")
	private boolean isEnabledDapsInteraction;

	@Value("${application.dataApp.websocket.isEnabled}")
	private boolean isEnabledDataAppWebSocket;

	@Value("${application.isEnabledClearingHouse}")
	private boolean isEnabledClearingHouse;
	
	@Value("${application.eccHttpSendRouter}")
	private String eccHttpSendRouter;

	@Autowired
	private MultipartMessageService multipartMessageService;
	
	@Autowired
	private RejectionMessageService rejectionMessageService;
	
	@Override
	public void process(Exchange exchange) throws Exception {
		
		String header;
		String payload;
		Message message=null;
		Map<String, Object> headesParts = new HashMap<String, Object>();
		Map<String, Object> multipartMessageParts = new HashMap<String, Object>();
		
		if (eccHttpSendRouter.equals("http-header")) {
			String headerFromHeaders = getHeaderFromHeadersMap(exchange.getIn().getHeaders());
			System.out.println(headerFromHeaders);
			System.out.println(exchange.getIn().getHeader("payload").toString());
			exchange.getIn().getHeaders().put("header", headerFromHeaders);
		}
		
		if(!exchange.getIn().getHeaders().containsKey("header"))
		{
			logger.error("Multipart message header is null");
			rejectionMessageService.sendRejectionMessage(
					RejectionMessageType.REJECTION_MESSAGE_COMMON, 
					message);
		}
		try {
			
			// Create headers parts
			// Put in the header value of the application.property: application.isEnabledDapsInteraction
			headesParts.put("Is-Enabled-Daps-Interaction", isEnabledDapsInteraction);
			headesParts.put("Is-Enabled-Clearing-House", isEnabledClearingHouse);
			headesParts.put("Is-Enabled-DataApp-WebSocket", isEnabledDataAppWebSocket);

			if(exchange.getIn().getHeaders().containsKey("payload")) {
				payload=exchange.getIn().getHeader("payload").toString();
				if(payload.equals("RejectionMessage")) {
					// Create multipart message for the RejectionMessage
					header= multipartMessageService.getHeaderContentString(exchange.getIn().getHeader("header").toString());
					multipartMessageParts.put("header", header);
				} else {
					// Create multipart message with payload
					header=exchange.getIn().getHeader("header").toString();
					multipartMessageParts.put("header", header);
					payload=exchange.getIn().getHeader("payload").toString();
					multipartMessageParts.put("payload", payload);
					message=multipartMessageService.getMessage(multipartMessageParts.get("header"));
				}
			}else {
				// Create multipart message without payload
				header=exchange.getIn().getHeader("header").toString();
				multipartMessageParts.put("header", header);
				message=multipartMessageService.getMessage(multipartMessageParts.get("header"));
			}

			// Return exchange
			exchange.getOut().setHeaders(headesParts);
			exchange.getOut().setBody(multipartMessageParts);
			
		} catch (Exception e) {
			logger.error("Error parsing multipart message:" + e);
			rejectionMessageService.sendRejectionMessage(
					RejectionMessageType.REJECTION_MESSAGE_COMMON, 
					message);
		}
	}

	private String getHeaderFromHeadersMap(Map<String, Object> headers) {
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