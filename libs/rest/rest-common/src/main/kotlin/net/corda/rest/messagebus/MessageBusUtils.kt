package net.corda.rest.messagebus

import net.corda.rest.exception.InternalServerException
import net.corda.rest.exception.ServiceUnavailableException
import net.corda.messaging.api.exception.CordaRPCAPIPartitionException
import net.corda.rest.exception.HttpApiException
import org.slf4j.Logger

object MessageBusUtils {

    /**
     * Tries to execute the given [block] and returns the result if no error occurs.
     * If an exception is thrown from [block]:
     *  - Throws the original exception when the type has not been explicitly ignored through [untranslatedExceptions].
     *  - Throws [ServiceUnavailableException] if original exception is of type [CordaRPCAPIPartitionException] (rebalance
     *      prevented the future from completing).
     *  - Throw [InternalServerException] if none of the above rules match.
     *
     *  @param logger Logger instance to register exceptions, if any, under WARN log level.
     *  @param operation String representing the operation being executed.
     *  @param untranslatedExceptions Set of exception types to re-throw untranslated.
     *  @param block Block to execute.
     */
    @Suppress("ThrowsCount")
    fun <T> tryWithExceptionHandling(
        logger: Logger,
        operation: String,
        untranslatedExceptions: Set<Class<out Exception>> = emptySet(),
        block: () -> T
    ): T {
        try {
            return block()
        } catch (ex: Exception) {
            when {
                untranslatedExceptions.contains(ex::class.java) -> {
                    throw ex
                }

                ex  is HttpApiException ->
                    throw ex

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
}