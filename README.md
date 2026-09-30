# Ledger settlement service: JMH benchmarks

Requires Java 25 and Maven.

## Build and run

    mvn clean package -DskipTests -pl benchmarks -am && java -jar benchmarks/target/benchmarks.jar -rf json -rff results.json

Add `-prof gc` to the `java -jar` part to record bytes allocated per operation.
The full configuration (5 warmup, 10 measurement iterations, 3 forks) takes a long time, so run it on a quiet machine.

## Results

See `docs/benchmarks.md` for the method rationale, the dead-code-elimination evidence, the results table with error bars, and the limits of this suite. Raw output is in `results.json`.
