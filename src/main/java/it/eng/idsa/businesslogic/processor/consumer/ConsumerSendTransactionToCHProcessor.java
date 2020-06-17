package it.eng.idsa.businesslogic.processor.consumer;

import de.fraunhofer.iais.eis.Message;
import it.eng.idsa.businesslogic.processor.producer.ProducerSendTransactionToCHProcessor;
import it.eng.idsa.businesslogic.service.MultipartMessageService;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import it.eng.idsa.businesslogic.service.ClearingHouseService;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Milan Karajovic and Gabriele De Luca
 */

@Component
public class ConsumerSendTransactionToCHProcessor implements Processor {
    private static final Logger logger = LogManager.getLogger(ConsumerSendTransactionToCHProcessor.class);

    @Autowired
    private ClearingHouseService clearingHouseService;

    @Autowired
    private MultipartMessageService multipartMessageService;

    @Override
    public void process(Exchange exchange) throws Exception {

        // Get "multipartMessageString" from the input "exchange"
        Map<String, Object> multipartMessageParts = exchange.getIn().getBody(HashMap.class);
        // Prepare data for CH
        String header = multipartMessageParts.get("header").toString();
        String payload =  multipartMessageParts.get("payload").toString();
        Message message = multipartMessageService.getMessage(header);
        // Send data to CH
        clearingHouseService.registerTransaction(message, payload);
        logger.info("Successfully wrote down in the Clearing House on Consumer Route");

        exchange.getOut().setHeaders(exchange.getIn().getHeaders());
        exchange.getOut().setBody(exchange.getIn().getBody());
    }

}