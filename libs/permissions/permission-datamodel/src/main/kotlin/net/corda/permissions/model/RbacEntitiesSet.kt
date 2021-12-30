package net.corda.permissions.model

import net.corda.db.schema.DbSchema
import net.corda.orm.EntitiesSet
import org.osgi.service.component.annotations.Component

@Suppress("Unused")
@Component(name = DbSchema.RPC_RBAC)
class RbacEntitiesSet : EntitiesSet by EntitiesSet.of(
    DbSchema.RPC_RBAC,
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
)