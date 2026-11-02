package com.ankit.wallet.exception;

import java.time.Instant;

/** Standard JSON error body returned by every failing request. */
public record ApiError(Instant timestamp, int status, String error, String message, String path) {
}
