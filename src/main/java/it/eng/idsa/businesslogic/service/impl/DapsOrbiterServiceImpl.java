package it.eng.idsa.businesslogic.service.impl;

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
import it.eng.idsa.businesslogic.service.DapsService;
import it.eng.idsa.businesslogic.util.ProxyAuthenticator;
import okhttp3.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.net.ssl.*;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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

    private static final Logger logger = LogManager.getLogger(DapsServiceImpl.class);
    public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private Key privKey;
    private Certificate cert;

    private String token = "";
    private SSLSocketFactory sslSocketFactory = null;

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
    @Value("${application.proxyUser}")
    private String proxyUser;
    @Value("${application.proxyPassword}")
    private String proxyPassword;
    @Value("${application.proxyHost}")
    private String proxyHost;
    @Value("${application.proxyPort}")
    private String proxyPort;
    @Value("${application.dapsJWKSUrl}")
    private String dapsJWKSUrl;

    @Value("${application.dapsJws}")
    private String dapsJws;

    @Override

    public String getJwtToken() {

        String dynamicAttributeToken = "INVALID_TOKEN";
        String targetAudience = "idsc:IDS_CONNECTORS_ALL";

        Map<String, Object> jwtClaims = null;

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
            Certificate[] certs = trustManagerKeyStore.getCertificateChain("ca");
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
                sslSocketFactory = sslContext.getSocketFactory();
            } catch (GeneralSecurityException e) {
                throw new RuntimeException(e);
            }

            Authenticator proxyAuthenticator = new Authenticator() {
                @Override
                public Request authenticate(Route route, Response response) throws IOException {
                    String credential = Credentials.basic(proxyUser, proxyPassword);
                    return response.request().newBuilder().header("Proxy-Authorization", credential).build();
                }
            };

            OkHttpClient client = null;
            final TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain,
                                                       String authType) throws CertificateException {
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain,
                                                       String authType) throws CertificateException {
                        }

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                    }
            };
            // Install the all-trusting trust manager
            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new SecureRandom());
            // Create an ssl socket factory with our all-trusting manager
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
            if (!proxyUser.equalsIgnoreCase("")) {
                client = new OkHttpClient.Builder().connectTimeout(60, TimeUnit.SECONDS)
                        .writeTimeout(60, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS)
                        .proxy(new Proxy(Proxy.Type.HTTP,
                                new InetSocketAddress(proxyHost, Integer.parseInt(proxyPort))))
                        .proxyAuthenticator(proxyAuthenticator)
                        .sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0])
                        .hostnameVerifier(new HostnameVerifier() {
                            @Override
                            public boolean verify(String hostname, SSLSession session) {
                                // TODO Auto-generated method stub
                                return true;
                            }
                        }).build();
            } else {
                client = new OkHttpClient.Builder().connectTimeout(60, TimeUnit.SECONDS)
                        .writeTimeout(60, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS)
                        .sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0])
                        .hostnameVerifier(new HostnameVerifier() {
                            @Override
                            public boolean verify(String hostname, SSLSession session) {
                                return true;
                            }
                        }).build();
            }

            // Get AKI
            //GET 2.5.29.14	SubjectKeyIdentifier / 2.5.29.35	AuthorityKeyIdentifier
            /*String aki_oid = Extension.authorityKeyIdentifier.getId();
            byte[] rawAuthorityKeyIdentifier = cert.getExtensionValue(aki_oid);
            ASN1OctetString akiOc = ASN1OctetString.getInstance(rawAuthorityKeyIdentifier);
            AuthorityKeyIdentifier aki = AuthorityKeyIdentifier.getInstance(akiOc.getOctets());
            byte[] authorityKeyIdentifier = aki.getKeyIdentifier();

            //GET SKI
            String ski_oid = Extension.subjectKeyIdentifier.getId();
            byte[] rawSubjectKeyIdentifier = cert.getExtensionValue(ski_oid);
            ASN1OctetString ski0c = ASN1OctetString.getInstance(rawSubjectKeyIdentifier);
            SubjectKeyIdentifier ski = SubjectKeyIdentifier.getInstance(ski0c.getOctets());
            byte[] subjectKeyIdentifier = ski.getKeyIdentifier();

            String aki_result = beatifyHex(encodeHexString(authorityKeyIdentifier).toUpperCase());
            String ski_result = beatifyHex(encodeHexString(subjectKeyIdentifier).toUpperCase());

            String connectorUUID = ski_result + "keyid:" + aki_result.substring(0, aki_result.length() - 1);
            */
            logger.info("ConnectorUUID: " + connectorUUID);
            logger.info("Retrieving Dynamic Attribute Token...");


            // create signed JWT (JWS)
            // Create expiry date one day (86400 seconds) from now
            //Date expiryDate = Date.from(Instant.now().plusSeconds(86400));
            /*JwtBuilder jwtb =
                    Jwts.builder()
                            .setIssuer(connectorUUID)
                            .setSubject(connectorUUID)
                            .claim("@context", "https://w3id.org/idsa/contexts/context.jsonld")
                            .claim("@type", "ids:DatRequestToken")
                            .setExpiration(expiryDate)
                            .setIssuedAt(Date.from(Instant.now()))
                            .setAudience(targetAudience)
                            .setNotBefore(Date.from(Instant.now()));*/
            //String jws = jwtb.signWith(privKey, SignatureAlgorithm.RS256).compact();
            //String jws = jwtb.signWith(SignatureAlgorithm.RS256, privKey).compact();
            logger.info("Request token: " + dapsJws);

            // build form body to embed client assertion into post request
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("grant_type", "client_credentials");
            jsonObject.put("client_assertion_type", "jwt-bearer");
            jsonObject.put("client_assertion", dapsJws);
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

            if (!jwtResponse.isSuccessful())
                throw new IOException("Unexpected code " + jwtResponse);

            //JSONObject jsonObject = new JSONObject(jwtString);
            //dynamicAttributeToken = jsonObject.getString("access_token");

            //logger.info("Dynamic Attribute Token: " + dynamicAttributeToken);

            // jwtClaims = verifyJWT(dynamicAttributeToken, dapsUrl);
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
            if (!proxyUser.equalsIgnoreCase("")) {
                System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "");
                System.setProperty("jdk.http.auth.proxying.disabledSchemes", "");
                ProxyAuthenticator proxyAuthenticator = new ProxyAuthenticator(proxyUser, proxyPassword);
                java.net.Authenticator.setDefault(proxyAuthenticator);
                Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, Integer.parseInt(proxyPort)));
                publicKeys = JWKSet.load(new URL(dapsJWKSUrl), 0, 0, 0, proxy);
            } else {
                publicKeys = JWKSet.load(new URL(dapsJWKSUrl));
            }
            RSAKey key = (RSAKey) publicKeys.getKeyByKeyId("sqs.es"); //default in AISEC version

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
            e.printStackTrace();
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

    /***
     * Split string ever len chars and return string array
     * @param src
     * @param len
     * @return
     */
    public static String[] split(String src, int len) {
        String[] result = new String[(int) Math.ceil((double) src.length() / (double) len)];
        for (int i = 0; i < result.length; i++)
            result[i] = src.substring(i * len, Math.min(src.length(), (i + 1) * len));
        return result;
    }

    /***
     * Beautyfies Hex strings and will generate a result later used to create the client id (XX:YY:ZZ)
     * @param hexString HexString to be beautified
     * @return beautifiedHex result
     */
    public String beatifyHex(String hexString) {
        String[] splitString = split(hexString, 2);
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < splitString.length; i++) {
            sb.append(splitString[i]);
            sb.append(":");
        }
        return sb.toString();
    }

    /**
     * Convert byte array to hex without any dependencies to libraries.
     *
     * @param num
     * @return
     */
    public String byteToHex(byte num) {
        char[] hexDigits = new char[2];
        hexDigits[0] = Character.forDigit((num >> 4) & 0xF, 16);
        hexDigits[1] = Character.forDigit((num & 0xF), 16);
        return new String(hexDigits);
    }

    /**
     * Encode a byte array to an hex string
     *
     * @param byteArray
     * @return
     */
    public String encodeHexString(byte[] byteArray) {
        StringBuffer hexStringBuffer = new StringBuffer();
        for (int i = 0; i < byteArray.length; i++) {
            hexStringBuffer.append(byteToHex(byteArray[i]));
        }
        return hexStringBuffer.toString();
    }

}