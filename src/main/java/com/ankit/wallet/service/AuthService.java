package com.ankit.wallet.service;

import com.ankit.wallet.dto.AuthResponse;
import com.ankit.wallet.dto.LoginRequest;
import com.ankit.wallet.dto.RegisterRequest;
import com.ankit.wallet.entity.Role;
import com.ankit.wallet.entity.User;
import com.ankit.wallet.entity.Wallet;
import com.ankit.wallet.exception.DuplicateResourceException;
import com.ankit.wallet.repository.UserRepository;
import com.ankit.wallet.repository.WalletRepository;
import com.ankit.wallet.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository users;
    private final WalletRepository wallets;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(UserRepository users, WalletRepository wallets, PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager, JwtService jwtService) {
        this.users = users;
        this.wallets = wallets;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    /** Creates the user AND their wallet in one transaction: both succeed or neither does. */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (users.existsByUsername(request.username())) {
            throw new DuplicateResourceException("Username already taken");
        }
        if (users.existsByEmail(request.email())) {
            throw new DuplicateResourceException("Email already registered");
        }
        User user = users.save(new User(request.username(), request.email(),
                passwordEncoder.encode(request.password()), Role.USER));
        wallets.save(new Wallet(user));
        return tokenFor(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        // Throws BadCredentialsException (handled as 401) when the password is wrong.
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        User user = users.findByUsername(request.username()).orElseThrow();
        return tokenFor(user);
    }

    private AuthResponse tokenFor(User user) {
        return new AuthResponse(jwtService.generateToken(user.getUsername(), user.getRole()),
                "Bearer", jwtService.getExpirationMinutes());
    }
}
