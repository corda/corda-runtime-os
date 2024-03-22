package net.corda.messaging.utils

import net.corda.lifecycle.LifecycleStatus
import net.corda.messaging.api.exception.CordaMessageAPIAuthException
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.subscription.ThreadLooper
import org.slf4j.Logger

object ExceptionUtils {
    val CordaMessageAPIException: Set<Class<out Throwable>> = setOf(
        CordaMessageAPIFatalException::class.java,
        CordaMessageAPIAuthException::class.java,
        CordaMessageAPIIntermittentException::class.java,
    )
}

fun onAuthException(
    log: Logger,
    attempts: Int,
    threadLooper: ThreadLooper,
    ex: Exception,
    errorMsg: String,
) {
    if (attempts < 3) {
        log.warn("$errorMsg Attempts: $attempts. Retrying.", ex)
    } else {
        log.error("$errorMsg Fatal error occurred. Closing subscription.", ex)
        threadLooper.updateLifecycleStatus(LifecycleStatus.ERROR, errorMsg)
        threadLooper.stopLoop()
    }
}