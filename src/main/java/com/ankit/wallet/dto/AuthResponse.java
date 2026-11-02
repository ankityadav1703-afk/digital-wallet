package com.ankit.wallet.dto;

public record AuthResponse(String token, String tokenType, long expiresInMinutes) {
}
