package it.eng.idsa.businesslogic.processor.producer.registration;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import de.fraunhofer.iais.eis.ids.jsonld.Serializer;
import it.eng.idsa.businesslogic.service.SelfDescriptionService;

@Component
public class ProducerCreateQueryBrokerMessageProcessor implements Processor  {

	@Autowired
	private SelfDescriptionService selfDescriptionService;


	@Override
	public void process(Exchange exchange) throws Exception {
		Map<String, Object> headersParts = new HashMap<String, Object>();
		// Get from the input "exchange"
		Map<String, Object> receivedDataHeader = exchange.getIn().getHeaders();

		headersParts.put("Forward-To", receivedDataHeader.get("Forward-To").toString());

		Map<String, Object> multipartMessageParts = new HashMap<String, Object>();
		String  registrationMessage = geObjectAsString(selfDescriptionService.getConnectorQueryMessage());
		multipartMessageParts.put("header", registrationMessage );
		multipartMessageParts.put("payload", exchange.getIn().getBody(String.class));
		
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
