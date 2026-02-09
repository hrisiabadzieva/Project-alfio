package alfio.manager;
import alfio.model.modification.EventModification;

import java.time.ZoneId;

public interface EventTimeZoneResolver {
    ZoneId resolveZoneId(EventModification em);
    String resolveTimeZoneIdString(EventModification em);
}
