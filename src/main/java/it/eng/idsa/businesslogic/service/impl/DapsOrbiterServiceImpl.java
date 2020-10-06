package it.eng.idsa.businesslogic.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import it.eng.idsa.businesslogic.service.DapsService;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * @author Milan Karajovic and Gabriele De Luca
 */

/**
 * Service Implementation for managing DAPS.
 */
@ConditionalOnProperty(name = "application.dapsVersion", havingValue = "orbiter")
@Service
@Transactional
public class DapsOrbiterServiceImpl implements DapsService {

    private static final Logger logger = LogManager.getLogger(DapsOrbiterServiceImpl.class);
    public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private String token = "";
    @Value("${application.targetDirectory}")
    private Path targetDirectory;
    @Value("${application.dapsUrl}")
    private String dapsUrl;
    @Value("${application.keyStoreName}")
    private String keyStoreName;
    @Value("${application.keyStorePassword}")
    private String keyStorePassword;
    @Value("${application.keystoreAliasName}")
    private String keystoreAliasName;
    @Value("${application.connectorUUID}")
    private String connectorUUID;
    @Value("${application.dapsJWKSUrl}")
    private String dapsJWKSUrl;

    @Value("${application.daps.orbiter.privateKey}")
    private String dapsOrbiterPrivateKey;
    @Value("${application.daps.orbiter.certificate}")
    private String dapsOrbiterCertificate; 
    @Value("${application.daps.orbiter.password}")
    private String dapsOrbiterPassword;


