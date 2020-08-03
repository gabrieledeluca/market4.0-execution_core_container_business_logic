package it.eng.idsa.businesslogic.processor.consumer;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.util.json.JsonObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class ConsumerHttpHeaderProcessor implements Processor {

	@Value("${application.eccHttpSendRouter}")
	private String eccHttpSendRouter;
	
	@Value("${application.isEnabledDapsInteraction}")
	private boolean isEnabledDapsInteraction;

	@Override
	public void process(Exchange exchange) throws Exception {
		
		// if the message is not sent over http-header this processor is skipped
		
		if (eccHttpSendRouter.equals("http-header")) {

			String headerFromHeaders = getHeaderFromHeadersMap(exchange.getIn().getHeaders());
			exchange.getIn().getHeaders().put("header", headerFromHeaders);

		}
		
		exchange.getOut().setHeaders(exchange.getIn().getHeaders());
		exchange.getOut().setBody(exchange.getIn().getBody());

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

		if (isEnabledDapsInteraction) {
			
			Map<String, Object> tokenAsMap = new HashMap<String, Object>();
			Map<String, String> tokenFormatAsMap = new HashMap<String, String>();
			tokenAsMap.put("@type", headers.get("IDS-SecurityToken-Type").toString());
			tokenAsMap.put("@id", headers.get("IDS-SecurityToken-Id").toString());
			tokenFormatAsMap.put("@id", headers.get("IDS-SecurityToken-TokenFormat").toString());
			tokenAsMap.put("tokenFormat", tokenFormatAsMap);
			tokenAsMap.put("tokenValue", headers.get("IDS-SecurityToken-TokenValue").toString());

			JsonObject jsonHeader = new JsonObject(headerAsMap);
			jsonHeader.put("authorizationToken", tokenAsMap);

			String header = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(jsonHeader);

			return header;

		}

		String header = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(headerAsMap);

		return header;

	}

}
