package it.eng.idsa.businesslogic.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import it.eng.idsa.businesslogic.service.DapsService;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * @author Milan Karajovic and Gabriele De Luca
 */

/**
 * Service Implementation for managing DAPS.
 */
@ConditionalOnProperty(name="application.dapsVersion", havingValue="v1")
@Service
@Transactional
public class DapsServiceImpl implements DapsService {

    private static final Logger logger = LogManager.getLogger(DapsServiceImpl.class);

   

    private Key privKey;
    private Certificate cert;

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

    @Override
    public String getJwtToken() {

        logger.debug("Get properties");

        try {
            logger.debug("Started get JWT token");
			InputStream jksInputStream = Files.newInputStream(targetDirectory.resolve(keyStoreName));
            KeyStore store = KeyStore.getInstance("JKS");
            store.load(jksInputStream, keyStorePassword.toCharArray());
            // get private key
            privKey = (PrivateKey) store.getKey(keystoreAliasName, keyStorePassword.toCharArray());
            // Get certificate of public key
            setCert(store.getCertificate(keystoreAliasName));

            //byte[] encodedPublicKey = publicKey.getEncoded();
            //String b64PublicKey = Base64.getEncoder().encodeToString(encodedPublicKey);

            // create signed JWT (JWS)
            // Create expiry date one day (86400 seconds) from now
            Date expiryDate = Date.from(Instant.now().plusSeconds(86400));
            JwtBuilder jwtb = Jwts.builder().setIssuer(connectorUUID).setSubject(connectorUUID)
                    .setExpiration(expiryDate).setIssuedAt(Date.from(Instant.now()))
                    .setAudience("https://api.localhost").setNotBefore(Date.from(Instant.now()));

            String jws = jwtb.signWith(SignatureAlgorithm.RS256, privKey).compact();
            /*
             * String json = "{\"\": \"\"," +
             * "\"\":\"urn:ietf:params:oauth:client-assertion-type:jwt-bearer\"," +
             * "\"\":\"" + jws + "\"," + "\"\":\"\"," + "}";
             */
           
            OkHttpClient client = null;
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

            // Install the all-trusting trust manager
            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            // Create an ssl socket factory with our all-trusting manager
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            
            
                client = new OkHttpClient.Builder().connectTimeout(60, TimeUnit.SECONDS)
                        .writeTimeout(60, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS)
                        .sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0])
                        .hostnameVerifier(new HostnameVerifier() {
                            @Override
                            public boolean verify(String hostname, SSLSession session) {
                                return true;
                            }
                        }).build();
            
            RequestBody formBody = new FormBody.Builder().add("grant_type", "client_credentials")
                    .add("client_assertion_type", "urn:ietf:params:oauth:client-assertion-type:jwt-bearer")
                    .add("client_assertion", jws).build();

            Request requestDaps = new Request.Builder().url(dapsUrl).post(formBody).build();
            Response responseDaps = client.newCall(requestDaps).execute();

            String body = responseDaps.body().string();
            ObjectNode node = new ObjectMapper().readValue(body, ObjectNode.class);

            if (node.has("access_token")) {
                token = node.get("access_token").asText();
                logger.info("access_token: {}", () -> token.toString());
            }
            logger.info("access_token: {}", () -> responseDaps.toString());
            logger.info("access_token: {}", () -> body);
            logger.info("access_token: {}", () -> responseDaps.message());