    @Override
    public String getJwtToken() {

        String targetAudience = "idsc:IDS_CONNECTORS_ALL";

        // Try clause for setup phase (loading keys, building trust manager)
        try {
            InputStream jksKeyStoreInputStream =
                    Files.newInputStream(targetDirectory.resolve(keyStoreName));
            InputStream jksTrustStoreInputStream =
                    Files.newInputStream(targetDirectory.resolve(keyStoreName));

            KeyStore keystore = KeyStore.getInstance("JKS");
            KeyStore trustManagerKeyStore = KeyStore.getInstance("JKS");

            logger.info("Loading key store: " + keyStoreName);
            logger.info("Loading trust store: " + keyStoreName);
            keystore.load(jksKeyStoreInputStream, keyStorePassword.toCharArray());
            trustManagerKeyStore.load(jksTrustStoreInputStream, keyStorePassword.toCharArray());
            java.security.cert.Certificate[] certs = trustManagerKeyStore.getCertificateChain("ca");
            logger.info("Cert chain: " + Arrays.toString(certs));

            logger.info("LOADED CA CERT: " + trustManagerKeyStore.getCertificate("ca"));
            jksKeyStoreInputStream.close();
            jksTrustStoreInputStream.close();

            // get private key
            Key privKey = keystore.getKey(keystoreAliasName, keyStorePassword.toCharArray());
            // Get certificate of public key
            X509Certificate cert = (X509Certificate) keystore.getCertificate(keystoreAliasName);

            TrustManager[] trustManagers;
            try {
                TrustManagerFactory trustManagerFactory =
                        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                trustManagerFactory.init(trustManagerKeyStore);
                trustManagers = trustManagerFactory.getTrustManagers();
                if (trustManagers.length != 1 || !(trustManagers[0] instanceof X509TrustManager)) {
                    throw new IllegalStateException(
                            "Unexpected default trust managers:" + Arrays.toString(trustManagers));
                }
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, trustManagers, null);
                sslContext.getSocketFactory();
            } catch (GeneralSecurityException e) {
                throw new RuntimeException(e);
            }

            OkHttpClient client = null;
            final TrustManager[] trustAllCerts = createTrustCertificates();
            // Install the all-trusting trust manager
            final SSLSocketFactory sslSocketFactory = sslSocketFactory(trustAllCerts);
            client = createHttpClient(trustAllCerts, sslSocketFactory);

            logger.info("ConnectorUUID: " + connectorUUID);
            logger.info("Retrieving Dynamic Attribute Token...");

            // create signed JWT (JWS)
            // Create expiry date one day (86400 seconds) from now
            Date expiryDate = Date.from(Instant.now().plusSeconds(86400));
            JwtBuilder jwtb =
                    Jwts.builder()
                            .claim("id", connectorUUID)
                            .setExpiration(expiryDate)
                            .setIssuedAt(Date.from(Instant.now()))
                            .setAudience(targetAudience)
                            .setNotBefore(Date.from(Instant.now()));
            //String jws = jwtb.signWith(privKey, SignatureAlgorithm.RS256).compact();
            String jws = jwtb.signWith(SignatureAlgorithm.RS256, getOrbiterPrivateKey()).compact();
            logger.info("Request token: " + jws);

            // build form body to embed client assertion into post request
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("grant_type", "client_credentials");
            jsonObject.put("client_assertion_type", "jwt-bearer");
            jsonObject.put("client_assertion", jws);
            jsonObject.put("scope", "all");
            String jsonString = jsonObject.toString();
            RequestBody formBody = RequestBody.create(JSON, jsonString); // new

            Request requestDaps = new Request.Builder().url(dapsUrl)
                    .header("Host", "ecc-consumer")
                    .header("accept", "application/json")
                    .header("Content-Type", "application/json")
                    .post(formBody).build();

            Response jwtResponse = client.newCall(requestDaps).execute();
            if (!jwtResponse.isSuccessful()) {
                throw new IOException("Unexpected code " + jwtResponse);
            }
            var responseBody = jwtResponse.body();
            if (responseBody == null) {
                throw new Exception("JWT response is null.");
            }
            var jwtString = responseBody.string();
            logger.info("Response body of token request:\n{}", jwtString);
            ObjectNode node = new ObjectMapper().readValue(jwtString, ObjectNode.class);

            if (node.has("response")) {
                token = node.get("response").asText();
                logger.info("access_token: {}", token.toString());
            }
            logger.info("access_token: {}", jwtResponse.toString());
            logger.info("access_token: {}", jwtString);
            logger.info("access_token: {}", jwtResponse.message());

            if (!jwtResponse.isSuccessful()) {
                throw new IOException("Unexpected code " + jwtResponse);
            }
        } catch (KeyStoreException
                | NoSuchAlgorithmException
                | CertificateException
                | UnrecoverableKeyException e) {
            logger.error("Cannot acquire token:", e);
        } catch (IOException e) {
            logger.error("Error retrieving token:", e);
        } catch (Exception e) {
            logger.error("Something else went wrong:", e);
        }
        //settings.setDynamicAttributeToken(dynamicAttributeToken);
        return token;
    }


	private SSLSocketFactory sslSocketFactory(final TrustManager[] trustAllCerts)
			throws NoSuchAlgorithmException, KeyManagementException {
		final SSLContext sslContext = SSLContext.getInstance("SSL");
		sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
		// Create an ssl socket factory with our all-trusting manager
		final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
		return sslSocketFactory;
	}


	private TrustManager[] createTrustCertificates() {
		final TrustManager[] trustAllCerts = new TrustManager[]{
		        new X509TrustManager() {
		            @Override
		            public void checkClientTrusted(java.security.cert.X509Certificate[] chain,
		                                           String authType) throws CertificateException {
		            }

		            @Override
		            public void checkServerTrusted(java.security.cert.X509Certificate[] chain,
		                                           String authType) throws CertificateException {
		            }

		            @Override
		            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
		                return new java.security.cert.X509Certificate[0];
		            }
		        }
		};
		return trustAllCerts;
	}


	private OkHttpClient createHttpClient(final TrustManager[] trustAllCerts, final SSLSocketFactory sslSocketFactory) {
		OkHttpClient client;
		//@formatter:off
		client = new OkHttpClient.Builder()
				.connectTimeout(60, TimeUnit.SECONDS)
		        .writeTimeout(60, TimeUnit.SECONDS)
		        .readTimeout(60, TimeUnit.SECONDS)
		        .sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0])
		        .hostnameVerifier(new HostnameVerifier() {
		            @Override
		            public boolean verify(String hostname, SSLSession session) {
		                return true;
		            }
		        }).build();
		//@formatter:on
		return client;
	}


    @Override
    /**
     * Send request towards Orbiter to validate if token is correct
     */
    public boolean validateToken(String tokenValue) {
        boolean isValid = false;

        logger.debug("Validating Orbiter token");
        OkHttpClient client = null;
        
		try {

			JSONObject jsonObject = new JSONObject();
            jsonObject.put("token", tokenValue);
            String jsonString = jsonObject.toString();
            RequestBody formBody = RequestBody.create(JSON, jsonString); // new
	            
			//@formatter:off
			Request requestDaps = new Request.Builder()
					.url(dapsUrl + "/validate")
					.header("Host", "ecc-consumer")
					.header("accept", "application/json")
					.header("Content-Type", "application/json")
					.post(formBody)
					.build();
			//@formatter:on
			
			final TrustManager[] trustAllCerts = createTrustCertificates();
			final SSLSocketFactory sslSocketFactory = sslSocketFactory(trustAllCerts);
			client = createHttpClient(trustAllCerts, sslSocketFactory);

			Response jwtResponse = client.newCall(requestDaps).execute();
			
			ResponseBody responseBody = jwtResponse.body();
			String response = responseBody.string();
			if (!jwtResponse.isSuccessful()) {
				logger.warn("Token did not validated successfuly", jwtResponse);
				throw new IOException("Error calling validate token." + jwtResponse);
			}
			
			logger.info("Response body of validate token request:\n{}", response);
			// parse body and check if content is like following
//			{
//			    "response": true,
//			    "description": "Token successfully validated"
//			}
//			otherwise we will get 'invalid token'
			try {
				ObjectNode node = new ObjectMapper().readValue(response, ObjectNode.class);
				if(node.has("response") && node.get("response").asBoolean()) {
					logger.info("Token successfuly validated - signature OK");
					isValid = true;
				}
			} catch ( JsonProcessingException ex) {
				logger.info("Token was not validated correct");
			}
		} catch (Exception e) {
			logger.error(e);
		}
        return isValid;
    }
    
    // Build the public key from modulus and exponent
