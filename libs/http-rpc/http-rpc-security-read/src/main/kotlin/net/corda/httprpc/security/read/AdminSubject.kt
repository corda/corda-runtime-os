package net.corda.httprpc.security.read

import net.corda.httprpc.security.AuthorizingSubject

/**
 * An implementation of [AuthorizingSubject] permitting all actions
 */
class AdminSubject(override val principal: String) : AuthorizingSubject {

    override fun isPermitted(action: String, vararg arguments: String) = true
}