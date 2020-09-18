package it.eng.idsa.businesslogic.processor.producer;

import java.io.IOException;
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
import de.fraunhofer.iais.eis.ids.jsonld.Serializer;
import it.eng.idsa.businesslogic.service.SelfDescriptionService;

@Component
public class ProducerCreateRegistrationMessageProcessor implements Processor {

	private static final Logger logger = LogManager.getLogger(ProducerSendDataToBusinessLogicProcessor.class);

	@Value("${application.isEnabledDapsInteraction}")
	private boolean isEnabledDapsInteraction;

	@Autowired
	private SelfDescriptionService selfDescriptionService;

	@Override
	public void process(Exchange exchange) throws Exception {
		Map<String, Object> headersParts = new HashMap<String, Object>();
		// Get from the input "exchange"
		Map<String, Object> receivedDataHeader = exchange.getIn().getHeaders();

//		headersParts.put("Is-Enabled-Daps-Interaction", isEnabledDapsInteraction);
		headersParts.put("Forward-To", receivedDataHeader.get("Forward-To").toString());
//		headersParts.put("selfDescriptionProcess", true);

		Map<String, Object> multipartMessageParts = new HashMap<String, Object>();

		String connector = selfDescriptionService.getConnectorAsString();
		Message connectorAvailable = selfDescriptionService.getConnectorAvailbilityMessage();
//		Map<String, String> headerMap = new HashMap<>();
//		headerMap.put("Content-Type", "application/json; charset=utf-8");

//		MultipartMessage multipartMessage = new MultipartMessageBuilder()
////				.withHeaderHeader(headerMap)
//    			.withHeaderContent(geObjectAsString(connectorAvailable))
////    			.withPayloadHeader(headerMap)
//				.withPayloadContent(connector)
//				.build();

//		String multipartMessageString = MultipartMessageProcessor.multipartMessagetoString(multipartMessage);
//		logger.info("Connector message:");
//		logger.info(multipartMessageString);
//		
//		multipartMessage.getHeaderHeader();
//		multipartMessage.getPayloadHeader();

//		header = multipartMessageService.getHeaderContentString(geObjectAsString(connectorAvailable));
		multipartMessageParts.put("header", geObjectAsString(connectorAvailable));
		if (connector != null) {
			multipartMessageParts.put("payload", connector);
		}
		// Return exchange
		exchange.getOut().setHeaders(headersParts);
		exchange.getOut().setBody(multipartMessageParts);

	}

	private String geObjectAsString(Object toSerialize) {
		final Serializer serializer = new Serializer();
		String result = null;
		try {
			result = serializer.serialize(toSerialize);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return result;
	}
}
