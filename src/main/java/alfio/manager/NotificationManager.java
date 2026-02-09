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
package alfio.manager;

import alfio.manager.i18n.MessageSourceManager;
import alfio.manager.notification.attachments.AttachmentGenerator;
import alfio.manager.support.CustomMessageManager;
import alfio.manager.support.PartialTicketTextGenerator;
import alfio.manager.support.TemplateGenerator;
import alfio.manager.system.ConfigurationLevel;
import alfio.manager.system.ConfigurationManager;
import alfio.manager.system.Mailer;
import alfio.model.*;
import alfio.model.metadata.TicketMetadataContainer;
import alfio.model.subscription.SubscriptionDescriptor;
import alfio.model.system.ConfigurationKeys;
import alfio.model.user.Organization;
import alfio.repository.EmailMessageRepository;
import alfio.repository.TicketRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.util.ClockProvider;
import alfio.util.EventUtil;
import alfio.util.Json;
import alfio.util.RenderedTemplate;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.gson.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.codec.Hex;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static alfio.model.EmailMessage.Status.*;
import static alfio.util.MiscUtils.removeTabsAndNewlines;
import static java.util.Objects.requireNonNullElse;

@Component
public class NotificationManager {

    public static final String SEND_TICKET_CC = "sendTicketCc";
    private static final Logger log = LoggerFactory.getLogger(NotificationManager.class);
    private final Mailer mailer;
    private final MessageSourceManager messageSourceManager;
    private final EmailMessageRepository emailMessageRepository;
    private final TransactionTemplate tx;
    private final OrganizationRepository organizationRepository;
    private final ConfigurationManager configurationManager;
    private final Gson gson;
    private final ClockProvider clockProvider;
    private final PurchaseContextManager purchaseContextManager;
    private final TicketRepository ticketRepository;
    private final Map<Mailer.AttachmentIdentifier, AttachmentGenerator> attachmentGenerators;

    public NotificationManager(Mailer mailer,
                               MessageSourceManager messageSourceManager,
                               PlatformTransactionManager transactionManager,
                               EmailMessageRepository emailMessageRepository,
                               OrganizationRepository organizationRepository,
                               ConfigurationManager configurationManager,
                               TicketRepository ticketRepository,
                               ClockProvider clockProvider,
                               PurchaseContextManager purchaseContextManager,
                               List<AttachmentGenerator> attachmentGenerators) {

        this.messageSourceManager = messageSourceManager;
        this.mailer = mailer;
        this.emailMessageRepository = emailMessageRepository;
        this.organizationRepository = organizationRepository;
        this.ticketRepository = ticketRepository;

        DefaultTransactionDefinition definition =
            new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_NESTED);
        this.tx = new TransactionTemplate(transactionManager, definition);

        this.configurationManager = configurationManager;

        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(Mailer.Attachment.class, new AttachmentConverter());
        this.gson = builder.create();

        this.clockProvider = clockProvider;
        this.purchaseContextManager = purchaseContextManager;

