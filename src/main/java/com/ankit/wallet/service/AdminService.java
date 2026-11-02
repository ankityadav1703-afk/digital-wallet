package com.ankit.wallet.service;

import com.ankit.wallet.dto.AdminReport;
import com.ankit.wallet.dto.WalletResponse;
import com.ankit.wallet.entity.Wallet;
import com.ankit.wallet.entity.WalletStatus;
import com.ankit.wallet.exception.ResourceNotFoundException;
import com.ankit.wallet.repository.UserRepository;
import com.ankit.wallet.repository.WalletRepository;
import com.ankit.wallet.repository.WalletTransactionRepository;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {

    private final WalletRepository wallets;
    private final UserRepository users;
    private final WalletTransactionRepository transactions;

    public AdminService(WalletRepository wallets, UserRepository users, WalletTransactionRepository transactions) {
        this.wallets = wallets;
        this.users = users;
        this.transactions = transactions;
    }

    @Transactional(readOnly = true)
    public WalletResponse getWallet(Long walletId) {
        return WalletResponse.from(wallets.findById(walletId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found: " + walletId)));
    }

    @Transactional
    public WalletResponse freeze(Long walletId) {
        Wallet wallet = lock(walletId);
        wallet.freeze();
        return WalletResponse.from(wallet);
    }

    @Transactional
    public WalletResponse unfreeze(Long walletId) {
        Wallet wallet = lock(walletId);
        wallet.unfreeze();
        return WalletResponse.from(wallet);
    }

    @Transactional(readOnly = true)
    public AdminReport report() {
        BigDecimal total = wallets.sumOfBalances();
        return new AdminReport(users.count(), wallets.count(), wallets.countByStatus(WalletStatus.FROZEN),
                total == null ? BigDecimal.ZERO : total, transactions.count());
    }

    private Wallet lock(Long walletId) {
        return wallets.findByIdForUpdate(walletId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found: " + walletId));
    }
}
