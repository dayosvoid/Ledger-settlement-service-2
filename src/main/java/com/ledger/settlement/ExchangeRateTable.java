package com.ledger.settlement;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class ExchangeRateTable {

    private static volatile Map<String, BigDecimal> table;

    static void load() {
        table = fetchFromDownstream();
    }

    private static Map<String, BigDecimal> fetchFromDownstream() {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Map<String, BigDecimal> result = new ConcurrentHashMap<>();
        result.put("NGN", BigDecimal.ONE);
        result.put("USD", BigDecimal.valueOf(1500));
        return result;
    }

    static BigDecimal rateFor(String currency) {
        return table.getOrDefault(currency, BigDecimal.ONE);
    }

    private ExchangeRateTable() {}
}
