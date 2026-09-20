package com.ledger.settlement;

public class MerchantNotFoundException extends RuntimeException {
    public MerchantNotFoundException(String merchantId) {
        super("Merchant not found: " + merchantId);
    }
}