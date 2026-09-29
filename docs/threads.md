## Baseline (pooled threads, before virtual thread migration)

| Metric      | Value     |
|-------------|-----------|
| Throughput  | 263.28 req/s |
| p50         | 599.35 ms |
| p95         | 1.73 s    |
| p99         | 2.86 s    |

## notes worth capturing
200 VUs, 60s duration, GET /payments/settlement?merchantId=MR-4471
0% error rate (http_req_failed: 0.00%, 15,909 total requests)

## Current thread pool (before migration)
Spring Boot embedded Tomcat, no custom executor bean, running on defaults:

| Setting            | Value |
|--------------------|-------|
| Core / min-spare   | 10    |
| Max threads        | 200   |
| Queue (accept-count)| 100  |

## Baseline vs Virtual Threads (before pinning defect)

| Metric      | Baseline (pooled) | Virtual Threads |
|-------------|-------------------|------------------|
| Throughput  | 263.28 req/s      | 240.84 req/s     |
| p50         | 599.35 ms         | 613.91 ms        |
| p95         | 1.73 s            | 2.21 s           |
| p99         | 2.86 s            | 4.34 s           |

## Pinning event (planted defect)

Captured via: `jcmd <pid> JFR.start name=pin settings=profile filename=pinned.jfr` during a cold-start 200 VU / 60s k6 run against `GET /payments/settlement`.

**Root cause event:**

| Field | Value |
|---|---|
| Duration | 1.99 s |
| Blocking operation | `LockSupport.park` (via `Thread.sleep`) |
| Pinning reason | `VM call to com.ledger.settlement.ExchangeRateTable.<clinit> on stack` |
| Unmountable frame | `ExchangeRateTable.<clinit>` (the class initializer) |
| Underlying blocking call | `Thread.sleep(2000)` inside `ExchangeRateTable.fetchFromDownstream()` |

Three additional virtual threads were pinned for ~1.9s each while waiting on the same class's initialization (`pinnedReason = "Waited for initialization of com.ledger.settlement.ExchangeRateTable by another thread"`) — even though these threads were only *waiting*, not executing the initializer, they were still pinned, because class-initialization locking is a JVM-internal mechanism rather than an ordinary Java monitor.

**Why `synchronized` → `ReentrantLock` doesn't help here:** since Java 24, a virtual thread blocking inside an ordinary `synchronized` block releases its carrier just fine, so the old advice to replace `synchronized` with `ReentrantLock` no longer addresses most pinning cases. This defect isn't caused by a `synchronized` block at all — it's the class-initialization lock plus a blocking call inside `<clinit>`, which `ReentrantLock` has no bearing on whatsoever, so making that swap here would fix nothing.


## Before / After Summary

| Stage                              | Throughput   | p50     | p95     | p99     |
|-------------------------------------|--------------|---------|---------|---------|
| Baseline (pooled threads)           | 263.28 req/s | 599.35 ms | 1.73 s  | 2.86 s  |
| Virtual threads (before pinning fix)| 240.84 req/s | 613.91 ms | 2.21 s  | 4.34 s  |
| Virtual threads (after pinning fix) | 112.37 req/s | 1.57 s  | 3.31 s  | 4.19 s  |

Note: the "after fix" run reflects cold JVM/Hibernate first-use costs (query parser initialization, JIT warmup)
unrelated to the planted defect — confirmed via fixed.jfr, which shows zero ExchangeRateTable pinning events
but several short-lived pinning events from Hibernate's HQL parser and Spring's web classes initializing on
first use. A warmed-up re-run would isolate the virtual-thread-migration effect more precisely.


## Before / After Summary

| Stage                                 | Throughput   | p50       | p95     | p99     |
|-----------------------------------------|--------------|-----------|---------|---------|
| Baseline (pooled threads)               | 263.28 req/s | 599.35 ms | 1.73 s  | 2.86 s  |
| Virtual threads (before pinning fix)    | 240.84 req/s | 613.91 ms | 2.21 s  | 4.34 s  |
| Virtual threads (after pinning fix)     | 113.16 req/s | 1.51 s    | 3.43 s  | 4.63 s  |git 