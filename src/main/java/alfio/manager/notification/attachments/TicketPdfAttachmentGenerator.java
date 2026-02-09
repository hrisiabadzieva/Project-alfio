package alfio.manager.notification.attachments;

import alfio.util.EventUtil;
import alfio.util.TemplateManager;
import alfio.manager.support.AdditionalServiceHelper;
import alfio.manager.system.Mailer;
import alfio.controller.support.TemplateProcessor;
import alfio.model.FieldConfigurationDescriptionAndValue;
import alfio.model.Event;
import alfio.model.user.Organization;
import alfio.model.Ticket;
import alfio.model.TicketCategory;
import alfio.model.TicketReservation;
import alfio.model.TicketWithMetadataAttributes;
import alfio.repository.EventRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.repository.TicketRepository;
import alfio.repository.TicketReservationRepository;
import alfio.repository.SubscriptionRepository;
import alfio.util.Json;
import alfio.util.LocaleUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

@Component
public class TicketPdfAttachmentGenerator implements AttachmentGenerator {

    private static final Logger log = LoggerFactory.getLogger(TicketPdfAttachmentGenerator.class);

    private final EventRepository eventRepository;
    private final OrganizationRepository organizationRepository;
    private final alfio.manager.system.ConfigurationManager configurationManager;
    private final alfio.manager.FileUploadManager fileUploadManager;
    private final TemplateManager templateManager;
    private final TicketReservationRepository ticketReservationRepository;
    private final BiFunction<Ticket, Event, List<FieldConfigurationDescriptionAndValue>> retrieveFieldValues;
    private final alfio.manager.ExtensionManager extensionManager;
    private final TicketRepository ticketRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final AdditionalServiceHelper additionalServiceHelper;

    public TicketPdfAttachmentGenerator(EventRepository eventRepository,
                                        OrganizationRepository organizationRepository,
                                        alfio.manager.system.ConfigurationManager configurationManager,
                                        alfio.manager.FileUploadManager fileUploadManager,
                                        TemplateManager templateManager,
                                        TicketReservationRepository ticketReservationRepository,
                                        TicketRepository ticketRepository,
                                        alfio.manager.PurchaseContextFieldManager purchaseContextFieldManager,
                                        alfio.repository.AdditionalServiceItemRepository additionalServiceItemRepository,
                                        alfio.manager.ExtensionManager extensionManager,
                                        SubscriptionRepository subscriptionRepository,
                                        AdditionalServiceHelper additionalServiceHelper) {

        this.eventRepository = eventRepository;
        this.organizationRepository = organizationRepository;
        this.configurationManager = configurationManager;
        this.fileUploadManager = fileUploadManager;
        this.templateManager = templateManager;
        this.ticketReservationRepository = ticketReservationRepository;
        this.ticketRepository = ticketRepository;
        this.extensionManager = extensionManager;
        this.subscriptionRepository = subscriptionRepository;
        this.additionalServiceHelper = additionalServiceHelper;

        // същата логика като преди (от конструктора на NotificationManager)
        this.retrieveFieldValues = EventUtil.retrieveFieldValues(
            ticketRepository,
            purchaseContextFieldManager,
            additionalServiceItemRepository,
            true
        );
    }

    @Override
    public Mailer.AttachmentIdentifier id() {
        return Mailer.AttachmentIdentifier.TICKET_PDF;
    }

    @Override
    public Optional<byte[]> generate(Map<String, String> model) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Ticket ticket = Json.fromJson(model.get("ticket"), Ticket.class);

        try {
            TicketReservation reservation = ticketReservationRepository.findReservationById(ticket.getTicketsReservationId());
            TicketCategory ticketCategory = Json.fromJson(model.get("ticketCategory"), TicketCategory.class);
            Event event = eventRepository.findById(ticket.getEventId());
            Organization organization = organizationRepository.getById(Integer.parseInt(model.get("organizationId"), 10));

            var ticketWithMetadata = TicketWithMetadataAttributes.build(ticket, ticketRepository.getTicketMetadata(ticket.getId()));
            var locale = LocaleUtil.forLanguageTag(ticket.getUserLanguage());

            TemplateProcessor.renderPDFTicket(
                locale,
                event,
                reservation,
                ticketWithMetadata,
                ticketCategory,
                organization,
                templateManager,
                fileUploadManager,
                configurationManager.getShortReservationID(event, reservation),
                baos,
                retrieveFieldValues,
                extensionManager,
                TemplateProcessor.getSubscriptionDetailsModelForTicket(
                    ticket,
                    subscriptionRepository::findDescriptorBySubscriptionId,
                    locale
                ),
                additionalServiceHelper.findForTicket(ticket, event)
            );

            return Optional.of(baos.toByteArray());
        } catch (IOException e) {
            log.warn("was not able to generate ticket pdf for ticket with id {}", ticket.getId(), e);
            return Optional.empty();
        }
    }
}