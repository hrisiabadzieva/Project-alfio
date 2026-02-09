/**
 * This file is part of alf.io.
 *
 * alf.io is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * alf.io is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with alf.io.  If not, see <http://www.gnu.org/licenses/>.
 */
package alfio.manager.payment;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import alfio.manager.payment.custom.offline.CustomOfflineConfigurationManager;
import alfio.manager.payment.custom.offline.CustomOfflineConfigurationManager.CustomOfflinePaymentMethodDoesNotExistException;
import alfio.manager.support.PaymentResult;
import alfio.model.transaction.PaymentContext;
import alfio.model.transaction.PaymentMethod;
import alfio.model.transaction.PaymentProvider;
import alfio.model.transaction.PaymentProxy;
import alfio.model.transaction.Transaction;
import alfio.model.transaction.TransactionRequest;
import alfio.model.transaction.UserDefinedOfflinePaymentMethod;
import alfio.repository.EventRepository;
import alfio.repository.TicketReservationRepository;
import alfio.repository.TransactionRepository;
import alfio.util.ClockProvider;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import static alfio.manager.TicketReservationManager.NOT_YET_PAID_TRANSACTION_ID;
import static alfio.model.TicketReservation.TicketReservationStatus.CUSTOM_OFFLINE_PAYMENT;

@Component
public class CustomOfflinePaymentManager implements PaymentProvider {

    private static final Logger log = LoggerFactory.getLogger(CustomOfflinePaymentManager.class);
    private final ClockProvider clockProvider;
    private final TicketReservationRepository ticketReservationRepository;
    private final TransactionRepository transactionRepository;
    private final EventRepository eventRepository;
    private final CustomOfflineConfigurationManager customOfflineConfigurationManager;
    private final OfflinePaymentDeadlineResolver deadlineResolver;

    public CustomOfflinePaymentManager(
        ClockProvider clockProvider,
        TicketReservationRepository ticketReservationRepository,
        TransactionRepository transactionRepository,
        EventRepository eventRepository,
        CustomOfflineConfigurationManager customOfflineConfigurationManager,
        OfflinePaymentDeadlineResolver deadlineResolver
    ) {
        this.clockProvider = clockProvider;
        this.ticketReservationRepository = ticketReservationRepository;
        this.transactionRepository = transactionRepository;
        this.eventRepository = eventRepository;
        this.customOfflineConfigurationManager = customOfflineConfigurationManager;
        this.deadlineResolver = deadlineResolver;
    }

    @Override
    public Set<? extends PaymentMethod> getSupportedPaymentMethods(
        PaymentContext paymentContext,
        TransactionRequest transactionRequest
    ) {
        OptionalInt maybeOrgId = paymentContext.getConfigurationLevel().getOrganizationId();

        if (!maybeOrgId.isPresent()) {
            return Set.of();
        }

        var orgId = maybeOrgId.getAsInt();

        return customOfflineConfigurationManager
            .getOrganizationCustomOfflinePaymentMethods(orgId)
            .stream()
            .collect(Collectors.toSet());
    }

    @Override
    public PaymentProxy getPaymentProxy() {
        return PaymentProxy.CUSTOM_OFFLINE;
    }

    @Override
    public boolean accept(PaymentMethod paymentMethod, PaymentContext context, TransactionRequest transactionRequest) {
        try {
            customOfflineConfigurationManager.getOrganizationCustomOfflinePaymentMethodById(
                context.getPurchaseContext().getOrganizationId(),
                paymentMethod.getPaymentMethodId()
            );

            return isActive(context);
        } catch (CustomOfflinePaymentMethodDoesNotExistException e) {
            return false;
        }
    }

    @Override
    public boolean accept(Transaction transaction) {
        return transaction.getPaymentProxy() == PaymentProxy.CUSTOM_OFFLINE;
    }

    @Override
    public PaymentMethod getPaymentMethodForTransaction(Transaction transaction) {
        var transMetadata = transaction.getMetadata();

        if(!transMetadata.containsKey(Transaction.SELECTED_PAYMENT_METHOD_KEY)) {
            log.warn(
                "Transaction '{}' using 'CUSTOM_OFFLINE' has no {} metadata field. This should not happen.",
                transaction.getId(),
                Transaction.SELECTED_PAYMENT_METHOD_KEY
            );
            return null;
        }

        var paymentMethodId = transMetadata.get(Transaction.SELECTED_PAYMENT_METHOD_KEY);

        var reservationId = transaction.getReservationId();
        var event = eventRepository.findByReservationId(reservationId);
        if(event == null) {
            log.warn(
                "Transaction '{}' using 'CUSTOM_OFFLINE' is not associated with an event, so we cannot find the payment method.",
                transaction.getId()
            );
            return null;
        }

        UserDefinedOfflinePaymentMethod paymentMethod;
        try {
            paymentMethod = customOfflineConfigurationManager.getOrganizationCustomOfflinePaymentMethodById(
                event.getOrganizationId(),
                paymentMethodId
            );
        } catch (CustomOfflinePaymentMethodDoesNotExistException e) {
            log.warn(
                "Transaction '{}' using 'CUSTOM_OFFLINE' has a payment method id of '{}', which does not exist in the event organization.",
                transaction.getId(),
                paymentMethodId
            );
            return null;
        }

        return paymentMethod;
    }

    private void overrideExistingTransactions(PaymentSpecification spec) {
        PaymentManagerUtils.invalidateExistingTransactions(
            spec.getReservationId(),
            transactionRepository
        );

        transactionRepository.insert(
            UUID.randomUUID().toString(),
            null,
            spec.getReservationId(),
            ZonedDateTime.now(clockProvider.getClock()),
            spec.getPriceWithVAT(),
            spec.getCurrencyCode(),
            "",
            PaymentProxy.CUSTOM_OFFLINE.name(),
            0L,
            0L,
            Transaction.Status.PENDING,
            Map.of(
                Transaction.SELECTED_PAYMENT_METHOD_KEY,
                spec.getSelectedPaymentMethod().getPaymentMethodId()
            )
        );
    }


    @Override
    public boolean isActive(PaymentContext paymentContext) {
        return paymentContext.getPurchaseContext() != null
            && paymentContext.getPurchaseContext().event().isPresent();
    }

    private void transitionToOfflinePayment(PaymentSpecification spec, ZonedDateTime deadline) {
        int updatedReservation = ticketReservationRepository.postponePayment(
            spec.getReservationId(),
            CUSTOM_OFFLINE_PAYMENT,
            this.getPaymentProxy().name(),
            Date.from(deadline.toInstant()),
            spec.getEmail(),
            spec.getCustomerName().getFullName(),
            spec.getCustomerName().getFirstName(),
            spec.getCustomerName().getLastName(),
            spec.getBillingAddress(),
            spec.getCustomerReference()
        );

        Validate.isTrue(updatedReservation == 1, "expected exactly one updated reservation, got " + updatedReservation);
    }


    @Override
    public PaymentResult doPayment(PaymentSpecification spec) {
        var deadlineOpt = deadlineResolver.resolveDeadline(spec);

        if (deadlineOpt.isEmpty()) {
            log.warn("CUSTOM_OFFLINE payment is not supported without an event. reservationId={}", spec.getReservationId());
            return PaymentResult.failed("custom_offline_not_supported_without_event");
        }

        transitionToOfflinePayment(spec, deadlineOpt.get());
        overrideExistingTransactions(spec);
        return PaymentResult.successful(NOT_YET_PAID_TRANSACTION_ID);
    }
}
