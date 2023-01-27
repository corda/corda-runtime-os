package net.corda.httprpc.server.impl.security.provider.bearer

import net.corda.httprpc.security.AuthorizingSubject

/**
 * An implementation of [AuthorizingSubject] permitting all actions
 */
class TestAdminSubject(override val principal: String) : AuthorizingSubject {

    override fun isPermitted(action: String, vararg arguments: String) = true
}