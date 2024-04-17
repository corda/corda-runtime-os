package net.corda.rest.security.read.rbac

import net.corda.data.rest.PasswordExpiryStatus
import net.corda.libs.permission.PermissionValidator
import net.corda.rest.authorization.AuthorizingSubject
import java.util.function.Supplier

/**
 * Authorizing Subject for the Role Based Access Control permission system.
 */
class RBACAuthorizingSubject(
    private val permissionValidatorSupplier: Supplier<PermissionValidator>,
    override val principal: String,
    override val expiryStatus: PasswordExpiryStatus?
) : AuthorizingSubject {

    /**
     * Use the permission validator to determine if this user is authorized for the requested action.
     */
    override fun isPermitted(
        action: String,
        vararg arguments: String,
    ): Boolean {
        return permissionValidatorSupplier.get().authorizeUser(principal, action)
    }
}
