package net.corda.httprpc.security.read.rbac

import net.corda.httprpc.security.AuthorizingSubject
import net.corda.libs.permission.PermissionValidator

/**
 * Authorizing Subject for the Role Based Access Control permission system.
 */
class RBACAuthorizingSubject(
    private val permissionValidator: PermissionValidator,
    override val principal: String
) : AuthorizingSubject {

    /**
     * Use the permission validator to determine if this user is authorized for the requested action.
     */
    override fun isPermitted(action: String, vararg arguments: String): Boolean {
        // for now, admin is a super admin and can do anything
        if ("admin".equals(principal, true)) return true

        return permissionValidator.authorizeUser(principal, action)
    }
}