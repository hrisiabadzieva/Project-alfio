package alfio.manager.payment;

public interface StripeWebhookEvent {

    String getType();

    Boolean getLivemode();

    String getAccount();
}

