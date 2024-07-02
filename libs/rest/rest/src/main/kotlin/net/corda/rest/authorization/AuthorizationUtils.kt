package net.corda.rest.authorization

import org.slf4j.LoggerFactory

object AuthorizationUtils {
    private val log = LoggerFactory.getLogger(AuthorizationUtils::class.java)

    fun authorize(
        authorizingSubject: AuthorizingSubject,
        resourceAccessString: String,
        authorizationProvider: AuthorizationProvider? = null
    ): Boolean {
        val principal = authorizingSubject.principal
        log.trace("Authorize \"$principal\" for \"$resourceAccessString\".")

        val isAuthorized = authorizationProvider?.isAuthorized(authorizingSubject, resourceAccessString)
            ?: authorizingSubject.isPermitted(resourceAccessString)

        log.trace("Authorize \"$principal\" for \"$resourceAccessString\" completed. Outcome: $isAuthorized")
        return isAuthorized
    }
}
