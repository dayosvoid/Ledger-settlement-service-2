package com.ledger.settlement;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettlementService {

    private final PaymentRepository repository;
    private final BigDecimal feeRate;

    // Constructor injection: Spring passes in the repository and the value of ledger.fee-rate.
    public SettlementService(PaymentRepository repository,
                             @Value("${ledger.fee-rate}") BigDecimal feeRate) {
        this.repository = repository;
        this.feeRate = feeRate;
    }

    /** Runs in one database transaction: the payment is saved completely or not at all. */
    @Transactional
    public PaymentResponse record(RecordPaymentRequest request) {
        PaymentEntity saved = repository.save(new PaymentEntity(
                UUID.randomUUID().toString(), request.merchantId(),
                request.amountMinor(), request.currency(), Instant.now()));
        return PaymentResponse.from(saved);
    }

    /** Total minus fee. The fee is truncated to whole minor units: 128450 x 0.031 = 3981.95 -> 3981. */
    @Transactional(readOnly = true)
    public SettlementResponse settle(String merchantId) {
        List<PaymentEntity> payments = repository.findByMerchantId(merchantId);
        if (payments.isEmpty()) {
            throw new MerchantNotFoundException(merchantId);
        }
        long total = 0;
        for (PaymentEntity payment : payments) {
            total += payment.getAmountMinor();
        }
        long fee = BigDecimal.valueOf(total).multiply(feeRate)
                .setScale(0, RoundingMode.DOWN).longValueExact();
        return new SettlementResponse(merchantId, total, fee, total - fee);
    }

    @Transactional(readOnly = true)
    public PaymentResponse findPayment(String id) {
        return repository.findById(id)
                .map(PaymentResponse::from)
                .orElseThrow(() -> new PaymentNotFoundException(id));
    }
}
