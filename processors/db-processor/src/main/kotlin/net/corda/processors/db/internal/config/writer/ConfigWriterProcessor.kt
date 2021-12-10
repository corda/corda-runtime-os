package net.corda.processors.db.internal.config.writer

import net.corda.data.config.Configuration
import net.corda.data.config.ConfigurationManagementRequest
import net.corda.data.config.ConfigurationManagementResponse
import net.corda.libs.configuration.read.ConfigReader
import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import java.util.concurrent.CompletableFuture

/**
 * Responds to RPC requests of type [ConfigurationManagementRequest] with a response of type
 * [ConfigurationManagementResponse].
 */
internal class ConfigWriterProcessor(
    private val dbUtils: DBUtils,
    private val publisher: Publisher
) : RPCResponderProcessor<ConfigurationManagementRequest, ConfigurationManagementResponse> {

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
        val configEntity = ConfigEntity(request.section, request.configuration, request.version)

        return try {
            dbUtils.writeEntity(setOf(configEntity))
            true
        } catch (e: Exception) {
            respFuture.completeExceptionally(
                ConfigWriteException("Config couldn't be written to the database.", e)
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
            respFuture.complete(ConfigurationManagementResponse("DONT_CARE"))
        } else {
            respFuture.completeExceptionally(
                ConfigWriteException("Config was written to the database, but couldn't be published.", failedFutures[0])
            )
        }
    }
}