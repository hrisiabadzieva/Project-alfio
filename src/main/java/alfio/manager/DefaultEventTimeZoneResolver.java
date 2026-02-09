package alfio.manager;

import alfio.model.modification.EventModification;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.ZoneId;

@Component
public class DefaultEventTimeZoneResolver implements EventTimeZoneResolver {

    private static final String FALLBACK_TZ = "UTC";

    @Override
    public ZoneId resolveZoneId(EventModification em) {
        return ZoneId.of(resolveTimeZoneIdString(em));
    }

    @Override
    public String resolveTimeZoneIdString(EventModification em) {
        String timeZone = ObjectUtils.firstNonNull(
            em.getZoneId(),
            em.getGeolocation() != null ? em.getGeolocation().timeZone() : null
        );

        if (timeZone == null || timeZone.isBlank()) {
            return FALLBACK_TZ;
        }

        try {
            ZoneId.of(timeZone); // валидация
            return timeZone;
        } catch (DateTimeException e) {
            return FALLBACK_TZ;
        }
    }
}

