package it.eng.idsa.businesslogic.processor.producer;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.stereotype.Component;;

@Component
public class ProducerProcessRegistrationResponseProcessor implements Processor {

	@Override
	public void process(Exchange exchange) throws Exception {
		String igor = (String) exchange.getIn().getHeader("headerIgor");
//		System.out.println(igor);
	}

	
}
