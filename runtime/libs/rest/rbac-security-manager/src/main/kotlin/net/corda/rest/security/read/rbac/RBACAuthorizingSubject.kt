package net.corda.rest.security.read.rbac

import net.corda.rest.security.AuthorizingSubject
import net.corda.libs.permission.PermissionValidator
import java.util.function.Supplier

/**
 * Authorizing Subject for the Role Based Access Control permission system.
 */
class RBACAuthorizingSubject(
    private val permissionValidatorSupplier: Supplier<PermissionValidator>,
    override val principal: String
) : AuthorizingSubject {

    /**
     * Use the permission validator to determine if this user is authorized for the requested action.
     */
    override fun isPermitted(action: String, vararg arguments: String): Boolean {
        return permissionValidatorSupplier.get().authorizeUser(principal, action)
    }
}