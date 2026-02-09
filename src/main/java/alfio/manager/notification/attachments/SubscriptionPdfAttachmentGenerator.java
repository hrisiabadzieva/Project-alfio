package alfio.manager.notification.attachments;

import alfio.util.TemplateManager;
import alfio.manager.system.Mailer;
import alfio.controller.support.TemplateProcessor;
import alfio.model.user.Organization;
import alfio.model.subscription.Subscription;
import alfio.model.subscription.SubscriptionDescriptor;
import alfio.model.metadata.SubscriptionMetadata;
import alfio.repository.user.OrganizationRepository;
import alfio.repository.SubscriptionRepository;
import alfio.repository.TicketReservationRepository;
import alfio.util.LocaleUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component
public class SubscriptionPdfAttachmentGenerator implements AttachmentGenerator {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionPdfAttachmentGenerator.class);

    private final OrganizationRepository organizationRepository;
    private final alfio.manager.system.ConfigurationManager configurationManager;
    private final alfio.manager.FileUploadManager fileUploadManager;
    private final TemplateManager templateManager;
    private final TicketReservationRepository ticketReservationRepository;
    private final alfio.manager.ExtensionManager extensionManager;
    private final SubscriptionRepository subscriptionRepository;
    private final alfio.manager.PurchaseContextFieldManager purchaseContextFieldManager;

    public SubscriptionPdfAttachmentGenerator(OrganizationRepository organizationRepository,
                                              alfio.manager.system.ConfigurationManager configurationManager,
                                              alfio.manager.FileUploadManager fileUploadManager,
                                              TemplateManager templateManager,
                                              TicketReservationRepository ticketReservationRepository,
                                              alfio.manager.ExtensionManager extensionManager,
                                              SubscriptionRepository subscriptionRepository,
                                              alfio.manager.PurchaseContextFieldManager purchaseContextFieldManager) {
        this.organizationRepository = organizationRepository;
        this.configurationManager = configurationManager;
        this.fileUploadManager = fileUploadManager;
        this.templateManager = templateManager;
        this.ticketReservationRepository = ticketReservationRepository;
        this.extensionManager = extensionManager;
        this.subscriptionRepository = subscriptionRepository;
        this.purchaseContextFieldManager = purchaseContextFieldManager;
    }

    @Override
    public Mailer.AttachmentIdentifier id() {
        return Mailer.AttachmentIdentifier.SUBSCRIPTION_PDF;
    }

    @Override
    public Optional<byte[]> generate(Map<String, String> model) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try {
            UUID subscriptionId = UUID.fromString(model.get("subscriptionId"));
            Subscription subscription = subscriptionRepository.findSubscriptionById(subscriptionId);
            SubscriptionDescriptor subscriptionDescriptor = subscriptionRepository.findDescriptorBySubscriptionId(subscriptionId);

            var reservation = ticketReservationRepository.findReservationById(subscription.getReservationId());
            Organization organization = organizationRepository.getById(subscriptionDescriptor.getOrganizationId());
            var metadata = Objects.requireNonNullElseGet(
                subscriptionRepository.getSubscriptionMetadata(subscription.getId()),
                SubscriptionMetadata::empty
            );

            Locale locale = LocaleUtil.forLanguageTag(reservation.getUserLanguage());

            TemplateProcessor.renderSubscriptionPDF(
                subscription,
                locale,
                subscriptionDescriptor,
                reservation,
                metadata,
                organization,
                templateManager,
                fileUploadManager,
                configurationManager.getShortReservationID(subscriptionDescriptor, reservation),
                baos,
                extensionManager,
                purchaseContextFieldManager
            );

            return Optional.of(baos.toByteArray());
        } catch (IOException e) {
            log.warn("was not able to generate subscription pdf", e);
            return Optional.empty();
        }
    }
}

