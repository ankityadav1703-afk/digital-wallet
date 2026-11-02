package com.ankit.wallet.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Immutable ledger row. Every money movement creates one; rows are never updated or deleted,
 * so the balance of any wallet can always be re-checked from the ledger.
 */
@Entity
@Table(name = "wallet_transactions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"idempotency_key", "source_wallet_id"}),
        indexes = {
                @Index(name = "idx_tx_source", columnList = "source_wallet_id"),
                @Index(name = "idx_tx_target", columnList = "target_wallet_id")
        })
public class WalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private TransactionType type;

    @Column(name = "source_wallet_id")
    private Long sourceWalletId;

    @Column(name = "target_wallet_id")
    private Long targetWalletId;

    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(length = 200)
    private String description;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected WalletTransaction() {
        // required by JPA
    }

    private WalletTransaction(TransactionType type, Long sourceWalletId, Long targetWalletId,
                              BigDecimal amount, String description, String idempotencyKey) {
        this.type = type;
        this.sourceWalletId = sourceWalletId;
        this.targetWalletId = targetWalletId;
        this.amount = amount;
        this.description = description;
        this.idempotencyKey = idempotencyKey;
    }

    public static WalletTransaction deposit(Long walletId, BigDecimal amount, String description) {
        return new WalletTransaction(TransactionType.DEPOSIT, null, walletId, amount, description, null);
    }

    public static WalletTransaction withdrawal(Long walletId, BigDecimal amount, String description) {
        return new WalletTransaction(TransactionType.WITHDRAWAL, walletId, null, amount, description, null);
    }

    public static WalletTransaction transfer(Long sourceId, Long targetId, BigDecimal amount,
                                             String description, String idempotencyKey) {
        return new WalletTransaction(TransactionType.TRANSFER, sourceId, targetId, amount, description, idempotencyKey);
    }

    public Long getId() { return id; }
    public TransactionType getType() { return type; }
    public Long getSourceWalletId() { return sourceWalletId; }
    public Long getTargetWalletId() { return targetWalletId; }
    public BigDecimal getAmount() { return amount; }
    public String getDescription() { return description; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Instant getCreatedAt() { return createdAt; }
}
