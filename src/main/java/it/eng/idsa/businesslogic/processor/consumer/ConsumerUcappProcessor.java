package it.eng.idsa.businesslogic.processor.consumer;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import com.google.gson.internal.LinkedTreeMap;
import de.fraunhofer.dataspaces.iese.camel.interceptor.model.IdsMsgTarget;
import de.fraunhofer.dataspaces.iese.camel.interceptor.model.IdsUseObject;
import de.fraunhofer.dataspaces.iese.camel.interceptor.model.UsageControlObject;
import de.fraunhofer.dataspaces.iese.camel.interceptor.service.UcService;
import de.fraunhofer.iais.eis.Message;
import it.eng.idsa.businesslogic.processor.producer.ProducerUcappProcessor;
import it.eng.idsa.businesslogic.service.MultipartMessageService;
import it.eng.idsa.businesslogic.service.RejectionMessageService;
import it.eng.idsa.businesslogic.util.RejectionMessageType;
import org.apache.camel.Exchange;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.converter.stream.CachedOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Antonio Scatoloni and Gabriele De Luca
 */
@ComponentScan("de.fraunhofer.dataspaces.iese")
@Component
public class ConsumerUcappProcessor implements Processor {

    private Gson gson;
    private static final Logger logger = LoggerFactory.getLogger(ConsumerUcappProcessor.class);
    @Value("${application.isEnabledUsageControl?:false}")
    private boolean isEnabledUsageControl;
    @Autowired
    private UcService ucService;
    @Autowired
    private MultipartMessageService multipartMessageService;
    @Autowired
    private RejectionMessageService rejectionMessageService;

    private NamedNode definition;

    public ConsumerUcappProcessor(UcService ucService) {
        gson = ProducerUcappProcessor.createGson();
    }

    public void setDefinition(NamedNode definition) {
        this.definition = definition;
    }

    @Override
    public void process(Exchange exchange) {
        if (!isEnabledUsageControl) {
            exchange.getOut().setHeaders(exchange.getIn().getHeaders());
            exchange.getOut().setBody(exchange.getIn().getBody());
            return;
        }
        Message message = null;
        try {
            Map<String, Object> multipartMessageParts = exchange.getIn().getBody(HashMap.class);
            String header = multipartMessageParts.get("header").toString();
            String payload = multipartMessageParts.get("payload").toString();
            message = multipartMessageService.getMessage(header);
            //String dataAsString = exchange.getIn().getBody(String.class);
            logger.info("from: " + exchange.getFromEndpoint());
            logger.info("Message Body: " + payload);
            logger.info("Message Body Out: " + exchange.getOut().getBody(String.class));

            // Dummy connectionid
            UsageControlObject ucObj = null;
            JsonElement transferedDataObject = getDataObject(payload);
            boolean isUsageControlObject = false;
            ucObj = gson.fromJson(transferedDataObject, UsageControlObject.class);
            isUsageControlObject = true;

            if (isUsageControlObject && null != ucObj) {
                String targetArtifactId = ucObj.getMeta().getTargetArtifact().getId().toString();
                IdsMsgTarget idsMsgTarget = getIdsMsgTarget();
                if (null != ucObj.getPayload() && !(CachedOutputStream.class.equals(ucObj.getPayload().getClass().getEnclosingClass()))) {
                    logger.info("Message Body In: " + ucObj.getPayload().toString());

                    IdsUseObject idsUseObject = new IdsUseObject();
                    idsUseObject.setTargetDataUri(targetArtifactId);
                    idsUseObject.setMsgTarget(idsMsgTarget);
                    idsUseObject.setDataObject(ucObj.getPayload());

                    Object result = ucService.enforceUsageControl(idsUseObject);
                    // LOGGER.info("Message Body Out: " + dataObject.toString());
                    if (result instanceof LinkedTreeMap<?, ?>) {
                        final LinkedTreeMap<?, ?> treeMap = (LinkedTreeMap<?, ?>) result;
                        final JsonElement jsonElement = gson.toJsonTree(treeMap);
                        ucObj.setPayload(jsonElement);
                        logger.info("Result from Usage Control: " + jsonElement.toString());
                    } else if (null == result) {
                        ucObj.setPayload(null);
                    }
                    multipartMessageParts.put("payload", ucObj.getPayload());
                    exchange.getIn().setBody(multipartMessageParts);
                }
            }
            exchange.getOut().setHeaders(exchange.getIn().getHeaders());
            exchange.getOut().setBody(exchange.getIn().getBody());
        } catch (Exception e) {
            logger.error("Usage Control Enforcement has failed with MESSAGE: " + e.getMessage());
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


}
