
package it.eng.idsa.businesslogic.service.impl;

import de.fraunhofer.iais.eis.*;
import de.fraunhofer.iais.eis.ids.jsonld.Serializer;
import de.fraunhofer.iais.eis.util.ConstraintViolationException;
import de.fraunhofer.iais.eis.util.PlainLiteral;
import de.fraunhofer.iais.eis.util.Util;
import it.eng.idsa.businesslogic.service.SelfDescriptionService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

/**
 * @author Antonio Scatoloni and Gabriele De Luca
 */

@Service
public class SelfDescriptionServiceImpl implements SelfDescriptionService {
    private static final Logger logger = LogManager.getLogger(SelfDescriptionServiceImpl.class);
    String infoModelVersion;
    String companyURI;
    String connectorURI;
    String resourceTitle;
    String resourceLang;
    String resourceDescription;
    Connector connector;

    @PostConstruct
    private void initConnector() throws ConstraintViolationException, URISyntaxException {
        this.connector = (new BaseConnectorBuilder(new URI(this.connectorURI)))
                ._maintainer_(new URI(this.companyURI))
                ._curator_(new URI(this.companyURI))
                ._catalog_(this.getCatalog())
                ._securityProfile_(SecurityProfile.BASE_CONNECTOR_SECURITY_PROFILE)
                ._inboundModelVersion_(Util.asList(new String[]{this.infoModelVersion}))
                ._outboundModelVersion_(this.infoModelVersion)
                .build();
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
            e.printStackTrace();
        }
        return result;
    }

    private Resource getResource() {
        Resource offeredResource = (new ResourceBuilder())
                ._title_(Util.asList(new PlainLiteral[]{new PlainLiteral(this.resourceTitle, this.resourceLang)}))
                ._description_(Util.asList(new PlainLiteral[]{new PlainLiteral(this.resourceDescription, this.resourceLang)}))
                ._contractOffer_(getContractOffers())
                .build();
        return offeredResource;
    }

    //TODO
    private ArrayList<ContractOffer> getContractOffers() {
        try {
            File file = new File(
                    this.getClass().getClassLoader().getResource("contract-offers.json").getFile()
            );
            Path filePath = Path.of(file.getAbsolutePath());
            ContractOffer contractOffer = new Serializer().deserialize(Files.readString(filePath), ContractOffer.class);
            ArrayList<ContractOffer> contractOfferList = new ArrayList<>();
            contractOfferList.add(contractOffer);
            return contractOfferList;
        } catch (IOException e) {
            logger.error("Error in SelfDescriptionService while retrieving contract-offers.json with message: " + e.getMessage());
        }
        return null;
    }

    private Catalog getCatalog() {
        Catalog catalog = (new CatalogBuilder())
                ._offer_(Util.asList(new Resource[]{this.getResource()}))
                .build();
        return catalog;
    }

    //TODO Add Properties
    @Value("${it.eng.idsa.service.infomodel.version ?:2.1.0-SNAPSHOT}")
    public void setInfoModelVersion(String infoModelVersion) {
        this.infoModelVersion = infoModelVersion;
    }

    @Value("${it.eng.idsa.service.company.uri ?:http://w3c.eng.it}")
    public void setCompanyURI(String companyURI) {
        this.companyURI = companyURI;
    }

    @Value("${it.eng.idsa.service.connector.uri ?:http://w3c.eng.it}")
    public void setConnectorURI(String connectorURI) {
        this.connectorURI = connectorURI;
    }

    @Value("${it.eng.idsa.service.resources.title ?:MultipartData}")
    public void setResourceTitle(String resourceTitle) {
        this.resourceTitle = resourceTitle;
    }

    @Value("${it.eng.idsa.service.resources.title ?:EN}")
    public void setResourceLang(String resourceLang) {
        this.resourceLang = resourceLang;
    }

    @Value("${it.eng.idsa.service.resources.title?:'Execution Core Container}")
    public void setResourceDescription(String resourceDescription) {
        this.resourceDescription = resourceDescription;
    }

    public static void main(String[] args) {
        SelfDescriptionServiceImpl selfDescriptionService = new SelfDescriptionServiceImpl();
        selfDescriptionService.getContractOffers();
    }
}
