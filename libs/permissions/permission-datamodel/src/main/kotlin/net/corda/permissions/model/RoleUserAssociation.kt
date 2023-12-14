package net.corda.permissions.model

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
@Table(name = "rbac_role_user_rel")
class RoleUserAssociation(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    var id: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id")
    var role: Role,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    var user: User,

    @Column(name = "update_ts", nullable = false)
    var updateTimestamp: Instant,
)
