package it.eng.idsa.businesslogic.processor.consumer;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import it.eng.idsa.businesslogic.service.HttpHeaderService;

@Component
public class ConsumerHttpHeaderProcessor implements Processor {

	@Value("${application.eccHttpSendRouter}")
	private String eccHttpSendRouter;
	
	@Value("${application.isEnabledDapsInteraction}")
	private boolean isEnabledDapsInteraction;
	
	@Value("${application.openDataAppReceiverRouter}")
	private String openDataAppReceiverRouter;
	
	@Autowired
	private HttpHeaderService headerService;

	@Override
	public void process(Exchange exchange) throws Exception {
		
		// if the message is not sent over http-header this processor is skipped
		
//		if (eccHttpSendRouter.equals("http-header") && !openDataAppReceiverRouter.equals("http-header")) {
//			String headerFromHeaders = new String();
//			if (isEnabledDapsInteraction) {
//				headerFromHeaders = headerService.getHeaderMessagePartFromHttpHeadersWithToken(exchange.getIn().getHeaders());
//			}else {
//				headerFromHeaders = headerService.getHeaderMessagePartFromHttpHeadersWithoutToken(exchange.getIn().getHeaders());
//			}
//
//			exchange.getIn().getHeaders().put("header", headerFromHeaders);
//
//		}
		
		exchange.getOut().setHeaders(exchange.getIn().getHeaders());
		exchange.getOut().setBody(exchange.getIn().getBody());

	}

}
