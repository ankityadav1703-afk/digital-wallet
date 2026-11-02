package com.ankit.wallet.service;

import com.ankit.wallet.dto.PageResponse;
import com.ankit.wallet.dto.TransactionResponse;
import com.ankit.wallet.dto.WalletResponse;
import com.ankit.wallet.entity.TransactionType;
import com.ankit.wallet.entity.Wallet;
import com.ankit.wallet.entity.WalletTransaction;
import com.ankit.wallet.exception.InvalidOperationException;
import com.ankit.wallet.exception.ResourceNotFoundException;
import com.ankit.wallet.repository.WalletRepository;
import com.ankit.wallet.repository.WalletTransactionRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Core money logic. Every method that changes a balance is @Transactional:
 * if anything throws, the whole database transaction rolls back (no half-finished transfers).
 *
 * Concurrency strategy (two layers):
 *  1. PESSIMISTIC lock (SELECT ... FOR UPDATE) on the wallet rows we change, so concurrent
 *     requests for the same wallet queue up instead of overwriting each other.
 *  2. OPTIMISTIC lock (@Version on Wallet) as a safety net that fails loudly if a lost update
 *     ever slipped through.
 * For transfers we lock both wallets in ascending id order, which makes deadlocks impossible
 * (A->B and B->A at the same time both lock the lower id first).
 */
@Service
public class WalletService {

    private final WalletRepository wallets;
    private final WalletTransactionRepository transactions;

    public WalletService(WalletRepository wallets, WalletTransactionRepository transactions) {
        this.wallets = wallets;
        this.transactions = transactions;
    }

    @Transactional(readOnly = true)
    public WalletResponse getMyWallet(String username) {
        Long walletId = walletIdOf(username);
        Wallet wallet = wallets.findById(walletId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found: " + walletId));
        return WalletResponse.from(wallet);
    }

    @Transactional
    public TransactionResponse deposit(String username, BigDecimal amount, String description) {
        Wallet wallet = lock(walletIdOf(username));
        wallet.ensureActive();
        wallet.credit(amount);
        WalletTransaction tx = transactions.save(WalletTransaction.deposit(wallet.getId(), amount, description));
        return TransactionResponse.from(tx);
    }

    @Transactional
    public TransactionResponse withdraw(String username, BigDecimal amount, String description) {
        Wallet wallet = lock(walletIdOf(username));
        wallet.ensureActive();
        wallet.debit(amount); // throws InsufficientFundsException if the balance is too low
        WalletTransaction tx = transactions.save(WalletTransaction.withdrawal(wallet.getId(), amount, description));
        return TransactionResponse.from(tx);
    }

    /**
     * Atomic transfer. If an Idempotency-Key is supplied and was already used by this sender,
     * the original result is returned and no money moves (safe to retry after a network timeout).
     */
    @Transactional
    public TransactionResponse transfer(String username, Long targetWalletId, BigDecimal amount,
                                        String description, String idempotencyKey) {
        Long sourceId = walletIdOf(username);
        if (sourceId.equals(targetWalletId)) {
            throw new InvalidOperationException("Cannot transfer to the same wallet");
        }

        // Always lock the lower id first, whichever direction the money flows.
        Long firstId = Math.min(sourceId, targetWalletId);
        Long secondId = Math.max(sourceId, targetWalletId);
        Wallet first = lock(firstId);
        Wallet second = lock(secondId);
        Wallet source = first.getId().equals(sourceId) ? first : second;
        Wallet target = first.getId().equals(sourceId) ? second : first;

        // Checked AFTER the locks so two simultaneous requests with the same key cannot both proceed.
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<WalletTransaction> previous =
                    transactions.findByIdempotencyKeyAndSourceWalletId(idempotencyKey, sourceId);
            if (previous.isPresent()) {
                WalletTransaction p = previous.get();
                boolean samePayload = p.getTargetWalletId().equals(targetWalletId)
                        && p.getAmount().compareTo(amount) == 0;
                if (!samePayload) {
                    throw new InvalidOperationException("Idempotency-Key was already used for a different transfer");
                }
                return TransactionResponse.from(p);
            }
        }

        source.ensureActive();
        target.ensureActive();
        source.debit(amount);
        target.credit(amount);

        String key = (idempotencyKey == null || idempotencyKey.isBlank()) ? null : idempotencyKey;
        WalletTransaction tx = transactions.save(
                WalletTransaction.transfer(sourceId, targetWalletId, amount, description, key));
        return TransactionResponse.from(tx);
    }

    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> history(String username, TransactionType type, Pageable pageable) {
        Long walletId = walletIdOf(username);
        Page<WalletTransaction> page = (type == null)
                ? transactions.findForWallet(walletId, pageable)
                : transactions.findForWalletAndType(walletId, type, pageable);
        return PageResponse.from(page.map(TransactionResponse::from));
    }

    private Long walletIdOf(String username) {
        return wallets.findIdByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("No wallet found for user " + username));
    }

    private Wallet lock(Long walletId) {
        return wallets.findByIdForUpdate(walletId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found: " + walletId));
    }
}