//    public static PublicKey getPublicKey(String modulusB64u, String exponentB64u)
//            throws NoSuchAlgorithmException, InvalidKeySpecException {
//        // conversion to BigInteger. I have transformed to Hex because new
//        // BigDecimal(byte) does not work for me
//        byte exponentB[] = Base64.getUrlDecoder().decode(exponentB64u);
//        byte modulusB[] = Base64.getUrlDecoder().decode(modulusB64u);
//        BigInteger exponent = new BigInteger(toHexFromBytes(exponentB), 16);
//        BigInteger modulus = new BigInteger(toHexFromBytes(modulusB), 16);
//
//        // Build the public key
//        RSAPublicKeySpec spec = new RSAPublicKeySpec(modulus, exponent);
//        KeyFactory factory = KeyFactory.getInstance("RSA");
//        PublicKey pub = factory.generatePublic(spec);
//
//        return pub;
//    }

//    private static String toHexFromBytes(byte[] bytes) {
//        StringBuffer rc = new StringBuffer(bytes.length * 2);
//        for (int i = 0; i < bytes.length; i++) {
//            rc.append(HEX_TABLE[0xFF & bytes[i]]);
//        }
//        return rc.toString();
//    }

//    public Certificate getCert() {
//        return cert;
//    }
//
//    public void setCert(Certificate cert) {
//        this.cert = cert;
//    }

