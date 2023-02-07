package net.corda.permissions.model

import net.corda.db.schema.DbSchema
import java.time.Instant
import javax.persistence.CascadeType
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.FetchType
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.OneToMany
import javax.persistence.Table
import javax.persistence.Version

/**
 * A named collection of permissions.
 */
@Entity
@Table(name = "rbac_role")
class Role(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: String,

    @Column(name = "update_ts", nullable = false)
    var updateTimestamp: Instant,

    /**
     * Human readable name of a role.
     */
    @Column(name = "name", nullable = false)
    var name: String,

    /**
     * Group this role is visible to.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_vis")
    var groupVisibility: Group?,
) {
    /**
     * Version column for optimistic locking.
     */
    @Version
    var version: Int = 0

    /**
     * Each role can be associated with multiple permissions. A given permission can belong to multiple roles.
     */
    @OneToMany(mappedBy = "role", orphanRemoval = true, cascade = [CascadeType.ALL], fetch = FetchType.EAGER)
    var rolePermAssociations: MutableSet<RolePermissionAssociation> = mutableSetOf()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Role) return false

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}