        this.attachmentGenerators = attachmentGenerators.stream()
            .collect(Collectors.toMap(AttachmentGenerator::id, g -> g));
    }

    public void sendTicketByEmail(Ticket ticket,
                                  Event event,
                                  Locale locale,
                                  PartialTicketTextGenerator textBuilder,
                                  TicketReservation reservation,
                                  TicketCategory ticketCategory,
                                  Supplier<Map<String, List<String>>> ticketAdditionalInfoSupplier) {

        Organization organization = organizationRepository.getById(event.getOrganizationId());

        boolean htmlEmailEnabled = configurationManager.getFor(ConfigurationKeys.ENABLE_HTML_EMAILS, event.getConfigurationLevel())
            .getValueAsBooleanOrDefault();
        // pre-generate template in order to reuse model
        var renderedTemplate = textBuilder.generate(ticket);

        List<Mailer.Attachment> attachments = new ArrayList<>();
        if(EventUtil.isAccessOnline(ticketCategory, event)) { // generate only calendar invitation
            var attachmentModel = new HashMap<String, String>();
            // attachment model expects non-string properties to be JSON, so we convert them
            renderedTemplate.getSrcModel().forEach((k, v) -> {
                if(v instanceof String string) {
                    attachmentModel.put(k, string);
                } else {
                    attachmentModel.put(k, Json.toJson(v));
                }
            });
            attachments.add(CustomMessageManager.generateCalendarAttachmentForOnlineEvent(attachmentModel));
        } else {
            attachments.add(CustomMessageManager.generateTicketAttachment(ticket, reservation, ticketCategory, organization, htmlEmailEnabled));
        }

        String displayName = event.getDisplayName();
        String subject = messageSourceManager.getMessageSourceFor(event).getMessage("ticket-email-subject", new Object[]{displayName}, locale);
        String encodedAttachments = encodeAttachments(attachments.toArray(new Mailer.Attachment[0]));
        String checksum = calculateChecksum(ticket.getEmail(), encodedAttachments, subject, renderedTemplate);
        String recipient = ticket.getEmail();
        String cc = getCCForTicket(ticket);

        tx.execute(status -> {
            emailMessageRepository.findIdByEventIdAndChecksum(event.getId(), checksum).ifPresentOrElse(
                // see issue #967
                id -> emailMessageRepository.updateStatusToWaitingWithHtml(id, renderedTemplate.getHtmlPart()),
                () -> emailMessageRepository.insert(event.getId(), null, reservation.getId(), recipient, cc, subject, renderedTemplate.getTextPart(), renderedTemplate.getHtmlPart(), encodedAttachments, checksum, ZonedDateTime.now(clockProvider.getClock()), event.getOrganizationId())
            );
            return null;
        });
    }

    private String getCCForTicket(Ticket ticket) {
        var metadata = ticketRepository.getTicketMetadata(ticket.getId());
        if (metadata != null) {
            var key = metadata.getMetadataForKey(TicketMetadataContainer.GENERAL);
            if (key.isEmpty()) {
                return null;
            }
            return key.get().getAttributes().get(SEND_TICKET_CC);
        }
        return null;
    }

    public void sendSimpleEmail(PurchaseContext purchaseContext, String reservationId, String recipient, List<String> cc, String subject, TemplateGenerator textBuilder) {
        sendSimpleEmail(purchaseContext, reservationId, recipient, cc, subject, textBuilder, Collections.emptyList());
    }

    public List<String> getCCForEventOrganizer(PurchaseContext purchaseContext) {
        var systemNotificationCC = configurationManager.getFor(ConfigurationKeys.MAIL_SYSTEM_NOTIFICATION_CC, purchaseContext.getConfigurationLevel()).getValueOrDefault("");
        return Stream.of(StringUtils.split(systemNotificationCC, ','))
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(StringUtils::isNotBlank)
            .collect(Collectors.toList());
    }

    public void sendSimpleEmail(PurchaseContext event, String reservationId, String recipient, String subject, TemplateGenerator textBuilder) {
        sendSimpleEmail(event, reservationId, recipient, Collections.emptyList(), subject, textBuilder);
    }

    public void sendSimpleEmail(PurchaseContext purchaseContext, String reservationId, String recipient, String subject, TemplateGenerator textBuilder, List<Mailer.Attachment> attachments) {
        sendSimpleEmail(purchaseContext, reservationId, recipient, Collections.emptyList(), subject, textBuilder, attachments);
    }

    public void sendSimpleEmail(PurchaseContext purchaseContext, String reservationId, String recipient, List<String> cc, String subject, TemplateGenerator textBuilder, List<Mailer.Attachment> attachments) {

        if (recipient == null) {
            log.warn("No recipient provided, mail for reservation id {} with subject {} will be ignored", removeTabsAndNewlines(reservationId), removeTabsAndNewlines(subject));
            return;
        }

        String encodedAttachments = attachments.isEmpty() ? null : encodeAttachments(attachments.toArray(new Mailer.Attachment[0]));
        String encodedCC = Json.toJson(cc);

        var renderedTemplate = textBuilder.generate();
        String checksum = calculateChecksum(recipient, encodedAttachments, subject, renderedTemplate);
        //in order to minimize the database size, it is worth checking if there is already another message in the table
        Optional<Integer> existing = emailMessageRepository.findIdByPurchaseContextAndChecksum(purchaseContext, checksum);

        existing.ifPresentOrElse(id ->
            //see issue #967
            emailMessageRepository.updateStatusToWaitingWithHtml(id, renderedTemplate.getHtmlPart())
            ,
            () -> {
                var pair = getEventIdSubscriptionId(purchaseContext);
                emailMessageRepository.insert(pair.getLeft(), pair.getRight(), reservationId, recipient, encodedCC, subject, renderedTemplate.getTextPart(), renderedTemplate.getHtmlPart(), encodedAttachments, checksum, ZonedDateTime.now(clockProvider.getClock()), purchaseContext.getOrganizationId());
            });
    }

    private static Pair<Integer, UUID> getEventIdSubscriptionId(PurchaseContext purchaseContext) {
        if(purchaseContext.ofType(PurchaseContext.PurchaseContextType.event)) {
            return Pair.of(((Event)purchaseContext).getId(), null);
        } else {
            return Pair.of(null, ((SubscriptionDescriptor) purchaseContext).getId());
        }
    }

    public Pair<Integer, List<LightweightMailMessage>> loadAllMessagesForPurchaseContext(PurchaseContext purchaseContext, Integer page, String search) {
        final int pageSize = 50;
        int offset = page == null ? 0 : page * pageSize;
        String toSearch = StringUtils.trimToNull(search);
        toSearch = toSearch == null ? null : ("%" + toSearch + "%");
        if(purchaseContext.ofType(PurchaseContext.PurchaseContextType.event)) {
            int eventId = ((Event) purchaseContext).getId();
            return Pair.of(emailMessageRepository.countFindByEventId(eventId, toSearch), emailMessageRepository.findByEventId(eventId, offset, pageSize, toSearch));
        } else {
            var subscriptionDescriptorId = ((SubscriptionDescriptor)purchaseContext).getId();
            return Pair.of(emailMessageRepository.countFindBySubscriptionDescriptorId(subscriptionDescriptorId, toSearch), emailMessageRepository.findBySubscriptionDescriptorId(subscriptionDescriptorId, offset, pageSize, toSearch));
        }
    }

    public List<LightweightMailMessage> loadAllMessagesForReservationId(PurchaseContext purchaseContext, String reservationId) {
        return emailMessageRepository.findByPurchaseContextAndReservationId(purchaseContext, reservationId);
    }

    public Optional<LightweightMailMessage> loadSingleMessageForPurchaseContext(PurchaseContext purchaseContext, int messageId) {
        if(purchaseContext.ofType(PurchaseContext.PurchaseContextType.event)) {
            return emailMessageRepository.findByEventIdAndMessageId(((Event)purchaseContext).getId(), messageId);
        } else {
            return emailMessageRepository.findBySubscriptionDescriptorIdAndMessageId(((SubscriptionDescriptor)purchaseContext).getId(), messageId);
        }
    }

    @Transactional
    public int sendWaitingMessages() {
        emailMessageRepository.setToRetryOldInProcess(ZonedDateTime.now(clockProvider.getClock()).minusHours(1));
        return emailMessageRepository.loadAllWaitingForProcessing().stream()
            .collect(Collectors.groupingBy(NotificationManager::purchaseContextCacheKey))
            .entrySet().stream()
            .flatMapToInt(entry -> {
                var splitKey = entry.getKey().split("//");
                PurchaseContext purchaseContext = purchaseContextManager.findById(PurchaseContext.PurchaseContextType.from(splitKey[0]), splitKey[1]).orElseThrow();
                // TODO we can try to send emails in batches, if the provider supports it.
                return entry.getValue().stream().mapToInt(message -> processMessage(message, purchaseContext));
            }).sum();
    }

    private int processMessage(EmailMessage message, PurchaseContext purchaseContext) {
        int messageId = message.getId();
        ConfigurationLevel configurationLevel = ConfigurationLevel.purchaseContext(purchaseContext);
        if(message.getAttempts() >= configurationManager.getFor(ConfigurationKeys.MAIL_ATTEMPTS_COUNT, configurationLevel).getValueAsIntOrDefault(10)) {
            tx.execute(status -> emailMessageRepository.updateStatusAndAttempts(messageId, ERROR.name(), message.getAttempts(), Arrays.asList(IN_PROCESS.name(), WAITING.name(), RETRY.name())));
            log.warn("Message with id {} will be discarded", messageId);
            return 0;
        }

        try {
            int result = Optional.ofNullable(tx.execute(status -> emailMessageRepository.updateStatus(messageId, message.getChecksum(), IN_PROCESS.name(), Arrays.asList(WAITING.name(), RETRY.name())))).orElse(0);
            if(result > 0) {
                return Optional.ofNullable(tx.execute(status -> {
                    sendMessage(purchaseContext, message);
                    return 1;
                })).orElse(0);
            } else {
                log.debug("no messages have been updated on DB for the following criteria: id: {}, checksum: {}", messageId, message.getChecksum());
            }
        } catch(Exception e) {
            tx.execute(status -> emailMessageRepository.updateStatusAndAttempts(message.getId(), RETRY.name(), ZonedDateTime.now(clockProvider.getClock()).plusMinutes(message.getAttempts() + 1L), message.getAttempts() + 1, Arrays.asList(IN_PROCESS.name(), WAITING.name(), RETRY.name())));
            log.warn("could not send message: ",e);
        }
        return 0;
    }

    private void sendMessage(PurchaseContext purchaseContext, EmailMessage message) {
        // FIXME save the locale of the message, so that we can retrieve its title
        mailer.send(purchaseContext, purchaseContext.getDisplayName(), message.getRecipient(), message.getCc(), message.getSubject(), message.getMessage(), Optional.ofNullable(message.getHtmlMessage()), decodeAttachments(message.getAttachments()));
        emailMessageRepository.updateStatusToSent(message.getId(), message.getChecksum(), ZonedDateTime.now(clockProvider.getClock()), Collections.singletonList(IN_PROCESS.name()));
    }

    private String encodeAttachments(Mailer.Attachment... files) {
        return gson.toJson(files);
    }

    private Mailer.Attachment[] decodeAttachments(String input) {
        if(StringUtils.isBlank(input)) {
            return new Mailer.Attachment[0];
        }
        Mailer.Attachment[] attachments = gson.fromJson(input, Mailer.Attachment[].class);

        Set<Mailer.AttachmentIdentifier> alreadyPresents = Arrays.stream(attachments).map(Mailer.Attachment::getIdentifier).filter(Objects::nonNull).collect(Collectors.toSet());
        //
        List<Mailer.Attachment> toReinterpret = Arrays.stream(attachments)
            .filter(attachment -> attachment.getIdentifier() != null && !attachment.getIdentifier().reinterpretAs().isEmpty())
            .collect(Collectors.toList());

        List<Mailer.Attachment> generated = Arrays.stream(attachments)
            .map(attachment -> this.transformAttachment(attachment, attachment.getIdentifier()))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        List<Mailer.Attachment> reinterpreted = new ArrayList<>();
        toReinterpret.forEach(attachment ->
            attachment.getIdentifier().reinterpretAs().stream()
                .filter(identifier -> !alreadyPresents.contains(identifier))
                .forEach(identifier -> reinterpreted.add(this.transformAttachment(attachment, identifier))
            )
        );

        generated.addAll(reinterpreted.stream().filter(Objects::nonNull).collect(Collectors.toList()));
        return generated.toArray(new Mailer.Attachment[0]);
    }

    private Mailer.Attachment transformAttachment(Mailer.Attachment attachment, Mailer.AttachmentIdentifier identifier) {
        if (identifier == null) {
            return attachment;
        }

        AttachmentGenerator generator = attachmentGenerators.get(identifier);
        if (generator == null) {
            log.warn("No AttachmentGenerator registered for {}", identifier);
            return null;
        }

        return generator.generate(attachment.getModel())
            .map(bytes ->
                new Mailer.Attachment(
                    identifier.fileName(attachment.getFilename()),
                    bytes,
                    identifier.contentType(attachment.getContentType()),
                    null,
                    null
                )
            )
            .orElse(null);
    }

    private static String calculateChecksum(String recipient, String attachments, String subject, RenderedTemplate renderedTemplate)  {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(recipient.getBytes(StandardCharsets.UTF_8));
            digest.update(subject.getBytes(StandardCharsets.UTF_8));
            Optional.ofNullable(attachments).ifPresent(v -> digest.update(v.getBytes(StandardCharsets.UTF_8)));
            digest.update(renderedTemplate.getTextPart().getBytes(StandardCharsets.UTF_8));
            if(renderedTemplate.isMultipart()) {
                digest.update(renderedTemplate.getHtmlPart().getBytes(StandardCharsets.UTF_8));
            }
            return new String(Hex.encode(digest.digest()));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final class AttachmentConverter implements JsonSerializer<Mailer.Attachment>, JsonDeserializer<Mailer.Attachment> {

        private static final String SOURCE = "source";
        private static final String IDENTIFIER = "identifier";
        private static final String MODEL = "model";

        @Override
        public JsonElement serialize(Mailer.Attachment src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            obj.addProperty("filename", src.getFilename());
            obj.addProperty(SOURCE, src.getSource() != null ? Base64.getEncoder().encodeToString(src.getSource()) : null);
            obj.addProperty("contentType", src.getContentType());
            obj.addProperty(IDENTIFIER, src.getIdentifier() != null ? src.getIdentifier().name() : null);
            obj.addProperty(MODEL, src.getModel() != null ? Json.toJson(src.getModel()) : null);
            return obj;
        }

        @Override
        public Mailer.Attachment deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) {
            JsonObject jsonObject = json.getAsJsonObject();
            String filename = jsonObject.getAsJsonPrimitive("filename").getAsString();
            byte[] source =  jsonObject.has(SOURCE) ? Base64.getDecoder().decode(jsonObject.getAsJsonPrimitive(SOURCE).getAsString()) : null;
            String contentType = jsonObject.getAsJsonPrimitive("contentType").getAsString();
            Mailer.AttachmentIdentifier identifier =  jsonObject.has(IDENTIFIER) ? Mailer.AttachmentIdentifier.valueOf(jsonObject.getAsJsonPrimitive(IDENTIFIER).getAsString()) : null;
            Map<String, String> model = jsonObject.has(MODEL)  ? Json.fromJson(jsonObject.getAsJsonPrimitive(MODEL).getAsString(), new TypeReference<>() {}) : null;
            return new Mailer.Attachment(filename, source, contentType, model, identifier);
        }
    }

    private static String purchaseContextCacheKey(EmailMessage message) {
        return message.getPurchaseContextType() + "//"
            + requireNonNullElse(message.getEventId(), message.getSubscriptionDescriptorId());
    }
}
