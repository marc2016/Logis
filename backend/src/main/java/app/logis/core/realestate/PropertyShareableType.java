package app.logis.core.realestate;

import app.logis.core.sharing.ShareableType;
import org.springframework.stereotype.Component;

@Component
class PropertyShareableType implements ShareableType {

    @Override
    public String type() {
        return Property.SHARE_TYPE;
    }
}
