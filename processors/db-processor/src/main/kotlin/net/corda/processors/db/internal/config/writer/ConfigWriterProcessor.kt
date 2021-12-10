package net.corda.processors.db.internal.config.writer

import net.corda.data.permissions.management.PermissionManagementRequest
import net.corda.data.permissions.management.PermissionManagementResponse
import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.v5.base.util.contextLogger
import java.util.concurrent.CompletableFuture

/**
 * Receives RPC requests to update the cluster's config, updates the config in the cluster database using [dbUtils],
 * and publishes the updated config for use by the rest of the cluster using [publisher].
 *
 * // TODO - Joel - Describe expected format.
 */
internal class ConfigWriterProcessor(
    private val dbUtils: DBUtils,
    private val publisher: Publisher
) : RPCResponderProcessor<PermissionManagementRequest, PermissionManagementResponse> {

    private companion object {
        private val logger = contextLogger()
    }

    override fun onNext(
        // TODO - Joel - Replace with new Avro request and response classes.
        request: PermissionManagementRequest, respFuture: CompletableFuture<PermissionManagementResponse>
    ) {
        if (publishConfigToDB(request, respFuture)) {
            publishConfigToKafka(request, respFuture)
        }
    }

    // TODO - Joel - Describe.
    private fun publishConfigToDB(request: PermissionManagementRequest, respFuture: CompletableFuture<PermissionManagementResponse>): Boolean {
        val (key, value) = request.requestUserId.split('=')
        val configEntity = ConfigEntity(key, value)

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

    // TODO - Joel - Describe.
    private fun publishConfigToKafka(request: PermissionManagementRequest, respFuture: CompletableFuture<PermissionManagementResponse>) {
        val (key, value) = request.requestUserId.split('=')
        val record = Record(TOPIC_CONFIG, key, value)

        val failedFutures = publisher.publish(listOf(record)).mapNotNull { future ->
            try {
                future.get()
                null
            } catch (e: Exception) {
                e
            }
        }

        if (failedFutures.isEmpty()) {
            respFuture.complete(PermissionManagementResponse("DONT_CARE"))
        } else {
            respFuture.completeExceptionally(
                ConfigWriteException("Config was written to the database, but couldn't be published.", failedFutures[0])
            )
        }

        logger.info("JJJ published records $key, $value") // TODO - Joel - This logging is only for demo purposes.
    }
}