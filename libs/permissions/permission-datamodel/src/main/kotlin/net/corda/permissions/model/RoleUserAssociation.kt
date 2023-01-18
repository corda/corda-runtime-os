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
 * Associates Users and Roles.
 * A user can be associated with many roles and one role can be associated in many different users and groups.
 */
@Entity
@Table(name = "rbac_role_user_rel", schema = DbSchema.RBAC)
class RoleUserAssociation(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id")
    val role: Role,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    val user: User,

    @Column(name = "update_ts", nullable = false)
    var updateTimestamp: Instant,
)