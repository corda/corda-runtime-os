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
 * Associates Permissions and Roles.
 * A role can be associated with many permissions and a permission maybe associated in multiple roles.
 */
@Entity
@Table(name = "rbac_role_perm_rel")
class RolePermissionAssociation(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id")
    val role: Role,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "perm_id")
    val permission: Permission,

    @Column(name = "update_ts", nullable = false)
    var updateTimestamp: Instant,
)