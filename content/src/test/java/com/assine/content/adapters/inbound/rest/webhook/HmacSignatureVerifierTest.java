package com.assine.content.adapters.inbound.rest.webhook;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class HmacSignatureVerifierTest {

    private static final String SECRET = "super-secret";
    private static final byte[] BODY =
            "{\"page_id\":\"abc-123\"}".getBytes(StandardCharsets.UTF_8);

    private static String hmacHex(String secret, byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body));
    }

    @Test
    void verifiesCorrectSignatureWithoutPrefix() throws Exception {
        String sig = hmacHex(SECRET, BODY);
        assertThat(HmacSignatureVerifier.verify(SECRET, BODY, sig)).isTrue();
    }

    @Test
    void verifiesCorrectSignatureWithSha256Prefix() throws Exception {
        String sig = "sha256=" + hmacHex(SECRET, BODY);
        assertThat(HmacSignatureVerifier.verify(SECRET, BODY, sig)).isTrue();
    }

    @Test
    void rejectsSignatureFromDifferentSecret() throws Exception {
        String sig = hmacHex("other-secret", BODY);
        assertThat(HmacSignatureVerifier.verify(SECRET, BODY, sig)).isFalse();
    }

    @Test
    void rejectsTamperedBody() throws Exception {
        String sig = hmacHex(SECRET, BODY);
        byte[] tampered = "{\"page_id\":\"abc-124\"}".getBytes(StandardCharsets.UTF_8);
        assertThat(HmacSignatureVerifier.verify(SECRET, tampered, sig)).isFalse();
    }

    @Test
    void rejectsMalformedHex() {
        assertThat(HmacSignatureVerifier.verify(SECRET, BODY, "not-hex-zz")).isFalse();
        assertThat(HmacSignatureVerifier.verify(SECRET, BODY, "abc")).isFalse(); // odd length
    }

    @Test
    void rejectsNullArgs() {
        assertThat(HmacSignatureVerifier.verify(null, BODY, "deadbeef")).isFalse();
        assertThat(HmacSignatureVerifier.verify(SECRET, null, "deadbeef")).isFalse();
        assertThat(HmacSignatureVerifier.verify(SECRET, BODY, null)).isFalse();
    }

    @Test
    void isConstantTimeEqualIgnoresCaseOfHex() throws Exception {
        // MessageDigest.isEqual is byte-exact; our hex parser is case-insensitive via Character.digit.
        String sig = hmacHex(SECRET, BODY).toUpperCase();
        assertThat(HmacSignatureVerifier.verify(SECRET, BODY, sig)).isTrue();
    }
}
