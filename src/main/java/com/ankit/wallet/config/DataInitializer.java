package com.ankit.wallet.config;

import com.ankit.wallet.entity.Role;
import com.ankit.wallet.entity.User;
import com.ankit.wallet.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Creates the first ADMIN account at startup (credentials come from configuration). */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final String adminUsername;
    private final String adminPassword;
    private final String adminEmail;

    public DataInitializer(UserRepository users, PasswordEncoder encoder,
                           @Value("${app.admin.username:}") String adminUsername,
                           @Value("${app.admin.password:}") String adminPassword,
                           @Value("${app.admin.email:admin@wallet.local}") String adminEmail) {
        this.users = users;
        this.encoder = encoder;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.adminEmail = adminEmail;
    }

    @Override
    public void run(String... args) {
        if (adminUsername.isBlank() || adminPassword.isBlank() || users.existsByUsername(adminUsername)) {
            return;
        }
        users.save(new User(adminUsername, adminEmail, encoder.encode(adminPassword), Role.ADMIN));
        log.info("Created admin user '{}'", adminUsername);
    }
}
