package com.ledger.settlement;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Body of POST /payments. */
public record RecordPaymentRequest(
        @NotBlank String merchantId,
        @Positive long amountMinor,
        @NotBlank @Size(min = 3, max = 3) String currency) { }