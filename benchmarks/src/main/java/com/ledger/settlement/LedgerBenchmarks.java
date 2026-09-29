package com.ledger.settlement;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import tools.jackson.databind.json.JsonMapper;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(3)
public class LedgerBenchmarks {

    /** Inputs live in non-final fields, filled at runtime, so the JIT cannot fold them away. */
    @State(Scope.Benchmark)
    public static class Inputs {
        String merchantId;
        BigDecimal feeRate;
        List<PaymentEntity> payments;
        PaymentEntity entity;
        PaymentResponse response;
        JsonMapper mapper;

        @Setup
        public void setUp() {
            merchantId = "m-1";
            feeRate = new BigDecimal("0.031");
            Random rnd = new Random(42);
            payments = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                payments.add(new PaymentEntity("p" + i, merchantId,
                        1_000L + rnd.nextInt(500_000), "NGN", Instant.now()));
            }
            entity = payments.get(0);
            response = PaymentResponse.from(entity);
            mapper = JsonMapper.builder().build();
        }
    }

    @Benchmark
    public PaymentResponse mapping(Inputs in) {
        return PaymentResponse.from(in.entity);
    }

    @Benchmark
    public String jsonSerialisation(Inputs in) {
        return in.mapper.writeValueAsString(in.response);
    }

    @Benchmark
    public SettlementResponse settlement(Inputs in) {
        return SettlementService.calculate(in.merchantId, in.payments, in.feeRate);
    }
}