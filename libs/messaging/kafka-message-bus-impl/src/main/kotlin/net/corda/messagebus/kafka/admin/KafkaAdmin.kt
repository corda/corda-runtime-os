package net.corda.messagebus.kafka.admin

import net.corda.messagebus.api.admin.Admin
import net.corda.utilities.retry.Linear
import net.corda.utilities.retry.tryWithBackoff
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.apache.kafka.clients.admin.AdminClient
import org.slf4j.LoggerFactory
import java.util.concurrent.ExecutionException

class KafkaAdmin(private val adminClient: AdminClient) : Admin {

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun getTopics(): Set<String> {
        return tryWithBackoff(
            logger = logger,
            maxRetries = 3,
            maxTimeMillis = 3000,
            backoffStrategy = Linear(200),
            shouldRetry = { _, _, throwable -> throwable.isRecoverable() },
            onRetryAttempt = { attempt, delayMillis, throwable ->
                logger.warn("Attempt $attempt failed with \"${throwable.message}\", will try again after $delayMillis milliseconds")
            },
            onRetryExhaustion = { attempts, elapsedMillis, throwable ->
                val errorMessage =
                    "Execution failed with \"${throwable.message}\" after retrying $attempts times for $elapsedMillis milliseconds."
                logger.warn(errorMessage)
                CordaRuntimeException(errorMessage, throwable)
            },
            {
                adminClient.listTopics().names().get()
            }
        )
    }

    private fun Throwable.isRecoverable(): Boolean {
        return when (this) {
            is ExecutionException -> true
            else -> false
        }
    }


    override fun close() {
        adminClient.close()
    }
}
