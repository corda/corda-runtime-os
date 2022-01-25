package net.corda.permissions.model

import net.corda.db.schema.DbSchema
import net.corda.orm.JpaEntitiesSet
import org.osgi.service.component.annotations.Component

@Suppress("Unused")
@Component
class RpcRbacEntitiesSet : JpaEntitiesSet {
    override val name = DbSchema.RPC_RBAC

    override val content: Set<Class<*>> =
        setOf(
            User::class.java,
            Group::class.java,
            Role::class.java,
            Permission::class.java,
            UserProperty::class.java,
            GroupProperty::class.java,
            ChangeAudit::class.java,
            RoleUserAssociation::class.java,
            RoleGroupAssociation::class.java,
            RolePermissionAssociation::class.java
        )
}