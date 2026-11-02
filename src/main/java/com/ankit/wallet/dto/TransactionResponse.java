package com.ankit.wallet.dto;

import com.ankit.wallet.entity.TransactionType;
import com.ankit.wallet.entity.WalletTransaction;
import java.math.BigDecimal;
import java.time.Instant;

public record TransactionResponse(Long id, TransactionType type, Long sourceWalletId, Long targetWalletId,
                                  BigDecimal amount, String description, Instant createdAt) {

    public static TransactionResponse from(WalletTransaction t) {
        return new TransactionResponse(t.getId(), t.getType(), t.getSourceWalletId(), t.getTargetWalletId(),
                t.getAmount(), t.getDescription(), t.getCreatedAt());
    }
}
