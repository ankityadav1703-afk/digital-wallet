package com.ankit.wallet.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ankit.wallet.dto.TransactionResponse;
import com.ankit.wallet.entity.Role;
import com.ankit.wallet.entity.TransactionType;
import com.ankit.wallet.entity.User;
import com.ankit.wallet.entity.Wallet;
import com.ankit.wallet.entity.WalletTransaction;
import com.ankit.wallet.exception.InsufficientFundsException;
import com.ankit.wallet.exception.InvalidOperationException;
import com.ankit.wallet.exception.ResourceNotFoundException;
import com.ankit.wallet.exception.WalletFrozenException;
import com.ankit.wallet.repository.WalletRepository;
import com.ankit.wallet.repository.WalletTransactionRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** Pure unit tests: repositories are mocked, no Spring and no database involved. */
@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private WalletRepository wallets;

    @Mock
    private WalletTransactionRepository transactions;

    @InjectMocks
    private WalletService service;

    private static Wallet wallet(long id, String balance) {
        User user = new User("user" + id, "user" + id + "@example.com", "hash", Role.USER);
        Wallet wallet = new Wallet(user);
        ReflectionTestUtils.setField(wallet, "id", id);
        wallet.credit(new BigDecimal(balance));
        return wallet;
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    private void givenOwnerOf(String username, Wallet wallet) {
        when(wallets.findIdByUsername(username)).thenReturn(Optional.of(wallet.getId()));
    }

    private void givenLockable(Wallet wallet) {
        when(wallets.findByIdForUpdate(wallet.getId())).thenReturn(Optional.of(wallet));
    }

    private void givenSaveReturnsArgument() {
        when(transactions.save(any(WalletTransaction.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void deposit_increasesBalanceAndWritesLedgerEntry() {
        Wallet alice = wallet(1, "100.00");
        givenOwnerOf("alice", alice);
        givenLockable(alice);
        givenSaveReturnsArgument();

        TransactionResponse response = service.deposit("alice", money("50.00"), "top up");

        assertEquals(0, money("150.00").compareTo(alice.getBalance()));
        assertEquals(TransactionType.DEPOSIT, response.type());
        assertEquals(1L, response.targetWalletId());
    }

    @Test
    void withdraw_moreThanBalance_throwsAndChangesNothing() {
        Wallet alice = wallet(1, "30.00");
        givenOwnerOf("alice", alice);
        givenLockable(alice);

        assertThrows(InsufficientFundsException.class, () -> service.withdraw("alice", money("30.01"), null));

        assertEquals(0, money("30.00").compareTo(alice.getBalance()));
        verify(transactions, never()).save(any());
    }

    @Test
    void withdraw_exactBalance_leavesZero() {
        Wallet alice = wallet(1, "30.00");
        givenOwnerOf("alice", alice);
        givenLockable(alice);
        givenSaveReturnsArgument();

        service.withdraw("alice", money("30.00"), null);

        assertEquals(0, BigDecimal.ZERO.compareTo(alice.getBalance()));
    }

    @Test
    void deposit_intoFrozenWallet_isRejected() {
        Wallet alice = wallet(1, "10.00");
        alice.freeze();
        givenOwnerOf("alice", alice);
        givenLockable(alice);

        assertThrows(WalletFrozenException.class, () -> service.deposit("alice", money("5.00"), null));

        verify(transactions, never()).save(any());
    }

    @Test
    void transfer_movesMoneyFromSourceToTarget() {
        Wallet alice = wallet(1, "100.00");
        Wallet bob = wallet(2, "20.00");
        givenOwnerOf("alice", alice);
        givenLockable(alice);
        givenLockable(bob);
        givenSaveReturnsArgument();

        TransactionResponse response = service.transfer("alice", 2L, money("30.00"), "rent", null);

        assertEquals(0, money("70.00").compareTo(alice.getBalance()));
        assertEquals(0, money("50.00").compareTo(bob.getBalance()));
        assertEquals(TransactionType.TRANSFER, response.type());
        assertEquals(1L, response.sourceWalletId());
        assertEquals(2L, response.targetWalletId());
    }

    @Test
    void transfer_toSameWallet_isRejectedBeforeAnyLocking() {
        Wallet alice = wallet(1, "100.00");
        givenOwnerOf("alice", alice);

        assertThrows(InvalidOperationException.class, () -> service.transfer("alice", 1L, money("5.00"), null, null));

        verify(wallets, never()).findByIdForUpdate(any());
    }

    @Test
    void transfer_withInsufficientFunds_changesNeitherWallet() {
        Wallet alice = wallet(1, "10.00");
        Wallet bob = wallet(2, "0.00");
        givenOwnerOf("alice", alice);
        givenLockable(alice);
        givenLockable(bob);

        assertThrows(InsufficientFundsException.class, () -> service.transfer("alice", 2L, money("10.01"), null, null));

        assertEquals(0, money("10.00").compareTo(alice.getBalance()));
        assertEquals(0, money("0.00").compareTo(bob.getBalance()));
        verify(transactions, never()).save(any());
    }

    @Test
    void transfer_toMissingWallet_throwsNotFound() {
        Wallet alice = wallet(1, "100.00");
        givenOwnerOf("alice", alice);
        givenLockable(alice);
        when(wallets.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.transfer("alice", 99L, money("5.00"), null, null));

        assertEquals(0, money("100.00").compareTo(alice.getBalance()));
    }

    @Test
    void transfer_toFrozenWallet_isRejected() {
        Wallet alice = wallet(1, "100.00");
        Wallet bob = wallet(2, "0.00");
        bob.freeze();
        givenOwnerOf("alice", alice);
        givenLockable(alice);
        givenLockable(bob);

        assertThrows(WalletFrozenException.class, () -> service.transfer("alice", 2L, money("5.00"), null, null));

        assertEquals(0, money("100.00").compareTo(alice.getBalance()));
    }

    @Test
    void transfer_alwaysLocksLowerWalletIdFirst_toAvoidDeadlocks() {
        Wallet low = wallet(1, "0.00");
        Wallet high = wallet(2, "100.00");
        givenOwnerOf("highOwner", high); // the sender owns the HIGHER id
        givenLockable(low);
        givenLockable(high);
        givenSaveReturnsArgument();

        service.transfer("highOwner", 1L, money("10.00"), null, null);

        InOrder order = inOrder(wallets);
        order.verify(wallets).findByIdForUpdate(1L);
        order.verify(wallets).findByIdForUpdate(2L);
        assertEquals(0, money("10.00").compareTo(low.getBalance()));
        assertEquals(0, money("90.00").compareTo(high.getBalance()));
    }

    @Test
    void transfer_withRepeatedIdempotencyKey_returnsOriginalAndMovesNoMoney() {
        Wallet alice = wallet(1, "70.00");
        Wallet bob = wallet(2, "30.00");
        givenOwnerOf("alice", alice);
        givenLockable(alice);
        givenLockable(bob);
        WalletTransaction original = WalletTransaction.transfer(1L, 2L, money("30.00"), "rent", "key-1");
        when(transactions.findByIdempotencyKeyAndSourceWalletId("key-1", 1L)).thenReturn(Optional.of(original));

        TransactionResponse response = service.transfer("alice", 2L, money("30.00"), "rent", "key-1");

        assertEquals(TransactionType.TRANSFER, response.type());
        assertEquals(0, money("70.00").compareTo(alice.getBalance()));
        assertEquals(0, money("30.00").compareTo(bob.getBalance()));
        verify(transactions, never()).save(any());
    }

    @Test
    void transfer_reusingKeyForDifferentAmount_isRejected() {
        Wallet alice = wallet(1, "70.00");
        Wallet bob = wallet(2, "30.00");
        givenOwnerOf("alice", alice);
        givenLockable(alice);
        givenLockable(bob);
        WalletTransaction original = WalletTransaction.transfer(1L, 2L, money("30.00"), "rent", "key-1");
        when(transactions.findByIdempotencyKeyAndSourceWalletId("key-1", 1L)).thenReturn(Optional.of(original));

        assertThrows(InvalidOperationException.class,
                () -> service.transfer("alice", 2L, money("31.00"), "rent", "key-1"));
    }

    @Test
    void unknownUser_hasNoWallet() {
        when(wallets.findIdByUsername("ghost")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.deposit("ghost", money("1.00"), null));
    }
}
