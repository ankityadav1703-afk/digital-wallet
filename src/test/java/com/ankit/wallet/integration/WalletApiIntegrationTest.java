package com.ankit.wallet.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * End-to-end tests: real Spring context, real security filter chain, real JPA against in-memory H2,
 * calling the REST API through MockMvc.
 */
@SpringBootTest
@AutoConfigureMockMvc
class WalletApiIntegrationTest {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    @Autowired
    private MockMvc mvc;

    // ---------- helpers ----------

    private static String uniqueName(String prefix) {
        return prefix + COUNTER.incrementAndGet() + "x" + (System.nanoTime() % 100000);
    }

    private String register(String username) throws Exception {
        String body = String.format(
                "{\"username\":\"%s\",\"email\":\"%s@example.com\",\"password\":\"Password123\"}", username, username);
        String response = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.token");
    }

    private String adminToken() throws Exception {
        String response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Admin@12345\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.token");
    }

    private ResultActions postJson(String url, String token, String body) throws Exception {
        return mvc.perform(post(url).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions deposit(String token, String amount) throws Exception {
        return postJson("/api/wallet/deposit", token, "{\"amount\":" + amount + ",\"description\":\"test\"}");
    }

    private long walletId(String token) throws Exception {
        String response = mvc.perform(get("/api/wallet").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private BigDecimal balance(String token) throws Exception {
        String response = mvc.perform(get("/api/wallet").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Object raw = JsonPath.read(response, "$.balance");
        return new BigDecimal(raw.toString());
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }

    // ---------- authentication ----------

    @Test
    void registerThenLogin_returnsJwt_andWrongPasswordIsRejected() throws Exception {
        String username = uniqueName("login");
        register(username);

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"Password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"WrongPassword1\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void register_withInvalidData_returns400() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ab\",\"email\":\"not-an-email\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_duplicateUsername_returns409() throws Exception {
        String username = uniqueName("dup");
        register(username);

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"username\":\"%s\",\"email\":\"other%s@example.com\",\"password\":\"Password123\"}",
                                username, username)))
                .andExpect(status().isConflict());
    }

    @Test
    void walletEndpoints_withoutToken_return401() throws Exception {
        mvc.perform(get("/api/wallet")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/wallet/deposit").contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":10.00}")).andExpect(status().isUnauthorized());
    }

    @Test
    void walletEndpoints_withGarbageToken_return401() throws Exception {
        mvc.perform(get("/api/wallet").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- money movement ----------

    @Test
    void newUser_getsEmptyActiveWallet() throws Exception {
        String token = register(uniqueName("fresh"));

        mvc.perform(get("/api/wallet").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        assertMoney("0.00", balance(token));
    }

    @Test
    void depositWithdrawAndTransfer_updateBalancesAndHistory() throws Exception {
        String aliceToken = register(uniqueName("alice"));
        String bobToken = register(uniqueName("bob"));
        long bobWalletId = walletId(bobToken);

        deposit(aliceToken, "100.00").andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("DEPOSIT"));

        postJson("/api/wallet/transfer", aliceToken,
                "{\"targetWalletId\":" + bobWalletId + ",\"amount\":40.00,\"description\":\"lunch\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("TRANSFER"));

        postJson("/api/wallet/withdraw", bobToken, "{\"amount\":15.50}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("WITHDRAWAL"));

        assertMoney("60.00", balance(aliceToken));
        assertMoney("24.50", balance(bobToken));

        // Alice's history: the deposit and the outgoing transfer.
        mvc.perform(get("/api/wallet/transactions").header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        // Filtering by type.
        mvc.perform(get("/api/wallet/transactions?type=TRANSFER").header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].type").value("TRANSFER"));

        // Bob's history: the incoming transfer and his withdrawal.
        mvc.perform(get("/api/wallet/transactions").header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void withdraw_moreThanBalance_returns422_andBalanceIsUnchanged() throws Exception {
        String token = register(uniqueName("poor"));
        deposit(token, "10.00").andExpect(status().isOk());

        postJson("/api/wallet/withdraw", token, "{\"amount\":10.01}")
                .andExpect(status().isUnprocessableEntity());

        assertMoney("10.00", balance(token));
    }

    @Test
    void transfer_withSameIdempotencyKey_movesMoneyOnlyOnce() throws Exception {
        String aliceToken = register(uniqueName("idem"));
        String bobToken = register(uniqueName("idemb"));
        long bobWalletId = walletId(bobToken);
        deposit(aliceToken, "100.00").andExpect(status().isOk());

        String body = "{\"targetWalletId\":" + bobWalletId + ",\"amount\":10.00}";
        String first = mvc.perform(post("/api/wallet/transfer").header("Authorization", "Bearer " + aliceToken)
                        .header("Idempotency-Key", "order-42").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String second = mvc.perform(post("/api/wallet/transfer").header("Authorization", "Bearer " + aliceToken)
                        .header("Idempotency-Key", "order-42").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertEquals((Object) JsonPath.read(first, "$.id"), (Object) JsonPath.read(second, "$.id"));
        assertMoney("90.00", balance(aliceToken));
        assertMoney("10.00", balance(bobToken));
    }

    @Test
    void transfer_withInvalidInput_isRejected() throws Exception {
        String aliceToken = register(uniqueName("bad"));
        long ownWalletId = walletId(aliceToken);
        deposit(aliceToken, "50.00").andExpect(status().isOk());

        // unknown target wallet -> 404
        postJson("/api/wallet/transfer", aliceToken, "{\"targetWalletId\":999999999,\"amount\":5.00}")
                .andExpect(status().isNotFound());
        // transfer to yourself -> 400
        postJson("/api/wallet/transfer", aliceToken, "{\"targetWalletId\":" + ownWalletId + ",\"amount\":5.00}")
                .andExpect(status().isBadRequest());
        // negative amount -> 400 (bean validation)
        postJson("/api/wallet/transfer", aliceToken, "{\"targetWalletId\":999999999,\"amount\":-5.00}")
                .andExpect(status().isBadRequest());
        // three decimal places -> 400
        postJson("/api/wallet/transfer", aliceToken, "{\"targetWalletId\":999999999,\"amount\":5.005}")
                .andExpect(status().isBadRequest());

        assertMoney("50.00", balance(aliceToken));
    }

    @Test
    void history_withInvalidTypeParameter_returns400() throws Exception {
        String token = register(uniqueName("hist"));

        mvc.perform(get("/api/wallet/transactions?type=NOPE").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    // ---------- admin ----------

    @Test
    void adminEndpoints_areForbiddenForNormalUsers_andAllowedForAdmin() throws Exception {
        String userToken = register(uniqueName("plain"));

        mvc.perform(get("/api/admin/report").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/report")).andExpect(status().isUnauthorized());

        mvc.perform(get("/api/admin/report").header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").isNumber())
                .andExpect(jsonPath("$.totalWallets").isNumber());
    }

    @Test
    void frozenWallet_blocksMoneyMovement_untilAdminUnfreezesIt() throws Exception {
        String userToken = register(uniqueName("frozen"));
        long walletId = walletId(userToken);
        String admin = adminToken();

        mvc.perform(post("/api/admin/wallets/" + walletId + "/freeze").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FROZEN"));

        deposit(userToken, "10.00").andExpect(status().isConflict());

        mvc.perform(post("/api/admin/wallets/" + walletId + "/unfreeze").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        deposit(userToken, "10.00").andExpect(status().isOk());
        assertMoney("10.00", balance(userToken));
    }

    @Test
    void unknownPath_returns404_notAServerError() throws Exception {
        String token = register(uniqueName("nf"));

        mvc.perform(get("/api/does-not-exist").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }
}
