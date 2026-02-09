package alfio.manager.payment;

public interface StripeWebhookEventParser {
    StripeWebhookEvent parseAndVerify(String body, String signature, String secret);
}

