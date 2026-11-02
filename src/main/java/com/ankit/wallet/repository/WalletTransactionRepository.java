package com.ankit.wallet.repository;

import com.ankit.wallet.entity.TransactionType;
import com.ankit.wallet.entity.WalletTransaction;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {

    Optional<WalletTransaction> findByIdempotencyKeyAndSourceWalletId(String idempotencyKey, Long sourceWalletId);

    @Query("select t from WalletTransaction t where t.sourceWalletId = :walletId or t.targetWalletId = :walletId")
    Page<WalletTransaction> findForWallet(@Param("walletId") Long walletId, Pageable pageable);

    @Query("select t from WalletTransaction t "
            + "where (t.sourceWalletId = :walletId or t.targetWalletId = :walletId) and t.type = :type")
    Page<WalletTransaction> findForWalletAndType(@Param("walletId") Long walletId,
                                                 @Param("type") TransactionType type,
                                                 Pageable pageable);
}
