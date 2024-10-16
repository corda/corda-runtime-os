package net.corda.rest.authorization

import net.corda.data.rest.PasswordExpiryStatus
import org.slf4j.Logger
import org.slf4j.LoggerFactory

interface AuthorizationProvider {
    /**
     * Checks if the given subject is authorized to perform the specified action.
     * @param subject The subject (user or service) requesting authorization.
     * @param action The action for which authorization is requested.
     * @return Boolean indicating whether the action is authorized.
     */
    fun isAuthorized(subject: AuthorizingSubject, action: String): Boolean

    companion object Default : AuthorizationProvider {

        val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        override fun isAuthorized(subject: AuthorizingSubject, action: String): Boolean {
            if (subject.expiryStatus == PasswordExpiryStatus.EXPIRED) {
                log.warn("Password has expired. Please change it to carry on.")
                return false
            }
            return AuthorizationUtils.authorize(subject, action)
        }
    }
}
