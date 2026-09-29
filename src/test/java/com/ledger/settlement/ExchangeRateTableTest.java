package com.ledger.settlement;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ExchangeRateTableTest {

    @Test
    void concurrentReadsAfterLoadReturnConsistentValues() throws InterruptedException {
        ExchangeRateTable.load();

        int threadCount = 50;
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        BigDecimal[] results = new BigDecimal[threadCount];

        for (int i = 0; i < threadCount; i++) {
            int idx = i;
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                results[idx] = ExchangeRateTable.rateFor("USD");
            });
        }

        ready.await();
        go.countDown();
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);

        for (BigDecimal r : results) {
            assertThat(r).isEqualByComparingTo(BigDecimal.valueOf(1500));
        }
    }
}