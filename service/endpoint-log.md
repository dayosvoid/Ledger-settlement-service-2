# Endpoint log

| Request | Status | Body summary | Time |
|---------|--------|--------------|------|
| POST /payments (MR-4471, 128450, GBP) | 201 | Location header; body has the new payment id | 4.435s |
| POST /payments (amountMinor -5) | 400 | application/problem+json; title "Validation failed", errors.amountMinor | 0.159s |
| POST /payments (merchantId "") | 400 | application/problem+json; errors.merchantId | 0.017s |
| GET /payments/settlement?merchantId=MR-4471 | 200 | total 128450, fee 3981, owed 124469 | 1.200s |
| GET /payments/settlement?merchantId=MR-0000 | 404 | application/problem+json; "Merchant not found: MR-0000" | 0.119s |

The first call is slower because the application had just started.