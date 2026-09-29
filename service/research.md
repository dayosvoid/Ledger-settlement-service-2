# Research

## Documentation
**Page: Testcontainers** (read on September 20, 2026)
> "When doing so, the connection details take precedence over any connection-related configuration properties."

## Agent Review

Prompt given: "Add a GET /payments/{id} endpoint to a Spring Boot 4 service that has PaymentController, PaymentEntity and PaymentRepository"

| # | Defect | Found? | Evidence (agent's code) | My version |
|---|--------|--------|-------------------------|------------|
| 1 | Field injection with @Autowired instead of a constructor parameter | No | `public PaymentController(SettlementService settlementService)` | Same pattern in my controller |
| 2 | JPA entity returned as the API type | No | `getPaymentById` returns `PaymentResponse`; the service maps the entity to it | I reuse `PaymentResponse.from(entity)` instead of repeating the mapping |
| 3 | Missing @Valid or missing constraint annotations | No (not applicable) | `@PathVariable String id` on a GET; no request body for @Valid to check | None needed |
| 4 | Error body that is not a problem detail | No | `handleMerchantNotFound` returns a `ProblemDetail` with a 404 | See the additional findings; I use a dedicated exception and handler |

### Additional findings (real problems, not among the four)
- **Wrong exception reused.** A missing payment throws `MerchantNotFoundException`, so the title says "Merchant not found". With my exception class the message would read "Merchant not found: Payment not found: <id>". Fix: a dedicated `PaymentNotFoundException` with its own 404 handler.
- **Duplicate handler.** It adds `@ExceptionHandler(MerchantNotFoundException.class)`, but my `ProblemHandler` already handles that exception. In the same class that is an ambiguous mapping. In the controller, it would override the shared handler for that controller and skip my shared `problem(...)` helper.
- **Missing transaction annotation.** `findPayment` has no `@Transactional(readOnly = true)`, unlike my other service methods.
- **Doesn't fit my service.** It uses a field called `paymentRepository`, but mine is `repository`, so it wouldn't compile as pasted.

## Integration test result
`mvn clean verify` on 2026-09-20, against a Testcontainers PostgreSQL container:

```
[INFO] Running com.ledger.settlement.PaymentControllerIT
[INFO] Container is started (JDBC URL: jdbc:postgresql://localhost:61293/test?loggerLevel=OFF)
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS

