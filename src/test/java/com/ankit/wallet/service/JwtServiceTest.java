package com.ankit.wallet.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ankit.wallet.entity.Role;
import com.ankit.wallet.security.JwtService;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-key-that-is-long-enough-for-hmac-sha256-0123456789";

    @Test
    void generatedToken_isValid_andCarriesUsername() {
        JwtService jwt = new JwtService(SECRET, 60);

        String token = jwt.generateToken("alice", Role.USER);

        assertTrue(jwt.isValid(token));
        assertEquals("alice", jwt.extractUsername(token));
    }

    @Test
    void tamperedToken_isRejected() {
        JwtService jwt = new JwtService(SECRET, 60);
        String token = jwt.generateToken("alice", Role.USER);

        String tampered = token.substring(0, token.length() - 3) + (token.endsWith("abc") ? "xyz" : "abc");

        assertFalse(jwt.isValid(tampered));
    }

    @Test
    void tokenSignedWithAnotherSecret_isRejected() {
        JwtService issuer = new JwtService(SECRET, 60);
        JwtService other = new JwtService("a-completely-different-secret-key-that-is-also-long-enough-987654", 60);

        assertFalse(other.isValid(issuer.generateToken("alice", Role.USER)));
    }

    @Test
    void expiredToken_isRejected() {
        JwtService jwt = new JwtService(SECRET, -1); // expired one minute ago

        assertFalse(jwt.isValid(jwt.generateToken("alice", Role.USER)));
    }

    @Test
    void garbageInput_isRejected() {
        JwtService jwt = new JwtService(SECRET, 60);

        assertFalse(jwt.isValid("not.a.jwt"));
        assertFalse(jwt.isValid(""));
    }
}
