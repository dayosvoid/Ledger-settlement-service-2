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

## Configuration

All benchmarks use `@BenchmarkMode(Mode.AverageTime)`, `@OutputTimeUnit(TimeUnit.MICROSECONDS)`, `@Warmup(iterations = 5)`, `@Measurement(iterations = 10)` and `@Fork(3)`, with 1 s per iteration and 1 thread. JDK 25.0.4.1 (HotSpot), default JVM options, Windows machine, `-prof gc`. Command: `java -jar benchmarks/target/benchmarks.jar LedgerBenchmarks -prof gc -rf json -rff results.json`.

Inputs live in a `@State(Scope.Benchmark)` class filled in `@Setup`: 100 payments with amounts from a seeded `Random`, one entity, one response and one plain Jackson `JsonMapper`. Every benchmark method returns its result.

**Why forks matter:** each fork is a fresh JVM, so the JIT's compilation decisions are re-rolled per fork and one lucky or unlucky set of decisions cannot decide the score. The JSON benchmark allocated 752, 776 and 768 B/op in three forks.

## Results (final run, results.json)

| Benchmark | Time | Allocation |
|---|---|---|
| `PaymentResponse.from` | 0.013 ± 0.001 µs/op (13 ns) | 40.000 ± 0.001 B/op |
| Jackson `writeValueAsString(PaymentResponse)` | 1.532 ± 1.290 µs/op | 765.343 ± 6.778 B/op |
| `SettlementService.calculate` (100 payments) | 0.232 ± 0.032 µs/op | 240.002 ± 0.001 B/op |

Notes:
- Error is JMH's 99.9% confidence half-width over 30 measurement iterations (3 forks x 10).
- JSON: 28 of 30 iterations fall between 1.04 and 1.47 µs. Two outliers (2.49 and 11.66 µs), both in fork 1, widen the error. No GC ran during the 11.66 µs iteration, so the cause is not GC and is unknown. The mean is reported unedited.
- The settlement input is 100 payments. The dead-code table above used 3, so those numbers are not comparable.
- 40 B/op for mapping is consistent with one `PaymentResponse` object per call. The 240 B/op for settlement covers BigDecimal values, stream pipeline objects and the response record; it is not itemised.
- `results-run1.json` is an earlier run of the same configuration, kept as evidence. Its JSON time (3.803 ± 6.029 µs/op) was dominated by a single 50.6 µs first iteration in fork 1, so it was repeated on a quieter machine and the second run is reported above. Mapping and settlement agreed between the two runs.

## Comparisons

| Comparison | A | B | Verdict |
|---|---|---|---|
| Mapping, DCE broken vs fixed | 0.816 ± 0.322 ns | 9.521 ± 8.956 ns | Inconclusive (intervals overlap; 11.7x point difference, but 3 iterations from 1 fork) |
| Settlement, DCE broken vs fixed | 98.594 ± 64.279 ns | 84.124 ± 11.297 ns | Inconclusive |
| Mapping, static final inputs vs @State inputs | 9.521 ± 8.956 ns | 13 ± 1 ns | Inconclusive |
| Mapping, three runs of the final configuration | 18 ± 6, 19 ± 5, 13 ± 1 ns | | Inconclusive (drift not resolvable) |
| Settlement, three runs | 0.227 ± 0.016, 0.233 ± 0.023, 0.232 ± 0.032 µs | | No difference |
| JSON, three runs | 1.562 ± 0.529, 3.803 ± 6.029, 1.532 ± 1.290 µs | | Inconclusive (outliers in fork 1) |

"Three runs" means: an initial run without `-prof gc` (console output only, no JSON saved), `results-run1.json` (with `-prof gc`), and the final `results.json`. All three used the same configuration.

The three benchmarks measure different work, so they are not compared with each other.

## Limits: what this suite does not tell you

1. **Concurrency.** Every benchmark runs on one thread. It says nothing about lock contention, allocation pressure across many request threads, or GC behaviour under load.
2. **Cache effects at production data sizes.** Settlement runs over 100 payments and mapping over one entity, all hot in cache. A real merchant may have thousands or millions of payments, and the cost per payment will not stay at about 2.3 ns.
3. **Harness vs live request path.** There is no HTTP, no Spring MVC, no transaction and no database. `settle()` is dominated by the query, so the 0.232 µs calculation is a small part of a real settlement request. The Jackson mapper here is a plain `JsonMapper`, not the one Spring Boot configures.
4. **Environment.** One Windows machine, default JVM options, no CPU pinning or fixed clock speed. The run-to-run drift in mapping (18 to 13 ns) and the JSON outliers show that machine state moves the numbers. JMH also notes that compiler blackholes are experimental on this JVM.
5. **Input shape.** Amounts come from one seeded random distribution and one fee rate. Other amount ranges or fee rates may behave differently.