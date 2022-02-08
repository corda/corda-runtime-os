package net.corda.permissions.model

object RbacEntities {
    val classes = setOf(
        User::class.java,
        Group::class.java,
        Role::class.java,
        Permission::class.java,
        UserProperty::class.java,
        GroupProperty::class.java,
        ChangeAudit::class.java,
        RoleUserAssociation::class.java,
        RoleGroupAssociation::class.java,
        RolePermissionAssociation::class.java,
    )
}