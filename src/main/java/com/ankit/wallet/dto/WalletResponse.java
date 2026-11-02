package com.ankit.wallet.dto;

import com.ankit.wallet.entity.Wallet;
import com.ankit.wallet.entity.WalletStatus;
import java.math.BigDecimal;

public record WalletResponse(Long id, String owner, BigDecimal balance, WalletStatus status) {

    public static WalletResponse from(Wallet wallet) {
        return new WalletResponse(wallet.getId(), wallet.getUser().getUsername(), wallet.getBalance(), wallet.getStatus());
    }
}
