package net.corda.messagebus.kafka.utils

import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import org.apache.kafka.common.KafkaException
import org.slf4j.Logger
import java.util.function.Supplier

internal object KafkaRetryUtils {
    /**
     * Performs a given Kafka action retrying multiple times and sleeping between retries.
     * If number of retries been exhausted [CordaMessageAPIFatalException] is thrown providing details.
     */
    fun <T> executeKafkaActionWithRetry(
        retryCount: Int = 3,
        sleepMs: Long = 5000,
        action: Supplier<T>,
        errorMessage: Supplier<String>,
        log: Logger
    ): T {
        require(retryCount > 0) { "Retry count should be positive. Current value: $retryCount" }
        require(sleepMs > 0) { "Sleep internal must be positive. Current value: $sleepMs" }

        var retryCountCurr = retryCount
        var lastException: KafkaException? = null
        while (retryCountCurr > 0) {
            try {
                return action.get()
            } catch (ex: KafkaException) {
                lastException = ex
                retryCountCurr--
                log.warn("Cannot perform Kafka action. Will retry $retryCountCurr more times", ex)
                if (retryCountCurr > 0) {
                    Thread.sleep(sleepMs)
                }
            }
        }
        val message = errorMessage.get()
        log.error(message, lastException)
        throw CordaMessageAPIFatalException(message, lastException)
    }
}