package net.corda.messaging.utils

import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.exception.CordaMessageAPIProducerRequiresReset

object ExceptionUtils {
    val CordaMessageAPIException: Set<Class<out Throwable>> = setOf(
        CordaMessageAPIFatalException::class.java,
        CordaMessageAPIIntermittentException::class.java,
        CordaMessageAPIProducerRequiresReset::class.java
    )
}
