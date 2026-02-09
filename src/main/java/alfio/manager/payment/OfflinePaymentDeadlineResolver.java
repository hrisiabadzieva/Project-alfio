package alfio.manager.payment;

import java.time.ZonedDateTime;
import java.util.Optional;

public interface OfflinePaymentDeadlineResolver {
    Optional<ZonedDateTime> resolveDeadline(PaymentSpecification spec);
}

