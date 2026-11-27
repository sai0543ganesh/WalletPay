package com.walletpay.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.walletpay.exception.ApiException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class WebhookSignatureTest {
    private static final String SECRET = "unit-test-key-not-for-production-0123456789";
    private static final Instant NOW = Instant.ofEpochSecond(1_800_000_000);

    static String sign(String timestamp, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of()
                .formatHex(mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void acceptsAuthenticCurrentPayload() throws Exception {
        String ts = Long.toString(NOW.getEpochSecond());
        assertThatCode(() -> WebhookService.authenticate(ts, sign(ts, "{}"), "{}", SECRET, NOW))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsChangedPayload() throws Exception {
        String ts = Long.toString(NOW.getEpochSecond());
        assertThatThrownBy(
                        () ->
                                WebhookService.authenticate(
                                        ts, sign(ts, "{}"), "{\"x\":1}", SECRET, NOW))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsStalePayload() throws Exception {
        String ts = Long.toString(NOW.minusSeconds(301).getEpochSecond());
        assertThatThrownBy(() -> WebhookService.authenticate(ts, sign(ts, "{}"), "{}", SECRET, NOW))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsFuturePayload() throws Exception {
        String ts = Long.toString(NOW.plusSeconds(301).getEpochSecond());
        assertThatThrownBy(() -> WebhookService.authenticate(ts, sign(ts, "{}"), "{}", SECRET, NOW))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsMalformedSignature() {
        assertThatThrownBy(
                        () ->
                                WebhookService.authenticate(
                                        "1800000000", "invalid", "{}", SECRET, NOW))
                .isInstanceOf(ApiException.class);
    }
}
