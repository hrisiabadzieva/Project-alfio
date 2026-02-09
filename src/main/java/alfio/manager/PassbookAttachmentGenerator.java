package alfio.manager;

import alfio.manager.notification.attachments.AttachmentGenerator;
import alfio.manager.system.Mailer;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Component
public class PassbookAttachmentGenerator implements AttachmentGenerator {

    private final PassKitManager passKitManager;

    public PassbookAttachmentGenerator(PassKitManager passKitManager) {
        this.passKitManager = passKitManager;
    }

    @Override
    public Mailer.AttachmentIdentifier id() {
        return Mailer.AttachmentIdentifier.PASSBOOK;
    }

    @Override
    public Optional<byte[]> generate(Map<String, String> model) {
        return Optional.ofNullable(passKitManager.getPass(model));
    }
}


