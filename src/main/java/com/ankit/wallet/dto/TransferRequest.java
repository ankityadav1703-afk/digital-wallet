package com.ankit.wallet.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record TransferRequest(
        @NotNull Long targetWalletId,
        @NotNull @DecimalMin(value = "0.01", message = "must be at least 0.01")
        @Digits(integer = 15, fraction = 2, message = "must have at most 2 decimal places") BigDecimal amount,
        @Size(max = 200) String description) {
}
