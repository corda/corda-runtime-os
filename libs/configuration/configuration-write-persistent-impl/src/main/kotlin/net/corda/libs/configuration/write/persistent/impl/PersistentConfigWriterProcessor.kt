package net.corda.libs.configuration.write.persistent.impl

import net.corda.data.config.Configuration
import net.corda.data.config.ConfigurationManagementRequest
import net.corda.data.config.ConfigurationManagementResponse
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.write.persistent.PersistentConfigWriterException
import net.corda.libs.configuration.write.persistent.TOPIC_CONFIG
import net.corda.libs.configuration.write.persistent.TOPIC_CONFIG_MGMT_REQUEST
import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.v5.base.util.contextLogger
import java.util.concurrent.CompletableFuture

/**
 * An RPC responder processor that handles configuration management requests.
 *
 * Listens for configuration management requests on [TOPIC_CONFIG_MGMT_REQUEST]. Persists the updated
 * configuration to the cluster database and publishes the updated configuration to [TOPIC_CONFIG].
 */
internal class PersistentConfigWriterProcessor(
    private val config: SmartConfig,
    private val publisher: Publisher,
    private val dbUtils: DBUtils
) : RPCResponderProcessor<ConfigurationManagementRequest, ConfigurationManagementResponse> {

    private companion object {
        val logger = contextLogger()
    }

    /**
     * For each [request], the processor attempts to commit the updated config to the cluster database. If successful,
     * the updated config is then published by the [publisher] to the [TOPIC_CONFIG] topic for consumption using a
     * `ConfigReader`.
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
        // TODO - Joel - Use my alias for this.
        respFuture: CompletableFuture<ConfigurationManagementResponse>
    ): Boolean {
        val configEntity = ConfigEntity(request.section, request.configuration, request.version)

        return try {
            dbUtils.writeEntity(config, setOf(configEntity))
            true
        } catch (e: Exception) {
            // TODO - Joel - Log error.
            respFuture.completeExceptionally(
                PersistentConfigWriterException("Config $configEntity couldn't be written to the database.", e)
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
        val record =
            Record(TOPIC_CONFIG, request.section, Configuration(request.configuration, request.version.toString()))

        val failedFutures = publisher.publish(listOf(record)).mapNotNull { future ->
            try {
                future.get()
                null
            } catch (e: Exception) {
                e
            }
        }

        // TODO - Joel - Correct behaviour is to keep retrying publication, e.g. background reconciliation.
        //  But being careful not to overwrite new written config.

        if (failedFutures.isEmpty()) {
            respFuture.complete(ConfigurationManagementResponse(true))
        } else {
            // TODO - Joel - Report all failures.
            val cause = failedFutures[0]
            logger.debug("Record $record was written to the database, but couldn't be published. Cause: $cause.")
            respFuture.completeExceptionally(
                // TODO - Joel - Return current version. Extend `ExceptionEnvelope` with new fields.
                PersistentConfigWriterException(
                    "Record $record was written to the database, but couldn't be published.", cause
                )
            )
        }
    }
}