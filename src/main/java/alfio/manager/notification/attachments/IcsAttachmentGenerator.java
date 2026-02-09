package alfio.manager.notification.attachments;

import alfio.manager.notification.OnlineCheckInTextBuilder;
import alfio.util.EventUtil;
import alfio.manager.i18n.MessageSourceManager;
import alfio.util.LocaleUtil;
import alfio.model.Event;
import alfio.model.EventDescription;
import alfio.model.user.Organization;
import alfio.model.Ticket;
import alfio.model.TicketCategory;
import alfio.manager.system.Mailer;
import alfio.repository.EventDescriptionRepository;
import alfio.repository.EventRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.repository.TicketCategoryRepository;
import alfio.util.Json;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static alfio.model.system.ConfigurationKeys.INCLUDE_CHECK_IN_URL_ICAL;

import static java.util.Objects.requireNonNullElseGet;

@Component
public class IcsAttachmentGenerator implements AttachmentGenerator {

    private static final String EVENT_ID = "eventId";
    private static final String ONLINE_CHECK_IN_URL = "onlineCheckInUrl";
    private static final String CUSTOM_CHECK_IN_URL = "customCheckInUrl";
    private static final String CUSTOM_CHECK_IN_URL_TEXT = "customCheckInUrlText";
    private static final String CUSTOM_CHECK_IN_URL_DESCRIPTION = "customCheckInUrlDescription";

    private final EventRepository eventRepository;
    private final EventDescriptionRepository eventDescriptionRepository;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final OrganizationRepository organizationRepository;
    private final MessageSourceManager messageSourceManager;
    private final alfio.manager.system.ConfigurationManager configurationManager;

    public IcsAttachmentGenerator(EventRepository eventRepository,
                                  EventDescriptionRepository eventDescriptionRepository,
                                  TicketCategoryRepository ticketCategoryRepository,
                                  OrganizationRepository organizationRepository,
                                  MessageSourceManager messageSourceManager,
                                  alfio.manager.system.ConfigurationManager configurationManager) {
        this.eventRepository = eventRepository;
        this.eventDescriptionRepository = eventDescriptionRepository;
        this.ticketCategoryRepository = ticketCategoryRepository;
        this.organizationRepository = organizationRepository;
        this.messageSourceManager = messageSourceManager;
        this.configurationManager = configurationManager;
    }

    @Override
    public Mailer.AttachmentIdentifier id() {
        return Mailer.AttachmentIdentifier.CALENDAR_ICS;
    }

    @Override
    public Optional<byte[]> generate(Map<String, String> model) {
        Event event;
        Locale locale;
        Integer categoryId;

        if (model.containsKey(EVENT_ID)) {
            // legacy branch
            event = eventRepository.findById(Integer.parseInt(model.get(EVENT_ID), 10));
            locale = Json.fromJson(model.get("locale"), Locale.class);
            categoryId = null;
        } else {
            Ticket ticket = Json.fromJson(model.get("ticket"), Ticket.class);
            event = eventRepository.findById(ticket.getEventId());
            locale = LocaleUtil.forLanguageTag(ticket.getUserLanguage());
            categoryId = ticket.getCategoryId();
        }

        Organization organization = organizationRepository.getById(event.getOrganizationId());
        TicketCategory category = Optional.ofNullable(categoryId)
            .map(ticketCategoryRepository::getById)
            .orElse(null);

        String description = eventDescriptionRepository
            .findDescriptionByEventIdTypeAndLocale(
                event.getId(),
                EventDescription.EventDescriptionType.DESCRIPTION,
                locale.getLanguage()
            )
            .orElse("");

        if (model.containsKey(ONLINE_CHECK_IN_URL)
            && configurationManager
            .getFor(INCLUDE_CHECK_IN_URL_ICAL, event.getConfigurationLevel())
            .getValueAsBooleanOrDefault()) {

            MessageSource messageSource = messageSourceManager.getMessageSourceFor(event);

            description = description
                + "\n```\n"
                + OnlineCheckInTextBuilder.build(messageSource, model, locale)
                + "\n```\n";
        }

        return EventUtil.getIcalForEvent(event, category, description, organization);
    }

    private static String buildOnlineCheckInInformation(
        MessageSource messageSource,
        Map<String, String> model,
        Locale locale
    ) {
        String body;

        if (model.containsKey(CUSTOM_CHECK_IN_URL)) {
            body =
                requireNonNullElseGet(
                    model.get(CUSTOM_CHECK_IN_URL_TEXT),
                    () -> messageSource.getMessage("email.event.online.check-in", null, locale)
                )
                    + "\n"
                    + model.get(ONLINE_CHECK_IN_URL)
                    + "\n"
                    + model.getOrDefault(CUSTOM_CHECK_IN_URL_DESCRIPTION, "")
                    + "\n";
        } else {
            body =
                messageSource.getMessage("email.event.online.check-in", null, locale)
                    + "\n"
                    + model.get(ONLINE_CHECK_IN_URL)
                    + "\n\n";
        }

        return "\n******************************************\n"
            + messageSource.getMessage("event.location.online", null, locale)
            + "\n\n"
            + messageSource.getMessage("email.event.online.important-information", null, locale)
            + "\n\n"
            + body;
    }
}

