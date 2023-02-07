package net.corda.permissions.model

import net.corda.db.schema.DbSchema
import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.FetchType
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.Table
import javax.persistence.Version

@Entity
@Table(name = "rbac_group_props")
class GroupProperty(
    @Id
    @Column(name = "id", nullable = false)
    val id: String,

    @Column(name = "update_ts", nullable = false)
    var updateTimestamp: Instant,

    /**
     * A GroupProperty can be associated with one Group.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_ref", nullable = false)
    var groupRef: Group,

    @Column(name = "key", nullable = false)
    var key: String,

    @Column(name = "value", nullable = false)
    var value: String
) {
    /**
     * Version column for optimistic locking.
     */
    @Version
    var version: Int = 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GroupProperty) return false

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}