package alfio.manager.payment;

import com.stripe.model.Event;

final class StripeSdkWebhookEvent implements StripeWebhookEvent {

    private final Event delegate;

    StripeSdkWebhookEvent(Event delegate) {
        this.delegate = delegate;
    }

    @Override public String getType() { return delegate.getType(); }
    @Override public Boolean getLivemode() { return delegate.getLivemode(); }
    @Override public String getAccount() { return delegate.getAccount(); }
}

