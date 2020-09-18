package it.eng.idsa.businesslogic.processor.producer;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.stereotype.Component;;

@Component
public class ProducerProcessRegistrationResponseProcessor implements Processor {

	@Override
	public void process(Exchange exchange) throws Exception {
		Map<String, Object> headesParts = exchange.getIn().getHeaders();
		Map<String, Object> multipartMessagePartsReceived = exchange.getIn().getBody(HashMap.class);

	}

	
}
