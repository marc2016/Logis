package app.logis.core.ledger;

import app.logis.core.sharing.ShareableType;
import org.springframework.stereotype.Component;

@Component
class AccountShareableType implements ShareableType {

    @Override
    public String type() {
        return Account.SHARE_TYPE;
    }
}
