package net.corda.permissions.model.impl

import net.corda.permissions.model.ChangeAudit
import net.corda.permissions.model.Group
import net.corda.permissions.model.GroupProperty
import net.corda.permissions.model.Permission
import net.corda.permissions.model.RbacDboFactory
import net.corda.permissions.model.Role
import net.corda.permissions.model.RoleGroupAssociation
import net.corda.permissions.model.RolePermissionAssociation
import net.corda.permissions.model.RoleUserAssociation
import net.corda.permissions.model.User
import net.corda.permissions.model.UserProperty
import org.osgi.service.component.annotations.Component
import java.time.Instant

@Component(service = [RbacDboFactory::class])
class RbacDboFactoryImpl : RbacDboFactory {

    override val allEntityClasses: Set<Class<*>> = setOf(
        User::class.java, Group::class.java, Role::class.java, Permission::class.java, UserProperty::class.java,
        GroupProperty::class.java, ChangeAudit::class.java, RoleUserAssociation::class.java,
        RoleGroupAssociation::class.java, RolePermissionAssociation::class.java
    )

    override fun createUser(id: String, updateTimestamp: Instant, fullName: String, loginName: String, enabled: Boolean,
                   saltValue: String?, hashedPassword: String?, passwordExpiry: Instant?, parentGroup: Group?
    ): User {
        return User(
            id, updateTimestamp, fullName, loginName, enabled, saltValue, hashedPassword,
            passwordExpiry, parentGroup
        )
    }
}