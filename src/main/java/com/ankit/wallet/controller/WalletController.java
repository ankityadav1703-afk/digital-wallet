package com.ankit.wallet.controller;

import com.ankit.wallet.dto.AmountRequest;
import com.ankit.wallet.dto.PageResponse;
import com.ankit.wallet.dto.TransactionResponse;
import com.ankit.wallet.dto.TransferRequest;
import com.ankit.wallet.dto.WalletResponse;
import com.ankit.wallet.entity.TransactionType;
import com.ankit.wallet.service.WalletService;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** All endpoints act on the wallet of the logged-in user (taken from the JWT, never from the request body). */
@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    private static final int MAX_PAGE_SIZE = 100;

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @GetMapping
    public WalletResponse myWallet(Principal principal) {
        return walletService.getMyWallet(principal.getName());
    }

    @PostMapping("/deposit")
    public TransactionResponse deposit(Principal principal, @Valid @RequestBody AmountRequest request) {
        return walletService.deposit(principal.getName(), request.amount(), request.description());
    }

    @PostMapping("/withdraw")
    public TransactionResponse withdraw(Principal principal, @Valid @RequestBody AmountRequest request) {
        return walletService.withdraw(principal.getName(), request.amount(), request.description());
    }

    @PostMapping("/transfer")
    public TransactionResponse transfer(Principal principal,
                                        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                        @Valid @RequestBody TransferRequest request) {
        return walletService.transfer(principal.getName(), request.targetWalletId(), request.amount(),
                request.description(), idempotencyKey);
    }

    @GetMapping("/transactions")
    public PageResponse<TransactionResponse> transactions(Principal principal,
                                                          @RequestParam(required = false) TransactionType type,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        return walletService.history(principal.getName(), type, pageable);
    }
}
