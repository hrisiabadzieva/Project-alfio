package alfio.manager.notification.attachments;

import alfio.util.TemplateManager;
import alfio.manager.system.Mailer;
import alfio.controller.support.TemplateProcessor;
import alfio.repository.EventRepository;
import alfio.manager.FileUploadManager;
import alfio.manager.ExtensionManager;
import alfio.manager.PurchaseContextManager;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Component
public class ReceiptPdfAttachmentGenerator implements AttachmentGenerator {

    private final PurchaseContextPdfAttachmentSupport support;
    private final FileUploadManager fileUploadManager;
    private final TemplateManager templateManager;
    private final ExtensionManager extensionManager;

    public ReceiptPdfAttachmentGenerator(PurchaseContextPdfAttachmentSupport support,
                                         FileUploadManager fileUploadManager,
                                         TemplateManager templateManager,
                                         ExtensionManager extensionManager) {
        this.support = support;
        this.fileUploadManager = fileUploadManager;
        this.templateManager = templateManager;
        this.extensionManager = extensionManager;
    }

    @Override
    public Mailer.AttachmentIdentifier id() {
        return Mailer.AttachmentIdentifier.RECEIPT_PDF;
    }

    @Override
    public Optional<byte[]> generate(Map<String, String> model) {
        return support.generatePdf(model, payload ->
            TemplateProcessor.buildReceiptPdf(
                payload.getLeft(),
                fileUploadManager,
                payload.getMiddle(),
                templateManager,
                payload.getRight(),
                extensionManager
            )
        );
    }
}

