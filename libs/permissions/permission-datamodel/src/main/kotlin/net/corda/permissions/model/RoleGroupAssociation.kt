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
 * Associates Groups and Roles.
 * A role can be associated with many groups and many roles can be associated with a group.
 */
@Entity
@Table(name = "rbac_role_group_rel")
class RoleGroupAssociation(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    var id: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id")
    var role: Role,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    var group: Group,

    @Column(name = "update_ts", nullable = false)
    var updateTimestamp: Instant,
)
