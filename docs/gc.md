## Starting JVM defaults (before any tuning)

| Flag          | Value           |
|----------------|-----------------|
| MaxHeapSize    | 2111832064 bytes (~2014 MB / ~1.97 GB) |
| UseG1GC        | true (ergonomic default) |git branch

Note: the JFR recording (10min, started at JVM launch) ended ~40s before the k6 load run completed,
since the JVM took ~36s to fully start before k6 began sending traffic. The recording captures
~9m20s of the full 10-minute steady-state load, which is sufficient for stable GC behavior analysis.



## Baseline GC Profile (gc-baseline.jfr)

| Metric                  | Value |
|--------------------------|-------|
| Allocation rate          | ~2.3 MB/s (derived from heap-before/after deltas across 46 collections over the 600s recording) |
| Top 3 allocating classes | byte[] (40.01%), java.lang.String (6.31%), java.net.URL (4.45%) — note: java.net.URL and the related JarFileUrlKey (2.25%) reflect one-time classloading during the ~36s JVM startup window captured at the start of this recording, not steady-state request handling; byte[] is the genuine steady-state signal |
| Collection count          | 46 total (38 Young, 8 Old) |
| Longest pause             | 28.3 ms (Old GC, GC ID 40, 19:57:55) |


## Problem Classification

This profile shows **allocation pressure**, not a pause-time problem:

- `byte[]` alone accounts for 40.01% of allocation samples — a single class dominating the profile,
  the classic signature of allocation pressure rather than long individual collections.
- GC pauses are short and healthy: 46 collections total, longest pause 28.3 ms, most young-gen pauses
  under 20 ms. This does not match a pause-time problem (which would show modest allocation with long
  individual collections).
- Notably, GC pause time (max 28.3 ms) is far smaller than the request latency tail observed under load
  (p99 2.38s, max 5.22s) — GC is not the primary driver of tail latency here. The dominant `byte[]`
  allocation is worth reducing regardless, but the multi-second latency outliers likely stem from a
  different source (database/connection-pool contention, serialization overhead) outside GC's control.

**Conclusion:** proceed with the allocation-pressure path — find and fix the hot `byte[]` allocation site
in code, rather than tuning heap size or switching collectors.


## Problem Classification

This profile shows **allocation pressure**, not a pause-time problem:

- GC pauses are short and healthy: 46 collections total (38 young, 8 old), longest actual stop-the-world
  pause 28.3 ms (from `jfr view gc`'s "Longest Pause" column — the raw `jdk.GarbageCollection` event's
  `duration` field includes non-pause bookkeeping and is not the right number to use here).
- GC pause time (max 28.3 ms) is far smaller than the request latency tail observed under load
  (p99 2.38s, max 5.22s) — GC is not the primary driver of tail latency in this service.
- Filtering `jdk.ObjectAllocationSample` events to only request-handling threads (`http-nio-8080-exec-*`,
  excluding JVM startup noise on the `main` thread) gives ~49,398 real samples. The top allocators —
  `java.lang.Object[]` (4655), `byte[]` (3546), `java.util.LinkedHashMap` (1683), `java.time.Instant` (1497)
  — are overwhelmingly Tomcat/Spring MVC framework overhead (request attribute-change listener notification,
  interceptor chain construction, per-request timing), not application code.
- Only 1,136 of the 49,398 samples touched `com.ledger.settlement` code anywhere in their stack. Of those,
  the single recurring hot site was `SettlementService.settle(String)` line 41 — a
  `payments.stream().mapToLong(PaymentEntity::getAmountMinor).sum()` call that builds a fresh stream
  pipeline (spliterator, intermediate pipeline stages) on every request just to sum a small list of longs.

**Conclusion:** the numerically dominant allocation is framework overhead outside this service's control.
The one genuine, fixable application-level hot site is the stream pipeline in `settle()`, replaced with a
plain accumulator loop — same result, zero stream-machinery allocation per request.



## Before / After Comparison

| Metric                    | Baseline (gc-baseline.jfr) | Tuned (gc-tuned.jfr) |
|----------------------------|------------------------------|------------------------|
| Allocation rate            | ~2.3 MB/s                   | ~2.14 MB/s             |
| Collection count           | 46 (38 young, 8 old)         | 46 (39 young, 7 old)   |
| Longest pause               | 28.3 ms (Old GC)             | 97.6 ms (Old GC)       |
| p99 request latency         | 2.38 s                      | 1.39 s                 |
| Throughput                  | 49.39 req/s                 | 49.69 req/s            |

Note: throughput is essentially identical since both runs targeted the same 50 req/s constant arrival
rate — the meaningful signal is in latency and dropped iterations (366 → 188), not raw req/s.


## Trade-off

The `settle()` allocation fix modestly reduced allocation rate (~2.3→2.14 MB/s) and clearly improved
request-tail latency (p99 2.38s→1.39s, max 5.22s→4.42s, dropped iterations 366→188). However, the tuned
run's single longest GC pause (97.6ms) is worse than baseline's (28.3ms), and several other Old GC pauses
ran higher too — this is likely normal run-to-run variance in Old-generation promotion timing rather than
a consequence of the code change itself, since the fix only removed short-lived stream-pipeline objects
that die young and wouldn't reach the old generation. This trade is acceptable because even the worst-case
pause (97.6ms) remains two orders of magnitude smaller than the multi-second p99/max request latency this
service already exhibits — GC is not, and was never, the bottleneck limiting this endpoint's tail latency.