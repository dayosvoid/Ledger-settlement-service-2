package com.ledger.settlement;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Warmup;

/** Same as DeadCodeBenchmark, except each method RETURNS its result so JMH consumes it. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(1)
public class FixedBenchmark {

    private static final BigDecimal FEE_RATE = new BigDecimal("0.031");
    private static final List<PaymentEntity> PAYMENTS = List.of(
            new PaymentEntity("p1", "m-1", 128450L, "NGN", Instant.EPOCH),
            new PaymentEntity("p2", "m-1", 50000L, "NGN", Instant.EPOCH),
            new PaymentEntity("p3", "m-1", 7250L, "NGN", Instant.EPOCH));

    @Benchmark
    public SettlementResponse fixedSettlement() {
        return SettlementService.calculate("m-1", PAYMENTS, FEE_RATE);
    }

    @Benchmark
    public PaymentResponse fixedMapping() {
        return PaymentResponse.from(PAYMENTS.get(0));
    }
}
