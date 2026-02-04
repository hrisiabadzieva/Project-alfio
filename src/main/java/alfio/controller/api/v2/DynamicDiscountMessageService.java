package alfio.controller.api.v2;

import alfio.manager.i18n.MessageSourceManager;
import alfio.model.ContentLanguage;
import alfio.model.Event;
import alfio.model.PromoCodeDiscount;
import alfio.util.MonetaryUtil;
import org.apache.commons.lang3.Validate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class DynamicDiscountMessageService {

    private final MessageSourceManager messageSourceManager;

    public DynamicDiscountMessageService(MessageSourceManager messageSourceManager) {
        this.messageSourceManager = messageSourceManager;
    }

    public Map<String, String> formatDynamicCodeMessage(Event event, PromoCodeDiscount promoCodeDiscount) {
        Validate.isTrue(promoCodeDiscount != null && promoCodeDiscount.getDiscountType() != PromoCodeDiscount.DiscountType.NONE);

        var messageSource = messageSourceManager.getMessageSourceFor(event);
        Map<String, String> res = new HashMap<>();
        String code;
        String amount;

        switch (promoCodeDiscount.getDiscountType()) {
            case PERCENTAGE:
                code = "reservation.dynamic.discount.confirmation.percentage.message";
                amount = String.valueOf(promoCodeDiscount.getDiscountAmount());
                break;
            case FIXED_AMOUNT:
                amount = event.getCurrency() + " " + MonetaryUtil.formatCents(promoCodeDiscount.getDiscountAmount(), event.getCurrency());
                code = "reservation.dynamic.discount.confirmation.fix-per-ticket.message";
                break;
            case FIXED_AMOUNT_RESERVATION:
                amount = event.getCurrency() + " " + MonetaryUtil.formatCents(promoCodeDiscount.getDiscountAmount(), event.getCurrency());
                code = "reservation.dynamic.discount.confirmation.fix-per-reservation.message";
                break;
            default:
                throw new IllegalStateException("Unexpected discount code type");
        }

        for (ContentLanguage cl : event.getContentLanguages()) {
            res.put(cl.locale().getLanguage(), messageSource.getMessage(code, new Object[]{amount}, cl.locale()));
        }
        return res;
    }
}
