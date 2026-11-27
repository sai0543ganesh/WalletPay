package com.walletpay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.walletpay.dto.ApiModels.LoginRequest;
import com.walletpay.dto.ApiModels.PasswordRequest;
import com.walletpay.dto.ApiModels.RegisterRequest;
import com.walletpay.dto.ApiModels.TransferRequest;
import com.walletpay.dto.ApiModels.WebhookRequest;
import com.walletpay.exception.ApiException;
import com.walletpay.repository.WalletRepository;
import com.walletpay.service.AuthService;
import com.walletpay.service.WalletService;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "app.demo-funding-enabled=true")
@AutoConfigureMockMvc
class WalletApiIT {
    private static final String PASSWORD = "Strong-test-password-42";
    private static final String WEBHOOK_KEY =
            "integration-only-webhook-key-never-use-in-production-012345";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired ObjectMapper mapper;
    @Autowired AuthService auth;
    @Autowired WalletService wallets;
    @Autowired WalletRepository walletRepository;
    @Autowired com.walletpay.service.DemoFundsService demoFunds;
    @Autowired TransactionTemplate transactionTemplate;

    @BeforeEach
    void resetDisposableDatabase() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.getCatalog())
                    .as("Integration tests may reset only a dedicated *_test database")
                    .endsWith("_test");
        }
        jdbc.execute(
                "TRUNCATE TABLE webhook_events, audit_events, wallet_transactions, auth_sessions,"
                        + " wallets, app_users RESTART IDENTITY CASCADE");
    }

    private MvcResult register(String phone) throws Exception {
        return mvc.perform(
                        post("/api/v1/auth/register")
                                .with(realCsrf())
                                .contentType("application/json")
                                .content(
                                        mapper.writeValueAsString(
                                                new RegisterRequest(
                                                        "Test Person", phone, PASSWORD))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("fullName").value("Test Person"))
                .andReturn();
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor realCsrf() {
        return request -> {
            try {
                MvcResult result =
                        mvc.perform(get("/api/v1/auth/csrf"))
                                .andExpect(status().isOk())
                                .andReturn();
                var json = mapper.readTree(result.getResponse().getContentAsByteArray());
                request.addHeader(json.get("headerName").asText(), json.get("token").asText());
                var cookies = new java.util.ArrayList<Cookie>();
                if (request.getCookies() != null)
                    cookies.addAll(java.util.Arrays.asList(request.getCookies()));
                cookies.add(result.getResponse().getCookie("XSRF-TOKEN"));
                request.setCookies(cookies.toArray(Cookie[]::new));
                return request;
            } catch (Exception ex) {
                throw new IllegalStateException("Could not prepare real CSRF cookie", ex);
            }
        };
    }

    private Cookie session(MvcResult result) {
        return result.getResponse().getCookie("walletpay_session");
    }

    private long id(MvcResult result) {
        return mapper.readTree(result.getResponse().getContentAsByteArray()).get("id").asLong();
    }

    private void fund(long id, long amount) {
        jdbc.update("UPDATE wallets SET balance_minor = ? WHERE user_id = ?", amount, id);
    }

    private long balance(long id) {
        return jdbc.queryForObject(
                "SELECT balance_minor FROM wallets WHERE user_id = ?", Long.class, id);
    }

    private String transferBody(String phone, long amount, String note) {
        return mapper.writeValueAsString(new TransferRequest(phone, amount, note));
    }

    private String sign(String ts, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(WEBHOOK_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of()
                .formatHex(mac.doFinal((ts + "." + body).getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void registrationUsesHttpOnlyCookieAndCreatesZeroBalance() throws Exception {
        MvcResult result = register("9000000001");
        assertThat(session(result)).isNotNull();
        assertThat(session(result).isHttpOnly()).isTrue();
        mvc.perform(get("/api/v1/wallet").cookie(session(result)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("balanceMinor").value(0));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT password_hash FROM app_users WHERE id = ?",
                                String.class,
                                id(result)))
                .startsWith("$2")
                .isNotEqualTo(PASSWORD);
    }

    @Test
    void protectedApiRejectsAnonymousAndMalformedJwt() throws Exception {
        mvc.perform(get("/api/v1/wallet"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/v1/wallet").cookie(new Cookie("walletpay_session", "forged")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void csrfIsRequiredAndEndpointReturnsUsableHeader() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        MvcResult result =
                mvc.perform(get("/api/v1/auth/csrf"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("headerName").value("X-XSRF-TOKEN"))
                        .andReturn();
        String token =
                mapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
        mvc.perform(
                        post("/api/v1/auth/register")
                                .cookie(result.getResponse().getCookie("XSRF-TOKEN"))
                                .header("X-XSRF-TOKEN", token)
                                .contentType("application/json")
                                .content(
                                        mapper.writeValueAsString(
                                                new RegisterRequest(
                                                        "Real CSRF", "9000000002", PASSWORD))))
                .andExpect(status().isCreated());
    }

    @Test
    void noDemoPasswordBypassAndInvalidLoginIsGeneric() throws Exception {
        register("0987654321");
        mvc.perform(
                        post("/api/v1/auth/login")
                                .with(realCsrf())
                                .contentType("application/json")
                                .content(
                                        "{\"phone\":\"0987654321\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void validatesAmountsAndPreventsDirectCredit() throws Exception {
        var sender = register("9000000003");
        register("9000000004");
        for (long amount : new long[] {-1, 0, 100_000_001L}) {
            mvc.perform(
                            post("/api/v1/transfers")
                                    .cookie(session(sender))
                                    .with(realCsrf())
                                    .header("Idempotency-Key", UUID.randomUUID())
                                    .contentType("application/json")
                                    .content(transferBody("9000000004", amount, "invalid")))
                    .andExpect(status().isBadRequest());
        }
        assertThat(balance(id(sender))).isZero();
        mvc.perform(
                        post("/api/onramp/complete")
                                .cookie(session(sender))
                                .with(realCsrf())
                                .contentType("application/json")
                                .content("{\"amount\":100}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void transferIsAtomicIdempotentAndReceiptIsPrivate() throws Exception {
        var a = register("9000000010");
        var b = register("9000000011");
        var outsider = register("9000000012");
        fund(id(a), 10_000);
        String key = UUID.randomUUID().toString();
        String body = transferBody("9000000011", 2500, "Lunch");
        var first =
                mvc.perform(
                                post("/api/v1/transfers")
                                        .cookie(session(a))
                                        .with(realCsrf())
                                        .header("Idempotency-Key", key)
                                        .contentType("application/json")
                                        .content(body))
                        .andExpect(status().isCreated())
                        .andReturn();
        String txId = mapper.readTree(first.getResponse().getContentAsString()).get("id").asText();
        mvc.perform(
                        post("/api/v1/transfers")
                                .cookie(session(a))
                                .with(realCsrf())
                                .header("Idempotency-Key", key)
                                .contentType("application/json")
                                .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("id").value(txId));
        mvc.perform(
                        post("/api/v1/transfers")
                                .cookie(session(a))
                                .with(realCsrf())
                                .header("Idempotency-Key", key)
                                .contentType("application/json")
                                .content(transferBody("9000000011", 2501, "Lunch")))
                .andExpect(status().isConflict());
        assertThat(balance(id(a))).isEqualTo(7500);
        assertThat(balance(id(b))).isEqualTo(2500);
        mvc.perform(get("/api/v1/transactions/" + txId + "/receipt").cookie(session(b)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("direction").value("CREDIT"));
        mvc.perform(get("/api/v1/transactions/" + txId + "/receipt").cookie(session(outsider)))
                .andExpect(status().isNotFound());
    }

    @Test
    void insufficientFundsRollBackAllEffects() throws Exception {
        var a = register("9000000020");
        var b = register("9000000021");
        fund(id(a), 50);
        mvc.perform(
                        post("/api/v1/transfers")
                                .cookie(session(a))
                                .with(realCsrf())
                                .header("Idempotency-Key", UUID.randomUUID())
                                .contentType("application/json")
                                .content(transferBody("9000000021", 51, "Too much")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("INSUFFICIENT_BALANCE"));
        assertThat(balance(id(a))).isEqualTo(50);
        assertThat(balance(id(b))).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wallet_transactions", Long.class))
                .isZero();
    }

    @Test
    void transferFindsCountryPrefixedRecipientAndRetryDoesNotDebitTwice() throws Exception {
        var sender = register("+919000000050");
        var recipient = register("+919000000051");
        fund(id(sender), 10_000);
        String key = UUID.randomUUID().toString();
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(
                            post("/api/v1/transfers")
                                    .cookie(session(sender))
                                    .with(realCsrf())
                                    .header("Idempotency-Key", key)
                                    .contentType("application/json")
                                    .content(transferBody("9000000051", 6900, "Prefix regression")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("counterpartyPhone").value("+919000000051"));
        }
        assertThat(balance(id(sender))).isEqualTo(3100);
        assertThat(balance(id(recipient))).isEqualTo(6900);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM wallet_transactions WHERE type='TRANSFER'",
                                Long.class))
                .isEqualTo(1);
        mvc.perform(
                        post("/api/v1/transfers")
                                .cookie(session(sender))
                                .with(realCsrf())
                                .header("Idempotency-Key", UUID.randomUUID())
                                .contentType("application/json")
                                .content(transferBody("9000000050", 100, "Self")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("detail").value("You cannot transfer to your own wallet."));
        assertThat(balance(id(sender))).isEqualTo(3100);
    }

    @Test
    void transferAcceptsCountryCodeWhenRecipientRegisteredNationalNumber() throws Exception {
        var sender = register("9000000060");
        var recipient = register("9000000061");
        fund(id(sender), 1000);
        for (String phone : List.of("+919000000061", "919000000061")) {
            mvc.perform(
                            post("/api/v1/transfers")
                                    .cookie(session(sender))
                                    .with(realCsrf())
                                    .header("Idempotency-Key", UUID.randomUUID())
                                    .contentType("application/json")
                                    .content(transferBody(phone, 100, "Prefix")))
                    .andExpect(status().isCreated());
        }
        assertThat(balance(id(sender))).isEqualTo(800);
        assertThat(balance(id(recipient))).isEqualTo(200);
    }

    @Test
    void missingRecipientGivesSpecificErrorWithoutAnyDebit() throws Exception {
        var sender = register("9000000070");
        fund(id(sender), 10_000);
        mvc.perform(
                        post("/api/v1/transfers")
                                .cookie(session(sender))
                                .with(realCsrf())
                                .header("Idempotency-Key", UUID.randomUUID())
                                .contentType("application/json")
                                .content(transferBody("9000000071", 6900, "Missing")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("code").value("RECIPIENT_NOT_FOUND"));
        assertThat(balance(id(sender))).isEqualTo(10_000);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wallet_transactions", Long.class))
                .isZero();
    }

    @Test
    void signedWebhookCreditsOnceAndRejectsInvalidEvents() throws Exception {
        var a = register("9000000030");
        var intent =
                mvc.perform(
                                post("/api/v1/deposits")
                                        .cookie(session(a))
                                        .with(realCsrf())
                                        .header("Idempotency-Key", UUID.randomUUID())
                                        .contentType("application/json")
                                        .content("{\"amountMinor\":1000,\"provider\":\"SANDBOX\"}"))
                        .andExpect(status().isCreated())
                        .andReturn();
        assertThat(balance(id(a))).isZero();
        String deposit =
                mapper.readTree(intent.getResponse().getContentAsString()).get("id").asText();
        String ts = Long.toString(Instant.now().getEpochSecond());
        String body =
                mapper.writeValueAsString(
                        new WebhookRequest(
                                UUID.randomUUID(),
                                UUID.fromString(deposit),
                                1000,
                                "INR",
                                "COMPLETED"));
        mvc.perform(
                        post("/api/v1/webhooks/sandbox")
                                .header("X-Webhook-Timestamp", ts)
                                .header("X-Webhook-Signature", "00")
                                .contentType("application/json")
                                .content(body))
                .andExpect(status().isUnauthorized());
        for (int i = 0; i < 2; i++)
            mvc.perform(
                            post("/api/v1/webhooks/sandbox")
                                    .header("X-Webhook-Timestamp", ts)
                                    .header("X-Webhook-Signature", sign(ts, body))
                                    .contentType("application/json")
                                    .content(body))
                    .andExpect(status().isOk());
        assertThat(balance(id(a))).isEqualTo(1000);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM webhook_events", Long.class))
                .isEqualTo(1);
        mvc.perform(
                        post("/api/v1/webhooks/sandbox")
                                .header("X-Webhook-Timestamp", ts)
                                .header("X-Webhook-Signature", sign(ts, "null"))
                                .contentType("application/json")
                                .content("null"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void logoutRevokesCapturedJwt() throws Exception {
        var a = register("9000000040");
        Cookie stolen = session(a);
        mvc.perform(post("/api/v1/auth/logout").cookie(stolen).with(realCsrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/wallet").cookie(stolen)).andExpect(status().isUnauthorized());
    }

    @Test
    void passwordChangeRevokesEverySession() throws Exception {
        var a = register("9000000050");
        var second =
                mvc.perform(
                                post("/api/v1/auth/login")
                                        .with(realCsrf())
                                        .contentType("application/json")
                                        .content(
                                                mapper.writeValueAsString(
                                                        new LoginRequest("9000000050", PASSWORD))))
                        .andExpect(status().isOk())
                        .andReturn();
        mvc.perform(
                        put("/api/v1/profile/password")
                                .cookie(session(a))
                                .with(realCsrf())
                                .contentType("application/json")
                                .content(
                                        mapper.writeValueAsString(
                                                new PasswordRequest(
                                                        PASSWORD, "New-strong-password-42"))))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/wallet").cookie(session(second)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void historyFiltersPagesAndCsvAreConsistent() throws Exception {
        var a = register("9000000060");
        register("9000000061");
        fund(id(a), 2000);
        wallets.transfer(id(a), new TransferRequest("9000000061", 100, "=1+1"), UUID.randomUUID());
        wallets.transfer(
                id(a), new TransferRequest("9000000061", 200, "Coffee"), UUID.randomUUID());
        mvc.perform(get("/api/v1/transactions?size=1&direction=DEBIT").cookie(session(a)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("totalItems").value(2))
                .andExpect(jsonPath("totalPages").value(2));
        mvc.perform(get("/api/v1/transactions?search=Coffee").cookie(session(a)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("totalItems").value(1));
        mvc.perform(get("/api/v1/transactions?size=101").cookie(session(a)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/transactions?from=2026-11-02&to=2026-11-01").cookie(session(a)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/transactions/export").cookie(session(a)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("'=1+1")));
        mvc.perform(get("/api/v1/analytics?days=30").cookie(session(a)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("spendingMinor").value(300));
    }

    @Test
    void rejectsNonIntegerJsonMoneyWithoutChangingFunds() throws Exception {
        var sender = register("9000000091");
        register("9000000092");
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        for (String amount :
                new String[] {"1.5", "1.0", "\"100\"", "\"1.5\"", "true", "null", "[]", "{}"}) {
            mvc.perform(
                            post("/api/v1/transfers")
                                    .cookie(session(sender))
                                    .with(realCsrf())
                                    .header("Idempotency-Key", UUID.randomUUID())
                                    .contentType("application/json")
                                    .content(
                                            "{\"recipientPhone\":\"9000000092\",\"amountMinor\":"
                                                    + amount
                                                    + ",\"note\":\"Invalid JSON type\"}"))
                    .andExpect(status().isBadRequest());
            mvc.perform(
                            post("/api/v1/deposits")
                                    .cookie(session(sender))
                                    .with(realCsrf())
                                    .header("Idempotency-Key", UUID.randomUUID())
                                    .contentType("application/json")
                                    .content(
                                            "{\"amountMinor\":"
                                                    + amount
                                                    + ",\"provider\":\"SANDBOX\"}"))
                    .andExpect(status().isBadRequest());
            String body =
                    "{\"eventId\":\""
                            + UUID.randomUUID()
                            + "\",\"depositId\":\""
                            + UUID.randomUUID()
                            + "\",\"amountMinor\":"
                            + amount
                            + ",\"currency\":\"INR\",\"status\":\"COMPLETED\"}";
            mvc.perform(
                            post("/api/v1/webhooks/sandbox")
                                    .header("X-Webhook-Timestamp", timestamp)
                                    .header("X-Webhook-Signature", sign(timestamp, body))
                                    .contentType("application/json")
                                    .content(body))
                    .andExpect(status().isBadRequest());
        }
        assertThat(balance(id(sender))).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wallet_transactions", Long.class))
                .isZero();
    }

    @Test
    void unknownRouteAndWrongMethodUseCorrectStatusCodes() throws Exception {
        var a = register("9000000090");
        mvc.perform(get("/api/v1/no-such-route").cookie(session(a)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/wallet").cookie(session(a)).with(realCsrf()))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(get("/api/v1/wallet").header("Authorization", "Basic dXNlcjpwYXNzd29yZA=="))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void databaseRejectsNegativeBalanceIndependentOfApi() throws Exception {
        var a = register("9000000070");
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "UPDATE wallets SET balance_minor = -1 WHERE user_id = ?",
                                        id(a)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(balance(id(a))).isZero();
    }

    @Test
    void concurrentTransfersCannotOverspend() throws Exception {
        var a = register("9000000080");
        var b = register("9000000081");
        fund(id(a), 1000);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < 8; i++)
                futures.add(
                        executor.submit(
                                () -> {
                                    start.await();
                                    try {
                                        wallets.transfer(
                                                id(a),
                                                new TransferRequest(
                                                        "9000000081", 200, "Concurrent"),
                                                UUID.randomUUID());
                                        return true;
                                    } catch (ApiException ex) {
                                        assertThat(ex.code()).isEqualTo("INSUFFICIENT_BALANCE");
                                        return false;
                                    }
                                }));
            start.countDown();
            int successes = 0;
            for (Future<Boolean> future : futures)
                if (future.get(30, TimeUnit.SECONDS)) successes++;
            assertThat(successes).isEqualTo(5);
            assertThat(balance(id(a))).isZero();
            assertThat(balance(id(b))).isEqualTo(1000);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void welcomeCreditIsManualFixedIdempotentAndVisibleAcrossViews() throws Exception {
        var account = register("9000000100");
        var outsider = register("9000000101");
        assertThat(balance(id(account))).isZero();
        mvc.perform(get("/api/v1/demo-funds").cookie(session(account)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("enabled").value(true))
                .andExpect(jsonPath("welcomeAvailable").value(true))
                .andExpect(jsonPath("welcomeCreditMinor").value(10_000));
        UUID key = UUID.randomUUID();
        var first =
                mvc.perform(
                                post("/api/v1/demo-funds/welcome")
                                        .cookie(session(account))
                                        .with(realCsrf())
                                        .header("Idempotency-Key", key))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("type").value("DEMO_CREDIT"))
                        .andExpect(jsonPath("status").value("COMPLETED"))
                        .andExpect(jsonPath("direction").value("CREDIT"))
                        .andExpect(jsonPath("amountMinor").value(10_000))
                        .andExpect(jsonPath("counterpartyName").value("WalletPay demo"))
                        .andReturn();
        String txId =
                mapper.readTree(first.getResponse().getContentAsByteArray()).get("id").asText();
        mvc.perform(
                        post("/api/v1/demo-funds/welcome")
                                .cookie(session(account))
                                .with(realCsrf())
                                .header("Idempotency-Key", key))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("id").value(txId));
        mvc.perform(
                        post("/api/v1/demo-funds/welcome")
                                .cookie(session(account))
                                .with(realCsrf())
                                .header("Idempotency-Key", UUID.randomUUID()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("WELCOME_ALREADY_CLAIMED"));
        assertThat(balance(id(account))).isEqualTo(10_000);
        mvc.perform(get("/api/v1/demo-funds").cookie(session(account)))
                .andExpect(jsonPath("welcomeAvailable").value(false));
        mvc.perform(
                        get("/api/v1/transactions?type=DEMO_CREDIT&direction=CREDIT")
                                .cookie(session(account)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("totalItems").value(1));
        mvc.perform(get("/api/v1/transactions/" + txId + "/receipt").cookie(session(account)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("amountMinor").value(10_000));
        mvc.perform(get("/api/v1/transactions/" + txId + "/receipt").cookie(session(outsider)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/transactions/export?type=DEMO_CREDIT").cookie(session(account)))
                .andExpect(status().isOk())
                .andExpect(
                        content().string(org.hamcrest.Matchers.containsString("WalletPay demo")));
        mvc.perform(get("/api/v1/analytics?days=30").cookie(session(account)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("incomeMinor").value(10_000))
                .andExpect(jsonPath("transferCount").value(0));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM audit_events WHERE action = 'DEMO_WELCOME_CLAIMED'",
                                Long.class))
                .isEqualTo(1);
    }

    @Test
    void welcomeCreditRequiresAuthenticationCsrfAndUuidKeyAndRejectsAmountBodies()
            throws Exception {
        mvc.perform(get("/api/v1/demo-funds")).andExpect(status().isUnauthorized());
        mvc.perform(
                        post("/api/v1/demo-funds/welcome")
                                .with(realCsrf())
                                .header("Idempotency-Key", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
        var account = register("9000000110");
        mvc.perform(
                        post("/api/v1/demo-funds/welcome")
                                .cookie(session(account))
                                .header("Idempotency-Key", UUID.randomUUID()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/demo-funds/welcome").cookie(session(account)).with(realCsrf()))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        post("/api/v1/demo-funds/welcome")
                                .cookie(session(account))
                                .with(realCsrf())
                                .header("Idempotency-Key", "not-a-uuid"))
                .andExpect(status().isBadRequest());
        for (String body :
                new String[] {
                    "{\"amountMinor\":10000}",
                    "{\"amountMinor\":1.5}",
                    "{\"amountMinor\":\"10000\"}",
                    "null",
                    "{}"
                }) {
            mvc.perform(
                            post("/api/v1/demo-funds/welcome")
                                    .cookie(session(account))
                                    .with(realCsrf())
                                    .header("Idempotency-Key", UUID.randomUUID())
                                    .contentType("application/json")
                                    .content(body))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(
                        post("/api/v1/demo-funds/topups")
                                .cookie(session(account))
                                .with(realCsrf())
                                .header("Idempotency-Key", UUID.randomUUID())
                                .contentType("application/json")
                                .content("{\"amountMinor\":100}"))
                .andExpect(status().isNotFound());
        assertThat(balance(id(account))).isZero();
    }

    @Test
    void welcomeKeyCannotBeReusedForDepositOrViceVersa() throws Exception {
        var account = register("9000000120");
        UUID depositKey = UUID.randomUUID();
        wallets.deposit(
                id(account),
                new com.walletpay.dto.ApiModels.DepositRequest(500, "SANDBOX"),
                depositKey);
        mvc.perform(
                        post("/api/v1/demo-funds/welcome")
                                .cookie(session(account))
                                .with(realCsrf())
                                .header("Idempotency-Key", depositKey))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("IDEMPOTENCY_CONFLICT"));
        UUID welcomeKey = UUID.randomUUID();
        demoFunds.claimWelcome(id(account), welcomeKey);
        mvc.perform(
                        post("/api/v1/deposits")
                                .cookie(session(account))
                                .with(realCsrf())
                                .header("Idempotency-Key", welcomeKey)
                                .contentType("application/json")
                                .content("{\"amountMinor\":10000,\"provider\":\"SANDBOX\"}"))
                .andExpect(status().isConflict());
        assertThat(balance(id(account))).isEqualTo(10_000);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM wallet_transactions WHERE type = 'DEPOSIT' AND status = 'PENDING'",
                                Long.class))
                .isEqualTo(1);
    }

    @Test
    void concurrentDifferentWelcomeKeysGrantExactlyOnce() throws Exception {
        var account = register("9000000130");
        long userId = id(account);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < 8; i++)
                futures.add(
                        executor.submit(
                                () -> {
                                    start.await();
                                    try {
                                        demoFunds.claimWelcome(userId, UUID.randomUUID());
                                        return true;
                                    } catch (ApiException ex) {
                                        assertThat(ex.code()).isEqualTo("WELCOME_ALREADY_CLAIMED");
                                        return false;
                                    }
                                }));
            start.countDown();
            int granted = 0;
            for (var future : futures) if (future.get(30, TimeUnit.SECONDS)) granted++;
            assertThat(granted).isEqualTo(1);
            assertThat(balance(userId)).isEqualTo(10_000);
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT count(*) FROM wallet_transactions WHERE type = 'DEMO_CREDIT'",
                                    Long.class))
                    .isEqualTo(1);
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT count(*) FROM audit_events WHERE action = 'DEMO_WELCOME_CLAIMED'",
                                    Long.class))
                    .isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentSameWelcomeKeyReplaysOneTransaction() throws Exception {
        var account = register("9000000140");
        long userId = id(account);
        UUID key = UUID.randomUUID();
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<UUID>> futures = new ArrayList<>();
            for (int i = 0; i < 8; i++)
                futures.add(
                        executor.submit(
                                () -> {
                                    start.await();
                                    return demoFunds.claimWelcome(userId, key).id();
                                }));
            start.countDown();
            var ids = new java.util.HashSet<UUID>();
            for (var future : futures) ids.add(future.get(30, TimeUnit.SECONDS));
            assertThat(ids).hasSize(1);
            assertThat(balance(userId)).isEqualTo(10_000);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void welcomeBalanceLimitRollsBackEligibilityAndDatabaseRejectsDuplicateCredit()
            throws Exception {
        var account = register("9000000150");
        fund(id(account), com.walletpay.service.Money.MAX_BALANCE);
        assertThatThrownBy(() -> demoFunds.claimWelcome(id(account), UUID.randomUUID()))
                .isInstanceOf(ApiException.class);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT demo_welcome_claimed FROM wallets WHERE user_id = ?",
                                Boolean.class,
                                id(account)))
                .isFalse();
        fund(id(account), 0);
        demoFunds.claimWelcome(id(account), UUID.randomUUID());
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "INSERT INTO wallet_transactions (id,reference,type,status,sender_id,recipient_id,amount_minor,idempotency_key,request_hash,completed_at) VALUES (?,?,'DEMO_CREDIT','COMPLETED',?,?,10000,?,'test',now())",
                                        UUID.randomUUID(),
                                        "duplicate-demo",
                                        id(account),
                                        id(account),
                                        UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(balance(id(account))).isEqualTo(10_000);
    }
}
