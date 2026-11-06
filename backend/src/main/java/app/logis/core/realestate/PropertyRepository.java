package app.logis.core.realestate;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

interface PropertyRepository extends JpaRepository<Property, Long> {

    List<Property> findByIdIn(Collection<Long> ids);

    boolean existsByLegalEntityId(Long legalEntityId);
}
