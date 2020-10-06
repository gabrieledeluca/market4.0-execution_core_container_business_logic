package it.eng.idsa.businesslogic.processor.producer;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.stereotype.Component;;

@Component
public class ProducerProcessRegistrationResponseProcessor implements Processor {

	@Override
	/**
	 * Removes Content-Type from response in order to be able to serialize it correct - workaround
	 */
	public void process(Exchange exchange) throws Exception {
		// TODO workaround until we solve issue with Content-Type issue present in response
		String multipartMessagePartsReceived = exchange.getIn().getBody(String.class);

		String[] lines = multipartMessagePartsReceived.split(System.getProperty("line.separator"));
		for(int i=0;i<lines.length;i++){
		    if(lines[i].startsWith("Content-Type")){
		        lines[i]="";
		    }
		}
		StringBuilder finalStringBuilder= new StringBuilder("");
		for(String s:lines){
		   if(!s.equals("")){
		       finalStringBuilder.append(s).append(System.getProperty("line.separator"));
		    }
		}
		String finalString = finalStringBuilder.toString();
		exchange.getOut().setBody(finalString);
	}

	
}
