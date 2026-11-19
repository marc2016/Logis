package app.logis.core.realestate;

import app.logis.core.sharing.ShareRole;
import app.logis.core.sharing.SharingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;

/**
 * Addresses and legal entities of the properties a user can see, archived ones included: through a share on the
 * property or on one of its units. For the admin mail export (design D1 of mail-export-pseudonymization).
 */
@Service
@Transactional(readOnly = true)
public class PropertyAddresses {

    public record Entry(Long legalEntityId, String street, String postalCode, String city) {
    }

    private final SharingService sharing;
    private final PropertyRepository properties;
    private final UnitRepository units;

    PropertyAddresses(SharingService sharing, PropertyRepository properties, UnitRepository units) {
        this.sharing = sharing;
        this.properties = properties;
        this.units = units;
    }

    public List<Entry> visibleTo(Long userId) {
        var ids = new HashSet<>(sharing.rolesFor(Property.SHARE_TYPE, userId, ShareRole.VIEWER).keySet());
        units.findAllById(sharing.rolesFor(Unit.SHARE_TYPE, userId, ShareRole.VIEWER).keySet())
                .forEach(u -> ids.add(u.getPropertyId()));
        return properties.findByIdIn(ids).stream().map(p -> {
            var a = p.getAddress();
            return new Entry(p.getLegalEntityId(), a.street(), a.postalCode(), a.city());
        }).toList();
    }
}
