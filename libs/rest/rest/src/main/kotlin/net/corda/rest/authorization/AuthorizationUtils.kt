package net.corda.rest.authorization

import net.corda.utilities.trace
import org.slf4j.LoggerFactory
import net.corda.utilities.withMDC

object AuthorizationUtils {
    private val log = LoggerFactory.getLogger(AuthorizationUtils::class.java)

    const val USER_MDC = "http.user"
    const val METHOD_MDC = "http.method"
    const val PATH_MDC = "http.path"
    private const val METHOD_SEPARATOR = ":"

    private fun <T> withMDC(user: String, method: String, path: String, block: () -> T): T {
        return withMDC(listOf(USER_MDC to user, METHOD_MDC to method, PATH_MDC to path).toMap(), block)
    }

    fun authorize(authorizingSubject: AuthorizingSubject, resourceAccessString: String, authorizationProvider: AuthorizationProvider? = null) {
        val principal = authorizingSubject.principal
        log.trace("Authorize \"$principal\" for \"$resourceAccessString\".")

        val isAuthorized = authorizationProvider?.isAuthorized(authorizingSubject, resourceAccessString)
            ?: authorizingSubject.isPermitted(resourceAccessString)

        if (!isAuthorized) {
            val pathParts = resourceAccessString.split(METHOD_SEPARATOR, limit = 2)
            withMDC(principal, pathParts.firstOrNull() ?: "no_method", pathParts.lastOrNull() ?: "no_path") {
                "User not authorized.".let {
                    log.info(it)
                    throw IllegalStateException(it)
                }
            }
        }
        log.trace("Authorize \"$principal\" for \"$resourceAccessString\" completed.")
    }
}