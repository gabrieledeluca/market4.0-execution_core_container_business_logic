package it.eng.idsa.businesslogic.service.impl;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URISyntaxException;

import javax.xml.datatype.DatatypeConfigurationException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import de.fraunhofer.iais.eis.Message;
import de.fraunhofer.iais.eis.ids.jsonld.Serializer;
import de.fraunhofer.iais.eis.util.ConstraintViolationException;
import it.eng.idsa.businesslogic.service.DapsService;

public class SelfDescriptionServiceImplTest {
	
	@Mock
	private DapsService dapsService;

	private SelfDescriptionServiceImpl selfDefinitionService;

	private String infoModelVersion = "4.0.0";
	private String companyURI = "http://companyURI";
	private String connectorURI = "http://connectorURI";
	private String resourceTitle = "Resource title";
	private String resourceLang = "en";
	private String resourceDescription = "Resource description";

	@BeforeEach
	public void setup() throws ConstraintViolationException, URISyntaxException {
		MockitoAnnotations.initMocks(this);
		when(dapsService.getJwtToken()).thenReturn("mockTokenValue");
		selfDefinitionService = new SelfDescriptionServiceImpl(dapsService, infoModelVersion, companyURI, connectorURI,
				resourceTitle, resourceLang, resourceDescription);
		selfDefinitionService.initConnector();
	}

	@Test
	public void getConnectionString() {
		String connectionString = selfDefinitionService.getConnectorAsString();
		assertNotNull(connectionString);
//		System.out.println(connectionString);

		assertTrue(connectionString.contains("ids:BaseConnector"));
		assertTrue(connectionString.contains("outboundModelVersion"));
		assertTrue(connectionString.contains("inboundModelVersion"));
		assertTrue(connectionString.contains("maintainer"));
		assertTrue(connectionString.contains("curator"));
		assertTrue(connectionString.contains("title"));
		assertTrue(connectionString.contains("securityProfile"));
		assertTrue(connectionString.contains("description"));
		// TODO following 2 are not present yet, maybe different model
//		assertTrue(connectionString.contains("ids:catalog"));
//		assertTrue(connectionString.contains("ids:mainTitle"));
		
	}
	
	@Test
	public void connectorAvailabilityMessage() throws ConstraintViolationException, URISyntaxException, DatatypeConfigurationException {
		Message availabilityMessage = selfDefinitionService.getConnectorAvailbilityMessage();
		assertNotNull(availabilityMessage);
		assertNotNull(availabilityMessage.getSecurityToken());
		String ss = geObjectAsString(availabilityMessage);
		System.out.println(ss);
	}
	
	@Test
	public void connectorInactiveMessage() throws ConstraintViolationException, URISyntaxException, DatatypeConfigurationException {
		Message inactiveMessage = selfDefinitionService.getConnectorInactiveMessage();
		assertNotNull(inactiveMessage.getSecurityToken());
		assertNotNull(inactiveMessage);
	}

	@Test
	public void connectorUpdateMessage() throws ConstraintViolationException, URISyntaxException, DatatypeConfigurationException {
		Message updateMessage = selfDefinitionService.getConnectorUpdateMessage();
		assertNotNull(updateMessage.getSecurityToken());
		assertNotNull(updateMessage);
	}
	
	@Test
	public void connectorUnavailableMessage() throws ConstraintViolationException, URISyntaxException, DatatypeConfigurationException {
		Message unavailableMessage = selfDefinitionService.getConnectorUnavailableMessage();
		assertNotNull(unavailableMessage.getSecurityToken());
		assertNotNull(unavailableMessage);
	}
	
	private String geObjectAsString(Object toSerialize) {
		final Serializer serializer = new Serializer();
		String result = null;
		try {
			result = serializer.serialize(toSerialize);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return result;
	}
}
