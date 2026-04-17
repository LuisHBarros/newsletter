package com.assine.content.adapters.inbound.rest.webhook;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** HMAC-SHA256 verifier for Notion webhook deliveries. */
public final class HmacSignatureVerifier {

    private HmacSignatureVerifier() {}

    /**
     * @param secret        HMAC shared secret (from Secrets Manager)
     * @param body          raw request body bytes
     * @param headerValue   value of the {@code Notion-Signature} / {@code X-Notion-Signature} header;
     *                      may be prefixed with {@code sha256=}. Hex-encoded lowercase.
     */
    public static boolean verify(String secret, byte[] body, String headerValue) {
        if (secret == null || headerValue == null || body == null) return false;
        String sent = headerValue.startsWith("sha256=") ? headerValue.substring(7) : headerValue;

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(body);
            byte[] received = hexToBytes(sent);
            return received != null && MessageDigest.isEqual(expected, received);
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] hexToBytes(String hex) {
        if (hex == null || (hex.length() & 1) != 0) return null;
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(hex.charAt(i * 2), 16);
            int lo = Character.digit(hex.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) return null;
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }
}
