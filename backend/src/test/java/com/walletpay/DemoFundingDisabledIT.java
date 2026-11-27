package com.walletpay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.walletpay.dto.ApiModels.RegisterRequest;
import com.walletpay.service.AuthService;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "app.demo-funding-enabled=false")
@AutoConfigureMockMvc
class DemoFundingDisabledIT {
    @Autowired MockMvc mvc;
    @Autowired AuthService auth;
    @Autowired ObjectMapper mapper;
    @Autowired Environment environment;
    @Autowired JdbcTemplate jdbc;

    @Test
    void disabledFeatureAdvertisesNoGrantAndRejectsAuthenticatedPost() throws Exception {
        var account =
                auth.register(
                        new RegisterRequest(
                                "Disabled Demo", "9000000190", "Strong-test-password-42"));
        var authentication =
                new UsernamePasswordAuthenticationToken(account.user().id(), null, List.of());
        mvc.perform(get("/api/v1/demo-funds").with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("enabled").value(false))
                .andExpect(jsonPath("welcomeAvailable").value(false))
                .andExpect(jsonPath("welcomeCreditMinor").value(10_000));
        var csrf = mvc.perform(get("/api/v1/auth/csrf")).andReturn();
        var json = mapper.readTree(csrf.getResponse().getContentAsByteArray());
        mvc.perform(
                        post("/api/v1/demo-funds/welcome")
                                .with(authentication(authentication))
                                .cookie(csrf.getResponse().getCookie("XSRF-TOKEN"))
                                .header(json.get("headerName").asText(), json.get("token").asText())
                                .header("Idempotency-Key", UUID.randomUUID()))
                .andExpect(status().isNotFound());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT balance_minor FROM wallets WHERE user_id = ?",
                                Long.class,
                                account.user().id()))
                .isZero();
    }

    @Test
    void migrationPreservesExistingAccountsBalancesAndPendingDeposits() throws Exception {
        String schema = "demo_migration_" + UUID.randomUUID().toString().replace("-", "");
        String url = environment.getRequiredProperty("spring.datasource.url");
        String username = environment.getRequiredProperty("spring.datasource.username");
        String password = environment.getProperty("spring.datasource.password", "");
        // Schema-isolation checks use their own connections, never the application pool.
        try (var connection = DriverManager.getConnection(url, username, password);
                var statement = connection.createStatement()) {
            assertThat(connection.getCatalog()).endsWith("_test");
            Flyway.configure()
                    .dataSource(url, username, password)
                    .schemas(schema)
                    .defaultSchema(schema)
                    .target("2")
                    .load()
                    .migrate();
            connection.setSchema(schema);
            statement.executeUpdate(
                    "INSERT INTO app_users (id,full_name,phone,password_hash) VALUES (1,'Existing Person','9000000191','hash')");
            statement.executeUpdate("INSERT INTO wallets (user_id,balance_minor) VALUES (1,1234)");
            String depositId = UUID.randomUUID().toString();
            statement.executeUpdate(
                    "INSERT INTO wallet_transactions (id,reference,type,status,sender_id,recipient_id,amount_minor,idempotency_key,request_hash) VALUES ('"
                            + depositId
                            + "','old-deposit','DEPOSIT','PENDING',1,1,500,'"
                            + UUID.randomUUID()
                            + "','old-hash')");
            Flyway.configure()
                    .dataSource(url, username, password)
                    .schemas(schema)
                    .defaultSchema(schema)
                    .load()
                    .migrate();
            try (var result =
                    statement.executeQuery(
                            "SELECT balance_minor,demo_welcome_claimed FROM wallets WHERE user_id=1")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getLong(1)).isEqualTo(1234);
                assertThat(result.getBoolean(2)).isFalse();
            }
            try (var result =
                    statement.executeQuery(
                            "SELECT status,completed_at FROM wallet_transactions WHERE id='"
                                    + depositId
                                    + "'")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo("PENDING");
                assertThat(result.getTimestamp(2)).isNull();
            }
            statement.executeUpdate(
                    "INSERT INTO wallet_transactions (id,reference,type,status,sender_id,recipient_id,amount_minor,idempotency_key,request_hash,completed_at) VALUES ('"
                            + UUID.randomUUID()
                            + "','new-demo','DEMO_CREDIT','COMPLETED',1,1,10000,'"
                            + UUID.randomUUID()
                            + "','new-hash',now())");
        } finally {
            // Only the UUID-named schema created by this test is removed.
            Flyway.configure()
                    .dataSource(url, username, password)
                    .schemas(schema)
                    .defaultSchema(schema)
                    .cleanDisabled(false)
                    .load()
                    .clean();
        }
    }
}
