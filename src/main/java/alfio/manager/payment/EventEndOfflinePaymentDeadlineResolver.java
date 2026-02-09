package alfio.manager.payment;

import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.Optional;

@Component
public class EventEndOfflinePaymentDeadlineResolver
    implements OfflinePaymentDeadlineResolver {

    @Override
    public Optional<ZonedDateTime> resolveDeadline(PaymentSpecification spec) {
        if (spec.getPurchaseContext() == null) {
            return Optional.empty();
        }
        return spec.getPurchaseContext().event().map(e -> e.getEnd());
    }
}