            if (!responseDaps.isSuccessful())
                throw new IOException("Unexpected code " + responseDaps);

        } catch (IOException |KeyStoreException | NoSuchAlgorithmException | 
        		CertificateException |KeyManagementException | UnrecoverableKeyException e) {
        	logger.error(e);
            return null;
        	}
        return token;
    }

    @Override
    public boolean validateToken(String tokenValue) {
        boolean isValid = false;

        logger.debug("Get properties");

        try {
            // Set up a JWT processor to parse the tokens and then check their signature
            // and validity time window (bounded by the "iat", "nbf" and "exp" claims)
            ConfigurableJWTProcessor<SecurityContext> jwtProcessor = new DefaultJWTProcessor<SecurityContext>();

            // The public RSA keys to validate the signatures will be sourced from the
            // OAuth 2.0 server's JWK set, published at a well-known URL. The RemoteJWKSet
            // object caches the retrieved keys to speed up subsequent look-ups and can
            // also gracefully handle key-rollover
            JWKSource<SecurityContext> keySource = new RemoteJWKSet<SecurityContext>(
                    new URL(dapsJWKSUrl));

            // Load JWK set from URL
            JWKSet publicKeys = null;
           
                publicKeys = JWKSet.load(new URL(dapsJWKSUrl));
            
            RSAKey key = (RSAKey) publicKeys.getKeyByKeyId("default");

            // The expected JWS algorithm of the access tokens (agreed out-of-band)
            JWSAlgorithm expectedJWSAlg = JWSAlgorithm.RS256;

            // Configure the JWT processor with a key selector to feed matching public
            // RSA keys sourced from the JWK set URL
            JWSKeySelector<SecurityContext> keySelector = new JWSVerificationKeySelector<SecurityContext>(
                    expectedJWSAlg, keySource);
            jwtProcessor.setJWSKeySelector(keySelector);

            // Validate signature
            String exponentB64u = key.getPublicExponent().toString();
            String modulusB64u = key.getModulus().toString();

            // Build the public key from modulus and exponent
            PublicKey publicKey = getPublicKey(modulusB64u, exponentB64u);

            // print key as PEM (base64 and headers)
            String publicKeyPEM = "-----BEGIN PUBLIC KEY-----\n"
                    + Base64.getEncoder().encodeToString(publicKey.getEncoded()) + "\n" + "-----END PUBLIC KEY-----";

            logger.debug("publicKeyPEM: {}", () -> publicKeyPEM);

            //get signed data and signature from JWT
            String signedData = tokenValue.substring(0, tokenValue.lastIndexOf("."));
            String signatureB64u = tokenValue.substring(tokenValue.lastIndexOf(".") + 1, tokenValue.length());
            byte signature[] = Base64.getUrlDecoder().decode(signatureB64u);

            //verify Signature
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initVerify(publicKey);
            sig.update(signedData.getBytes());
            boolean v = sig.verify(signature);
            logger.debug("result_validation_signature = ", () -> v);

            if (v == false) {
                isValid = false;
            } else {
                // Process the token
                SecurityContext ctx = null; // optional context parameter, not required here
                JWTClaimsSet claimsSet = jwtProcessor.process(tokenValue, ctx);

                logger.debug("claimsSet = ", () -> claimsSet.toJSONObject());

                isValid = true;
            }

        } catch (Exception e) {
        	logger.error(e);
        }

        return isValid;
    }

    // Build the public key from modulus and exponent
    public static PublicKey getPublicKey(String modulusB64u, String exponentB64u)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        // conversion to BigInteger. I have transformed to Hex because new
        // BigDecimal(byte) does not work for me
        byte exponentB[] = Base64.getUrlDecoder().decode(exponentB64u);
        byte modulusB[] = Base64.getUrlDecoder().decode(modulusB64u);
        BigInteger exponent = new BigInteger(toHexFromBytes(exponentB), 16);
        BigInteger modulus = new BigInteger(toHexFromBytes(modulusB), 16);

        // Build the public key
        RSAPublicKeySpec spec = new RSAPublicKeySpec(modulus, exponent);
        KeyFactory factory = KeyFactory.getInstance("RSA");
        PublicKey pub = factory.generatePublic(spec);

        return pub;
    }

    private static String toHexFromBytes(byte[] bytes) {
        StringBuffer rc = new StringBuffer(bytes.length * 2);
        for (int i = 0; i < bytes.length; i++) {
            rc.append(HEX_TABLE[0xFF & bytes[i]]);
        }
        return rc.toString();
    }

    public Certificate getCert() {
		return cert;
	}

	public void setCert(Certificate cert) {
		this.cert = cert;
	}

	private static final String[] HEX_TABLE = new String[]{"00", "01", "02", "03", "04", "05", "06", "07", "08", "09",
            "0a", "0b", "0c", "0d", "0e", "0f", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "1a", "1b",
            "1c", "1d", "1e", "1f", "20", "21", "22", "23", "24", "25", "26", "27", "28", "29", "2a", "2b", "2c", "2d",
            "2e", "2f", "30", "31", "32", "33", "34", "35", "36", "37", "38", "39", "3a", "3b", "3c", "3d", "3e", "3f",
            "40", "41", "42", "43", "44", "45", "46", "47", "48", "49", "4a", "4b", "4c", "4d", "4e", "4f", "50", "51",
            "52", "53", "54", "55", "56", "57", "58", "59", "5a", "5b", "5c", "5d", "5e", "5f", "60", "61", "62", "63",
            "64", "65", "66", "67", "68", "69", "6a", "6b", "6c", "6d", "6e", "6f", "70", "71", "72", "73", "74", "75",
            "76", "77", "78", "79", "7a", "7b", "7c", "7d", "7e", "7f", "80", "81", "82", "83", "84", "85", "86", "87",
            "88", "89", "8a", "8b", "8c", "8d", "8e", "8f", "90", "91", "92", "93", "94", "95", "96", "97", "98", "99",
            "9a", "9b", "9c", "9d", "9e", "9f", "a0", "a1", "a2", "a3", "a4", "a5", "a6", "a7", "a8", "a9", "aa", "ab",
            "ac", "ad", "ae", "af", "b0", "b1", "b2", "b3", "b4", "b5", "b6", "b7", "b8", "b9", "ba", "bb", "bc", "bd",
            "be", "bf", "c0", "c1", "c2", "c3", "c4", "c5", "c6", "c7", "c8", "c9", "ca", "cb", "cc", "cd", "ce", "cf",
            "d0", "d1", "d2", "d3", "d4", "d5", "d6", "d7", "d8", "d9", "da", "db", "dc", "dd", "de", "df", "e0", "e1",
            "e2", "e3", "e4", "e5", "e6", "e7", "e8", "e9", "ea", "eb", "ec", "ed", "ee", "ef", "f0", "f1", "f2", "f3",
            "f4", "f5", "f6", "f7", "f8", "f9", "fa", "fb", "fc", "fd", "fe", "ff",};

}
