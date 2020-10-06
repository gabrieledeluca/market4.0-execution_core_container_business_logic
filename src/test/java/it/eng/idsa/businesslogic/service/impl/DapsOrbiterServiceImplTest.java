package it.eng.idsa.businesslogic.service.impl;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Date;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import it.eng.idsa.businesslogic.service.DapsService;

@Disabled
public class DapsOrbiterServiceImplTest {

	private String eccConsumer = "2a62eda0-50bd-4640-9847-b0ea946f89bf";
	private String eccProducer = "805f80f9-3170-4615-b80a-e93f2a4708e5";
	
	private DapsService dapsService;
	
	@BeforeEach
	public void setup() {
		dapsService = new DapsOrbiterServiceImpl();
		
		ReflectionTestUtils.setField(dapsService, "targetDirectory", Paths.get("c:\\Users\\igor.balog\\tools\\certificates"));
		ReflectionTestUtils.setField(dapsService, "keyStoreName", "engineering1-keystore.jks");
		ReflectionTestUtils.setField(dapsService, "keyStorePassword", "password");
		ReflectionTestUtils.setField(dapsService, "keystoreAliasName", "1");
		ReflectionTestUtils.setField(dapsService, "dapsUrl", "http://212.81.222.225:8084/token");
	}
	
	@Test
	public void getDapsOrbiterTokenProducer() {
		ReflectionTestUtils.setField(dapsService, "connectorUUID", eccProducer);
		ReflectionTestUtils.setField(dapsService, "dapsOrbiterPrivateKey", "ecc-producer.key");
		String jwToken = dapsService.getJwtToken();
		assertTrue(StringUtils.isNotBlank(jwToken));
	}
	
	@Test
	public void generateJwsProducer() {
		try {
			Date expiryDate = Date.from(Instant.now().plusSeconds(86400));
            JwtBuilder jwtb =
                    Jwts.builder()
                            .claim("id", eccProducer)
                            .setExpiration(expiryDate)
                            .setIssuedAt(Date.from(Instant.now()))
                            .setAudience("idsc:IDS_CONNECTORS_ALL")
                            .setNotBefore(Date.from(Instant.now()));
			String jws = jwtb.signWith(SignatureAlgorithm.RS256, getOrbiterPrivateKey("ecc-producer.key")).compact();
			System.out.println("Producer key:\n" + jws);
		} catch (KeyStoreException e) {
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (CertificateException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	@Test
	public void generateJwsConsumer() {
		try {
			Date expiryDate = Date.from(Instant.now().plusSeconds(86400));
            JwtBuilder jwtb =
                    Jwts.builder()
                            .claim("id", eccConsumer)
                            .setExpiration(expiryDate)
                            .setIssuedAt(Date.from(Instant.now()))
                            .setAudience("idsc:IDS_CONNECTORS_ALL")
                            .setNotBefore(Date.from(Instant.now()));
			String jws = jwtb.signWith(SignatureAlgorithm.RS256, getOrbiterPrivateKey("ecc-consumer.key")).compact();
			System.out.println("Consumer jwt:\n" + jws);
		} catch (KeyStoreException e) {
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (CertificateException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Reads Orbiter private key from file, removes header and footer and creates java PrivateKey object
	 * @return
	 * @throws IOException
	 * @throws GeneralSecurityException
	 */
	private PrivateKey getOrbiterPrivateKey(String keyFile) throws IOException, GeneralSecurityException {
		InputStream orbiterPrivateKeyInputStream = null;
		try {
			orbiterPrivateKeyInputStream = new ClassPathResource(keyFile).getInputStream();
			String privateKeyPEM = IOUtils.toString(orbiterPrivateKeyInputStream, StandardCharsets.UTF_8.name());
			privateKeyPEM = privateKeyPEM.replace("-----BEGIN PRIVATE KEY-----\n", "");
			privateKeyPEM = privateKeyPEM.replace("-----END PRIVATE KEY-----", "");
			byte[] encoded = org.apache.commons.codec.binary.Base64.decodeBase64(privateKeyPEM);
			KeyFactory kf = KeyFactory.getInstance("RSA");
			PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encoded);
			return (PrivateKey) kf.generatePrivate(keySpec);
		} finally {
			if(orbiterPrivateKeyInputStream != null) {
				orbiterPrivateKeyInputStream.close();
			}
		}
	}
}
