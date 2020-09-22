
package it.eng.idsa.businesslogic.service.impl;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.UUID;

import javax.annotation.PostConstruct;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import de.fraunhofer.iais.eis.BaseConnectorBuilder;
import de.fraunhofer.iais.eis.Connector;
import de.fraunhofer.iais.eis.ConnectorUnavailableMessageBuilder;
import de.fraunhofer.iais.eis.ConnectorUpdateMessageBuilder;
import de.fraunhofer.iais.eis.ContractOffer;
import de.fraunhofer.iais.eis.ContractOfferBuilder;
import de.fraunhofer.iais.eis.DynamicAttributeToken;
import de.fraunhofer.iais.eis.DynamicAttributeTokenBuilder;
import de.fraunhofer.iais.eis.Message;
import de.fraunhofer.iais.eis.Permission;
import de.fraunhofer.iais.eis.PermissionBuilder;
import de.fraunhofer.iais.eis.Resource;
import de.fraunhofer.iais.eis.ResourceBuilder;
import de.fraunhofer.iais.eis.ResourceCatalog;
import de.fraunhofer.iais.eis.ResourceCatalogBuilder;
import de.fraunhofer.iais.eis.SecurityProfile;
import de.fraunhofer.iais.eis.TokenFormat;
import de.fraunhofer.iais.eis.ids.jsonld.Serializer;
import de.fraunhofer.iais.eis.util.ConstraintViolationException;
import de.fraunhofer.iais.eis.util.TypedLiteral;
import de.fraunhofer.iais.eis.util.Util;
import edu.emory.mathcs.backport.java.util.Arrays;
import it.eng.idsa.businesslogic.service.DapsService;
import it.eng.idsa.businesslogic.service.SelfDescriptionService;

/**
 * @author Antonio Scatoloni and Gabriele De Luca
 */

@Service
public class SelfDescriptionServiceImpl implements SelfDescriptionService {
	private static final Logger logger = LogManager.getLogger(SelfDescriptionServiceImpl.class);
	private String infoModelVersion;
	private String companyURI;
	private String connectorURI;
	private String resourceTitle;
	private String resourceLang;
	private String resourceDescription;
	Connector connector;
	
	private DapsService dapsService;
	private URI issuerConnectorURI;

	public SelfDescriptionServiceImpl(
			DapsService dapsService,
			@Value("${it.eng.idsa.service.infomodel.version ?:4.0.0}") final String infoModelVersion,
			@Value("${it.eng.idsa.service.company.uri ?:http://w3c.eng.it}") final String companyURI,
			@Value("${it.eng.idsa.service.connector.uri ?:http://w3c.eng.it}") final String connectorURI,
			@Value("${it.eng.idsa.service.resources.title ?:MultipartData}") final String resourceTitle,
			@Value("${it.eng.idsa.service.resources.title ?:EN}") final String resourceLang,
			@Value("${it.eng.idsa.service.resources.title?:'Execution Core Container}") final String resourceDescription) {
		this.dapsService = dapsService;
		this.infoModelVersion = infoModelVersion;
		this.companyURI = companyURI;
		this.connectorURI = connectorURI;
		this.resourceTitle = resourceTitle;
		this.resourceLang = resourceLang;
		this.resourceDescription = resourceDescription;
	}

	@PostConstruct
	public void initConnector() throws ConstraintViolationException, URISyntaxException {
		issuerConnectorURI = new URI("https://test.connector.de/" + RandomStringUtils.randomAlphabetic(3));
		this.connector = (new BaseConnectorBuilder(issuerConnectorURI))
				._maintainer_(new URI(this.companyURI))
				._curator_(issuerConnectorURI)
				._resourceCatalog_((ArrayList<? extends ResourceCatalog>) this.getCatalog()) // Infomodel version 4.0.0
				._securityProfile_(SecurityProfile.BASE_SECURITY_PROFILE) // Infomodel version 4.0.0
				// ._securityProfile_(SecurityProfile.BASE_CONNECTOR_SECURITY_PROFILE) //
				// Infomodel version 2.1.0-SNAPSHOT
				._maintainer_(new URI("https://maintainerURL" + RandomStringUtils.randomAlphabetic(3)))
//				._authInfo_(new AuthInfoBuilder(new URI("conn1:auth_info"))._authService_(new URI("https://authServiceURL"))._authStandard_(AuthStandard.OAUTH2_JWT).build())
				._inboundModelVersion_(Util.asList(new String[] { this.infoModelVersion }))
				._title_(Util.asList(new TypedLiteral("Test Fraunhofer Digital Broker " + RandomStringUtils.randomAlphabetic(3), "en")))
//				._mainTitle_(Util.asList(new PlainLiteral("Fraunhofer Digital Broker", "en")))
				._description_(Util.asList(new TypedLiteral("This is selfDescription description " + RandomStringUtils.randomAlphabetic(3), "en")))
				._outboundModelVersion_(this.infoModelVersion).build();
	}

	public Connector getConnector() throws ConstraintViolationException, URISyntaxException {
		if (null == this.connector) {
			this.initConnector();
		}

		return this.connector;
	}

	@Override
	public String getConnectorAsString() {
		final Serializer serializer = new Serializer();
		String result = null;
		try {
			result = serializer.serialize(this.connector);
		} catch (IOException e) {
			logger.error(e);
		}
		return result;
	}

	private Resource getResource() throws ConstraintViolationException, URISyntaxException {
		Resource offeredResource = (new ResourceBuilder())
				._title_(Util.asList(new TypedLiteral(this.resourceTitle, this.resourceLang)))
				._description_(Util.asList(new TypedLiteral(this.resourceDescription, this.resourceLang)))
				._contractOffer_(getContractOffers()).build();
		return offeredResource;
	}

