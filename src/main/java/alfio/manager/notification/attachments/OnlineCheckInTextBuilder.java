package alfio.manager.notification;

import org.springframework.context.MessageSource;

import java.util.Locale;
import java.util.Map;

import static java.util.Objects.requireNonNullElseGet;

public final class OnlineCheckInTextBuilder {

    private static final String ONLINE_CHECK_IN_URL = "onlineCheckInUrl";
    private static final String CUSTOM_CHECK_IN_URL = "customCheckInUrl";
    private static final String CUSTOM_CHECK_IN_URL_TEXT = "customCheckInUrlText";
    private static final String CUSTOM_CHECK_IN_URL_DESCRIPTION = "customCheckInUrlDescription";

    private OnlineCheckInTextBuilder() {
    }

    public static String build(MessageSource messageSource, Map<String, String> model, Locale locale) {
        String body;

        if (model.containsKey(CUSTOM_CHECK_IN_URL)) {
            body = requireNonNullElseGet(
                model.get(CUSTOM_CHECK_IN_URL_TEXT),
                () -> messageSource.getMessage("email.event.online.check-in", null, locale)
            ) + "\n"
                + model.get(ONLINE_CHECK_IN_URL) + "\n"
                + model.getOrDefault(CUSTOM_CHECK_IN_URL_DESCRIPTION, "") + "\n";
        } else {
            body = messageSource.getMessage("email.event.online.check-in", null, locale) + "\n"
                + model.get(ONLINE_CHECK_IN_URL) + "\n\n";
        }

        return "\n******************************************\n"
            + messageSource.getMessage("event.location.online", null, locale) + "\n\n"
            + messageSource.getMessage("email.event.online.important-information", null, locale) + "\n\n"
            + body;
    }
}

