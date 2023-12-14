package net.corda.permissions.model

import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "rbac_change_audit")
class ChangeAudit(
    @Id
    @Column(name = "id", nullable = false)
    var id: String,

    @Column(name = "update_ts", nullable = false)
    var updateTimestamp: Instant,

    @Column(name = "actor_user", nullable = false)
    var actorUser: String,

    @Enumerated(value = EnumType.STRING)
    @Column(name = "change_type", nullable = false)
    var changeType: RestPermissionOperation,

    @Column(name = "details", nullable = false)
    var details: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ChangeAudit) return false

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}
