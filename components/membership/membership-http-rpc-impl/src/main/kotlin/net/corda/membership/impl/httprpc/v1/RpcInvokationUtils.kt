package net.corda.membership.impl.httprpc.v1

import net.corda.httprpc.exception.InternalServerException
import net.corda.httprpc.exception.ServiceUnavailableException
import net.corda.messaging.api.exception.CordaRPCAPIPartitionException
import org.slf4j.Logger

/**
 * Tries to execute the given [block] and returns the result if no error occurs.
 * If an exception is thrown from [block]:
 *  - Throws the original exception when the type has not been explicitly ignored through [ignoredExceptions].
 *  - Throws [ServiceUnavailableException] if original exception is of type [CordaRPCAPIPartitionException] (rebalance
 *      prevented the future from completing).
 *  - Throw [InternalServerException] if none of the above rules match.
 *
 *  @param logger Logger instance to register exceptions, if any, under WARN log level.
 *  @param operation String representing the operation being executed.
 *  @param ignoredExceptions List of exception types to ignore.
 *  @param block Block to execute.
 */
@Suppress("ThrowsCount")
internal fun <T> tryWithExceptionHandling(
    logger: Logger,
    operation: String,
    ignoredExceptions: Collection<Class<out Exception>> = emptyList(),
    block: () -> T
): T {
    try {
        return block()
    } catch (ex: Exception) {
        when {
            ignoredExceptions.contains(ex::class.java) -> {
                throw ex
            }

            ex is CordaRPCAPIPartitionException -> {
                logger.warn("Could not $operation", ex)
                throw ServiceUnavailableException("Could not $operation: Repartition Event!")
            }

            else -> {
                logger.warn("Could not $operation", ex)
                throw InternalServerException("Could not $operation: ${ex.message}")
            }
        }
    }
}
