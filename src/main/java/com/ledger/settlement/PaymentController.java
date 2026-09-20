package com.ledger.settlement;

import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final SettlementService service;

    public PaymentController(SettlementService service) {
        this.service = service;
    }

    /** Records a payment. Returns 201 with a Location header pointing at the new payment. */
    @PostMapping
    public ResponseEntity<PaymentResponse> record(@Valid @RequestBody RecordPaymentRequest request) {
        PaymentResponse created = service.record(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    /** Returns one payment by id, or a 404 problem detail if there is none. */
    @GetMapping("/{id}")
    public PaymentResponse byId(@PathVariable String id) {
        return service.findPayment(id);
    }

    /** What the merchant is owed. */
    @GetMapping("/settlement")
    public SettlementResponse settlement(@RequestParam String merchantId) {
        return service.settle(merchantId);
    }
}
