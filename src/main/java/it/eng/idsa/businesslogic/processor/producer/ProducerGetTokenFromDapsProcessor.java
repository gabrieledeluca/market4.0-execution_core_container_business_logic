package it.eng.idsa.businesslogic.processor.producer;

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;

import de.fraunhofer.iais.eis.Message;
import de.fraunhofer.iais.eis.Token;
import de.fraunhofer.iais.eis.TokenBuilder;
import de.fraunhofer.iais.eis.TokenFormat;
import de.fraunhofer.iais.eis.ids.jsonld.Serializer;
import it.eng.idsa.businesslogic.service.DapsService;
import it.eng.idsa.businesslogic.service.HttpHeaderService;
import it.eng.idsa.businesslogic.service.MultipartMessageService;
import it.eng.idsa.businesslogic.service.RejectionMessageService;
import it.eng.idsa.businesslogic.util.RejectionMessageType;
import it.eng.idsa.multipart.builder.MultipartMessageBuilder;
import it.eng.idsa.multipart.domain.MultipartMessage;

/**
 * 
 * @author Milan Karajovic and Gabriele De Luca
 *
 */

@Component
public class ProducerGetTokenFromDapsProcessor implements Processor {

	private static final Logger logger = LogManager.getLogger(ProducerGetTokenFromDapsProcessor.class);

	@Autowired
	private MultipartMessageService multipartMessageService;

	@Autowired
	private RejectionMessageService rejectionMessageService;

	@Autowired
	private DapsService dapsService;

	@Autowired
	private HttpHeaderService httpHeaderService;

	@Value("${application.eccHttpSendRouter}")
	private String eccHttpSendRouter;

	@Override
	public void process(Exchange exchange) throws Exception {

		MultipartMessage multipartMessage = exchange.getIn().getBody(MultipartMessage.class);
		Map<String, Object> headersParts = exchange.getIn().getHeaders();
		Message message = null;

		// Get message id
		if (eccHttpSendRouter.equals("http-header")) {
			logger.info("message id=" + multipartMessage.getHttpHeaders().get("IDS-Id"));
			message = multipartMessage.getHeaderContent();
		} else {
			message = multipartMessageService.getMessage(multipartMessage.getHeaderContentString());
			logger.info("message id=" + message.getId());
		}

		// Get the Token from the DAPS
		String token = null;
		try {
			token = dapsService.getJwtToken();
		} catch (Exception e) {
			logger.error("Can not get the token from the DAPS server ", e);
			rejectionMessageService.sendRejectionMessage(RejectionMessageType.REJECTION_TOKEN_LOCAL_ISSUES, message);
		}

		if (token == null) {
			logger.error("Can not get the token from the DAPS server");
			rejectionMessageService.sendRejectionMessage(RejectionMessageType.REJECTION_COMMUNICATION_LOCAL_ISSUES,
					message);
		}

		if (token.isEmpty()) {
			logger.error("The token from the DAPS server is empty");
			rejectionMessageService.sendRejectionMessage(RejectionMessageType.REJECTION_TOKEN_LOCAL_ISSUES, message);
		}

		logger.info("token=" + token);
		if (eccHttpSendRouter.equals("http-header")) {
			//TODO move this to SendDataToBussinessLogicServiceImpl
			multipartMessage = new MultipartMessageBuilder()
					.withHttpHeader(multipartMessage.getHttpHeaders())
					.withHeaderHeader(multipartMessage.getHeaderHeader())
                    .withHeaderContent(multipartMessage.getHeaderContent())
					.withPayloadHeader(multipartMessage.getPayloadHeader())
					.withPayloadContent(multipartMessage.getPayloadContent())
					.withToken(token).build();
		} else {
			String messageStringWithToken = multipartMessageService.addToken(message, token);
			logger.info("messageStringWithToken=" + messageStringWithToken);

			multipartMessage = new MultipartMessageBuilder()
					.withHttpHeader(multipartMessage.getHttpHeaders())
					.withHeaderHeader(multipartMessage.getHeaderHeader())
					.withHeaderContent(messageStringWithToken)
					.withPayloadHeader(multipartMessage.getPayloadHeader())
					.withPayloadContent(multipartMessage.getPayloadContent())
					.withToken(token).build();
		}
		// Return exchange
		exchange.getOut().setBody(multipartMessage);
		exchange.getOut().setHeaders(headersParts);

	}

	private void transformJWTTokenToHeaders(String token, Map<String, Object> headersParts)
			throws JsonMappingException, JsonProcessingException, ParseException {
		Token tokenJsonValue = new TokenBuilder()._tokenFormat_(TokenFormat.JWT)._tokenValue_(token).build();
		String tokenValueSerialized = new Serializer().serializePlainJson(tokenJsonValue);
		JSONParser parser = new JSONParser();
		JSONObject jsonObjectToken = (JSONObject) parser.parse(tokenValueSerialized);

		headersParts.put("IDS-SecurityToken-Type", jsonObjectToken.get("@type").toString());
		headersParts.put("IDS-SecurityToken-Id", tokenJsonValue.getId().toString());
		headersParts.put("IDS-SecurityToken-TokenFormat", tokenJsonValue.getTokenFormat().toString());
		headersParts.put("IDS-SecurityToken-TokenValue", token);
	}

}
