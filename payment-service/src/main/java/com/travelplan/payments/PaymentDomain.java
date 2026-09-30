package com.travelplan.payments;

import java.time.Instant;
import java.util.UUID;

final class PaymentDomain {
    private PaymentDomain() {}

    record Gateway(UUID id, String provider, String displayName, boolean enabled, Instant createdAt) {}

    record Method(UUID id, UUID userId, UUID gatewayId, String provider, String providerReference,
                  String displayLabel, String status, String displayMetadata, String failureCode,
                  String failureStage, String idempotencyKey, String requestHash, Instant createdAt) {}

    record ConnectAttempt(Method method, boolean shouldInspect, boolean created) {}

    static final class NotFound extends RuntimeException {}

    static final class Conflict extends RuntimeException {
        final String code;
        Conflict(String code) { this.code = code; }
    }
}
