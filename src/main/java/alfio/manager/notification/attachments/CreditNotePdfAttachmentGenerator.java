package alfio.manager.notification.attachments;

import alfio.util.TemplateManager;
import alfio.manager.system.Mailer;
import alfio.controller.support.TemplateProcessor;
import alfio.manager.ExtensionManager;
import alfio.manager.FileUploadManager;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Component
public class CreditNotePdfAttachmentGenerator implements AttachmentGenerator {

    private final PurchaseContextPdfAttachmentSupport support;
    private final FileUploadManager fileUploadManager;
    private final TemplateManager templateManager;
    private final ExtensionManager extensionManager;

    public CreditNotePdfAttachmentGenerator(PurchaseContextPdfAttachmentSupport support,
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
        return Mailer.AttachmentIdentifier.CREDIT_NOTE_PDF;
    }

    @Override
    public Optional<byte[]> generate(Map<String, String> model) {
        return support.generatePdf(model, payload ->
            TemplateProcessor.buildCreditNotePdf(
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

