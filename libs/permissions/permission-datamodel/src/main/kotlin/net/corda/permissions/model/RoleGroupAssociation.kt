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

/**
 * Associates Groups and Roles.
 * A role can be associated with many groups and many roles can be associated with a group.
 */
@Entity
@Table(name = "rbac_role_group_rel", schema = DbSchema.RBAC)
class RoleGroupAssociation(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id")
    val role: Role,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    val group: Group,

    @Column(name = "update_ts", nullable = false)
    var updateTimestamp: Instant,
)