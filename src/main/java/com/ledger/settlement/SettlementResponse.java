package com.ledger.settlement;

/** What a merchant is owed. All figures are in minor units. */
public record SettlementResponse(String merchantId, long totalMinor, long feeMinor, long owedMinor) { }
