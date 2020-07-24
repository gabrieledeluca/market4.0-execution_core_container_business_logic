/**
 *
 */
package it.eng.idsa.businesslogic.processor.producer;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import de.fraunhofer.dataspaces.iese.camel.interceptor.model.*;
import de.fraunhofer.dataspaces.iese.camel.interceptor.service.UcService;
import de.fraunhofer.iais.eis.Message;
import it.eng.idsa.businesslogic.service.MultipartMessageService;
import it.eng.idsa.businesslogic.service.RejectionMessageService;
import it.eng.idsa.businesslogic.util.RejectionMessageType;
import org.apache.camel.Exchange;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Antonio Scatoloni and Gabriele De Luca
 *
 */
@ComponentScan("de.fraunhofer.dataspaces.iese")
@Component
public class ProducerUcappProcessor implements Processor {

    private Gson gson;
    private static final Logger logger = LoggerFactory.getLogger(ProducerUcappProcessor.class);
    @Value("${application.isEnabledUsageControl?:false}")
    private boolean isEnabledUsageControl;
    @Autowired
    private UcService ucService;
    @Autowired
    private MultipartMessageService multipartMessageService;
    @Autowired
    private RejectionMessageService rejectionMessageService;

    private NamedNode definition;


    public ProducerUcappProcessor(UcService ucService) {
        gson = createGson();
    }

    /**
     * simple Builder
     *
     * @param ucService
     * @return
     */
    public static ProducerUcappProcessor ucappProcessor(UcService ucService) {
        return new ProducerUcappProcessor(ucService);
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
            message = multipartMessageService.getMessage(header);
            //String meta = exchange.getIn().getHeaders().get("Usage-Control").toString();
            //String dataAsString = createUsageControlObject(meta, multipartMessageParts.get("payload").toString());
            String dataAsString = createUsageControlObject(message.getId(), multipartMessageParts.get("payload").toString());
            logger.info("from: " + exchange.getFromEndpoint());
            logger.info("Message Body: " + dataAsString);
            logger.info("Message Body Out: " + exchange.getOut().getBody(String.class));

            // Dummy connectionid
           /* UsageControlObject ucObj = null;
            JsonElement transferedDataObject = getDataObject(dataAsString);
            boolean isUsageControlObject = false;
            ucObj = gson.fromJson(transferedDataObject, UsageControlObject.class);
            isUsageControlObject = true;
            if (isUsageControlObject && null != ucObj) {
                String targetArtifactId = ucObj.getMeta().getTargetArtifact().getId().toString();
                final IdsMsgTarget idsMsgTarget = getIdsMsgTarget();
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
                    } else if (null == result) {
                        ucObj.setPayload(null);
                    }
                    String transferedDataAsString = gson.toJson(ucObj);
                    multipartMessageParts.put("payload", transferedDataAsString);
                    exchange.getIn().setBody(multipartMessageParts);
                    exchange.getMessage().setBody(multipartMessageParts);
                }
            }*/
            multipartMessageParts.put("payload", dataAsString);
            exchange.getIn().setBody(multipartMessageParts);
            exchange.getMessage().setBody(multipartMessageParts);

            exchange.getOut().setHeaders(exchange.getIn().getHeaders());
            exchange.getOut().setBody(exchange.getIn().getBody());
        } catch (Exception e) {
            logger.error("Usage Control Enforcement has failed with MESSAGE: " + e.getMessage());
            rejectionMessageService.sendRejectionMessage(
                    RejectionMessageType.REJECTION_USAGE_CONTROL,
                    message);
        }
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

    public static Gson createGson() {
        Gson gson = new GsonBuilder().registerTypeAdapter(ZonedDateTime.class, new TypeAdapter<ZonedDateTime>() {
            @Override
            public void write(JsonWriter writer, ZonedDateTime zdt) throws IOException {
                writer.value(zdt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            }

            @Override
            public ZonedDateTime read(JsonReader in) throws IOException {
                return ZonedDateTime.parse(in.nextString(), DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            }
        }).enableComplexMapKeySerialization().create();

        return gson;
    }


    /*private String createUsageControlObject(String meta, String payload) {
        UsageControlObject usageControlObject = new UsageControlObject();
        JsonElement jsonElement = gson.fromJson(payload, JsonElement.class);
        usageControlObject.setPayload(jsonElement);
        Meta meta1 = gson.fromJson(meta, Meta.class);
        usageControlObject.setMeta(meta1);
        String usageControlObjectPayload = gson.toJson(usageControlObject, UsageControlObject.class);
        return usageControlObjectPayload;
    }*/

    private String createUsageControlObject(URI targetId, String payload) throws URISyntaxException {
        UsageControlObject usageControlObject = new UsageControlObject();
        JsonElement jsonElement = gson.fromJson(payload, JsonElement.class);
        usageControlObject.setPayload(jsonElement);
        Meta meta = new Meta();
        meta.setAssignee(new URI("http://ids-b.iese.de/consumer")); //TODO
        meta.setAssigner(new URI("http://ids-b.iese.de/provider")); //TODO
        TargetArtifact targetArtifact = new TargetArtifact();
        LocalDateTime localDateTime = LocalDateTime.now();
        ZonedDateTime zonedDateTime = ZonedDateTime.of(localDateTime, ZoneId.of("CET"));
        targetArtifact.setCreationDate(zonedDateTime);
        targetArtifact.setId(targetId);
        meta.setTargetArtifact(targetArtifact);
        usageControlObject.setMeta(meta);
        String usageControlObjectPayload = gson.toJson(usageControlObject, UsageControlObject.class);
        return usageControlObjectPayload;
    }

}
