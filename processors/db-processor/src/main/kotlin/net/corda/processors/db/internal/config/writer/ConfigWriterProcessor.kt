package net.corda.processors.db.internal.config.writer

import net.corda.data.config.Configuration
import net.corda.data.config.ConfigurationManagementRequest
import net.corda.data.config.ConfigurationManagementResponse
import net.corda.libs.configuration.read.ConfigReader
import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.v5.base.util.contextLogger
import java.util.concurrent.CompletableFuture

/**
 * Responds to RPC requests of type [ConfigurationManagementRequest] with a response of type
 * [ConfigurationManagementResponse].
 */
internal class ConfigWriterProcessor(
    private val dbUtils: DBUtils,
    private val publisher: Publisher
) : RPCResponderProcessor<ConfigurationManagementRequest, ConfigurationManagementResponse> {

    private companion object {
        val logger = contextLogger()
    }

    /**
     * For each [request], the processor attempts to commit the updated config to the cluster database using [dbUtils].
     * If successful, the updated config is then published by the [publisher] to the [TOPIC_CONFIG] topic for
     * consumption using a [ConfigReader].
     *
     * If both steps succeed, [respFuture] is completed to indicate success. Otherwise, it is completed exceptionally.
     */
    override fun onNext(request: ConfigurationManagementRequest, respFuture: ConfigManagementResponseFuture) {
        if (publishConfigToDB(request, respFuture)) {
            publishConfigToKafka(request, respFuture)
        }
    }

    /**
     * Commits the updated config described by [request] to the database.
     *
     * If the transaction fails, the exception is caught and [respFuture] is completed exceptionally. The transaction
     * is not retried.
     *
     * @return True if the commit was successful, false if the commit failed.
     */
    private fun publishConfigToDB(
        request: ConfigurationManagementRequest,
        respFuture: CompletableFuture<ConfigurationManagementResponse>
    ): Boolean {
        // TODO - Joel - Re-enable version, which wasn't working.
        val configEntity = ConfigEntity(request.section, request.configuration/**, request.version*/)

        return try {
            dbUtils.writeEntity(setOf(configEntity))
            true
        } catch (e: Exception) {
            logger.debug("Config $configEntity couldn't be written to the database. Cause: $e.")
            respFuture.completeExceptionally(
                ConfigWriteException("Config $configEntity couldn't be written to the database.", e)
            )
            false
        }
    }

    /**
     * Publishes the updated config described by [request] on the [TOPIC_CONFIG] Kafka topic.
     *
     * If the publication fails, the exception is caught and [respFuture] is completed exceptionally. Publication is
     * not retried. Otherwise, we complete [respFuture] successfully.
     */
    private fun publishConfigToKafka(
        request: ConfigurationManagementRequest,
        respFuture: CompletableFuture<ConfigurationManagementResponse>
    ) {
        val record = Record(TOPIC_CONFIG, request.section, Configuration(request.configuration, request.version))

        val failedFutures = publisher.publish(listOf(record)).mapNotNull { future ->
            try {
                future.get()
                null
            } catch (e: Exception) {
                e
            }
        }

        if (failedFutures.isEmpty()) {
            respFuture.complete(ConfigurationManagementResponse(true))
        } else {
            val cause = failedFutures[0]
            logger.debug("Record $record was written to the database, but couldn't be published. Cause: $cause.")
            respFuture.completeExceptionally(
                ConfigWriteException("Record $record was written to the database, but couldn't be published.", cause)
            )
        }
    }
}