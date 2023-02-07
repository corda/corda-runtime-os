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

@Entity
@Table(name = "rbac_group")
class Group(
    @Id
    @Column(name = "id", nullable = false)
    val id: String,

    @Column(name = "update_ts", nullable = false)
    var updateTimestamp: Instant,

    /**
     * Human readable name of the group.
     */
    @Column(name = "name", nullable = false)
    var name: String,

    /**
     * Group can have 1 parent group.
     * Parent group is null when the group is a "root" level group.
     * One group can be the parent for many groups.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_group")
    var parentGroup: Group?
) {
    /**
     * Version column for optimistic locking.
     */
    @Version
    var version: Int = 0

    /**
     * Groups can have multiple roles associated with it.
     */
    @OneToMany(mappedBy = "group", orphanRemoval = true, cascade = [CascadeType.ALL], fetch = FetchType.EAGER)
    var roleGroupAssociations: MutableSet<RoleGroupAssociation> = mutableSetOf()

    /**
     * Groups can have multiple properties associated with it.
     */
    @OneToMany(mappedBy = "groupRef", orphanRemoval = true, cascade = [CascadeType.ALL], fetch = FetchType.EAGER)
    var groupProperties: MutableSet<GroupProperty> = mutableSetOf()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Group) return false

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}