	// TODO could not load contract-offers.json file from cmd
	private ArrayList<ContractOffer> getContractOffers() throws ConstraintViolationException, URISyntaxException {
//		try {
//			File file = new File(this.getClass().getClassLoader().getResource("contract-offers.json").getFile());
//			Path filePath = Path.of(file.getAbsolutePath());
//			logger.info("aaaaaaaaaaaaa {}", filePath.toString());
//			ContractOffer contractOffer = new Serializer().deserialize(Files.readString(filePath), ContractOffer.class);
		Permission permission = new PermissionBuilder(new URI("http://example.com/ids-profile/12344")).build();
			ContractOffer contractOffer = new ContractOfferBuilder(new URI("http://example.com/ids-profile/1234"))
					._provider_(new URI("http://example.com/party/my-party"))
					._permission_(Util.asList(permission))
					.build();
			
			ArrayList<ContractOffer> contractOfferList = new ArrayList<>();
			contractOfferList.add(contractOffer);
			return contractOfferList;
//		} catch (IOException e) {
//			logger.error("Error in SelfDescriptionService while retrieving contract-offers.json with message: "
//					+ e.getMessage());
//		}
//		return null;
	}

//	// Infomodel version 4.0.0
	private java.util.List<ResourceCatalog> getCatalog() throws ConstraintViolationException, URISyntaxException {
		ResourceCatalog catalog = (new ResourceCatalogBuilder()
				._offeredResource_(Util.asList(new Resource[] { this.getResource() })).build());
		java.util.List<ResourceCatalog> catalogList = new ArrayList<>();
		catalogList.add(catalog);
		return catalogList;
	}

	// Infomodel version 2.1.0-SNAPSHOT
//	private Catalog getCatalog210() {
//		Catalog catalog = (new CatalogBuilder())._offer_(Util.asList(new Resource[] { this.getResource() })).build();
//		return catalog;
//	}
	
	@Override
	public Message getConnectorAvailbilityMessage() throws ConstraintViolationException, URISyntaxException, DatatypeConfigurationException {
		DynamicAttributeToken securityToken = getJwToken();

		return new ConnectorUpdateMessageBuilder(new URI("https://w3id.org/idsa/autogen/connectorAvailableMessage/" + UUID.randomUUID().toString()))
		._securityToken_(securityToken)
		._senderAgent_(new URI("http://example.org" + RandomStringUtils.randomAlphabetic(3)))
		._issued_(DatatypeFactory.newInstance().newXMLGregorianCalendar(new GregorianCalendar()))
		._issuerConnector_(issuerConnectorURI)
		._modelVersion_(infoModelVersion)
		._affectedConnector_(connector.getId())
		.build();
	}

	private DynamicAttributeToken getJwToken() throws URISyntaxException {
		String jwToken = dapsService.getJwtToken();
		DynamicAttributeToken securityToken = 
				new DynamicAttributeTokenBuilder(new URI("https://w3id.org/idsa/autogen/dynamicAttributeToken/ec96a61a-8725-4227-90e7-a1976e6d4dfe"))
				._tokenValue_(jwToken)
				._tokenFormat_(TokenFormat.JWT)
				.build();
		return securityToken;
	}

	@Override
	public Message getConnectorUpdateMessage()
			throws ConstraintViolationException, URISyntaxException, DatatypeConfigurationException {
		
		DynamicAttributeToken securityToken = getJwToken();
	
		return new ConnectorUpdateMessageBuilder(new URI("https://w3id.org/idsa/autogen/connectorUpdateMessage/6d875403-cfea-4aad-979c-3515c2e71967"))
				._securityToken_(securityToken)
				._senderAgent_(new URI("http://example.org"))
				._modelVersion_(infoModelVersion)
				._issuerConnector_(issuerConnectorURI)
				._issued_(DatatypeFactory.newInstance().newXMLGregorianCalendar(new GregorianCalendar()))
				._affectedConnector_(connector.getId())
				.build();
	}

	@Override
	public Message getConnectorUnavailableMessage()
			throws ConstraintViolationException, URISyntaxException, DatatypeConfigurationException {

		DynamicAttributeToken securityToken = getJwToken();
		
		return new ConnectorUnavailableMessageBuilder(new URI("http://industrialdataspace.org/connectorUnavailableMessage/1a421b8c-3407-44a8-aeb9-253f145c869a"))
				._issued_(DatatypeFactory.newInstance().newXMLGregorianCalendar(new GregorianCalendar()))
				._modelVersion_(infoModelVersion)
				._issuerConnector_(issuerConnectorURI)
				._securityToken_(securityToken)
				.build();
	}

	@Override
	public Message getConnectorInactiveMessage()
			throws ConstraintViolationException, URISyntaxException, DatatypeConfigurationException {
		
		DynamicAttributeToken securityToken = getJwToken();
		
		return new ConnectorUnavailableMessageBuilder(new URI("https://w3id.org/idsa/autogen/connectorInactiveMessage/8ea20fa1-7258-41c9-abc2-82c787d50ec3"))
				._modelVersion_(infoModelVersion)
				._issuerConnector_(issuerConnectorURI)
				._senderAgent_(new URI("http://example.org"))
				._issued_(DatatypeFactory.newInstance().newXMLGregorianCalendar(new GregorianCalendar()))
				._securityToken_(securityToken)
				._affectedConnector_(connector.getId())
				.build();
	}

}
