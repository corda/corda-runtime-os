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

@Suppress("LongParameterList")
@Entity
@Table(name = "rbac_user")
class User(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: String,

    @Column(name = "update_ts", nullable = false)
    var updateTimestamp: Instant,

    @Column(name = "full_name", nullable = false)
    var fullName: String,

    @Column(name = "login_name", nullable = false, unique = true)
    var loginName: String,

    @Column(name = "enabled", nullable = false)
    var enabled: Boolean,

    @Column(name = "salt_value")
    var saltValue: String?,

    @Column(name = "hashed_password")
    var hashedPassword: String?,

    @Column(name = "password_expiry")
    var passwordExpiry: Instant?,

    /**
     * Each user can be associated with exactly 1 group.
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
     * Users have properties.
     */
    @OneToMany(mappedBy = "userRef", orphanRemoval = true, cascade = [CascadeType.ALL], fetch = FetchType.EAGER)
    var userProperties: MutableSet<UserProperty> = mutableSetOf()

    /**
     * Users can also be directly associated with roles.
     */
    @OneToMany(mappedBy = "user", orphanRemoval = true, cascade = [CascadeType.ALL], fetch = FetchType.EAGER)
    var roleUserAssociations: MutableSet<RoleUserAssociation> = mutableSetOf()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is User) return false

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}