//    private static final String[] HEX_TABLE = new String[]{"00", "01", "02", "03", "04", "05", "06", "07", "08", "09",
//            "0a", "0b", "0c", "0d", "0e", "0f", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "1a", "1b",
//            "1c", "1d", "1e", "1f", "20", "21", "22", "23", "24", "25", "26", "27", "28", "29", "2a", "2b", "2c", "2d",
//            "2e", "2f", "30", "31", "32", "33", "34", "35", "36", "37", "38", "39", "3a", "3b", "3c", "3d", "3e", "3f",
//            "40", "41", "42", "43", "44", "45", "46", "47", "48", "49", "4a", "4b", "4c", "4d", "4e", "4f", "50", "51",
//            "52", "53", "54", "55", "56", "57", "58", "59", "5a", "5b", "5c", "5d", "5e", "5f", "60", "61", "62", "63",
//            "64", "65", "66", "67", "68", "69", "6a", "6b", "6c", "6d", "6e", "6f", "70", "71", "72", "73", "74", "75",
//            "76", "77", "78", "79", "7a", "7b", "7c", "7d", "7e", "7f", "80", "81", "82", "83", "84", "85", "86", "87",
//            "88", "89", "8a", "8b", "8c", "8d", "8e", "8f", "90", "91", "92", "93", "94", "95", "96", "97", "98", "99",
//            "9a", "9b", "9c", "9d", "9e", "9f", "a0", "a1", "a2", "a3", "a4", "a5", "a6", "a7", "a8", "a9", "aa", "ab",
//            "ac", "ad", "ae", "af", "b0", "b1", "b2", "b3", "b4", "b5", "b6", "b7", "b8", "b9", "ba", "bb", "bc", "bd",
//            "be", "bf", "c0", "c1", "c2", "c3", "c4", "c5", "c6", "c7", "c8", "c9", "ca", "cb", "cc", "cd", "ce", "cf",
//            "d0", "d1", "d2", "d3", "d4", "d5", "d6", "d7", "d8", "d9", "da", "db", "dc", "dd", "de", "df", "e0", "e1",
//            "e2", "e3", "e4", "e5", "e6", "e7", "e8", "e9", "ea", "eb", "ec", "ed", "ee", "ef", "f0", "f1", "f2", "f3",
//            "f4", "f5", "f6", "f7", "f8", "f9", "fa", "fb", "fc", "fd", "fe", "ff",};

    /***
     * Split string ever len chars and return string array
     * @param src
     * @param len
     * @return
     */
//    public static String[] split(String src, int len) {
//        String[] result = new String[(int) Math.ceil((double) src.length() / (double) len)];
//        for (int i = 0; i < result.length; i++)
//            result[i] = src.substring(i * len, Math.min(src.length(), (i + 1) * len));
//        return result;
//    }

    /***
     * Beautyfies Hex strings and will generate a result later used to create the client id (XX:YY:ZZ)
     * @param hexString HexString to be beautified
     * @return beautifiedHex result
     */
//    public String beatifyHex(String hexString) {
//        String[] splitString = split(hexString, 2);
//        StringBuffer sb = new StringBuffer();
//        for (int i = 0; i < splitString.length; i++) {
//            sb.append(splitString[i]);
//            sb.append(":");
//        }
//        return sb.toString();
//    }

    /**
     * Convert byte array to hex without any dependencies to libraries.
     *
     * @param num
     * @return
     */
//    public String byteToHex(byte num) {
//        char[] hexDigits = new char[2];
//        hexDigits[0] = Character.forDigit((num >> 4) & 0xF, 16);
//        hexDigits[1] = Character.forDigit((num & 0xF), 16);
//        return new String(hexDigits);
//    }

    /**
     * Encode a byte array to an hex string
     *
     * @param byteArray
     * @return
     */
//    public String encodeHexString(byte[] byteArray) {
//        StringBuffer hexStringBuffer = new StringBuffer();
//        for (int i = 0; i < byteArray.length; i++) {
//            hexStringBuffer.append(byteToHex(byteArray[i]));
//        }
//        return hexStringBuffer.toString();
//    }
//    
	/**
	 * Reads Orbiter private key from file, removes header and footer and creates java PrivateKey object
	 * @return
	 * @throws IOException
	 * @throws GeneralSecurityException
	 */
	private PrivateKey getOrbiterPrivateKey() throws IOException, GeneralSecurityException {
		InputStream orbiterPrivateKeyInputStream = null;
		try {
			orbiterPrivateKeyInputStream = Files.newInputStream(targetDirectory.resolve(dapsOrbiterPrivateKey));
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