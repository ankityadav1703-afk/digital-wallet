package com.ankit.wallet.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ankit.wallet.entity.Role;
import com.ankit.wallet.entity.User;
import com.ankit.wallet.entity.Wallet;
import com.ankit.wallet.entity.WalletStatus;
import com.ankit.wallet.exception.InsufficientFundsException;
import com.ankit.wallet.exception.WalletFrozenException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** The money rules live inside the Wallet entity, so they are tested without any framework. */
class WalletEntityTest {

    private static Wallet newWallet() {
        return new Wallet(new User("tester", "tester@example.com", "hash", Role.USER));
    }

    @Test
    void newWallet_startsActiveWithZeroBalance() {
        Wallet wallet = newWallet();

        assertEquals(WalletStatus.ACTIVE, wallet.getStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(wallet.getBalance()));
    }

    @Test
    void creditThenDebit_updatesBalance() {
        Wallet wallet = newWallet();

        wallet.credit(new BigDecimal("25.50"));
        wallet.debit(new BigDecimal("10.25"));

        assertEquals(0, new BigDecimal("15.25").compareTo(wallet.getBalance()));
    }

    @Test
    void debit_belowZero_isRejected() {
        Wallet wallet = newWallet();
        wallet.credit(new BigDecimal("5.00"));

        assertThrows(InsufficientFundsException.class, () -> wallet.debit(new BigDecimal("5.01")));
        assertEquals(0, new BigDecimal("5.00").compareTo(wallet.getBalance()));
    }

    @Test
    void frozenWallet_failsActiveCheck_andCanBeUnfrozen() {
        Wallet wallet = newWallet();

        wallet.freeze();
        assertThrows(WalletFrozenException.class, wallet::ensureActive);

        wallet.unfreeze();
        wallet.ensureActive(); // no exception
    }
}
