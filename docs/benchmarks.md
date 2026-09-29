# Benchmarks

## Why these three methods

- **`PaymentResponse.from(PaymentEntity)`**: on the hot path of every payment response, and cheap enough that a careless benchmark would be optimised away entirely.
- **Jackson serialisation of `PaymentResponse`**: the per-request cost of turning our response type into JSON, dominated by string building and allocation. The method is Jackson's, applied to our type.
- **`SettlementService.calculate`**: the money arithmetic, extracted from `settle()` so it can be measured without the database. It stays on `long` minor units and `BigDecimal` because binary floating point cannot hold a money value exactly.

## Dead code elimination evidence

Both runs used 1 fork, 3 warmup and 3 measurement iterations of 1 s each, JDK 25.0.4.1, static final inputs.

| Method | Broken (result discarded) | Fixed (result returned) | Change |
|---|---|---|---|
| `PaymentResponse.from` | 0.816 ± 0.322 ns/op | 9.521 ± 8.956 ns/op | about 11.7x; intervals overlap, so formally inconclusive |
| `SettlementService.calculate` | 98.594 ± 64.279 ns/op | 84.124 ± 11.297 ns/op | intervals overlap, inconclusive |

Findings:
- A 0.8 ns "mapping" is not physically plausible for allocating a five-field record, so the JIT removed the work.
- The settlement calculation was not eliminated even when its result was discarded, so whether DCE happens depends on the method.
- The error bars are wide because 3 iterations from 1 fork is too little data. The final run fixes this.