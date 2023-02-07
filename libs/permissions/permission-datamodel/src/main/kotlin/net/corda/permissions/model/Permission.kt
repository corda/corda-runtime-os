package net.corda.permissions.model

import net.corda.db.schema.DbSchema
import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.FetchType
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.Table
import javax.persistence.Version

/**
 * A given permission can belong to multiple roles.
 */
@Suppress("LongParameterList")
@Entity
@Table(name = "rbac_perm")
class Permission(
    @Id
    @Column(name = "id", nullable = false)
    val id: String,

    @Column(name = "update_ts", nullable = false)
    var updateTimestamp: Instant,

    /**
     * Group this permission is visible to.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "group_vis")
    var groupVisibility: Group?,

    /**
     * Optional identifier of the virtual node within which the physical node permission applies to.
     */
    @Column(name = "virtual_node", nullable = true)
    var virtualNode: String?,

    /**
     * Defines whether this is an ALLOW or DENY type of permission. DENY always have advantage over ALLOW no matter at which level they are
     * granted.
     */
    @Column(name = "perm_type", nullable = false)
    @Enumerated(value = EnumType.STRING)
    var permissionType: PermissionType,

    /**
     * Machine-parseable string representing an individual permission. It can be any arbitrary string as long as the authorization code can
     * make use of it in the context of user permission matching.
     */
    @Column(name = "perm_string", nullable = false)
    var permissionString: String
) {
    /**
     * Version column for optimistic locking.
     */
    @Version
    var version: Int = 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Permission) return false

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}