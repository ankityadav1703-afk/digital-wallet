package com.ankit.wallet.dto;

import java.math.BigDecimal;

public record AdminReport(long totalUsers, long totalWallets, long frozenWallets,
                          BigDecimal totalBalance, long totalTransactions) {
}
