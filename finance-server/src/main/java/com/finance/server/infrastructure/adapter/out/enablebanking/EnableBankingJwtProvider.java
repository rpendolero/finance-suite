package com.finance.server.infrastructure.adapter.out.enablebanking;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

@RequiredArgsConstructor
public final class EnableBankingJwtProvider {
    private final String applicationId;
    private final PrivateKey privateKey;
    private final ObjectMapper mapper;

    public static EnableBankingJwtProvider of(String applicationId, String pemPrivateKey, ObjectMapper mapper) {
        if (applicationId == null || applicationId.isBlank())
            throw new IllegalArgumentException("Enable Banking application id is required");
        return new EnableBankingJwtProvider(applicationId, parsePrivateKey(pemPrivateKey), mapper);
    }

    public String token() {
        try {
            long now = Instant.now().getEpochSecond();
            String header = encode(mapper.writeValueAsBytes(Map.of("typ", "JWT", "alg", "RS256", "kid", applicationId)));
            String payload = encode(mapper.writeValueAsBytes(Map.of("iss", "enablebanking.com", "aud", "api.enablebanking.com", "iat", now, "exp", now + 3600)));
            String unsigned = header + "." + payload;
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(unsigned.getBytes(StandardCharsets.US_ASCII));
            return unsigned + "." + encode(signature.sign());
        } catch (Exception e) {
            throw new IllegalStateException("Could not create Enable Banking JWT", e);
        }
    }

    private String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static PrivateKey parsePrivateKey(String pem) {
        if (pem == null || pem.isBlank()) throw new IllegalArgumentException("Enable Banking private key is required");
        try {
            String value = pem.replace("\\n", "\n").replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "").replaceAll("\\s", "");
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(value)));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid PKCS#8 RSA private key", e);
        }
    }
}
