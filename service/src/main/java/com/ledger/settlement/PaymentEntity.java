package com.ledger.settlement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One row of the payments table.
 *
 * Why this is a mutable class and not a record: JPA (Hibernate) builds an entity with its
 * no-argument constructor and then fills the fields in, and it may also generate subclasses
 * (proxies) of the entity. A record is final, has final fields and no no-argument constructor,
 * so Hibernate can do none of that. The API types (RecordPaymentRequest, PaymentResponse) are
 * records instead.
 */
@Entity
@Table(name = "payments")
public class PaymentEntity {

    @Id
    private String id;

    @Column(name = "merchant_id", nullable = false)
    private String merchantId;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;            // minor units, never a floating point type

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    public PaymentEntity() { }           // required by JPA

    public PaymentEntity(String id, String merchantId, long amountMinor,
                         String currency, Instant recordedAt) {
        this.id = id;
        this.merchantId = merchantId;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.recordedAt = recordedAt;
    }

    public String getId() { return id; }
    public String getMerchantId() { return merchantId; }
    public long getAmountMinor() { return amountMinor; }
    public String getCurrency() { return currency; }
    public Instant getRecordedAt() { return recordedAt; }
}