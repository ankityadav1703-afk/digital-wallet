package com.ankit.wallet.entity;

import com.ankit.wallet.exception.InsufficientFundsException;
import com.ankit.wallet.exception.WalletFrozenException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * A user's wallet. The business rules (cannot go negative, cannot be used when
 * frozen) live here in the entity, so they cannot be bypassed by a service.
 */
@Entity
@Table(name = "wallets")
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private WalletStatus status = WalletStatus.ACTIVE;

    /** Optimistic locking: Hibernate adds "WHERE version = ?" to every UPDATE and fails if it changed. */
    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Wallet() {
        // required by JPA
    }

    public Wallet(User user) {
        this.user = user;
    }

    public void ensureActive() {
        if (status == WalletStatus.FROZEN) {
            throw new WalletFrozenException(id);
        }
    }

    public void credit(BigDecimal amount) {
        balance = balance.add(amount);
    }

    public void debit(BigDecimal amount) {
        if (balance.compareTo(amount) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient funds in wallet " + id + ": balance " + balance + ", requested " + amount);
        }
        balance = balance.subtract(amount);
    }

    public void freeze() { this.status = WalletStatus.FROZEN; }
    public void unfreeze() { this.status = WalletStatus.ACTIVE; }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public BigDecimal getBalance() { return balance; }
    public WalletStatus getStatus() { return status; }
    public Long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
}
