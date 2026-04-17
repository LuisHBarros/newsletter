package com.assine.billing.adapters.inbound.rest.payment;

import com.assine.billing.adapters.inbound.rest.common.PageResponse;
import com.assine.billing.adapters.inbound.rest.payment.dto.PaymentFilter;
import com.assine.billing.adapters.inbound.rest.payment.dto.PaymentResponse;
import com.assine.billing.application.payment.GetPaymentService;
import com.assine.billing.domain.payment.model.Payment;
import com.assine.billing.domain.payment.model.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Read-only endpoints for billing payments. Creation is driven by inbound events
 * ({@code subscription.requested}), not by HTTP callers.
 */
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private static final String SCOPE_ADMIN = "billing:admin";
    private final GetPaymentService getPaymentService;

    private boolean isAdmin(Jwt jwt) {
        List<String> scopes = jwt.getClaimAsStringList("scope");
        return scopes != null && scopes.contains(SCOPE_ADMIN);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> getPayment(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        Payment payment = getPaymentService.execute(id);
        verifyVisibility(payment, jwt);
        return ResponseEntity.ok(PaymentResponse.from(payment));
    }

    @GetMapping
    public ResponseEntity<PageResponse<PaymentResponse>> listPayments(
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) UUID subscriptionId,
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            Pageable pageable,
            @AuthenticationPrincipal Jwt jwt) {
        if (!isAdmin(jwt) && customerId == null && subscriptionId == null) {
            throw new AccessDeniedException("Non-admin callers must filter by customerId or subscriptionId");
        }
        PaymentFilter filter = new PaymentFilter(customerId, subscriptionId, status, startDate, endDate);
        Page<Payment> page = getPaymentService.find(filter, pageable);
        Page<PaymentResponse> mapped = page.map(PaymentResponse::from);
        return ResponseEntity.ok(PageResponse.from(mapped));
    }

    private UUID extractUserId(Jwt jwt) {
        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new AccessDeniedException("Invalid JWT: missing subject claim");
        }
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException e) {
            throw new AccessDeniedException("Invalid JWT: subject is not a valid UUID");
        }
    }

    private void verifyVisibility(Payment payment, Jwt jwt) {
        if (isAdmin(jwt)) return;
        UUID callerId = extractUserId(jwt);
        if (payment.getCustomer() == null || !callerId.equals(payment.getCustomer().getUserId())) {
            throw new AccessDeniedException("Access denied to payment");
        }
    }
}
