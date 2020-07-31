package it.eng.idsa.businesslogic.processor.consumer;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.util.json.JsonObject;
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
		
		if (eccHttpSendRouter.equals("http-header") && exchange.getIn().getHeader("headerBindingDone")!= null) {
			if (exchange.getIn().getHeader("headerBindingDone").toString().equals("false")) {
				String headerFromHeaders = getHeaderFromHeadersMap(exchange.getIn().getHeaders());
				exchange.getIn().getHeaders().put("header", headerFromHeaders);
				exchange.getIn().getHeaders().replace("headerBindingDone", true);
			}
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


	private String getHeaderFromHeadersMap(Map<String, Object> headers) throws JsonProcessingException {
		
		Map<String, String> headerAsMap = new HashMap<String, String>();
		headerAsMap.put("@type", headers.get("IDS-Messagetype").toString());
		headerAsMap.put("@id", headers.get("IDS-Id").toString());
		headerAsMap.put("issued", headers.get("IDS-Issued").toString());
		headerAsMap.put("modelVersion", headers.get("IDS-ModelVersion").toString());
		headerAsMap.put("issuerConnector", headers.get("IDS-IssuerConnector").toString());  
		headerAsMap.put("transferContract", headers.get("IDS-TransferContract").toString()); 
		headerAsMap.put("correlationMessage", headers.get("IDS-CorrelationMessage").toString());
		
		String header = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(headerAsMap);
		
		if (headers.containsKey("IDS-SecurityToken-Type")) {
			Map<String, Object> tokenAsMap = new HashMap<String, Object>();
			Map<String, String> tokenFormatAsMap = new HashMap<String, String>();
			tokenAsMap.put("@type", headers.get("IDS-SecurityToken-Type").toString());
			tokenAsMap.put("@id",headers.get("IDS-SecurityToken-Id").toString());
			tokenFormatAsMap.put("@id", headers.get("IDS-SecurityToken-TokenFormat").toString());
			tokenAsMap.put("tokenFormat", tokenFormatAsMap);
			tokenAsMap.put("tokenValue", headers.get("IDS-SecurityToken-TokenValue").toString());
			
			String token = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(headerAsMap);
			
			System.out.println(token);
			
			JsonObject jsonHeader = new JsonObject(headerAsMap);
			jsonHeader.put("authorizationToken", tokenAsMap);
			
			header = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(headerAsMap);
			
		}                                
		
		return header;
		                                                                                             
		
		
	}

}