package com.assine.subscriptions.adapters.inbound.rest.subscription;

import com.assine.subscriptions.adapters.inbound.rest.common.PageResponse;
import com.assine.subscriptions.adapters.inbound.rest.subscription.dto.CancelSubscriptionRequest;
import com.assine.subscriptions.adapters.inbound.rest.subscription.dto.CreateSubscriptionRequest;
import com.assine.subscriptions.adapters.inbound.rest.subscription.dto.SubscriptionResponse;
import com.assine.subscriptions.adapters.inbound.rest.subscription.dto.UpdateSubscriptionRequest;
import com.assine.subscriptions.application.subscription.SubscriptionService;
import com.assine.subscriptions.domain.subscription.model.Subscription;
import com.assine.subscriptions.domain.subscription.model.SubscriptionStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private static final String SCOPE_ADMIN = "subscriptions:admin";

    private UUID extractUserId(Jwt jwt) {
        String sub = jwt.getSubject();
        if (sub == null || sub.isBlank()) {
            throw new AccessDeniedException("Invalid JWT: missing subject claim");
        }
        try {
            return UUID.fromString(sub);
        } catch (IllegalArgumentException e) {
            throw new AccessDeniedException("Invalid JWT: subject is not a valid UUID");
        }
    }

    private boolean isAdmin(Jwt jwt) {
        List<String> scopes = jwt.getClaimAsStringList("scope");
        return scopes != null && scopes.contains(SCOPE_ADMIN);
    }

    private void verifyOwnership(Subscription subscription, UUID userId, Jwt jwt) {
        if (!subscription.getUserId().equals(userId) && !isAdmin(jwt)) {
            throw new AccessDeniedException("Access denied to subscription");
        }
    }

    @PostMapping
    public ResponseEntity<SubscriptionResponse> createSubscription(
            @Valid @RequestBody CreateSubscriptionRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID currentUserId = extractUserId(jwt);
        if (!isAdmin(jwt) && !currentUserId.equals(request.userId())) {
            throw new AccessDeniedException("Cannot create subscription on behalf of another user");
        }
        Subscription subscription = subscriptionService.createSubscription(
                request.userId(),
                request.planId(),
                request.metadata()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(subscription));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SubscriptionResponse> getSubscription(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        Subscription subscription = subscriptionService.getSubscription(id);
        UUID userId = extractUserId(jwt);
        verifyOwnership(subscription, userId, jwt);
        return ResponseEntity.ok(toResponse(subscription));
    }

    @GetMapping
    public ResponseEntity<PageResponse<SubscriptionResponse>> getSubscriptions(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) UUID planId,
            @RequestParam(required = false) SubscriptionStatus status,
            Pageable pageable,
            @AuthenticationPrincipal Jwt jwt) {
        UUID currentUserId = extractUserId(jwt);
        boolean admin = isAdmin(jwt);

        // Non-admin users can only view their own subscriptions
        if (!admin && userId != null && !userId.equals(currentUserId)) {
            throw new AccessDeniedException("Access denied to user subscriptions");
        }

        Page<Subscription> subscriptions;
        UUID targetUserId = admin && userId != null ? userId : currentUserId;

        if (userId != null && planId != null) {
            Subscription one = subscriptionService.getUserSubscription(targetUserId, planId);
            subscriptions = new PageImpl<>(List.of(one), pageable, 1);
        } else if (planId != null && admin) {
            subscriptions = subscriptionService.getPlanSubscriptions(planId, pageable);
        } else if (status != null && admin) {
            subscriptions = subscriptionService.getSubscriptionsByStatus(status, pageable);
        } else if (userId != null || targetUserId.equals(currentUserId)) {
            // Default to current user's subscriptions
            subscriptions = subscriptionService.getUserSubscriptions(targetUserId, pageable);
        } else {
            throw new IllegalArgumentException("At least one filter parameter is required");
        }

        Page<SubscriptionResponse> mapped = subscriptions.map(this::toResponse);
        return ResponseEntity.ok(PageResponse.from(mapped));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SubscriptionResponse> updateSubscription(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSubscriptionRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        Subscription subscription = subscriptionService.getSubscription(id);
        UUID userId = extractUserId(jwt);
        verifyOwnership(subscription, userId, jwt);

        // Status, currentPeriodStart and canceledAt are not mutable via REST:
        // they are owned by the EventRouter handlers that consume billing events
        // (activate / renewPeriod / confirmCanceled). See UpdateSubscriptionRequest.
        Subscription updated = subscriptionService.updateSubscription(
                id,
                request.currentPeriodEnd(),
                request.cancelAtPeriodEnd(),
                request.metadata()
        );
        return ResponseEntity.ok(toResponse(updated));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<SubscriptionResponse> cancelSubscription(
            @PathVariable UUID id,
            @Valid @RequestBody CancelSubscriptionRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        Subscription subscription = subscriptionService.getSubscription(id);
        UUID userId = extractUserId(jwt);
        verifyOwnership(subscription, userId, jwt);

        subscriptionService.cancelSubscription(id, request.cancelAtPeriodEnd(), request.reason());
        Subscription updated = subscriptionService.getSubscription(id);
        return ResponseEntity.ok(toResponse(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<SubscriptionResponse> deleteSubscription(
            @PathVariable UUID id,
            @RequestParam(required = false) String reason,
            @AuthenticationPrincipal Jwt jwt) {
        Subscription subscription = subscriptionService.getSubscription(id);
        UUID userId = extractUserId(jwt);
        verifyOwnership(subscription, userId, jwt);

        subscriptionService.cancelSubscription(id, true, reason);
        Subscription updated = subscriptionService.getSubscription(id);
        return ResponseEntity.ok(toResponse(updated));
    }

    private SubscriptionResponse toResponse(Subscription subscription) {
        return new SubscriptionResponse(
                subscription.getId(),
                subscription.getUserId(),
                subscription.getPlan().getId(),
                subscription.getPlan().getName(),
                subscription.getStatus(),
                subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd(),
                subscription.getCancelAtPeriodEnd(),
                subscription.getCanceledAt(),
                subscription.getTrialStart(),
                subscription.getTrialEnd(),
                subscription.getMetadata(),
                subscription.getCreatedAt(),
                subscription.getUpdatedAt()
        );
    }
}
