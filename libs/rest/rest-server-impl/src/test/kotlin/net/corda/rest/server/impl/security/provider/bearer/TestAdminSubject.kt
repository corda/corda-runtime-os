package net.corda.rest.server.impl.security.provider.bearer

import net.corda.data.rest.PasswordExpiryStatus
import net.corda.rest.authorization.AuthorizingSubject

/**
 * An implementation of [AuthorizingSubject] permitting all actions
 */
class TestAdminSubject(
    override val principal: String,
    override val expiryStatus: PasswordExpiryStatus = PasswordExpiryStatus.ACTIVE
) : AuthorizingSubject {

    override fun isPermitted(action: String, vararg arguments: String) = true
}
