package alfio.manager.notification.attachments;

import alfio.manager.system.Mailer;

import java.util.Map;
import java.util.Optional;

public interface AttachmentGenerator {
    Mailer.AttachmentIdentifier id();
    Optional<byte[]> generate(Map<String, String> model);
}

