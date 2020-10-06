package it.eng.idsa.businesslogic.processor.producer;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.internal.LinkedTreeMap;
import de.fraunhofer.dataspaces.iese.camel.interceptor.model.IdsMsgTarget;
import de.fraunhofer.dataspaces.iese.camel.interceptor.model.IdsUseObject;
import de.fraunhofer.dataspaces.iese.camel.interceptor.model.UsageControlObject;
import de.fraunhofer.dataspaces.iese.camel.interceptor.service.UcService;
import de.fraunhofer.iais.eis.Message;
import it.eng.idsa.businesslogic.configuration.WebSocketServerConfigurationA;
import it.eng.idsa.businesslogic.processor.consumer.ConsumerUsageControlProcessor;
import it.eng.idsa.businesslogic.processor.consumer.websocket.server.ResponseMessageBufferBean;
import it.eng.idsa.businesslogic.service.MultipartMessageService;
import it.eng.idsa.businesslogic.service.RejectionMessageService;
import it.eng.idsa.businesslogic.util.RejectionMessageType;
import it.eng.idsa.multipart.builder.MultipartMessageBuilder;
import it.eng.idsa.multipart.domain.MultipartMessage;
import it.eng.idsa.multipart.processor.MultipartMessageProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.converter.stream.CachedOutputStream;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Component;

/**
 * @author Antonio Scatoloni and Gabriele De Luca
 */
@ComponentScan("de.fraunhofer.dataspaces.iese")
@Component
public class ProducerUsageControlProcessor implements Processor {
    private Gson gson;
    private static final Logger logger = LoggerFactory.getLogger(ProducerUsageControlProcessor.class);

    @Value("${application.isEnabledUsageControl:false}")
    private boolean isEnabledUsageControl;

    @Autowired
    private UcService ucService;

    @Autowired
    private MultipartMessageService multipartMessageService;

    @Autowired
    private RejectionMessageService rejectionMessageService;

    @Value("${application.dataApp.websocket.isEnabled}")
    private boolean isEnabledWebSocket;

    @Autowired(required = false)
    WebSocketServerConfigurationA webSocketServerConfiguration;

    public ProducerUsageControlProcessor() {
        gson = ConsumerUsageControlProcessor.createGson();
    }

    @Override
    public void process(Exchange exchange) {
        if (!isEnabledUsageControl) {
            exchange.getOut().setHeaders(exchange.getIn().getHeaders());
            exchange.getOut().setBody(exchange.getIn().getBody());
            return;
        }
        Message message = null;
        String responseMultipartMessageString = null;
        try {
            String multipartMessageBody = exchange.getIn().getBody().toString();
            String header = multipartMessageService.getHeaderContentString(multipartMessageBody);
            String payload = multipartMessageService.getPayloadContent(multipartMessageBody);
            message = multipartMessageService.getMessage(header);
            logger.info("from: " + exchange.getFromEndpoint());
            logger.info("Message Body: " + payload);
            logger.info("Message Body Out: " + exchange.getOut().getBody(String.class));

            JsonElement transferedDataObject = getDataObject(payload);
            UsageControlObject ucObj = gson.fromJson(transferedDataObject, UsageControlObject.class);
            boolean isUsageControlObject = true;

            if (isUsageControlObject
                    && null != ucObj
                    && null != ucObj.getMeta()
                    && null != ucObj.getPayload()) {
                String targetArtifactId = ucObj.getMeta().getTargetArtifact().getId().toString();
                IdsMsgTarget idsMsgTarget = getIdsMsgTarget();
                if (null != ucObj.getPayload() && !(CachedOutputStream.class.equals(ucObj.getPayload().getClass().getEnclosingClass()))) {
                    logger.info("Message Body In: " + ucObj.getPayload().toString());

                    IdsUseObject idsUseObject = new IdsUseObject();
                    idsUseObject.setTargetDataUri(targetArtifactId);
                    idsUseObject.setMsgTarget(idsMsgTarget);
                    idsUseObject.setDataObject(ucObj.getPayload());

                    Object result = ucService.enforceUsageControl(idsUseObject);
                    if (result instanceof LinkedTreeMap<?, ?>) {
                        final LinkedTreeMap<?, ?> treeMap = (LinkedTreeMap<?, ?>) result;
                        final JsonElement jsonElement = gson.toJsonTree(treeMap);
                        ucObj.setPayload(jsonElement);
                        logger.info("Result from Usage Control: " + jsonElement.toString());
                    } else if (null == result || StringUtils.isEmpty(result.toString())) {
                        throw new Exception("Usage Control Enforcement with EMPTY RESULT encountered.");
                    }
                    // Prepare Response
                    MultipartMessage multipartMessage = new MultipartMessageBuilder()
                            .withHeaderContent(header)
                            .withPayloadContent(extractPayloadFromJson(ucObj.getPayload()))
                            .build();
                    responseMultipartMessageString = MultipartMessageProcessor.
                            multipartMessagetoString(multipartMessage, false);
                }
            }
            if(isEnabledWebSocket) {
                ResponseMessageBufferBean responseMessageServerBean = webSocketServerConfiguration.responseMessageBufferWebSocket();
                responseMessageServerBean.add(responseMultipartMessageString.getBytes());
            }
            exchange.getOut().setHeaders(exchange.getIn().getHeaders());
            exchange.getOut().setBody(responseMultipartMessageString);
        } catch (Exception e) {
            logger.error("Usage Control Enforcement has failed with MESSAGE: ", e.getMessage());
            rejectionMessageService.sendRejectionMessage(
                    RejectionMessageType.REJECTION_USAGE_CONTROL,
                    message);
        }
    }

    //TODO
    public static IdsMsgTarget getIdsMsgTarget() {
        IdsMsgTarget idsMsgTarget = new IdsMsgTarget();
        idsMsgTarget.setName("Anwendung A");
        // idsMsgTarget.setAppUri(target.toString());
        idsMsgTarget.setAppUri("http://ziel-app");
        return idsMsgTarget;
    }

    private JsonElement getDataObject(String s) {
        JsonElement obj = null;
        try {
            JsonElement jsonElement = gson.fromJson(s, JsonElement.class);
            if (null != jsonElement && !(jsonElement.isJsonArray() && jsonElement.getAsJsonArray().size() == 0)) {
                obj = jsonElement;
            }
        } catch (JsonSyntaxException jse) {
            obj = null;
        }
        return obj;
    }

    private String extractPayloadFromJson(JsonElement payload) {
        final JsonObject asJsonObject = payload.getAsJsonObject();
        JsonElement payloadInner = asJsonObject.get("payload");
        if (null != payloadInner) return payloadInner.getAsString();
        return payload.toString();
    }
}
