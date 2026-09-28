# GC Under Load: Ledger Settlement Service

## Starting JVM defaults (before any tuning)

| Flag        | Value                                  |
|-------------|----------------------------------------|
| MaxHeapSize | 2111832064 bytes (~2014 MB, ergonomic) |
| UseG1GC     | true (ergonomic default)               |

## Method

- Load: `perf/steady-load.js`, k6 constant-arrival-rate, 50 req/s for 10 minutes against
  `GET /payments/settlement?merchantId=MR-4471`.
- Recording: `java -XX:StartFlightRecording=duration=10m,filename=<name>.jfr,settings=profile -jar ...`
  started at JVM launch. The JVM took ~35 s to start, so the recording began before load did and
  ended ~40 s before the k6 run finished.
- Each configuration was measured once. There are no repeated runs and therefore no error bars.

## Baseline GC profile (gc-baseline.jfr)

| Metric                  | Value |
|-------------------------|-------|
| Collection count        | 46 (38 young, 8 old) |
| Longest pause           | 28.3 ms (Old GC), from the "Longest Pause" column of `jfr view gc` |
| Allocation rate         | ~1.2 MB/s over the load period, estimated from heap-before minus previous heap-after in `jfr view gc`. The whole-recording average is higher because JVM startup allocates in a burst. |
| Top allocating classes  | Request threads only (`http-nio-8080-exec-*`, 49,398 samples), by sample count: `java.lang.Object[]` 4,655; `byte[]` 3,546; `java.util.LinkedHashMap` 1,683 |

The recording-wide `allocation-by-class` view was not used: `byte[]` (40%), `java.net.URL` and
`JarFileUrlKey` there come from Spring Boot jar loading on the `main` thread in the first two seconds.

## Problem classification

The profile shows **allocation pressure in framework code, not a pause problem**.

- Pauses are short: 46 collections, longest 28.3 ms, most young pauses under 20 ms.
- The request latency tail (p99 2.38 s, max 5.22 s) is far larger than any GC pause, so GC is not
  the main driver of tail latency. Both runs started cold, so the tail likely includes JVM and
  Hibernate warmup as well.
- Most sampled allocation on request threads is Tomcat and Spring MVC overhead
  (`CopyOnWriteArrayList.toArray()` in `Request.setAttribute`, `HandlerExecutionChain` building,
  `Request.markStartTime`), which this service does not control.
- Only 1,136 of the 49,398 samples had `com.ledger.settlement` in the stack. The recurring site was
  `SettlementService.settle(String)` line 41, `payments.stream().mapToLong(...).sum()`, which builds
  a stream pipeline on every request just to sum a small list.

**Decision:** allocation path, so fix the one application-level site in code. No heap or collector
flag was changed.

## The change

Replaced the stream in `settle()` with an accumulator loop. The result is identical and the per-call
stream objects are gone.

## Before / after

| Metric                 | Baseline (gc-baseline.jfr) | Tuned (gc-tuned.jfr) |
|------------------------|----------------------------|----------------------|
| Allocation rate (load period) | ~1.2 MB/s           | ~1.0 MB/s            |
| Steady young-GC cycle  | ~45 MB every ~44 s (~1.0 MB/s) | ~36 MB every ~34 s (~1.05 MB/s) |
| Collection count       | 46 (38 young, 8 old)       | 46 (39 young, 7 old) |
| Longest pause          | 28.3 ms (Old GC)           | 97.6 ms (Old GC)     |
| p99 request latency    | 2.38 s                     | 1.39 s               |
| Max request latency    | 5.22 s                     | 4.42 s               |
| Dropped iterations     | 366                        | 188                  |
| Throughput             | 49.39 req/s                | 49.69 req/s          |

**Reading the table.** Throughput is the same because both runs target 50 req/s. The steady young-GC
cycle shows no difference in allocation rate. The p99, max and dropped-iteration differences come
from one run each, both cold-started, with no error bars, so they are reported as **inconclusive**:
a separate 30 s smoke test at the same rate gave p99 1.03 s, which shows how much a single run moves.
The stream-to-loop change saves microseconds per request and cannot plausibly explain a ~1 s
change in p99.

## Trade-off

The longest pause rose from 28.3 ms to 97.6 ms in the tuned run. Removing short-lived stream objects
should not push more data into the old generation, so this is most likely old-generation timing
variance, but one run cannot confirm that. It is acceptable for this service because even 97.6 ms is
well below the request-latency tail already seen in both runs. The change is justified by the
profile evidence, not by the load-test deltas.