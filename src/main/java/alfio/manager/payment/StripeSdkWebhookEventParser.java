package alfio.manager.payment;

import com.stripe.model.Event;
import com.stripe.net.Webhook;
import org.springframework.stereotype.Component;

@Component
public class StripeSdkWebhookEventParser implements StripeWebhookEventParser {

    @Override
    public StripeWebhookEvent parseAndVerify(String body, String signature, String secret) {
        try {
            Event event = Webhook.constructEvent(body, signature, secret);
            return new StripeSdkWebhookEvent(event);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid Stripe webhook signature/payload", e);
        }
    }
}



