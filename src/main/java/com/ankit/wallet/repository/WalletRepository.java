package com.ankit.wallet.repository;

import com.ankit.wallet.entity.Wallet;
import com.ankit.wallet.entity.WalletStatus;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletRepository extends JpaRepository<Wallet, Long> {

    /**
     * Loads ONLY the id (a scalar, not an entity). This matters: if we loaded the Wallet entity first
     * and then locked it, Hibernate could keep the stale copy from before the lock was granted.
     */
    @Query("select w.id from Wallet w where w.user.username = :username")
    Optional<Long> findIdByUsername(@Param("username") String username);

    /** SELECT ... FOR UPDATE: other transactions wait here until we commit or roll back. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.id = :id")
    Optional<Wallet> findByIdForUpdate(@Param("id") Long id);

    long countByStatus(WalletStatus status);

    @Query("select sum(w.balance) from Wallet w")
    BigDecimal sumOfBalances();
}
