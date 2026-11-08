package app.logis.modules.vacationrental;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A name under which mails refer to an agency; the agency's own name is always one of them. */
@Entity
@Table(name = "agency_aliases", schema = "vacationrental")
class AgencyAlias {

    static final int MAX_LENGTH = 255;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agency_id", nullable = false)
    private Long agencyId;

    @Column(nullable = false, length = MAX_LENGTH)
    private String alias;

    protected AgencyAlias() {
    }

    AgencyAlias(Long agencyId, String alias) {
        this.agencyId = agencyId;
        this.alias = alias;
    }

    Long getId() {
        return id;
    }

    Long getAgencyId() {
        return agencyId;
    }

    void setAgencyId(Long agencyId) {
        this.agencyId = agencyId;
    }

    String getAlias() {
        return alias;
    }
}
