package com.assine.billing.application.payment;

import com.assine.billing.adapters.inbound.rest.payment.dto.PaymentFilter;
import com.assine.billing.domain.payment.model.Payment;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Ported from {@code TransferSpecification}. Since payments have a single owner
 * (customer), there is no DEBIT/CREDIT split; filters are applied conjunctively.
 */
public class PaymentSpecification {

    private PaymentSpecification() {}

    public static Specification<Payment> withFilters(PaymentFilter filter) {
        return (root, query, cb) -> {
            Predicate predicate = cb.conjunction();

            if (filter == null) {
                return predicate;
            }

            if (filter.customerId() != null) {
                predicate = cb.and(predicate, cb.equal(root.get("customer").get("id"), filter.customerId()));
            }

            if (filter.subscriptionId() != null) {
                UUID subId = filter.subscriptionId();
                predicate = cb.and(predicate, cb.equal(root.get("subscription").get("id"), subId));
            }

            if (filter.status() != null) {
                predicate = cb.and(predicate, cb.equal(root.get("status"), filter.status()));
            }

            if (filter.startDate() != null) {
                predicate = cb.and(predicate,
                    cb.greaterThanOrEqualTo(root.get("createdAt"),
                        filter.startDate().atStartOfDay().toInstant(ZoneOffset.UTC)));
            }

            if (filter.endDate() != null) {
                LocalDate endDate = filter.endDate();
                predicate = cb.and(predicate,
                    cb.lessThanOrEqualTo(root.get("createdAt"),
                        endDate.atTime(23, 59, 59).toInstant(ZoneOffset.UTC)));
            }

            return predicate;
        };
    }
}
