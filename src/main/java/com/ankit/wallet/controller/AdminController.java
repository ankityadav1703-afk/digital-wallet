package com.ankit.wallet.controller;

import com.ankit.wallet.dto.AdminReport;
import com.ankit.wallet.dto.WalletResponse;
import com.ankit.wallet.service.AdminService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Only reachable with ROLE_ADMIN (enforced in SecurityConfig). */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/report")
    public AdminReport report() {
        return adminService.report();
    }

    @GetMapping("/wallets/{id}")
    public WalletResponse wallet(@PathVariable Long id) {
        return adminService.getWallet(id);
    }

    @PostMapping("/wallets/{id}/freeze")
    public WalletResponse freeze(@PathVariable Long id) {
        return adminService.freeze(id);
    }

    @PostMapping("/wallets/{id}/unfreeze")
    public WalletResponse unfreeze(@PathVariable Long id) {
        return adminService.unfreeze(id);
    }
}
