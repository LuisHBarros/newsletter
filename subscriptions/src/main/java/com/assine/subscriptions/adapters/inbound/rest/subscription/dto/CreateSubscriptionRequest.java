package com.assine.subscriptions.adapters.inbound.rest.subscription.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.UUID;

public record CreateSubscriptionRequest(
    @NotNull(message = "User ID is required")
    UUID userId,

    @NotNull(message = "User email is required")
    @Email(message = "User email must be valid")
    String userEmail,

    String userName,

    @NotNull(message = "Plan ID is required")
    UUID planId,

    Map<String, Object> metadata
) {}
