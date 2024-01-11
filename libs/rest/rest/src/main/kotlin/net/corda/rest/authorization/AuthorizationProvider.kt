package net.corda.rest.authorization

import net.corda.rest.exception.ForbiddenException

interface AuthorizationProvider {
    /**
     * Checks if the given subject is authorized to perform the specified action.
     * @param subject The subject (user or service) requesting authorization.
     * @param action The action for which authorization is requested.
     * @return Boolean indicating whether the action is authorized.
     */
    fun isAuthorized(subject: AuthorizingSubject, action: String): Boolean

    companion object Default : AuthorizationProvider {
        override fun isAuthorized(subject: AuthorizingSubject, action: String): Boolean {
            return try {
                AuthorizationUtils.authorize(subject, action)
                true
            } catch (e: ForbiddenException) {
                false
            }
        }
    }
}