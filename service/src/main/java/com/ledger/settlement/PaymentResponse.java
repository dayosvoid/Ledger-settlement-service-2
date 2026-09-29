package com.ledger.settlement;

import java.time.Instant;

/** What the API returns for a payment. The entity never leaves the service layer. */
public record PaymentResponse(String id, String merchantId, long amountMinor,
                              String currency, Instant recordedAt) {
    static PaymentResponse from(PaymentEntity e) {
        return new PaymentResponse(e.getId(), e.getMerchantId(), e.getAmountMinor(),
                e.getCurrency(), e.getRecordedAt());
    }
}
