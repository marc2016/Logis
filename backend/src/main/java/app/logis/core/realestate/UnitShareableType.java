package app.logis.core.realestate;

import app.logis.core.sharing.ShareRole;
import app.logis.core.sharing.ShareableType;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/** Units are children of properties: only VIEWER/EDITOR, managed by the property's owners. */
@Component
class UnitShareableType implements ShareableType {

    private final UnitRepository units;

    UnitShareableType(UnitRepository units) {
        this.units = units;
    }

    @Override
    public String type() {
        return Unit.SHARE_TYPE;
    }

    @Override
    public String parentType() {
        return Property.SHARE_TYPE;
    }

    @Override
    public Optional<Long> parentOf(Long objectId) {
        return units.findById(objectId).map(Unit::getPropertyId);
    }

    @Override
    public Set<Long> childrenOf(Long parentId) {
        return new HashSet<>(units.findIdsByProperty(parentId));
    }

    @Override
    public Set<ShareRole> allowedRoles() {
        return EnumSet.of(ShareRole.VIEWER, ShareRole.EDITOR);
    }
}
