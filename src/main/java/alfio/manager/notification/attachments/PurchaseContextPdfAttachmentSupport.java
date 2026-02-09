package alfio.manager.notification.attachments;

import alfio.manager.PurchaseContextManager;
import alfio.model.PurchaseContext;
import alfio.repository.EventRepository;
import alfio.util.Json;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.lang3.tuple.Triple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

@Component
public class PurchaseContextPdfAttachmentSupport {

    private static final Logger log = LoggerFactory.getLogger(PurchaseContextPdfAttachmentSupport.class);

    private static final String EVENT_ID = "eventId";

    private final PurchaseContextManager purchaseContextManager;
    private final EventRepository eventRepository;

    public PurchaseContextPdfAttachmentSupport(PurchaseContextManager purchaseContextManager, EventRepository eventRepository) {
        this.purchaseContextManager = purchaseContextManager;
        this.eventRepository = eventRepository;
    }

    public Optional<byte[]> generatePdf(Map<String, String> model,
                                        Function<Triple<PurchaseContext, Locale, Map<String, Object>>, Optional<byte[]>> pdfGenerator) {

        String reservationId = model.get("reservationId");

        Map<String, Object> reservationEmailModel = Json.fromJson(
            model.get("reservationEmailModel"),
            new TypeReference<>() {}
        );

        PurchaseContext purchaseContext = resolvePurchaseContext(model, reservationEmailModel);
        Locale language = Json.fromJson(model.get("language"), Locale.class);

        Optional<byte[]> pdf = pdfGenerator.apply(Triple.of(purchaseContext, language, reservationEmailModel));

        // FIXME legacy: reservationEmailModel should be typed
        reservationEmailModel.put("event", purchaseContext);

        if (pdf.isEmpty()) {
            log.warn("was not able to generate pdf for reservation id {} for locale {}", reservationId, language);
        }

        return pdf;
    }

    private PurchaseContext resolvePurchaseContext(Map<String, String> model, Map<String, Object> reservationEmailModel) {
        if (reservationEmailModel.get("purchaseContext") != null) {
            @SuppressWarnings("unchecked")
            var purchaseContextModel = (Map<String, String>) reservationEmailModel.get("purchaseContext");
            // legacy hack
            var purchaseContextType = model.get(EVENT_ID) != null ? PurchaseContext.PurchaseContextType.event : PurchaseContext.PurchaseContextType.subscription;
            return purchaseContextManager
                .findBy(purchaseContextType, purchaseContextModel.get("publicIdentifier"))
                .orElseThrow();
        }
        return eventRepository.findById(Integer.parseInt(model.get(EVENT_ID), 10));
    }
}

