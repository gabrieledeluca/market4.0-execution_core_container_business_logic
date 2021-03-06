/**
 * 
 */
package it.eng.idsa.businesslogic.service.impl;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.UUID;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import de.fraunhofer.iais.eis.LogNotification;
import de.fraunhofer.iais.eis.LogNotificationBuilder;
import de.fraunhofer.iais.eis.Message;
import de.fraunhofer.iais.eis.ids.jsonld.Serializer;
import it.eng.idsa.businesslogic.configuration.ApplicationConfiguration;
import it.eng.idsa.businesslogic.service.ClearingHouseService;
import it.eng.idsa.businesslogic.service.HashFileService;
import it.eng.idsa.clearinghouse.model.Body;
import it.eng.idsa.clearinghouse.model.NotificationContent;


/**
 * @author Milan Karajovic and Gabriele De Luca
 *
 */

@Service
@Transactional
public class ClearingHouseServiceImpl implements ClearingHouseService {
	@Autowired
	private ApplicationConfiguration configuration;

	private static final Logger logger = LogManager.getLogger(ClearingHouseServiceImpl.class);
	private final static String informationModelVersion = getInformationModelVersion();

	private static URI connectorURI;
	
	@Autowired
	private HashFileService hashService;


	@Override
	public boolean registerTransaction(Message correlatedMessage, String payload) {
		// TODO Auto-generated method stub
		try {
			logger.debug("registerTransaction...");
			try {
				connectorURI=new URI(configuration.getUriSchema()+configuration.getUriAuthority()+configuration.getUriConnector()+UUID.randomUUID().toString());
			} catch (URISyntaxException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			String endpoint=configuration.getClearingHouseUrl();
			RestTemplate restTemplate=new RestTemplate();
			//Create Message for Clearing House
			GregorianCalendar gcal = new GregorianCalendar();
			XMLGregorianCalendar xgcal = DatatypeFactory.newInstance()
					.newXMLGregorianCalendar(gcal);
			gcal = new GregorianCalendar();
			xgcal = DatatypeFactory.newInstance()
					.newXMLGregorianCalendar(gcal);
			ArrayList<URI> recipientConnectors = new ArrayList<URI>();
			recipientConnectors.add(connectorURI);
			
			LogNotification logNotification=new LogNotificationBuilder()
			 ._modelVersion_(informationModelVersion) 
			 ._issuerConnector_(whoIAm())
			 ._issued_(xgcal) .build();
			
			NotificationContent notificationContent=new NotificationContent();
			notificationContent.setHeader(logNotification);
			Body body=new Body();
			body.setHeader(correlatedMessage);
			String hash = hashService.hash(payload);
			body.setPayload(hash);

			notificationContent.setBody(body);
			
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			String msgSerialized = new Serializer().serializePlainJson(notificationContent);
			logger.info("msgSerialized to CH="+msgSerialized);
			JsonObject jsonObject = (JsonObject) Jsoner.deserialize(msgSerialized);

			//JSONParser parser = new JSONParser();
			//JSONObject jsonObject = (JSONObject) parser.parse(msgSerialized);

			HttpEntity<JsonObject> entity = new HttpEntity<>(jsonObject, headers);
			
			logger.info("Sending Data to the Clearing House "+endpoint+" ...");
			restTemplate.postForObject(endpoint, entity, String.class);
			logger.info("Data [LogNotitication.id="+logNotification.getId()+"] sent to the Clearing House "+endpoint);
			hashService.recordHash(hash, payload, notificationContent);

		}catch(Exception e) {
			e.printStackTrace();
		}
		return false;
	}
	private static String getInformationModelVersion() {
//		String currnetInformationModelVersion = null;
//		try {
//	
//			InputStream is = RejectionMessageServiceImpl.class.getClassLoader().getResourceAsStream("META-INF/maven/it.eng.idsa/market4.0-execution_core_container_business_logic/pom.xml");
//			MavenXpp3Reader reader = new MavenXpp3Reader();
//			Model model = reader.read(is);
//			MavenProject project = new MavenProject(model);
//			Properties props = project.getProperties(); 
//			if (props.get("information.model.version")!=null) {
//				return props.get("information.model.version").toString();
//			}
//			for (int i = 0; i < model.getDependencies().size(); i++) {
//				if (model.getDependencies().get(i).getGroupId().equalsIgnoreCase("de.fraunhofer.iais.eis.ids.infomodel")){
//					String version=model.getDependencies().get(i).getVersion();
//					// If we want, we can delete "-SNAPSHOT" from the version
//					//					if (version.contains("-SNAPSHOT")) {
//					//						version=version.substring(0,version.indexOf("-SNAPSHOT"));
//					//					}
//					currnetInformationModelVersion=version;
//				}
//			}
//		}catch(Exception e) {
//			e.printStackTrace();
//		}
//		return currnetInformationModelVersion;
		return "1.0";
	}
	
	private URI whoIAm() {
		//TODO 
		return URI.create("auto-generated");
	}

}
