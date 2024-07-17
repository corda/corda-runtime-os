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
        return try {
            tryWithBackoff(
                logger = logger,
                maxRetries = 3,
                maxTimeMillis = 3000,
                backoffStrategy = Linear(200)
            ) {
                adminClient.listTopics().names().get()
            }
        } catch (e: ExecutionException) {
            logger.warn("could not get Topics")
            throw CordaRuntimeException(e.message)
        }

    }

    override fun close() {
        adminClient.close()
    }
}
