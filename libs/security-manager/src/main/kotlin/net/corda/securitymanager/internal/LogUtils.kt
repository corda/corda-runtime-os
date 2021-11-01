package net.corda.securitymanager.internal

import org.osgi.service.component.annotations.Component
import org.slf4j.Logger

/** Handles logging operations for the `DiscoverySecurityManager`. */
@Component(service = [LogUtils::class])
open class LogUtils {
    /** Logs the [message] at info level using the provided [logger]. */
    open fun logInfo(logger: Logger, message: String) = logger.info(message)
}