package com.ledger.settlement;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data generates the implementation, and the query comes from the method name. */
public interface PaymentRepository extends JpaRepository<PaymentEntity, String> {
    List<PaymentEntity> findByMerchantId(String merchantId);
}