package com.ankit.wallet.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ankit.wallet.dto.RegisterRequest;
import com.ankit.wallet.dto.WalletResponse;
import com.ankit.wallet.exception.InsufficientFundsException;
import com.ankit.wallet.repository.WalletRepository;
import com.ankit.wallet.service.AuthService;
import com.ankit.wallet.service.WalletService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Proves the locking strategy: many threads hit the same wallets at the same time and the money
 * is never lost, duplicated or overdrawn. (Same annotations as the API test so both share one context.)
 */
@SpringBootTest
@AutoConfigureMockMvc
class ConcurrencyIntegrationTest {

    private static final int THREADS = 16;

    @Autowired
    private AuthService authService;

    @Autowired
    private WalletService walletService;

    @Autowired
    private WalletRepository wallets;

    private Long register(String username) {
        authService.register(new RegisterRequest(username, username + "@example.com", "Password123"));
        return wallets.findIdByUsername(username).orElseThrow();
    }

    private BigDecimal balanceOf(String username) {
        WalletResponse wallet = walletService.getMyWallet(username);
        return wallet.balance();
    }

    /** Starts all tasks at the same instant and waits for them; returns the outcome of each. */
    private List<Throwable> runConcurrently(List<Callable<Void>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<Void>> futures = new ArrayList<>();
        for (Callable<Void> task : tasks) {
            futures.add(pool.submit(() -> {
                startGate.await();
                return task.call();
            }));
        }
        startGate.countDown();
        List<Throwable> failures = new ArrayList<>();
        for (Future<Void> future : futures) {
            try {
                future.get(60, TimeUnit.SECONDS);
            } catch (ExecutionException ex) {
                failures.add(ex.getCause());
            }
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        return failures;
    }

    @Test
    void crossTransfers_inBothDirections_neverLoseOrCreateMoney_andNeverDeadlock() throws Exception {
        String alice = "conc_alice";
        String bob = "conc_bob";
        Long aliceWallet = register(alice);
        Long bobWallet = register(bob);
        walletService.deposit(alice, new BigDecimal("1000.00"), "seed");
        walletService.deposit(bob, new BigDecimal("1000.00"), "seed");

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            tasks.add(() -> {
                walletService.transfer(alice, bobWallet, new BigDecimal("10.00"), "a->b", null);
                return null;
            });
            tasks.add(() -> {
                walletService.transfer(bob, aliceWallet, new BigDecimal("10.00"), "b->a", null);
                return null;
            });
        }

        List<Throwable> failures = runConcurrently(tasks);

        assertTrue(failures.isEmpty(), "No transfer should fail, but got: " + failures);
        // 20 transfers each way of the same amount: both balances return to 1000.00.
        assertEquals(0, new BigDecimal("1000.00").compareTo(balanceOf(alice)));
        assertEquals(0, new BigDecimal("1000.00").compareTo(balanceOf(bob)));
    }

    @Test
    void oneWayTransfers_conserveTheTotal() throws Exception {
        String sender = "conc_sender";
        String receiver = "conc_receiver";
        register(sender);
        Long receiverWallet = register(receiver);
        walletService.deposit(sender, new BigDecimal("500.00"), "seed");

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            tasks.add(() -> {
                walletService.transfer(sender, receiverWallet, new BigDecimal("4.00"), null, null);
                return null;
            });
        }

        List<Throwable> failures = runConcurrently(tasks);

        assertTrue(failures.isEmpty(), "No transfer should fail, but got: " + failures);
        assertEquals(0, new BigDecimal("400.00").compareTo(balanceOf(sender)));
        assertEquals(0, new BigDecimal("100.00").compareTo(balanceOf(receiver)));
    }

    @Test
    void concurrentWithdrawals_cannotOverdrawTheWallet() throws Exception {
        String user = "conc_overdraw";
        register(user);
        walletService.deposit(user, new BigDecimal("100.00"), "seed");

        // 20 threads each try to withdraw 10.00 from a wallet holding 100.00: exactly 10 may succeed.
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            tasks.add(() -> {
                walletService.withdraw(user, new BigDecimal("10.00"), null);
                return null;
            });
        }

        List<Throwable> failures = runConcurrently(tasks);

        assertEquals(10, failures.size(), "exactly half of the withdrawals must be rejected");
        assertTrue(failures.stream().allMatch(f -> f instanceof InsufficientFundsException),
                "every failure must be an InsufficientFundsException, but got: " + failures);
        assertEquals(0, BigDecimal.ZERO.compareTo(balanceOf(user)));
    }

    @Test
    void sameIdempotencyKey_sentConcurrently_movesMoneyOnce() throws Exception {
        String sender = "conc_idem_s";
        String receiver = "conc_idem_r";
        register(sender);
        Long receiverWallet = register(receiver);
        walletService.deposit(sender, new BigDecimal("100.00"), "seed");

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            tasks.add(() -> {
                walletService.transfer(sender, receiverWallet, new BigDecimal("25.00"), null, "same-key");
                return null;
            });
        }

        List<Throwable> failures = runConcurrently(tasks);

        assertTrue(failures.isEmpty(), "Replays must succeed quietly, but got: " + failures);
        assertEquals(0, new BigDecimal("75.00").compareTo(balanceOf(sender)));
        assertEquals(0, new BigDecimal("25.00").compareTo(balanceOf(receiver)));
    }
}
